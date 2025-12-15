package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Utilidades.TelefonosUI;
import Controlador.FormasPagoDAO;
import Controlador.ExportadorCSV;
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
    private final JButton btExportar = new JButton("Exportar CSV");
        // Estado de las notas del cliente cargado
    private List<NotasDAO.NotaResumen> notasCliente = new ArrayList<>();
    private double saldoGlobalCreditos = 0.0;

    // Botón para imprimir
    private final JButton btImprimir = new JButton("Imprimir hoja detalle");


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
        // Ya no asumimos que el código es numérico; soporta varchar
        return (c >= 6 ? Double.class : String.class);
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
        north.add(btImprimir);
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

        // guardamos la lista en memoria
        notasCliente.addAll(list);
        saldoGlobalCreditos = calcularSaldoGlobal(list);

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

    // === 3) Fila con saldo global del cliente (créditos) ===
    String txtSaldo = formatMoney(saldoGlobalCreditos);
    modelInfo.addRow(new Object[]{"Saldo global créditos", txtSaldo});
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
    /** Suma los saldos pendientes de todas las notas de crédito (tipo 'CR'). */
private double calcularSaldoGlobal(List<NotasDAO.NotaResumen> list) {
    if (list == null) return 0.0;
    double total = 0.0;
    for (NotasDAO.NotaResumen r : list) {
        if (r == null) continue;
        String tipo = safeUpper(r.tipo);
        if (!"CR".equals(tipo)) continue;
        Double saldo = r.saldo;
        if (saldo == null) continue;
        if (saldo > 0.005) {
            total += saldo;
        }
    }
    return total;
}

private static String safeUpper(String s) {
    return (s == null) ? "" : s.trim().toUpperCase();
}

