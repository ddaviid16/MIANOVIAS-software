package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Utilidades.CatalogoCFDI;
import Utilidades.TelefonosUI;
import Controlador.FormasPagoDAO;
import Controlador.ExportadorCSV;
import Controlador.FacturaDatosDAO;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.print.PageFormat;
import java.awt.print.Printable;

import Modelo.ClienteResumen;
import Modelo.cliente;


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
    private final JButton btBuscarNombre = new JButton("Buscar por nombre");
    private final JButton btExportar = new JButton("Exportar CSV");
        // Estado de las notas del cliente cargado
    private List<NotasDAO.NotaResumen> notasCliente = new ArrayList<>();
    private double saldoGlobalCreditos = 0.0;

    // Botón para imprimir
    private final JButton btImprimir = new JButton("Imprimir hoja detalle");

    // Saldo global fuera de la tabla
    private final JLabel lbSaldoGlobal = new JLabel("Saldo total: $ 0.00");


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
        new String[]{"Código", "Artículo", "Marca", "Modelo", "Talla", "Color",
                     "Precio", "%Desc", "Subtotal", "Status"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }

    @Override public Class<?> getColumnClass(int c) {
        // 6,7,8 son numéricos; el resto texto
        return switch (c) {
            case 6, 7, 8 -> Double.class;
            default -> String.class;
        };
    }
};


    private final JTable tbDet = new JTable(modelDet);

    // ===== Tabs inferiores: Detalle / Pagos / Cliente / Factura =====
private final JLabel lbAbonaA = new JLabel(" ");

private final DefaultTableModel modelPago = new DefaultTableModel(
        new String[]{"Efectivo","T. Crédito","T. Débito","AmEx","Transfer","Depósito","Devolución","Ref. DV"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
    @Override public Class<?> getColumnClass(int c) { return c == 7 ? String.class : Double.class; }
};
private final JTable tbPago = new JTable(modelPago);

// Tab de OBSEQUIOS (por nota seleccionada)
private final DefaultTableModel modelObsequios = new DefaultTableModel(
        new String[]{"Código", "Obsequio"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
};
private final JTable tbObsequios = new JTable(modelObsequios);


private final DefaultTableModel modelFactura = new DefaultTableModel(
        new String[]{"Campo","Valor"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
};
private final JTable tbFactura = new JTable(modelFactura);

private final JTabbedPane tabs = new JTabbedPane();

    // ===== Fechas específicas de la nota =====
    private final DefaultTableModel modelFechasNota = new DefaultTableModel(
            new String[]{"Campo", "Valor"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbFechasNota = new JTable(modelFechasNota);

    // Orden deseado para la tabla "Datos del cliente"
private static final String[] ORDEN_INFO = {
        "Nombre",
        "Apellido paterno",
        "Apellido materno",
        "Telefono1",
        "Telefono2",
        "Parentezco telefono2",
        "Fecha evento",
        "Fecha prueba1",
        "Hora prueba1",
        "Asesora prueba1",
        "Observacion prueba1",
        "Fecha prueba2",
        "Hora prueba2",
        "Asesora prueba2",
        "Observacion prueba2",
        "Fecha entrega",
        "Hora entrega",
        "Asesora entrega",
        "Observacion entrega",
        "Fecha cita1",
        "Hora cita1",
        "Asesora cita1",
        "Observacion cita1",
        "Fecha cita2",
        "Hora cita2",
        "Asesora cita2",
        "Observacion cita2",
        "Busto",
        "Cintura",
        "Cadera",
        "Como se entero",
        "Lugar evento",
        "Situacion evento",
        "Status"
};

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
        north.add(btBuscarNombre);
        north.add(btExportar);
        north.add(btImprimir);
        add(north, BorderLayout.NORTH);

        // --------- Panel superior: info cliente + historial ----------
        tbInfo.setRowHeight(22);
        JScrollPane spInfo = new JScrollPane(tbInfo);

        // Panel contenedor con título y saldo arriba a la derecha
        JPanel panelInfo = new JPanel(new BorderLayout());
        panelInfo.setBorder(BorderFactory.createTitledBorder("Datos del cliente"));

        lbSaldoGlobal.setHorizontalAlignment(SwingConstants.RIGHT);
        lbSaldoGlobal.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 8));
        lbSaldoGlobal.setFont(lbSaldoGlobal.getFont().deriveFont(Font.BOLD));

        panelInfo.add(lbSaldoGlobal, BorderLayout.NORTH);
        panelInfo.add(spInfo, BorderLayout.CENTER);

        tbNotas.setRowHeight(22);
        JScrollPane spNotas = new JScrollPane(tbNotas);
        spNotas.setBorder(BorderFactory.createTitledBorder("Operaciones del cliente"));

        // Ocultar la columna # Nota solo en la vista (se seguirá leyendo desde el modelo)
        ocultarColumnaVista(tbNotas, 0);

        JSplitPane splitTop = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelInfo, spNotas);
        splitTop.setResizeWeight(0.38);


        // --------- Panel inferior: pestañas (Detalle/Pagos/Cliente/Factura) + Fechas ----------
        tbDet.setRowHeight(22);
        tbPago.setRowHeight(22);
        tbObsequios.setRowHeight(22);
        tbFactura.setRowHeight(22);        
        // Ocultar columna Status del detalle (sigue estando en el modelo)
        ocultarColumnaVista(tbDet, 9); // índice de "Status" en la vista

        // Pintar en rojo los renglones devueltos (status = 'C')
        instalarRendererDetalle();


        // --- Tab "Detalle"
        JScrollPane spDet = new JScrollPane(tbDet);
        spDet.setBorder(BorderFactory.createTitledBorder("Detalle de la nota seleccionada"));

        // --- Tab "Pagos" con rótulo "Abona a:"
        JPanel pagosPanel = new JPanel(new BorderLayout(6, 6));

        lbAbonaA.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));
        lbAbonaA.setFont(lbAbonaA.getFont().deriveFont(Font.BOLD));

        JPanel pagosTop = new JPanel(new BorderLayout(4, 4));
        pagosTop.add(lbAbonaA, BorderLayout.NORTH);

        JScrollPane spPagos = new JScrollPane(tbPago);
        spPagos.setPreferredSize(new Dimension(10, tbPago.getRowHeight() * 3 + 32));
        pagosTop.add(spPagos, BorderLayout.CENTER);

        pagosPanel.add(pagosTop, BorderLayout.NORTH);

        // --- Tab "Cliente"
        JScrollPane spObsequios = new JScrollPane(tbObsequios);
        spObsequios.setBorder(BorderFactory.createTitledBorder("Obsequios de la nota"));

        // --- Tab "Factura"
        JScrollPane spFactura = new JScrollPane(tbFactura);

        // --- Armar las pestañas
        tabs.addTab("Detalle", spDet);
        tabs.addTab("Pagos", pagosPanel);
        tabs.addTab("Obsequios", spObsequios);
        tabs.addTab("Factura", spFactura);

        // --- Fechas de la nota (lado derecho)
        tbFechasNota.setRowHeight(22);
        JScrollPane spFechas = new JScrollPane(tbFechasNota);
        spFechas.setPreferredSize(new Dimension(340, 140));
        spFechas.setBorder(BorderFactory.createTitledBorder("Fechas de la nota"));

        // Izquierda: tabs, derecha: fechas
        JSplitPane splitBottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, spFechas);
        splitBottom.setResizeWeight(0.78);


        // --------- Contenedor principal (arriba/abajo) ----------
        JSplitPane splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitTop, splitBottom);
        splitMain.setResizeWeight(0.56);
        add(splitMain, BorderLayout.CENTER);

        // Eventos
        btBuscar.addActionListener(_e -> buscar());
        btBuscarNombre.addActionListener(_e -> seleccionarClientePorApellido());  // ← NUEVO
        btExportar.addActionListener(_e -> {
            if (Utilidades.SeguridadUI.pedirYValidarClave(this)) {
                exportarCSVCliente();
            }
        });
        btImprimir.addActionListener(_e -> imprimirHojaDetalleCliente());


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

        modelPago.setRowCount(0);
        modelObsequios.setRowCount(0);
        modelFactura.setRowCount(0);
        lbAbonaA.setText(" ");
        lbSaldoGlobal.setText("Saldo total: $ 0.00");
    }


