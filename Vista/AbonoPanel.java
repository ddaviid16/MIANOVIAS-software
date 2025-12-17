package Vista;

import java.awt.FlowLayout;
import java.awt.Window;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
// ===== IMPRESIÓN =====
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;

import Controlador.AbonoService;
import Controlador.EmpresaDAO;
import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.Empresa;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Modelo.PagoFormas;
import Utilidades.TelefonosUI;


public class AbonoPanel extends JPanel {

    private JTextField txtTelefono, txtNombre;
    private JComboBox<Nota> cbNotas;
    private JLabel lblSaldo;

    // formas de pago
    private JTextField txtTC, txtTD, txtAMX, txtTRF, txtDEP, txtEFE;
    // --- Pago con folio de devolución ---
    private JComboBox<Controlador.NotasDVDAO.DVDisponible> cbDV;
    private JTextField txtMontoDV;


    private String lastTel = null;
    private Integer cajeraCodigo;
    private String  cajeraNombre;

    public void setCajeraActual(int codigoEmpleado, String nombreCompleto) {
        this.cajeraCodigo = codigoEmpleado;
        this.cajeraNombre = nombreCompleto;
    }

    public AbonoPanel() {
    setLayout(new BorderLayout());

    JPanel top = new JPanel(new GridBagLayout());
    top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(6,6,6,6);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1;

    int y=0;

    // Teléfono + Nombre
    txtTelefono = new JTextField();
    // <<< AQUÍ SE INSTALA EL FORMATEADOR DE TELÉFONO >>>
    TelefonosUI.instalar(txtTelefono, 10);

    txtNombre   = readOnly();
        addCell(top,c,0,y,new JLabel("Teléfono de cliente:"),1,false);
        addCell(top,c,1,y,txtTelefono,1,true);
        addCell(top,c,2,y,new JLabel("Nombre:"),1,false);
        addCell(top,c,3,y,txtNombre,1,false); 
        y++;
        
        // Botón para abrir el diálogo de búsqueda de clientes
        JButton btBuscarCliente = new JButton("Buscar por nombre o apellido");
        btBuscarCliente.addActionListener(_e -> seleccionarClientePorApellido());
        addCell(top, c, 2, y, btBuscarCliente, 1, false);
        y++;

        // recarga automática al escribir o perder el foco
        ((AbstractDocument) txtTelefono.getDocument())
                .addDocumentListener((SimpleDocListener) this::cargarClienteYNotas);
        txtTelefono.addActionListener(_e -> cargarClienteYNotas());
        txtTelefono.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { cargarClienteYNotas(); }
        });

        // Notas de crédito con saldo
        cbNotas = new JComboBox<>();
        cbNotas.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Nota) {
                    Nota n = (Nota) value;
                    String folio = (n.getFolio() != null && !n.getFolio().isBlank()) ? n.getFolio() : "(sin folio)";
                    setText("Folio " + folio +
                            "  |  Total: $" + String.format("%.2f", n.getTotal()) +
                            "  |  Saldo: $" + String.format("%.2f", n.getSaldo()));
                } else {
                    setText("— Selecciona folio de crédito —");
                }
                return this;
            }
        });
        cbNotas.addActionListener(_e -> actualizarSaldo());
        addCell(top,c,0,y,new JLabel("Folios con saldo:"),1,false);
        addCell(top,c,1,y,cbNotas,3,true); 
        y++;

        lblSaldo = new JLabel("Saldo: $0.00");
        lblSaldo.setHorizontalAlignment(SwingConstants.RIGHT);
        addCell(top,c,2,y,new JLabel(),1,false);
        addCell(top,c,3,y,lblSaldo,1,true); 
        y++;

        add(top, BorderLayout.NORTH);

        // ====== Panel pagos
        JPanel pay = new JPanel(new GridBagLayout());
        pay.setBorder(BorderFactory.createTitledBorder("Abono (formas de pago)"));
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(6,6,6,6);
        d.fill   = GridBagConstraints.HORIZONTAL;
        d.weightx = 1;
        int r=0;

        txtTC = moneyField(); txtTD = moneyField(); txtAMX = moneyField();
        txtTRF= moneyField(); txtDEP = moneyField(); txtEFE = moneyField();

        addCell(pay,d,0,r,new JLabel("Tarjeta crédito:"),1,false);
        addCell(pay,d,1,r,txtTC,1,true);
        addCell(pay,d,2,r,new JLabel("Tarjeta débito:"),1,false);
        addCell(pay,d,3,r,txtTD,1,true); 
        r++;

        addCell(pay,d,0,r,new JLabel("American Express:"),1,false);
        addCell(pay,d,1,r,txtAMX,1,true);
        addCell(pay,d,2,r,new JLabel("Transferencia:"),1,false);
        addCell(pay,d,3,r,txtTRF,1,true); 
        r++;

        addCell(pay,d,0,r,new JLabel("Depósito:"),1,false);
        addCell(pay,d,1,r,txtDEP,1,true);
        addCell(pay,d,2,r,new JLabel("Efectivo:"),1,false);
        addCell(pay,d,3,r,txtEFE,1,true); 
        r++;
        // ====== PAGO CON FOLIO DE DEVOLUCIÓN ======
        cbDV = new JComboBox<>();
        cbDV.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Controlador.NotasDVDAO.DVDisponible dv) {
                    setText("Folio " + dv.folio + " | Disponible: $" + String.format("%.2f", dv.disponible));
                } else {
                    setText("--- Pagar con folio de devolución ---");
                }
                return this;
            }
        });
        cbDV.addActionListener(_e -> {
        Controlador.NotasDVDAO.DVDisponible sel =
                (Controlador.NotasDVDAO.DVDisponible) cbDV.getSelectedItem();
        if (sel != null) {
            txtMontoDV.setText(""); // ya no se llena automáticamente
            txtMontoDV.setEnabled(true);
        } else {
            txtMontoDV.setText("");
            txtMontoDV.setEnabled(false);
        }
    });

        txtMontoDV = moneyField();

        addCell(pay, d, 0, r, new JLabel("Folio devolución:"), 1, false);
        addCell(pay, d, 1, r, cbDV, 1, true);
        addCell(pay, d, 2, r, new JLabel("Monto devolución:"), 1, false);
        addCell(pay, d, 3, r, txtMontoDV, 1, true);
        r++;


        JButton btAbonar = new JButton("Registrar ABONO");
        btAbonar.addActionListener(_e -> guardarAbono());
        addCell(pay,d,3,r,btAbonar,1,false);

        add(pay, BorderLayout.CENTER);

        // estado inicial
        limpiar();
    }

    private void cargarClienteYNotas() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        
        if (tel.isEmpty()) { limpiar(); return; }
        if (tel.equals(lastTel)) return;

        try {
            // 1) Cliente
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            txtNombre.setText(cr == null ? "— no registrado —"
                                         : (cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto()));

            // 2) Notas CR con saldo
            NotasDAO ndao = new NotasDAO();
            List<Nota> lista = ndao.listarCreditosConSaldo(tel);

            cbNotas.removeAllItems();
            if (lista.isEmpty()) {
                cbNotas.addItem(null);            // placeholder
                lblSaldo.setText("Saldo: $0.00");
            } else {
                for (Nota n : lista) cbNotas.addItem(n);
                cbNotas.setSelectedIndex(0);
                actualizarSaldo();
            }
            // Cargar devoluciones disponibles (DV)
            try {
                cbDV.removeAllItems();
                cbDV.addItem(null); // placeholder
                if (tel != null && !tel.isBlank()) {
                    Controlador.NotasDVDAO dao = new Controlador.NotasDVDAO();
                    java.util.List<Controlador.NotasDVDAO.DVDisponible> dvs = dao.listarDisponiblesPorTelefono(tel);
                    for (Controlador.NotasDVDAO.DVDisponible dv : dvs) {
                        if (dv.disponible > 0.005) cbDV.addItem(dv);
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "No se pudieron consultar devoluciones: " + ex.getMessage(),
                        "Error DV", JOptionPane.ERROR_MESSAGE);
            }

            lastTel = tel;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarSaldo() {
        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) { lblSaldo.setText("Saldo: $0.00"); return; }
        lblSaldo.setText("Saldo: $" + String.format("%.2f", sel.getSaldo()));
    }

private void guardarAbono() {
    Nota sel = (Nota) cbNotas.getSelectedItem();
    if (sel == null) {
        JOptionPane.showMessageDialog(this, "Selecciona una nota de crédito con saldo.");
        return;
    }

    double saldo = sel.getSaldo();
    double abono = sumaPagos();
    if (abono <= 0.0) {
        JOptionPane.showMessageDialog(this, "Captura un abono mayor a cero.");
        return;
    }
    if (abono > saldo + 0.005) {
        JOptionPane.showMessageDialog(this, "El abono no puede ser mayor al saldo (" +
                String.format("%.2f", saldo) + ").");
        return;
    }

    int r = JOptionPane.showConfirmDialog(this,
            "¿Registrar ABONO a la nota " + sel.getNumeroNota() + "?\n" +
                    "Abono: " + String.format("%.2f", abono) + "\nSaldo actual: " +
                    String.format("%.2f", saldo),
            "Confirmación", JOptionPane.YES_NO_OPTION);
    if (r != JOptionPane.YES_OPTION) return;

    PagoFormas p = new PagoFormas();
    p.setFechaOperacion(LocalDate.now());
    p.setTarjetaCredito(nullIfZero(parse(txtTC)));
    p.setTarjetaDebito(nullIfZero(parse(txtTD)));
    p.setAmericanExpress(nullIfZero(parse(txtAMX)));
    p.setTransferencia(nullIfZero(parse(txtTRF)));
    p.setDeposito(nullIfZero(parse(txtDEP)));
    p.setEfectivo(nullIfZero(parse(txtEFE)));
    p.setDevolucion(nullIfZero(parse(txtMontoDV)));
    p.setTipoOperacion("AB");
    p.setStatus("A");

    Controlador.NotasDVDAO.DVDisponible dvSel = (Controlador.NotasDVDAO.DVDisponible) cbDV.getSelectedItem();
    double montoDV = parse(txtMontoDV);
    if (dvSel != null) {
        if (montoDV <= 0.005) {
            JOptionPane.showMessageDialog(this, "Ingresa un monto válido para usar el folio de devolución.");
            return;
        }
        if (montoDV > dvSel.disponible + 0.005) {
            JOptionPane.showMessageDialog(this, "El monto de devolución no puede exceder lo disponible (" +
                    String.format("%.2f", dvSel.disponible) + ").");
            return;
        }
        p.setDevolucion(montoDV);
        p.setReferenciaDV(dvSel.folio);
    }

AbonoService svc = new AbonoService();
try (Connection cn = Conexion.Conecta.getConnection()) {
    cn.setAutoCommit(false);
    AbonoService.ResultadoAbono res = svc.registrarAbono(cn, sel.getNumeroNota(), p);

    EmpresaInfo emp = cargarEmpresaInfo();
    List<NotaDetalle> dets = new NotasDAO().listarDetalleDeNota(sel.getNumeroNota());
    String folioPrint = (res.folio == null || res.folio.isBlank()) ? "—" : res.folio;

    // saldo = saldo ANTERIOR al abono (lo definiste arriba)
    Printable prn = construirPrintableAbono(
            emp, sel, dets, p,
            abono,
            saldo,         // ← saldo anterior
            res.saldoNuevo,
            folioPrint, cajeraCodigo, cajeraNombre);


        if (Math.abs(res.saldoNuevo) <= 0.005) {
            // --- LIQUIDADO ---
            // Ahora SOLO se imprime el recibo de abono, sin hoja de condiciones
            imprimirYConfirmarAsync(
                    prn,
                    () -> JOptionPane.showMessageDialog(this,
                            "Abono final registrado e impreso.\nSaldo liquidado.\nFolio: " + folioPrint),
                    () -> JOptionPane.showMessageDialog(this,
                            "Abono final registrado pero la impresión no se confirmó.\nSaldo liquidado.",
                            "Aviso", JOptionPane.WARNING_MESSAGE)
            );
            // Notificar a Corte de Caja que hubo una operación completada
            try {
                Utilidades.EventBus.notificarOperacionFinalizada();
            } catch (Exception ignore) {}

        } else {
            // --- NORMAL ---
            imprimirYConfirmarAsync(
                    prn,
                    () -> JOptionPane.showMessageDialog(this,
                            "Abono registrado e impreso.\nFolio: " + folioPrint),
                    () -> JOptionPane.showMessageDialog(this,
                            "Abono registrado pero impresión no confirmada.")
            );
            // Notificar a Corte de Caja que hubo una operación completada
            try {
                Utilidades.EventBus.notificarOperacionFinalizada();
            } catch (Exception ignore) {}
        }

        cn.commit();
        // Después de registrar e imprimir el abono, dejamos la UI lista para el siguiente cliente
        limpiar();


    } catch (Exception ex) {
        try (Connection c2 = Conexion.Conecta.getConnection()) {
            if (c2 != null && !c2.getAutoCommit()) c2.rollback();
        } catch (Exception ignore) {}
        JOptionPane.showMessageDialog(this,
                "Ocurrió un error, se canceló el abono.\n\nDetalle: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
        limpiar();
    }
}
    // ---------- Helpers UI / numéricos ----------

    // Helper null-safe como en tus otros paneles
private String n(String s) {
    return (s == null) ? "" : s;
}
private String obtenerOModificarCondiciones(int numeroNota, Connection cn) throws SQLException {
    String memo;

    // usar la misma conexión
    try (PreparedStatement ps = cn.prepareStatement(
            "SELECT memo FROM notas_memo WHERE numero_nota = ?")) {
        ps.setInt(1, numeroNota);
        try (ResultSet rs = ps.executeQuery()) {
            memo = rs.next() ? rs.getString("memo") : null;
        }
    }

    if (memo == null || memo.isBlank()) {
        memo = obtenerCondicionesPredeterminadas();
    }

    Map<String, String> vars = construirVarsDesdeNota(numeroNota);
    String renderizado = renderMemo(memo, vars);

    JTextArea area = new JTextArea(renderizado, 18, 60);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);

    int r = JOptionPane.showConfirmDialog(
            this,
            new JScrollPane(area),
            "Condiciones de venta (nota " + numeroNota + ")",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
    );

    if (r != JOptionPane.OK_OPTION) return null;

    String finalMemo = area.getText().trim();

    try (PreparedStatement ps = cn.prepareStatement(
            "INSERT INTO notas_memo (numero_nota, memo) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE memo = VALUES(memo)")) {
        ps.setInt(1, numeroNota);
        ps.setString(2, finalMemo);
        ps.executeUpdate();
    }

    return finalMemo;
}
private static Printable combinarEnDosPaginas(Printable p0, Printable p1) {
    return (g, pf, pageIndex) -> {
        if (pageIndex == 0) return p0.print(g, pf, 0);
        if (pageIndex == 1) return p1.print(g, pf, 0);
        return Printable.NO_SUCH_PAGE;
    };
}

    /**
 * Si el crédito queda liquidado (saldo = 0), permite editar e imprimir
 * el formato de condiciones correspondiente a esa nota.
 */
private void mostrarEditorCondicionesYImprimir(int numeroNota) {
    try {
        // 1. Obtener texto actual del memo (si existe)
        String memoExistente = null;
        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT memo FROM notas_memo WHERE numero_nota = ?")) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) memoExistente = rs.getString(1);
            }
        }

        // 2. Si no hay memo guardado, traer el formato predeterminado
        String base = (memoExistente == null || memoExistente.isBlank())
                ? obtenerCondicionesPredeterminadas()
                : memoExistente;

        // 3. Render preliminar con variables
        Map<String, String> vars = construirVarsDesdeNota(numeroNota);
        String borrador = renderMemo(base, vars);

        // 4. Editor de texto (idéntico a editarMemoPrevia)
        JTextArea area = new JTextArea(borrador, 18, 60);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        int r = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(area),
                "Condiciones de venta (nota " + numeroNota + ")",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (r != JOptionPane.OK_OPTION) return;

        String memoFinal = area.getText().trim();
        if (memoFinal.isEmpty()) memoFinal = null;

        // 5. Guardar memo actualizado
        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     """
                     INSERT INTO notas_memo (numero_nota, memo)
                     VALUES (?, ?)
                     ON DUPLICATE KEY UPDATE memo = VALUES(memo)
                     """)) {
            ps.setInt(1, numeroNota);
            ps.setString(2, memoFinal);
            ps.executeUpdate();
        }

        // 6. Imprimir formato final
        imprimirFormatoCondiciones(numeroNota, memoFinal, vars);

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "Error al mostrar o imprimir condiciones: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }
}
private String obtenerCondicionesPredeterminadas() {
    try (Connection cn = Conexion.Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement("SELECT texto FROM empresa_condiciones WHERE id=1");
         ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getString("texto");
    } catch (SQLException ex) {
        System.err.println("Error leyendo condiciones predeterminadas: " + ex.getMessage());
    }
    return "";
}

