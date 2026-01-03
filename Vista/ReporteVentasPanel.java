package Vista;

import Controlador.InventarioDAO;
import Modelo.Inventario;

import Conexion.Conecta;
import Controlador.AsesorDAO;
import Controlador.ExportadorCSV;
import Controlador.NotasDAO;
import java.util.Map;
import java.util.HashMap;

import Modelo.Asesor;
import Modelo.NotaDetalle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.AttributeSet;


import java.awt.*;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import java.util.Locale;

/**
 * Panel "Reporte de ventas".
 *
 * Filtros:
 *  1) De fecha a fecha, de artículo a artículo
 *  2) De fecha a fecha, de vendedor a vendedor
 *  3) De fecha a fecha, de cliente a cliente
 */
public class ReporteVentasPanel extends JPanel {

    // ======== Fechas / formato ========
    
    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private final DateField fechaIni = new DateField();
    private final DateField fechaFin = new DateField();
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    // ======== Tipo de filtro ========
    private final JRadioButton rbArticulo = new JRadioButton("Por artículo", true);
    private final JRadioButton rbVendedor = new JRadioButton("Por vendedor");
    private final JRadioButton rbCliente = new JRadioButton("Por cliente");

    // Panel con CardLayout para filtros específicos
    private final CardLayout cardsFiltros = new CardLayout();
    private final JPanel panelFiltrosEspecificos = new JPanel(cardsFiltros);

    // --- Filtro por artículo ---
    private final JTextField tfArtIni = new JTextField(8);
    private final JTextField tfArtFin = new JTextField(8);

    // Sub-modo del filtro por artículo: por código o por tipo
    private final JRadioButton rbArtPorCodigo = new JRadioButton("Por código", true);
    private final JRadioButton rbArtPorTipo   = new JRadioButton("Por tipo");
    private final JComboBox<String> cbTipoArticulo = new JComboBox<>();


    // --- Filtro por vendedor (asesor) ---
    private final JComboBox<AsesorItem> cbVendIni = new JComboBox<>();
    private final JComboBox<AsesorItem> cbVendFin = new JComboBox<>();

    // --- Filtro por cliente (teléfono) ---
    private final JComboBox<ClienteItem> cbCliIni = new JComboBox<>();
    private final JComboBox<ClienteItem> cbCliFin = new JComboBox<>();
// Mapa de número de asesor -> nombre completo (para mostrar en la tabla)
private final Map<Integer, String> mapaAsesores = new HashMap<>();

    // ======== Tabla de notas (arriba) ========
    // ======== Tabla de notas (arriba) ========
private final DefaultTableModel modelNotas = new DefaultTableModel(
        new String[]{"# Nota", "Asesor", "Tipo", "Folio", "Cliente", "Fecha", "Total", "Saldo", "Status"}, 0) {
    @Override
    public boolean isCellEditable(int r, int c) { return false; }

    @Override
    public Class<?> getColumnClass(int c) {
        return switch (c) {
            case 0 -> Integer.class;   // numero_nota (oculta en la vista)
            case 6, 7 -> Double.class; // Total, Saldo
            default -> String.class;
        };
    }
};

    private final JTable tbNotas = new JTable(modelNotas);

    // ======== Detalle (abajo) ========
    private final DefaultTableModel modelDet = new DefaultTableModel(
            new String[]{"Código", "Artículo", "Marca", "Modelo", "Talla", "Color", "Precio", "Desc", "Subtotal"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return (c >= 6 && c <= 8) ? Double.class : Object.class;
        }
    };
    private final JTable tbDet = new JTable(modelDet);

    private final JTabbedPane tabs = new JTabbedPane();

    // ======== Botones acción ========
    private final JButton btCargar = new JButton("Cargar");
    private final JButton btExportar = new JButton("Exportar CSV");

    // ======== Clases auxiliares ========
    private static class AsesorItem {
        final Integer numero;
        final String nombre;

        AsesorItem(Integer numero, String nombre) {
            this.numero = numero;
            this.nombre = nombre;
        }