private void buscar() {
    String tel = Utilidades.TelefonosUI.soloDigitos(txtTel.getText());
    if (tel.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Captura el teléfono del cliente.", "Atención",
                JOptionPane.WARNING_MESSAGE);
        return;
    }
    limpiar();
    notasCliente.clear();
    saldoGlobalCreditos = 0.0;

    // === 1) Datos completos del cliente (todas las columnas) ===
    try {
        Map<String, String> detalle = new clienteDAO().detalleGenericoPorTelefono(tel);
        if (detalle == null) {
            modelInfo.addRow(new Object[]{"—", "Cliente no encontrado"});
        } else {
            // Primero llenamos la tabla con los nombres "bonitos"
            for (Map.Entry<String, String> e : detalle.entrySet()) {
                modelInfo.addRow(new Object[]{prettify(e.getKey()), e.getValue()});
            }
            // Y luego reordenamos las filas según el orden deseado
            reordenarTablaInfo();
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

        // Ordenar: más antigua primero, luego por número de nota
        list.sort(
                Comparator
                        .comparing((NotasDAO.NotaResumen r) -> r.fecha,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(r -> r.numero)
        );

                // guardamos la lista en memoria ya ordenada
        notasCliente.addAll(list);

        // Saldo global de créditos (CR) del cliente, directo de la BD
        saldoGlobalCreditos = obtenerSaldoGlobalCreditos(tel);



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

    // === 3) Saldo global del cliente (créditos) fuera de la tabla ===
    String txtSaldo = formatMoney(saldoGlobalCreditos);
    lbSaldoGlobal.setText("Saldo total: " + txtSaldo);


}


/** Carga productos, fechas y formas de pago de la nota seleccionada. */
private void cargarDetalleYFechasDeNotaSeleccionada() {
    modelDet.setRowCount(0);
    modelFechasNota.setRowCount(0);
    modelPago.setRowCount(0);
    modelFactura.setRowCount(0);
    lbAbonaA.setText(" ");


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
                    z(d.getSubtotal()),
                    safe(d.getStatus())  // <-- NUEVO, queda oculta
            });
        }

        // ---------- Obsequios de la nota ----------
        cargarObsequiosDeNota(numeroNota);
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
        FormasPagoDAO.FormasPagoRow fp = null;
        try {
            fp = fdao.obtenerPorNota(numeroNota);
            String resumenPagos = formatearPagos(fp);   // helper ya existente
            if (!"—".equals(resumenPagos)) {
                modelFechasNota.addRow(new Object[]{
                        "Pagos (formas/montos)", resumenPagos
                });
            }
        } catch (Exception exPagos) {
            // no tiramos el panel si falla
        }

        // Llenar el tab "Pagos"
        if (fp != null) {
            modelPago.addRow(new Object[]{
                    z(fp.efectivo),
                    z(fp.tarjetaCredito),
                    z(fp.tarjetaDebito),
                    z(fp.americanExpress),
                    z(fp.transferencia),
                    z(fp.deposito),
                    z(fp.devolucion),
                    (fp.referenciaDV == null ? "" : fp.referenciaDV.trim())
            });
        } else {
            modelPago.addRow(new Object[]{0.0,0.0,0.0,0.0,0.0,0.0,0.0,""});
        }

        // ---------- Relación de ABONO con CR ----------
        if ("AB".equals(tipo)) {
            String folioCR = obtenerFolioCreditoAbonado(numeroNota);
            folioCR = (folioCR == null) ? "" : folioCR.trim();

            modelFechasNota.addRow(new Object[]{
                    "Abona a (folio)",
                    folioCR.isEmpty() ? "—" : folioCR
            });

            lbAbonaA.setText(folioCR.isEmpty()
                    ? "Abona a: (sin relación)"
                    : "Abona a: " + folioCR);
        } else {
            lbAbonaA.setText(" ");
        }

        // ---------- Fechas en la tabla ----------
        modelFechasNota.addRow(new Object[]{"Fecha evento (nota)",  evTxt});
        modelFechasNota.addRow(new Object[]{"Fecha entrega (nota)", enTxt});

        // ---------- Factura ----------
        cargarFacturaTab(numeroNota);



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

private static String safeUpper(String s) {
    return (s == null) ? "" : s.trim().toUpperCase();
}

private static String formatMoney(double v) {
    return "$ " + String.format("%,.2f", v);
}
/** Línea para la tabla de COMPRAS del formato impreso. */
private static class CompraLinea {
    int numeroNota;
    String folio;
    LocalDate fecha;
    String codigoArticulo;
    String articulo;
    String modelo;
    String talla;
    String color;
    Double precio;
    Double descuento;
    Double precioPagar;
    boolean esDevolucion;    // ya no se usa para DV, pero lo dejo por compatibilidad
    String status;           // "D" devuelto, "C" nota cancelada, "" normal
}

/** Fila de la tabla de datos del encabezado (parte superior). */
private static class EncabezadoLinea {
    final String labelL;
    final String valueL;
    final String labelR;
    final String valueR;

    EncabezadoLinea(String labelL, String valueL, String labelR, String valueR) {
        this.labelL = labelL;
        this.valueL = valueL;
        this.labelR = labelR;
        this.valueR = valueR;
    }
}

/** Línea para la tabla de PAGOS / DEVOLUCIONES del formato impreso. */
private static class PagoLinea {
    int numeroNota;
    String folio;
    LocalDate fecha;
    String concepto;
    Double importe;
    Double saldo;
    boolean esDevolucion;   // <-- NUEVO: true solo para notas tipo DV
}

/** Devuelve la primera nota de crédito (tipo 'CR') del cliente, por fecha ascendente. */
private NotasDAO.NotaResumen elegirPrimerCredito(List<NotasDAO.NotaResumen> notas) {
    if (notas == null || notas.isEmpty()) return null;
    NotasDAO.NotaResumen primero = null;

    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;
        String tipo = safeUpper(r.tipo);
        if (!"CR".equals(tipo)) continue;

        if (primero == null) {
            primero = r;
        } else if (r.fecha != null) {
            if (primero.fecha == null || r.fecha.isBefore(primero.fecha)) {
                primero = r;
            }
        }
    }
    return primero;
}

/** Devuelve la nota "principal" del cliente (crédito con saldo, o la última CN/CR). */
private NotasDAO.NotaResumen elegirNotaPrincipal(List<NotasDAO.NotaResumen> notas) {
    if (notas == null || notas.isEmpty()) return null;
    NotasDAO.NotaResumen principal = null;

    // Primero: créditos con saldo pendiente
    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;
        String tipo = safeUpper(r.tipo);
        if (!"CR".equals(tipo)) continue;
        Double saldo = r.saldo;
        if (saldo == null || saldo <= 0.005) continue;
        if (principal == null ||
                (r.fecha != null && (principal.fecha == null || r.fecha.isAfter(principal.fecha)))) {
            principal = r;
        }
    }
    if (principal != null) return principal;

    // Si no hay créditos con saldo, última CN/CR
    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;
        String tipo = safeUpper(r.tipo);
        if (!"CN".equals(tipo) && !"CR".equals(tipo)) continue;
        if (principal == null ||
                (r.fecha != null && (principal.fecha == null || r.fecha.isAfter(principal.fecha)))) {
            principal = r;
        }
    }
    return principal;
}

/** Texto de folio para impresión: folio explícito o "TIPO numero". */
private String folioDeNota(NotasDAO.NotaResumen r) {
    if (r == null) return "";
    String folio = safe(r.folio);
    if (!folio.isEmpty()) return folio;
    return safe(r.tipo) + " " + r.numero;
}

/** Intenta parsear texto "yyyy-MM-dd" o "dd/MM/yyyy" a LocalDate. */
private LocalDate parseDate(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;
    try {
        return LocalDate.parse(s); // yyyy-MM-dd
    } catch (Exception ignore) {}
    try {
        String[] p = s.split("[/]");
        if (p.length == 3) {
            int dd = Integer.parseInt(p[0]);
            int mm = Integer.parseInt(p[1]);
            int yy = Integer.parseInt(p[2]);
            return LocalDate.of(yy, mm, dd);
        }
    } catch (Exception ignore) {}
    return null;
}

private String firstNonBlank(String... vals) {
    if (vals == null) return "";
    for (String v : vals) {
        if (v != null && !v.trim().isEmpty()) return v.trim();
    }
    return "";
}

private List<CompraLinea> construirCompras(NotasDAO ndao, List<NotasDAO.NotaResumen> notas) throws SQLException {
    List<CompraLinea> out = new ArrayList<>();
    if (notas == null) return out;

    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;

        String tipo       = safeUpper(r.tipo);
        String statusNota = safeUpper(r.status);

        // Solo contado y crédito; las devoluciones van en PAGOS
        if (!"CN".equals(tipo) && !"CR".equals(tipo)) continue;

        // NOTAS CANCELADAS: NO se muestran en la hoja
        if ("C".equals(statusNota)) continue;

        List<Modelo.NotaDetalle> dets = ndao.listarDetalleDeNota(r.numero);

        if (dets == null || dets.isEmpty()) {
            // Sin detalle: renglón resumen de la nota (solo si no está cancelada)
            CompraLinea c = new CompraLinea();
            c.numeroNota      = r.numero;
            c.folio           = folioDeNota(r);
            c.fecha           = r.fecha;
            c.codigoArticulo  = "";
            c.modelo          = "";
            c.talla           = "";
            c.color           = "";
            c.descuento       = 0.0;
            c.status          = "";   // nada de 'C' aquí

            c.articulo        = "Venta " + ("CN".equals(tipo) ? "contado" : "crédito");
            double total      = z(r.total);
            c.precio          = total;
            c.precioPagar     = total;
            c.esDevolucion    = false;

            out.add(c);
        } else {
            // Con detalle: una fila por renglón
            for (Modelo.NotaDetalle d : dets) {
                // Renglones cancelados: tampoco se muestran
                String stDet = safeUpper(d.getStatus());
                if ("C".equals(stDet)) {
                    continue;
                }

                CompraLinea c = new CompraLinea();
                c.numeroNota     = r.numero;
                c.folio          = folioDeNota(r);
                c.fecha          = r.fecha;
                c.codigoArticulo = String.valueOf(d.getCodigoArticulo());
                c.articulo       = safe(d.getArticulo());
                c.modelo         = safe(d.getModelo());
                c.talla          = safe(d.getTalla());
                c.color          = safe(d.getColor());
                c.descuento      = z(d.getDescuento());

                // Status visual: D devuelto, A activo
                String stFinal;
                if ("D".equals(stDet)) {
                    stFinal = "D";
                } else {
                    stFinal = "A";
                }
                c.status = stFinal;

                double precio = z(d.getPrecio());
                double sub    = z(d.getSubtotal());

                c.precio      = precio;
                c.precioPagar = sub;
                c.esDevolucion = false;

                out.add(c);
            }
        }
    }
    return out;
}