private Map<String, String> construirVarsDesdeNota(int numeroNota) throws SQLException {
    Map<String, String> map = new HashMap<>();
    final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy"); // Helper format

    try (Connection cn = Conexion.Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement("""
            SELECT 
                n.folio,
                n.total,
                n.fecha_registro,
                n.telefono,
                CONCAT(c.nombre, ' ', c.apellido_paterno, ' ', c.apellido_materno) AS cliente_nombre,
                c.fecha_evento,
                c.fecha_entrega,
                c.busto,
                c.cintura,
                c.cadera,
                a.nombre_completo AS asesora
            FROM Notas n
            LEFT JOIN Clientes c ON n.telefono = c.telefono1
            LEFT JOIN asesor a ON n.asesor = a.numero_empleado
            WHERE n.numero_nota = ?
        """)) {

        ps.setInt(1, numeroNota);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                map.put("folio", rs.getString("folio"));
                map.put("cliente_nombre", rs.getString("cliente_nombre"));
                map.put("fecha_compra", safeDate(rs.getDate("fecha_registro"), MX));
                map.put("fecha_evento", safeDate(rs.getDate("fecha_evento"), MX));
                map.put("fecha_en_tienda", safeDate(rs.getDate("fecha_entrega"), MX));
                map.put("asesora", rs.getString("asesora"));
                map.put("busto", safeNum(rs.getString("busto")));
                map.put("cintura", safeNum(rs.getString("cintura")));
                map.put("cadera", safeNum(rs.getString("cadera")));
                
                // --- INICIO: NUEVO BLOQUE PARA DETALLE DE ARTÍCULO ---
                try (PreparedStatement psDetalle = cn.prepareStatement(
                        "SELECT * FROM nota_detalle WHERE numero_nota = ? ORDER BY id LIMIT 1")) {
                    
                    psDetalle.setInt(1, numeroNota);
                    try (ResultSet rsDetalle = psDetalle.executeQuery()) {
                        if (rsDetalle.next()) {
                            map.put("modelo", safeNum(rsDetalle.getString("modelo")));
                            map.put("marca", safeNum(rsDetalle.getString("marca")));
                            map.put("color", safeNum(rsDetalle.getString("color")));
                            map.put("talla", safeNum(rsDetalle.getString("talla")));
                            map.put("codigo", safeNum(rsDetalle.getString("codigo_articulo")));
                            
                            double precio = rsDetalle.getDouble("precio");
                            double desc = rsDetalle.getDouble("descuento");
                            double pagar = rsDetalle.getDouble("subtotal");
                            
                            map.put("precio", String.format("%.2f", precio));
                            map.put("descuento_pct", String.format("%.2f", desc));
                            map.put("precio_pagar", String.format("%.2f", pagar));
                        }
                    }
                }
                // --- FIN: NUEVO BLOQUE ---
            }
        }
    }
    
    // También agregar whatsapp
    try {
        EmpresaInfo emp = cargarEmpresaInfo();
        map.put("whatsapp", n(emp.whatsapp));
    } catch (Exception ignore) {}

    return map;
}
// REEMPLAZA tu método safeDate(java.sql.Date d) con ESTOS DOS:
private String safeDate(java.sql.Date d, DateTimeFormatter formatter) {
    if (d == null) return "";
    return d.toLocalDate().format(formatter);
}

