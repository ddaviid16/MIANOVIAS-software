package Vista;

import Controlador.NotasDAO;
import Modelo.NotaDetalle;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;

// === imports de tus modelos/daos usados por las utilidades de impresión ===
import Controlador.EmpresaDAO;
import Modelo.Empresa;
import Modelo.Nota;
import Modelo.PagoFormas;

/**
 * Re-imprimir nota:
 * - Buscar por teléfono del cliente.
 * - Tabla superior: todas las notas del cliente (venta, abono, cancelación, etc.).
 * - Al seleccionar una nota, se muestra su detalle en la tabla inferior.
 * - Botón "Re-imprimir" llama al motor de impresión integrado (o al callback si te interesa inyectarlo).
 *
 * No agrega título "Menú", usa solo el contenido del panel.
 */
public class ReimprimirNotaPanel extends JPanel {
    private static String tipoKey(String s){
    if (s == null) return "";
    String t = Normalizer.normalize(s, Normalizer.Form.NFD);
    t = t.replaceAll("\\p{InCombiningDiacriticalMarks}+", ""); // quita acentos
    return t.trim().toUpperCase();
}


    private final JTextField txtTelefono = new JTextField();
    private final JButton btBuscar = new JButton("Buscar");

    // Tabla de cabecera (notas)
    private final DefaultTableModel modelNotas = new DefaultTableModel(
            new String[]{"# Nota", "Tipo", "Folio", "Fecha", "Total", "Saldo", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Integer.class;         // numero_nota
                case 4, 5 -> Double.class;       // total, saldo
                default -> String.class;
            };
        }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    // Tabla de detalle (renglones de la nota)
    private final DefaultTableModel modelDet = new DefaultTableModel(
            new String[]{"Código", "Artículo", "Marca", "Modelo", "Talla", "Color", "Precio", "%Desc", "Subtotal"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return (c==0) ? Integer.class : (c>=6 ? Double.class : String.class);
        }
    };
    private final JTable tbDetalle = new JTable(modelDet);

    private final JButton btReimprimir = new JButton("Re-imprimir");

    /** Callback opcional: (numeroNota, tipo) -> imprimir */
    private final BiConsumer<Integer, String> onReimprimir;

    /** Constructor por defecto: reimpresión integrada. */
    public ReimprimirNotaPanel() {
        this(null);
    }

    /** Constructor con callback de impresión (si quieres inyectar tu propio motor). */
    public ReimprimirNotaPanel(BiConsumer<Integer, String> onReimprimir) {
        this.onReimprimir = (onReimprimir != null) ? onReimprimir : this::reimprimirDesdeUI;
        buildUI();
        hookEvents();
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtro superior
        JPanel filtro = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        filtro.add(new JLabel("Teléfono:"), c);
        c.weightx = 1; c.gridx = 1;
        filtro.add(txtTelefono, c);
        c.weightx = 0; c.gridx = 2;
        filtro.add(btBuscar, c);
        add(filtro, BorderLayout.NORTH);

        // Master/Detail
        tbNotas.setRowHeight(22);
        tbNotas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tbNotas.setAutoCreateRowSorter(true);
        TableRowSorter<?> sorter = (TableRowSorter<?>) tbNotas.getRowSorter();
        sorter.toggleSortOrder(3); // primero ASC
        sorter.toggleSortOrder(3); // ahora DESC

        JScrollPane spNotas = new JScrollPane(tbNotas);
        spNotas.setBorder(BorderFactory.createTitledBorder("Notas del cliente"));

        tbDetalle.setRowHeight(22);
        JScrollPane spDet = new JScrollPane(tbDetalle);
        spDet.setBorder(BorderFactory.createTitledBorder("Detalle de la nota seleccionada"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spNotas, spDet);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        // Botonera inferior
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btReimprimir);
        add(south, BorderLayout.SOUTH);
    }