private List<PagoLinea> construirPagos(NotasDAO ndao, List<NotasDAO.NotaResumen> notas) throws SQLException {
    List<PagoLinea> out = new ArrayList<>();
    if (notas == null) return out;

    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;

        String tipo   = safeUpper(r.tipo);
        String status = safeUpper(r.status);

        // Movimientos cancelados: fuera de la impresión
        if ("C".equals(status)) continue;

        FormasPagoDAO.FormasPagoRow fp = null;
        try {
            fp = new FormasPagoDAO().obtenerPorNota(r.numero);
        } catch (Exception ignore) {
            fp = null;
        }

        if ("CR".equals(tipo) || "AB".equals(tipo)) {
            double pagoSinDV = 0.0;
            double montoDVAplicada = 0.0;
            String referenciaDV = "";

            if (fp != null) {
                pagoSinDV =
                        z(fp.efectivo) +
                        z(fp.tarjetaCredito) +
                        z(fp.tarjetaDebito) +
                        z(fp.americanExpress) +
                        z(fp.transferencia) +
                        z(fp.deposito);
                montoDVAplicada = z(fp.devolucion);
                referenciaDV = safe(fp.referenciaDV);
            } else {
                // Fallback cuando no hay formas de pago guardadas para no perder el movimiento.
                pagoSinDV = z(r.total);
            }

            if (pagoSinDV > 0.005) {
                PagoLinea p = new PagoLinea();
                p.numeroNota = r.numero;
                p.folio      = folioDeNota(r);
                p.fecha      = r.fecha;
                if ("CR".equals(tipo)) {
                    p.concepto = "Pago inicial de la nota";
                    p.saldo    = null;
                } else {
                    String folioCR = obtenerFolioCreditoAbonado(r.numero);
                    p.concepto = (folioCR == null || folioCR.isBlank())
                            ? "Abono a crédito"
                            : "Abono a " + folioCR;
                    p.saldo = z(r.saldo);
                }
                p.importe = pagoSinDV;
                p.esDevolucion = false;
                out.add(p);
            }

            if (montoDVAplicada > 0.005) {
                PagoLinea p = new PagoLinea();
                p.numeroNota = r.numero;
                p.folio      = folioDeNota(r);
                p.fecha      = r.fecha;
                p.concepto   = referenciaDV.isBlank()
                        ? "Devolución aplicada como pago"
                        : "Devolución aplicada como pago (" + referenciaDV + ")";
                p.importe      = -Math.abs(montoDVAplicada);
                p.saldo        = null;
                p.esDevolucion = true;
                out.add(p);
            }

        } else if ("DV".equals(tipo)) {
            // Devoluciones en negativo
            PagoLinea p = new PagoLinea();
            p.numeroNota = r.numero;
            p.folio      = folioDeNota(r);
            p.fecha      = r.fecha;

            String descDev = "";
            try {
                List<Modelo.NotaDetalle> dets = ndao.listarDetalleDeNota(r.numero);
                if (dets != null && !dets.isEmpty()) {
                    // Usamos LinkedHashSet para evitar duplicados y respetar el orden
                    java.util.LinkedHashSet<String> arts = new java.util.LinkedHashSet<>();

                    for (Modelo.NotaDetalle d : dets) {
                        String cod   = (d.getCodigoArticulo() == null
                                        ? ""
                                        : String.valueOf(d.getCodigoArticulo()));
                        String art   = safe(d.getArticulo());
                        String mod   = safe(d.getModelo());
                        String talla = safe(d.getTalla());
                        String color = safe(d.getColor());

                        StringBuilder sb = new StringBuilder();

                        if (!isBlank(cod)) {
                            sb.append(cod);
                        }
                        if (!isBlank(art)) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(art);
                        }
                        if (!isBlank(mod)) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(mod);
                        }
                        if (!isBlank(talla)) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(talla);
                        }
                        if (!isBlank(color)) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(color);
                        }

                        if (sb.length() > 0) {
                            arts.add(sb.toString());
                        }
                    }

                    if (!arts.isEmpty()) {
                        // Si la devolución trae varios renglones, los separamos con " | "
                        descDev = String.join(" | ", arts);
                    }
                }
            } catch (SQLException ex) {
                // texto genérico si truena
            }

            if (isBlank(descDev)) {
                descDev = "artículo";
            }

            p.concepto     = "Devolución - " + descDev;
            double total   = z(r.total);
            p.importe      = -Math.abs(total);
            p.saldo        = null;
            p.esDevolucion = true;
            out.add(p);
        }

    }
    return out;
}
/** Lee las observaciones de una nota específica.
 *  Primero busca en notas_observaciones; si no hay, cae a Notas.observaciones.
 */
private String leerObservacionesDeNota(int numeroNota) {
    try (java.sql.Connection cn = Conexion.Conecta.getConnection()) {

        String obs = null;

        // 1) Tabla notas_observaciones (la nueva)
        final String sql1 = "SELECT observaciones FROM notas_observaciones WHERE numero_nota = ?";
        try (java.sql.PreparedStatement ps = cn.prepareStatement(sql1)) {
            ps.setInt(1, numeroNota);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    obs = rs.getString(1);
                }
            }
        }

        // 2) Si está vacío, intentamos en Notas.observaciones (compatibilidad)
        if (obs == null || obs.trim().isEmpty()) {
            final String sql2 = "SELECT observaciones FROM Notas WHERE numero_nota = ?";
            try (java.sql.PreparedStatement ps = cn.prepareStatement(sql2)) {
                ps.setInt(1, numeroNota);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        obs = rs.getString(1);
                    }
                }
            }
        }

        if (obs == null) return "";
        // Normalizar espacios / saltos de línea
        obs = obs.replaceAll("\\s+", " ").trim();
        return obs;

    } catch (Exception ignore) {
        // Si falla no reventamos nada, sólo devolvemos vacío
    }
    return "";
}
/**
 * Lee, si existe, el registro de saldo migrado del cliente (tabla con
 * columnas telefono1, saldo_migrado, fecha_saldo, obsequios, observacion)
 * y lo convierte en un texto para el cuadro de observaciones.
 */
private String leerSaldoMigradoObservaciones(String telefono) {
    if (telefono == null || telefono.isBlank()) return "";

    final String sql =
        "SELECT obsequios, observacion " +
        "FROM HistorialCliente " +        // <-- cambia al nombre real de tu tabla
        "WHERE telefono1 = ? LIMIT 1";

    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setString(1, telefono);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return "";

            String obsequios          = rs.getString("obsequios");
            String observacion        = rs.getString("observacion");

            StringBuilder sb = new StringBuilder();

            if (!isBlank(obsequios)) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append("Obsequios: ")
                  .append(obsequios.trim());
            }
            if (!isBlank(observacion)) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append(observacion.trim());
            }

            String out = sb.toString().replaceAll("\\s+", " ").trim();
            return out;
        }

    } catch (Exception ignore) {
        // Si truena, simplemente no agregamos este bloque
    }
    return "";
}

/** Construye observaciones a partir de las notas del cliente (folio + observación). */
private String construirObservacionesNotas(List<NotasDAO.NotaResumen> notas) {
    if (notas == null || notas.isEmpty()) return "";
    List<String> partes = new ArrayList<>();

    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;
        String obs = leerObservacionesDeNota(r.numero);
        if (obs == null) continue;
        obs = obs.trim();
        if (obs.isEmpty()) continue;

        String folio = folioDeNota(r);   // ya tienes este helper
        partes.add(folio + ": " + obs);
    }
    return String.join("  |  ", partes);
}

