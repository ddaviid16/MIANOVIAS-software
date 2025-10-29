// VentaContadoPanel.java
package Vista;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import Controlador.NotasMemoDAO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;

// ===== IMPRESIÓN =====
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import Controlador.AsesorDAO;
import Controlador.InventarioDAO;
import Controlador.NotasDAO;
import Controlador.PedidosDAO;
import Controlador.VentaContadoService;
import Controlador.clienteDAO;
import Controlador.EmpresaDAO;

import Modelo.ClienteResumen;
import Modelo.Inventario;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Modelo.PagoFormas;
import Modelo.Empresa;

public class VentaContadoPanel extends JPanel {

    public interface Navigator {
        void show(String cardId);
        default void prefillClienteTelefono(String tel) {}
    }
    private JButton btnCondiciones;
    private JButton btnObservaciones;
    private String memoEditable; // texto crudo con placeholders (lo que edita el usuario)
    private String observacionesTexto; // texto libre capturado por operador


    private JButton btnAplicarDV;
    private java.util.List<Modelo.PagoDV> dvAplicadas = new java.util.ArrayList<>();

    private final Navigator nav;

    private java.util.List<String> obsequiosSel = new java.util.ArrayList<>();
    private JLabel lblObsequios;

    // ========= Campos cabecera
    private JTextField txtTelefono, txtTelefono2, txtNombreCompleto;
    private JTextField txtFechaEvento, txtFechaPrueba1, txtFechaPrueba2, txtFechaEntrega, txtUltimaNota;
    private JComboBox<Modelo.Asesor> cbAsesor;
    private JButton btnRegistrarCliente;

    // === Control de fecha_evento y fecha_entrega por venta
    private JCheckBox chkUsarFechaCliente;
    private JTextField txtFechaEventoVenta;   // dd-MM-yyyy
    private JTextField txtFechaEntregaVenta;  // dd-MM-yyyy

    // ========= Carrito
    private JTextField txtCod;
    private JTable tb;
    private DefaultTableModel model;

    private final JButton btRegistrarPedido = new JButton("Registrar artículo");

    // ========= Totales y pagos
    private JTextField txtSubtotal, txtTotal;
    private JTextField txtTC, txtTD, txtAMX, txtTRF, txtDEP, txtEFE;
    // === Pago con Devolución ===
    private JComboBox<String> cbFolioDV; // Folio de devolución
    private JTextField txtMontoDV;       // Monto de devolución
    private double montoDVAplicado = 0;  // Valor interno aplicado



    private final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private String lastTelefonoConsultado = null;
    private boolean updatingTable = false;


    private double clamp0a100(double v){ if (v<0) return 0; if (v>100) return 100; return v; }

    public VentaContadoPanel(Navigator navigator) {
        this.nav = navigator;
        buildUI();
        cargarAsesores();
        cargarClienteAutoHooks();
    }
    public VentaContadoPanel() { this(null); }

    private void buildUI() {
        setLayout(new BorderLayout());

        // ====== Encabezado (cliente / asesor / info)
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;

        // Fila 0: Teléfono1 y Teléfono2
        txtTelefono = new JTextField();
        txtTelefono2 = readOnlyField();
        addCell(top,c,0,y,new JLabel("Teléfono cliente:"),1,false);
        addCell(top,c,1,y,txtTelefono,1,true);
        addCell(top,c,2,y,new JLabel("Teléfono 2:"),1,false);
        addCell(top,c,3,y,txtTelefono2,1,true);
        y++;

        // Fila 1: Nombre
        txtNombreCompleto = readOnlyField();
        addCell(top,c,0,y,new JLabel("Nombre y apellidos:"),1,false);
        addCell(top,c,1,y,txtNombreCompleto,3,true);
        y++;

        // Fila 2: Botón "Registrar cliente"
        btnRegistrarCliente = new JButton("Registrar cliente");
        btnRegistrarCliente.setVisible(false);
        btnRegistrarCliente.addActionListener(_e -> abrirFormularioCliente());
        addCell(top, c, 0, y, btnRegistrarCliente, 1, false);
        y++;

        // Fila 3: Asesor
        cbAsesor = new JComboBox<>();
        addCell(top,c,0,y,new JLabel("Asesor*:"),1,false);
        addCell(top,c,1,y,cbAsesor,1,true);
        y++;

        // Fila 4: Fechas (evento y prueba1)
        txtFechaEvento = readOnlyField();
        txtFechaPrueba1 = readOnlyField();
        addCell(top,c,0,y,new JLabel("Fecha de evento (cliente):"),1,false);
        addCell(top,c,1,y,txtFechaEvento,1,true);
        addCell(top,c,2,y,new JLabel("Fecha de prueba 1:"),1,false);
        addCell(top,c,3,y,txtFechaPrueba1,1,true);
        y++;

        // Fila 5: Fechas (prueba2 y entrega)
        txtFechaPrueba2 = readOnlyField();
        txtFechaEntrega = readOnlyField();
        addCell(top,c,0,y,new JLabel("Fecha de prueba 2:"),1,false);
        addCell(top,c,1,y,txtFechaPrueba2,1,true);
        addCell(top,c,2,y,new JLabel("Fecha de entrega:"),1,false);
        addCell(top,c,3,y,txtFechaEntrega,1,true);
        y++;

        // -------- (OCULTO) Última nota del cliente --------
        txtUltimaNota = readOnlyField();

        // ===== Control: fechas por venta
        chkUsarFechaCliente = new JCheckBox("Usar la fecha de evento del cliente");
        chkUsarFechaCliente.setSelected(true);
        txtFechaEventoVenta  = new JTextField();
        txtFechaEntregaVenta = new JTextField();
        txtFechaEventoVenta.setEnabled(false);
        txtFechaEntregaVenta.setEnabled(false);
        chkUsarFechaCliente.addActionListener(_e -> {
            boolean en = !chkUsarFechaCliente.isSelected();
            txtFechaEventoVenta.setEnabled(en);
            txtFechaEntregaVenta.setEnabled(en);
        });

        addCell(top, c, 0, y, chkUsarFechaCliente, 2, true);
        addCell(top, c, 2, y, new JLabel("Fecha de evento para esta venta (dd-MM-yyyy):"), 1, false);
        addCell(top, c, 3, y, txtFechaEventoVenta, 1, true);
        y++;
        addCell(top, c, 2, y, new JLabel("Fecha de entrega para esta venta (dd-MM-yyyy):"), 1, false);
        addCell(top, c, 3, y, txtFechaEntregaVenta, 1, true);
        y++;

        // Fila 7: Código + seleccionar
        txtCod = new JTextField();

        JButton btSeleccionar = new JButton("Seleccionar artículo…");
        btSeleccionar.addActionListener(_e -> seleccionarArticulo());

        JButton btAdd = new JButton("Agregar artículo por código");
        btAdd.addActionListener(_e -> agregarArticulo());

        addCell(top, c, 0, y, new JLabel("Código artículo:"), 1, false);
        addCell(top, c, 1, y, txtCod, 1, true);
        addCell(top, c, 2, y, btSeleccionar, 2, false);
        y++;

        // Botón PEDIR
        btRegistrarPedido.setVisible(false);
        btRegistrarPedido.addActionListener(_e -> abrirDialogoPedido());
        addCell(top, c, 0, y, btRegistrarPedido, 1, false);

        // Botón obsequios
        JButton btObsequios = new JButton("Añadir obsequio(s)");
        btObsequios.addActionListener(_e -> seleccionarObsequios());
        lblObsequios = new JLabel("0 obsequios seleccionados");

        addCell(top, c, 1, y, btAdd, 1, false);
        addCell(top, c, 2, y, btObsequios, 1, false);
        addCell(top, c, 3, y, lblObsequios, 1, false);
        y++;

        ((AbstractDocument) txtCod.getDocument()).addDocumentListener((SimpleDocListener) () -> {
            String s = txtCod.getText();
            btRegistrarPedido.setVisible(s != null && s.trim().equalsIgnoreCase("PEDIR"));
        });

        add(top, BorderLayout.NORTH);

        // ====== Tabla carrito
        String[] cols = {"Código","Artículo","Marca","Modelo","Talla","Color","Fecha art.","Precio","%Desc","Desc. $","Subtotal","Quitar"};
        model = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c){ return c==8 || c==11 || c==6; }
        };
        tb = new JTable(model);
        tb.setRowHeight(26);
        tb.setAutoCreateRowSorter(true);
        tb.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Editor numérico para %Desc (col 8)
        tb.getColumnModel().getColumn(8).setCellEditor(decimalEditor());

        // Alinear numéricos
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        tb.getColumnModel().getColumn(7).setCellRenderer(right);
        tb.getColumnModel().getColumn(8).setCellRenderer(right);
        tb.getColumnModel().getColumn(9).setCellRenderer(right);
        tb.getColumnModel().getColumn(10).setCellRenderer(right);

        // Botón quitar
        new ButtonColumn(tb, new AbstractAction("Quitar") {
            @Override public void actionPerformed(ActionEvent e) {
                int row = Integer.parseInt(e.getActionCommand());
                int mr = tb.convertRowIndexToModel(row);
                model.removeRow(mr);
                recalcularTotales();
            }
        }, 11);

        // Recalcular cuando cambie el %Desc
        model.addTableModelListener(evt -> {
            if (evt.getType() != javax.swing.event.TableModelEvent.UPDATE) return;
            if (updatingTable) return;
            int col = evt.getColumn();
            int row = evt.getFirstRow();
            if (col == 8 && row >= 0) {
                updatingTable = true;
                try {
                    double precio = parseMoney(model.getValueAt(row, 7));
                    double pdesc  = clamp0a100(parseMoney(model.getValueAt(row, 8)));
                    model.setValueAt(String.format("%.2f", pdesc), row, 8);
                    double monto = precio * (pdesc / 100.0);
                    double sub   = precio - monto;
                    model.setValueAt(String.format("%.2f", monto), row, 9);
                    model.setValueAt(String.format("%.2f", sub),   row, 10);
                } finally {
                    updatingTable = false;
                }
                recalcularTotales();
            }
        });

        add(new JScrollPane(tb), BorderLayout.CENTER);

        // ====== Totales y pagos
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(6,6,6,6);
        d.fill = GridBagConstraints.HORIZONTAL;
        d.weightx = 1;

        int r=0;
        txtSubtotal = new JTextField(); txtSubtotal.setEditable(false);
        txtTotal = new JTextField(); txtTotal.setEditable(false);
        addCell(bottom,d,2,r,new JLabel("Subtotal:"),1,false);
        addCell(bottom,d,3,r,txtSubtotal,1,true); r++;
        addCell(bottom,d,2,r,new JLabel("Total:"),1,false);
        addCell(bottom,d,3,r,txtTotal,1,true); r++;

        txtTC = moneyField(); txtTD = moneyField(); txtAMX = moneyField();
        txtTRF = moneyField(); txtDEP = moneyField(); txtEFE = moneyField();

        addCell(bottom,d,0,r,new JLabel("Tarjeta crédito:"),1,false);
        addCell(bottom,d,1,r,txtTC,1,true);
        addCell(bottom,d,2,r,new JLabel("Tarjeta débito:"),1,false);
        addCell(bottom,d,3,r,txtTD,1,true); r++;

        addCell(bottom,d,0,r,new JLabel("American Express:"),1,false);
        addCell(bottom,d,1,r,txtAMX,1,true);
        addCell(bottom,d,2,r,new JLabel("Transferencia:"),1,false);
        addCell(bottom,d,3,r,txtTRF,1,true); r++;

        addCell(bottom,d,0,r,new JLabel("Depósito:"),1,false);
        addCell(bottom,d,1,r,txtDEP,1,true);
        addCell(bottom,d,2,r,new JLabel("Efectivo:"),1,false);
        addCell(bottom,d,3,r,txtEFE,1,true); r++;
        cbFolioDV = new JComboBox<>();
        txtMontoDV = moneyField();

        addCell(bottom,d,0,r,new JLabel("Folio devolución:"),1,false);
        addCell(bottom,d,1,r,cbFolioDV,1,true);
        addCell(bottom,d,2,r,new JLabel("Monto devolución:"),1,false);
        addCell(bottom,d,3,r,txtMontoDV,1,true);
