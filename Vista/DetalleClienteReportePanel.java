package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Utilidades.TelefonosUI;
import Controlador.FormasPagoDAO;
import Controlador.ExportadorCSV;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Detalle de cliente:
 *  - Arriba: campo Teléfono + botón Buscar.
 *  - Centro: Split vertical con:
 *       (a) Tabla de "Datos del cliente".
 *       (b) Tabla de "Operaciones del cliente".
 *  - Abajo: Split horizontal con:
 *       (c) "Detalle de la nota seleccionada" (productos)
 *       (d) "Fechas de la nota" (evento/entrega por renglones; mixta o uniforme)
 */
public class DetalleClienteReportePanel extends JPanel {

    private final JTextField txtTel = new JTextField();
    private final JButton btBuscar = new JButton("Buscar");
    private final JButton btExportar = new JButton("Exportar CSV");

    // ===== Datos del cliente (clave/valor) =====
    private final DefaultTableModel modelInfo = new DefaultTableModel(
            new String[]{"Campo", "Valor"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbInfo = new JTable(modelInfo);

    // ===== Operaciones del cliente =====
    private final DefaultTableModel modelNotas = new DefaultTableModel(
            new String[]{"# Nota", "Tipo", "Folio", "Fecha", "Total", "Saldo", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Integer.class;
                case 3 -> String.class; // fecha formateada
                case 4, 5 -> Double.class;
                default -> String.class;
            };
        }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    private static void ocultarColumnaVista(JTable t, int viewIndex) {
        if (viewIndex < 0 || viewIndex >= t.getColumnModel().getColumnCount()) return;
        TableColumnModel cm = t.getColumnModel();
        cm.removeColumn(cm.getColumn(viewIndex));
    }

    // ===== Detalle de la nota seleccionada =====
    private final DefaultTableModel modelDet = new DefaultTableModel(
            new String[]{"Código", "Artículo", "Marca", "Modelo", "Talla", "Color", "Precio", "%Desc", "Subtotal"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return (c == 0) ? Integer.class : (c >= 6 ? Double.class : String.class);
        }
    };
    private final JTable tbDet = new JTable(modelDet);

    // ===== Fechas específicas de la nota =====
    private final DefaultTableModel modelFechasNota = new DefaultTableModel(
            new String[]{"Campo", "Valor"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbFechasNota = new JTable(modelFechasNota);

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DetalleClienteReportePanel() {
        setLayout(new BorderLayout());

        TelefonosUI.instalar(txtTel, 10);

        // --------- Encabezado ----------
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        txtTel.setColumns(22);
        north.add(new JLabel("Teléfono:"));
        north.add(txtTel);
        north.add(btBuscar);
        north.add(btExportar);
        add(north, BorderLayout.NORTH);

        // --------- Panel superior: info cliente + historial ----------
        tbInfo.setRowHeight(22);
        JScrollPane spInfo = new JScrollPane(tbInfo);
        spInfo.setBorder(BorderFactory.createTitledBorder("Datos del cliente"));

        tbNotas.setRowHeight(22);
        JScrollPane spNotas = new JScrollPane(tbNotas);
        spNotas.setBorder(BorderFactory.createTitledBorder("Operaciones del cliente"));

        // Ocultar la columna # Nota solo en la vista (se seguirá leyendo desde el modelo)
        ocultarColumnaVista(tbNotas, 0);

        JSplitPane splitTop = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spInfo, spNotas);
        splitTop.setResizeWeight(0.38);

        // --------- Panel inferior: detalle + fechas de nota ----------
        tbDet.setRowHeight(22);
        JScrollPane spDet = new JScrollPane(tbDet);
        spDet.setBorder(BorderFactory.createTitledBorder("Detalle de la nota seleccionada"));

        tbFechasNota.setRowHeight(22);
        JScrollPane spFechas = new JScrollPane(tbFechasNota);
        spFechas.setPreferredSize(new Dimension(340, 140));
        spFechas.setBorder(BorderFactory.createTitledBorder("Fechas de la nota"));

        JSplitPane splitBottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, spDet, spFechas);
        splitBottom.setResizeWeight(0.78);

        // --------- Contenedor principal (arriba/abajo) ----------
        JSplitPane splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitTop, splitBottom);
        splitMain.setResizeWeight(0.56);
        add(splitMain, BorderLayout.CENTER);

        // Eventos
        btBuscar.addActionListener(_e -> buscar());
        btExportar.addActionListener(_e -> {
            if (Utilidades.SeguridadUI.pedirYValidarClave(this)) {
                exportarCSVCliente();
            }
        });

        txtTel.addActionListener(_e -> buscar());
        tbNotas.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) cargarDetalleYFechasDeNotaSeleccionada();
        });
    }

    private void limpiar() {
        modelInfo.setRowCount(0);
        modelNotas.setRowCount(0);
        modelDet.setRowCount(0);
        modelFechasNota.setRowCount(0);
    }