/** Observaciones automáticas con lo que haya del cliente. */
private String construirObservacionesAuto(cliente c) {
    List<String> partes = new ArrayList<>();
    if (!isBlank(c.getSituacionEvento())) {
        partes.add("Situación evento: " + c.getSituacionEvento());
    }
    if (!isBlank(c.getLugarEvento())) {
        partes.add("Lugar del evento: " + c.getLugarEvento());
    }
    if (!isBlank(c.getComoSeEntero())) {
        partes.add("Se enteró por: " + c.getComoSeEntero());
    }
    return String.join("  |  ", partes);
}
private void imprimirHojaDetalleCliente() {
    String tel = Utilidades.TelefonosUI.soloDigitos(txtTel.getText());
    if (tel.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Captura el teléfono del cliente antes de imprimir.",
                "Atención", JOptionPane.WARNING_MESSAGE);
        return;
    }

    try {
        clienteDAO cdao = new clienteDAO();
        cliente cli = cdao.buscarClientePorTelefono1(tel);
        if (cli == null) {
            JOptionPane.showMessageDialog(this, "No se encontró el cliente en la base de datos.",
                    "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);

        NotasDAO ndao2 = new NotasDAO();
        // Siempre leer lo más fresco de la BD al imprimir
        List<NotasDAO.NotaResumen> notas = ndao2.listarNotasPorTelefonoResumen(tel);

        List<CompraLinea> compras = construirCompras(ndao2, notas);
        List<PagoLinea> pagos   = construirPagos(ndao2, notas);
        String desgloseSaldos   = construirDesgloseSaldos(notas);

        double saldoGlobal = obtenerSaldoGlobalCreditos(tel);

        // Nota de crédito inicial (para folio de apartado y fecha de operación)
        NotasDAO.NotaResumen notaCreditoInicial = elegirPrimerCredito(notas);
        // Si por alguna razón no hay CR, caemos al criterio anterior
        NotasDAO.NotaResumen notaPrincipal = (notaCreditoInicial != null)
                ? notaCreditoInicial
                : elegirNotaPrincipal(notas);

        String folioPrincipal = "";
        String modeloVestido = "";
        String tallaVestido = "";
        String colorVestido = "";
        LocalDate fechaOperacion = null;

        if (notaPrincipal != null) {
            folioPrincipal = folioDeNota(notaPrincipal); // Folio de apartado
            fechaOperacion = notaPrincipal.fecha;        // Fecha de operación

            try {
                List<Modelo.NotaDetalle> dets = ndao2.listarDetalleDeNota(notaPrincipal.numero);
                if (!dets.isEmpty()) {
                    Modelo.NotaDetalle d0 = dets.get(0);
                    modeloVestido = safe(d0.getModelo());
                    tallaVestido = safe(d0.getTalla());
                    colorVestido = safe(d0.getColor());
                }
            } catch (SQLException ignore) { }
        }


        // Datos de calendario del cliente
        LocalDate fechaEvento = null;
        LocalDate fechaPrueba1 = null;
        LocalDate fechaEntrega = null;
        String horaPrueba1 = "";
        String horaEntrega = "";
        String asesora = "";

        if (cr != null) {
            fechaEvento = cr.getFechaEvento();
            fechaPrueba1 = cr.getFechaPrueba1();
            fechaEntrega = cr.getFechaEntrega();
            horaPrueba1 = safe(cr.getHoraPrueba1());
            horaEntrega = safe(cr.getHoraEntrega());
            asesora = obtenerNombreAsesoraPorTelefono(tel);
        } else {
            fechaEvento = parseDate(cli.getFechaEvento());
            fechaPrueba1 = parseDate(cli.getFechaPrueba1());
            fechaEntrega = parseDate(cli.getFechaEntrega());
        }

        // Si no hay asesora en el calendario, la tomamos de la tabla Notas -> asesor
        if (isBlank(asesora)) {
            asesora = obtenerNombreAsesoraPorTelefono(tel);
        }

        String nombreCompleto;
        if (cr != null && cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank()) {
            nombreCompleto = cr.getNombreCompleto();
        } else {
            nombreCompleto = (safe(cli.getNombre()) + " " +
                    safe(cli.getApellidoPaterno()) + " " +
                    safe(cli.getApellidoMaterno())).trim().replaceAll("\\s+", " ");
        }

        String celular = safe(cli.getTelefono1());
        String telefono2 = safe(cli.getTelefono2());

        Double busto = cli.getBusto();
        Double cintura = cli.getCintura();
        Double cadera = cli.getCadera();

        // ===== Obsequios + observaciones del cliente + notas + saldo migrado =====

        // Obsequios de la operación seleccionada (pestaña "Obsequios")
        String textoObsequios = resumenObsequiosSeleccionados();

        // Observación general del cliente (campo "Observaciones" del cliente)
        String obsCliente = valorInfo("Observaciones"); // viene de la tabla superior
        obsCliente = safe(obsCliente);

        // Observaciones de TODAS las notas (tabla notas_observaciones / Notas)
        String obsNotas = construirObservacionesNotas(notas);

        // Datos de migración de saldo (si existe registro para este teléfono)
        String obsMigrado = leerSaldoMigradoObservaciones(tel);

        StringBuilder obsBuilder = new StringBuilder();

        // 1) Obsequios de la nota seleccionada
        if (!isBlank(textoObsequios)) {
            obsBuilder.append("Obsequios: ")
                    .append(textoObsequios);
        }

        // 2) Observaciones generales del cliente
        if (!isBlank(obsCliente)) {
            if (obsBuilder.length() > 0) obsBuilder.append("  |  ");
            obsBuilder.append("Observaciones del cliente: ")
                    .append(obsCliente);
        }

        // 3) Observaciones capturadas por nota (notas_observaciones)
        if (!isBlank(obsNotas)) {
            if (obsBuilder.length() > 0) obsBuilder.append("  |  ");
            obsBuilder.append("Observación de folio ")
                    .append(obsNotas);
        }

        // 4) Saldo migrado + obsequios + observación de la tabla de migración
        if (!isBlank(obsMigrado)) {
            if (obsBuilder.length() > 0) obsBuilder.append("  |  ");
            obsBuilder.append(obsMigrado);
        }

        // Texto final que se manda a la hoja impresa
        String observaciones = obsBuilder.toString();




        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Hoja detalle cliente " + nombreCompleto);

        Printable printable = new HojaDetallePrintable(
                nombreCompleto,
                celular,
                telefono2,
                fechaOperacion,
                folioPrincipal,
                modeloVestido,
                tallaVestido,
                colorVestido,
                cli.getStatus(),
                fechaEvento,
                fechaPrueba1,
                fechaEntrega,
                horaPrueba1,
                horaEntrega,
                asesora,
                busto,
                cintura,
                cadera,
                saldoGlobal,
                observaciones,
                compras,
                pagos,
                desgloseSaldos
        );
        job.setPrintable(printable);

        if (job.printDialog()) {
            job.print();
        }

    } catch (SQLException | PrinterException ex) {
        JOptionPane.showMessageDialog(this,
                "No se pudo imprimir la hoja de detalle:\n" + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}
/** Implementación Printable que dibuja la hoja de detalle (página 0)
 *  y la hoja de compras/pagos (página 1).
 */
private static class HojaDetallePrintable implements Printable {

    private final String nombreCompleto;
    private final String celular;
    private final String telefono2;
    private final LocalDate fechaOperacion;
    private final String folioApartado;
    private final String modeloVestido;
    private final String tallaVestido;
    private final String colorVestido;
    private final String statusCliente;
    private final LocalDate fechaEvento;
    private final LocalDate fechaAjuste;
    private final LocalDate fechaEntrega;
    private final String horaAjuste;
    private final String horaEntrega;
    private final String asesora;
    private final Double busto;
    private final Double cintura;
    private final Double cadera;
    private final double saldoGlobal;
    private final String observaciones;
    private final List<CompraLinea> compras;
    private final List<PagoLinea> pagos;
    private final String desgloseSaldos;

    // Lista lineal de líneas a imprimir (compras + pagos)
    private List<LineaMov> lineas;

    // Totales para los renglones "TOTAL ..."
    private double totalComprasMov = 0.0;
    private double totalPagos        = 0.0;  // sólo pagos (CR/AB)
    private double totalDevoluciones = 0.0;  // sólo devoluciones (DV)

    private static final DateTimeFormatter DF_IMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final int TOP_MARGIN    = 30;
    private static final int BOTTOM_MARGIN = 30;
    private static final int ROW_HEIGHT    = 12;
    // 2 mm adicionales de margen izquierdo
    private static final double MM_TO_POINTS = 72.0 / 25.4;
    private static final int EXTRA_LEFT_MARGIN = (int) Math.round(2 * MM_TO_POINTS);


    /** Tipo de línea dentro de la tabla de movimientos. */
    private static class LineaMov {
        enum Tipo {
            TITLE_COMPRAS,
            HEADER_COMPRAS,
            ROW_COMPRA,
            TOTAL_COMPRAS,
            SPACE,
            TITLE_PAGOS,
            HEADER_PAGOS,
            ROW_PAGO,
            TOTAL_PAGOS,
            SALDOS_RESUMEN
        }
        final Tipo tipo;
        final CompraLinea compra;
        final PagoLinea pago;

        LineaMov(Tipo tipo) {
            this(tipo, null, null);
        }
        LineaMov(Tipo tipo, CompraLinea c) {
            this(tipo, c, null);
        }
        LineaMov(Tipo tipo, PagoLinea p) {
            this(tipo, null, p);
        }
        LineaMov(Tipo tipo, CompraLinea c, PagoLinea p) {
            this.tipo = tipo;
            this.compra = c;
            this.pago = p;
        }
    }

    HojaDetallePrintable(
            String nombreCompleto,
            String celular,
            String telefono2,
            LocalDate fechaOperacion,
            String folioApartado,
            String modeloVestido,
            String tallaVestido,
            String colorVestido,
            String statusCliente,
            LocalDate fechaEvento,
            LocalDate fechaAjuste,
            LocalDate fechaEntrega,
            String horaAjuste,
            String horaEntrega,
            String asesora,
            Double busto,
            Double cintura,
            Double cadera,
            double saldoGlobal,
            String observaciones,
            List<CompraLinea> compras,
            List<PagoLinea> pagos,
            String desgloseSaldos
    ) {
        this.nombreCompleto = nombreCompleto;
        this.celular = celular;
        this.telefono2 = telefono2;
        this.fechaOperacion = fechaOperacion;
        this.folioApartado = folioApartado;
        this.modeloVestido = modeloVestido;
        this.tallaVestido = tallaVestido;
        this.colorVestido = colorVestido;
        this.statusCliente = statusCliente;
        this.fechaEvento = fechaEvento;
        this.fechaAjuste = fechaAjuste;
        this.fechaEntrega = fechaEntrega;
        this.horaAjuste = horaAjuste;
        this.horaEntrega = horaEntrega;
        this.asesora = asesora;
        this.busto = busto;
        this.cintura = cintura;
        this.cadera = cadera;
        this.saldoGlobal = saldoGlobal;
        this.observaciones = observaciones;
        this.compras = (compras == null) ? Collections.emptyList() : compras;
        this.pagos   = (pagos   == null) ? Collections.emptyList()   : pagos;
        this.desgloseSaldos = (desgloseSaldos == null ? "" : desgloseSaldos.trim());
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        Graphics2D g2 = (Graphics2D) g;
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.setColor(Color.BLACK);

        ensureLineasConstruidas();

        // Página 0: siempre existe (detalle del cliente)
        if (pageIndex == 0) {
            int startY = layoutHojaDetalle(g2, pf, true);

            // Si no hay movimientos, ya con la hoja de detalle basta
            if (lineas.isEmpty()) {
                return PAGE_EXISTS;
            }

            int h = (int) pf.getImageableHeight();
            int disponible = h - BOTTOM_MARGIN - startY;
            if (disponible < ROW_HEIGHT) {
                // No cabe ni una línea, pero la hoja existe
                return PAGE_EXISTS;
            }

            int maxLines = disponible / ROW_HEIGHT;
            drawMovimientos(g2, pf, startY, 0, maxLines);
            return PAGE_EXISTS;
        }

        // Páginas 1, 2, 3...: sólo movimientos (continuación)
        int firstIndex = computeFirstLineIndexForPage(pageIndex, g2, pf);
        if (firstIndex >= lineas.size()) {
            // Ya no hay más líneas de movimientos
            return NO_SUCH_PAGE;
        }

        int h = (int) pf.getImageableHeight();
        int y = TOP_MARGIN;

        // Encabezado pequeño de continuación
        Font fTituloCont = new Font("SansSerif", Font.BOLD, 12);
        g2.setFont(fTituloCont);
        g2.drawString("MOVIMIENTOS (continuación)", 20 + EXTRA_LEFT_MARGIN, y);
        y += 18;

        int disponible = h - BOTTOM_MARGIN - y;
        if (disponible < ROW_HEIGHT) {
            return PAGE_EXISTS;
        }

        int maxLines = disponible / ROW_HEIGHT;
        drawMovimientos(g2, pf, y, firstIndex, maxLines);
        return PAGE_EXISTS;
    }

    /** Construye la lista lineal de líneas (compras + pagos) con sus totales. */
    private void ensureLineasConstruidas() {
        if (lineas != null) return;

        lineas = new ArrayList<>();

        // Ordenar compras por fecha y folio
        List<CompraLinea> comprasOrdenadas = new ArrayList<>(compras);
        comprasOrdenadas.sort(Comparator.comparingInt(c -> c.numeroNota));

        totalComprasMov = 0.0;
        for (CompraLinea c : comprasOrdenadas) {
            if (c != null && c.precioPagar != null) {
                totalComprasMov += c.precioPagar; // devoluciones vienen negativas
            }
        }

        if (!comprasOrdenadas.isEmpty()) {
            lineas.add(new LineaMov(LineaMov.Tipo.TITLE_COMPRAS));
            lineas.add(new LineaMov(LineaMov.Tipo.HEADER_COMPRAS));
            for (CompraLinea c : comprasOrdenadas) {
                lineas.add(new LineaMov(LineaMov.Tipo.ROW_COMPRA, c));
            }
            lineas.add(new LineaMov(LineaMov.Tipo.TOTAL_COMPRAS));
            lineas.add(new LineaMov(LineaMov.Tipo.SPACE));
        }

        // Ordenar pagos por fecha y folio
        List<PagoLinea> pagosOrdenados = new ArrayList<>(pagos);
        pagosOrdenados.sort(Comparator.comparingInt(p -> p.numeroNota));

        totalPagos        = 0.0;
        totalDevoluciones = 0.0;

        for (PagoLinea p : pagosOrdenados) {
            if (p != null && p.importe != null) {
                if (p.esDevolucion) {
                    // En PagoLinea ya guardaste las devoluciones en negativo,
                    // para el total queremos el monto positivo
                    totalDevoluciones += Math.abs(p.importe);
                } else {
                    totalPagos += p.importe;
                }
            }
        }

        if (!pagosOrdenados.isEmpty()) {
            lineas.add(new LineaMov(LineaMov.Tipo.TITLE_PAGOS));
            lineas.add(new LineaMov(LineaMov.Tipo.HEADER_PAGOS));
            for (PagoLinea p : pagosOrdenados) {
                lineas.add(new LineaMov(LineaMov.Tipo.ROW_PAGO, p));
            }
            lineas.add(new LineaMov(LineaMov.Tipo.TOTAL_PAGOS));
        }


        // Resumen de saldos pendientes por nota (fuera del recuadro de pagos)
        if (desgloseSaldos != null && !desgloseSaldos.isBlank()) {
            lineas.add(new LineaMov(LineaMov.Tipo.SALDOS_RESUMEN));
        }
    }

    /**
     * Calcula qué índice de línea empieza en la página pageIndex,
     * simulando cuántas líneas caben en cada página anterior.
     */
    private int computeFirstLineIndexForPage(int pageIndex, Graphics2D g2, PageFormat pf) {
        if (pageIndex <= 0) return 0;
        int h = (int) pf.getImageableHeight();
        int lineIndex = 0;

        for (int p = 0; p < pageIndex; p++) {
            int startY;
            if (p == 0) {
                // Página 0: se usa el espacio que queda después del encabezado
                startY = layoutHojaDetalle(g2, pf, false);
            } else {
                int y = TOP_MARGIN;
                y += 18; // "MOVIMIENTOS (continuación)"
                startY = y;
            }
            int disponible = h - BOTTOM_MARGIN - startY;
            if (disponible <= 0) break;

            int maxLines = disponible / ROW_HEIGHT;
            if (maxLines <= 0) break;

            lineIndex += maxLines;
            if (lineIndex >= lineas.size()) {
                lineIndex = lineas.size();
                break;
            }
        }
        return lineIndex;
    }

    /**
     * Dibuja encabezado, datos del cliente, observaciones y medidas.
     * Devuelve la coordenada Y a partir de la cual se pueden imprimir movimientos.
     */
    private int layoutHojaDetalle(Graphics2D g2, PageFormat pf, boolean draw) {
        int w = (int) pf.getImageableWidth();
        int y = TOP_MARGIN;

        Font fTitulo = new Font("SansSerif", Font.BOLD, 14);
        Font fSub    = new Font("SansSerif", Font.BOLD, 12);
        Font fLabel  = new Font("SansSerif", Font.BOLD, 9);
        Font fValue  = new Font("SansSerif", Font.PLAIN, 9);
        Font fNormal = fValue;

        // Encabezado
        if (draw) {
            g2.setFont(fTitulo);
            drawCentered(g2, "MIANOVIAS QUERETARO", w, y);
        }
        y += 16;
        if (draw) {
            g2.setFont(fSub);
            drawCentered(g2, "HOJA DE DETALLE", w, y);
        }
        y += 22;

        // Saldo global arriba derecha
        int xTable = 20 + EXTRA_LEFT_MARGIN;
        int tableWidth = w - 40 - EXTRA_LEFT_MARGIN;


        String lblSaldo = "Saldo total:";
        String valSaldo = formatMoney(saldoGlobal);

        if (draw) {
            g2.setFont(fLabel);
            int lblW = g2.getFontMetrics().stringWidth(lblSaldo);
            g2.setFont(fValue);
            int valW = g2.getFontMetrics().stringWidth(valSaldo);

            int totalSaldoW = lblW + 4 + valW;
            int xSaldo = xTable + tableWidth - totalSaldoW;
            int ySaldo = y;

            g2.setFont(fLabel);
            g2.drawString(lblSaldo, xSaldo, ySaldo);
            g2.setFont(fValue);
            g2.drawString(valSaldo, xSaldo + lblW + 4, ySaldo);
        }

        y += 10;

        // Tabla de datos del cliente
        int rowH = 12;

        String modeloTalla = valueOrEmpty(modeloVestido);
        if (tallaVestido != null && !tallaVestido.isBlank()) {
            if (!modeloTalla.isEmpty()) modeloTalla += "   ";
            modeloTalla += "Talla: " + tallaVestido;
        }

        List<EncabezadoLinea> filas = new ArrayList<>();
        filas.add(new EncabezadoLinea("Cliente:",           valueOrEmpty(nombreCompleto),
                                      "Teléfono:",          valueOrEmpty(formatPhone(telefono2))));
        filas.add(new EncabezadoLinea("Celular:",           valueOrEmpty(formatPhone(celular)),
                                      "Folio de apartado:", valueOrEmpty(folioApartado)));
        filas.add(new EncabezadoLinea("Fecha operación:",   formatDate(fechaOperacion),
                                      "Color:",             valueOrEmpty(colorVestido)));
        filas.add(new EncabezadoLinea("Modelo vestido:",    modeloTalla,
                                      "Asesora:",           valueOrEmpty(asesora)));
        filas.add(new EncabezadoLinea("Fecha evento:",      formatDate(fechaEvento),
                                      "",                   ""));
        filas.add(new EncabezadoLinea("Fecha de ajuste:",   formatDate(fechaAjuste),
                                      "Hora ajuste:",       valueOrEmpty(horaAjuste)));
        filas.add(new EncabezadoLinea("Fecha de entrega:",  formatDate(fechaEntrega),
                                      "Hora entrega:",      valueOrEmpty(horaEntrega)));

        int headerHeight = filas.size() * rowH + 4;

        if (draw) {
            g2.setFont(fNormal);
            g2.drawRect(xTable, y, tableWidth, headerHeight);

            int midX = xTable + tableWidth / 2;
            g2.drawLine(midX, y, midX, y + headerHeight);

            // Calculamos el ancho máximo de las etiquetas de la columna izquierda
            g2.setFont(fLabel);
            FontMetrics fmLabel = g2.getFontMetrics();
            int leftMaxLabelWidth = 0;
            for (EncabezadoLinea f : filas) {
                if (f.labelL != null && !f.labelL.isBlank()) {
                    int wLabel = fmLabel.stringWidth(f.labelL);
                    if (wLabel > leftMaxLabelWidth) {
                        leftMaxLabelWidth = wLabel;
                    }
                }
            }

            int col1LabelX = xTable + 6;
            int col1ValueX = col1LabelX + leftMaxLabelWidth + 6; // ← aquí se recorre a la IZQUIERDA
            int col2LabelX = midX + 6;
            int col2ValueX = midX + tableWidth / 4;

            for (int i = 0; i < filas.size(); i++) {
                int rowTop = y + 4 + i * rowH;
                int rowBottom = rowTop + rowH;
                int baseY = rowBottom - 3;

                g2.drawLine(xTable, rowBottom, xTable + tableWidth, rowBottom);

                EncabezadoLinea f = filas.get(i);

                if (f.labelL != null && !f.labelL.isBlank()) {
                    g2.setFont(fLabel);
                    g2.drawString(f.labelL, col1LabelX, baseY);
                }
                if (f.valueL != null && !f.valueL.isBlank()) {
                    g2.setFont(fValue);
                    g2.drawString(f.valueL, col1ValueX, baseY);
                }
                if (f.labelR != null && !f.labelR.isBlank()) {
                    g2.setFont(fLabel);
                    g2.drawString(f.labelR, col2LabelX, baseY);
                }
                if (f.valueR != null && !f.valueR.isBlank()) {
                    g2.setFont(fValue);
                    g2.drawString(f.valueR, col2ValueX, baseY);
                }
            }
        }


        y = y + headerHeight + 26;

        // Observaciones + medidas para ajuste
        int xLeft = 20 + EXTRA_LEFT_MARGIN;
        int anchoTotal = w - 40 - EXTRA_LEFT_MARGIN;

        int anchoObs = (int) (anchoTotal * 0.65);
        int anchoMed = anchoTotal - anchoObs - 10;
        if (anchoMed < 120) anchoMed = 120;

        int xObs = xLeft;
        int xMed = xObs + anchoObs + 10;
        int rowHeight = 14;

        int hObs = 90;
        int hMed = 3 * rowHeight + 24;

        if (draw) {
            g2.setFont(fSub);
            g2.drawRect(xObs, y, anchoObs, hObs);
            g2.drawString("OBSERVACIONES:", xObs + 6, y + 14);
            g2.setFont(fNormal);
            drawWrappedText(g2, observaciones, xObs + 6, y + 30, anchoObs - 12, 12);

            g2.setFont(fSub);
            g2.drawRect(xMed, y, anchoMed, hMed);
            g2.drawString("MEDIDAS PARA AJUSTE", xMed + 6, y + 14);
            g2.setFont(fNormal);

            int yMedLine = y + 18;
            g2.drawLine(xMed, yMedLine, xMed + anchoMed, yMedLine);

            int yMed = yMedLine + rowHeight;
            drawField(g2, "Busto:",   formatMedida(busto),   xMed + 6, xMed + 90, yMed); yMed += rowHeight;
            drawField(g2, "Cintura:", formatMedida(cintura), xMed + 6, xMed + 90, yMed); yMed += rowHeight;
            drawField(g2, "Cadera:",  formatMedida(cadera),  xMed + 6, xMed + 90, yMed);
        }

        y = y + Math.max(hObs, hMed) + 26;
        // Aquí termina la parte "fija" de la hoja 1. A partir de este Y van los movimientos.
        return y;
    }

    /** Dibuja las líneas de movimientos desde firstIndex, máximo maxLines líneas. */
    private void drawMovimientos(Graphics2D g2, PageFormat pf, int startY, int firstIndex, int maxLines) {
        int w = (int) pf.getImageableWidth();
        int x = 20 + EXTRA_LEFT_MARGIN;
        int tableWidth = w - 40 - EXTRA_LEFT_MARGIN;


        Font fTitulo = new Font("SansSerif", Font.BOLD, 12);
        Font fHeader = new Font("SansSerif", Font.BOLD, 8);
        Font fNormal = new Font("SansSerif", Font.PLAIN, 8);

        // Columnas de COMPRAS
        int colFolio   = (int) (tableWidth * 0.12);
        int colFecha   = (int) (tableWidth * 0.09);
        int colCodigo  = (int) (tableWidth * 0.09);
        int colArticulo= (int) (tableWidth * 0.16);
        int colModelo  = (int) (tableWidth * 0.11);
        int colTalla   = (int) (tableWidth * 0.06);
        int colColor   = (int) (tableWidth * 0.08);
        int colStatusC = (int) (tableWidth * 0.05);
        int colPrecio  = (int) (tableWidth * 0.09);
        int colDesc    = (int) (tableWidth * 0.05);
        int colPagar   = tableWidth - (colFolio + colFecha + colCodigo + colArticulo +
                                    colModelo + colTalla + colColor + colStatusC + colPrecio + colDesc);

        // Columnas de PAGOS (pagos + devoluciones, sin saldo)
        int colFolio2   = (int) (tableWidth * 0.15);
        int colFecha2   = (int) (tableWidth * 0.18);
        int colConcepto = (int) (tableWidth * 0.47);
        int colImporte  = tableWidth - (colFolio2 + colFecha2 + colConcepto);


        int y = startY;
        int printed = 0;

        for (int i = firstIndex; i < lineas.size() && printed < maxLines; i++) {
            LineaMov ln = lineas.get(i);
            switch (ln.tipo) {
                case TITLE_COMPRAS: {
                    g2.setFont(fTitulo);
                    g2.drawString("COMPRAS", x, y);
                    break;
                }
                case HEADER_COMPRAS: {
                    g2.setFont(fHeader);
                    int xx = x;
                    g2.drawString("# Folio",  xx, y); xx += colFolio;
                    g2.drawString("Fecha",    xx, y); xx += colFecha;
                    g2.drawString("Código",   xx, y); xx += colCodigo;
                    g2.drawString("Artículo", xx, y); xx += colArticulo;
                    g2.drawString("Modelo",   xx, y); xx += colModelo;
                    g2.drawString("Talla",    xx, y); xx += colTalla;
                    g2.drawString("Color",    xx, y); xx += colColor;
                    g2.drawString("St",       xx, y); xx += colStatusC;
                    g2.drawString("Precio",   xx, y); xx += colPrecio;
                    g2.drawString("Desc%",    xx, y); xx += colDesc;
                    g2.drawString("A pagar",  xx, y);

                    int headerBottom = y + 3;
                    g2.drawLine(x, headerBottom, x + tableWidth, headerBottom);
                    break;
                }

                case ROW_COMPRA: {
                    g2.setFont(fNormal);
                    CompraLinea c = ln.compra;
                    int xx = x;
                    g2.drawString(valueOrEmpty(c.folio),                  xx, y); xx += colFolio;
                    g2.drawString(formatDate(c.fecha),                    xx, y); xx += colFecha;
                    g2.drawString(valueOrEmpty(c.codigoArticulo),         xx, y); xx += colCodigo;
                    g2.drawString(trimTo(g2, valueOrEmpty(c.articulo),
                                        colArticulo - 4),                 xx, y); xx += colArticulo;
                    g2.drawString(trimTo(g2, valueOrEmpty(c.modelo),
                                        colModelo - 4),                   xx, y); xx += colModelo;
                    g2.drawString(valueOrEmpty(c.talla),                  xx, y); xx += colTalla;
                    g2.drawString(valueOrEmpty(c.color),                  xx, y); xx += colColor;
                    g2.drawString(valueOrEmpty(c.status),                 xx, y); xx += colStatusC;
                    g2.drawString(formatMoney(c.precio),                  xx, y); xx += colPrecio;
                    g2.drawString(formatPercent(c.descuento),             xx, y); xx += colDesc;
                    g2.drawString(formatMoney(c.precioPagar),             xx, y);
                    break;
                }
                case TOTAL_COMPRAS: {
                    g2.setFont(fTitulo.deriveFont(10f));
                    g2.drawString("TOTAL COMPRAS:" +
                                  formatMoney(totalComprasMov), x, y);
                    break;
                }
                case SPACE: {
                    // Línea en blanco para separar secciones
                    break;
                }
                case TITLE_PAGOS: {
                    g2.setFont(fTitulo);
                    g2.drawString("PAGOS Y DEVOLUCIONES", x, y);
                    break;
                }
                case HEADER_PAGOS: {
                    g2.setFont(fHeader);
                    int xx = x;
                    g2.drawString("# Folio",    xx, y); xx += colFolio2;
                    g2.drawString("Fecha pago", xx, y); xx += colFecha2;
                    g2.drawString("Concepto",   xx, y); xx += colConcepto;
                    g2.drawString("Importe",    xx, y); xx += colImporte;
                    int headerBottom = y + 3;
                    g2.drawLine(x, headerBottom, x + tableWidth, headerBottom);
                    break;
                }
                case ROW_PAGO: {
                    g2.setFont(fNormal);
                    PagoLinea p = ln.pago;
                    int xx = x;
                    g2.drawString(valueOrEmpty(p.folio),            xx, y); xx += colFolio2;
                    g2.drawString(formatDate(p.fecha),              xx, y); xx += colFecha2;
                    g2.drawString(trimTo(g2, valueOrEmpty(p.concepto),
                                        colConcepto - 4),          xx, y); xx += colConcepto;
                    g2.drawString(formatMoney(p.importe),           xx, y);
                    break;
                }
                case TOTAL_PAGOS: {
                    g2.setFont(fTitulo.deriveFont(10f));

                    String txt = "TOTAL PAGOS: " + formatMoney(totalPagos)
                            + "    TOTAL DEVOLUCIONES: " + formatMoney(totalDevoluciones);

                    g2.drawString(txt, x, y);
                    break;
                }
                case SALDOS_RESUMEN: {
                    if (desgloseSaldos != null && !desgloseSaldos.isBlank()) {
                        g2.setFont(fTitulo.deriveFont(9f));
                        String txt = "Saldos pendientes por nota: " + desgloseSaldos;
                        if (saldoGlobal > 0.005) {
                            txt += "   |   Saldo total: " + formatMoney(saldoGlobal);
                        }
                        // Envolver dentro del ancho de la tabla, usando la misma altura de fila
                        drawWrappedText(g2, txt, x, y, tableWidth, ROW_HEIGHT);
                    }
                    break;
                }


            }
            y += ROW_HEIGHT;
            printed++;
        }
    }

    // ---------- helpers gráficos / formato ----------

    private void drawCentered(Graphics2D g2, String text, int width, int y) {
        if (text == null) return;
        int textWidth = g2.getFontMetrics().stringWidth(text);
        int x = (width - textWidth) / 2;
        g2.drawString(text, x, y);
    }

    private void drawField(Graphics2D g2, String label, String value, int xLabel, int xValue, int y) {
        g2.drawString(label, xLabel, y);
        if (value != null && !value.isEmpty()) {
            g2.drawString(value, xValue, y);
        }
    }

    private String valueOrEmpty(String s) {
        return (s == null) ? "" : s;
    }

    private String formatDate(LocalDate d) {
        if (d == null) return "";
        return DF_IMP.format(d);
    }

    private String formatMoney(Double v) {
        if (v == null) return "";
        return "$ " + String.format("%,.2f", v);
    }

    private String formatMoney(double v) {
        return "$ " + String.format("%,.2f", v);
    }

    private String formatPercent(Double v) {
        if (v == null) return "";
        return String.format("%,.0f%%", v);
    }

    private String formatMedida(Double v) {
        if (v == null) return "";
        return String.format("%,.1f", v);
    }

    private void drawWrappedText(Graphics2D g2, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null || text.isBlank()) return;
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            int width = g2.getFontMetrics().stringWidth(test);
            if (width > maxWidth && line.length() > 0) {
                g2.drawString(line.toString(), x, y);
                y += lineHeight;
                line.setLength(0);
                line.append(w);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(w);
            }
        }
        if (line.length() > 0) {
            g2.drawString(line.toString(), x, y);
        }
    }

    private String trimTo(Graphics2D g2, String text, int maxWidth) {
        if (text == null) return "";
        if (g2.getFontMetrics().stringWidth(text) <= maxWidth) return text;
        String ell = "...";
        int ellW = g2.getFontMetrics().stringWidth(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (g2.getFontMetrics().stringWidth(sb.toString()) + ellW > maxWidth) {
                if (sb.length() > 0) sb.setLength(sb.length() - 1);
                break;
            }
        }
        sb.append(ell);
        return sb.toString();
    }
}

private static String formatPhone(String tel) {
    if (tel == null) return "";
    String digits = tel.replaceAll("\\D+", ""); // deja solo números
    if (digits.length() != 10) {
        return tel.trim();
    }
    String p1 = digits.substring(0, 3);
    String p2 = digits.substring(3, 6);
    String p3 = digits.substring(6);
    return p1 + "-" + p2 + "-" + p3;
}
private void cargarFacturaTab(int numeroNota) {
    modelFactura.setRowCount(0);

    try {
        FacturaDatosDAO dao = new FacturaDatosDAO();
        FacturaDatosDAO.Row fd = dao.obtenerPorNota(numeroNota);

        if (fd != null) {
            String personaFmt =
                    (fd.persona == null) ? "" :
                            (fd.persona.equalsIgnoreCase("PM") ? "Persona moral" :
                             fd.persona.equalsIgnoreCase("PF") ? "Persona física" :
                             fd.persona);

            addKV(modelFactura, "Persona", personaFmt);
            addKV(modelFactura, "RFC", n(fd.rfc));// usar catálogo para mostrar DESCRIPCIÓN completa
            String regClave = n(fd.regimen);
            String usoClave = n(fd.usoCfdi);

            CatalogoCFDI.Regimen reg = CatalogoCFDI.buscarRegimenPorClave(regClave);
            CatalogoCFDI.UsoCfdi uso = CatalogoCFDI.buscarUsoPorClave(usoClave);

            String regFmt = (reg == null ? regClave : reg.toString());
            String usoFmt = (uso == null ? usoClave : uso.toString());

            addKV(modelFactura, "Régimen fiscal", regFmt);
            addKV(modelFactura, "Uso del CFDI", usoFmt);
            addKV(modelFactura, "Código postal", n(fd.codigoPostal));
            addKV(modelFactura, "Correo", n(fd.correo));

            if (fd.createdAt != null) addKV(modelFactura, "Capturado", fd.createdAt.toString());
            if (fd.updatedAt != null) addKV(modelFactura, "Actualizado", fd.updatedAt.toString());
        } else {
            // Sin captura previa
            addKV(modelFactura, "Persona", "");
            addKV(modelFactura, "RFC", "");
            addKV(modelFactura, "Régimen fiscal", "");
            addKV(modelFactura, "Uso del CFDI", "");
            addKV(modelFactura, "Código postal", "");
            addKV(modelFactura, "Correo", "");
        }
    } catch (Exception ex) {
        // si quieres, aquí puedes mostrar un JOptionPane de advertencia
    }
}

private static void addKV(DefaultTableModel m, String k, String v) {
    m.addRow(new Object[]{k, (v == null ? "" : v)});
}

private static String n(String s) { return (s == null) ? "" : s; }
/** Reordena las filas de la tabla "Datos del cliente" según ORDEN_INFO. */
private void reordenarTablaInfo() {
    if (modelInfo.getRowCount() == 0) return;

    // Guardamos "Campo" -> "Valor" en un mapa para rearmar la tabla
    Map<String, Object> mapa = new LinkedHashMap<>();
    for (int i = 0; i < modelInfo.getRowCount(); i++) {
        Object campoObj = modelInfo.getValueAt(i, 0);
        String campo = (campoObj == null) ? "" : String.valueOf(campoObj);
        Object valor = modelInfo.getValueAt(i, 1);
        mapa.put(campo, valor);
    }

    java.util.List<Object[]> filas = new ArrayList<>();
    Set<String> usados = new HashSet<>();

    // 1) Primero, los campos en el orden solicitado
    for (String etiqueta : ORDEN_INFO) {
        if (mapa.containsKey(etiqueta)) {
            filas.add(new Object[]{etiqueta, mapa.get(etiqueta)});
            usados.add(etiqueta);
        }
    }

    // 2) Luego, cualquier otro campo que venga del DAO y no esté en la lista
    for (Map.Entry<String, Object> e : mapa.entrySet()) {
        if (!usados.contains(e.getKey())) {
            filas.add(new Object[]{e.getKey(), e.getValue()});
        }
    }

    // Volcamos de nuevo al modelo
    modelInfo.setRowCount(0);
    for (Object[] fila : filas) {
        modelInfo.addRow(fila);
    }
}
/** Carga en la pestaña "Obsequios" los obsequios ligados a la nota. */
private void cargarObsequiosDeNota(int numeroNota) {
    modelObsequios.setRowCount(0);

    final String sql =
            "SELECT " +
            " obsequio1, obsequio1_cod, " +
            " obsequio2, obsequio2_cod, " +
            " obsequio3, obsequio3_cod, " +
            " obsequio4, obsequio4_cod, " +
            " obsequio5, obsequio5_cod " +
            "FROM obsequios " +
            "WHERE numero_nota = ?";

    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, numeroNota);

        try (java.sql.ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                // Cada par obsequioX / obsequioX_cod puede venir null
                agregarObsequioFila(rs.getString("obsequio1"), rs.getString("obsequio1_cod"));
                agregarObsequioFila(rs.getString("obsequio2"), rs.getString("obsequio2_cod"));
                agregarObsequioFila(rs.getString("obsequio3"), rs.getString("obsequio3_cod"));
                agregarObsequioFila(rs.getString("obsequio4"), rs.getString("obsequio4_cod"));
                agregarObsequioFila(rs.getString("obsequio5"), rs.getString("obsequio5_cod"));
            }

            // Si después de todo no hay ninguna fila, mostramos mensaje
            if (modelObsequios.getRowCount() == 0) {
                modelObsequios.addRow(new Object[]{"(Sin obsequios capturados)", ""});
            }
        }

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "Error cargando obsequios: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