    private void hookEvents() {
        btBuscar.addActionListener(_e -> cargarNotas());
        txtTelefono.addActionListener(_e -> cargarNotas());

        tbNotas.getSelectionModel().addListSelectionListener(_e -> {
            if (!_e.getValueIsAdjusting()) cargarDetalleSeleccionada();
        });

        btReimprimir.addActionListener(_e -> {
            int row = tbNotas.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Selecciona una nota.", "Atención",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tbNotas.convertRowIndexToModel(row);
            Integer numero = (Integer) modelNotas.getValueAt(modelRow, 0);
            String tipo = (String) modelNotas.getValueAt(modelRow, 1);
            try {
                onReimprimir.accept(numero, tipo); // usa el integrado (o tu callback inyectado)
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al re-imprimir: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void cargarNotas() {
        modelNotas.setRowCount(0);
        modelDet.setRowCount(0);

        String tel = txtTelefono.getText().trim();
        if (tel.isBlank()) return;

        try {
            List<NotasDAO.NotaResumen> lista = new NotasDAO().listarNotasPorTelefonoResumen(tel);
            for (NotasDAO.NotaResumen r : lista) {
                modelNotas.addRow(new Object[]{
                        r.numero, r.tipo, nullToEmpty(r.folio),
                        r.fecha == null ? "" : r.fecha.toString(),
                        r.total == null ? 0.0 : r.total,
                        r.saldo == null ? 0.0 : r.saldo,
                        nullToEmpty(r.status)
                });
            }
            if (modelNotas.getRowCount() > 0) {
                tbNotas.setRowSelectionInterval(0,0);
            } else {
                JOptionPane.showMessageDialog(this, "El cliente no tiene notas registradas.",
                        "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando notas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDetalleSeleccionada() {
        modelDet.setRowCount(0);
        int row = tbNotas.getSelectedRow();
        if (row < 0) return;

        int modelRow = tbNotas.convertRowIndexToModel(row);
        Integer numero = (Integer) modelNotas.getValueAt(modelRow, 0);

        try {
            List<NotaDetalle> det = new NotasDAO().listarDetalleDeNota(numero);
            for (NotaDetalle d : det) {
                modelDet.addRow(new Object[]{
                        d.getCodigoArticulo(),
                        nullToEmpty(d.getArticulo()),
                        nullToEmpty(d.getMarca()),
                        nullToEmpty(d.getModelo()),
                        nullToEmpty(d.getTalla()),
                        nullToEmpty(d.getColor()),
                        zero(d.getPrecio()),
                        zero(d.getDescuento()),
                        zero(d.getSubtotal())
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error cargando detalle: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String nullToEmpty(String s){ return s==null ? "" : s; }
    private static Double zero(Double d){ return d==null? 0.0 : d; }

    /* ===================================================================
     * ===============  PEGAMENTO DE REIMPRESIÓN (integrado)  =============
     * =================================================================== */

    private void reimprimirDesdeUI(Integer numeroNota, String tipoRaw) {
    int row = tbNotas.getSelectedRow();
    if (row < 0) {
        JOptionPane.showMessageDialog(this, "Selecciona una nota.", "Atención", JOptionPane.WARNING_MESSAGE);
        return;
    }
    int modelRow = tbNotas.convertRowIndexToModel(row);

    String folio = String.valueOf(modelNotas.getValueAt(modelRow, 2));
    Double total = asDouble(modelNotas.getValueAt(modelRow, 4));
    Double saldo = asDouble(modelNotas.getValueAt(modelRow, 5));
    String tipoCelda = (String) modelNotas.getValueAt(modelRow, 1);

    // 1) Inferir tipo por texto/heurística existente
    TipoNota tipo = inferirTipoDesdeFila(tipoCelda, total, saldo);

    // 2) Fallback adicional: si el folio inicia con D => devolución (ej. D0007)
    String folioU = folio == null ? "" : folio.trim().toUpperCase();
    if (folioU.startsWith("D")) {
        tipo = TipoNota.DEVOLUCION;
    }

    // 3) Cargar detalle
    List<NotaDetalle> dets;
    try {
        dets = new NotasDAO().listarDetalleDeNota(numeroNota);
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Error cargando detalle: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // 4) Reconstruir Nota mínima
    Nota nota = new Nota();
    try { nota.getClass().getMethod("setNumero", Integer.class).invoke(nota, numeroNota); } catch (Exception ignore) {}
    try { nota.getClass().getMethod("setTotal",  Double.class).invoke(nota, total);       } catch (Exception ignore) {}
    try { nota.getClass().getMethod("setSaldo",  Double.class).invoke(nota, saldo);       } catch (Exception ignore) {}
    try { nota.getClass().getMethod("setTelefono", String.class).invoke(nota, txtTelefono.getText().trim()); } catch (Exception ignore) {}

    // === Cargar observaciones de la nota, si existen ===
String observacionesTexto = "";
try {
    Controlador.NotasObservacionesDAO obsDAO = new Controlador.NotasObservacionesDAO();
    observacionesTexto = obsDAO.getByNota(numeroNota);
} catch (SQLException ex) {
    observacionesTexto = "";
}

    // 5) Pagos aproximados para reimpresión (no afecta devolución)
    String tipoStrParaPagos = switch (tipo) {
        case ABONO -> "ABONO";
        case ANTICIPO -> "ANTICIPO";
        case DEVOLUCION -> "DEVOLUCION";
        default -> "CONTADO";
    };
    PagoFormas pagos = crearPagoFormasParaReimpresion(total, saldo, tipoStrParaPagos);

    // 6) Empresa
    EmpresaInfo emp = cargarEmpresaInfo();

    // 7) Elegir formato correcto (AQUÍ ajustamos DEVOLUCIÓN)
    Printable printable;

switch (tipo) {
    case DEVOLUCION: {
        // 1) Recalcular monto DV desde el detalle
        double montoDV = 0d;
        if (dets != null) {
            for (NotaDetalle d : dets) {
                montoDV += (d.getSubtotal() == null ? 0d : d.getSubtotal());
            }
        }
        montoDV = Math.round(montoDV * 100.0) / 100.0;

        // 2) Por defecto (CN) el saldo a favor es el monto devuelto
        double saldoAFavor = montoDV;

        // 3) Si la venta origen fue CR, limitar a lo efectivamente pagado
        Integer origen = leerNotaOrigenPorDV(numeroNota);
        if (origen != null) {
            NotaHead oh = leerNotaHead(origen);
            if (oh != null && "CR".equalsIgnoreCase(oh.tipo)) {
                double pagadoActual = Math.max(0d, oh.total - oh.saldo);
                saldoAFavor = Math.min(montoDV, pagadoActual);
            }
        }



        // 4) Inyectar el saldo a favor en la Nota para que el formato lo imprima
        setNotaTotalSeguro(nota, saldoAFavor);

        printable = construirPrintableDevolucion(emp, nota, dets, folio);
        break;
    }

case ABONO: {
    // 1. Dame la venta ligada a este abono
    Integer notaOrigen = null;
    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(
             "SELECT nota_relacionada FROM Notas WHERE numero_nota = ?")) {
        ps.setInt(1, numeroNota);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(1);
                if (!rs.wasNull()) notaOrigen = v;
            }
        }
    } catch (Exception ignore) { }

    // 2. Cargar detalle EXACTO de esa venta origen
    List<NotaDetalle> detsOrigen = java.util.Collections.emptyList();
    if (notaOrigen != null) {
        try {
            detsOrigen = new NotasDAO().listarDetalleDeNota(notaOrigen);
        } catch (SQLException ex) {
            detsOrigen = java.util.Collections.emptyList();
        }
    }

    // 3. Observaciones de la venta original
    String observacionesOriginal = "";
    if (notaOrigen != null) {
        try {
            Controlador.NotasObservacionesDAO obsDAO = new Controlador.NotasObservacionesDAO();
            observacionesOriginal = obsDAO.getByNota(notaOrigen);
        } catch (Exception ignore) {}
    }

    double abonoReal = (total == null ? 0d : total);
    double saldoPosterior = (saldo == null ? 0d : saldo);

    printable = construirPrintableAbono(
        emp, nota, detsOrigen, pagos, abonoReal, saldoPosterior, folio, observacionesOriginal
    );
    break;
}
case ANTICIPO: {
        LocalDate fEntrega = null, fEvento = null; String asesor = "";
        printable = construirPrintableCreditoEmpresarial(
                emp, nota, dets, pagos, fEntrega, fEvento, asesor, folio, "", "", observacionesTexto);
        break;
    }

    case CONTADO:
    default: {
        LocalDate fEntrega = null, fEvento = null; String asesor = "";
        printable = construirPrintableContadoEmpresarial(
                emp, nota, dets, pagos, fEntrega, fEvento, asesor, folio, "", "", observacionesTexto);
        break;
    }
}


    // 8) Imprimir
    imprimirYConfirmarAsync(
            printable,
            () -> JOptionPane.showMessageDialog(this, "Re-impresión completada.", "Listo", JOptionPane.INFORMATION_MESSAGE),
            () -> JOptionPane.showMessageDialog(this, "Impresión cancelada o fallida.", "Aviso", JOptionPane.WARNING_MESSAGE)
    );
}




    private static Double asDouble(Object v) {
        if (v == null) return 0d;
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0d; }
    }
    private enum TipoNota { CONTADO, ANTICIPO, ABONO, DEVOLUCION }

private TipoNota inferirTipoDesdeFila(String tipoCelda, Double total, Double saldo) {
    String t = normaliza(tipoCelda); // mayúsculas, sin acentos

    // === Devolución ===
    if (t.contains("DEVOLUCION") || t.contains("DEVOLUCIÓN") || t.contains("CAMBIO")
        || t.equals("DV") || t.equals("D")) {
        return TipoNota.DEVOLUCION;
    }

    // === Abono ===
    if (t.contains("ABONO") || t.contains("PAGO PARCIAL") || t.equals("AB")) {
        return TipoNota.ABONO;
    }

    // === Anticipo / Crédito ===
    if (t.contains("CREDITO") || t.contains("ANTICIPO") || t.contains("APARTADO")
        || t.contains("SEPARADO") || t.equals("CR")) {
        return TipoNota.ANTICIPO;
    }

    // === Contado ===
    if (t.equals("CN") || t.equals("C") || t.contains("CONTADO")) {
        return TipoNota.CONTADO;
    }

    // Heurística de respaldo: si hay saldo pendiente, trátalo como ANTICIPO
    double s = (saldo == null ? 0d : saldo);
    double tot = (total == null ? 0d : total);
    if (s > 0.0009 && s <= Math.max(tot, 0d)) return TipoNota.ANTICIPO;

    return TipoNota.CONTADO;
}


/** Quita acentos/espacios raros y pone en mayúsculas. */
private String normaliza(String s) {
    if (s == null) return "";
    String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");             // sin acentos
    n = n.toUpperCase().replaceAll("\\s+", " ").trim();
    return n;
}


    /** Si no tienes el desglose, usa esta aproximación (no cambia tus formatos). */
    private PagoFormas crearPagoFormasParaReimpresion(Double total, Double saldo, String tipo) {
        double t = (total == null ? 0d : total);
        double s = (saldo == null ? 0d : saldo);
        double efectivo;
        switch ((tipo == null ? "" : tipo).toUpperCase()) {
            case "ABONO":    efectivo = t; break;                // monto abonado
            case "CREDITO":
            case "ANTICIPO": efectivo = Math.max(0d, t - s); break; // anticipo estimado
            default:         efectivo = t; break;                // contado (total pagado)
        }
        PagoFormas p = new PagoFormas();
        try { p.setTarjetaCredito(0d); } catch (Exception ignore) {}
        try { p.setTarjetaDebito(0d); } catch (Exception ignore) {}
        try { p.setAmericanExpress(0d); } catch (Exception ignore) {}
        try { p.setTransferencia(0d); } catch (Exception ignore) {}
        try { p.setDeposito(0d); } catch (Exception ignore) {}
        try { p.setEfectivo(efectivo); } catch (Exception ignore) {}
        return p;
    }

    /* ==========================================================
     ========== UTILIDADES DE IMPRESIÓN (AS IS, TUS FORMATOS) ===
     ========================================================== */

    /** Diálogo de impresión con espera y confirmación. */
    private void imprimirYConfirmarAsync(Printable printable, Runnable onOk, Runnable onFail) {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(printable);
            if (!job.printDialog()) { onFail.run(); return; }

            JDialog wait = new JDialog(SwingUtilities.getWindowAncestor(this), "Imprimiendo…",
                    Dialog.ModalityType.DOCUMENT_MODAL);
            JPanel p = new JPanel(new BorderLayout(10,10));
            p.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));
            p.add(new JLabel("Esperando confirmación de la impresora…"), BorderLayout.NORTH);
            javax.swing.JProgressBar bar = new javax.swing.JProgressBar(); bar.setIndeterminate(true);
            p.add(bar, BorderLayout.CENTER);
            wait.setContentPane(p);
            wait.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            wait.setSize(360,110);
            wait.setLocationRelativeTo(this);

            new Thread(() -> {
                try {
                    job.print();
                    SwingUtilities.invokeLater(() -> { wait.dispose(); onOk.run(); });
                } catch (PrinterException ex) {
                    SwingUtilities.invokeLater(() -> { wait.dispose(); onFail.run(); });
                }
            }, "reprint-job").start();

            wait.setVisible(true);
        } catch (Exception ex) {
            onFail.run();
        }
    }

    // ---------- Empresa ----------
    private static class EmpresaInfo {
        String razonSocial, nombreFiscal, rfc;
        String calleNumero, colonia, cp, ciudad, estado;
        String whatsapp, telefono, instagram, facebook, tiktok, correo, web;
        BufferedImage logo;
    }

    /** Lee la nota origen de una DV desde la tabla Devoluciones. */
private Integer leerNotaOrigenPorDV(int numeroDv) {
    final String sql = "SELECT nota_origen FROM Devoluciones WHERE numero_nota_dv=?";
    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setInt(1, numeroDv);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        }
    } catch (Exception ignore) { }
    return null;
}
/** Lee la nota origen de un abono desde la tabla NotasAbonos. */
private Integer leerNotaOrigenPorAbono(int numeroAbono) {
    
        final String sql = "SELECT folio FROM Notas WHERE numero_nota = ?";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroAbono);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    if (!rs.wasNull()) return v;
                }
            }
        } catch (Exception ignore) { }
    
    return null;
}



/** Cabecera mínima de una nota para calcular pagos actuales. */
private static class NotaHead { String tipo; double total; double saldo; }

/** Lee tipo/total/saldo actuales de una nota (post DV). */
private NotaHead leerNotaHead(int numeroNota) {
    final String sql = "SELECT tipo, COALESCE(total,0), COALESCE(saldo,0) FROM Notas WHERE numero_nota=?";
    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setInt(1, numeroNota);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                NotaHead h = new NotaHead();
                h.tipo  = rs.getString(1);
                h.total = rs.getDouble(2);
                h.saldo = rs.getDouble(3);
                return h;
            }
        }
    } catch (Exception ignore) { }
    return null;
}
/** Hace setTotal(...) ya sea con Double o con double por reflexión. */
private void setNotaTotalSeguro(Object nota, double valor) {
    try {
        nota.getClass().getMethod("setTotal", Double.class).invoke(nota, valor);
        return;
    } catch (Exception ignore) { }
    try {
        nota.getClass().getMethod("setTotal", double.class).invoke(nota, valor);
    } catch (Exception ignore) { }
}

    // Lee de BD el número (PK) de la empresa a usar para impresión. Toma la primera fila.
    private Integer obtenerNumeroEmpresaBD() {
        final String sql = "SELECT numero_empresa FROM Empresa ORDER BY numero_empresa LIMIT 1";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        } catch (Exception ignore) { }
        return null;
    }

    private EmpresaInfo cargarEmpresaInfo() {
        EmpresaInfo info = new EmpresaInfo();
        try {
            EmpresaDAO edao = new EmpresaDAO();
            Integer numEmpresa = obtenerNumeroEmpresaBD();
            Empresa e = edao.buscarPorNumero(numEmpresa);
            if (e != null) {
                info.razonSocial  = n(e.getRazonSocial());
                info.nombreFiscal = n(e.getNombreFiscal());
                info.rfc          = n(e.getRfc());
                info.calleNumero  = n(e.getCalleNumero());
                info.colonia      = n(e.getColonia());
                info.cp           = n(e.getCodigoPostal());
                info.ciudad       = n(e.getCiudad());
                info.estado       = n(e.getEstado());
                info.whatsapp     = n(e.getWhatsapp());
                info.telefono     = n(e.getTelefono());
                info.instagram    = n(e.getInstagram());
                info.facebook     = n(e.getFacebook());
                info.tiktok       = n(e.getTiktok());
                info.correo       = n(e.getCorreo());
                info.web          = n(e.getPaginaWeb());
                if (e.getLogo()!=null) {
                    try { info.logo = ImageIO.read(new ByteArrayInputStream(e.getLogo())); } catch(Exception ignore){}
                }
            }
        } catch (Exception ex) {
            info.razonSocial = "MIANOVIAS";
        }
        return info;
    }

    /* ==========================================================
       =============== FORMATOS (CONTADO / CRÉDITO) =============
       ========================================================== */

    // ===== CONTADO ===== (formato tal cual)
    private Printable construirPrintableContadoEmpresarial(
            EmpresaInfo emp, Modelo.Nota n, List<Modelo.NotaDetalle> dets, Modelo.PagoFormas p,
            java.time.LocalDate fechaEntregaMostrar, java.time.LocalDate fechaEventoMostrar,
            String asesorNombre, String folioTxt,
            String clienteNombreFallback, String tel2Fallback, String observacionesTexto) {

        final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

        final String tel1      = (n.getTelefono() == null) ? "" : n.getTelefono();
        final String fechaHoy  = java.time.LocalDate.now().format(MX);
        final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaEntregaMostrar.format(MX);
        final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaEventoMostrar.format(MX);
        final double total     = (n.getTotal() == null) ? 0d : n.getTotal();

        final double tc = (p.getTarjetaCredito()   == null) ? 0d : p.getTarjetaCredito();
        final double td = (p.getTarjetaDebito()    == null) ? 0d : p.getTarjetaDebito();
        final double am = (p.getAmericanExpress()  == null) ? 0d : p.getAmericanExpress();
        final double tr = (p.getTransferencia()    == null) ? 0d : p.getTransferencia();
        final double dp = (p.getDeposito()         == null) ? 0d : p.getDeposito();
        final double ef = (p.getEfectivo()         == null) ? 0d : p.getEfectivo();

        // Cliente (nombre/tel2 y pruebas si existen)
        String tmpNombre  = (clienteNombreFallback == null) ? "" : clienteNombreFallback;
        String tmpTel2    = (tel2Fallback == null) ? "" : tel2Fallback;
        String tmpPrueba1 = "";
        String tmpPrueba2 = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
            if (cr != null) {
                if (cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank())
                    tmpNombre = cr.getNombreCompleto();
                if (cr.getTelefono2() != null) tmpTel2 = cr.getTelefono2();
                if (cr.getFechaPrueba1() != null) tmpPrueba1 = cr.getFechaPrueba1().format(MX);
                if (cr.getFechaPrueba2() != null) tmpPrueba2 = cr.getFechaPrueba2().format(MX);
            }
        } catch (Exception ignore) { }
        // "Congela" valores para el Printable
        final String fCliNombre  = tmpNombre;
        final String fCliTel2    = tmpTel2;
        final String fCliPrueba1 = tmpPrueba1;
        final String fCliPrueba2 = tmpPrueba2;

        // Medidas
        String medBusto = "", medCintura = "", medCadera = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
            if (raw != null) {
                for (java.util.Map.Entry<String,String> e : raw.entrySet()) {
                    String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                    String v = e.getValue() == null ? "" : e.getValue();
                    if (k.equals("busto"))      medBusto   = v;
                    else if (k.equals("cintura")) medCintura = v;
                    else if (k.equals("cadera"))  medCadera  = v;
                }
            }
        } catch (Exception ignore) { }

        StringBuilder _med = new StringBuilder();
        if (!medBusto.isBlank())   { _med.append("Busto: ").append(medBusto); }
        if (!medCintura.isBlank()){ if (_med.length()>0) _med.append("   "); _med.append("Cintura: ").append(medCintura); }
        if (!medCadera.isBlank())  { if (_med.length()>0) _med.append("   "); _med.append("Cadera: ").append(medCadera); }
        final String medidasFmt = _med.toString();

        final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;

        // Obsequios (reimpresión: vacío)
        final java.util.List<String> obsequiosPrint = java.util.Collections.emptyList();

        return new Printable() {
            @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                if (pageIndex > 0) return NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) g;

                final int MARGIN = 36;
                final int x0 = (int) Math.round(pf.getImageableX());
                final int y0 = (int) Math.round(pf.getImageableY());
                final int W  = (int) Math.round(pf.getImageableWidth());
                int x = x0 + MARGIN;
                int y = y0 + MARGIN;
                int w = W  - (MARGIN * 2);

                final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                final Font fText    = g2.getFont().deriveFont(10.2f);
                final Font fSmall   = g2.getFont().deriveFont(8.8f);

                int leftTextX = x;
                int headerH   = 0;
                if (emp.logo != null) {
                    int hLogo = 56;
                    int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                    g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                    leftTextX = x + wLogo + 16;
                    headerH   = Math.max(headerH, hLogo);
                }

                int infoRightWidth = w - (leftTextX - x);
                int infoTextWidth  = infoRightWidth - 160;

                g2.setFont(fH1);
                int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                g2.setFont(fSmall);
                yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);

                String dir = joinNonBlank(", ",
                        emp.calleNumero, emp.colonia,
                        (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                        emp.ciudad, emp.estado);
                yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);

                yy = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Tel: ", emp.telefono),
                        labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

                g2.setFont(fH1);
                rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                final int rightColW = 120;
                final int xRight    = x + w - rightColW;
                int yRight          = y + 22;

                g2.setFont(fSmall);
                yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
                yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

                BufferedImage igIcon = loadIcon("instagram.png");
                BufferedImage fbIcon = loadIcon("facebook.png");
                BufferedImage ttIcon = loadIcon("tiktok.png");

                yRight = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRight, rightColW, 12, 6);
                yRight = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRight, rightColW, 12, 6);
                yRight = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRight, rightColW, 12, 6);

                int leftBlockH  = Math.max(yy - y, headerH);
                int rightBlockH = Math.max(0, yRight - y);
                int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;

                int afterTail = y + usedHeader;

                g2.setFont(fTitle);
                center(g2, "NOTA DE VENTA", x, w, afterTail + 14);
                y = afterTail + 32;

                g2.setFont(fSection);
                g2.drawString("Datos del cliente", x, y);
                y += 13;

                final int gapCols = 24;
                final int leftW   = (w - gapCols) / 2;
                final int rightW  = w - gapCols - leftW;

                int yLeft  = y;
                int yRight2 = y;

                g2.setFont(fText);
                yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(fCliNombre)), x, yLeft, leftW);
                yLeft  = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Teléfono: ", safe(tel1)),
                        labelIf("Teléfono 2: ", safe(fCliTel2))), x, yLeft + 2, leftW);
                if (!medidasFmt.isBlank()) {
                    yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);
                }

                yRight2 = drawWrapped(g2, labelIf("Fecha de venta: ", fechaHoy), x + leftW + gapCols, yRight2, rightW);
                if (!fEvento.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de evento: ", fEvento), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!fCliPrueba1.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", fCliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!fCliPrueba2.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", fCliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!fEntrega.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de entrega: ", fEntrega), x + leftW + gapCols, yRight2 + 2, rightW);
                if (asesorNombre != null && !asesorNombre.isBlank())
                    yRight2 = drawWrapped(g2, labelIf("Asesor/a: ", asesorNombre), x + leftW + gapCols, yRight2 + 2, rightW);

                y = Math.max(yLeft, yRight2) + 10;

                final int colSubW  = 95;
                final int colDescW = 70;
                final int colPreW  = 100;
                final int gap = 16;
                final int colArtW = w - (colSubW + colDescW + colPreW + (gap * 3));
                final int xArt = x;
                final int xPre = xArt + colArtW + gap;
                final int xDes = xPre + colPreW + gap;
                final int xSub = xDes + colDescW + gap;

                g2.drawLine(x, y, x + w, y); y += 15;
                g2.setFont(fText);
                g2.drawString("Artículo", xArt, y);
                g2.drawString("Precio",   xPre, y);
                g2.drawString("%Desc",    xDes, y);
                g2.drawString("Subtotal", xSub, y);
                y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                for (Modelo.NotaDetalle d : dets) {
                    String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                            ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                    String detalle = (d.getCodigoArticulo() > 0 ? d.getCodigoArticulo() + " · " : "")
                            + safe(artBase)
                            + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                            + " | " + labelIf("Color: ", safe(d.getColor()))
                            + " | " + labelIf("Talla: ", safe(d.getTalla()));

                    int yRowStart = y;
                    int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                    g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                    g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                    g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                    y = yAfter + 12;
                }