private String safeDate(java.sql.Date d) {
    if (d == null) return "";
    return d.toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
}

// Fecha larga con mes en español, estilo "12 de diciembre de 2025"
private static final Locale LOCALE_ES_MX = Locale.of("es", "MX");
    private static final DateTimeFormatter MX_LARGO =
        DateTimeFormatter.ofPattern("dd-MMMM-yyyy", LOCALE_ES_MX);
private String fechaLarga(LocalDate f) {
    if (f == null) return "";
    return f.format(MX_LARGO);
}

// AGREGA TODOS ESTOS MÉTODOS DE AYUDA (helpers)
private static void centerText(Graphics2D g2, String text, int x, int w, int y) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int cx = x + (w - fm.stringWidth(text)) / 2;
    g2.drawString(text, cx, y);
}
/** Formatea teléfono para impresión tipo 123-456-7890 */
private String formatearTelefonoImpresion(String tel) {
    if (tel == null) return "";
    String dig = tel.replaceAll("\\D+", "");
    if (dig.isEmpty()) return "";
    if (dig.length() == 10) {
        return dig.substring(0, 3) + "-" + dig.substring(3, 6) + "-" + dig.substring(6);
    } else if (dig.length() == 7) {
        return dig.substring(0, 3) + "-" + dig.substring(3);
    }
    // Si no coincide con longitudes típicas, regresamos los dígitos "pelones"
    return dig;
}