/**
 * Agrega una fila de obsequio si hay algo de información.
 * Columna 0: descripción del obsequio
 * Columna 1: código (si existe).
 */
private void agregarObsequioFila(String desc, String cod) {
    String d = safe(desc);
    String c = safe(cod);

    // Si no hay ni descripción ni código, no agregamos nada
    if (d.isEmpty() && c.isEmpty()) {
        return;
    }

    // Puedes formatear el código como quieras; aquí va tal cual
    modelObsequios.addRow(new Object[]{c, d});
}
/** 
 * Devuelve un resumen en texto plano de los obsequios de la nota
 * actualmente cargada en la pestaña "Obsequios".
 */
private String resumenObsequiosSeleccionados() {
    if (modelObsequios.getRowCount() == 0) return "";

    java.util.List<String> partes = new java.util.ArrayList<>();

    for (int i = 0; i < modelObsequios.getRowCount(); i++) {
        Object codObj  = modelObsequios.getValueAt(i, 0);
        Object descObj = modelObsequios.getValueAt(i, 1);

        String cod  = (codObj  == null) ? "" : codObj.toString().trim();
        String desc = (descObj == null) ? "" : descObj.toString().trim();

        // Saltar el placeholder
        if ("(Sin obsequios capturados)".equalsIgnoreCase(cod)
                || "(Sin obsequios capturados)".equalsIgnoreCase(desc)) {
            continue;
        }

        if (cod.isEmpty() && desc.isEmpty()) {
            continue;
        } else if (!cod.isEmpty() && !desc.isEmpty()) {
            partes.add(cod + " - " + desc);
        } else if (!desc.isEmpty()) {
            partes.add(desc);
        } else { // solo código
            partes.add(cod);
        }
    }

    return String.join("  |  ", partes);
}
/**
 * Agrega un obsequio (código + descripción) a una lista en formato de texto.
 */