// === Observaciones (si existen) ===
if (observacionesTexto != null && !observacionesTexto.isBlank()) {
    y += 10;
    g2.setFont(fSection);
    g2.drawString("Observaciones", x, y);
    y += 12;
    g2.setFont(fText);
    y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
    y += 10;
}

                y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                g2.setFont(fH1);
                rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y);
                y += 22;

                g2.setFont(fText);
                java.math.BigDecimal bdTotal = java.math.BigDecimal
                        .valueOf(total)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                String pagadoLetra = numeroALetras(bdTotal.doubleValue());
                int anchoLetras = w - 230;
                int yInicioTotales = y - 24;
                drawWrapped(g2, "Cantidad pagada con letra: " + pagadoLetra, x, yInicioTotales, anchoLetras);

                g2.setFont(fSection);
                g2.drawString("Pagos", x, y);
                y += 12;
                g2.setFont(fText);

                final int gapCols2 = 24;
                final int leftW2   = (w - gapCols2) / 2;
                final int rightW2  = w - gapCols2 - leftW2;

                int yPLeft  = y;
                int yPRight = y;

                yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW2);
                yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW2);
                yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW2);

                yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW2 + gapCols2, yPRight, rightW2);
                yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW2 + gapCols2, yPRight + 2, rightW2);

                y = Math.max(yPLeft, yPRight) + 8;

                if (obsequiosPrint != null && !obsequiosPrint.isEmpty()) {
                    y += 10;
                    g2.drawLine(x, y, x + w, y); y += 16;

                    g2.setFont(fSection);
                    g2.drawString("Obsequios incluidos", x, y);
                    y += 12;

                    g2.setFont(fText);

                    final int gap2      = 16;
                    final int colCodW   = 70;
                    final int colTalW   = 70;
                    final int colMarW   = 70;
                    final int colModW   = 70;

                    final int xCod = x;
                    final int xNom = xCod + colCodW + gap2;
                    final int xMar = xNom + colCodW + gap2;
                    final int xMod = xMar + colMarW + gap2;
                    final int xTal = xMod + colModW + gap2;
                    final int xCol = xTal + colTalW + gap2;

                    g2.drawString("Código",   xCod, y);
                    g2.drawString("Obsequio", xNom, y);
                    g2.drawString("Marca",    xMar, y);
                    g2.drawString("Modelo",   xMod, y);
                    g2.drawString("Talla",    xTal, y);
                    g2.drawString("Color",    xCol, y);
                    y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                    // (En reimpresión lo dejamos vacío, misma estructura)
                }
                return PAGE_EXISTS;
            }

            // ===== helpers =====
            private String safe(String s){ return (s == null) ? "" : s.trim(); }
            private String fmt2(Double v){ if (v == null) v = 0d; return String.format("%.2f", v); }
            private String coalesce(String a, String b, String def){
                if (a != null && !a.isBlank()) return a;
                if (b != null && !b.isBlank()) return b;
                return (def == null) ? "" : def;
            }
            private String labelIf(String label, String val){
                if (val == null || val.isBlank()) return "";
                return label + val;
            }
            private String trimJoin(String sep, String... vals){
                StringBuilder sb = new StringBuilder();
                for (String v : vals){
                    v = safe(v);
                    if (!v.isBlank()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(v);
                    }
                }
                return sb.toString();
            }
            private String joinNonBlank(String sep, String... parts){
                StringBuilder sb = new StringBuilder();
                for (String s : parts){
                    if (s != null && !s.isBlank()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(s);
                    }
                }
                return sb.toString();
            }
            private void center(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int cx = x + (w - fm.stringWidth(s)) / 2;
                g2.drawString(s, cx, y);
            }
            private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int rx = x + w - fm.stringWidth(s);
                g2.drawString(s, rx, y);
            }
            private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                if (text == null) return y;
                text = text.trim();
                if (text.isEmpty()) return y;
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String[] words = text.split("\\s+");
                StringBuilder line = new StringBuilder();
                for (String w : words){
                    String tryLine = (line.length()==0 ? w : line + " " + w);
                    if (fm.stringWidth(tryLine) <= maxWidth){
                        line.setLength(0); line.append(tryLine);
                    } else {
                        g2.drawString(line.toString(), x, y);
                        y += 12;
                        line.setLength(0); line.append(w);
                    }
                }
                if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                return y + 12;
            }
            private BufferedImage loadIcon(String pathOrName) {
                try {
                    String name = pathOrName;
                    int slash = name.lastIndexOf('/');
                    if (slash >= 0) name = name.substring(slash + 1);

                    java.nio.file.Path override = java.nio.file.Paths.get(
                            System.getProperty("user.dir"), "icons", name);
                    if (java.nio.file.Files.exists(override)) {
                        javax.imageio.ImageIO.setUseCache(false);
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    }

                    String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                    try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                        if (in == null) return null;
                        javax.imageio.ImageIO.setUseCache(false);
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                } catch (Exception e) { return null; }
            }
            private static BufferedImage trimTransparent(BufferedImage src) {
                if (src == null) return null;
                int w = src.getWidth(), h = src.getHeight();
                int minX = w, minY = h, maxX = -1, maxY = -1;
                int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                for (int y = 0; y < h; y++) {
                    int off = y * w;
                    for (int x = 0; x < w; x++) {
                        if (((p[off + x] >>> 24) & 0xFF) != 0) {
                            if (x < minX) minX = x; if (x > maxX) maxX = x;
                            if (y < minY) minY = y; if (y > maxY) maxY = y;
                        }
                    }
                }
                if (maxX < minX || maxY < minY) return src;
                BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                        minX, minY, maxX + 1, maxY + 1, null);
                g.dispose();
                return out;
            }
            private String numeroALetras(double valor) {
                long pesos = Math.round(Math.floor(valor + 1e-6));
                int centavos = (int) Math.round((valor - pesos) * 100.0);
                if (centavos == 100) { pesos += 1; centavos = 0; }

                if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

                final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                        "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                        "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
                final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
                final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                        "seiscientos", "setecientos", "ochocientos", "novecientos"};

                java.util.function.Function<Integer,String> tres = n -> {
                    if (n == 0) return "";
                    if (n == 100) return "cien";
                    int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
                    String s = (c > 0 ? CE[c] : "");
                    if (r > 0) {
                        if (!s.isEmpty()) s += " ";
                        if (r < 30) s += UN[r];
                        else {
                            s += DE[d];
                            if (u > 0) s += " y " + UN[u];
                        }
                    }
                    return s.trim();
                };

                StringBuilder sb = new StringBuilder();
                int millones = (int)((pesos / 1_000_000) % 1000);
                int miles    = (int)((pesos / 1_000) % 1000);
                int cientos  = (int)(pesos % 1000);

                long milesDeMillones = pesos / 1_000_000_000L;
                if (milesDeMillones > 0) {
                    sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
                    if (millones>0 || miles>0 || cientos>0) sb.append(" ");
                }
                if (millones > 0) {
                    sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
                    if (miles>0 || cientos>0) sb.append(" ");
                }
                if (miles > 0) {
                    if (miles == 1) sb.append("mil");
                    else sb.append(tres.apply(miles)).append(" mil");
                    if (cientos>0) sb.append(" ");
                }
                if (cientos > 0) sb.append(tres.apply(cientos));

                String texto = sb.toString().trim();
                texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
                texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

                return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
            }
            private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                     int x, int y, int maxWidth, int iconSize, int gapPx) {
                if ((text == null || text.isBlank()) && icon == null) return y;
                int tx = x;
                int baseline = y;
                if (icon != null) {
                    int ascent = g2.getFontMetrics().getAscent();
                    int iconY  = baseline - Math.min(iconSize, ascent);
                    g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                    tx += iconSize + gapPx;
                }
                return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
            }
        };
    }

    // ===== CRÉDITO (ANTICIPO) ===== (formato tal cual)
    private Printable construirPrintableCreditoEmpresarial(
            EmpresaInfo emp, Modelo.Nota n, List<Modelo.NotaDetalle> dets, Modelo.PagoFormas p,
            java.time.LocalDate fechaEntregaMostrar, java.time.LocalDate fechaEventoMostrar,
            String asesorNombre, String folioTxt,
            String clienteNombreFallback, String tel2Fallback, String observacionesTexto) {

        final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

        final String tel1      = (n.getTelefono() == null) ? "" : n.getTelefono();
        final String fechaHoy  = java.time.LocalDate.now().format(MX);
        final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaEntregaMostrar.format(MX);
        final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaEventoMostrar.format(MX);
        final double total     = (n.getTotal() == null) ? 0d : n.getTotal();

        final double tc = (p.getTarjetaCredito()   == null) ? 0d : p.getTarjetaCredito();
        final double td = (p.getTarjetaDebito()    == null) ? 0d : p.getTarjetaDebito();
        final double am = (p.getAmericanExpress()  == null) ? 0d : p.getAmericanExpress();
        final double tr = (p.getTransferencia()    == null) ? 0d : p.getTransferencia();
        final double dp = (p.getDeposito()         == null) ? 0d : p.getDeposito();
        final double ef = (p.getEfectivo()         == null) ? 0d : p.getEfectivo();
        final double anticipo = tc + td + am + tr + dp + ef;
        final double saldo    = (n.getSaldo() != null) ? n.getSaldo() : Math.max(0d, total - anticipo);

        final String cliNombre;
        final String cliTel2;
        final String cliPrueba1;
        final String cliPrueba2;
        String tmpNombre  = (clienteNombreFallback == null) ? "" : clienteNombreFallback;
        String tmpTel2    = (tel2Fallback == null) ? "" : tel2Fallback;
        String tmpPrueba1 = "";
        String tmpPrueba2 = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
            if (cr != null) {
                if (cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank())
                    tmpNombre = cr.getNombreCompleto();
                if (cr.getTelefono2() != null) tmpTel2 = cr.getTelefono2();
                if (cr.getFechaPrueba1() != null) tmpPrueba1 = cr.getFechaPrueba1().format(MX);
                if (cr.getFechaPrueba2() != null) tmpPrueba2 = cr.getFechaPrueba2().format(MX);
            }
        } catch (Exception ignore) { }
        cliNombre = tmpNombre;
        cliTel2   = tmpTel2;
        cliPrueba1 = tmpPrueba1;
        cliPrueba2 = tmpPrueba2;

        String medBusto = "", medCintura = "", medCadera = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
            if (raw != null) {
                for (java.util.Map.Entry<String,String> e : raw.entrySet()) {
                    String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                    String v = e.getValue() == null ? "" : e.getValue();
                    if (k.equals("busto"))      medBusto   = v;
                    else if (k.equals("cintura")) medCintura = v;
                    else if (k.equals("cadera"))  medCadera  = v;
                }
            }
        } catch (Exception ignore) { }

        StringBuilder _med = new StringBuilder();
        if (!medBusto.isBlank())   { _med.append("Busto: ").append(medBusto); }
        if (!medCintura.isBlank()){ if (_med.length()>0) _med.append("   "); _med.append("Cintura: ").append(medCintura); }
        if (!medCadera.isBlank())  { if (_med.length()>0) _med.append("   "); _med.append("Cadera: ").append(medCadera); }
        final String medidasFmt = _med.toString();

        final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;

        final java.util.List<String> obsequiosPrint = java.util.Collections.emptyList();

        return new Printable() {
            @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                if (pageIndex > 0) return NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) g;

                final int MARGIN = 36;
                final int x0 = (int) Math.round(pf.getImageableX());
                final int y0 = (int) Math.round(pf.getImageableY());
                final int W  = (int) Math.round(pf.getImageableWidth());
                int x = x0 + MARGIN;
                int y = y0 + MARGIN;
                int w = W  - (MARGIN * 2);

                final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                final Font fText    = g2.getFont().deriveFont(10.2f);
                final Font fSmall   = g2.getFont().deriveFont(8.8f);

                int leftTextX = x;
                int headerH   = 0;
                if (emp.logo != null) {
                    int hLogo = 56;
                    int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                    g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                    leftTextX = x + wLogo + 16;
                    headerH   = Math.max(headerH, hLogo);
                }

                int infoRightWidth = w - (leftTextX - x);
                int infoTextWidth  = infoRightWidth - 160;

                g2.setFont(fH1);
                int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                g2.setFont(fSmall);
                yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);

                String dir = joinNonBlank(", ",
                        emp.calleNumero, emp.colonia,
                        (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                        emp.ciudad, emp.estado);
                yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);

                yy = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Tel: ", emp.telefono),
                        labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

                g2.setFont(fH1);
                rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                final int rightColW = 120;
                final int xRight    = x + w - rightColW;
                int yRightHdr       = y + 22;

                g2.setFont(fSmall);
                yRightHdr = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRightHdr, rightColW);
                yRightHdr = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRightHdr, rightColW);

                BufferedImage igIcon = loadIcon("instagram.png");
                BufferedImage fbIcon = loadIcon("facebook.png");
                BufferedImage ttIcon = loadIcon("tiktok.png");

                yRightHdr = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRightHdr, rightColW, 12, 6);
                yRightHdr = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRightHdr, rightColW, 12, 6);
                yRightHdr = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRightHdr, rightColW, 12, 6);

                int leftBlockH  = Math.max(yy - y, headerH);
                int rightBlockH = Math.max(0, yRightHdr - y);
                int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;
                int afterTail   = y + usedHeader;

                g2.setFont(fTitle);
                center(g2, "NOTA DE ANTICIPO", x, w, afterTail + 14);
                y = afterTail + 32;

                g2.setFont(fSection);
                g2.drawString("Datos del cliente", x, y);
                y += 13;

                final int gapCols = 24;
                final int leftW   = (w - gapCols) / 2;
                final int rightW  = w - gapCols - leftW;

                int yLeft  = y;
                int yRight2 = y;

                g2.setFont(fText);
                yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(cliNombre)), x, yLeft, leftW);
                yLeft  = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Teléfono: ", safe(tel1)),
                        labelIf("Teléfono 2: ", safe(cliTel2))), x, yLeft + 2, leftW);
                if (!medidasFmt.isBlank()) yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);

                yRight2 = drawWrapped(g2, labelIf("Fecha de venta: ", fechaHoy), x + leftW + gapCols, yRight2, rightW);
                if (!fEvento.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de evento: ", fEvento), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!cliPrueba1.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", cliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!cliPrueba2.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", cliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!fEntrega.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de entrega: ", fEntrega), x + leftW + gapCols, yRight2 + 2, rightW);
                if (asesorNombre != null && !asesorNombre.isBlank())
                    yRight2 = drawWrapped(g2, labelIf("Asesor/a: ", asesorNombre), x + leftW + gapCols, yRight2 + 2, rightW);

                y = Math.max(yLeft, yRight2) + 10;

                final int colSubW  = 95;
                final int colDescW = 70;
                final int colPreW  = 100;
                final int gap = 16;
                final int colArtW = w - (colSubW + colDescW + colPreW + (gap * 3));
                final int xArt = x;
                final int xPre = xArt + colArtW + gap;
                final int xDes = xPre + colPreW + gap;
                final int xSub = xDes + colDescW + gap;

                g2.drawLine(x, y, x + w, y); y += 15;
                g2.setFont(fText);
                g2.drawString("Artículo", xArt, y);
                g2.drawString("Precio",   xPre, y);
                g2.drawString("%Desc",    xDes, y);
                g2.drawString("Subtotal", xSub, y);
                y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                for (Modelo.NotaDetalle d : dets) {
                    String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                            ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                    String detalle = (d.getCodigoArticulo() > 0 ? d.getCodigoArticulo() + " · " : "")
                            + safe(artBase)
                            + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                            + " | " + labelIf("Color: ", safe(d.getColor()))
                            + " | " + labelIf("Talla: ", safe(d.getTalla()));

                    int yRowStart = y;
                    int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                    g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                    g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                    g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                    y = yAfter + 12;
                }