r++;

// Evento para recalcular totales al cambiar devolución
txtMontoDV.getDocument().addDocumentListener((SimpleDocListener) () -> {
    montoDVAplicado = parseMoney(txtMontoDV.getText());
    validarSumaPagos();
});



        JButton btGuardar = new JButton("Registrar venta");
        btGuardar.addActionListener(_e -> guardarVenta());

        btnCondiciones = new JButton("Condiciones…");
        btnCondiciones.addActionListener(_e -> editarMemoPrevia(false));

        btnObservaciones = new JButton("Observaciones…");
        btnObservaciones.addActionListener(_e -> editarObservaciones());
        addCell(bottom, d, 1, r, btnObservaciones, 1, false);


        // Coloca el botón a la izquierda del Guardar (ajusta columnas si quieres)
        addCell(bottom,d,2,r,btnCondiciones,1,false);
        addCell(bottom,d,3,r,btGuardar,1,false);

        add(bottom, BorderLayout.SOUTH);
    }

    private void cargarClienteAutoHooks() {
        txtTelefono.addActionListener(_e -> cargarCliente());
        ((AbstractDocument) txtTelefono.getDocument()).addDocumentListener((SimpleDocListener) () -> {
            String t = txtTelefono.getText().trim();
            if (t.length() >= 7) {
                if (lastTelefonoConsultado == null || !lastTelefonoConsultado.equals(t)) {
                    cargarCliente();
                }
            } else {
                limpiarInfoCliente();
            }
        });
        txtTelefono.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { cargarCliente(); }
        });
    }

    private void seleccionarObsequios() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        DialogSeleccionObsequios dlg;
        if (owner instanceof Frame f) dlg = new DialogSeleccionObsequios(f, obsequiosSel);
        else                          dlg = new DialogSeleccionObsequios((Frame) null, obsequiosSel);
        dlg.setVisible(true);
        List<String> nuevos = dlg.getSeleccionados();
        if (nuevos != null) {
            obsequiosSel = nuevos;
            lblObsequios.setText(obsequiosSel.size() + " obsequios seleccionados");
        }
    }

    /* ========= Memo (editor, template, render, printable, combinador) ========= */



private Map<String,String> construirCtxMemoPreliminar() {
    Map<String,String> ctx = new HashMap<>();
    EmpresaInfo emp = cargarEmpresaInfo();
    ctx.put("RAZON_SOCIAL", emp.razonSocial == null ? "" : emp.razonSocial);

    ctx.put("CLIENTE_NOMBRE", n(txtNombreCompleto.getText()));
    ctx.put("CLIENTE_TEL", n(txtTelefono.getText()));

    ctx.put("FOLIO", "");            // aún no lo tenemos
    ctx.put("NUMERO_NOTA", "");      // aún no lo tenemos
    ctx.put("FECHA", LocalDate.now().format(MX));

    // Fechas visibles para el cliente
    ctx.put("FECHA_ENTREGA", n(txtFechaEntrega.getText()));
    ctx.put("FECHA_LIMITE_AJUSTES", ""); // opcional

    // Detalle del vestido principal (si hay)
    String articulo = "", talla = "", color = "";
    if (model.getRowCount() > 0) {
        articulo = String.valueOf(model.getValueAt(0,1));
        talla    = String.valueOf(model.getValueAt(0,4));
        color    = String.valueOf(model.getValueAt(0,5));
    }
    ctx.put("ARTICULO", articulo);
    ctx.put("TALLA", talla);
    ctx.put("COLOR", color);

    ctx.put("WHATSAPP", emp.whatsapp == null ? "" : emp.whatsapp);


    return ctx;
}
private String obtenerCondicionesPredeterminadas() {
    try (Connection cn = Conexion.Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement("SELECT texto FROM empresa_condiciones WHERE id=1");
         ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getString("texto");
    } catch (SQLException ex) {
        System.err.println("Error leyendo condiciones predeterminadas: " + ex.getMessage());
    }
    return ""; // fallback vacío si no hay registro
}