private void agregarObsequioALista(List<String> lista, String cod, String desc) {
    String c = safe(cod);
    String d = safe(desc);

    if (c.isEmpty() && d.isEmpty()) return;

    if (!c.isEmpty() && !d.isEmpty()) {
        lista.add(c + " - " + d);
    } else if (!d.isEmpty()) {
        lista.add(d);
    } else {
        lista.add(c);
    }
}

/**
 * Construye un resumen de TODOS los obsequios ligados a las notas del cliente,
 * agrupados por folio. Ejemplo:
 * "CR 1234: 234-5678 - Manta | 567-8912 - Velo  ||  CN 5678: 999-0000 - Mantilla"
 */
private String construirResumenObsequiosCliente(List<NotasDAO.NotaResumen> notas) {
    if (notas == null || notas.isEmpty()) return "";

    final String sql =
            "SELECT " +
            " obsequio1, obsequio1_cod, " +
            " obsequio2, obsequio2_cod, " +
            " obsequio3, obsequio3_cod, " +
            " obsequio4, obsequio4_cod, " +
            " obsequio5, obsequio5_cod " +
            "FROM obsequios " +
            "WHERE numero_nota = ?";

    List<String> partesNotas = new ArrayList<>();

    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

        for (NotasDAO.NotaResumen r : notas) {
            if (r == null) continue;

            ps.setInt(1, r.numero);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) continue;

                List<String> obseDeEstaNota = new ArrayList<>();
                agregarObsequioALista(obseDeEstaNota, rs.getString("obsequio1_cod"), rs.getString("obsequio1"));
                agregarObsequioALista(obseDeEstaNota, rs.getString("obsequio2_cod"), rs.getString("obsequio2"));
                agregarObsequioALista(obseDeEstaNota, rs.getString("obsequio3_cod"), rs.getString("obsequio3"));
                agregarObsequioALista(obseDeEstaNota, rs.getString("obsequio4_cod"), rs.getString("obsequio4"));
                agregarObsequioALista(obseDeEstaNota, rs.getString("obsequio5_cod"), rs.getString("obsequio5"));

                if (!obseDeEstaNota.isEmpty()) {
                    String folio = folioDeNota(r); // helper ya definido más arriba
                    partesNotas.add(folio + ": " + String.join(" | ", obseDeEstaNota));
                }
            }
        }
    } catch (Exception ignore) {
        // No rompemos la impresión solo por los obsequios
    }

    return String.join("  ||  ", partesNotas);
}
    /**
     * Saldo global de créditos (CR) de un cliente, leyendo directo de la tabla Notas.
     * Sólo suma tipo='CR' y status='A'.
     */
    private double obtenerSaldoGlobalCreditos(String telefono) {
        if (telefono == null || telefono.isBlank()) return 0.0;
        try {
            NotasDAO dao = new NotasDAO();
            return dao.obtenerSaldoGlobalCreditosPorTelefono(telefono);
        } catch (SQLException ex) {
            // Si truena, regresamos 0 para no reventar la pantalla.
            // Si quieres depurar, aquí puedes hacer un System.out.println(ex.getMessage());
            return 0.0;
        }
    }