private void buscar() {
    String tel = Utilidades.TelefonosUI.soloDigitos(txtTel.getText());
    if (tel.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Captura el teléfono del cliente.", "Atención",
                JOptionPane.WARNING_MESSAGE);
        return;
    }
    limpiar();

    // === 1) Datos completos del cliente (todas las columnas) ===
    try {
        Map<String, String> detalle = new clienteDAO().detalleGenericoPorTelefono(tel);
        if (detalle == null) {
            modelInfo.addRow(new Object[]{"—", "Cliente no encontrado"});
        } else {
            for (Map.Entry<String, String> e : detalle.entrySet()) {
                modelInfo.addRow(new Object[]{prettify(e.getKey()), e.getValue()});
            }
        }
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Error consultando cliente: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

 // === 2) Historial de operaciones (todas las notas del cliente) ===
try {
    NotasDAO ndao = new NotasDAO();
    List<NotasDAO.NotaResumen> list = ndao.listarNotasPorTelefonoResumen(tel);

    for (NotasDAO.NotaResumen r : list) {
        String f = (r.fecha == null)
                ? ""
                : r.fecha.format(DF);

        double total = (r.total == null ? 0.0 : r.total);
        double saldo = (r.saldo == null ? 0.0 : r.saldo);

        modelNotas.addRow(new Object[]{
                r.numero,
                r.tipo,
                nullToEmpty(r.folio),
                f,
                total,
                saldo,
                nullToEmpty(r.status)
        });
    }

    if (list.isEmpty()) {
        modelNotas.addRow(new Object[]{"—", "—", "—", "—", 0.0, 0.0, "Sin registros"});
    } else {
        tbNotas.setRowSelectionInterval(0, 0);
    }
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this, "Error cargando operaciones: " + ex.getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
}
}