// === Observaciones (si existen) ===
if (observacionesTexto != null && !observacionesTexto.isBlank()) {
    y += 10;
    g2.setFont(fSection);
    g2.drawString("Observaciones", x, y);
    y += 12;
    g2.setFont(fText);
    y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
    y += 10;
}

                y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                g2.setFont(fH1);
                rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y); y += 22;

                g2.setFont(fText);
                rightAlign(g2, "Anticipo: $" + fmt2(anticipo), x, w, y); y += 14;

                g2.setFont(fH1);
                rightAlign(g2, "SALDO RESTANTE: $" + fmt2(saldo), x, w, y); y += 22;

                g2.setFont(fText);
                java.math.BigDecimal bdAnticipo = java.math.BigDecimal
                        .valueOf(anticipo)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                String pagadoLetra = numeroALetras(bdAnticipo.doubleValue());
                int anchoLetras = w - 230;
                int yInicioTotales = y - (24 + 14 + 22);
                drawWrapped(g2, "Cantidad pagada con letra: " + pagadoLetra, x, yInicioTotales + 2, anchoLetras);

                g2.setFont(fSection);
                g2.drawString("Pagos", x, y); y += 12;
                g2.setFont(fText);

                final int gapCols2 = 24;
                final int leftW2   = (w - gapCols2) / 2;
                final int rightW2  = w - gapCols2 - leftW2;

                int yPLeft  = y;
                int yPRight = y;

                yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW2);
                yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW2);
                yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW2);

                yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW2 + gapCols2, yPRight, rightW2);
                yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW2 + gapCols2, yPRight + 2, rightW2);

                y = Math.max(yPLeft, yPRight) + 8;

                if (obsequiosPrint != null && !obsequiosPrint.isEmpty()) {
                    y += 10;
                    g2.drawLine(x, y, x + w, y); y += 16;

                    g2.setFont(fSection);
                    g2.drawString("Obsequios incluidos", x, y);
                    y += 12;

                    g2.setFont(fText);
                    // (En reimpresión lo dejamos vacío)
                }
                return PAGE_EXISTS;
            }

            // helpers (idénticos a Contado)
            private String safe(String s){ return (s == null) ? "" : s.trim(); }
            private String fmt2(Double v){ if (v == null) v = 0d; return String.format("%.2f", v); }
            private String coalesce(String a, String b, String def){
                if (a != null && !a.isBlank()) return a;
                if (b != null && !b.isBlank()) return b;
                return (def == null) ? "" : def;
            }
            private String labelIf(String label, String val){
                if (val == null || val.isBlank()) return "";
                return label + val;
            }
            private String trimJoin(String sep, String... vals){
                StringBuilder sb = new StringBuilder();
                for (String v : vals){
                    v = (v==null? "": v.trim());
                    if (!v.isEmpty()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(v);
                    }
                }
                return sb.toString();
            }
            private String joinNonBlank(String sep, String... parts){
                StringBuilder sb = new StringBuilder();
                for (String s : parts){
                    if (s != null && !s.isBlank()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(s);
                    }
                }
                return sb.toString();
            }
            private void center(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int cx = x + (w - fm.stringWidth(s)) / 2;
                g2.drawString(s, cx, y);
            }
            private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int rx = x + w - fm.stringWidth(s);
                g2.drawString(s, rx, y);
            }
            private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                if (text == null) return y;
                text = text.trim();
                if (text.isEmpty()) return y;
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String[] words = text.split("\\s+");
                StringBuilder line = new StringBuilder();
                for (String w : words){
                    String tryLine = (line.length()==0 ? w : line + " " + w);
                    if (fm.stringWidth(tryLine) <= maxWidth){
                        line.setLength(0); line.append(tryLine);
                    } else {
                        g2.drawString(line.toString(), x, y);
                        y += 12;
                        line.setLength(0); line.append(w);
                    }
                }
                if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                return y + 12;
            }
            private BufferedImage loadIcon(String pathOrName) {
                try {
                    String name = pathOrName;
                    int slash = name.lastIndexOf('/');
                    if (slash >= 0) name = name.substring(slash + 1);

                    java.nio.file.Path override = java.nio.file.Paths.get(System.getProperty("user.dir"), "icons", name);
                    if (java.nio.file.Files.exists(override)) {
                        javax.imageio.ImageIO.setUseCache(false);
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    }

                    String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                    try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                        if (in == null) return null;
                        javax.imageio.ImageIO.setUseCache(false);
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                } catch (Exception e) { return null; }
            }
            private BufferedImage trimTransparent(BufferedImage src) {
                if (src == null) return null;
                int w = src.getWidth(), h = src.getHeight();
                int minX = w, minY = h, maxX = -1, maxY = -1;
                int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                for (int y = 0; y < h; y++) {
                    int off = y * w;
                    for (int x = 0; x < w; x++) {
                        if (((p[off + x] >>> 24) & 0xFF) != 0) {
                            if (x < minX) minX = x; if (x > maxX) maxX = x;
                            if (y < minY) minY = y; if (y > maxY) maxY = y;
                        }
                    }
                }
                if (maxX < minX || maxY < minY) return src;
                BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                        minX, minY, maxX + 1, maxY + 1, null);
                g.dispose();
                return out;
            }
            private String numeroALetras(double valor) {
                long pesos = Math.round(Math.floor(valor + 1e-6));
                int centavos = (int) Math.round((valor - pesos) * 100.0);
                if (centavos == 100) { pesos += 1; centavos = 0; }

                if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

                final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                        "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                        "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
                final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
                final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                        "seiscientos", "setecientos", "ochocientos", "novecientos"};

                java.util.function.Function<Integer,String> tres = n -> {
                    if (n == 0) return "";
                    if (n == 100) return "cien";
                    int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
                    String s = (c > 0 ? CE[c] : "");
                    if (r > 0) {
                        if (!s.isEmpty()) s += " ";
                        if (r < 30) s += UN[r];
                        else {
                            s += DE[d];
                            if (u > 0) s += " y " + UN[u];
                        }
                    }
                    return s.trim();
                };

                StringBuilder sb = new StringBuilder();
                int millones = (int)((pesos / 1_000_000) % 1000);
                int miles    = (int)((pesos / 1_000) % 1000);
                int cientos  = (int)(pesos % 1000);

                long milesDeMillones = pesos / 1_000_000_000L;
                if (milesDeMillones > 0) {
                    sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
                    if (millones>0 || miles>0 || cientos>0) sb.append(" ");
                }
                if (millones > 0) {
                    sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
                    if (miles>0 || cientos>0) sb.append(" ");
                }
                if (miles > 0) {
                    if (miles == 1) sb.append("mil");
                    else sb.append(tres.apply(miles)).append(" mil");
                    if (cientos>0) sb.append(" ");
                }
                if (cientos > 0) sb.append(tres.apply(cientos));

                String texto = sb.toString().trim();
                texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
                texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

                return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
            }
            private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                     int x, int y, int maxWidth, int iconSize, int gapPx) {
                if ((text == null || text.isBlank()) && icon == null) return y;
                int tx = x;
                int baseline = y;
                if (icon != null) {
                    int ascent = g2.getFontMetrics().getAscent();
                    int iconY  = baseline - Math.min(iconSize, ascent);
                    g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                    tx += iconSize + gapPx;
                }
                return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
            }
        };
    }

    /* ==========================================================
       ========================= ABONO ==========================
       ========================================================== */

    private Printable construirPrintableAbono(
            EmpresaInfo emp,
            Modelo.Nota notaBase,                  // contiene total/saldo/telefono/numero
            List<Modelo.NotaDetalle> dets,         // detalle de artículos vendidos
            Modelo.PagoFormas pagos,               // lo que se abonó por forma
            double abonoRealizado,                 // suma de pagos capturados
            double saldoRestante,                  // nuevo saldo después del abono
            String folioTxt, String observacionesTexto) {

        final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
        final String tel1     = (notaBase.getTelefono() == null) ? "" : notaBase.getTelefono();
        final String fechaHoy = java.time.LocalDate.now().format(MX);
        final double total    = (notaBase.getTotal() == null) ? 0d : notaBase.getTotal();

        final double tc = pagos.getTarjetaCredito()   == null ? 0d : pagos.getTarjetaCredito();
        final double td = pagos.getTarjetaDebito()    == null ? 0d : pagos.getTarjetaDebito();
        final double am = pagos.getAmericanExpress()  == null ? 0d : pagos.getAmericanExpress();
        final double tr = pagos.getTransferencia()    == null ? 0d : pagos.getTransferencia();
        final double dp = pagos.getDeposito()         == null ? 0d : pagos.getDeposito();
        final double ef = pagos.getEfectivo()         == null ? 0d : pagos.getEfectivo();

        String cliNombre  = "";
        String cliTel2    = "";
        String cliPrueba1 = "", cliPrueba2 = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
            if (cr != null) {
                cliNombre  = cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto();
                cliTel2    = cr.getTelefono2() == null ? "" : cr.getTelefono2();
                if (cr.getFechaPrueba1()!=null) cliPrueba1 = cr.getFechaPrueba1().format(MX);
                if (cr.getFechaPrueba2()!=null) cliPrueba2 = cr.getFechaPrueba2().format(MX);
            }
        } catch (Exception ignore) { }

        String medBusto = "", medCintura = "", medCadera = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
            if (raw != null) {
                for (java.util.Map.Entry<String,String> e : raw.entrySet()) {
                    String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                    String v = e.getValue() == null ? "" : e.getValue();
                    if (k.equals("busto"))      medBusto   = v;
                    else if (k.equals("cintura")) medCintura = v;
                    else if (k.equals("cadera"))  medCadera  = v;
                }
            }
        } catch (Exception ignore) { }

        StringBuilder _med = new StringBuilder();
        if (!medBusto.isBlank())   { _med.append("Busto: ").append(medBusto); }
        if (!medCintura.isBlank()){ if (_med.length()>0) _med.append("   "); _med.append("Cintura: ").append(medCintura); }
        if (!medCadera.isBlank())  { if (_med.length()>0) _med.append("   "); _med.append("Cadera: ").append(medCadera); }
        final String medidasFmt = _med.toString();

        final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;
        final String fCliNombre = cliNombre, fCliTel2 = cliTel2, fCliPrueba1 = cliPrueba1, fCliPrueba2 = cliPrueba2;

        return new Printable() {
            @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                if (pageIndex > 0) return NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) g;

                final int MARGIN = 36;
                final int x0 = (int) Math.round(pf.getImageableX());
                final int y0 = (int) Math.round(pf.getImageableY());
                final int W  = (int) Math.round(pf.getImageableWidth());
                int x = x0 + MARGIN;
                int y = y0 + MARGIN;
                int w = W  - (MARGIN * 2);

                final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                final Font fText    = g2.getFont().deriveFont(10.2f);
                final Font fSmall   = g2.getFont().deriveFont(8.8f);

                int leftTextX = x;
                int headerH   = 0;
                if (emp.logo != null) {
                    int hLogo = 56;
                    int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                    g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                    leftTextX = x + wLogo + 16;
                    headerH   = Math.max(headerH, hLogo);
                }
                int infoRightWidth = w - (leftTextX - x);
                int infoTextWidth  = infoRightWidth - 160;

                g2.setFont(fH1);
                int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                g2.setFont(fSmall);
                yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);

                String dir = joinNonBlank(", ",
                        emp.calleNumero, emp.colonia,
                        (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                        emp.ciudad, emp.estado);
                yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);
                yy = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Tel: ", emp.telefono),
                        labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

                g2.setFont(fH1);
                rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                final int rightColW = 120;
                final int xRight    = x + w - rightColW;
                int yRight          = y + 22;

                g2.setFont(fSmall);
                yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
                yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

                BufferedImage igIcon = loadIcon("instagram.png");
                BufferedImage fbIcon = loadIcon("facebook.png");
                BufferedImage ttIcon = loadIcon("tiktok.png");

                yRight = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRight, rightColW, 12, 6);
                yRight = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRight, rightColW, 12, 6);
                yRight = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRight, rightColW, 12, 6);

                int leftBlockH  = Math.max(yy - y, headerH);
                int rightBlockH = Math.max(0, yRight - y);
                int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;

                int afterTail = y + usedHeader;

                g2.setFont(fTitle);
                center(g2, "RECIBO DE ABONO", x, w, afterTail + 14);
                y = afterTail + 32;

                g2.setFont(fSection);
                g2.drawString("Datos del cliente", x, y);
                y += 13;

                final int gapCols = 24;
                final int leftW   = (w - gapCols) / 2;
                final int rightW  = w - gapCols - leftW;

                int yLeft  = y;
                int yRight2 = y;

                g2.setFont(fText);
                yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(fCliNombre)), x, yLeft, leftW);
                yLeft  = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Teléfono: ", safe(tel1)),
                        labelIf("Teléfono 2: ", safe(fCliTel2))), x, yLeft + 2, leftW);
                if (!medidasFmt.isBlank()) {
                    yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);
                }

                yRight2 = drawWrapped(g2, labelIf("Fecha de abono: ", fechaHoy), x + leftW + gapCols, yRight2, rightW);
                if (!fCliPrueba1.isBlank())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", fCliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!fCliPrueba2.isBlank())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", fCliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);

                y = Math.max(yLeft, yRight2) + 10;

                final int colSubW  = 95, colDescW = 70, colPreW = 100;
                final int gap = 16;
                final int colArtW = w - (colSubW + colDescW + colPreW + (gap * 3));
                final int xArt = x, xPre = xArt + colArtW + gap, xDes = xPre + colPreW + gap, xSub = xDes + colDescW + gap;

                g2.drawLine(x, y, x + w, y); y += 15;
                g2.setFont(fText);
                g2.drawString("Artículo", xArt, y);
                g2.drawString("Precio",   xPre, y);
                g2.drawString("%Desc",    xDes, y);
                g2.drawString("Subtotal", xSub, y);
                y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                if (dets != null && !dets.isEmpty()) {
                    for (Modelo.NotaDetalle d : dets) {
                        String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                                ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                        String detalle = (d.getCodigoArticulo() > 0 ? d.getCodigoArticulo() + " · " : "")
                                + safe(artBase)
                                + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                                + " | " + labelIf("Color: ", safe(d.getColor()))
                                + " | " + labelIf("Talla: ", safe(d.getTalla()));

                        int yRowStart = y;
                        int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                        g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                        g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                        g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                        y = yAfter + 12;
                    }
                } else {
                    y = drawWrapped(g2, "(Detalle no disponible)", xArt, y, colArtW);
                }