private static String formatMoney(double v) {
    return "$ " + String.format("%,.2f", v);
}
/** Línea para la tabla de COMPRAS del formato impreso. */
private static class CompraLinea {
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
    String folio;
    LocalDate fecha;
    String concepto;
    Double importe;
    Double saldo;
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

/** Construye las líneas de COMPRAS a partir de las notas CN/CR del cliente. */
private List<CompraLinea> construirCompras(NotasDAO ndao, List<NotasDAO.NotaResumen> notas) throws SQLException {
    List<CompraLinea> out = new ArrayList<>();
    if (notas == null) return out;

    for (NotasDAO.NotaResumen r : notas) {
        String tipo = safeUpper(r.tipo);
        if (!"CN".equals(tipo) && !"CR".equals(tipo)) continue;

        List<Modelo.NotaDetalle> dets = ndao.listarDetalleDeNota(r.numero);
        if (dets == null || dets.isEmpty()) {
            CompraLinea c = new CompraLinea();
            c.folio = folioDeNota(r);
            c.fecha = r.fecha;
            c.codigoArticulo = "";
            c.articulo = tipo.equals("CN") ? "Venta contado" : "Venta crédito";
            c.modelo = "";
            c.talla = "";
            c.color = "";
            c.precio = z(r.total);
            c.descuento = 0.0;
            c.precioPagar = z(r.total);
            out.add(c);
        } else {
            for (Modelo.NotaDetalle d : dets) {
                CompraLinea c = new CompraLinea();
                c.folio = folioDeNota(r);
                c.fecha = r.fecha;
                c.codigoArticulo = String.valueOf(d.getCodigoArticulo()); // soporta varchar o numérico
                c.articulo = safe(d.getArticulo());
                c.modelo = safe(d.getModelo());
                c.talla = safe(d.getTalla());
                c.color = safe(d.getColor());
                c.precio = z(d.getPrecio());
                c.descuento = z(d.getDescuento());
                c.precioPagar = z(d.getSubtotal());
                out.add(c);
            }
        }
    }
    return out;
}

/** Construye las líneas de PAGOS/DEVOLUCIONES. */
private List<PagoLinea> construirPagos(NotasDAO ndao, List<NotasDAO.NotaResumen> notas) throws SQLException {
    List<PagoLinea> out = new ArrayList<>();
    if (notas == null) return out;

    // Saldo de cada crédito
    Map<Integer, Double> saldoCredito = new HashMap<>();
    for (NotasDAO.NotaResumen r : notas) {
        if ("CR".equals(safeUpper(r.tipo))) {
            saldoCredito.put(r.numero, z(r.saldo));
        }
    }

    for (NotasDAO.NotaResumen r : notas) {
        String tipo = safeUpper(r.tipo);

        if ("CR".equals(tipo)) {
            // Pago inicial: total - saldo
            double total = z(r.total);
            double saldo = z(r.saldo);
            double enganche = total - saldo;
            if (enganche > 0.005) {
                PagoLinea p = new PagoLinea();
                p.folio = folioDeNota(r);
                p.fecha = r.fecha;
                p.concepto = "Pago inicial vestido";
                p.importe = enganche;
                p.saldo = saldo;
                out.add(p);
            }
        } else if ("AB".equals(tipo)) {
            PagoLinea p = new PagoLinea();
            p.folio = folioDeNota(r);
            p.fecha = r.fecha;
            String folioCR = obtenerFolioCreditoAbonado(r.numero);
            if (folioCR == null || folioCR.isBlank()) {
                p.concepto = "Abono a crédito";
            } else {
                p.concepto = "Abono a " + folioCR;
            }
            p.importe = z(r.total);

            Double saldoCr = obtenerSaldoCreditoDeAbono(r.numero);
            if (saldoCr != null) p.saldo = saldoCr;
            else p.saldo = z(r.saldo);

            out.add(p);
        } else if ("DV".equals(tipo)) {
            PagoLinea p = new PagoLinea();
            p.folio = folioDeNota(r);
            p.fecha = r.fecha;
            p.concepto = "Devolución";
            p.importe = z(r.total);
            p.saldo = z(r.saldo);
            out.add(p);
        }
    }
    return out;
}
/** Lee las observaciones de una nota específica. */
private String leerObservacionesDeNota(int numeroNota) {
    final String sql = "SELECT observaciones FROM Notas WHERE numero_nota = ?";
    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setInt(1, numeroNota);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String obs = rs.getString(1);
                if (obs == null) return "";
                // Normalizar espacios / saltos de línea
                obs = obs.replaceAll("\\s+", " ").trim();
                return obs;
            }
        }
    } catch (Exception ignore) {
        // Si falla, simplemente no ponemos observación de esa nota
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

        // Notas del cliente (si no las tenemos en memoria, las consultamos)
        List<NotasDAO.NotaResumen> notas;
        if (notasCliente != null && !notasCliente.isEmpty()) {
            notas = notasCliente;
        } else {
            NotasDAO ndao = new NotasDAO();
            notas = ndao.listarNotasPorTelefonoResumen(tel);
        }

        NotasDAO ndao2 = new NotasDAO();
        List<CompraLinea> compras = construirCompras(ndao2, notas);
        List<PagoLinea> pagos = construirPagos(ndao2, notas);

        double saldoGlobal = (saldoGlobalCreditos > 0.0 || (notasCliente != null && !notasCliente.isEmpty()))
                ? saldoGlobalCreditos
                : calcularSaldoGlobal(notas);

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
            asesora = firstNonBlank(cr.getAsesoraEntrega(), cr.getAsesoraCita1(), cr.getAsesoraCita2());
        } else {
            fechaEvento = parseDate(cli.getFechaEvento());
            fechaPrueba1 = parseDate(cli.getFechaPrueba1());
            fechaEntrega = parseDate(cli.getFechaEntrega());
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

// Observaciones de las notas (folio + observación)
String observacionesNotas = construirObservacionesNotas(notas);
// Observaciones automáticas del cliente (solo si no hay de notas)
String observacionesAuto = construirObservacionesAuto(cli);

// PRIORIDAD: lo que está capturado en las notas.
// Solo si NO hay observaciones en ninguna nota, usamos las automáticas.
String observaciones;
if (!isBlank(observacionesNotas)) {
    observaciones = observacionesNotas;
} else {
    observaciones = observacionesAuto;
}



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
                pagos
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

    private static final DateTimeFormatter DF_IMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
            List<PagoLinea> pagos
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
        this.pagos = (pagos == null) ? Collections.emptyList() : pagos;
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        Graphics2D g2 = (Graphics2D) g;
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.setColor(Color.BLACK);

        if (pageIndex == 0) {
            printHojaDetalle(g2, pf);
        } else if (pageIndex == 1) {
            printComprasPagos(g2, pf);
        } else {
            return NO_SUCH_PAGE;
        }
        return PAGE_EXISTS;
    }