/** Vista previa/edición antes de imprimir. */
private void editarMemoPrevia(boolean forzarTemplate) {
    Map<String,String> vars = construirVarsDesdeUI();
    String base = (memoEditable == null || memoEditable.isBlank() || forzarTemplate)
            ? obtenerCondicionesPredeterminadas()
            : memoEditable;
    String borrador = renderMemo(base, vars);

    JTextArea area = new JTextArea(borrador, 18, 60);
    area.setLineWrap(true); area.setWrapStyleWord(true);

    int r = JOptionPane.showConfirmDialog(this, new JScrollPane(area),
            "Condiciones de entrega / aceptación",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (r == JOptionPane.OK_OPTION) {
        memoEditable = area.getText().trim();
        if (memoEditable.isEmpty()) memoEditable = null;
    }
}

/** Render sencillo de tokens. Soporta {token} y {{TOKEN}} (insensible a mayúsculas). */
private static String renderMemo(String tpl, Map<String,String> vars) {
    if (tpl == null) return "";
    String out = tpl;
    for (Map.Entry<String,String> e : vars.entrySet()) {
        String key = java.util.regex.Pattern.quote(e.getKey());
        String val = e.getValue() == null ? "" : e.getValue();
        out = out.replaceAll("(?i)\\{\\{\\s*" + key + "\\s*\\}\\}", java.util.regex.Matcher.quoteReplacement(val));
        out = out.replaceAll("(?i)\\{\\s*"  + key + "\\s*\\}",     java.util.regex.Matcher.quoteReplacement(val));
    }
    // Cualquier token remanente lo cambiamos por "—"
    out = out.replaceAll("\\{\\{[^}]+\\}\\}", "—");
    out = out.replaceAll("\\{[^}]+\\}", "—");
    return out;
}


/** Variables para vista previa desde la UI (antes de crear la nota). */
private Map<String,String> construirVarsDesdeUI() {
    Map<String,String> v = new LinkedHashMap<>();
    EmpresaInfo emp = cargarEmpresaInfo();

    // Cliente
    v.put("cliente_nombre", n(txtNombreCompleto.getText()));
    v.put("fecha_compra", LocalDate.now().format(MX));
    LocalDate fe = fechaPreferida();
    v.put("fecha_evento", fe == null ? "" : fe.format(MX));

    // Artículo principal del carrito (si hay)
    String modelo = "", marca = "", color = "", talla = "", codigo = "", precio = "", pdesc = "", pagar = "";
    if (model.getRowCount() > 0) {
        Object cod = model.getValueAt(0,0);
        if (cod != null) codigo = String.valueOf(cod);
        marca  = n(String.valueOf(model.getValueAt(0,2)));
        modelo = n(String.valueOf(model.getValueAt(0,3)));
        talla  = n(String.valueOf(model.getValueAt(0,4)));
        color  = n(String.valueOf(model.getValueAt(0,5)));
        precio = String.valueOf(model.getValueAt(0,7));
        pdesc  = String.valueOf(model.getValueAt(0,8));
        pagar  = String.valueOf(model.getValueAt(0,10));
    }
    v.put("modelo", modelo); v.put("marca", marca); v.put("color", color); v.put("talla", talla);
    v.put("codigo", codigo); v.put("precio", precio); v.put("descuento_pct", pdesc); v.put("precio_pagar", pagar);

    // Fechas y asesor
    v.put("fecha_en_tienda", n(txtFechaEntrega.getText()));
    Modelo.Asesor a = (Modelo.Asesor) cbAsesor.getSelectedItem();
    v.put("asesora", (a==null) ? "" : a.getNombreCompleto());

    // Medidas del cliente (si existen columnas)
    String busto="", cintura="", cadera="";
    try {
        clienteDAO cdao = new clienteDAO();
        java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(txtTelefono.getText().trim());
        if (raw != null) {
            for (Map.Entry<String,String> e : raw.entrySet()) {
                String k = (e.getKey()==null?"":e.getKey().toLowerCase());
                if (k.equals("busto"))   busto   = e.getValue();
                if (k.equals("cintura")) cintura = e.getValue();
                if (k.equals("cadera"))  cadera  = e.getValue();
            }
        }
    } catch (Exception ignore){}
    v.put("busto", busto); v.put("cintura", cintura); v.put("cadera", cadera);

    // Empresa
    v.put("whatsapp", n(emp.whatsapp));
    return v;
}

/** Variables definitivas para impresión después de guardar la venta. */
private Map<String,String> buildMemoVars(EmpresaInfo emp, Nota n, java.util.List<NotaDetalle> dets,
                                         LocalDate fechaEntregaMostrar, LocalDate fechaEventoMostrar,
                                         String asesorNombre) {
    Map<String,String> v = new LinkedHashMap<>();

    // Cliente
    String cliNombre = n(txtNombreCompleto.getText());
    try {
        clienteDAO cdao = new clienteDAO();
        ClienteResumen cr = cdao.buscarResumenPorTelefono(n.getTelefono());
        if (cr != null && cr.getNombreCompleto()!=null && !cr.getNombreCompleto().isBlank())
            cliNombre = cr.getNombreCompleto();
        // Medidas
        String busto="", cintura="", cadera="";
        java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(n.getTelefono());
        if (raw != null) {
            for (Map.Entry<String,String> e : raw.entrySet()) {
                String k = (e.getKey()==null?"":e.getKey().toLowerCase());
                if (k.equals("busto"))   busto   = e.getValue();
                if (k.equals("cintura")) cintura = e.getValue();
                if (k.equals("cadera"))  cadera  = e.getValue();
            }
        }
        v.put("busto", busto); v.put("cintura", cintura); v.put("cadera", cadera);
    } catch (Exception ignore) {}

    v.put("cliente_nombre", cliNombre);
    v.put("fecha_compra", LocalDate.now().format(MX));
    v.put("fecha_evento", fechaEventoMostrar==null? "" : fechaEventoMostrar.format(MX));
    v.put("fecha_en_tienda", fechaEntregaMostrar==null? "" : fechaEntregaMostrar.format(MX));

    // Artículo principal
    NotaDetalle d0 = null;
    for (NotaDetalle d : dets) { if (d.getCodigoArticulo() > 0) { d0 = d; break; } }
    if (d0 == null && !dets.isEmpty()) d0 = dets.get(0);
    v.put("modelo",  d0==null? "": n(d0.getModelo()));
    v.put("marca",   d0==null? "": n(d0.getMarca()));
    v.put("color",   d0==null? "": n(d0.getColor()));
    v.put("talla",   d0==null? "": n(d0.getTalla()));
    v.put("codigo", (d0!=null && d0.getCodigoArticulo()>0) ? String.valueOf(d0.getCodigoArticulo()) : "");
    v.put("precio", String.format("%.2f", d0==null?0d:(d0.getPrecio()==null?0d:d0.getPrecio())));
    v.put("descuento_pct", String.format("%.2f", d0==null?0d:(d0.getDescuento()==null?0d:d0.getDescuento())));
    v.put("precio_pagar", String.format("%.2f", d0==null?0d:(d0.getSubtotal()==null?0d:d0.getSubtotal())));

    // Asesor y empresa
    v.put("asesora", n(asesorNombre));
    v.put("whatsapp", n(emp.whatsapp));

    return v;
}

// Segunda página con las condiciones (formato de formulario + cuerpo del memo)
private Printable construirPrintableCondiciones(
        EmpresaInfo emp,
        Nota n,
        java.util.List<NotaDetalle> dets,
        String memoCuerpoRenderizado,
        String folio,
        String asesorNombre,
        LocalDate fechaEvento,
        LocalDate fechaEnTienda,
        String clienteNombre) {

    final String tel1     = n.getTelefono()==null ? "" : n.getTelefono();

    // Primer artículo "real"
    NotaDetalle d0 = null;
    for (NotaDetalle d : dets) { if (d.getCodigoArticulo() > 0) { d0 = d; break; } }
    if (d0 == null && !dets.isEmpty()) d0 = dets.get(0);


    // Medidas del cliente (efectivamente final)
    String _b="", _c1="", _c2="";
    try {
        clienteDAO cdao = new clienteDAO();
        java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
        if (raw != null) {
            for (java.util.Map.Entry<String,String> e : raw.entrySet()) {
                String k = e.getKey()==null ? "" : e.getKey().toLowerCase();
                String v = e.getValue()==null ? "" : e.getValue();
                if (k.equals("busto"))   _b  = v;
                if (k.equals("cintura")) _c1 = v;
                if (k.equals("cadera"))  _c2 = v;
            }
        }
    } catch (Exception ignore) { }

    return (g, pf, pageIndex) -> {
    if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
    Graphics2D g2 = (Graphics2D) g;

    final int M = 50; // márgenes más amplios
    int x = (int) pf.getImageableX() + M;
    int y = (int) pf.getImageableY() + M;
    int w = (int) pf.getImageableWidth() - (M * 2);

    // Títulos y estilos
    Font fTitle = g2.getFont().deriveFont(Font.BOLD, 13f);
    Font fBody = g2.getFont().deriveFont(11f);
    Font fSmall = g2.getFont().deriveFont(10f);
    g2.setFont(fTitle);

    // Cuerpo del memo con interlineado mejorado
    g2.setFont(fSmall);
    y = drawWrappedSimple(g2, memoCuerpoRenderizado == null ? "" : memoCuerpoRenderizado, x, y, w);
    y += 20;

    // Separador
    g2.drawLine(x, y, x + w, y);
    y += 25;


    return Printable.PAGE_EXISTS;
};
}



// Helpers del printable de condiciones
private static void centerSimple(Graphics2D g2, String s, int x, int w, int y){
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int cx = x + (w - fm.stringWidth(s)) / 2;
    g2.drawString(s, cx, y);
}
private static int drawWrappedSimple(Graphics2D g2, String text, int x, int y, int maxWidth){
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
/** Dibuja: LABEL ________ y escribe valor si viene. Devuelve el nuevo y. */
private static int drawFieldLine(Graphics2D g2, int x, int y, int w, String label, String value) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int yy = y + fm.getAscent();
    int lw = fm.stringWidth(label) + 6;
    g2.drawString(label, x, yy);
    g2.drawLine(x + lw, yy + 3, x + w, yy + 3);
    if (value != null && !value.isBlank()) g2.drawString(value, x + lw + 3, yy);
    return y + 18;
}

/** Dibuja dos campos en una fila (izquierda y derecha). Devuelve el nuevo y. */
private static int drawTwoColsLine(Graphics2D g2, int x, int y, int w,
                                   String leftLabel, String leftVal,
                                   String rightLabel, String rightVal) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int half = (w - 20) / 2;
    int yy = y + fm.getAscent();

    int lw = fm.stringWidth(leftLabel) + 6;
    g2.drawString(leftLabel, x, yy);
    g2.drawLine(x + lw, yy + 3, x + half, yy + 3);
    if (leftVal != null && !leftVal.isBlank()) g2.drawString(leftVal, x + lw + 3, yy);

    int xr = x + half + 20;
    int rw = fm.stringWidth(rightLabel) + 6;
    g2.drawString(rightLabel, xr, yy);
    g2.drawLine(xr + rw, yy + 3, x + w, yy + 3);
    if (rightVal != null && !rightVal.isBlank()) g2.drawString(rightVal, xr + rw + 3, yy);

    return y + 18;
}