/** Pinta en rojo los renglones cuyo status = 'C' en el detalle de la nota. */
private void instalarRendererDetalle() {
    tbDet.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            int statusColModel = modelDet.getColumnCount() - 1; // última col = Status
            Object stObj = modelDet.getValueAt(modelRow, statusColModel);
            String status = (stObj == null ? "" : stObj.toString().trim().toUpperCase());

            if (!isSelected) {
                if ("C".equals(status)) {
                    // Rojo clarito para no quemar las retinas
                    c.setBackground(new Color(255, 222, 3));
                } else {
                    c.setBackground(Color.WHITE);
                }
            }

            return c;
        }
    });
}
/** Devuelve texto "CR 1234 $100.00  |  CR 5678 $50.00" con créditos con saldo pendiente. */
private String construirDesgloseSaldos(List<NotasDAO.NotaResumen> notas) {
    if (notas == null || notas.isEmpty()) return "";
    List<String> partes = new ArrayList<>();

    for (NotasDAO.NotaResumen r : notas) {
        if (r == null) continue;

        String tipo = safeUpper(r.tipo);
        String status = safeUpper(r.status);
        if (!"CR".equals(tipo)) continue;   // sólo créditos
        if ("C".equals(status)) continue;   // ignorar cancelados

        Double saldo = r.saldo;
        if (saldo == null) continue;
        if (saldo <= 0.005) continue;       // prácticamente cero

        String folio = folioDeNota(r);
        partes.add(folio + " " + formatMoney(saldo));
    }

    return String.join("  |  ", partes);
}
/** Abre un diálogo para buscar cliente por nombre/apellidos y carga su teléfono en el panel. */
private void seleccionarClientePorApellido() {
    java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
    DialogBusquedaCliente dlg = new DialogBusquedaCliente(owner);
    dlg.setLocationRelativeTo(this);
    dlg.setVisible(true);

    ClienteResumen cr = dlg.getSeleccionado();
    if (cr != null) {
        String tel = Utilidades.TelefonosUI.soloDigitos(cr.getTelefono1());
        if (tel != null && !tel.isEmpty()) {
            txtTel.setText(tel);
            // usamos el mismo flujo que si hubieras escrito el teléfono a mano
            buscar();
        }
    }
}
/** Diálogo modal para buscar cliente por nombre o apellidos. */
private static class DialogBusquedaCliente extends JDialog {