/** Carga productos, fechas y formas de pago de la nota seleccionada. */
private void cargarDetalleYFechasDeNotaSeleccionada() {
    modelDet.setRowCount(0);
    modelFechasNota.setRowCount(0);

    int viewRow = tbNotas.getSelectedRow();
    if (viewRow < 0) return;
    int row = tbNotas.convertRowIndexToModel(viewRow);

    Object numObj = modelNotas.getValueAt(row, 0);
    if (!(numObj instanceof Integer numeroNota)) return;

    // TIPO de la nota (col 1 del MODELO)
    String tipo = String.valueOf(modelNotas.getValueAt(row, 1));
    tipo = (tipo == null) ? "" : tipo.trim().toUpperCase();

    NotasDAO dao = new NotasDAO();
    FormasPagoDAO fdao = new FormasPagoDAO();   // <-- usar FormasPagoDAO aquí

    try {
        // ---------- Productos ----------
        List<Modelo.NotaDetalle> dets = dao.listarDetalleDeNota(numeroNota);
        for (Modelo.NotaDetalle d : dets) {
            modelDet.addRow(new Object[] {
                    d.getCodigoArticulo(),
                    safe(d.getArticulo()),
                    safe(d.getMarca()),
                    safe(d.getModelo()),
                    safe(d.getTalla()),
                    safe(d.getColor()),
                    z(d.getPrecio()),
                    z(d.getDescuento()),
                    z(d.getSubtotal())
            });
        }

        // ---------- Fechas de la nota ----------
        Set<LocalDate> ev = new LinkedHashSet<>();
        Set<LocalDate> en = new LinkedHashSet<>();
        for (Modelo.NotaDetalle d : dets) {
            Integer id = d.getId();
            if (id == null) continue;
            NotasDAO.FechasRenglon f = dao.leerFechasDeRenglon(id);
            if (f != null) {
                if (f.fechaEvento  != null) ev.add(f.fechaEvento);
                if (f.fechaEntrega != null) en.add(f.fechaEntrega);
            }
        }

        String evTxt = formateaConMixto(ev);
        String enTxt = formateaConMixto(en);

        String fechaEventoCliente  = valorInfo("Fecha evento");
        String fechaEntregaCliente = valorInfo("Fecha entrega");
        if (ev.size() == 1 && !isBlank(fechaEventoCliente)) {
            String unica = DF.format(ev.iterator().next());
            if (!unica.equals(normalizaFecha(fechaEventoCliente))) {
                evTxt += "   (≠ cliente: " + normalizaFecha(fechaEventoCliente) + ")";
            }
        }
        if (en.size() == 1 && !isBlank(fechaEntregaCliente)) {
            String unica = DF.format(en.iterator().next());
            if (!unica.equals(normalizaFecha(fechaEntregaCliente))) {
                enTxt += "   (≠ cliente: " + normalizaFecha(fechaEntregaCliente) + ")";
            }
        }

        // ---------- FORMAS DE PAGO ----------
        try {
            FormasPagoDAO.FormasPagoRow fp = fdao.obtenerPorNota(numeroNota);
            String resumenPagos = formatearPagos(fp);   // usa tu helper de abajo
            if (!"—".equals(resumenPagos)) {
                modelFechasNota.addRow(new Object[] {
                        "Pagos (formas/montos)", resumenPagos
                });
            }
        } catch (Exception exPagos) {
            // si algo truena aquí, no tiramos el panel; solo no mostramos pagos
        }

        // ---------- Relación de ABONO con CR ----------
        if ("AB".equals(tipo)) {
            String folioCR = obtenerFolioCreditoAbonado(numeroNota);
            folioCR = (folioCR == null) ? "" : folioCR.trim();
            modelFechasNota.addRow(new Object[] {
                    "Abona a (folio)",
                    folioCR.isEmpty() ? "—" : folioCR
            });
        }

        // ---------- Fechas en la tabla ----------
        modelFechasNota.addRow(new Object[] { "Fecha evento (nota)",  evTxt });
        modelFechasNota.addRow(new Object[] { "Fecha entrega (nota)", enTxt });

    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this,
                "Error cargando detalle/fechas: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    // ---------- helpers SQL ----------

    private String obtenerFolioCreditoAbonado(int numeroNotaAbono) {
        final String sql =
                "SELECT COALESCE(nc.folio, CONCAT('CR ', nc.numero_nota)) AS folio_cr " +
                        "FROM Notas na " +
                        "LEFT JOIN Notas nc ON nc.numero_nota = na.nota_relacionada AND nc.tipo='CR' " +
                        "WHERE na.numero_nota = ? AND na.tipo = 'AB' " +
                        "LIMIT 1";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNotaAbono);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString("folio_cr");
                    return (v == null) ? "" : v.trim();
                }
            }
        } catch (Exception ignore) { }
        return "";
    }

    /** Devuelve el saldo actual del crédito al que abona este AB, o null si no se encuentra. */
    private Double obtenerSaldoCreditoDeAbono(int numeroNotaAbono) {
        final String sql =
                "SELECT nc.saldo " +
                        "FROM Notas na " +
                        "LEFT JOIN Notas nc ON nc.numero_nota = na.nota_relacionada AND nc.tipo='CR' " +
                        "WHERE na.numero_nota = ? AND na.tipo = 'AB' " +
                        "LIMIT 1";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, numeroNotaAbono);

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        java.math.BigDecimal bd = rs.getBigDecimal(1);
                        return (bd == null ? null : bd.doubleValue());
                    } catch (Throwable t) {
                        double v = rs.getDouble(1);
                        return rs.wasNull() ? null : v;
                    }
                }
            }
        } catch (Exception ignore) {
            // si truena, simplemente no sobreescribimos el saldo
        }
        return null;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String prettify(String col) {
        String s = col == null ? "" : col.trim().replace('_', ' ');
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static Double z(Double d) { return d == null ? 0.0 : d; }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** Lee del grid superior “Datos del cliente” el valor de un campo por nombre bonito (ej. "Fecha evento"). */
    private String valorInfo(String campoBonito) {
        for (int i = 0; i < modelInfo.getRowCount(); i++) {
            Object k = modelInfo.getValueAt(i, 0);
            if (campoBonito.equals(String.valueOf(k))) {
                Object v = modelInfo.getValueAt(i, 1);
                return v == null ? "" : String.valueOf(v);
            }
        }
        return "";
    }

    /** "2026-02-28 00:00:00" o "28/02/2026" -> "yyyy-MM-dd" si es posible. */
    private String normalizaFecha(String s) {
        if (isBlank(s)) return "";
        s = s.trim();
        try { // yyyy-MM-dd
            LocalDate d = LocalDate.parse(s.substring(0, 10));
            return DF.format(d);
        } catch (Exception ignore) {}
        try { // dd/MM/yyyy
            String[] p = s.split("[/]");
            if (p.length == 3) {
                int dd = Integer.parseInt(p[0]);
                int mm = Integer.parseInt(p[1]);
                int yy = Integer.parseInt(p[2]);
                return DF.format(LocalDate.of(yy, mm, dd));
            }
        } catch (Exception ignore) {}
        return s;
    }

    /** Devuelve "—" si vacío; si 1 fecha -> yyyy-MM-dd; si >1 -> "Mixtas (n)". */
    private String formateaConMixto(Set<LocalDate> fechas) {
        if (fechas.isEmpty()) return "—";
        if (fechas.size() == 1) return DF.format(fechas.iterator().next());
        return "Mixtas (" + fechas.size() + ")";
    }

    /**
     * Exporta 2 archivos CSV:
     *  (a) Cliente_INFO_<tel>.csv  -> "Campo","Valor" (todo lo que muestras en la tabla superior)
     *  (b) Cliente_DETALLE_<tel>.csv -> una fila por renglón de detalle (o 1 fila si no hay detalle),
     *      con pagos, DV, "abona a", fechas (uniforme/mixta) y banderas de diferencia con cliente.
     */
    private void exportarCSVCliente() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTel.getText());

        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Captura el teléfono y da Buscar antes de exportar.", "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            // ============ (a) CSV de INFO ============
            List<CsvInfo> infoRows = new ArrayList<>();
            for (int i = 0; i < modelInfo.getRowCount(); i++) {
                String campo = String.valueOf(modelInfo.getValueAt(i, 0));
                String valor = String.valueOf(modelInfo.getValueAt(i, 1));
                infoRows.add(new CsvInfo(campo, valor));
            }
            String fnameInfo = "Cliente_INFO_" + tel;
            ExportadorCSV.guardarListaCSV(infoRows, fnameInfo, "campo", "valor");

            // ============ (b) CSV de DETALLE ============
            NotasDAO ndao = new NotasDAO();
            FormasPagoDAO fdao = new FormasPagoDAO();

            // Nombre del cliente (opcional)
            String nombre = "";
            try {
                var cr = new clienteDAO().buscarResumenPorTelefono(tel);
                if (cr != null && cr.getNombreCompleto() != null) nombre = cr.getNombreCompleto();
            } catch (Exception ignore) {}

            // Para comparar fechas con las del cliente (tomadas de la tabla superior)
            String fechaEventoCliente = normalizaFecha(valorInfo("Fecha evento"));
            String fechaEntregaCliente = normalizaFecha(valorInfo("Fecha entrega"));

            List<NotasDAO.NotaResumen> notas = ndao.listarNotasPorTelefonoResumen(tel);

            List<CsvDetalle> out = new ArrayList<>();

            for (NotasDAO.NotaResumen r : notas) {
                int numero = r.numero;
                String tipo = safe(r.tipo);
                String folio = safe(r.folio);
                String fecha = (r.fecha == null ? "" : DF.format(r.fecha));
                String status = safe(r.status);
                Double total = z(r.total);
                Double saldo = z(r.saldo);

                // Pagos
                Double pef = 0.0, pcr = 0.0, pdb = 0.0, pam = 0.0, ptr = 0.0, pdep = 0.0, pdv = 0.0;
                String refDV = "";
                try {
                    FormasPagoDAO.FormasPagoRow fp = fdao.obtenerPorNota(numero);
                    if (fp != null) {
                        pef = z(fp.efectivo);
                        pcr = z(fp.tarjetaCredito);
                        pdb = z(fp.tarjetaDebito);
                        pam = z(fp.americanExpress);
                        ptr = z(fp.transferencia);
                        pdep = z(fp.deposito);
                        pdv = z(fp.devolucion);
                        refDV = (fp.referenciaDV == null ? "" : fp.referenciaDV);
                    }
                } catch (Exception ignore) {}

                // Detalle renglones
                var dets = ndao.listarDetalleDeNota(numero);

                // Fechas por nota (uniforme/mixta) + comparación contra cliente
                java.util.Set<java.time.LocalDate> ev = new java.util.LinkedHashSet<>();
                java.util.Set<java.time.LocalDate> en = new java.util.LinkedHashSet<>();
                for (var d : dets) {
                    Integer id = d.getId();
                    if (id == null) continue;
                    var f = ndao.leerFechasDeRenglon(id);
                    if (f != null) {
                        if (f.fechaEvento != null) ev.add(f.fechaEvento);
                        if (f.fechaEntrega != null) en.add(f.fechaEntrega);
                    }
                }
                String eventoNota = formateaConMixto(ev);
                String entregaNota = formateaConMixto(en);
                boolean eventoDifiere = false;
                boolean entregaDifiere = false;
                if (ev.size() == 1 && !isBlank(fechaEventoCliente)) {
                    String unica = DF.format(ev.iterator().next());
                    eventoDifiere = !unica.equals(fechaEventoCliente);
                }
                if (en.size() == 1 && !isBlank(fechaEntregaCliente)) {
                    String unica = DF.format(en.iterator().next());
                    entregaDifiere = !unica.equals(fechaEntregaCliente);
                }

                if (dets.isEmpty()) {
                    CsvDetalle row = new CsvDetalle();
                    row.telefono = tel;
                    row.nombre = nombre;
                    row.numeroNota = numero;
                    row.folio = folio;
                    row.fecha = fecha;
                    row.tipo = tipo;
                    row.status = status;
                    row.total = total;
                    row.saldo = saldo;
                    row.p_efectivo = pef;
                    row.p_tcredito = pcr;
                    row.p_tdebito = pdb;
                    row.p_amex = pam;
                    row.p_transfer = ptr;
                    row.p_deposito = pdep;
                    row.p_devolucion = pdv;
                    row.p_ref_dv = refDV;
                    row.folioCreditoAbonado = obtenerFolioCreditoAbonado(numero);
                    row.eventoNota = eventoNota;
                    row.entregaNota = entregaNota;
                    row.eventoDifiereCliente = eventoDifiere ? "SI" : "NO";
                    row.entregaDifiereCliente = entregaDifiere ? "SI" : "NO";
                    out.add(row);
                } else {
                    for (var d : dets) {
                        CsvDetalle row = new CsvDetalle();
                        row.telefono = tel;
                        row.nombre = nombre;
                        row.numeroNota = numero;
                        row.folio = folio;
                        row.fecha = fecha;
                        row.tipo = tipo;
                        row.status = status;
                        row.total = total;
                        row.saldo = saldo;

                        row.codigo = d.getCodigoArticulo();
                        row.articulo = safe(d.getArticulo());
                        row.marca = safe(d.getMarca());
                        row.modelo = safe(d.getModelo());
                        row.talla = safe(d.getTalla());
                        row.color = safe(d.getColor());
                        row.precio = z(d.getPrecio());
                        row.descuento = z(d.getDescuento());
                        row.subtotal = z(d.getSubtotal());

                        row.p_efectivo = pef;
                        row.p_tcredito = pcr;
                        row.p_tdebito = pdb;
                        row.p_amex = pam;
                        row.p_transfer = ptr;
                        row.p_deposito = pdep;
                        row.p_devolucion = pdv;
                        row.p_ref_dv = refDV;

                        row.folioCreditoAbonado = obtenerFolioCreditoAbonado(numero);

                        row.eventoNota = eventoNota;
                        row.entregaNota = entregaNota;
                        row.eventoDifiereCliente = eventoDifiere ? "SI" : "NO";
                        row.entregaDifiereCliente = entregaDifiere ? "SI" : "NO";

                        out.add(row);
                    }
                }
            }

            String fnameDet = "Cliente_DETALLE_" + tel;
            ExportadorCSV.guardarListaCSV(
                    out, fnameDet,
                    // — columnas del cliente —
                    "telefono", "nombre",
                    // — nota —
                    "numeroNota", "folio", "fecha", "tipo", "status", "total", "saldo",
                    // — detalle —
                    "codigo", "articulo", "marca", "modelo", "talla", "color", "precio", "descuento", "subtotal",
                    // — pagos —
                    "p_efectivo", "p_tcredito", "p_tdebito", "p_amex", "p_transfer", "p_deposito", "p_devolucion", "p_ref_dv",
                    // — relaciones/fechas —
                    "folioCreditoAbonado", "eventoNota", "entregaNota", "eventoDifiereCliente", "entregaDifiereCliente"
            );

            JOptionPane.showMessageDialog(this, "CSV(s) generados correctamente.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** POJO para CSV de info del cliente (tabla superior). */
    public static class CsvInfo {
        public String campo;
        public String valor;
        public CsvInfo() {}
        public CsvInfo(String c, String v) { this.campo = c; this.valor = v; }
    }

    /** POJO para CSV detallado por renglón de nota. */
    public static class CsvDetalle {
        // Cliente
        public String telefono;
        public String nombre;

        // Nota
        public Integer numeroNota;
        public String folio;
        public String fecha;
        public String tipo;
        public String status;
        public Double total;
        public Double saldo;

        // Detalle
        public String codigo;
        public String articulo;
        public String marca;
        public String modelo;
        public String talla;
        public String color;
        public Double precio;
        public Double descuento;
        public Double subtotal;

        // Pagos
        public Double p_efectivo;
        public Double p_tcredito;
        public Double p_tdebito;
        public Double p_amex;
        public Double p_transfer;
        public Double p_deposito;
        public Double p_devolucion;
        public String p_ref_dv;

        // Relaciones / Fechas
        public String folioCreditoAbonado;   // para AB
        public String eventoNota;           // "—", "yyyy-MM-dd" o "Mixtas (n)"
        public String entregaNota;          // idem
        public String eventoDifiereCliente; // "SI"/"NO"
        public String entregaDifiereCliente;// "SI"/"NO"
    }

    /** Convierte un FormasPagoRow en texto compacto "EF $x, TC $y, DV $z". */
    private String formatearPagos(FormasPagoDAO.FormasPagoRow fp) {
        if (fp == null) return "—";

        java.util.List<String> partes = new java.util.ArrayList<>();

        agregarPago(partes, "EF",  fp.efectivo);
        agregarPago(partes, "TC",  fp.tarjetaCredito);
        agregarPago(partes, "TD",  fp.tarjetaDebito);
        agregarPago(partes, "AMEX", fp.americanExpress);
        agregarPago(partes, "TR",  fp.transferencia);
        agregarPago(partes, "DEP", fp.deposito);
        agregarPago(partes, "DV",  fp.devolucion);

        if (fp.devolucion != null && Math.abs(fp.devolucion) >= 0.005
                && fp.referenciaDV != null && !fp.referenciaDV.isBlank()) {
            partes.add("DV ref: " + fp.referenciaDV.trim());
        }

        if (partes.isEmpty()) return "—";
        return String.join(", ", partes);
    }

    private void agregarPago(java.util.List<String> partes, String etiqueta, Double monto) {
        if (monto == null) return;
        if (Math.abs(monto) < 0.005) return;  // prácticamente cero
        partes.add(etiqueta + " $" + String.format("%,.2f", monto));
    }
}