private static int drawWrappedSimple(Graphics2D g2, String text, int x, int y, int maxWidth) {
    if (text == null || text.trim().isEmpty()) return y;
    java.awt.FontMetrics fm = g2.getFontMetrics();
    String[] words = text.split("\\s+");
    StringBuilder line = new StringBuilder();
    
    for (String word : words) {
        String tryLine = (line.length() == 0 ? word : line + " " + word);
        if (fm.stringWidth(tryLine) <= maxWidth) {
            line.setLength(0);
            line.append(tryLine);
        } else {
            g2.drawString(line.toString(), x, y);
            y += fm.getHeight(); // Usa el alto de la fuente
            line.setLength(0);
            line.append(word);
        }
    }
    
    if (line.length() > 0) {
        g2.drawString(line.toString(), x, y);
    }

    return y + fm.getHeight(); // Avanza a la siguiente línea
}

// ----- INICIA BLOQUE DE NUEVOS HELPERS (REEMPLAZA LOS 4 ANTERIORES) -----

/** Dibuja: LABEL: [Valor encima de la línea]. Devuelve el nuevo y. */
private static int drawFieldLine(Graphics2D g2, int x, int y, int w, String label, String value) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int yy = y + fm.getAscent();
    int lw = fm.stringWidth(label) + 6;
    g2.drawString(label, x, yy);
    
    int valueX = x + lw + 3;
    int x_end = x + w;

    // 1. Dibuja la LÍNEA PRIMERO (continua)
    g2.drawLine(valueX, yy + 3, x_end, yy + 3);

    // 2. Dibuja el VALOR ENCIMA de la línea
    if (value != null && !value.isBlank()) {
        g2.drawString(value, valueX, yy);
    }
    return y + 18;
}