        @Override
        public String toString() {
            if (numero == null) return "";
            return numero + " - " + (nombre == null ? "" : nombre);
        }
    }

    private static class ClienteItem {
        final String telefono;
        final String nombre;

        ClienteItem(String telefono, String nombre) {
            this.telefono = telefono;
            this.nombre = nombre;
        }

        @Override
        public String toString() {
            if (telefono == null) return "";
            return telefono + " - " + (nombre == null ? "" : nombre);
        }
    }

    // Para exportar CSV
    // Para exportar CSV (nivel detalle)
static class CsvRow {
    public Integer numeroNota;
    public String folio;
    public String cliente;
    public String asesor;
    public String fecha; //fecha de la nota
    public String tipo;
    public String status;
    public Double total;
    public Double saldo;

    public String codigo;
    public String articulo;
    public String marca;
    public String modelo;
    public String talla;
    public String color;
    
    public String fechaRegistroArticulo; // fecha_registro de Inventarios
    public Double costoArticulo;         // costoIva de Inventarios

    public Double precio;
    public Double descuento;
    public Double subtotal;
}


    // ======== Constructor ========
    public ReporteVentasPanel() {
        setLayout(new BorderLayout(8, 8));

        construirPanelFiltros();
        construirTablas();

        // Cargar combos
        cargarAsesores();
        cargarClientes();

        cargarTiposArticulo();

        // Listeners
        btCargar.addActionListener(e -> cargar());
        btExportar.addActionListener(_e -> {
        if (Utilidades.SeguridadUI.pedirYValidarClave(this)) {
            exportarCSV();
        }

    });
// al final del constructor, después de registrar listeners
fechaIni.set(LocalDate.now().withDayOfMonth(1));
fechaFin.set(LocalDate.now());


        // Cambio de tarjeta de filtros
        rbArticulo.addActionListener(e -> cardsFiltros.show(panelFiltrosEspecificos, "ARTICULO"));
        rbVendedor.addActionListener(e -> cardsFiltros.show(panelFiltrosEspecificos, "VENDEDOR"));
        rbCliente.addActionListener(e -> cardsFiltros.show(panelFiltrosEspecificos, "CLIENTE"));

        // Selección de fila para cargar detalle
        tbNotas.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                Integer num = getNumeroNotaSeleccionada();
                if (num != null) {
                    cargarDetalle(num);
                } else {
                    modelDet.setRowCount(0);
                }
            }
        });

    }

    // ===================== Construcción de UI =====================

    private void construirPanelFiltros() {
        JPanel north = new JPanel(new BorderLayout(4, 4));

        // --- Línea 1: fechas + tipo de filtro + botones ---
        JPanel linea1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        linea1.add(new JLabel("Del:"));
        linea1.add(fechaIni.panel());
        linea1.add(new JLabel("al"));
        linea1.add(fechaFin.panel());


        ButtonGroup grupo = new ButtonGroup();
        grupo.add(rbArticulo);
        grupo.add(rbVendedor);
        grupo.add(rbCliente);

        linea1.add(Box.createHorizontalStrut(16));
        linea1.add(new JLabel("Filtro:"));
        linea1.add(rbArticulo);
        linea1.add(rbVendedor);
        linea1.add(rbCliente);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        panelBotones.add(btCargar);
        panelBotones.add(btExportar);

        JPanel header = new JPanel(new BorderLayout());
        header.add(linea1, BorderLayout.CENTER);
        header.add(panelBotones, BorderLayout.EAST);

        // --- Línea 2: panel de filtros específicos (CardLayout) ---
        panelFiltrosEspecificos.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

                // Card de artículos
        JPanel cardArt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        // Rango por código
        cardArt.add(new JLabel("Artículo de:"));
        cardArt.add(tfArtIni);
        cardArt.add(new JLabel("a"));
        cardArt.add(tfArtFin);

        // Separador visual
        cardArt.add(Box.createHorizontalStrut(20));

        // Búsqueda por tipo de artículo
        cardArt.add(new JLabel("O  Buscar por tipo de artículo:"));
        cardArt.add(cbTipoArticulo);


        // Card de vendedores
        JPanel cardVend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        cardVend.add(new JLabel("Vendedor de:"));
        cardVend.add(cbVendIni);
        cardVend.add(new JLabel("a"));
        cardVend.add(cbVendFin);

        // Card de clientes
        JPanel cardCli = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        cardCli.add(new JLabel("Cliente (teléfono) de:"));
        cardCli.add(cbCliIni);
        cardCli.add(new JLabel("a"));
        cardCli.add(cbCliFin);

        panelFiltrosEspecificos.add(cardArt, "ARTICULO");
        panelFiltrosEspecificos.add(cardVend, "VENDEDOR");
        panelFiltrosEspecificos.add(cardCli, "CLIENTE");
        cardsFiltros.show(panelFiltrosEspecificos, "ARTICULO");

        north.add(header, BorderLayout.NORTH);
        north.add(panelFiltrosEspecificos, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
    }

    private void construirTablas() {
        JScrollPane spNotas = new JScrollPane(tbNotas);
        spNotas.setBorder(BorderFactory.createTitledBorder("Notas"));

        // Ocultar la columna # Nota en la vista (queda en el modelo)
        ocultarColumnaVista(tbNotas, 0);

        // Detalle abajo en pestañas (por ahora solo Detalle)
        tabs.addTab("Detalle", new JScrollPane(tbDet));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spNotas, tabs);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
    }

    // ===================== Lógica de filtros =====================