// === Observaciones (de la venta original) ===
if (observacionesTexto != null && !observacionesTexto.isBlank()) {
    y += 10;
    g2.setFont(fSection);
    g2.drawString("Observaciones", x, y);
    y += 12;
    g2.setFont(fText);
    y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
    y += 10;
}

                y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                g2.setFont(fH1);
                rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y);
                y += 22;

                g2.setFont(fText);
                String abonoLetra = numeroALetras(abonoRealizado);  // Convierte el abono a letras
                int anchoLetras = w - 230;
                
                int yInicioTotales = y - 24; // solo hubo una línea (TOTAL)
                drawWrapped(g2, "Abono en letra: " + abonoLetra, x, yInicioTotales, anchoLetras);  // Imprime el abono en letras
                y += 22;

                g2.setFont(fText);
                rightAlign(g2, "Abono: $" + fmt2(abonoRealizado), x, w, y);
                y += 14;

                g2.setFont(fH1);
                rightAlign(g2, "SALDO RESTANTE: $" + fmt2(saldoRestante), x, w, y);
                y += 22;

                if (Math.abs(saldoRestante) < 0.005) {
                    g2.setFont(fH1);
                    center(g2, "Venta liquidada", x, w, y);
                    y += 18;
                }

                g2.setFont(fSection);
                g2.drawString("Pagos", x, y); y += 12; g2.setFont(fText);

                final int gapCols2 = 24;
                final int leftW2   = (w - gapCols2) / 2;
                final int rightW2  = w - gapCols2 - leftW2;

                int yPLeft = y, yPRight = y;
                yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW2);
                yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW2);
                yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW2);

                yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW2 + gapCols2, yPRight, rightW2);
                yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW2 + gapCols2, yPRight + 2, rightW2);

                return PAGE_EXISTS;
            }

            private String safe(String s){ return (s == null) ? "" : s.trim(); }
            private String fmt2(Double v){ if (v == null) v = 0d; return String.format("%.2f", v); }
            private String coalesce(String a, String b, String def){
                if (a != null && !a.isBlank()) return a;
                if (b != null && !b.isBlank()) return b;
                return (def == null) ? "" : def;
            }
            private String labelIf(String label, String val){
                if (val == null || val.isBlank()) return "";
                return label + val;
            }
            private String trimJoin(String sep, String... vals){
                StringBuilder sb = new StringBuilder();
                for (String v : vals){
                    v = safe(v);
                    if (!v.isBlank()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(v);
                    }
                }
                return sb.toString();
            }
            private String joinNonBlank(String sep, String... parts){
                StringBuilder sb = new StringBuilder();
                for (String s : parts){
                    if (s != null && !s.isBlank()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(s);
                    }
                }
                return sb.toString();
            }
            private void center(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int cx = x + (w - fm.stringWidth(s)) / 2;
                g2.drawString(s, cx, y);
            }
            private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int rx = x + w - fm.stringWidth(s);
                g2.drawString(s, rx, y);
            }
            private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                if (text == null) return y;
                text = text.trim();
                if (text.isEmpty()) return y;
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String[] words = text.split("\\s+");
                StringBuilder line = new StringBuilder();
                for (String w : words){
                    String tryLine = (line.length()==0 ? w : line + " " + w);
                    if (fm.stringWidth(tryLine) <= maxWidth){
                        line.setLength(0); line.append(tryLine);
                    } else {
                        g2.drawString(line.toString(), x, y);
                        y += 12;
                        line.setLength(0); line.append(w);
                    }
                }
                if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                return y + 12;
            }

            private BufferedImage loadIcon(String pathOrName) {
                try {
                    String name = pathOrName;
                    int slash = name.lastIndexOf('/');
                    if (slash >= 0) name = name.substring(slash + 1);

                    java.nio.file.Path override = java.nio.file.Paths.get(
                            System.getProperty("user.dir"), "icons", name);
                    if (java.nio.file.Files.exists(override)) {
                        javax.imageio.ImageIO.setUseCache(false);
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    }

                    String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                    try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                        if (in == null) return null;
                        javax.imageio.ImageIO.setUseCache(false);
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                } catch (Exception e) { return null; }
            }

            private static BufferedImage trimTransparent(BufferedImage src) {
                if (src == null) return null;
                int w = src.getWidth(), h = src.getHeight();
                int minX = w, minY = h, maxX = -1, maxY = -1;
                int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                for (int y = 0; y < h; y++) {
                    int off = y * w;
                    for (int x = 0; x < w; x++) {
                        if (((p[off + x] >>> 24) & 0xFF) != 0) {
                            if (x < minX) minX = x; if (x > maxX) maxX = x;
                            if (y < minY) minY = y; if (y > maxY) maxY = y;
                        }
                    }
                }
                
                if (maxX < minX || maxY < minY) return src;
                BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                        minX, minY, maxX + 1, maxY + 1, null);
                g.dispose();
                return out;

                
            }

            private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                     int x, int y, int maxWidth, int iconSize, int gapPx) {
                if ((text == null || text.isBlank()) && icon == null) return y;
                int tx = x;
                int baseline = y;
                if (icon != null) {
                    int ascent = g2.getFontMetrics().getAscent();
                    int iconY  = baseline - Math.min(iconSize, ascent);
                    g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                    tx += iconSize + gapPx;
                }
                return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
            }

        };
    }
    // ====== NOTA DE DEVOLUCIÓN (FIX: vars final + sin drawIconLine) ======