private static int drawTwoColsLine2575(Graphics2D g2, int x, int y, int w,
                                       String leftLabel, String leftVal,
                                       String rightLabel, String rightVal) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    // ----- ESTE ES EL CAMBIO: 25% para la columna 1 -----
    int col1_width = (int)((w - 20) * 0.15); // Más pequeño para CODIGO
    int x1_end = x + col1_width;
    // ---------------------------------------------------
    int xr = x1_end + 20;
    int x2_end = x + w;
    int yy = y + fm.getAscent();

    // Col 1 (25%)
    int lw = fm.stringWidth(leftLabel) + 6;
    g2.drawString(leftLabel, x, yy);
    int value1X = x + lw + 3;
    g2.drawLine(value1X, yy + 3, x1_end, yy + 3); // Dibuja línea
    if (leftVal != null && !leftVal.isBlank()) {
        g2.drawString(leftVal, value1X, yy); // Dibuja valor encima
    }

    // Col 2 (75%)
    int rw = fm.stringWidth(rightLabel) + 6;
    g2.drawString(rightLabel, xr, yy);
    int value2X = xr + rw + 3;
    g2.drawLine(value2X, yy + 3, x2_end, yy + 3); // Dibuja línea
    if (rightVal != null && !rightVal.isBlank()) {
        g2.drawString(rightVal, value2X, yy); // Dibuja valor encima
    }

    return y + 18;
}

/** Dibuja 2 columnas (35/65) [Valor encima de la línea]. (Para CODIGO y VESTIDO) */
private static int drawTwoColsLine(Graphics2D g2, int x, int y, int w,
                                       String leftLabel, String leftVal,
                                       String rightLabel, String rightVal) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    // ----- ESTE ES EL CAMBIO: 35% para la columna 1 -----
    int col1_width = (int)((w - 20) * 0.35);
    int x1_end = x + col1_width;
    // ---------------------------------------------------
    int xr = x1_end + 20;
    int x2_end = x + w;
    int yy = y + fm.getAscent();

    // Col 1 (35%)
    int lw = fm.stringWidth(leftLabel) + 6;
    g2.drawString(leftLabel, x, yy);
    int value1X = x + lw + 3;
    g2.drawLine(value1X, yy + 3, x1_end, yy + 3); // Dibuja línea
    if (leftVal != null && !leftVal.isBlank()) {
        g2.drawString(leftVal, value1X, yy); // Dibuja valor encima
    }

    // Col 2 (65%)
    int rw = fm.stringWidth(rightLabel) + 6;
    g2.drawString(rightLabel, xr, yy);
    int value2X = xr + rw + 3;
    g2.drawLine(value2X, yy + 3, x2_end, yy + 3); // Dibuja línea
    if (rightVal != null && !rightVal.isBlank()) {
        g2.drawString(rightVal, value2X, yy); // Dibuja valor encima
    }

    return y + 18;
}

/** Dibuja 2 columnas (70/30) [Valor encima de la línea]. (Para DESCUENTO y PAGAR) */
private static int drawTwoColsLine7030(Graphics2D g2, int x, int y, int w,
                                       String leftLabel, String leftVal,
                                       String rightLabel, String rightVal) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    // ----- ESTE ES EL CAMBIO: 70% para la columna 1 -----
    int col1_width = (int)((w - 20) * 0.70);
    int x1_end = x + col1_width;
    // ---------------------------------------------------
    int xr = x1_end + 20;
    int x2_end = x + w;
    int yy = y + fm.getAscent();

    // Col 1 (70%)
    int lw = fm.stringWidth(leftLabel) + 6;
    g2.drawString(leftLabel, x, yy);
    int value1X = x + lw + 3;
    g2.drawLine(value1X, yy + 3, x1_end, yy + 3); // Dibuja línea
    if (leftVal != null && !leftVal.isBlank()) {
        g2.drawString(leftVal, value1X, yy); // Dibuja valor encima
    }

    // Col 2 (30%)
    int rw = fm.stringWidth(rightLabel) + 6;
    g2.drawString(rightLabel, xr, yy);
    int value2X = xr + rw + 3;
    g2.drawLine(value2X, yy + 3, x2_end, yy + 3); // Dibuja línea
    if (rightVal != null && !rightVal.isBlank()) {
        g2.drawString(rightVal, value2X, yy); // Dibuja valor encima
    }

    return y + 18;
}


/** Dibuja 3 columnas (33/33/33) [Valor encima de la línea]. */
private static int drawThreeColsLine(Graphics2D g2, int x, int y, int w,
                                     String leftLabel, String leftVal,
                                     String midLabel, String midVal,
                                     String rightLabel, String rightVal) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int third = (w - 40) / 3;
    int yy = y + fm.getAscent();

    int x1_end = x + third;
    int xm = x1_end + 20;
    int x2_end = xm + third;
    int xr = x2_end + 20;
    int x3_end = x + w;

    // Col 1
    int lw = fm.stringWidth(leftLabel) + 6;
    g2.drawString(leftLabel, x, yy);
    int value1X = x + lw + 3;
    g2.drawLine(value1X, yy + 3, x1_end, yy + 3); // Dibuja línea
    if (leftVal != null && !leftVal.isBlank()) {
        g2.drawString(leftVal, value1X, yy); // Dibuja valor encima
    }

    // Col 2
    int mw = fm.stringWidth(midLabel) + 6;
    g2.drawString(midLabel, xm, yy);
    int value2X = xm + mw + 3;
    g2.drawLine(value2X, yy + 3, x2_end, yy + 3); // Dibuja línea
    if (midVal != null && !midVal.isBlank()) {
        g2.drawString(midVal, value2X, yy); // Dibuja valor encima
    }

    // Col 3
    int rw = fm.stringWidth(rightLabel) + 6;
    g2.drawString(rightLabel, xr, yy);
    int value3X = xr + rw + 3;
    g2.drawLine(value3X, yy + 3, x3_end, yy + 3); // Dibuja línea
    if (rightVal != null && !rightVal.isBlank()) {
        g2.drawString(rightVal, value3X, yy); // Dibuja valor encima
    }

    return y + 18;
}

// ----- TERMINA BLOQUE DE NUEVOS HELPERS -----

private String safeNum(String s) {
    return (s == null || s.isBlank()) ? "—" : s;
}

private String renderMemo(String base, Map<String, String> vars) {
    String result = base;
    for (Map.Entry<String, String> e : vars.entrySet()) {
        String token = "\\{" + e.getKey() + "\\}";
        result = result.replaceAll(token, e.getValue() == null ? "" : e.getValue());
    }
    return result;
}