private void cargar() {
    modelNotas.setRowCount(0);
    modelDet.setRowCount(0);

    LocalDate ini = fechaIni.get();
    LocalDate fin = fechaFin.get();

    if (ini == null || fin == null) {
        JOptionPane.showMessageDialog(this, "Debes seleccionar ambas fechas.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    if (fin.isBefore(ini)) {
        JOptionPane.showMessageDialog(this, "La fecha final no puede ser menor que la inicial.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    LocalDate finExcl = fin.plusDays(1); // [ini, fin)

    try {
        if (rbArticulo.isSelected()) {
            cargarPorArticulo(ini, finExcl);
        } else if (rbVendedor.isSelected()) {
            cargarPorVendedor(ini, finExcl);
        } else {
            cargarPorCliente(ini, finExcl);
        }

        if (modelNotas.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No hay registros para los filtros seleccionados.",
                    "Sin registros", JOptionPane.INFORMATION_MESSAGE);
        }
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "Error al cargar reporte: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

private void cargarPorArticulo(LocalDate ini, LocalDate finExcl) throws Exception {
    String sIni  = tfArtIni.getText().trim();
    String sFin  = tfArtFin.getText().trim();
    String tipo  = (String) cbTipoArticulo.getSelectedItem();
    tipo = (tipo == null) ? "" : tipo.trim();

    boolean tieneRango = !sIni.isEmpty() && !sFin.isEmpty();
    boolean tieneTipo  = !tipo.isEmpty();

    // Si marca ambas cosas, le regañamos
    if (tieneRango && tieneTipo) {
        JOptionPane.showMessageDialog(this,
                "Capturaste un rango de artículos Y también un tipo.\n" +
                "Usa solo una opción: rango de código O tipo de artículo.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    // Solo rango de código
    if (tieneRango) {
        cargarPorRangoCodigoArticulo(ini, finExcl);
        return;
    }

    // Solo tipo
    if (tieneTipo) {
        cargarPorTipoArticulo(ini, finExcl);
        return;
    }

    // Nada
    JOptionPane.showMessageDialog(this,
            "Debes capturar un rango de códigos de artículo\n" +
            "O seleccionar un tipo de artículo.",
            "Aviso", JOptionPane.WARNING_MESSAGE);
}


private void cargarPorRangoCodigoArticulo(LocalDate ini, LocalDate finExcl) throws Exception {
    String sIni = tfArtIni.getText().trim();
    String sFin = tfArtFin.getText().trim();

    if (sIni.isEmpty() || sFin.isEmpty()) {
        JOptionPane.showMessageDialog(this,
                "Debes capturar el código de artículo inicial y final.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    // Trabajar como cadenas (porque en BD es VARCHAR)
    String artIni = sIni.toUpperCase();
    String artFin = sFin.toUpperCase();

    // Asegurar que artIni <= artFin lexicográficamente
    if (artIni.compareTo(artFin) > 0) {
        String tmp = artIni;
        artIni = artFin;
        artFin = tmp;
    }

    String sql =
        "SELECT DISTINCT n.numero_nota, n.asesor, n.tipo, n.folio, n.fecha_registro, " +
        "       n.total, n.saldo, n.status, " +
        "       CONCAT(COALESCE(c.nombre,''),' ',COALESCE(c.apellido_paterno,''),' ',COALESCE(c.apellido_materno,'')) AS nombre_cliente " +
        "FROM Notas n " +
        "JOIN Nota_Detalle d ON d.numero_nota = n.numero_nota " +
        "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
        "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
        "  AND n.fecha_registro >= ? AND n.fecha_registro < ? " +
        "  AND d.codigo_articulo BETWEEN ? AND ? " +
        "ORDER BY n.fecha_registro, n.numero_nota";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setDate(1, Date.valueOf(ini));
        ps.setDate(2, Date.valueOf(finExcl));
        ps.setString(3, artIni);
        ps.setString(4, artFin);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                agregarFilaNota(rs);
            }
        }
    }
}

private void cargarPorTipoArticulo(LocalDate ini, LocalDate finExcl) throws Exception {
    String tipo = (String) cbTipoArticulo.getSelectedItem();
    if (tipo == null || tipo.trim().isEmpty()) {
        JOptionPane.showMessageDialog(this,
                "Debes seleccionar un tipo de artículo.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    String sql =
        "SELECT DISTINCT n.numero_nota, n.asesor, n.tipo, n.folio, n.fecha_registro, " +
        "       n.total, n.saldo, n.status, " +
        "       CONCAT(COALESCE(c.nombre,''),' ',COALESCE(c.apellido_paterno,''),' ',COALESCE(c.apellido_materno,'')) AS nombre_cliente " +
        "FROM Notas n " +
        "JOIN Nota_Detalle d ON d.numero_nota = n.numero_nota " +
        "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
        "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
        "  AND n.fecha_registro >= ? AND n.fecha_registro < ? " +
        "  AND d.articulo = ? " +
        "ORDER BY n.fecha_registro, n.numero_nota";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setDate(1, Date.valueOf(ini));
        ps.setDate(2, Date.valueOf(finExcl));
        ps.setString(3, tipo);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                agregarFilaNota(rs);
            }
        }
    }
}

    private void cargarPorVendedor(LocalDate ini, LocalDate finExcl) throws Exception {
        AsesorItem ai = (AsesorItem) cbVendIni.getSelectedItem();
        AsesorItem af = (AsesorItem) cbVendFin.getSelectedItem();
        if (ai == null || af == null || ai.numero == null || af.numero == null) {
            JOptionPane.showMessageDialog(this,
                    "Debes seleccionar vendedor inicial y final.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int vendIni = ai.numero;
        int vendFin = af.numero;
        if (vendIni > vendFin) {
            int tmp = vendIni;
            vendIni = vendFin;
            vendFin = tmp;
        }

        String sql =
    "SELECT n.numero_nota, n.asesor, n.tipo, n.folio, n.fecha_registro, " +
    "       n.total, n.saldo, n.status, " +
    "       CONCAT(COALESCE(c.nombre,''),' ',COALESCE(c.apellido_paterno,''),' ',COALESCE(c.apellido_materno,'')) AS nombre_cliente " +
    "FROM Notas n " +
    "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
    "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
    "  AND n.fecha_registro >= ? AND n.fecha_registro < ? " +
    "  AND n.asesor BETWEEN ? AND ? " +
    "ORDER BY n.fecha_registro, n.numero_nota";



        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(ini));
            ps.setDate(2, Date.valueOf(finExcl));
            ps.setInt(3, vendIni);
            ps.setInt(4, vendFin);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    agregarFilaNota(rs);
                }
            }
        }
    }

    private void cargarPorCliente(LocalDate ini, LocalDate finExcl) throws Exception {
        ClienteItem ci = (ClienteItem) cbCliIni.getSelectedItem();
        ClienteItem cf = (ClienteItem) cbCliFin.getSelectedItem();
        if (ci == null || cf == null || ci.telefono == null || cf.telefono == null) {
            JOptionPane.showMessageDialog(this,
                    "Debes seleccionar cliente inicial y final.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String telIni = ci.telefono;
        String telFin = cf.telefono;
        if (telIni.compareTo(telFin) > 0) {
            String tmp = telIni;
            telIni = telFin;
            telFin = tmp;
        }

        String sql =
    "SELECT n.numero_nota, n.asesor, n.tipo, n.folio, n.fecha_registro, " +
    "       n.total, n.saldo, n.status, " +
    "       CONCAT(COALESCE(c.nombre,''),' ',COALESCE(c.apellido_paterno,''),' ',COALESCE(c.apellido_materno,'')) AS nombre_cliente " +
    "FROM Notas n " +
    "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
    "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
    "  AND n.fecha_registro >= ? AND n.fecha_registro < ? " +
    "  AND n.telefono BETWEEN ? AND ? " +
    "ORDER BY n.fecha_registro, n.numero_nota";



        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(ini));
            ps.setDate(2, Date.valueOf(finExcl));
            ps.setString(3, telIni);
            ps.setString(4, telFin);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    agregarFilaNota(rs);
                }
            }
        }
    }