    private JTextField txtApellido;
    private JTable tabla;
    private javax.swing.table.DefaultTableModel modelo;
    private java.util.List<ClienteResumen> resultados = new java.util.ArrayList<>();
    private ClienteResumen seleccionado;

    public DialogBusquedaCliente(java.awt.Window owner) {
        super(owner, "Buscar cliente por nombre/apellidos", ModalityType.APPLICATION_MODAL);
        construirUI();
    }

    private void construirUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtro
        JPanel pnlFiltro = new JPanel(new BorderLayout(5, 0));
        pnlFiltro.add(new JLabel("Nombre / Apellidos:"), BorderLayout.WEST);
        txtApellido = new JTextField();
        pnlFiltro.add(txtApellido, BorderLayout.CENTER);

        JButton btnBuscar = new JButton("Buscar");
        pnlFiltro.add(btnBuscar, BorderLayout.EAST);

        main.add(pnlFiltro, BorderLayout.NORTH);

        // Tabla
        modelo = new javax.swing.table.DefaultTableModel(
                new Object[]{"Nombre completo", "Teléfono", "Teléfono 2", "Evento", "Prueba 1", "Prueba 2", "Entrega"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.setAutoCreateRowSorter(true);

        main.add(new JScrollPane(tabla), BorderLayout.CENTER);

        // Botones abajo
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSeleccionar = new JButton("Seleccionar");
        JButton btnCerrar = new JButton("Cancelar");
        pnlBotones.add(btnCerrar);
        pnlBotones.add(btnSeleccionar);

        main.add(pnlBotones, BorderLayout.SOUTH);

        setContentPane(main);
        setSize(800, 400);
        setLocationRelativeTo(getOwner());

        // Eventos
        btnBuscar.addActionListener(_e -> buscar());
        btnSeleccionar.addActionListener(_e -> seleccionarActual());
        btnCerrar.addActionListener(_e -> dispose());

        // Doble clic en la tabla
        tabla.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    seleccionarActual();
                }
            }
        });

        // Enter en el campo de búsqueda = buscar
        txtApellido.addActionListener(_e -> buscar());
    }

    private void buscar() {
        String filtro = txtApellido.getText().trim();
        if (filtro.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe al menos una parte del nombre o apellidos.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            clienteDAO dao = new clienteDAO();
            resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);  // ya lo usas en otros paneles
            modelo.setRowCount(0);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            for (ClienteResumen cr : resultados) {
                modelo.addRow(new Object[]{
                        cr.getNombreCompleto(),
                        cr.getTelefono1(),
                        cr.getTelefono2(),
                        cr.getFechaEvento()   == null ? "" : cr.getFechaEvento().format(fmt),
                        cr.getFechaPrueba1()  == null ? "" : cr.getFechaPrueba1().format(fmt),
                        cr.getFechaPrueba2()  == null ? "" : cr.getFechaPrueba2().format(fmt),
                        cr.getFechaEntrega()  == null ? "" : cr.getFechaEntrega().format(fmt)
                });
            }

            if (resultados.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No se encontraron clientes con ese nombre/apellido.",
                        "Buscar cliente", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar clientes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seleccionarActual() {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un cliente de la tabla.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tabla.convertRowIndexToModel(row);
        seleccionado = resultados.get(modelRow);
        dispose();
    }

    public ClienteResumen getSeleccionado() {
        return seleccionado;
    }
}
/** Devuelve el nombre de la asesora asociada al último movimiento del cliente. */
private String obtenerNombreAsesoraPorTelefono(String telefono) {
    if (telefono == null || telefono.isBlank()) return "";

    final String sql =
            "SELECT a.nombre_completo " +
            "FROM Notas n " +
            "JOIN asesor a ON a.numero_empleado = n.asesor " +
            "WHERE n.telefono = ? " +
            "  AND n.asesor IS NOT NULL " +
            "ORDER BY n.fecha_registro DESC, n.numero_nota DESC " +
            "LIMIT 1";

    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setString(1, telefono);

        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String nom = rs.getString(1);
                return (nom == null) ? "" : nom.trim();
            }
        }
    } catch (Exception ignore) {
        // Si truena, no reventamos nada; simplemente devolvemos vacío.
    }
    return "";
}

}