private void imprimirFormatoCondiciones(int numeroNota, String memo, Map<String, String> vars) {
    try {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable((g, pf, page) -> {
            if (page > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;
            g2.translate(pf.getImageableX(), pf.getImageableY());
            g2.setFont(new Font("Serif", Font.PLAIN, 11));
            int y = 40;
            for (String line : memo.split("\n")) {
                g2.drawString(line, 50, y);
                y += 14;
            }
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) job.print();
    } catch (PrinterException ex) {
        JOptionPane.showMessageDialog(this, "Error al imprimir condiciones: " + ex.getMessage());
    }
}


    /** Deja la vista en blanco/estado inicial. */
    private void limpiar() {
        // ======= LIMPIAR UI =======
        txtTelefono.setText("");  // Limpiar teléfono
        txtNombre.setText("— no registrado —");  // Limpiar nombre

        // Limpiar el combo de notas con saldo
        cbNotas.removeAllItems();
        lblSaldo.setText("Saldo: $0.00");  // Limpiar saldo

        // Limpiar las formas de pago
        txtTC.setText("");
        txtTD.setText("");
        txtAMX.setText("");
        txtTRF.setText("");
        txtDEP.setText("");
        txtEFE.setText("");
        txtMontoDV.setText("");  // Limpiar monto de devolución

        // Limpiar folios de devolución
        cbDV.removeAllItems();
        txtMontoDV.setEnabled(false);
        

        // Limpiar datos del cliente
        txtTelefono.setText("");
        txtNombre.setText("— no registrado —");

        // Reset bandera
        lastTel = null;

        // Enfocar el campo teléfono para el siguiente cliente
        txtTelefono.requestFocus();

    }

    private JTextField readOnly() {
        JTextField t = new JTextField();
        t.setEditable(false);
        t.setOpaque(true);
        Color ro = UIManager.getColor("TextField.inactiveBackground");
        if (ro == null) ro = new Color(235,235,235);
        t.setBackground(ro);
        return t;
    }

    private void addCell(JPanel p, GridBagConstraints c, int x,int y,JComponent comp,int span,boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }

    private JTextField moneyField(){
        JTextField t = new JTextField();
        DocumentFilter df;
        try { df = new DialogArticulo.DecimalFilter(12); } // si la tienes
        catch (Throwable __) { df = new SimpleDecimalFilter(); }
        ((AbstractDocument) t.getDocument()).setDocumentFilter(df);
        return t;
    }

    private double parse(JTextField t){
        String s = t.getText()==null? "" : t.getText().trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch(Exception e){ return 0; }
    }

    private Double nullIfZero(double v){ return Math.abs(v) < 0.0001 ? null : v; }

    private double sumaPagos(){
    double s = parse(txtTC)+parse(txtTD)+parse(txtAMX)+parse(txtTRF)+parse(txtDEP)+parse(txtEFE);
    s += parse(txtMontoDV); // incluir folio DV como método de pago
    return s;
}
public String numeroALetras(double valor) {
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
        sb.append(tres.apply((int)(milesDeMillones % 1000))).append(milesDeMillones==1? " mil millones" : " mil millones");
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


    /** Filtro decimal simple si no tienes el de DialogArticulo */
    static class SimpleDecimalFilter extends javax.swing.text.DocumentFilter {
        @Override public void replace(FilterBypass fb,int offs,int len,String txt,javax.swing.text.AttributeSet a)
                throws javax.swing.text.BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offs) + (txt==null?"":txt) + cur.substring(offs+len);
            if (next.matches("\\d*(\\.\\d{0,2})?")) super.replace(fb, offs, len, txt, a);
        }
        @Override public void insertString(FilterBypass fb,int offs,String str,javax.swing.text.AttributeSet a)
                throws javax.swing.text.BadLocationException { replace(fb, offs, 0, str, a); }
    }

    // DocumentListener minimalista para “on change”
    @FunctionalInterface interface SimpleDocListener extends javax.swing.event.DocumentListener {
        void on();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e){ on(); }
    }
    /* =========================
   IMPRESIÓN  ABONO
   ========================= */

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
        }, "nota-print-job").start();

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
// Lee de BD el número (PK) de la empresa a usar para impresión.
// Toma la primera fila de la tabla Empresa.
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