private void agregarFilaNota(ResultSet rs) throws Exception {
    int numero = rs.getInt("numero_nota");

    // número de asesor (puede ser null)
    Integer asesorNum = (Integer) rs.getObject("asesor");
    String asesorNombre = "";
    if (asesorNum != null) {
        asesorNombre = mapaAsesores.getOrDefault(asesorNum, String.valueOf(asesorNum));
    }

    String tipo    = rs.getString("tipo");
    String folio   = rs.getString("folio");
    String cliente = rs.getString("nombre_cliente");
    Date f         = rs.getDate("fecha_registro");
    String fecha   = (f == null) ? "" : f.toLocalDate().format(DF);

    Double total = null;
    try {
        if (rs.getBigDecimal("total") != null) {
            total = rs.getBigDecimal("total").doubleValue();
        }
    } catch (Throwable ignore) {
        total = rs.getDouble("total");
    }

    Double saldo = null;
    try {
        if (rs.getBigDecimal("saldo") != null) {
            saldo = rs.getBigDecimal("saldo").doubleValue();
        }
    } catch (Throwable ignore) {
        saldo = rs.getDouble("saldo");
    }

    String status = rs.getString("status");

    modelNotas.addRow(new Object[]{
            numero,                  // 0 (oculta)
            safe(asesorNombre),      // 1
            safe(tipo),              // 2
            safe(folio),             // 3
            safe(cliente),           // 4
            fecha,                   // 5
            n(total),                // 6
            n(saldo),                // 7
            safe(status)             // 8
    });
}

    // ===================== Detalle =====================

    private void cargarDetalle(int numeroNota) {
        modelDet.setRowCount(0);
        try {
            NotasDAO dao = new NotasDAO();
            List<NotaDetalle> det = dao.listarDetalleDeNota(numeroNota);
            for (NotaDetalle d : det) {
                modelDet.addRow(new Object[]{
                        d.getCodigoArticulo(),
                        safe(d.getArticulo()),
                        safe(d.getMarca()),
                        safe(d.getModelo()),
                        safe(d.getTalla()),
                        safe(d.getColor()),
                        n(d.getPrecio()),
                        n(d.getDescuento()),
                        n(d.getSubtotal())
                });
            }
            if (det.isEmpty()) {
                modelDet.addRow(new Object[]{"—", "—", "—", "—", "—", "—", 0.0, 0.0, 0.0});
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error cargando detalle: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== Exportar CSV =====================

private void exportarCSV() {
    try {
        List<CsvRow> rows = new ArrayList<>();
        NotasDAO notasDAO = new NotasDAO();
        InventarioDAO invDAO = new InventarioDAO();
        Map<String, Inventario> cacheInv = new HashMap<>();


        // Recorremos TODAS las notas que aparecen en la tabla filtrada
        for (int vr = 0; vr < tbNotas.getRowCount(); vr++) {
            int mr = tbNotas.convertRowIndexToModel(vr);

            Object numObj = modelNotas.getValueAt(mr, 0);
            if (!(numObj instanceof Integer)) continue;
            Integer numeroNota = (Integer) numObj;

            // Nota: ignoramos la columna 1 (Asesor) para el CSV
            String asesor = String.valueOf(modelNotas.getValueAt(mr, 1));
            String tipo    = String.valueOf(modelNotas.getValueAt(mr, 2));
            String folio   = String.valueOf(modelNotas.getValueAt(mr, 3));
            String cliente = String.valueOf(modelNotas.getValueAt(mr, 4));
            String fecha   = String.valueOf(modelNotas.getValueAt(mr, 5));
            Double total   = toDouble(modelNotas.getValueAt(mr, 6));
            Double saldo   = toDouble(modelNotas.getValueAt(mr, 7));
            String status  = String.valueOf(modelNotas.getValueAt(mr, 8));

            // Traer detalle de la nota
            List<NotaDetalle> det = notasDAO.listarDetalleDeNota(numeroNota);

            if (det.isEmpty()) {
                // Por si alguna nota no tiene detalle (muy raro, pero mejor cubrirlo)
                CsvRow r = new CsvRow();
                r.numeroNota = numeroNota;
                r.asesor = asesor;
                r.folio      = folio;
                r.cliente    = cliente;
                r.fecha      = fecha; //fecha de la nota
                r.tipo       = tipo;
                r.status     = status;
                r.total      = total;
                r.saldo      = saldo;
                rows.add(r);
            } else {
                for (NotaDetalle d : det) {
                    CsvRow r = new CsvRow();
                    r.numeroNota = numeroNota;
                    r.asesor = asesor;
                    r.folio      = folio;
                    r.cliente    = cliente;
                    r.fecha      = fecha;
                    r.tipo       = tipo;
                    r.status     = status;
                    r.total      = total;
                    r.saldo      = saldo;


                    r.codigo     = d.getCodigoArticulo();
                    r.articulo   = d.getArticulo();
                    r.marca      = d.getMarca();
                    r.modelo     = d.getModelo();
                    r.talla      = d.getTalla();
                    r.color      = d.getColor();
                    // === NUEVO: buscar información en Inventarios ===
                    Inventario inv = null;
                    if (r.codigo != null && !r.codigo.isBlank()) {
                        inv = cacheInv.get(r.codigo);
                        if (inv == null) {
                            try {
                                inv = invDAO.buscarPorCodigo(r.codigo);
                            } catch (Exception exInv) {
                                inv = null; // si truena, lo dejamos null y seguimos
                            }
                            cacheInv.put(r.codigo, inv);
                        }
                    }

                    if (inv != null) {
                        if (inv.getFechaRegistro() != null) {
                            // usamos el mismo DF = yyyy-MM-dd que ya tienes en la clase
                            r.fechaRegistroArticulo = inv.getFechaRegistro().format(DF);
                        }
                        r.costoArticulo = inv.getCostoIva();
                    }
                    r.precio     = d.getPrecio();
                    r.descuento  = d.getDescuento();
                    r.subtotal   = d.getSubtotal();

                    rows.add(r);
                }
            }
        }

        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay datos para exportar.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        LocalDate ini = fechaIni.get();
LocalDate fin = fechaFin.get();
if (ini == null || fin == null) {
    JOptionPane.showMessageDialog(this,
            "Selecciona primero el rango de fechas para nombrar el archivo.",
            "Aviso", JOptionPane.WARNING_MESSAGE);
    return;
}

String fname = "ReporteVentas_" + ini.format(DF) + "_a_" + fin.format(DF);

        ExportadorCSV.guardarListaCSV(
        rows, fname,
        "asesor", "folio", "cliente", "fecha", "tipo", "status", "total", "saldo",
        "codigo", "articulo", "marca", "modelo", "talla", "color", "fechaRegistroArticulo", "costoArticulo",
        "precio", "descuento", "subtotal"
        );


        JOptionPane.showMessageDialog(this, "CSV generado correctamente.");
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "Error al exportar CSV: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    // ===================== Carga de combos =====================

private void cargarAsesores() {
    cbVendIni.removeAllItems();
    cbVendFin.removeAllItems();
    mapaAsesores.clear();

    try {
        AsesorDAO dao = new AsesorDAO();
        List<Asesor> lista = dao.listarActivosDetalle();
        for (Asesor a : lista) {
            AsesorItem item = new AsesorItem(a.getNumeroEmpleado(), a.getNombreCompleto());
            cbVendIni.addItem(item);
            cbVendFin.addItem(item);

            // para la tabla
            if (a.getNumeroEmpleado() != null) {
                mapaAsesores.put(a.getNumeroEmpleado(), a.getNombreCompleto());
            }
        }
        if (cbVendIni.getItemCount() > 0) {
            cbVendIni.setSelectedIndex(0);
            cbVendFin.setSelectedIndex(cbVendFin.getItemCount() - 1);
        }
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "Error cargando asesores: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}
private void cargarTiposArticulo() {
    cbTipoArticulo.removeAllItems();
    cbTipoArticulo.addItem(""); // opción vacía

    String sql = "SELECT DISTINCT articulo FROM Nota_Detalle ORDER BY articulo";
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            String art = ns(rs.getString(1));
            if (!art.isEmpty()) {
                cbTipoArticulo.addItem(art);
            }
        }

        if (cbTipoArticulo.getItemCount() > 1) {
            cbTipoArticulo.setSelectedIndex(1);
        }
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "Error cargando tipos de artículo: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}


    private void cargarClientes() {
        cbCliIni.removeAllItems();
        cbCliFin.removeAllItems();
        String sql = "SELECT telefono1, nombre, apellido_paterno, apellido_materno FROM Clientes ORDER BY telefono1";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String tel = rs.getString("telefono1");
                String nombre = ns(rs.getString("nombre"));
                String apPat = ns(rs.getString("apellido_paterno"));
                String apMat = ns(rs.getString("apellido_materno"));
                String full = (nombre + " " + apPat + " " + apMat).trim().replaceAll("\\s+", " ");
                ClienteItem item = new ClienteItem(tel, full);
                cbCliIni.addItem(item);
                cbCliFin.addItem(item);
            }

            if (cbCliIni.getItemCount() > 0) {
                cbCliIni.setSelectedIndex(0);
                cbCliFin.setSelectedIndex(cbCliFin.getItemCount() - 1);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error cargando clientes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== Utilidades =====================

    private Integer getNumeroNotaSeleccionada() {
        int vr = tbNotas.getSelectedRow();
        if (vr < 0) return null;
        int mr = tbNotas.convertRowIndexToModel(vr);
        Object v = modelNotas.getValueAt(mr, 0);
        if (!(v instanceof Integer)) return null;
        return (Integer) v;
    }

    private static void ocultarColumnaVista(JTable t, int viewIndex) {
        if (viewIndex < 0 || viewIndex >= t.getColumnModel().getColumnCount()) return;
        TableColumnModel cm = t.getColumnModel();
        cm.removeColumn(cm.getColumn(viewIndex));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static Double n(Double d) {
        return d == null ? 0.0 : d;
    }

    private static String ns(String s) {
        return s == null ? "" : s.trim();
    }

    private static Double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    // ========= Campo de fecha con botón calendario (abre DayPopup) =========
    // ========= Campo de fecha con botón calendario =========
private static class DateField {
    private LocalDate date;
    private final JTextField txt = new JTextField(10);
    private final JButton bt = new JButton("📅");

    DateField() {
        txt.setHorizontalAlignment(SwingConstants.CENTER);
        ((AbstractDocument) txt.getDocument()).setDocumentFilter(new DateMaskFilter(10));
        bt.setMargin(new Insets(0, 4, 0, 4));
        bt.addActionListener(_e -> openPicker());
    }

    JPanel panel() {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.add(txt, BorderLayout.CENTER);
        p.add(bt, BorderLayout.EAST);
        return p;
    }

    void set(LocalDate d) {
        this.date = d;
        txt.setText(d == null ? "" : d.format(DF));
    }

    LocalDate get() {
        String s = txt.getText().trim();
        if (!s.isEmpty()) {
            try {
                date = LocalDate.parse(s, DF);
            } catch (Exception ignore) {
                // si escribe basura, simplemente dejamos el último valor válido
            }
        }
        return date;
    }

    private void openPicker() {
        LocalDate base = (get() == null) ? LocalDate.now() : get();
        DayPopup dp = new DayPopup(base, this::set);
        dp.show(bt, 0, bt.getHeight());
    }
}

private static class DateMaskFilter extends DocumentFilter {
    private final int maxLen;
    DateMaskFilter(int maxLen) { this.maxLen = maxLen; }

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        if (string == null) return;
        String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
        String next = cur.substring(0, offset) + string + cur.substring(offset);
        if (isValid(next)) super.insertString(fb, offset, string, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
        String next = cur.substring(0, offset) + (text == null ? "" : text) + cur.substring(offset + length);
        if (isValid(next)) super.replace(fb, offset, length, text, attrs);
    }

    private boolean isValid(String s) {
        return s.length() <= maxLen && s.matches("[0-9\\-]*");
    }
}

// ========= Calendario emergente de 1 mes =========
private static class DayPopup extends JPopupMenu {
    private YearMonth ym;
    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
    private final Consumer<LocalDate> onPick;

    DayPopup(LocalDate initial, Consumer<LocalDate> onPick) {
        LocalDate base = (initial == null) ? LocalDate.now() : initial;
        this.ym = YearMonth.of(base.getYear(), base.getMonth());
        this.onPick = onPick;

        setLayout(new BorderLayout(6, 6));

        // header
        JPanel header = new JPanel(new BorderLayout());
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        prev.addActionListener(_e -> { ym = ym.minusMonths(1); refresh(); });
        next.addActionListener(_e -> { ym = ym.plusMonths(1); refresh(); });

        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(prev, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // nombres de días
        JPanel dow = new JPanel(new GridLayout(1, 7, 4, 4));
        DayOfWeek[] order = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        for (DayOfWeek d : order) {
            JLabel l = new JLabel(d.getDisplayName(TextStyle.SHORT, ES_MX), SwingConstants.CENTER);
            dow.add(l);
        }
        add(dow, BorderLayout.CENTER);
        add(grid, BorderLayout.SOUTH);

        refresh();
    }

    private void refresh() {
        String mes = ym.getMonth().getDisplayName(TextStyle.FULL, ES_MX);
        mes = mes.substring(0, 1).toUpperCase(ES_MX) + mes.substring(1);
        title.setText(mes + " " + ym.getYear());

        grid.removeAll();

        int firstDow = ym.atDay(1).getDayOfWeek().getValue(); // 1..7 (lun..dom)
        int blanks = (firstDow == 7) ? 6 : firstDow - 1;
        for (int i = 0; i < blanks; i++) grid.add(new JLabel(""));

        int len = ym.lengthOfMonth();
        for (int d = 1; d <= len; d++) {
            int day = d;
            JButton b = new JButton(String.valueOf(day));
            b.addActionListener(_e -> {
                LocalDate pick = ym.atDay(day);
                if (onPick != null) onPick.accept(pick);
                setVisible(false);
            });
            grid.add(b);
        }

        revalidate();
        repaint();
    }
}

}