// Empaqueta "ticket + condiciones" en un solo Printable
private static Printable combinarEnDosPaginas(Printable p0, Printable p1){
    return (g, pf, pageIndex) -> {
        if (pageIndex == 0) return p0.print(g, pf, 0);
        if (pageIndex == 1) return p1.print(g, pf, 0);
        return Printable.NO_SUCH_PAGE;
    };
}



    // ======= Carga de cliente/nota =======
    private void cargarCliente() {
        String tel = txtTelefono.getText().trim();
        if (tel.isEmpty()) { limpiarInfoCliente(); return; }
        if (tel.equals(lastTelefonoConsultado)) return;

        try {
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                limpiarInfoCliente();
                btnRegistrarCliente.setVisible(true);
                lastTelefonoConsultado = tel;
                return;
            }
            txtTelefono2.setText(n(cr.getTelefono2()));
            txtNombreCompleto.setText(n(cr.getNombreCompleto()));
            txtFechaEvento.setText(fmt(cr.getFechaEvento()));
            txtFechaPrueba1.setText(fmt(cr.getFechaPrueba1()));
            txtFechaPrueba2.setText(fmt(cr.getFechaPrueba2()));
            txtFechaEntrega.setText(fmt(cr.getFechaEntrega()));

            NotasDAO ndao = new NotasDAO();
            Integer ult = ndao.obtenerUltimaNotaPorTelefono(tel);
            txtUltimaNota.setText(ult == null ? "" : String.valueOf(ult));

            // === Cargar folios de devolución del cliente ===
try (Connection cn = Conexion.Conecta.getConnection();
     PreparedStatement ps = cn.prepareStatement("""
        SELECT 
            n.folio,
            n.total AS total_original,
            COALESCE(n.total - SUM(fp.devolucion), n.total) AS saldo_disponible,
            MAX(n.fecha_registro) AS fecha_reciente
        FROM Notas n
        JOIN Devoluciones d ON d.numero_nota_dv = n.numero_nota
        LEFT JOIN Formas_Pago fp
            ON fp.referencia_dv = n.folio
            AND fp.status = 'A'
            AND fp.devolucion IS NOT NULL
        WHERE n.tipo = 'DV'
          AND n.status = 'A'
          AND n.telefono = ?
        GROUP BY n.folio, n.total
        HAVING COALESCE(n.total - SUM(fp.devolucion), n.total) > 0
        ORDER BY fecha_reciente DESC;
     """)) {
    ps.setString(1, tel);
    try (ResultSet rs = ps.executeQuery()) {
        cbFolioDV.removeAllItems();

        // Agregar item inicial "placeholder" sin valor real
        cbFolioDV.addItem("--- Pagar con folio de devolución ---");

        // Luego agregar los folios disponibles del cliente
        while (rs.next()) {
            String folio = rs.getString("folio");
            double saldo = rs.getDouble("saldo_disponible");
            cbFolioDV.addItem(folio + " - $" + String.format("%.2f", saldo));
        }

        // Seleccionar por defecto el mensaje inicial
        cbFolioDV.setSelectedIndex(0);

    }
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this,
        "Error al cargar devoluciones: " + ex.getMessage(),
        "Error", JOptionPane.ERROR_MESSAGE);
}



            btnRegistrarCliente.setVisible(false);
            lastTelefonoConsultado = tel;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarInfoCliente() {
        txtTelefono2.setText("");
        txtNombreCompleto.setText("— no registrado —");
        txtFechaEvento.setText("");
        txtFechaPrueba1.setText("");
        txtFechaPrueba2.setText("");
        txtFechaEntrega.setText("");
        txtUltimaNota.setText("");
        btnRegistrarCliente.setVisible(false);
        lastTelefonoConsultado = null;
    }