// ---------- Printable de ABONO (sin obsequios) ----------
private Printable construirPrintableAbono(
        EmpresaInfo emp,
        Nota notaBase,                 // contiene total/saldo/telefono/numero
        List<NotaDetalle> dets,        // detalle de artículos vendidos
        PagoFormas p,                  // lo que se abonó por forma
        double abonoRealizado,         // suma de pagos capturados
        double saldoAnterior,          // saldo antes del abono
        double saldoRestante,          // nuevo saldo después del abono
        String folioTxt,
        int cajeraCodigo,
        String cajeraNombre) {

    // Teléfono tal como viene en la nota
    final String tel1Raw    = (notaBase.getTelefono() == null) ? "" : notaBase.getTelefono();
    // Teléfono formateado para impresión (123-456-7890)
    final String fTel1Print = formatearTelefonoImpresion(tel1Raw);
    // Fecha de abono con mes en español
    final String fechaHoy   = fechaLarga(LocalDate.now());
    final double total      = (notaBase.getTotal() == null) ? 0d : notaBase.getTotal();

    final double tc = p.getTarjetaCredito()   == null ? 0d : p.getTarjetaCredito();
    final double td = p.getTarjetaDebito()    == null ? 0d : p.getTarjetaDebito();
    final double am = p.getAmericanExpress()  == null ? 0d : p.getAmericanExpress();
    final double tr = p.getTransferencia()    == null ? 0d : p.getTransferencia();
    final double dp = p.getDeposito()         == null ? 0d : p.getDeposito();
    final double ef = p.getEfectivo()         == null ? 0d : p.getEfectivo();

    // Cliente (nombre/tel2 y pruebas si existen)
    String cliNombre      = "";
    String cliTel2        = "";
    String cliPrueba1     = "";
    String cliPrueba2     = "";
    String cliFechaEvento = "";

    // Buscar resumen del cliente
    try {
        clienteDAO cdao = new clienteDAO();
        ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1Raw);
        if (cr != null) {
            cliNombre = (cr.getNombreCompleto() == null) ? "" : cr.getNombreCompleto();

            cliTel2 = (cr.getTelefono2() == null) ? "" : cr.getTelefono2();
            cliTel2 = formatearTelefonoImpresion(cliTel2);

            if (cr.getFechaEvento()  != null) cliFechaEvento = fechaLarga(cr.getFechaEvento());
            if (cr.getFechaPrueba1() != null) cliPrueba1     = fechaLarga(cr.getFechaPrueba1());
            if (cr.getFechaPrueba2() != null) cliPrueba2     = fechaLarga(cr.getFechaPrueba2());
        }
    } catch (Exception ignore) { }

    final String folio           = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;
    final String fCajeraCodigo   = (cajeraCodigo == 0 ? "" : String.valueOf(cajeraCodigo).trim());
    final String fCajeraNombre   = (cajeraNombre == null ? "" : cajeraNombre.trim());
    final String fCliNombre      = cliNombre;
    final String fCliTel2        = cliTel2;
    final String fCliPrueba1     = cliPrueba1;
    final String fCliPrueba2     = cliPrueba2;
    final String fCliFechaEvento = cliFechaEvento;


    return new Printable() {
        @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;

            // Márgenes ~0.5"
            final int MARGIN = 36;
            final int x0 = (int) Math.round(pf.getImageableX());
            final int y0 = (int) Math.round(pf.getImageableY());
            final int W  = (int) Math.round(pf.getImageableWidth());
            int x = x0 + MARGIN;
            int y = y0 + MARGIN;
            int w = W  - (MARGIN * 2);

            // Tipos
            final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
            final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
            final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
            final Font fText    = g2.getFont().deriveFont(10.2f);
            final Font fSmall   = g2.getFont().deriveFont(8.8f);

            // Encabezado empresa
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
            String telEmpPrint = formatearTelefonoImpresion(emp.telefono);
            String waEmpPrint  = formatearTelefonoImpresion(emp.whatsapp);
            yy = drawWrapped(g2, joinNonBlank("   ",
            labelIf("Tel: ", telEmpPrint),
            labelIf("WhatsApp: ", waEmpPrint)), leftTextX, yy + 2, infoTextWidth);

            // === CONTACTO EN COLUMNA A LA DERECHA (debajo del folio) ===
            final int rightColW = 120;               // ancho de la columna derecha
            final int xRight    = x + w - rightColW; // X de la columna
            int yRight          = y + 22;            // un poco debajo del folio

            g2.setFont(fSmall);

            // 1) Correo y Web
            yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
            yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

            // 2) Redes con ícono + usuario (16px, mismos tamaños)
            BufferedImage igIcon = loadIcon("instagram.png");
            BufferedImage fbIcon = loadIcon("facebook.png");
            BufferedImage ttIcon = loadIcon("tiktok.png");

            yRight = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRight, rightColW, 12, 6);
            yRight = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRight, rightColW, 12, 6);
            yRight = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRight, rightColW, 12, 6);

            // Altura real del encabezado (máximo entre bloque izquierdo y columna derecha)
            int leftBlockH  = Math.max(yy - y, headerH);
            int rightBlockH = Math.max(0, yRight - y);
            int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;

            // Continúa debajo del encabezado
            int afterTail = y + usedHeader;

            g2.setFont(fH1);
            rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

            usedHeader = Math.max(yy - y, headerH) + 6;

            // Título
            g2.setFont(fTitle);
            center(g2, "NOTA DE ABONO", x, w, afterTail + 14);
            y = afterTail + 32;

            // ===== Datos del cliente (2 columnas) =====
            g2.setFont(fSection);
            g2.drawString("Datos del cliente", x, y);
            y += 13;

            final int gapCols = 24;
            final int leftW   = (w - gapCols) / 2;
            final int rightW  = w - gapCols - leftW;

            int yLeft  = y;
            yRight     = y;  // <-- reutiliza el yRight ya declarado arriba

            g2.setFont(fText);
            yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(fCliNombre)), x, yLeft, leftW);
            yLeft  = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Teléfono: ", safe(fTel1Print)),
                    labelIf("Teléfono 2: ", safe(fCliTel2))), x, yLeft + 2, leftW);

            yRight = drawWrapped(g2, labelIf("Fecha de abono: ", fechaHoy), x + leftW + gapCols, yRight, rightW);
            if (!fCliFechaEvento.isBlank()) {
            yRight = drawWrapped(g2, labelIf("Fecha de evento: ", fCliFechaEvento),
                                x + leftW + gapCols, yRight + 2, rightW);
                }
            if (!fCliPrueba1.isBlank())
                yRight = drawWrapped(g2, labelIf("Fecha de prueba 1: ", fCliPrueba1), x + leftW + gapCols, yRight + 2, rightW);
            if (!fCliPrueba2.isBlank())
                yRight = drawWrapped(g2, labelIf("Fecha de prueba 2: ", fCliPrueba2), x + leftW + gapCols, yRight + 2, rightW);

            

            y = Math.max(yLeft, yRight) + 10;

            // Tabla artículos (detalle de la venta)
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
                for (NotaDetalle d : dets) {
                    String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                            ? d.getCodigoArticulo() : d.getArticulo();
                String detalle = (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty()  ? d.getCodigoArticulo() + " · " : "")
                            + safe(artBase)
                            + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                            + " | " + labelIf("Color: ", safe(d.getColor()))
                            + " | " + labelIf("Talla: ", safe(d.getTalla()));

                    int yRowStart = y;                                    // <-- guarda el y de la PRIMERA línea
                    int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                    g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                    g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                    g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                    y = yAfter + 12; 
                }
            } else {
                y = drawWrapped(g2, "(Detalle no disponible)", xArt, y, colArtW);
            }

            // === Observaciones (solo si existen en la nota base) ===