// ---------- Página 1: hoja de detalle ----------
private void printHojaDetalle(Graphics2D g2, PageFormat pf) {
    int w = (int) pf.getImageableWidth();
    int y = 30;

    Font fTitulo = new Font("SansSerif", Font.BOLD, 14);
    Font fSub    = new Font("SansSerif", Font.BOLD, 12);
    Font fLabel  = new Font("SansSerif", Font.BOLD, 9);   // labels en negritas
    Font fValue  = new Font("SansSerif", Font.PLAIN, 9);  // valores
    Font fNormal = fValue;

    // Encabezado
    g2.setFont(fTitulo);
    drawCentered(g2, "MIANOVIAS QUERETARO", w, y);
    y += 16;
    g2.setFont(fSub);
    drawCentered(g2, "HOJA DE DETALLE", w, y);
    y += 22;

    // ===== SALDO GLOBAL FUERA DE LA TABLA (ARRIBA DERECHA) =====
    int xTable = 20;
    int tableWidth = w - 40;

    String lblSaldo = "Saldo global créditos:";
    String valSaldo = formatMoney(saldoGlobal);

    g2.setFont(fLabel);
    int lblW = g2.getFontMetrics().stringWidth(lblSaldo);
    g2.setFont(fValue);
    int valW = g2.getFontMetrics().stringWidth(valSaldo);

    int totalSaldoW = lblW + 4 + valW;
    int xSaldo = xTable + tableWidth - totalSaldoW;
    int ySaldo = y;   // misma línea donde empieza la tabla

    g2.setFont(fLabel);
    g2.drawString(lblSaldo, xSaldo, ySaldo);
    g2.setFont(fValue);
    g2.drawString(valSaldo, xSaldo + lblW + 4, ySaldo);

    // un pequeño margen debajo del saldo antes de la tabla
    y += 10;

    // ===== TABLA DE DATOS DEL CLIENTE (PARTE SUPERIOR) =====
    int rowH = 12;

    // Modelo + talla en el mismo recuadro
    String modeloTalla = valueOrEmpty(modeloVestido);
    if (tallaVestido != null && !tallaVestido.isBlank()) {
        if (!modeloTalla.isEmpty()) modeloTalla += "   ";
        modeloTalla += "Talla: " + tallaVestido;
    }

    java.util.List<EncabezadoLinea> filas = new java.util.ArrayList<>();
    filas.add(new EncabezadoLinea("Cliente:",           valueOrEmpty(nombreCompleto),
                                  "Teléfono:",          valueOrEmpty(formatPhone(telefono2))));
    filas.add(new EncabezadoLinea("Celular:",           valueOrEmpty(formatPhone(celular)),
                                  "Folio de apartado:", valueOrEmpty(folioApartado)));
    filas.add(new EncabezadoLinea("Fecha operación:",   formatDate(fechaOperacion),
                                  "Color:",             valueOrEmpty(colorVestido)));
    // Modelo + talla en una sola celda
    filas.add(new EncabezadoLinea("Modelo vestido:",    modeloTalla,
                                  "Asesora:",           valueOrEmpty(asesora)));
    // Fecha evento en su propia fila (sin hora asociada)
    filas.add(new EncabezadoLinea("Fecha evento:",      formatDate(fechaEvento),
                                  "",                   ""));
    // Fecha de ajuste alineada con Hora ajuste
    filas.add(new EncabezadoLinea("Fecha de ajuste:",   formatDate(fechaAjuste),
                                  "Hora ajuste:",       valueOrEmpty(horaAjuste)));
    // Fecha de entrega alineada con Hora entrega
    filas.add(new EncabezadoLinea("Fecha de entrega:",  formatDate(fechaEntrega),
                                  "Hora entrega:",      valueOrEmpty(horaEntrega)));

    int headerHeight = filas.size() * rowH + 4;

    // Marco general de la tabla
    g2.setFont(fNormal);
    g2.drawRect(xTable, y, tableWidth, headerHeight);

    // Línea vertical que separa lado izquierdo y derecho
    int midX = xTable + tableWidth / 2;
    g2.drawLine(midX, y, midX, y + headerHeight);

    int col1LabelX = xTable + 6;
    int col1ValueX = xTable + tableWidth / 4;
    int col2LabelX = midX + 6;
    int col2ValueX = midX + tableWidth / 4;

    for (int i = 0; i < filas.size(); i++) {
        int rowTop = y + 4 + i * rowH;
        int rowBottom = rowTop + rowH;
        int baseY = rowBottom - 3;

        // Línea horizontal de la fila
        g2.drawLine(xTable, rowBottom, xTable + tableWidth, rowBottom);

        EncabezadoLinea f = filas.get(i);

        // Labels en negritas
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

    // Dejamos un margen debajo del encabezado
    y = y + headerHeight + 26;

    // --------- SELECCIÓN DE ACCESORIOS (izquierda) ----------
    int anchoTotal = w - 40;
    int anchoIzq   = (int) (anchoTotal * 0.55);
    int xAcc       = 20;
    int rowHeight  = 14;
    String[] accesorios = {"Tocado", "Aretes", "Collar", "Zapatos", "Ligero", "Ramo"};

    int hAcc = accesorios.length * rowHeight + 24;
    g2.drawRect(xAcc, y, anchoIzq, hAcc);
    g2.setFont(fSub);
    g2.drawString("SELECCIÓN DE ACCESORIOS", xAcc + 6, y + 12);
    g2.setFont(fNormal);
    int yLine = y + 18;
    g2.drawLine(xAcc, yLine, xAcc + anchoIzq, yLine);

    int yAcc = yLine + rowHeight;
    for (String a : accesorios) {
        g2.drawString(a, xAcc + 6, yAcc - 4);
        g2.drawLine(xAcc, yAcc, xAcc + anchoIzq, yAcc);
        yAcc += rowHeight;
    }

    // --------- MEDIDAS PARA AJUSTE (derecha) ----------
    int xMed    = xAcc + anchoIzq + 20;
    int anchoMed = w - xMed - 20;
    int hMed     = 3 * rowHeight + 24;

    g2.drawRect(xMed, y, anchoMed, hMed);
    g2.setFont(fSub);
    g2.drawString("MEDIDAS PARA AJUSTE", xMed + 6, y + 12);
    g2.setFont(fNormal);
    int yMedLine = y + 18;
    g2.drawLine(xMed, yMedLine, xMed + anchoMed, yMedLine);

    int yMed = yMedLine + rowHeight;
    drawField(g2, "Busto:",   formatMedida(busto),   xMed + 6, xMed + 90, yMed); yMed += rowHeight;
    drawField(g2, "Cintura:", formatMedida(cintura), xMed + 6, xMed + 90, yMed); yMed += rowHeight;
    drawField(g2, "Cadera:",  formatMedida(cadera),  xMed + 6, xMed + 90, yMed);

    y = y + Math.max(hAcc, hMed) + 26;

    // --------- OBSERVACIONES ----------
    int hObs = 90;
    g2.drawRect(20, y, w - 40, hObs);
    g2.setFont(fSub);
    g2.drawString("OBSERVACIONES:", 26, y + 14);
    g2.setFont(fNormal);
    drawWrappedText(g2, observaciones, 26, y + 30, w - 60, 12);

    y += hObs + 20;

    // --------- MEDIDAS GENERALES (tabla fija al pie) ----------
    printMedidasGenerales(g2, w, y);
}
/** Tabla fija "MEDIDAS GENERALES" como en el formato físico. */
private void printMedidasGenerales(Graphics2D g2, int pageWidth, int yTop) {
    Font fSub    = new Font("SansSerif", Font.BOLD, 9);
    Font fNormal = new Font("SansSerif", Font.PLAIN, 8);

    int x      = 20;
    int ancho  = pageWidth - 40;
    int rowH   = 12;

    String[][] filas = {
            {"Ancho Espalda",     "Altura Busto",  "Falda Corta"},
            {"Talle Trasero",     "Sep Busto",     "Largo Manga"},
            {"Talle Delantero",   "Corte Imperio", "Contorno Brazo"},
            {"Contorno Busto",    "Cont bb-Talle", "Contorno Manga"},
            {"Contorno Bajo Bust","Escote Delant", ""},
            {"Contorno Cintura",  "Escote Trasero",""},
            {"Contorno Cadera",   "LargoFalda",    ""}
    };

    int alto = filas.length * rowH + 24;

    // Marco general
    g2.setFont(fSub);
    g2.drawRect(x, yTop, ancho, alto);
    g2.drawString("MEDIDAS GENERALES", x + 6, yTop + 12);

    int yHeader = yTop + 18;
    g2.drawLine(x, yHeader, x + ancho, yHeader);

    int colW   = ancho / 3;
    int xCol1  = x + colW;
    int xCol2  = x + 2 * colW;

    // Líneas verticales
    g2.drawLine(xCol1, yHeader, xCol1, yTop + alto);
    g2.drawLine(xCol2, yHeader, xCol2, yTop + alto);

    g2.setFont(fNormal);

    // Filas con labels
    for (int i = 0; i < filas.length; i++) {
        int lineY = yHeader + (i + 1) * rowH;
        int baseY = lineY - 3;
        String[] row = filas[i];

        if (row[0] != null && !row[0].isBlank())
            g2.drawString(row[0], x + 4, baseY);
        if (row[1] != null && !row[1].isBlank())
            g2.drawString(row[1], xCol1 + 4, baseY);
        if (row[2] != null && !row[2].isBlank())
            g2.drawString(row[2], xCol2 + 4, baseY);

        g2.drawLine(x, lineY, x + ancho, lineY);
    }
}

// ---------- Página 2: COMPRAS y PAGOS ----------
private void printComprasPagos(Graphics2D g2, PageFormat pf) {
    int w = (int) pf.getImageableWidth();
    int h = (int) pf.getImageableHeight();
    int x = 20;
    int y = 30;
    int rowHeight = 12;

    Font fTitulo = new Font("SansSerif", Font.BOLD, 12);
    Font fNormal = new Font("SansSerif", Font.PLAIN, 8);

    int tableWidth = w - 40;

    // ===== COMPRAS =====
    g2.setFont(fTitulo);
    g2.drawString("COMPRAS", x, y);
    y += rowHeight;

    g2.setFont(fNormal);

    // Anchos relativos al ancho disponible
    int colFolio   = (int) (tableWidth * 0.09);
    int colFecha   = (int) (tableWidth * 0.11);
    int colCodigo  = (int) (tableWidth * 0.11);
    int colArticulo= (int) (tableWidth * 0.20);
    int colModelo  = (int) (tableWidth * 0.12);
    int colTalla   = (int) (tableWidth * 0.06);
    int colColor   = (int) (tableWidth * 0.09);
    int colPrecio  = (int) (tableWidth * 0.09);
    int colDesc    = (int) (tableWidth * 0.06);
    int colPagar   = tableWidth - (colFolio + colFecha + colCodigo + colArticulo +
                                   colModelo + colTalla + colColor + colPrecio + colDesc);

    int xx = x;
    g2.drawString("# Folio",        xx, y); xx += colFolio;
    g2.drawString("Fecha compra",   xx, y); xx += colFecha;
    g2.drawString("Código",         xx, y); xx += colCodigo;
    g2.drawString("Artículo",       xx, y); xx += colArticulo;
    g2.drawString("Modelo",         xx, y); xx += colModelo;
    g2.drawString("Talla",          xx, y); xx += colTalla;
    g2.drawString("Color",          xx, y); xx += colColor;
    g2.drawString("Precio",         xx, y); xx += colPrecio;
    g2.drawString("Desc%",          xx, y); xx += colDesc;
    g2.drawString("A pagar",        xx, y);

    y += 4;

    double totalCompras = 0.0;
    for (CompraLinea c : compras) {
        if (y > h / 2) break;  // mitad superior para compras
        xx = x;
        g2.drawString(valueOrEmpty(c.folio),                    xx, y); xx += colFolio;
        g2.drawString(formatDate(c.fecha),                      xx, y); xx += colFecha;
        g2.drawString(valueOrEmpty(c.codigoArticulo),           xx, y); xx += colCodigo;
        g2.drawString(trimTo(g2, valueOrEmpty(c.articulo),
                             colArticulo - 4),                  xx, y); xx += colArticulo;
        g2.drawString(trimTo(g2, valueOrEmpty(c.modelo),
                             colModelo - 4),                    xx, y); xx += colModelo;
        g2.drawString(valueOrEmpty(c.talla),                    xx, y); xx += colTalla;
        g2.drawString(valueOrEmpty(c.color),                    xx, y); xx += colColor;
        g2.drawString(formatMoney(c.precio),                    xx, y); xx += colPrecio;
        g2.drawString(formatPercent(c.descuento),               xx, y); xx += colDesc;
        g2.drawString(formatMoney(c.precioPagar),               xx, y);

        if (c.precioPagar != null) {
            totalCompras += c.precioPagar;
        }
        y += rowHeight;
    }

    y += 4;
    g2.setFont(fTitulo.deriveFont(10f));
    g2.drawString("TOTAL DE COMPRAS: " + formatMoney(totalCompras), x, y);

    // ===== PAGOS Y/O DEVOLUCIONES =====
    y += 24;
    g2.setFont(fTitulo);
    g2.drawString("PAGOS Y/O DEVOLUCIONES", x, y);
    y += rowHeight;
    g2.setFont(fNormal);

    int colFolio2   = (int) (tableWidth * 0.13);
    int colFecha2   = (int) (tableWidth * 0.17);
    int colConcepto = (int) (tableWidth * 0.42);
    int colImporte  = (int) (tableWidth * 0.14);
    int colSaldo    = tableWidth - (colFolio2 + colFecha2 + colConcepto + colImporte);

    xx = x;
    g2.drawString("# Folio",    xx, y); xx += colFolio2;
    g2.drawString("Fecha pago", xx, y); xx += colFecha2;
    g2.drawString("Concepto",   xx, y); xx += colConcepto;
    g2.drawString("Importe",    xx, y); xx += colImporte;
    g2.drawString("Saldo",      xx, y);

    y += 4;

    double totalPagos = 0.0;
    for (PagoLinea p : pagos) {
        if (y > h - 40) break;
        xx = x;
        g2.drawString(valueOrEmpty(p.folio),                   xx, y); xx += colFolio2;
        g2.drawString(formatDate(p.fecha),                     xx, y); xx += colFecha2;
        g2.drawString(trimTo(g2, valueOrEmpty(p.concepto),
                             colConcepto - 4),                 xx, y); xx += colConcepto;
        g2.drawString(formatMoney(p.importe),                  xx, y); xx += colImporte;
        g2.drawString(formatMoney(p.saldo),                    xx, y);

        if (p.importe != null) {
            totalPagos += p.importe;
        }
        y += rowHeight;
    }

    y += 4;
    g2.setFont(fTitulo.deriveFont(10f));
    g2.drawString("TOTAL PAGOS/DEVOLUCIONES: " + formatMoney(totalPagos), x, y);
}

    // ---------- helpers gráficos ----------

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
        // si no trae 10 dígitos, lo regresamos como venga
        return tel.trim();
    }
    String p1 = digits.substring(0, 3);
    String p2 = digits.substring(3, 6);
    String p3 = digits.substring(6);
    return p1 + "-" + p2 + "-" + p3;
}

}