private void aplicarDevolucion() {
    Object item = cbFolioDV.getSelectedItem();

    // Validar selección
    if (item == null) {
        JOptionPane.showMessageDialog(this,
                "Selecciona un folio de devolución válido.",
                "Atención", JOptionPane.WARNING_MESSAGE);
        return;
    }

    String sel = item.toString().trim();

    // Ignorar si es el placeholder o está vacío
    if (sel.isEmpty() || sel.startsWith("---")) {
        JOptionPane.showMessageDialog(this,
                "Selecciona un folio de devolución real, no el texto por defecto.",
                "Atención", JOptionPane.WARNING_MESSAGE);
        return;
    }

    // Validar monto ingresado
    double montoIngresado = parseMoney(txtMontoDV.getText());
    if (montoIngresado <= 0) {
        JOptionPane.showMessageDialog(this, "Captura un monto válido.");
        return;
    }

    // Intentar extraer el monto disponible
    double montoDisponible;
    try {
        if (!sel.contains("$")) {
            JOptionPane.showMessageDialog(this,
                    "El folio seleccionado no contiene un monto disponible.\nTexto: " + sel,
                    "Error de formato", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Ejemplo: "DV0007 - $26,300.00"
        String montoStr = sel.substring(sel.indexOf('$') + 1)
                .replace(",", "")
                .trim();
        montoDisponible = Double.parseDouble(montoStr);
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "No se pudo determinar el monto disponible del folio.\nTexto recibido: " + sel,
                "Error en folio", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Validar límite
    if (montoIngresado > montoDisponible + 0.001) {
        JOptionPane.showMessageDialog(this,
                "El monto ingresado ($" + String.format("%.2f", montoIngresado) +
                ") excede el saldo disponible del folio ($" + String.format("%.2f", montoDisponible) + ").",
                "Monto excedido", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Aplicar visualmente al total
    double totalActual = parseMoney(txtTotal.getText());
    txtTotal.setText(String.format("%.2f", Math.max(0, totalActual - montoIngresado)));
    montoDVAplicado = montoIngresado;

    JOptionPane.showMessageDialog(this,
            "Devolución aplicada correctamente: $" + String.format("%.2f", montoDVAplicado),
            "Éxito", JOptionPane.INFORMATION_MESSAGE);

    validarSumaPagos();
}



    private void abrirFormularioCliente() {
        String tel = txtTelefono.getText().trim();
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingresa primero el teléfono del cliente.", "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (nav != null) {
            nav.prefillClienteTelefono(tel);
            nav.show("CARD_CLIENTES");
        } else {
            JFrame f = new JFrame("Registro de Clientes");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(760, 620);
            f.setLocationRelativeTo(this);
            f.setLayout(new BorderLayout());
            f.add(new ClientesPanel(tel), BorderLayout.CENTER);
            f.setVisible(true);
        }
    }

    private String fmt(LocalDate d) { return d == null ? "" : d.format(MX); }
    private LocalDate parseFecha(String s){
        if (s==null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return LocalDate.parse(s, MX); }
        catch (DateTimeParseException e){ return null; }
    }
    private LocalDate fechaPreferida(){
        if (chkUsarFechaCliente.isSelected()) {
            return parseFecha(txtFechaEvento.getText());
        } else {
            return parseFecha(txtFechaEventoVenta.getText());
        }
    }
    private JTextField readOnlyField() {
        JTextField t = new JTextField();
        t.setEditable(false);
        t.setOpaque(true);
        Color ro = UIManager.getColor("TextField.inactiveBackground");
        if (ro == null) ro = new Color(235,235,235);
        t.setBackground(ro);
        return t;
    }

    private void addCell(JPanel p, GridBagConstraints c, int x, int y, JComponent comp, int span, boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }

    private JTextField moneyField(){
        JTextField t = new JTextField();
        ((AbstractDocument) t.getDocument()).setDocumentFilter(onlyDecimal());
        t.getDocument().addDocumentListener((SimpleDocListener) this::validarSumaPagos);
        return t;
    }

    private DefaultCellEditor decimalEditor() {
        JTextField tf = new JTextField();
        ((AbstractDocument) tf.getDocument()).setDocumentFilter(onlyDecimal());
        return new DefaultCellEditor(tf);
    }

    private void cargarAsesores() {
        try {
            cbAsesor.removeAllItems();
            AsesorDAO ad = new AsesorDAO();
            for (Modelo.Asesor a : ad.listarActivosDetalle()) {
                cbAsesor.addItem(a);
            }
            cbAsesor.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Modelo.Asesor) {
                        setText(((Modelo.Asesor) value).getNombreCompleto());
                    } else if (value == null) {
                        setText("Selecciona asesor");
                    }
                    return this;
                }
            });
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar asesores: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void agregarArticulo() {
        try {
            int cod = Integer.parseInt(txtCod.getText().trim());
            InventarioDAO dao = new InventarioDAO();
            Inventario i = dao.buscarParaVenta(cod);
            if (i == null) {
                JOptionPane.showMessageDialog(this,"No existe el artículo o está inactivo");
                return;
            }
            try {
                String st = i.getStatus();
                if (st != null && !"A".equalsIgnoreCase(st.trim())) {
                    JOptionPane.showMessageDialog(this,
                        "No se puede vender: artículo inactivo (status=" + st + ")");
                    return;
                }
            } catch (Throwable ignore) {}

            if (i.getExistencia() != null && i.getExistencia() < 1) {
                JOptionPane.showMessageDialog(this,"Sin existencia.");
                return;
            }
            agregarArticuloDesdeInventario(i);
            txtCod.setText("");
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this,"Revisa el código del artículo");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,"Error BD: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seleccionarArticulo() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        DialogSeleccionArticulo dlg =
            (owner instanceof Frame f) ? new DialogSeleccionArticulo(f)
                                       : new DialogSeleccionArticulo((Frame) null);

        dlg.setVisible(true);
        Inventario sel = dlg.getSeleccionado();
        if (sel == null) return;

        try {
            InventarioDAO dao = new InventarioDAO();
            Inventario real = dao.buscarPorCodigo(sel.getCodigoArticulo());
            if (real == null) {
                JOptionPane.showMessageDialog(this,
                    "No se puede vender: artículo inactivo o sin existencia.");
                return;
            }
            agregarArticuloDesdeInventario(real);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Error consultando inventario: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void agregarArticuloDesdeInventario(Inventario i) {
        double precio = i.getPrecio()==null?0:i.getPrecio();
        double pdesc  = i.getDescuento()==null?0:i.getDescuento();
        pdesc = clamp0a100(pdesc);
        double monto  = precio * (pdesc/100.0);
        double sub    = precio - monto;

        String fArt = fmt(fechaPreferida());

        model.addRow(new Object[]{
                i.getCodigoArticulo(),
                i.getArticulo(),
                n(i.getMarca()),
                n(i.getModelo()),
                n(i.getTalla()),
                n(i.getColor()),
                fArt,
                String.format("%.2f", precio),
                String.format("%.2f", pdesc),
                String.format("%.2f", monto),
                String.format("%.2f", sub),
                "Quitar"
        });
        recalcularTotales();
    }

    // === Diálogo para "PEDIR"
    private void abrirDialogoPedido() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Registrar artículo (PEDIR)", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;
        JTextField tArticulo = new JTextField();
        JTextField tMarca = new JTextField();
        JTextField tModelo = new JTextField();
        JTextField tTalla = new JTextField();
        JTextField tColor = new JTextField();
        JTextField tPrecio = new JTextField();
        ((AbstractDocument)tPrecio.getDocument()).setDocumentFilter(onlyDecimal());
        JTextField tDesc = new JTextField("0");
        ((AbstractDocument)tDesc.getDocument()).setDocumentFilter(onlyDecimal());

        addCell(p,c,0,y,new JLabel("Artículo*:"),1,false); addCell(p,c,1,y,tArticulo,1,true); y++;
        addCell(p,c,0,y,new JLabel("Marca:"),1,false);     addCell(p,c,1,y,tMarca,1,true);     y++;
        addCell(p,c,0,y,new JLabel("Modelo:"),1,false);    addCell(p,c,1,y,tModelo,1,true);    y++;
        addCell(p,c,0,y,new JLabel("Talla:"),1,false);     addCell(p,c,1,y,tTalla,1,true);     y++;
        addCell(p,c,0,y,new JLabel("Color:"),1,false);     addCell(p,c,1,y,tColor,1,true);     y++;
        addCell(p,c,0,y,new JLabel("Precio*:"),1,false);   addCell(p,c,1,y,tPrecio,1,true);    y++;
        addCell(p,c,0,y,new JLabel("% Desc:"),1,false);    addCell(p,c,1,y,tDesc,1,true);      y++;

        JPanel south = new JPanel();
        JButton bOk = new JButton("Agregar al carrito");
        JButton bCancel = new JButton("Cancelar");
        south.add(bOk); south.add(bCancel);

        dlg.getContentPane().add(p, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        dlg.setSize(420, 360);
        dlg.setLocationRelativeTo(this);

        bCancel.addActionListener(_e -> dlg.dispose());
        bOk.addActionListener(_e -> {
            String art = tArticulo.getText().trim();
            double precio = parseMoney(tPrecio.getText());
            double desc = parseMoney(tDesc.getText());
            if (art.isEmpty()) { JOptionPane.showMessageDialog(dlg,"Captura el nombre del artículo."); return; }
            if (precio <= 0)   { JOptionPane.showMessageDialog(dlg,"Captura un precio válido (>0)."); return; }
            desc = clamp0a100(desc);

            double monto  = precio * (desc/100.0);
            double sub    = precio - monto;
            String fArt = fmt(fechaPreferida());

            model.addRow(new Object[]{
                    0,
                    "PEDIDO – " + art,
                    tMarca.getText().trim(),
                    tModelo.getText().trim(),
                    tTalla.getText().trim(),
                    tColor.getText().trim(),
                    fArt,
                    String.format("%.2f", precio),
                    String.format("%.2f", desc),
                    String.format("%.2f", monto),
                    String.format("%.2f", sub),
                    "Quitar"
            });
            recalcularTotales();
            dlg.dispose();
        });

        dlg.setVisible(true);
    }

    private void recalcularTotales() {
        double tot = 0;
        for (int r=0; r<model.getRowCount(); r++){
            tot += parseMoney(model.getValueAt(r,10));
        }
        txtSubtotal.setText(String.format("%.2f", tot));
        txtTotal.setText(String.format("%.2f", tot));
        validarSumaPagos();
    }

    private void validarSumaPagos(){
        double total = parseMoney(txtTotal.getText());
        double suma  = parseMoney(txtTC.getText()) + parseMoney(txtTD.getText())
             + parseMoney(txtAMX.getText()) + parseMoney(txtTRF.getText())
             + parseMoney(txtDEP.getText()) + parseMoney(txtEFE.getText())
             + montoDVAplicado;

        txtTotal.setToolTipText(String.format("Pagado: %.2f  | Diferencia: %.2f",
                                              suma, (total - suma)));
    }

    private boolean validarClienteObligatorio() {
        String tel = txtTelefono.getText().trim();
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Captura el teléfono del cliente y regístralo antes de continuar.");
            txtTelefono.requestFocus();
            return false;
        }
        try {
            clienteDAO cdao = new clienteDAO();
            Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                JOptionPane.showMessageDialog(this,
                    "El teléfono no está registrado. Presiona \"Registrar cliente\" y guarda sus datos.");
                btnRegistrarCliente.setVisible(true);
                btnRegistrarCliente.requestFocus();
                return false;
            }
            return true;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error validando cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void guardarVenta() {
        if (tb.isEditing()) tb.getCellEditor().stopCellEditing();
        if (!validarClienteObligatorio()) return;

        if (cbAsesor.getSelectedItem()==null){
            JOptionPane.showMessageDialog(this,"Selecciona un asesor"); return;
        }
        if (model.getRowCount()==0){
            JOptionPane.showMessageDialog(this,"Agrega al menos un artículo"); return;
        }
        double total = parseMoney(txtTotal.getText());
        double suma = parseMoney(txtTC.getText()) + parseMoney(txtTD.getText()) + parseMoney(txtAMX.getText())
                + parseMoney(txtTRF.getText()) + parseMoney(txtDEP.getText()) + parseMoney(txtEFE.getText())
                + montoDVAplicado;
        if (Math.abs(total - suma) > 0.005) {
            JOptionPane.showMessageDialog(this,
                "La suma de pagos (" + String.format("%.2f", suma) + ") debe ser igual al total (" + String.format("%.2f", total) + ")");
            return;
        }


        // Fechas por defecto (para renglones y para entrega)
        LocalDate fechaEventoDefault = fechaPreferida();
        LocalDate fechaEntregaDefault;
        if (chkUsarFechaCliente.isSelected()) {
            fechaEntregaDefault = parseFecha(txtFechaEntrega.getText());
        } else {
            LocalDate f = parseFecha(txtFechaEntregaVenta.getText());
            fechaEntregaDefault = (f != null) ? f : parseFecha(txtFechaEntrega.getText());
        }

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,"¿Registrar la venta de contado?","Confirmación",
                JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,ops,ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            // === Validar que el monto de devolución no exceda el disponible ===
Object selItem = cbFolioDV.getSelectedItem();
if (selItem != null) {
    String sel = selItem.toString().trim();

    // Ignorar si el usuario no eligió un folio real
    if (!sel.isEmpty() && !sel.startsWith("---")) {
        double montoIngresado = parseMoney(txtMontoDV.getText());
        if (montoIngresado <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Captura un monto válido para la devolución.",
                    "Monto inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double montoDisponible;
        try {
            // Asegura que el texto contenga un símbolo de $
            if (!sel.contains("$")) {
                JOptionPane.showMessageDialog(this,
                        "El folio seleccionado no contiene monto disponible.\nTexto: " + sel,
                        "Error de formato", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String montoStr = sel.substring(sel.indexOf('$') + 1)
                    .replace(",", "")
                    .trim();
            montoDisponible = Double.parseDouble(montoStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo determinar el monto disponible del folio de devolución (" + sel + ").",
                    "Error en folio", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (montoIngresado > montoDisponible + 0.001) {
            JOptionPane.showMessageDialog(this,
                    "El monto de devolución ($" + String.format("%.2f", montoIngresado) +
                    ") excede el saldo disponible del folio (" + String.format("%.2f", montoDisponible) + ").\n" +
                    "No se puede registrar la venta.",
                    "Error de validación", JOptionPane.ERROR_MESSAGE);
            return;
        }
    }
}


            if (observacionesTexto == null) {
            editarObservaciones(); // fuerza al usuario a capturar o confirmar que no hay
            }


            Nota n = new Nota();
            String tel = txtTelefono.getText().trim();
            n.setTelefono(tel.isEmpty()?null:tel);
            Modelo.Asesor sel = (Modelo.Asesor) cbAsesor.getSelectedItem();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "Selecciona un asesor");
                return;
            }
            n.setAsesor(sel.getNumeroEmpleado());
            n.setTipo("CN");
            n.setTotal(total);
            n.setSaldo(0.0);
            n.setStatus("A");

            List<NotaDetalle> dets = new ArrayList<>();
            for (int i=0; i<model.getRowCount(); i++){
                int cod = 0;
                try { cod = Integer.parseInt(model.getValueAt(i,0).toString()); } catch(Exception ignore){}
                if (cod <= 0) continue; // pedidos no van al detalle
                NotaDetalle d = new NotaDetalle();
                d.setCodigoArticulo(cod);
                d.setArticulo(model.getValueAt(i,1).toString());
                d.setMarca(model.getValueAt(i,2).toString());
                d.setModelo(model.getValueAt(i,3).toString());
                d.setTalla(model.getValueAt(i,4).toString());
                d.setColor(model.getValueAt(i,5).toString());
                d.setPrecio(parseMoney(model.getValueAt(i,7)));
                d.setDescuento(parseMoney(model.getValueAt(i,8)));
                d.setDescuentoMonto(parseMoney(model.getValueAt(i,9)));
                d.setSubtotal(parseMoney(model.getValueAt(i,10)));
                LocalDate fRow = parseFecha(String.valueOf(model.getValueAt(i,6)));
                d.setFechaEvento(fRow != null ? fRow : fechaEventoDefault);
                dets.add(d);
            }

            PagoFormas p = new PagoFormas();
            p.setFechaOperacion(LocalDate.now());
            p.setTarjetaCredito(nullIfZero(parseMoney(txtTC.getText())));
            p.setTarjetaDebito(nullIfZero(parseMoney(txtTD.getText())));
            p.setAmericanExpress(nullIfZero(parseMoney(txtAMX.getText())));
            p.setTransferencia(nullIfZero(parseMoney(txtTRF.getText())));
            p.setDeposito(nullIfZero(parseMoney(txtDEP.getText())));
            p.setEfectivo(nullIfZero(parseMoney(txtEFE.getText())));
            p.setDevolucion(nullIfZero(montoDVAplicado));
            // --- Asignar referencia de devolución (folio limpio) ---
            if (cbFolioDV.getSelectedItem() == null || cbFolioDV.getSelectedItem().toString().trim().startsWith("---")) {
                p.setReferenciaDV(null);
            } else {
                // Extrae solo el folio antes del " - $" si existe
                String clean = cbFolioDV.getSelectedItem().toString().trim().split("\\s*-\\s*\\$")[0].trim();
                p.setReferenciaDV(clean);
            }


            p.setTipoOperacion("CN");
            p.setStatus("A");

            // ======= 1) GUARDAR =======
            VentaContadoService svc = new VentaContadoService();
            int numeroNota = svc.crearVentaContado(n, dets, p, fechaEventoDefault, fechaEntregaDefault);
            n.setNumeroNota(numeroNota); // <-- ¡asigna el número real antes de imprimir!


            // Folio (preferimos el que retorna el servicio al objeto Nota)
            String folioImpresion = (n.getFolio()==null || n.getFolio().isBlank()) ? "—" : n.getFolio();

            // ======= 1.1) Aplicar devolución usada (si la hubo) =======
if (p.getDevolucion() != null && p.getDevolucion() > 0 &&
    p.getReferenciaDV() != null && !p.getReferenciaDV().isBlank()) {
    try {
        // Extraer número de nota DV desde el folio, ejemplo "DV0007" → 7
        String folioRef = p.getReferenciaDV().trim();
        int numeroNotaDV = 0;
        try {
            numeroNotaDV = Integer.parseInt(folioRef.replaceAll("[^0-9]", ""));
        } catch (Exception exNum) {
            throw new SQLException("Folio de devolución inválido: " + folioRef);
        }

        // Crear registro y aplicar el monto en Devoluciones.monto_usado
        java.util.List<Modelo.PagoDV> lst = new java.util.ArrayList<>();
        Modelo.PagoDV pagoDV = new Modelo.PagoDV();
        pagoDV.numeroNotaDV = numeroNotaDV;
        pagoDV.monto = p.getDevolucion();
        lst.add(pagoDV);

        new Controlador.NotasDVDAO().aplicarAVenta(numeroNota, "CN", lst);
    } catch (SQLException exDV) {
        JOptionPane.showMessageDialog(this,
            "Advertencia: la venta se registró, pero no se pudo actualizar el folio de devolución:\n" +
            exDV.getMessage(),
            "Error de devolución", JOptionPane.WARNING_MESSAGE);
    }
}


            // ======= 2) EXTRAS =======
            if (!obsequiosSel.isEmpty()) {
                try {
                    Controlador.ObsequiosDAO odao = new Controlador.ObsequiosDAO();
                    String telObsequio = txtTelefono.getText().trim();
                    LocalDate fEventoCliente = parseFecha(txtFechaEvento.getText());
                    odao.insertarParaNota(
                            numeroNota,
                            telObsequio.isEmpty() ? null : telObsequio,
                            LocalDate.now(),
                            obsequiosSel,
                            "CN",
                            sel.getNumeroEmpleado(),
                            "A",
                            fEventoCliente
                    );
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Obsequios no guardados: " + ex.getMessage(),
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }
            guardarPedidosDeCarrito(numeroNota, fechaEventoDefault);

            // ======= 3) DETERMINAR FECHAS EFECTIVAS PARA MOSTRAR =======
            LocalDate eventoCliente  = parseFecha(txtFechaEvento.getText());
            LocalDate entregaCliente = parseFecha(txtFechaEntrega.getText());

            LocalDate eventoDetalle = null;
            for (NotaDetalle d : dets) {
                if (d.getFechaEvento() != null) { eventoDetalle = d.getFechaEvento(); break; }
            }
            LocalDate eventoMostrar  = (eventoDetalle != null) ? eventoDetalle : eventoCliente;
            LocalDate entregaMostrar = (fechaEntregaDefault != null) ? fechaEntregaDefault : entregaCliente;

            // ======= 4) IMPRIMIR CON FOLIO Y DATOS DEL CLIENTE =======
EmpresaInfo emp = cargarEmpresaInfo();

String clienteNombre = txtNombreCompleto.getText().trim();
String tel2 = txtTelefono2.getText().trim();

Printable prn = construirPrintableEmpresarial(
        emp, n, dets, p,
        entregaMostrar, eventoMostrar,
        sel.getNombreCompleto(), folioImpresion,
        clienteNombre, tel2);

// === 4.1) MEMO ===
// si el usuario no abrió el editor antes, muéstrale la plantilla una vez
if (memoEditable == null) editarMemoPrevia(true);

// Texto CRUDO a guardar (puede incluir {tokens})
String memoCrudoAGuardar = (memoEditable == null || memoEditable.isBlank())
        ? obtenerCondicionesPredeterminadas()
        : memoEditable;

// Guardar/actualizar en BD
try {
    new NotasMemoDAO().upsert(numeroNota, memoCrudoAGuardar);
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this,
        "No se pudo guardar el MEMO de condiciones: " + ex.getMessage(),
        "Aviso", JOptionPane.WARNING_MESSAGE);
}
try {
    if (observacionesTexto != null && !observacionesTexto.isBlank()) {
        new Controlador.NotasObservacionesDAO().upsert(numeroNota, observacionesTexto);
    }
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this,
        "No se pudieron guardar las observaciones: " + ex.getMessage(),
        "Aviso", JOptionPane.WARNING_MESSAGE);
}



// Render final con datos reales
Map<String,String> varsFinal = buildMemoVars(
        emp, n, dets, entregaMostrar, eventoMostrar, sel.getNombreCompleto());
String memoRender = renderMemo(memoCrudoAGuardar, varsFinal);

// Printable de condiciones y combinación a dos páginas
// NUEVA llamada: pasamos dets, asesor, fechas y nombre cliente
Printable condiciones = construirPrintableCondiciones(
        emp,
        n,
        dets,
        memoRender,
        folioImpresion,
        sel.getNombreCompleto(),
        eventoMostrar,
        entregaMostrar,         // la usaremos como "estará en tienda"
        clienteNombre
);
Printable combinado = combinarEnDosPaginas(prn, condiciones);

// === 4.2) IMPRIMIR (UNA sola vez)
imprimirYConfirmarAsync(
    combinado,
    () -> JOptionPane.showMessageDialog(this,
            "Venta registrada e impresa.\nNota No: " + numeroNota + "   Folio: " + folioImpresion),
    () -> JOptionPane.showMessageDialog(this,
            "Venta registrada pero la impresión no se confirmó.\n" +
            "Puedes reimprimir desde el módulo de notas (Folio: " + folioImpresion + ").",
            "Aviso", JOptionPane.WARNING_MESSAGE)
);
// Notificar a Corte de Caja que hubo una operación completada
try {
    Utilidades.EventBus.notificarOperacionFinalizada();
} catch (Exception ignore) {}




            // ======= 5) LIMPIAR UI =======
model.setRowCount(0);
txtSubtotal.setText("");
txtTotal.setText("");
txtTC.setText(""); 
txtTD.setText(""); 
txtAMX.setText("");
txtTRF.setText(""); 
txtDEP.setText(""); 
txtEFE.setText("");
txtMontoDV.setText(""); 
montoDVAplicado = 0;

// limpiar folios de devolución
cbFolioDV.removeAllItems();

// limpiar datos del cliente
txtTelefono.setText("");
txtTelefono2.setText("");
txtNombreCompleto.setText("— no registrado —");
txtFechaEvento.setText("");
txtFechaPrueba1.setText("");
txtFechaPrueba2.setText("");
txtFechaEntrega.setText("");
txtUltimaNota.setText("");

// reset bandera
lastTelefonoConsultado = null;

// limpiar obsequios
obsequiosSel.clear();
lblObsequios.setText("0 obsequios seleccionados");

// enfocar el campo teléfono para siguiente venta
txtTelefono.requestFocus();


        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,"Error al guardar: "+e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===== Utilidades =====
    private String n(String s){ return s==null?"":s; }
    private double parseMoney(Object o){
        if (o==null) return 0;
        String s = o.toString().trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch(Exception e){ return 0; }
    }
    private Double nullIfZero(double v){ return Math.abs(v) < 0.0001 ? null : v; }

    private DocumentFilter onlyDecimal(){ return new DialogArticulo.DecimalFilter(12); }

    // Botón en columna
    static class ButtonColumn extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, java.awt.event.ActionListener {
        private final JTable table;
        private final Action action;
        private final JButton renderButton = new JButton("Quitar");
        private final JButton editButton   = new JButton("Quitar");
        public ButtonColumn(JTable table, Action action, int column) {
            this.table = table;
            this.action = action;
            editButton.addActionListener(this);
            TableColumnModel m = table.getColumnModel();
            m.getColumn(column).setCellRenderer(this);
            m.getColumn(column).setCellEditor(this);
        }
        @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean isSelected,boolean hasFocus,int row,int col){ return renderButton; }
        @Override public Component getTableCellEditorComponent(JTable table,Object value,boolean isSelected,int row,int col){ return editButton; }
        @Override public Object getCellEditorValue(){ return "Quitar"; }
        @Override public void actionPerformed(ActionEvent e){
            int row = table.getEditingRow();
            fireEditingStopped();
            action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, String.valueOf(row)));
        }
    }

    @FunctionalInterface interface SimpleDocListener extends javax.swing.event.DocumentListener {
        void on();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e){ on(); }
    }

    // ======== PEDIDOS ========

    private List<PedidosDAO.PedidoDraft> extraerPedidosDelCarrito() {
        List<PedidosDAO.PedidoDraft> items = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            int cod = 0;
            try { cod = Integer.parseInt(String.valueOf(model.getValueAt(i, 0))); } catch (Exception ignore) {}
            if (cod == 0) {
                PedidosDAO.PedidoDraft d = new PedidosDAO.PedidoDraft();
                d.articulo  = limpiarPrefijoPedido(String.valueOf(model.getValueAt(i, 1)));
                d.marca     = n(String.valueOf(model.getValueAt(i, 2)));
                d.modelo    = n(String.valueOf(model.getValueAt(i, 3)));
                d.talla     = n(String.valueOf(model.getValueAt(i, 4)));
                d.color     = n(String.valueOf(model.getValueAt(i, 5)));
                d.precio    = BigDecimal.valueOf(parseMoney(model.getValueAt(i, 7)));
                d.descuento = BigDecimal.valueOf(parseMoney(model.getValueAt(i, 8))); // %
                items.add(d);
            }
        }
        return items;
    }
    private static String limpiarPrefijoPedido(String s) {
        if (s == null) return "";
        return s.replaceFirst("^\\s*PEDIDO\\s*[–-]\\s*", "").trim();
    }
    private void guardarPedidosDeCarrito(int numeroNota, LocalDate fechaEventoParaDetalle) {
        try {
            List<PedidosDAO.PedidoDraft> drafts = extraerPedidosDelCarrito();
            if (drafts.isEmpty()) return;
            String tel = txtTelefono.getText().trim();
            new PedidosDAO().insertarPedidos(
                    numeroNota,
                    tel.isEmpty() ? null : tel,
                    fechaEventoParaDetalle,
                    drafts
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Los PEDIDOS no se pudieron guardar: " + ex.getMessage(),
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void editarObservaciones() {
    JTextArea area = new JTextArea(
        observacionesTexto == null ? "" : observacionesTexto, 10, 60);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);

    int r = JOptionPane.showConfirmDialog(this, new JScrollPane(area),
        "Observaciones de la venta",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (r == JOptionPane.OK_OPTION) {
        observacionesTexto = area.getText().trim();
        if (observacionesTexto.isEmpty()) observacionesTexto = null;
    }
}


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Venta de contado (test panel)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(1020, 700);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(new VentaContadoPanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }


    // ==========================================================
    // >>> IMPRESIÓN
    // ==========================================================


    
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
            Integer numEmp = obtenerNumeroEmpresaBD();   // <- nuevo
            Empresa e = edao.buscarPorNumero(numEmp);
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


    
    // ---------- Printable con márgenes seguros, empresa compacta,
//           contacto debajo del encabezado y obsequios al final ----------
private Printable construirPrintableEmpresarial(
        EmpresaInfo emp, Nota n, List<NotaDetalle> dets, PagoFormas p,
        LocalDate fechaEntregaMostrar, LocalDate fechaEventoMostrar,
        String asesorNombre, String folioTxt,
        String clienteNombreFallback, String tel2Fallback) {

    // ===== datos base ya calculados (efectivamente finales) =====
    final String tel1      = (n.getTelefono() == null) ? "" : n.getTelefono();
    final String fechaHoy  = LocalDate.now().format(MX);
    final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaEntregaMostrar.format(MX);
    final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaEventoMostrar.format(MX);
    final double total     = (n.getTotal() == null) ? 0d : n.getTotal();

    final double tc = (p.getTarjetaCredito()   == null) ? 0d : p.getTarjetaCredito();
    final double td = (p.getTarjetaDebito()    == null) ? 0d : p.getTarjetaDebito();
    final double am = (p.getAmericanExpress()  == null) ? 0d : p.getAmericanExpress();
    final double tr = (p.getTransferencia()    == null) ? 0d : p.getTransferencia();
    final double dp = (p.getDeposito()         == null) ? 0d : p.getDeposito();
    final double ef = (p.getEfectivo()         == null) ? 0d : p.getEfectivo();
    

    // ===== cliente (resumen) =====
    String cliNombre  = (clienteNombreFallback == null) ? "" : clienteNombreFallback;
    String cliTel2    = (tel2Fallback == null) ? "" : tel2Fallback;
    String cliPrueba1 = "", cliPrueba2 = "";
    try {
        clienteDAO cdao = new clienteDAO();
        ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
        if (cr != null) {
            if (cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank())
                cliNombre = cr.getNombreCompleto();
            if (cr.getTelefono2() != null) cliTel2 = cr.getTelefono2();
            if (cr.getFechaPrueba1() != null) cliPrueba1 = cr.getFechaPrueba1().format(MX);
            if (cr.getFechaPrueba2() != null) cliPrueba2 = cr.getFechaPrueba2().format(MX);
        }
    } catch (Exception ignore) { }

    // Medidas (si existen columnas)
    String medBusto = "", medCintura = "", medCadera = "";
    try {
        clienteDAO cdao = new clienteDAO();
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

    // Congelar datos cliente (evita "final or effectively final")
    final String fCliNombre  = cliNombre;
    final String fCliTel2    = cliTel2;
    final String fCliPrueba1 = cliPrueba1;
    final String fCliPrueba2 = cliPrueba2;

    // Obsequios (del panel) sin cambiar firma del método
    java.util.List<String> tmp = null;
    try { tmp = new java.util.ArrayList<>(VentaContadoPanel.this.obsequiosSel); } catch (Throwable ignore) {}
    final java.util.List<String> obsequiosPrint =
            (tmp == null) ? java.util.Collections.emptyList() : tmp;

    // ===== Printable =====
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

            // Tipografías
            final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
            final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
            final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
            final Font fText    = g2.getFont().deriveFont(10.2f);
            final Font fSmall   = g2.getFont().deriveFont(8.8f);

            // ===== Encabezado: logo + empresa (compacto) =====
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
            int infoTextWidth  = infoRightWidth - 160; // espacio para folio

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

            // Tel/WhatsApp permanecen en el bloque de empresa
            yy = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Tel: ", emp.telefono),
                    labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

            // Folio arriba derecha
            g2.setFont(fH1);
            rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

            // === CONTACTO EN COLUMNA A LA DERECHA (debajo del folio) ===
            final int rightColW = 120;                    // mismo ancho reservado que ya usas
            final int xRight    = x + w - rightColW;      // columna derecha
            int yRight          = y + 22;                 // un poco debajo del folio

            g2.setFont(fSmall);

            // 1) Correo y Web (texto con etiqueta, en líneas separadas)
            yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
            yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

            // 2) Redes con ícono + usuario (sin etiqueta “IG/FB/TikTok”)
            BufferedImage igIcon = loadIcon("instagram.png");
            BufferedImage fbIcon = loadIcon("facebook.png");
            BufferedImage ttIcon = loadIcon("tiktok.png");

            yRight = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRight, rightColW, 12, 6);
            yRight = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRight, rightColW, 12, 6);
            yRight = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRight, rightColW, 12, 6);

            // Altura real ocupada por el encabezado (máximo entre logo/empresa y la columna derecha)
            int leftBlockH  = Math.max(yy - y, headerH);
            int rightBlockH = Math.max(0, yRight - y);
            int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;

            // Continúa debajo del encabezado
            int afterTail = y + usedHeader;

            // Título
            g2.setFont(fTitle);
            center(g2, "NOTA DE VENTA", x, w, afterTail + 14);
            y = afterTail + 32;
            usedHeader = Math.max(yy - y, headerH) + 6; // altura ocupada por logo+empresa
            afterTail = y + usedHeader;

            // ===== Datos del cliente en 2 columnas =====
            g2.setFont(fSection);
            g2.drawString("Datos del cliente", x, y);
            y += 13;

            final int gapCols = 24;
            final int leftW   = (w - gapCols) / 2;
            final int rightW  = w - gapCols - leftW;

            int yLeft  = y;
            yRight = y;

            g2.setFont(fText);
            // Izquierda: identidad y contacto
            yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(fCliNombre)), x, yLeft, leftW);
            yLeft  = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Teléfono: ", safe(tel1)),
                    labelIf("Teléfono 2: ", safe(fCliTel2))), x, yLeft + 2, leftW);
            if (!medidasFmt.isBlank()) {
                yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);
            }

            // Derecha: fechas (evento/entrega de la venta) y asesor
            yRight = drawWrapped(g2, labelIf("Fecha de venta: ", fechaHoy), x + leftW + gapCols, yRight, rightW);
            if (!fEvento.isEmpty())
                yRight = drawWrapped(g2, labelIf("Fecha de evento: ", fEvento), x + leftW + gapCols, yRight + 2, rightW);
            if (!fCliPrueba1.isEmpty())
                yRight = drawWrapped(g2, labelIf("Fecha de prueba 1: ", fCliPrueba1), x + leftW + gapCols, yRight + 2, rightW);
            if (!fCliPrueba2.isEmpty())
                yRight = drawWrapped(g2, labelIf("Fecha de prueba 2: ", fCliPrueba2), x + leftW + gapCols, yRight + 2, rightW);
            if (!fEntrega.isEmpty())
                yRight = drawWrapped(g2, labelIf("Fecha de entrega: ", fEntrega), x + leftW + gapCols, yRight + 2, rightW);
            if (asesorNombre != null && !asesorNombre.isBlank())
                yRight = drawWrapped(g2, labelIf("Asesor/a: ", asesorNombre), x + leftW + gapCols, yRight + 2, rightW);

            y = Math.max(yLeft, yRight) + 10;

            // ===== Tabla de artículos =====
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

            for (NotaDetalle d : dets) {
                String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                        ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                String detalle = (d.getCodigoArticulo() > 0 ? d.getCodigoArticulo() + " · " : "")
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
            // === Observaciones (solo si existen) ===
try {
    String obs = new Controlador.NotasObservacionesDAO().getByNota(n.getNumeroNota());
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


            // TOTAL a la derecha
            g2.setFont(fH1);
            rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y);
            y += 22;
            // Total con letra (debajo del total, a la izquierda)
            g2.setFont(fText);
            java.math.BigDecimal bdTotal = java.math.BigDecimal
            .valueOf(total)
            .setScale(2, java.math.RoundingMode.HALF_UP);
            String pagadoLetra = numeroALetras(bdTotal.doubleValue());
            int anchoLetras = w - 230;
            
            int yInicioTotales = y - 24; // solo hubo una línea (TOTAL)
            drawWrapped(g2, "Cantidad pagada con letra: " + pagadoLetra, x, yInicioTotales, anchoLetras);

            // ===== Pagos (dos columnas) =====
            g2.setFont(fSection);
            g2.drawString("Pagos", x, y);
            y += 12;
            g2.setFont(fText);

            int yPLeft  = y;
            int yPRight = y;

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
            

            y = Math.max(yPLeft, yPRight) + 8;

            // ===== Obsequios incluidos (debajo de todo). Si no hay, no se imprime nada. =====
if (obsequiosPrint != null && !obsequiosPrint.isEmpty()) {
    y += 10;
    g2.drawLine(x, y, x + w, y); y += 16;

    g2.setFont(fSection);
    g2.drawString("Obsequios incluidos", x, y);
    y += 12;

    g2.setFont(fText);

    // Columnas: Código | Obsequio | Talla | Color
    final int gap2      = 16;
    final int colCodW   = 70;
    final int colTalW   = 70;
    final int colMarW   = 70;
    final int colModW   = 70;
    final int colColW   = 120;

    final int xCod = x;
    final int xNom = xCod + colCodW + gap2;
    final int xMar = xNom + colCodW + gap2;
    final int xMod = xMar + colMarW + gap2;
    final int xTal = xMod + colModW + gap2;
    final int xCol = xTal + colTalW + gap2;

    // Encabezado
    g2.drawString("Código",   xCod, y);
    g2.drawString("Obsequio", xNom, y);
    g2.drawString("Marca",    xMar, y);
    g2.drawString("Modelo",   xMod, y);
    g2.drawString("Talla",    xTal, y);
    g2.drawString("Color",    xCol, y);
    y += 10; g2.drawLine(x, y, x + w, y); y += 14;

    // ---- Precargar detalles de TODOS los obsequios en un solo query (sin filtrar por status/existencia)
    java.util.List<Integer> codigos = new java.util.ArrayList<>();
    for (String raw : obsequiosPrint) {
        if (raw == null) continue;
        String digits = raw.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try { codigos.add(Integer.parseInt(digits)); } catch (Exception ignore) {}
        }
    }

    java.util.Map<Integer, String[]> info = new java.util.HashMap<>();
    if (!codigos.isEmpty()) {
        StringBuilder sql = new StringBuilder(
            "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
            "FROM InventarioObsequios WHERE codigo_articulo IN (");
        for (int i=0;i<codigos.size();i++){ if(i>0) sql.append(','); sql.append('?'); }
        sql.append(')');

        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i=0;i<codigos.size();i++) ps.setInt(i+1, codigos.get(i));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int c         = rs.getInt(1);
                    String nombre = safe(rs.getString(2));
                    String marca  = safe(rs.getString(3));
                    String modelo = safe(rs.getString(4));
                    String talla  = safe(rs.getString(5));
                    String color  = safe(rs.getString(6));
                    info.put(c, new String[]{nombre, marca, modelo, talla, color});
                }
            }
        } catch (Exception ignore) { /* si falla, mostramos al menos el código */ }
    }

    // ---- Pintar filas usando el mapa precargado
    for (String raw : obsequiosPrint) {
    if (raw == null || raw.trim().isEmpty()) continue;

    String codigoTxt = raw.trim();
    int codNum = -1;
    try { codNum = Integer.parseInt(codigoTxt.replaceAll("[^0-9]", "")); } catch (Exception ignore) {}

    String nombre = "", marca = "", modelo = "", talla = "", color = "";
    String[] row = (codNum != -1) ? info.get(codNum) : null;
    if (row != null) {
        codigoTxt = String.valueOf(codNum);
        nombre    = row[0];
        marca     = row[1];
        modelo    = row[2];
        talla     = row[3];
        color     = row[4];
    }

    // MEDIR cada columna con drawWrapped y tomar el mayor “y de salida”
    int yCod = drawWrapped(g2, "• " + codigoTxt, xCod, y, colCodW);
    int yNom = drawWrapped(g2, nombre,          xNom, y, colCodW);
    int yMar = drawWrapped(g2, marca,           xMar, y, colMarW);
    int yMod = drawWrapped(g2, modelo,          xMod, y, colModW);
    int yTal = drawWrapped(g2, talla,           xTal, y, colTalW);
    int yCol = drawWrapped(g2, color,           xCol, y, colColW);

    // Avanza la siguiente fila exactamente al alto máximo consumido por esta
    y = Math.max(Math.max(Math.max(yCod, yNom), Math.max(yMar, yMod)), Math.max(yTal, yCol));
}
}
            return PAGE_EXISTS;
}

        // ===== helpers =====
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
            // Ajuste "uno" → "un" antes de "mil"/"millón"/"millones"/"pesos"
            texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
            texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

            return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
        }

        // ==== Íconos sociales (cacheados) ====
        
        private String safe(String s){ return (s == null) ? "" : s.trim(); }
        private String fmt2(Double v){ if (v == null) v = 0d; return String.format("%.2f", v); }
        private BufferedImage loadIcon(String pathOrName) {
    try {
        // 1) Nombre base (por si viene "/icons/tiktok.png")
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

        // 3) Fallback al classpath (lo que ya tenías)
        String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
        try (java.io.InputStream in = VentaContadoPanel.class.getResourceAsStream(cpPath)) {
            if (in == null) return null;
            javax.imageio.ImageIO.setUseCache(false);
            return trimTransparent(javax.imageio.ImageIO.read(in));
        }
    } catch (Exception e) {
        return null;
    }
}

/** Recorta pixeles totalmente transparentes alrededor del icono. */
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
        // alineamos el icono con la línea de texto
        int ascent = g2.getFontMetrics().getAscent();
        int iconY  = baseline - Math.min(iconSize, ascent);
        g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
        tx += iconSize + gapPx;
    }
    return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
}
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
                    y += 12; // renglón compacto
                    line.setLength(0); line.append(w);
                }
            }
            if (line.length()>0){ g2.drawString(line.toString(), x, y); }
            return y + 12;
        }
    };
    }
}