try {
    String obs = new Controlador.NotasObservacionesDAO().getByNota(notaBase.getNumeroNota());
    if (obs != null && !obs.isBlank()) {
        y += 6; g2.drawLine(x, y, x + w, y); y += 16;
        g2.setFont(fSection);
        g2.drawString("Observaciones", x, y);
        y += 12;
        g2.setFont(fText);
        y = drawWrapped(g2, obs, x + 4, y, w - 8);
    }
} catch (Exception ignore) {}
            y += 6; g2.drawLine(x, y, x + w, y); y += 16;


            // Totales (a la derecha)
            g2.setFont(fH1);
            rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y);
            y += 22;

            g2.setFont(fText);
            String abonoLetra = numeroALetras(abonoRealizado);  // Convierte el abono a letras
            int anchoLetras = w - 230;
            
            int yInicioTotales = y - 24; // solo hubo una línea (TOTAL)
            drawWrapped(g2, "Abono en letra: " + abonoLetra, x, yInicioTotales, anchoLetras);  // Imprime el abono en letras
            y += 22;

            // Saldo anterior (antes del abono actual)
            rightAlign(g2, "Saldo anterior: $" + fmt2(saldoAnterior), x, w, y);
            y += 14;

            g2.setFont(fText);
            rightAlign(g2, "Abono: $" + fmt2(abonoRealizado), x, w, y); // <— aquí dice ABONO
            y += 14;

            g2.setFont(fH1);
            rightAlign(g2, "SALDO RESTANTE: $" + fmt2(saldoRestante), x, w, y);
            y += 22;

            if (Math.abs(saldoRestante) < 0.005) {
                g2.setFont(fH1);
                center(g2, "Venta liquidada", x, w, y);
                y += 18;
            }

            // Pagos (dos columnas)
            g2.setFont(fSection);
            g2.drawString("Pagos", x, y); y += 12; g2.setFont(fText);

            int yPLeft = y, yPRight = y;
            yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW);
            yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW);
            yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW);
            // Solo imprimir si hubo pago con devolución
            if (p.getDevolucion() != null && p.getDevolucion() > 0) {
                String folioDv = (p.getReferenciaDV() != null && !p.getReferenciaDV().isBlank())
                        ? " (" + p.getReferenciaDV() + ")" : "";
                yPLeft = drawWrapped(g2, "Devolución: $" + fmt2(p.getDevolucion()) + folioDv, x, yPLeft + 2, leftW);
            }

            yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW + gapCols, yPRight, rightW);
            yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW + gapCols, yPRight + 2, rightW);
            yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW + gapCols, yPRight + 2, rightW);

            // Terminamos la sección de pagos: bajar y hasta lo más grande entre izquierda y derecha
            y = Math.max(yPLeft, yPRight) + 10;
            // ===== Cajera (pie de página) =====
            String cajeraLinea = "Cajera: " + safe(fCajeraCodigo);
            if (!fCajeraNombre.isEmpty()) {
                cajeraLinea += " - " + fCajeraNombre;
            }
            y += 16;
            g2.drawLine(x, y, x + w, y);
            y += 14;
            g2.setFont(fText);
            g2.drawString(cajeraLinea, x, y);
            return PAGE_EXISTS;
        }

        // helpers locales
        private BufferedImage loadIcon(String pathOrName) {
    try {
        // 1) Nombre base
        String name = pathOrName;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);

        // 2) Override en disco: ./icons/<name>
        java.nio.file.Path override = java.nio.file.Paths.get(
                System.getProperty("user.dir"), "icons", name);
        if (java.nio.file.Files.exists(override)) {
            javax.imageio.ImageIO.setUseCache(false);
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                return trimTransparent(javax.imageio.ImageIO.read(in));
            }
        }

        // 3) Fallback a classpath (/icons/<name>)
        String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
        try (java.io.InputStream in = AbonoPanel.class.getResourceAsStream(cpPath)) {
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
    };
}
private void seleccionarClientePorApellido() {
    Window owner = SwingUtilities.getWindowAncestor(this);
    DialogBusquedaCliente dlg = new DialogBusquedaCliente(owner);
    dlg.setLocationRelativeTo(this);
    dlg.setVisible(true);

    ClienteResumen cr = dlg.getSeleccionado();
    if (cr != null) {
        // Tel principal del cliente
        String tel = Utilidades.TelefonosUI.soloDigitos(cr.getTelefono1());
        if (tel != null && !tel.isEmpty()) {
            // Forzamos a que se recargue aunque sea el mismo teléfono
            lastTel = null;
            txtTelefono.setText(tel);
            // Nuestro listener ya llama a cargarClienteYNotas,
            // pero llamamos directo también para asegurarnos.
            cargarClienteYNotas();
        }
    }
}
// ===================
// Diálogo búsqueda cliente por apellido
// ===================
private static class DialogBusquedaCliente extends JDialog {

    private JTextField txtApellido;
    private JTable tabla;
    private DefaultTableModel modelo;
    private java.util.List<ClienteResumen> resultados = new java.util.ArrayList<>();
    private ClienteResumen seleccionado;

    public DialogBusquedaCliente(Window owner) {
        super(owner, "Buscar cliente por nombre o apellido", ModalityType.APPLICATION_MODAL);
        construirUI();
    }

    private void construirUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtro
        JPanel pnlFiltro = new JPanel(new BorderLayout(5, 0));
        pnlFiltro.add(new JLabel("Nombre o apellido:"), BorderLayout.WEST);
        txtApellido = new JTextField();
        pnlFiltro.add(txtApellido, BorderLayout.CENTER);

        JButton btnBuscar = new JButton("Buscar");
        pnlFiltro.add(btnBuscar, BorderLayout.EAST);

        main.add(pnlFiltro, BorderLayout.NORTH);

        // Tabla
        modelo = new DefaultTableModel(
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
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    seleccionarActual();
                }
            }
        });

        // Enter en el campo de apellido = buscar
        txtApellido.addActionListener(_e -> buscar());
    }

    private void buscar() {
        String filtro = txtApellido.getText().trim();
        if (filtro.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe al menos una parte del nombre o apellido.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            clienteDAO dao = new clienteDAO();
            resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);  // método que ya usas en ventas
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
                        "No se encontraron clientes con ese nombre o apellido.",
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

}