private Printable construirPrintableDevolucion(
        EmpresaInfo emp,
        Modelo.Nota n,                       // usa: telefono, total
        List<Modelo.NotaDetalle> dets,       // artículos devueltos (si los tienes)
        String folioTxt) {

    final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

    final String tel1     = (n.getTelefono() == null) ? "" : n.getTelefono();
    final String fechaHoy = java.time.LocalDate.now().format(MX);
    final double total    = (n.getTotal() == null) ? 0d : n.getTotal();
    final String folio    = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;

    // Cliente (nombre/tel2 y pruebas si existen)
    String _cliNombre = "", _cliTel2 = "", _cliPrueba1 = "", _cliPrueba2 = "";
    try {
        Controlador.clienteDAO cdao = new Controlador.clienteDAO();
        Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
        if (cr != null) {
            _cliNombre  = cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto();
            _cliTel2    = cr.getTelefono2() == null ? "" : cr.getTelefono2();
            if (cr.getFechaPrueba1()!=null) _cliPrueba1 = cr.getFechaPrueba1().format(MX);
            if (cr.getFechaPrueba2()!=null) _cliPrueba2 = cr.getFechaPrueba2().format(MX);
        }
    } catch (Exception ignore) { }
    // → finales para la clase interna
    final String cliNombre  = _cliNombre;
    final String cliTel2    = _cliTel2;
    final String cliPrueba1 = _cliPrueba1;
    final String cliPrueba2 = _cliPrueba2;

    // Medidas (opcional)
    String medBusto = "", medCintura = "", medCadera = "";
    try {
        Controlador.clienteDAO cdao = new Controlador.clienteDAO();
        java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
        if (raw != null) {
            for (var e : raw.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                String v = e.getValue() == null ? "" : e.getValue();
                if (k.equals("busto"))      medBusto   = v;
                else if (k.equals("cintura")) medCintura = v;
                else if (k.equals("cadera"))  medCadera  = v;
            }
        }
    } catch (Exception ignore) { }
    final String medidasFmt = (medBusto.isBlank() && medCintura.isBlank() && medCadera.isBlank())
            ? ""
            : ( (medBusto.isBlank()? "" : "Busto: " + medBusto)
              + (medCintura.isBlank()? "" : ( (medBusto.isBlank()? "" : "   ") + "Cintura: " + medCintura))
              + (medCadera.isBlank()? "" : ( (!medBusto.isBlank()||!medCintura.isBlank()? "   " : "") + "Cadera: " + medCadera)) );

    return new Printable() {
        @Override public int print(Graphics g, PageFormat pf, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;

            // Márgenes y tipografías (idénticos a otros formatos)
            final int MARGIN = 36;
            final int x0 = (int) Math.round(pf.getImageableX());
            final int y0 = (int) Math.round(pf.getImageableY());
            final int W  = (int) Math.round(pf.getImageableWidth());
            int x = x0 + MARGIN, y = y0 + MARGIN, w = W - (MARGIN * 2);

            final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
            final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
            final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
            final Font fText    = g2.getFont().deriveFont(10.2f);
            final Font fSmall   = g2.getFont().deriveFont(8.8f);

            // Encabezado empresa
            int leftTextX = x, headerH = 0;
            if (emp.logo != null) {
                int hLogo = 56;
                int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                leftTextX = x + wLogo + 16;
                headerH   = Math.max(headerH, hLogo);
            }
            int infoRightWidth = w - (leftTextX - x);
            int infoTextWidth  = infoRightWidth - 160;

            g2.setFont(fH1);
            int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
            g2.setFont(fSmall);
            yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
            yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);
            String dir = joinNonBlank(", ",
                    emp.calleNumero, emp.colonia,
                    (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                    emp.ciudad, emp.estado);
            yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);
            yy = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Tel: ", emp.telefono),
                    labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

            g2.setFont(fH1);
            rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

            // Columna derecha (correo, web, redes) — sin helper drawIconLine
            final int rightColW = 120;
            final int xRight    = x + w - rightColW;
            int yRight          = y + 22;

            g2.setFont(fSmall);
            yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
            yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

            BufferedImage igIcon = loadIcon("instagram.png");
            BufferedImage fbIcon = loadIcon("facebook.png");
            BufferedImage ttIcon = loadIcon("tiktok.png");

            // Instagram
            {
                int tx = xRight;
                if (igIcon != null) {
                    int iconSize = 12;
                    int ascent = g2.getFontMetrics().getAscent();
                    int iconY = yRight - Math.min(iconSize, ascent);
                    g2.drawImage(igIcon, xRight, iconY, iconSize, iconSize, null);
                    tx += iconSize + 6;
                }
                yRight = drawWrapped(g2, safe(emp.instagram), tx, yRight, rightColW - (tx - xRight));
            }
            // Facebook
            {
                int tx = xRight;
                if (fbIcon != null) {
                    int iconSize = 12;
                    int ascent = g2.getFontMetrics().getAscent();
                    int iconY = yRight - Math.min(iconSize, ascent);
                    g2.drawImage(fbIcon, xRight, iconY, iconSize, iconSize, null);
                    tx += iconSize + 6;
                }
                yRight = drawWrapped(g2, safe(emp.facebook), tx, yRight, rightColW - (tx - xRight));
            }
            // TikTok
            {
                int tx = xRight;
                if (ttIcon != null) {
                    int iconSize = 12;
                    int ascent = g2.getFontMetrics().getAscent();
                    int iconY = yRight - Math.min(iconSize, ascent);
                    g2.drawImage(ttIcon, xRight, iconY, iconSize, iconSize, null);
                    tx += iconSize + 6;
                }
                yRight = drawWrapped(g2, safe(emp.tiktok), tx, yRight, rightColW - (tx - xRight));
            }

            int leftBlockH  = Math.max(yy - y, headerH);
            int rightBlockH = Math.max(0, yRight - y);
            int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;
            int afterTail   = y + usedHeader;

            // Título
            g2.setFont(fTitle);
            center(g2, "NOTA DE DEVOLUCIÓN", x, w, afterTail + 14);
            y = afterTail + 32;

            // Datos del cliente
            g2.setFont(fSection);
            g2.drawString("Datos del cliente", x, y);
            y += 13;

            final int gapCols = 24;
            final int leftW   = (w - gapCols) / 2;
            final int rightW  = w - gapCols - leftW;

            int yLeft = y, yRight2 = y;
            g2.setFont(fText);
            yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(cliNombre)), x, yLeft, leftW);
            yLeft  = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Teléfono: ", safe(tel1)),
                    labelIf("Teléfono 2: ", safe(cliTel2))), x, yLeft + 2, leftW);
            if (!medidasFmt.isBlank()) yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);

            yRight2 = drawWrapped(g2, labelIf("Fecha de emisión: ", fechaHoy), x + leftW + gapCols, yRight2, rightW);
            if (!cliPrueba1.isEmpty())
                yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", cliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
            if (!cliPrueba2.isEmpty())
                yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", cliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);

            y = Math.max(yLeft, yRight2) + 10;

            // Tabla de artículos devueltos (igual que venta)
            final int colSubW  = 95, colDescW = 70, colPreW = 100, gap = 16;
            final int colArtW  = w - (colSubW + colDescW + colPreW + (gap * 3));
            final int xArt = x, xPre = xArt + colArtW + gap, xDes = xPre + colPreW + gap, xSub = xDes + colDescW + gap;

            g2.drawLine(x, y, x + w, y); y += 15;
            g2.setFont(fText);
            g2.drawString("Artículo", xArt, y);
            g2.drawString("Precio",   xPre, y);
            g2.drawString("%Desc",    xDes, y);
            g2.drawString("Subtotal", xSub, y);
            y += 10; g2.drawLine(x, y, x + w, y); y += 14;

            if (dets != null && !dets.isEmpty()) {
                for (Modelo.NotaDetalle d : dets) {
                    String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                            ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                    String detalle = (d.getCodigoArticulo() > 0 ? d.getCodigoArticulo() + " · " : "")
                            + safe(artBase)
                            + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                            + " | " + labelIf("Color: ", safe(d.getColor()))
                            + " | " + labelIf("Talla: ", safe(d.getTalla()));
                    int yRowStart = y;
                    int yAfter = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);
                    g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                    g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                    g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);
                    y = yAfter + 12;
                }
            } else {
                y = drawWrapped(g2, "(Detalle no disponible)", xArt, y, colArtW);
            }

            y += 6; g2.drawLine(x, y, x + w, y); y += 16;

            // Saldo a favor
            g2.setFont(fH1);
            rightAlign(g2, "SALDO A FAVOR: $" + fmt2(total), x, w, y);
            y += 22;

            // Leyenda
            g2.setFont(fSmall);
            y = drawWrapped(g2,
                    "Esta nota de devolución puede utilizarse como método de pago en compras futuras. "
                  + "No es canjeable por efectivo.", x, y, w);

            return PAGE_EXISTS;
        }

        // ===== Helpers locales =====
        private String safe(String s){ return (s == null) ? "" : s.trim(); }
        private String fmt2(Double v){ if (v == null) v = 0d; return String.format("%.2f", v); }
        private String coalesce(String a, String b, String def){
            if (a != null && !a.isBlank()) return a;
            if (b != null && !b.isBlank()) return b;
            return (def == null) ? "" : def;
        }
        private String labelIf(String label, String val){
            if (val == null || val.isBlank()) return "";
            return label + val;
        }
        private String trimJoin(String sep, String... vals){
            StringBuilder sb = new StringBuilder();
            for (String v : vals){
                v = (v==null? "": v.trim());
                if (!v.isEmpty()){
                    if (sb.length() > 0) sb.append(sep);
                    sb.append(v);
                }
            }
            return sb.toString();
        }
        private String joinNonBlank(String sep, String... parts){
            StringBuilder sb = new StringBuilder();
            for (String s : parts){
                if (s != null && !s.isBlank()){
                    if (sb.length() > 0) sb.append(sep);
                    sb.append(s);
                }
            }
            return sb.toString();
        }
        private void center(Graphics2D g2, String s, int x, int w, int y){
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int cx = x + (w - fm.stringWidth(s)) / 2;
            g2.drawString(s, cx, y);
        }
        private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int rx = x + w - fm.stringWidth(s);
            g2.drawString(s, rx, y);
        }
        private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
            if (text == null) return y;
            text = text.trim();
            if (text.isEmpty()) return y;
            java.awt.FontMetrics fm = g2.getFontMetrics();
            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String w : words){
                String tryLine = (line.length()==0 ? w : line + " " + w);
                if (fm.stringWidth(tryLine) <= maxWidth){
                    line.setLength(0); line.append(tryLine);
                } else {
                    g2.drawString(line.toString(), x, y);
                    y += 12;
                    line.setLength(0); line.append(w);
                }
            }
            if (line.length()>0){ g2.drawString(line.toString(), x, y); }
            return y + 12;
        }
        private BufferedImage loadIcon(String name) {
            try {
                int slash = name.lastIndexOf('/'); if (slash>=0) name = name.substring(slash+1);
                java.nio.file.Path override = java.nio.file.Paths.get(System.getProperty("user.dir"), "icons", name);
                if (java.nio.file.Files.exists(override)) {
                    javax.imageio.ImageIO.setUseCache(false);
                    try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                }
                String cp = "/icons/" + name;
                try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cp)) {
                    if (in == null) return null;
                    javax.imageio.ImageIO.setUseCache(false);
                    return trimTransparent(javax.imageio.ImageIO.read(in));
                }
            } catch(Exception e){ return null; }
        }
        private static BufferedImage trimTransparent(BufferedImage src) {
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            int minX = w, minY = h, maxX = -1, maxY = -1;
            int[] p = src.getRGB(0,0,w,h,null,0,w);
            for (int y = 0; y < h; y++) {
                int off = y * w;
                for (int x = 0; x < w; x++) {
                    if (((p[off + x] >>> 24) & 0xFF) != 0) {
                        if (x < minX) minX = x; if (x > maxX) maxX = x;
                        if (y < minY) minY = y; if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < minX || maxY < minY) return src;
            BufferedImage out = new BufferedImage(maxX-minX+1, maxY-minY+1, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D gg = out.createGraphics();
            gg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gg.drawImage(src, 0,0,out.getWidth(),out.getHeight(), minX,minY,maxX+1,maxY+1, null);
            gg.dispose();
            return out;
        }
    };
}



    /* Helper mínimo que usan cargarEmpresaInfo y otros */
    private String n(String s){ return s==null? "": s; }
}
