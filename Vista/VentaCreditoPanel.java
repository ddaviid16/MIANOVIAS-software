package Vista;

// ===== IMPRESIÓN =====
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
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
import javax.swing.table.TableColumnModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;


import Controlador.AsesorDAO;
import Controlador.EmpresaDAO;
import Controlador.InventarioDAO;
import Controlador.NotasDAO;
import Controlador.NotasMemoDAO;
import Controlador.ObsequiosDAO;
import Controlador.PedidosDAO;
import Controlador.VentaCreditoService;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.Empresa;
import Modelo.Inventario;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Modelo.PagoFormas;
import Utilidades.TelefonosUI;
import Controlador.FacturaDatosDAO;


public class VentaCreditoPanel extends JPanel {

    private java.util.List<String> obsequiosSel = new java.util.ArrayList<>();
    private JLabel lblObsequios;

    private JTextField txtTelefono, txtTelefono2, txtNombreCompleto;
    private JTextField txtFechaEvento, txtFechaPrueba1, txtFechaPrueba2, txtFechaEntrega, txtUltimaNota;
    private JComboBox<Modelo.Asesor> cbAsesor;
    private JButton btnRegistrarCliente;

    private JButton btnCondiciones;
    private JButton btnObservaciones;
    private String memoEditable; // texto del formato de condiciones
    private String observacionesTexto; // texto libre capturado por operador

    // ===== Usuario en sesión (cajera) =====
private int cajeraCodigo;
private String cajeraNombre;

public void setCajeraActual(int codigoEmpleado, String nombreCompleto) {
    this.cajeraCodigo = codigoEmpleado;
    this.cajeraNombre = nombreCompleto;
}
    
    private JButton btFactura;
    private DlgFactura.CapturaFactura facturaDraft; // nulo si el usuario no capturó
    private JLabel lbFacturaBadge;                  // pequeño indicador visual

    private JCheckBox chkUsarFechaCliente;
    private JTextField txtFechaEventoVenta;   // dd-MM-yyyy
    private JTextField txtFechaEntregaVenta;  // dd-MM-yyyy

    private JTextField txtCod;
    private JTable tb;
    private DefaultTableModel model;
     private JButton btnCambiarFechaVenta;
    private JLabel lblFechaVenta;

    private LocalDate fechaVentaSeleccionada = null;
    private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JButton btRegistrarPedido = new JButton("Registrar artículo");

    private JTextField txtSubtotal, txtTotal;
    private JTextField txtTC, txtTD, txtAMX, txtTRF, txtDEP, txtEFE;

    private final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private String lastTelefonoConsultado = null;
    private boolean updatingTable = false;

    // === Pago con Devolución ===
    private JComboBox<String> cbFolioDV; // Folio de devolución
    private JTextField txtMontoDV;       // Monto de devolución
    private double montoDVAplicado = 0;  // Valor interno aplicado
    private JButton btnAplicarDV;
    private java.util.List<Modelo.PagoDV> dvAplicadas = new java.util.ArrayList<>();

    public VentaCreditoPanel() {
        
    setLayout(new BorderLayout());

    // 1) Crear los campos de teléfono
    txtTelefono  = new JTextField();
    txtTelefono2 = new JTextField();

    // 2) Instalar la lógica de formato de teléfono
    TelefonosUI.instalar(txtTelefono, 10);
    TelefonosUI.instalar(txtTelefono2, 10);

    // 3) Ya después armas el layout
    JPanel top = new JPanel(new GridBagLayout());
    top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(6,6,6,6);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1;

    int y = 0;

    // Teléfonos
    addCell(top,c,0,y,new JLabel("Teléfono cliente:"),1,false);
    addCell(top,c,1,y,txtTelefono,1,true);
    addCell(top,c,2,y,new JLabel("Teléfono 2:"),1,false);
    addCell(top,c,3,y,txtTelefono2,1,true);
    y++;

        txtTelefono.addActionListener(_e -> cargarCliente());
        ((AbstractDocument) txtTelefono.getDocument()).addDocumentListener((SimpleDocListener) () -> {
            String t = txtTelefono.getText().trim();
            if (t.length() >= 7) {
                if (lastTelefonoConsultado == null || !lastTelefonoConsultado.equals(t)) cargarCliente();
            } else limpiarInfoCliente();
        });
        txtTelefono.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { cargarCliente(); }
        });

        // Nombre
        txtNombreCompleto = readOnlyField();
        addCell(top,c,0,y,new JLabel("Nombre y apellidos:"),1,false);
        addCell(top,c,1,y,txtNombreCompleto,3,true);
        y++;

        // Registrar cliente
        btnRegistrarCliente = new JButton("Registrar cliente");
        btnRegistrarCliente.setVisible(false);
        btnRegistrarCliente.addActionListener(_e -> abrirFormularioCliente());
        addCell(top, c, 0, y, btnRegistrarCliente, 1, false);
        y++;

        // Asesor
        cbAsesor = new JComboBox<>();
        cargarAsesores();
        addCell(top,c,0,y,new JLabel("Asesor*:"),1,false);
        addCell(top,c,1,y,cbAsesor,1,true);
        y++;

        // Fechas cliente
        txtFechaEvento   = readOnlyField();
        txtFechaPrueba1  = readOnlyField();
        addCell(top,c,0,y,new JLabel("Fecha de evento (cliente):"),1,false);
        addCell(top,c,1,y,txtFechaEvento,1,true);
        addCell(top,c,2,y,new JLabel("Fecha de prueba 1:"),1,false);
        addCell(top,c,3,y,txtFechaPrueba1,1,true);
        y++;

        txtFechaPrueba2  = readOnlyField();
        txtFechaEntrega  = readOnlyField();
        addCell(top,c,0,y,new JLabel("Fecha de prueba 2:"),1,false);
        addCell(top,c,1,y,txtFechaPrueba2,1,true);
        addCell(top,c,2,y,new JLabel("Fecha de entrega:"),1,false);
        addCell(top,c,3,y,txtFechaEntrega,1,true);
        y++;

        // ----------- (OCULTO) Última nota del cliente -----------
        // Se inicializa pero NO se agrega al layout.
        txtUltimaNota = readOnlyField();

        // ----------- Control de fecha por venta (REUBICADO) -----------
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

        // Fila: checkbox + "Fecha de evento para esta venta"
        addCell(top, c, 0, y, chkUsarFechaCliente, 2, true);
        addCell(top, c, 2, y, new JLabel("Fecha de evento para esta venta (dd-MM-yyyy):"), 1, false);
        addCell(top, c, 3, y, txtFechaEventoVenta, 1, true);
        y++;

        // Fila: "Fecha de entrega para esta venta"
        addCell(top, c, 2, y, new JLabel("Fecha de entrega para esta venta (dd-MM-yyyy):"), 1, false);
        addCell(top, c, 3, y, txtFechaEntregaVenta, 1, true);
        y++;

        // Código + seleccionar
        txtCod = new JTextField();

        JButton btSeleccionar = new JButton("Seleccionar artículo…");
        btSeleccionar.addActionListener(_e -> seleccionarArticulo());

        JButton btAdd = new JButton("Agregar artículo por código");
        btAdd.addActionListener(_e -> agregarArticulo());

        addCell(top, c, 0, y, new JLabel("Código artículo:"), 1, false);
        addCell(top, c, 1, y, txtCod, 1, true);
        addCell(top, c, 2, y, btSeleccionar, 2, false);
        y++;

        btRegistrarPedido.setVisible(false);
        btRegistrarPedido.addActionListener(_e -> abrirDialogoPedido());
        addCell(top, c, 0, y, btRegistrarPedido, 1, false);

        JButton btObsequios = new JButton("Añadir obsequio(s)");
        btObsequios.addActionListener(_e -> seleccionarObsequios());
        lblObsequios = new JLabel("0 obsequios seleccionados");

        addCell(top, c, 1, y, btAdd, 1, false);
        addCell(top, c, 2, y, btObsequios, 1, false);
        addCell(top, c, 3, y, lblObsequios, 1, false);
        y++;

        ((AbstractDocument) txtCod.getDocument()).addDocumentListener((SimpleDocListener) () -> {
    String s = txtCod.getText();
    String val = (s == null) ? "" : s.trim().toUpperCase();

    // Botón PEDIR como ya lo tenías
    btRegistrarPedido.setVisible("PEDIR".equals(val));

    // Modo admin para fecha de venta
    boolean esAdmin = "ADMIN".equals(val);
    if (btnCambiarFechaVenta != null) {
        btnCambiarFechaVenta.setVisible(esAdmin);
    }

    // Si dejan de estar en "admin", regresamos al modo normal
    if (!esAdmin) {
        fechaVentaSeleccionada = null;
        if (lblFechaVenta != null) {
            lblFechaVenta.setText("Fecha de venta: " + getFechaVentaEfectiva().format(MX));
        }
    }
});



        // ===== Tabla carrito con "Fecha art."
String[] cols = {"Código","Artículo","Marca","Modelo","Talla","Color","Fecha art.","Precio","%Desc","Desc. $","Subtotal","Quitar"};
model = new DefaultTableModel(cols,0) {
    @Override public boolean isCellEditable(int r,int c){ return c==8 || c==11 || c==6; } // %Desc, botón, fecha
};
tb = new JTable(model);
tb.setRowHeight(26);
tb.setAutoCreateRowSorter(true);
tb.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

tb.getColumnModel().getColumn(8).setCellEditor(decimalEditor());

DefaultTableCellRenderer right = new DefaultTableCellRenderer();
right.setHorizontalAlignment(SwingConstants.RIGHT);
tb.getColumnModel().getColumn(7).setCellRenderer(right);
tb.getColumnModel().getColumn(8).setCellRenderer(right);
tb.getColumnModel().getColumn(9).setCellRenderer(right);
tb.getColumnModel().getColumn(10).setCellRenderer(right);

new ButtonColumn(tb, new AbstractAction("Quitar") {
    @Override public void actionPerformed(ActionEvent e) {
        int row = Integer.parseInt(e.getActionCommand());
        int mr = tb.convertRowIndexToModel(row);
        model.removeRow(mr);
        recalcularTotales();
    }
}, 11);

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

        // ==== Panel de tabla, igual que en VentaContado ====
        JPanel panelTabla = new JPanel(new BorderLayout());
        panelTabla.add(tb.getTableHeader(), BorderLayout.NORTH);
        panelTabla.add(tb, BorderLayout.CENTER);


        // Totales/anticipo
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
        // === Folio de devolución ===
        cbFolioDV = new JComboBox<>();
        txtMontoDV = moneyField();

        addCell(bottom, d, 0, r, new JLabel("Folio devolución:"), 1, false);
        addCell(bottom, d, 1, r, cbFolioDV, 1, true);
        addCell(bottom, d, 2, r, new JLabel("Monto devolución:"), 1, false);
        addCell(bottom, d, 3, r, txtMontoDV, 1, true);
        r++;

        // Evento para recalcular totales al cambiar devolución
        txtMontoDV.getDocument().addDocumentListener((SimpleDocListener) () -> {
        montoDVAplicado = parseMoney(txtMontoDV.getText());  // Actualiza montoDVAplicado
        validarSumaPagos();  // Recalcula los totales de pago
    });

                // ---- Factura: botón + badge ----
        JPanel pnlFactura = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btFactura = new JButton("Factura…");
        btFactura.addActionListener(_e -> abrirDialogoFactura());
        lbFacturaBadge = new JLabel("—");                       // muestra estado: — | CAPTURA
        lbFacturaBadge.setOpaque(true);
        lbFacturaBadge.setBackground(new Color(245,245,245));
        lbFacturaBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        pnlFactura.add(btFactura);
        pnlFactura.add(lbFacturaBadge);

        // col 0 del mismo r donde agregas Observaciones/Condiciones/Guardar
        addCell(bottom, d, 0, r, pnlFactura, 1, false);

        btnCondiciones = new JButton("Condiciones…");
        btnCondiciones.addActionListener(_e -> editarMemoPrevia(false));
        addCell(bottom, d, 2, r, btnCondiciones, 1, false);

        btnObservaciones = new JButton("Observaciones…");
        btnObservaciones.addActionListener(_e -> editarObservaciones());
        addCell(bottom, d, 1, r, btnObservaciones, 1, false);


        JButton btGuardar = new JButton("Registrar venta (Crédito)");
        btGuardar.addActionListener(_e -> guardarVentaCredito());
        addCell(bottom,d,3,r,btGuardar,1,false);
        r++;
        lblFechaVenta = new JLabel("Fecha de venta: " + getFechaVentaEfectiva().format(MX));
        btnCambiarFechaVenta = new JButton("Cambiar fecha venta");
        btnCambiarFechaVenta.setVisible(false); // solo se ve con "admin"
        btnCambiarFechaVenta.addActionListener(_e -> cambiarFechaVenta());

        addCell(bottom, d, 2, r, lblFechaVenta, 1, false);
        addCell(bottom, d, 3, r, btnCambiarFechaVenta, 1, false);


        // ====== Panel que contiene todo (igual que VentaContado) ======
        JPanel contenido = new JPanel(new BorderLayout());
        contenido.add(top, BorderLayout.NORTH);
        contenido.add(panelTabla, BorderLayout.CENTER);   // <- SIN JScrollPane interno
        contenido.add(bottom, BorderLayout.SOUTH);

        // Scroll general del panel (uno solo)
        JScrollPane scroll = new JScrollPane(
                contenido,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        // Agregamos solo el scroll a este panel
        add(scroll, BorderLayout.CENTER);

        cargarAsesores();
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
    private void cargarCliente() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
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

            // === Cargar folios de devolución del cliente ===
// === Cargar folios de devolución del cliente (usando NotasDVDAO) ===
try {
    cbFolioDV.removeAllItems();
    cbFolioDV.addItem("--- Pagar con folio de devolución ---"); // placeholder

    // Reset estado interno de DV
    dvAplicadas.clear();
    montoDVAplicado = 0;
    txtMontoDV.setText("");

    java.util.List<Controlador.NotasDVDAO.DVDisponible> lista =
            new Controlador.NotasDVDAO().listarDisponiblesPorTelefono(tel);

    for (Controlador.NotasDVDAO.DVDisponible dv : lista) {
        double saldo = dv.disponible;  // ESTE es el saldo correcto
        cbFolioDV.addItem(dv.folio + " - $" + String.format("%.2f", saldo));
    }

    cbFolioDV.setSelectedIndex(0);
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this,
        "Error al cargar devoluciones: " + ex.getMessage(),
        "Error", JOptionPane.ERROR_MESSAGE);
}



            // Aunque no se muestre, se mantiene la asignación (no afecta a la UI)
            NotasDAO ndao = new NotasDAO();
            Integer ult = ndao.obtenerUltimaNotaPorTelefono(tel);
            txtUltimaNota.setText(ult == null ? "" : String.valueOf(ult));

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

    private void abrirFormularioCliente() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (tel == null || tel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingresa primero el teléfono del cliente.", "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Registro de Clientes", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.getContentPane().add(new ClientesPanel(tel));
        dlg.setSize(760, 620);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        cargarCliente();
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
        if (chkUsarFechaCliente.isSelected()) return parseFecha(txtFechaEvento.getText());
        return parseFecha(txtFechaEventoVenta.getText());
    }
    private LocalDate getFechaVentaEfectiva() {
    // Si el admin eligió una fecha, usar esa. Si no, hoy.
    return (fechaVentaSeleccionada != null) ? fechaVentaSeleccionada : LocalDate.now();
}
    private void cambiarFechaVenta() {
    LocalDate actual = getFechaVentaEfectiva();
    String valorActual = actual.format(MX); // dd-MM-yyyy

    String input = JOptionPane.showInputDialog(
            this,
            "Ingresa la fecha de venta (dd-MM-yyyy):",
            valorActual
    );

    if (input == null || input.trim().isEmpty()) {
        // Canceló o dejó vacío -> volvemos a hoy
        fechaVentaSeleccionada = null;
    } else {
        try {
            LocalDate f = LocalDate.parse(input.trim(), MX);
            fechaVentaSeleccionada = f;
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Fecha inválida. Ejemplo: 25-11-2025",
                    "Error de fecha",
                    JOptionPane.ERROR_MESSAGE);
            fechaVentaSeleccionada = null;
        }
    }

    if (lblFechaVenta != null) {
        lblFechaVenta.setText("Fecha de venta: " + getFechaVentaEfectiva().format(MX));
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

        // Opción placeholder
        cbAsesor.addItem(null);   // se verá como "Selecciona asesor" en el renderer

        AsesorDAO ad = new AsesorDAO();
        for (Modelo.Asesor a : ad.listarActivosDetalle()) {  // solo status='A' y tipo A/MA
            cbAsesor.addItem(a);
        }

        // Renderer (puedes dejarlo aquí o ponerlo una sola vez en el constructor)
        cbAsesor.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Modelo.Asesor a) {
                    setText(a.getNombreCompleto());
                } else if (value == null) {
                    setText("Selecciona asesor");
                }
                return this;
            }
        });

    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this,
                "No se pudieron cargar asesores: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    private void agregarArticulo() {
        try {
            String cod = txtCod.getText().trim();
            InventarioDAO dao = new InventarioDAO();
            Inventario i = dao.buscarParaVenta(cod);
            if (i == null) { JOptionPane.showMessageDialog(this,"No existe el artículo o está inactivo"); return; }
            try {
                String st = i.getStatus();
                if (st != null && !"A".equalsIgnoreCase(st)) {
                    JOptionPane.showMessageDialog(this,"No se puede vender: artículo inactivo (status="+st+")");
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
        DialogSeleccionArticulo dlg = new DialogSeleccionArticulo(SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        Inventario sel = dlg.getSeleccionado();
        if (sel != null) {
            try {
                String st = sel.getStatus();
                if (st != null && !"A".equalsIgnoreCase(st)) { JOptionPane.showMessageDialog(this,"No se puede vender: artículo inactivo (status="+st+")"); return; }
            } catch (Throwable ignore) {}
            if (sel.getExistencia() != null && sel.getExistencia() < 1) { JOptionPane.showMessageDialog(this,"Sin existencia."); return; }
            agregarArticuloDesdeInventario(sel);
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
            // limpiar campo de código y ocultar botón PEDIR
            txtCod.setText("");
            btRegistrarPedido.setVisible(false);

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
    // Total de la venta
    double total = parseMoney(txtTotal.getText());

    // Suma de todos los pagos, incluyendo la devolución
    double suma  = parseMoney(txtTC.getText()) + parseMoney(txtTD.getText())
                + parseMoney(txtAMX.getText()) + parseMoney(txtTRF.getText())
                + parseMoney(txtDEP.getText()) + parseMoney(txtEFE.getText())
                + montoDVAplicado;  // Asegúrate de incluir la devolución aquí

    // Muestra el tooltip con los detalles de la diferencia entre lo pagado y lo pendiente
    txtTotal.setToolTipText(String.format("Pagado: %.2f  | Diferencia: %.2f", suma, (total - suma)));
}



    private boolean validarClienteObligatorio() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (tel == null || tel.isEmpty()) {
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

    
    private void guardarVentaCredito() {
        if (tb.isEditing()) tb.getCellEditor().stopCellEditing();
        if (!validarClienteObligatorio()) return;

        if (cbAsesor.getSelectedItem()==null){
            JOptionPane.showMessageDialog(this,"Selecciona un asesor"); return;
        }
        if (model.getRowCount()==0){
            JOptionPane.showMessageDialog(this,"Agrega al menos un artículo"); return;
        }

        double total = parseMoney(txtTotal.getText());
        double anticipo = parseMoney(txtTC.getText()) + parseMoney(txtTD.getText()) + parseMoney(txtAMX.getText())
                + parseMoney(txtTRF.getText()) + parseMoney(txtDEP.getText()) + parseMoney(txtEFE.getText())+montoDVAplicado;

        if (anticipo < 0) anticipo = 0;
        if (anticipo > total + 0.005) {
            JOptionPane.showMessageDialog(this,"El anticipo no puede ser mayor al total.");
            return;
        }

        // Fechas a persistir
        LocalDate fechaEventoDefault = fechaPreferida();
        LocalDate fechaEntregaDefault;
        if (chkUsarFechaCliente.isSelected()) {
            fechaEntregaDefault = parseFecha(txtFechaEntrega.getText());
        } else {
            LocalDate f = parseFecha(txtFechaEntregaVenta.getText());
            fechaEntregaDefault = (f != null) ? f : parseFecha(txtFechaEntrega.getText());
        }

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Registrar la venta de CRÉDITO?\nAnticipo: " + String.format("%.2f", anticipo) +
                        "\nSaldo quedará pendiente para Abonos.",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;
        // === Validar que el monto de devolución no exceda el disponible ===

        String selFolio = (cbFolioDV.getSelectedItem() == null)
                ? ""
                : cbFolioDV.getSelectedItem().toString().trim();

        // Si no hay selección válida, saltar validación
        if (selFolio.isBlank() || selFolio.startsWith("---")) {
            // No hay folio seleccionado, continuar sin validar devolución
        } else {
    double montoIngresado = parseMoney(txtMontoDV.getText());
    double montoDisponible = 0;

    try {
        String montoStr = selFolio.replaceAll(".*\\$", "").replaceAll(",", "").trim();
        montoDisponible = Double.parseDouble(montoStr);
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
                "No se pudo determinar el monto disponible del folio de devolución (" + selFolio + ").",
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



        try {
            if (observacionesTexto == null) {
            editarObservaciones(); // fuerza al usuario a capturar o confirmar que no hay
            }

            Nota n = new Nota();
            String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
            n.setTelefono(tel == null || tel.isEmpty() ? null : tel);
            Modelo.Asesor sel = (Modelo.Asesor) cbAsesor.getSelectedItem();
            n.setAsesor(sel.getNumeroEmpleado());
            n.setTipo("CR");
            n.setTotal(total);
            n.setSaldo(Math.max(0.0, total - anticipo));
            n.setStatus("A");

           List<NotaDetalle> dets = new ArrayList<>();

for (int i = 0; i < model.getRowCount(); i++) {
    String cod = "";
    try { cod = model.getValueAt(i, 0).toString(); } catch (Exception ignore) {}
    if (cod == null) cod = "";
    cod = cod.trim();

    boolean esPedido = cod.isEmpty() || "0".equals(cod);
    if (esPedido) {
        // ESTE renglón NO va a Nota_Detalle, se guardará en Pedidos
        continue;
    }

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
// Lo que sí se guarda en Nota_Detalle
List<NotaDetalle> detsPrint = new ArrayList<>(dets);

// Añadir los PEDIDO SOLO PARA IMPRESIÓN
List<PedidosDAO.PedidoDraft> pedidosCarrito = extraerPedidosDelCarrito();

for (PedidosDAO.PedidoDraft ped : pedidosCarrito) {
    NotaDetalle d = new NotaDetalle();
    d.setCodigoArticulo(""); // vacío = pedido
    d.setArticulo("PEDIDO – " + limpiarPrefijoPedido(ped.articulo));
    d.setMarca(n(ped.marca));
    d.setModelo(n(ped.modelo));
    d.setTalla(n(ped.talla));
    d.setColor(n(ped.color));

    double precio = ped.precio == null ? 0.0 : ped.precio.doubleValue();
    double desc   = ped.descuento == null ? 0.0 : ped.descuento.doubleValue();
    double monto  = precio * (desc / 100.0);
    double sub    = precio - monto;

    d.setPrecio(precio);
    d.setDescuento(desc);
    d.setDescuentoMonto(monto);
    d.setSubtotal(sub);
    d.setFechaEvento(fechaEventoDefault);

    detsPrint.add(d);
}
LocalDate fechaVenta = getFechaVentaEfectiva();



            PagoFormas p = new PagoFormas();
            p.setFechaOperacion(fechaVenta);

            p.setTarjetaCredito(nullIfZero(parseMoney(txtTC.getText())));
            p.setTarjetaDebito(nullIfZero(parseMoney(txtTD.getText())));
            p.setAmericanExpress(nullIfZero(parseMoney(txtAMX.getText())));
            p.setTransferencia(nullIfZero(parseMoney(txtTRF.getText())));
            p.setDeposito(nullIfZero(parseMoney(txtDEP.getText())));
            p.setEfectivo(nullIfZero(parseMoney(txtEFE.getText())));

            // Asegúrate de que se registre el monto de devolución
            p.setDevolucion(nullIfZero(montoDVAplicado));

            // Si no hay folio seleccionado o es el placeholder, no guardes nada
            if (cbFolioDV.getSelectedItem() == null || cbFolioDV.getSelectedItem().toString().trim().startsWith("---")) {
                p.setReferenciaDV(null);
            } else {
                // Extrae solo el folio antes del " - $" si existe
                String clean = cbFolioDV.getSelectedItem().toString().trim().split("\\s*-\\s*\\$")[0].trim();
                p.setReferenciaDV(clean);
            }


            p.setTipoOperacion("CR");
            p.setStatus("A");


            VentaCreditoService svc = new VentaCreditoService();
            // -> ahora pasamos evento y entrega
            int numeroNota = svc.crearVentaCredito(n, dets, p, fechaVenta, fechaEventoDefault, fechaEntregaDefault);
            n.setNumeroNota(numeroNota); // <-- ¡asigna el número real antes de imprimir!
            // ======= Factura: si hay captura, persístela en Factura_Datos =======
try {
    if (facturaDraft != null) {
        new FacturaDatosDAO().upsert(
            numeroNota,
            facturaDraft.persona,        // "PF" o "PM"
            facturaDraft.rfc.trim().toUpperCase(),
            facturaDraft.regimen.trim().toUpperCase(),  // tú lo manejas como 4 chars
            facturaDraft.usoCfdi.trim().toUpperCase(),  // G03, CP01, S01, ...
            facturaDraft.correo == null ? null : facturaDraft.correo.trim()
        );
    }
} catch (Exception ex) {
    JOptionPane.showMessageDialog(this,
        "La venta se registró, pero no se pudieron guardar los datos de facturación:\n" + ex.getMessage(),
        "Factura", JOptionPane.WARNING_MESSAGE);
}
            

            // ======= 1.1) Aplicar devoluciones usadas (DV) =======
if (dvAplicadas != null && !dvAplicadas.isEmpty()) {
    try {
        new Controlador.NotasDVDAO().aplicarAVenta(numeroNota, "CR", dvAplicadas);
    } catch (SQLException exDV) {
        JOptionPane.showMessageDialog(this,
            "Advertencia: la venta se registró, pero no se pudo actualizar las devoluciones utilizadas.\n" +
            exDV.getMessage(),
            "Error de actualización DV", JOptionPane.WARNING_MESSAGE);
    }
}

            // Obsequios
            if (!obsequiosSel.isEmpty()) {
                try {
                    ObsequiosDAO odao = new ObsequiosDAO();

                    LocalDate fEvento = parseFecha(txtFechaEvento.getText());

                    odao.insertarParaNota(
                        numeroNota,
                        tel.isEmpty() ? null : tel,
                        fechaVenta,   // <<< misma fecha de venta
                        obsequiosSel,
                        "CR",
                        sel != null ? sel.getNumeroEmpleado() : null,
                        "A",
                        fEvento
                    );


                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Obsequios no guardados: " + ex.getMessage(),
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }

            // Guardar PEDIDOS
            guardarPedidosDeCarrito(numeroNota, fechaEventoDefault);

            // ======= DETERMINAR FECHAS EFECTIVAS PARA MOSTRAR =======
        LocalDate eventoCliente  = parseFecha(txtFechaEvento.getText());
        LocalDate entregaCliente = parseFecha(txtFechaEntrega.getText());

        LocalDate eventoDetalle = null;
        for (NotaDetalle d : dets) {
            if (d.getFechaEvento() != null) { eventoDetalle = d.getFechaEvento(); break; }
        }
        LocalDate eventoMostrar  = (eventoDetalle != null) ? eventoDetalle : eventoCliente;
        LocalDate entregaMostrar = (fechaEntregaDefault != null) ? fechaEntregaDefault : entregaCliente;

        // ======= IMPRIMIR =======
EmpresaInfo emp = cargarEmpresaInfo();
String clienteNombre = txtNombreCompleto.getText().trim();
String tel2 = Utilidades.TelefonosUI.soloDigitos(txtTelefono2.getText());
String folioImpresion = (n.getFolio()==null || n.getFolio().isBlank()) ? "—" : n.getFolio();

// 1) Ticket / nota de anticipo
Printable prnTicket = construirPrintableEmpresarial(
        emp, n, detsPrint, p,
        entregaMostrar, eventoMostrar,
        sel.getNombreCompleto(), folioImpresion,
        clienteNombre, tel2, observacionesTexto,
        cajeraCodigo, cajeraNombre
);

// ¿Hay al menos un artículo que cuente como "Vestido"?
boolean imprimirCondiciones = detsPrint.stream()
        .anyMatch(d -> esArticuloVestido(d.getArticulo()));

// Decidimos qué printable usar:
Printable printablePrincipal;
if (imprimirCondiciones) {
    
    // === 4.1) MEMO / CONDICIONES ===
    if (memoEditable == null) {
        // Fuerza a que el usuario vea/edite el texto al menos una vez
        editarMemoPrevia(true);
    }
    // Render final con datos reales para condiciones
    Map<String,String> varsFinal = buildMemoVars(
            emp, n, detsPrint, entregaMostrar, eventoMostrar, sel.getNombreCompleto()
    );

    Printable condiciones = construirPrintableCondiciones(
            emp,
            n,
            detsPrint,
            varsFinal,
            folioImpresion,
            sel.getNombreCompleto(),
            eventoMostrar,
            entregaMostrar,         // la usaremos como "estará en tienda"
            clienteNombre
    );

    printablePrincipal = combinarEnDosPaginas(prnTicket, condiciones);
} else {
    // Solo ticket, sin hoja de condiciones
    printablePrincipal = prnTicket;
}
// >>> SNAPSHOT PARA TARJETAS (antes de limpiar UI)
java.util.List<String> obsequiosTarjetas = new java.util.ArrayList<>(obsequiosSel);
String obsTarjetas = observacionesTexto;
LocalDate fechaCompraTarjetas = fechaVenta;

        imprimirYConfirmarAsync(
        printablePrincipal,
        () -> {
            // Primero las tarjetas por artículo
            imprimirTarjetasVenta(
                    emp,
                    clienteNombre,
                    eventoMostrar,
                    fechaCompraTarjetas,
                    sel.getNombreCompleto(),
                    detsPrint,
                    obsequiosTarjetas,
                    obsTarjetas
            );

            // Y luego el mensaje de éxito, SIN otra lambda
            JOptionPane.showMessageDialog(this,
                    "Venta de CRÉDITO registrada e impresa.\n Folio: " + folioImpresion);
        },
        () -> JOptionPane.showMessageDialog(this,
                "Venta de CRÉDITO registrada pero la impresión no se confirmó.\n" +
                        "Puedes reimprimir desde el módulo de reimpresión (Folio: " + folioImpresion + ").",
                "Aviso", JOptionPane.WARNING_MESSAGE)
);


// Notificar a Corte de Caja que hubo una operación completada
try {
    Utilidades.EventBus.notificarOperacionFinalizada();
} catch (Exception ignore) {}


        // ======= FORMATO DE CONDICIONES =======
try {

    NotasMemoDAO memoDao = new NotasMemoDAO();
    memoDao.upsert(numeroNota, memoEditable);
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this,
            "No se pudo guardar el formato de condiciones: " + ex.getMessage(),
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


            // ======= 5) LIMPIAR UI =======
            // limpiar observaciones
            fechaVentaSeleccionada = null;
if (lblFechaVenta != null) {
    lblFechaVenta.setText("Fecha de venta: " + getFechaVentaEfectiva().format(MX));
}
if (btnCambiarFechaVenta != null) {
    btnCambiarFechaVenta.setVisible(false);
}

observacionesTexto = null;

// limpiar asesor (regresar al placeholder "Selecciona asesor")
if (cbAsesor.getItemCount() > 0) {
    cbAsesor.setSelectedIndex(0);
}

// limpiar DV internas
dvAplicadas.clear();

// limpiar campo de código de artículo y botón PEDIR
txtCod.setText("");
btRegistrarPedido.setVisible(false);

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

// limpiar estado de factura en UI
facturaDraft = null;
actualizarFacturaBadge();

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

    private String n(String s){ return s==null?"":s; }
    private double parseMoney(Object o){
        if (o==null) return 0;
        String s = o.toString().trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch(Exception e){ return 0; }
    }
    private Double nullIfZero(double v){ return Math.abs(v) < 0.0001 ? null : v; }
    private double clamp0a100(double v){ if (v<0) return 0; if (v>100) return 100; return v; }
    private DocumentFilter onlyDecimal(){ return new DialogArticulo.DecimalFilter(12); }

    static class ButtonColumn extends AbstractCellEditor implements javax.swing.table.TableCellRenderer,
            javax.swing.table.TableCellEditor, java.awt.event.ActionListener {
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

    private List<PedidosDAO.PedidoDraft> extraerPedidosDelCarrito() {
    List<PedidosDAO.PedidoDraft> items = new ArrayList<>();

    for (int i = 0; i < model.getRowCount(); i++) {
        String codStr = "";
        try {
            codStr = String.valueOf(model.getValueAt(i, 0)).trim();
        } catch (Exception ignore) {}

        // Un PEDIDO es SOLO cuando el código es "0" (lo que pones en el diálogo)
        if (!"0".equals(codStr)) {
            continue; // artículo normal de inventario, no se va a Pedidos
        }

        PedidosDAO.PedidoDraft d = new PedidosDAO.PedidoDraft();
        d.articulo  = limpiarPrefijoPedido(String.valueOf(model.getValueAt(i, 1)));
        d.marca     = n(String.valueOf(model.getValueAt(i, 2)));
        d.modelo    = n(String.valueOf(model.getValueAt(i, 3)));
        d.talla     = n(String.valueOf(model.getValueAt(i, 4)));
        d.color     = n(String.valueOf(model.getValueAt(i, 5)));
        d.precio    = BigDecimal.valueOf(parseMoney(model.getValueAt(i, 7)));
        d.descuento = BigDecimal.valueOf(parseMoney(model.getValueAt(i, 8)));
        items.add(d);
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
            String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
            new PedidosDAO().insertarPedidos(
                    numeroNota,
                    tel == null || tel.isEmpty() ? null : tel,
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
/** Variables para vista previa desde la UI (antes de crear la nota). */
private Map<String,String> construirVarsDesdeUI() {
    Map<String,String> v = new LinkedHashMap<>();
    EmpresaInfo emp = cargarEmpresaInfo();

    // Cliente
    v.put("cliente_nombre", n(txtNombreCompleto.getText()));
    v.put("fecha_compra", getFechaVentaEfectiva().format(MX));
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

private Map<String, String> construirCtxMemoPreliminar() {
    Map<String, String> vars = new HashMap<>();
    vars.put("cliente_nombre", txtNombreCompleto.getText().trim());
    vars.put("fecha_compra", getFechaVentaEfectiva().format(MX));
    vars.put("fecha_evento", txtFechaEvento.getText().trim());
    vars.put("fecha_en_tienda", txtFechaEntrega.getText().trim());
    vars.put("asesora", cbAsesor.getSelectedItem() != null
            ? ((Modelo.Asesor) cbAsesor.getSelectedItem()).getNombreCompleto() : "");
    vars.put("whatsapp", obtenerWhatsAppEmpresa());
    return vars;
}

private String obtenerWhatsAppEmpresa() {
    try (Connection cn = Conexion.Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement("SELECT whatsapp FROM Empresa LIMIT 1");
         ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getString("whatsapp");
    } catch (SQLException ex) {
        System.err.println("Error leyendo WhatsApp empresa: " + ex.getMessage());
    }
    return "";
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
    v.put("fecha_compra", getFechaVentaEfectiva().format(MX));
    v.put("fecha_evento", fechaEventoMostrar==null? "" : fechaEventoMostrar.format(MX));
    v.put("fecha_en_tienda", fechaEntregaMostrar==null? "" : fechaEntregaMostrar.format(MX));

    // Artículo principal
    // Artículo principal
NotaDetalle d0 = null;

// 1) Intentar tomar primero un "vestido"
for (NotaDetalle d : dets) {
    if (esArticuloVestido(d.getArticulo())) {
        d0 = d;
        break;
    }
}

// 2) Si no encontró vestido, buscar cualquiera con código
if (d0 == null) {
    for (NotaDetalle d : dets) {
        if (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty()) {
            d0 = d;
            break;
        }
    }
}

// 3) Si sigue sin nada, tomar el primero
if (d0 == null && !dets.isEmpty()) {
    d0 = dets.get(0);
}

    v.put("modelo",  d0==null? "": n(d0.getModelo()));
    v.put("marca",   d0==null? "": n(d0.getMarca()));
    v.put("color",   d0==null? "": n(d0.getColor()));
    v.put("talla",   d0==null? "": n(d0.getTalla()));
    v.put("codigo", (d0!=null && d0.getCodigoArticulo()!=null && !d0.getCodigoArticulo().isEmpty()) ? d0.getCodigoArticulo() : "");
    v.put("precio", String.format("%.2f", d0==null?0d:(d0.getPrecio()==null?0d:d0.getPrecio())));
    v.put("descuento_pct", String.format("%.2f", d0==null?0d:(d0.getDescuento()==null?0d:d0.getDescuento())));
    v.put("precio_pagar", String.format("%.2f", d0==null?0d:(d0.getSubtotal()==null?0d:d0.getSubtotal())));

    // Asesor y empresa
    v.put("asesora", n(asesorNombre));
    v.put("whatsapp", n(emp.whatsapp));

    return v;
}
// Segunda página con las condiciones, ahora con formato controlado y encabezado
private Printable construirPrintableCondiciones(
        EmpresaInfo emp,
        Nota n,
        java.util.List<NotaDetalle> dets,
        Map<String, String> vars, // <-- Asegúrate que la firma acepte el Map
        String folio,
        String asesorNombre,
        LocalDate fechaEvento,
        LocalDate fechaEnTienda,
        String clienteNombre) {

    // (Opcional) Carga el texto de las cláusulas desde la BD
    // Esto asume que seguiste el PASO 1 y el texto SÓLO son las cláusulas.
    final String baseTexto = (memoEditable != null && !memoEditable.isBlank())
        ? memoEditable
        : obtenerCondicionesPredeterminadas();
    
    // Fallback por si la BD está vacía, basado en tu foto
    final String P1_INTRO = "En MIANOVIAS, ¡te damos la bienvenida a vivir esta gran experiencia!";
    final String P5_ACUERDO = "ESTOY DE ACUERDO:";
    final String P6_FIRMA = "NOMBRE Y FIRMA DEL CLIENTE";


    return (g, pf, pageIndex) -> {
        if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Márgenes estándar carta (~2.5 cm ≈ 70 px)
        final int M = 70;
        int x = (int) pf.getImageableX() + M;
        int y = (int) pf.getImageableY() + M;
        int w = (int) pf.getImageableWidth() - (M * 2);

        // === Fuentes ===
        Font bodyFont = new Font("Arial", Font.PLAIN, 11);
        Font labelFont = new Font("Arial", Font.BOLD, 10);
        
        g2.setFont(bodyFont);
        
        // 1. Párrafo de Bienvenida
        y = drawWrappedSimple(g2, P1_INTRO, x, y, w);
        y += 18; // Espacio extra

        // === 2. Bloque de Datos (usando los helpers) ===
        g2.setFont(labelFont); // Fuente negrita para etiquetas

        // Extraer datos del mapa
        String vNombre = vars.getOrDefault("cliente_nombre", "");
        String vCompra = vars.getOrDefault("fecha_compra", "");
        String vEvento = vars.getOrDefault("fecha_evento", "");
        String vModelo = vars.getOrDefault("modelo", "");
        String vTalla  = vars.getOrDefault("talla", "");
        String vColor  = vars.getOrDefault("color", "");
        String vMarca  = vars.getOrDefault("marca", "");
        String vAsesora = vars.getOrDefault("asesora", "");
        String vCodigo = vars.getOrDefault("codigo", "");
        String vEntrega = vars.getOrDefault("fecha_en_tienda", "");
        String vPrecio = vars.getOrDefault("precio", "");
        String vDesc = vars.getOrDefault("descuento_pct", "");
        String vPagar = vars.getOrDefault("precio_pagar", "");
        String vBusto = vars.getOrDefault("busto", "");
        String vCintura = vars.getOrDefault("cintura", "");
        String vCadera = vars.getOrDefault("cadera", "");

        // Dibujar los campos
        y = drawFieldLine(g2, x, y, w, "NOMBRE DE LA NOVIA:", vNombre);
        y = drawTwoColsLine(g2, x, y, w, "FECHA DE COMPRA:", vCompra, "FECHA DE EVENTO:", vEvento);
        y = drawThreeColsLine(g2, x, y, w, 
            "MODELO:", vModelo, 
            "TALLA:", vTalla,
            "COLOR:", vColor);
        y = drawFieldLine(g2, x, y, w, "DE LA MARCA:", vMarca);
        y = drawFieldLine(g2, x, y, w, "TU ASESORA:", vAsesora);
        y = drawTwoColsLine(g2, x, y, w, "CÓDIGO:", vCodigo, "TU VESTIDO ESTARÁ EN TIENDA EL DIA:", vEntrega);
        y = drawFieldLine(g2, x, y, w, "PRECIO:", "$" + vPrecio);
        y = drawFieldLine(g2,x,y,w,"APLICANDO UN DESCUENTO DEL:", vDesc + "%");
        y = drawFieldLine(g2,x,y,w,"PRECIO A PAGAR:", "$" + vPagar);
        
        // Medidas
        y += 6; // espacio
        g2.drawString("MEDIDAS CORPORALES AL DIA DE HOY:", x, y + g2.getFontMetrics().getAscent());
        y += 18;
        y = drawThreeColsLine(g2, x, y, w, 
            "C.BUSTO:", vBusto, 
            "C.CINTURA:", vCintura, 
            "C.CADERA:", vCadera);
        
        y += 18; // Espacio antes de las cláusulas

        // === 3. Cláusulas ===
        g2.setFont(bodyFont); // Volver a fuente normal
        final String clausulasRenderizadas = renderMemo(baseTexto, vars);
        // Dibuja el texto de la BD (que ahora SÓLO tiene cláusulas)
        String[] parrafos = clausulasRenderizadas.split("\n");
        for (String p : parrafos) {
            if (p.trim().isEmpty()) continue;
            y = drawWrappedSimple(g2, p.trim(), x, y, w);
            y += 6; // Espacio entre párrafos
        }
        
        y += 36; // Espacio grande antes de la firma

        // === 4. Firma ===
        // === 4. Firma ===
        g2.setFont(labelFont);
        
        // Dibuja "ESTOY DE ACUERDO" SIN línea
        g2.drawString(P5_ACUERDO, x, y + g2.getFontMetrics().getAscent());
        y += 18; // Avanza una línea
        
        y += 36; // Espacio grande antes de la firma
        
        // Dibuja "NOMBRE Y FIRMA" CON línea
        y = drawFieldLine(g2, x, y, w, P6_FIRMA, "");
        
        return Printable.PAGE_EXISTS;
    };
}
// Empaqueta "ticket + condiciones" en un solo Printable
private static Printable combinarEnDosPaginas(Printable p0, Printable p1){
    return (g, pf, pageIndex) -> {
        if (pageIndex == 0) return p0.print(g, pf, 0);
        if (pageIndex == 1) return p1.print(g, pf, 0);
        return Printable.NO_SUCH_PAGE;
    };
}

/* =========================
   IMPRESIÓN (reusado de contado)
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
// ---------- Printable para hoja de condiciones ----------
private Printable construirPrintableCondiciones(
        EmpresaInfo emp, String memoTexto, String folioTxt, String clienteNombre) {

    final String texto   = (memoTexto == null) ? "" : memoTexto;
    final String folio   = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;
    final String cliente = (clienteNombre == null) ? "" : clienteNombre;

    return (graphics, pf, pageIndex) -> {
        if (pageIndex > 0) return Printable.NO_SUCH_PAGE;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setColor(Color.BLACK);
        g2.translate(pf.getImageableX(), pf.getImageableY());

        final int MARGIN = 36;
        int x = MARGIN;
        int y = MARGIN;
        int w = (int) Math.round(pf.getImageableWidth()) - (MARGIN * 2);

        Font fTitle = g2.getFont().deriveFont(Font.BOLD, 14f);
        Font fText  = g2.getFont().deriveFont(10.5f);
        Font fSmall = g2.getFont().deriveFont(9f);

        // Título
        g2.setFont(fTitle);
        String titulo = "CONDICIONES DE RENTA / COMPRA";
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int cx = x + (w - fm.stringWidth(titulo)) / 2;
        g2.drawString(titulo, cx, y);
        y += 22;

        // Nombre de la empresa (si existe)
        g2.setFont(fSmall);
        String nomEmp = (emp != null && emp.razonSocial != null && !emp.razonSocial.isBlank())
                ? emp.razonSocial
                : "";
        if (!nomEmp.isEmpty()) {
            int wEmp = g2.getFontMetrics().stringWidth(nomEmp);
            g2.drawString(nomEmp, x + (w - wEmp) / 2, y);
            y += 16;
        }

        // Folio y cliente
        g2.drawString("Nota / Folio: " + folio, x, y);
        if (!cliente.isBlank()) {
            String sCli = "Cliente: " + cliente;
            int wCli = g2.getFontMetrics().stringWidth(sCli);
            g2.drawString(sCli, x + w - wCli, y);
        }
        y += 20;

        // Cuerpo del texto (respetando saltos de línea del formato)
        g2.setFont(fText);
        int lineHeight = g2.getFontMetrics().getHeight();
        for (String linea : texto.split("\\R")) {
            String ln = (linea == null) ? "" : linea;
            if (ln.isEmpty()) {
                y += lineHeight;
            } else {
                g2.drawString(ln, x, y);
                y += lineHeight;
            }
        }

        // Área de firma del cliente
        y += 40;
        int lineaWidth = 260;
        int xLinea = x + (w - lineaWidth) / 2;
        g2.drawLine(xLinea, y, xLinea + lineaWidth, y);
        y += 14;

        String lblFirma = "FIRMA DEL CLIENTE";
        int wFirma = g2.getFontMetrics().stringWidth(lblFirma);
        g2.drawString(lblFirma, x + (w - wFirma) / 2, y);

        return Printable.PAGE_EXISTS;
    };
}

// ---------- Printable empresarial (márgenes seguros y obsequios) ----------
private Printable construirPrintableEmpresarial(
        EmpresaInfo emp, Nota n, List<NotaDetalle> dets, PagoFormas p,
        LocalDate fechaEntregaMostrar, LocalDate fechaEventoMostrar,
        String asesorNombre, String folioTxt,
        String clienteNombreFallback, String tel2Fallback, String observacionesTexto,
        int cajeraCodigo, String cajeraNombre) {

    final String tel1      = (n.getTelefono() == null) ? "" : n.getTelefono();
    final String fechaHoy  = getFechaVentaEfectiva().format(MX);
    final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaEntregaMostrar.format(MX);
    final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaEventoMostrar.format(MX);
    final double total     = (n.getTotal() == null) ? 0d : n.getTotal();

    final double tc = (p.getTarjetaCredito()   == null) ? 0d : p.getTarjetaCredito();
    final double td = (p.getTarjetaDebito()    == null) ? 0d : p.getTarjetaDebito();
    final double am = (p.getAmericanExpress()  == null) ? 0d : p.getAmericanExpress();
    final double tr = (p.getTransferencia()    == null) ? 0d : p.getTransferencia();
    final double dp = (p.getDeposito()         == null) ? 0d : p.getDeposito();
    final double ef = (p.getEfectivo()         == null) ? 0d : p.getEfectivo();
    final double devolucion = (p.getDevolucion() == null) ? 0d : p.getDevolucion();  // Asegúrate de incluir la devolución

    final double anticipo = tc + td + am + tr + dp + ef + devolucion;
    final double saldo    = (n.getSaldo() != null) ? n.getSaldo() : Math.max(0d, total - anticipo);
    

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
    final String fCajeraCodigo = (cajeraCodigo == 0 ? "0" : String.valueOf(cajeraCodigo));
    final String fCajeraNombre = (cajeraNombre == null ? "" : cajeraNombre.trim());

    final String fCliNombre  = cliNombre;
    final String fCliTel2    = cliTel2;
    final String fCliPrueba1 = cliPrueba1;
    final String fCliPrueba2 = cliPrueba2;

    java.util.List<String> tmp = null;
    try { tmp = new java.util.ArrayList<>(VentaCreditoPanel.this.obsequiosSel); } catch (Throwable ignore) {}
    final java.util.List<String> obsequiosPrint =
            (tmp == null) ? java.util.Collections.emptyList() : tmp;

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

            yy = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Tel: ", emp.telefono),
                    labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

            // ===== Folio arriba derecha =====
            g2.setFont(fH1);
            rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

            // ===== Columna de contacto a la derecha (correo, web, IG/FB/TikTok) =====
            final int rightColW = 120;               // ancho de la columna derecha
            final int xRight    = x + w - rightColW; // X de la columna
            int yRightHdr       = y + 22;            // un poco debajo del folio

            g2.setFont(fSmall);

            // 1) Correo y Web
            yRightHdr = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRightHdr, rightColW);
            yRightHdr = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRightHdr, rightColW);

            // 2) Redes con ícono + usuario (16px)
            BufferedImage igIcon = loadIcon("instagram.png");
            BufferedImage fbIcon = loadIcon("facebook.png");
            BufferedImage ttIcon = loadIcon("tiktok.png");

            yRightHdr = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRightHdr, rightColW, 12, 6);
            yRightHdr = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRightHdr, rightColW, 12, 6);
            yRightHdr = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRightHdr, rightColW, 12, 6);

            // Altura real del encabezado (máximo entre bloque izquierdo y columna derecha)
            int leftBlockH  = Math.max(yy - y, headerH);
            int rightBlockH = Math.max(0, yRightHdr - y);
            int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;
            int afterTail   = y + usedHeader;

            // ===== Título =====
            g2.setFont(fTitle);
            center(g2, "NOTA DE VENTA CRÉDITO", x, w, afterTail + 14);
            y = afterTail + 32;

            // ===== Datos del cliente (sin cambios) =====
            g2.setFont(fSection);
            g2.drawString("Datos del cliente", x, y);
            y += 13;

            final int gapCols = 24;
            final int leftW   = (w - gapCols) / 2;
            final int rightW  = w - gapCols - leftW;

            int yLeft  = y;
            int yRight = y;

            g2.setFont(fText);
            yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(fCliNombre)), x, yLeft, leftW);
            yLeft  = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Teléfono: ", safe(tel1)),
                    labelIf("Teléfono 2: ", safe(fCliTel2))), x, yLeft + 2, leftW);
            if (!medidasFmt.isBlank()) yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);

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

            // ===== Tabla artículos (sin cambios) =====
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
                String detalle = (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty() ? d.getCodigoArticulo() + " · " : "")
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

            // === Observaciones (justo después de los artículos) ===
if (observacionesTexto != null && !observacionesTexto.isBlank()) {
    y += 6; g2.drawLine(x, y, x + w, y); y += 16;
    g2.setFont(fSection);
    g2.drawString("Observaciones", x, y);
    y += 12;
    g2.setFont(fText);
    y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
}

// Línea divisoria después de observaciones
y += 6; g2.drawLine(x, y, x + w, y); y += 16;

            // Totales (sin cambios)
            g2.setFont(fH1);
            rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y); y += 22;

            g2.setFont(fText);
            rightAlign(g2, "Anticipo: $" + fmt2(anticipo), x, w, y); y += 14;

            g2.setFont(fH1);
            rightAlign(g2, "SALDO RESTANTE: $" + fmt2(saldo), x, w, y); y += 22;
            // === Cantidad pagada con letra (CRÉDITO: usa el ANTICIPO) ===
            g2.setFont(fText);
            java.math.BigDecimal bdAnticipo = java.math.BigDecimal
                    .valueOf(anticipo)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            String pagadoLetra = numeroALetras(bdAnticipo.doubleValue());
            int anchoLetras = w - 230;                    // deja margen para el bloque derecho
            int yInicioTotales = y - (24 + 14 + 22);      // altura donde empezó el bloque de totales
            drawWrapped(g2, "Cantidad pagada con letra: " + pagadoLetra, x, yInicioTotales + 2, anchoLetras);

            // Pagos (sin cambios)
            g2.setFont(fSection);
            g2.drawString("Pagos", x, y); y += 12;
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

            // Obsequios (solo Código + Obsequio, texto ancho)
if (obsequiosPrint != null && !obsequiosPrint.isEmpty()) {
    y += 10;
    g2.drawLine(x, y, x + w, y); 
    y += 16;

    g2.setFont(fSection);
    g2.drawString("Obsequios incluidos", x, y);
    y += 12;

    g2.setFont(fText);

    final int gap2    = 16;
    final int colCodW = 70;                  // Código pequeño
    final int colNomW = w - colCodW - gap2;  // todo lo demás para "Obsequio"

    final int xCod = x;
    final int xNom = xCod + colCodW + gap2;

    g2.drawString("Código",   xCod, y);
    g2.drawString("Obsequio", xNom, y);
    y += 10; 
    g2.drawLine(x, y, x + w, y); 
    y += 14;

    // ---- Precargar detalles de TODOS los obsequios en un solo query (igual que antes)
    java.util.List<String> codigos = new java.util.ArrayList<>();
    for (String raw : obsequiosPrint) {
        if (raw == null) continue;
        String codigo = raw.trim();
        if (!codigo.isEmpty()) {
            codigos.add(codigo);
        }
    }

    java.util.Map<String, String[]> info = new java.util.HashMap<>();
    if (!codigos.isEmpty()) {
        StringBuilder sql = new StringBuilder(
            "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
            "FROM InventarioObsequios WHERE codigo_articulo IN ("
        );
        for (int i = 0; i < codigos.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');

        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {

            for (int i = 0; i < codigos.size(); i++) {
                ps.setString(i + 1, codigos.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String c      = rs.getString("codigo_articulo");
                    String nombre = safe(rs.getString("articulo"));
                    String marca  = safe(rs.getString("marca"));
                    String modelo = safe(rs.getString("modelo"));
                    String talla  = safe(rs.getString("talla"));
                    String color  = safe(rs.getString("color"));
                    info.put(c, new String[]{nombre, marca, modelo, talla, color});
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---- Pintar filas usando SOLO "Código" + descripción de Obsequio
    for (String raw : obsequiosPrint) {
        if (raw == null || raw.trim().isEmpty()) continue;

        String codigoTxt = raw.trim();
        String descripcion = "";

        String[] row = info.get(codigoTxt);
        if (row != null) {
            String nombre = row[0];
            String marca  = row[1];
            String modelo = row[2];
            String talla  = row[3];
            String color  = row[4];

            StringBuilder desc = new StringBuilder();
            if (!nombre.isBlank()) {
                desc.append(nombre);
            }

            // Marca + modelo entre paréntesis (opcional)
            String mm = "";
            if (!marca.isBlank()) mm = marca;
            if (!modelo.isBlank()) {
                if (!mm.isEmpty()) mm += " ";
                mm += modelo;
            }
            if (!mm.isEmpty()) {
                if (desc.length() > 0) desc.append(" ");
                desc.append('(').append(mm).append(')');
            }

            // Talla y color en la misma frase, también dentro del mismo campo
            if (!talla.isBlank()) {
                if (desc.length() > 0) desc.append(" ");
                desc.append("Talla ").append(talla);
            }
            if (!color.isBlank()) {
                if (desc.length() > 0) desc.append(" ");
                desc.append("Color ").append(color);
            }

            descripcion = desc.toString();
        }

        int yCod = drawWrapped(g2, "• " + codigoTxt, xCod, y, colCodW);
        int yNom = drawWrapped(g2, descripcion,      xNom, y, colNomW);

        y = Math.max(yCod, yNom);
    }
}


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

        // ========= Helpers (tus existentes + los de íconos) =========
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

        // === Íconos (override en ./icons/, fallback a /icons/ del classpath) ===
        private BufferedImage loadIcon(String pathOrName) {
            try {
                String name = pathOrName;
                int slash = name.lastIndexOf('/');
                if (slash >= 0) name = name.substring(slash + 1);

                // 1) ./icons/<name>
                java.nio.file.Path override = java.nio.file.Paths.get(System.getProperty("user.dir"), "icons", name);
                if (java.nio.file.Files.exists(override)) {
                    javax.imageio.ImageIO.setUseCache(false);
                    try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                }

                // 2) /icons/<name> en classpath
                String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                try (java.io.InputStream in = VentaCreditoPanel.class.getResourceAsStream(cpPath)) {
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
// === DIÁLOGO: aplicar Notas de Devolución (DV) ===
    private void abrirDialogoAplicarDV() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (tel == null || tel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Captura primero el teléfono del cliente.", "Atención", JOptionPane.WARNING_MESSAGE);
            txtTelefono.requestFocus();
            return;
        }

        // DV disponibles del cliente
        List<Controlador.NotasDVDAO.DVDisponible> disponibles;
        try {
            disponibles = new Controlador.NotasDVDAO().listarDisponiblesPorTelefono(tel);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudieron consultar DV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (disponibles == null || disponibles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay Notas de Devolución disponibles para este cliente.");
            return;
        }

        // Saldos actuales para sugerir montos
        double total = parseMoney(txtTotal.getText());
        double anticipo = parseMoney(txtTC.getText()) + parseMoney(txtTD.getText())
                        + parseMoney(txtAMX.getText()) + parseMoney(txtTRF.getText())
                        + parseMoney(txtDEP.getText()) + parseMoney(txtEFE.getText());
        double yaDV = dvAplicadas.stream().mapToDouble(p -> p.monto).sum();
        double restante = Math.max(0.0, total - anticipo - yaDV);

        // Mapa de DV ya aplicadas (para prellenar)
        java.util.Map<Integer, Double> pre = new java.util.HashMap<>();
        for (Modelo.PagoDV p : dvAplicadas) pre.put(p.numeroNotaDV, p.monto);

        // Modelo de tabla
        String[] cols = {"Usar", "Folio", "Origen", "DV base", "Aplicado", "Disponible", "Monto a usar"};
        javax.swing.table.DefaultTableModel tm = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override public Class<?> getColumnClass(int c) {
                return c==0 ? Boolean.class : Object.class;
            }
            @Override public boolean isCellEditable(int r, int c) {
                return c==0 || c==6; // checkbox y monto
            }
        };

        // Cargar filas
        for (Controlador.NotasDVDAO.DVDisponible dv : disponibles) {
            double disp = Math.max(0.0, dv.disponible);
            double sugerido = pre.containsKey(dv.numeroNotaDV)
                    ? Math.min(pre.get(dv.numeroNotaDV), disp)
                    : Math.min(disp, restante);
            // Evita negativos en lote
            if (restante <= 0) sugerido = 0.0;
            tm.addRow(new Object[]{
                    sugerido > 0.0,                       // usar?
                    dv.folio,
                    dv.origenTipo,
                    String.format("%.2f", dv.montoBase),
                    String.format("%.2f", dv.aplicado),
                    String.format("%.2f", disp),
                    String.format("%.2f", sugerido)
            });
        }

        JTable t = new JTable(tm);
        t.setRowHeight(24);
        // Alineación a la derecha para números
        javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        t.getColumnModel().getColumn(3).setCellRenderer(right);
        t.getColumnModel().getColumn(4).setCellRenderer(right);
        t.getColumnModel().getColumn(5).setCellRenderer(right);
        t.getColumnModel().getColumn(6).setCellRenderer(right);
        // Editor numérico (reusa tu filtro)
        {
            JTextField ed = new JTextField();
            ((AbstractDocument) ed.getDocument()).setDocumentFilter(onlyDecimal());
            t.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(ed));
        }

        // Panel y diálogo
        JPanel wrap = new JPanel(new BorderLayout(8,8));
        wrap.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        wrap.add(new JLabel("Selecciona las DV y captura el monto a usar (máx. el 'Disponible')."), BorderLayout.NORTH);
        wrap.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton bOk = new JButton("Aplicar");
        JButton bCancel = new JButton("Cancelar");
        south.add(bCancel); south.add(bOk);
        wrap.add(south, BorderLayout.SOUTH);

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Aplicar Notas de Devolución", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setContentPane(wrap);
        dlg.setSize(760, 420);
        dlg.setLocationRelativeTo(this);

        bCancel.addActionListener(_e -> dlg.dispose());
        bOk.addActionListener(_e -> {
            // Validar y tomar selección
            double totalSel = 0.0;
            java.util.List<Modelo.PagoDV> nuevos = new java.util.ArrayList<>();

            for (int i = 0; i < tm.getRowCount(); i++) {
                boolean usar = Boolean.TRUE.equals(tm.getValueAt(i, 0));
                if (!usar) continue;

                String folio = String.valueOf(tm.getValueAt(i, 1));
                double disponibleRow = parseMoney(tm.getValueAt(i, 5));
                double montoRow = parseMoney(tm.getValueAt(i, 6));

                if (montoRow <= 0.0) continue;
                if (montoRow - disponibleRow > 0.005) {
                    JOptionPane.showMessageDialog(dlg, "El monto a usar no puede exceder el disponible (folio: " + folio + ").");
                    return;
                }

                // Buscar numero_nota_dv de la fila i
                Controlador.NotasDVDAO.DVDisponible dv = disponibles.get(i);
                nuevos.add(new Modelo.PagoDV(dv.numeroNotaDV, dv.folio, montoRow));
                totalSel += montoRow;
            }

            // Validación contra el total (no exceder)
            double totalActual = parseMoney(txtTotal.getText());
            double pagosSinDV  = parseMoney(txtTC.getText()) + parseMoney(txtTD.getText())
                               + parseMoney(txtAMX.getText()) + parseMoney(txtTRF.getText())
                               + parseMoney(txtDEP.getText()) + parseMoney(txtEFE.getText());
            if (pagosSinDV + totalSel - totalActual > 0.005) {
                JOptionPane.showMessageDialog(dlg, "La suma de DV seleccionadas excede el total de la venta.\n" +
                                                     "Reduce el monto a usar.");
                return;
            }

            // Aceptar
            dvAplicadas = nuevos;
            txtMontoDV.setText(String.format("%.2f", totalSel));
            txtMontoDV.addActionListener(e -> validarSumaPagos());
            validarSumaPagos(); // refresca tooltips/sumas
            dlg.dispose();
        });

        dlg.setVisible(true);
    }
private void actualizarFacturaBadge() {
    if (lbFacturaBadge == null) return;
    if (facturaDraft == null) {
        lbFacturaBadge.setText("—");
        lbFacturaBadge.setForeground(new Color(100,100,100));
    } else {
        lbFacturaBadge.setText("CAPTURA");
        lbFacturaBadge.setForeground(new Color(0,128,0));
    }
}

private void abrirDialogoFactura() {
    DlgFactura.CapturaFactura initial = (facturaDraft == null) ? null : facturaDraft;
    Window owner = SwingUtilities.getWindowAncestor(this);
    DlgFactura dlg = (owner instanceof Frame f)
            ? new DlgFactura(f, initial)
            : new DlgFactura((Frame) null, initial);
    DlgFactura.CapturaFactura res = dlg.showDialog();
    if (dlg.fueLimpiado()) {
        facturaDraft = null;
    } else if (res != null) {
        facturaDraft = res;
    }
    actualizarFacturaBadge();
}
// ================== DLG FACTURA (captura básica previa al timbrado) ==================
static class DlgFactura extends JDialog {
    static class CapturaFactura {
        String persona;  // "PF" o "PM"
        String rfc;
        String regimen;  // 3-4 chars
        String usoCfdi;  // G03, CP01, S01, ...
        String correo;   // opcional
    }

    private boolean limpiado = false;
    boolean fueLimpiado(){ return limpiado; }

    private CapturaFactura result;

    private final JComboBox<String> cbPersona = new JComboBox<>(new String[]{"PF (Persona física)","PM (Persona moral)"});
    private final JTextField tfRFC = new JTextField();
    private final JComboBox<String> cbRegimen = new JComboBox<>(new String[]{
            "", "601","603","605","606","607","608","609","610","611","612","614","616","620","621","622","623","624","625","626"
    });
    private final JComboBox<String> cbUso = new JComboBox<>(new String[]{
            "", "G01","G02","G03","I01","I02","I03","I04","I05","I06","I07","D01","D02","D03","CP01","CN01","S01"
    });
    private final JTextField tfRegimenLibre = new JTextField(); // permite editar manual
    private final JTextField tfUsoLibre = new JTextField();
    private final JTextField tfCorreo = new JTextField();

    DlgFactura(Frame owner, CapturaFactura init) {
        super(owner, "Datos para facturar", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1; int y=0;

        addCell(p,c,0,y,new JLabel("Persona:"),1,false); addCell(p,c,1,y,cbPersona,1,true); y++;
        addCell(p,c,0,y,new JLabel("RFC:"),1,false);     addCell(p,c,1,y,tfRFC,1,true);     y++;

        addCell(p,c,0,y,new JLabel("Régimen fiscal:"),1,false);
        JPanel pr = new JPanel(new BorderLayout(6,0));
        pr.add(cbRegimen, BorderLayout.WEST);
        pr.add(tfRegimenLibre, BorderLayout.CENTER);
        addCell(p,c,1,y,pr,1,true); y++;

        addCell(p,c,0,y,new JLabel("Uso del CFDI:"),1,false);
        JPanel pu = new JPanel(new BorderLayout(6,0));
        pu.add(cbUso, BorderLayout.WEST);
        pu.add(tfUsoLibre, BorderLayout.CENTER);
        addCell(p,c,1,y,pu,1,true); y++;

        addCell(p,c,0,y,new JLabel("Correo:"),1,false); addCell(p,c,1,y,tfCorreo,1,true); y++;

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btGuardar = new JButton("Guardar");
        JButton btLimpiar = new JButton("Quitar captura");
        JButton btCancelar= new JButton("Cancelar");
        south.add(btLimpiar); south.add(btCancelar); south.add(btGuardar);

        getContentPane().add(p, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        // Sincroniza combos con campos libres
        cbRegimen.addActionListener(_e -> {
            String v = (String) cbRegimen.getSelectedItem();
            tfRegimenLibre.setText(v == null ? "" : v);
        });
        cbUso.addActionListener(_e -> {
            String v = (String) cbUso.getSelectedItem();
            tfUsoLibre.setText(v == null ? "" : v);
        });

        // Cargar inicial
        if (init != null) {
            cbPersona.setSelectedIndex("PM".equalsIgnoreCase(init.persona) ? 1 : 0);
            tfRFC.setText(init.rfc == null ? "" : init.rfc);
            tfRegimenLibre.setText(init.regimen == null ? "" : init.regimen);
            tfUsoLibre.setText(init.usoCfdi == null ? "" : init.usoCfdi);
            tfCorreo.setText(init.correo == null ? "" : init.correo);
        }

        btGuardar.addActionListener(_e -> {
            String persona = (cbPersona.getSelectedIndex()==1) ? "PM" : "PF";
            String rfc = tfRFC.getText().trim().toUpperCase();
            String regimen = tfRegimenLibre.getText().trim().toUpperCase();
            String uso = tfUsoLibre.getText().trim().toUpperCase();
            String correo = tfCorreo.getText().trim();

            // Validaciones mínimas
            if (persona.equals("PF") && rfc.length()!=13) {
                JOptionPane.showMessageDialog(this,"El RFC de persona física debe tener 13 caracteres.");
                return;
            }
            if (persona.equals("PM") && rfc.length()!=12) {
                JOptionPane.showMessageDialog(this,"El RFC de persona moral debe tener 12 caracteres.");
                return;
            }
            if (regimen.length() < 3 || regimen.length() > 4) {
                JOptionPane.showMessageDialog(this,"Régimen debe ser de 3–4 caracteres (ej. 601).");
                return;
            }
            if (uso.length() < 3 || uso.length() > 4) {
                JOptionPane.showMessageDialog(this,"Uso de CFDI debe ser de 3–4 caracteres (ej. G03, CP01).");
                return;
            }
            if (!correo.isBlank() && !correo.contains("@")) {
                JOptionPane.showMessageDialog(this,"Correo inválido.");
                return;
            }

            CapturaFactura cf = new CapturaFactura();
            cf.persona = persona;
            cf.rfc = rfc;
            cf.regimen = regimen;
            cf.usoCfdi = uso;
            cf.correo = correo.isBlank()? null : correo;
            result = cf;
            dispose();
        });

        btLimpiar.addActionListener(_e -> {
            limpiado = true;
            result = null;
            dispose();
        });

        btCancelar.addActionListener(_e -> {
            result = null; dispose();
        });

        setSize(520, 300);
        setLocationRelativeTo(owner);
    }

    DlgFactura.CapturaFactura showDialog() { setVisible(true); return result; }

    // pequeño helper local para grid
    private static void addCell(JPanel p, GridBagConstraints c, int x, int y, JComponent comp, int span, boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }
}
@Override
public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
    if (aFlag) {
        cargarAsesores();   // recarga lista desde BD cada vez que entras al panel
    }
}
private static int drawWrappedSimple(Graphics2D g2, String text, int x, int y, int maxWidth) {
    if (text == null || text.trim().isEmpty()) return y;  // Se asegura que no intente dibujar texto vacío
    java.awt.FontMetrics fm = g2.getFontMetrics();
    String[] words = text.split("\\s+");
    StringBuilder line = new StringBuilder();
    
    for (String word : words) {
        String tryLine = (line.length() == 0 ? word : line + " " + word);
        if (fm.stringWidth(tryLine) <= maxWidth) {
            line.setLength(0);  // Restablece la línea
            line.append(tryLine);
        } else {
            g2.drawString(line.toString(), x, y);  // Dibuja la línea
            y += fm.getHeight();  // Aumenta la altura de la línea
            line.setLength(0);  // Restablece la línea
            line.append(word);  // Inicia la nueva línea con la palabra actual
        }
    }
    
    if (line.length() > 0) {
        g2.drawString(line.toString(), x, y);  // Dibuja la última línea
    }

    return y + fm.getHeight();  // Asegura que se agregue el espacio adecuado para la última línea
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
// ===== Tarjetas por artículo (¼ de hoja carta) =====
private static class TarjetaVentaData {
    String razonSocial;
    String cliente;
    String fechaEvento;
    String fechaCompra;
    String asesor;
    String codigoArticulo;
    String articulo;
    String marca;
    String modelo;
    String talla;
    String color;
    String obsequios;
    String observaciones;
}

private static class TarjetasVentaPrinter implements Printable {

    private final java.util.List<TarjetaVentaData> tarjetas;

    TarjetasVentaPrinter(java.util.List<TarjetaVentaData> tarjetas) {
        this.tarjetas = tarjetas;
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        int tarjetasPorPagina = 4; // 2 x 2
        int start = pageIndex * tarjetasPorPagina;
        if (start >= tarjetas.size()) {
            return NO_SUCH_PAGE;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(
                java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        double iw = pf.getImageableWidth();
        double ih = pf.getImageableHeight();
        double ix = pf.getImageableX();
        double iy = pf.getImageableY();

        double cardW = iw / 2.0;
        double cardH = ih / 2.0;

        for (int i = 0; i < tarjetasPorPagina; i++) {
            int idx = start + i;
            if (idx >= tarjetas.size()) break;

            TarjetaVentaData t = tarjetas.get(idx);
            int row = i / 2;
            int col = i % 2;
            double x = ix + col * cardW;
            double y = iy + row * cardH;

            dibujarTarjeta(g2, t, x, y, cardW, cardH);
        }

        return PAGE_EXISTS;
    }

    private void dibujarTarjeta(Graphics2D g2, TarjetaVentaData t,
                                double x, double y, double w, double h) {

        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int iw = (int) Math.round(w);
        int ih = (int) Math.round(h);

        // Marco
        g2.drawRect(ix, iy, iw, ih);

        int margin = 10;
        int lineH  = 12;

        Font base = g2.getFont();
        Font titleFont = base.deriveFont(Font.BOLD, 12f);
        Font textFont  = base.deriveFont(10f);

        // RAZÓN SOCIAL centrada
        g2.setFont(titleFont);
        String rs = safe(t.razonSocial);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int titleX = ix + (iw - fm.stringWidth(rs)) / 2;
        int yCursor = iy + margin + fm.getAscent();
        g2.drawString(rs, titleX, yCursor);

        // Resto de texto
        g2.setFont(textFont);
        yCursor += lineH * 2;
        int textX = ix + margin;

        g2.drawString("Cliente: " + safe(t.cliente), textX, yCursor);          yCursor += lineH;
        g2.drawString("Fecha Evento: " + safe(t.fechaEvento), textX, yCursor); yCursor += lineH;
        g2.drawString("Fecha Compra: " + safe(t.fechaCompra), textX, yCursor); yCursor += lineH;
        g2.drawString("Asesor: " + safe(t.asesor), textX, yCursor);            yCursor += lineH;

        yCursor += lineH / 2;
        g2.drawString("Código Artículo: " + safe(t.codigoArticulo), textX, yCursor); yCursor += lineH;
        g2.drawString("Artículo: " + safe(t.articulo), textX, yCursor);               yCursor += lineH;
        g2.drawString("Marca: " + safe(t.marca), textX, yCursor);                     yCursor += lineH;
        g2.drawString("Modelo: " + safe(t.modelo), textX, yCursor);                   yCursor += lineH;
        g2.drawString("Talla: " + safe(t.talla) + "   Color: " + safe(t.color),
                      textX, yCursor);
        yCursor += lineH * 2;

        // Obsequios
g2.drawString("Obsequios:", textX, yCursor);
yCursor += lineH;
String ob = safe(t.obsequios);
if (!ob.isEmpty()) {
    String[] lineas = ob.split("\\r?\\n");
    for (String linea : lineas) {
        if (linea.trim().isEmpty()) continue;
        yCursor = drawWrappedSimple(
                g2,
                linea.trim(),
                textX + 10,
                yCursor,
                iw - 2 * margin - 10
        );
    }
} else {
    yCursor += lineH;
}


        // Observaciones
        yCursor += lineH / 2;
        g2.drawString("Observaciones:", textX, yCursor);
        yCursor += lineH;
        String obs = safe(t.observaciones);
        if (!obs.isEmpty()) {
            yCursor = drawWrappedSimple(g2, obs, textX + 10, yCursor, iw - 2 * margin - 10);
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    static void imprimir(Component parent, java.util.List<TarjetaVentaData> tarjetas) throws PrinterException {
        if (tarjetas == null || tarjetas.isEmpty()) return;
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new TarjetasVentaPrinter(tarjetas));
        if (!job.printDialog()) return;
        job.print();
    }
}
// ¿Este artículo cuenta como "Vestido"?
private boolean esArticuloVestido(String articulo) {
    if (articulo == null) return false;
    String s = articulo.toLowerCase();
    return s.contains("vestido");
}

private java.util.List<TarjetaVentaData> construirTarjetasVenta(
        EmpresaInfo emp,
        String clienteNombre,
        LocalDate fechaEvento,
        LocalDate fechaCompra,
        String asesorNombre,
        java.util.List<NotaDetalle> detsPrint,
        java.util.List<String> obsequiosCodigos,
        String observaciones) {

    java.util.List<TarjetaVentaData> tarjetas = new java.util.ArrayList<>();

    String razon = (emp.razonSocial != null && !emp.razonSocial.isBlank())
            ? emp.razonSocial
            : (emp.nombreFiscal != null ? emp.nombreFiscal : "");

    String fechaEventoStr = (fechaEvento == null) ? "" : fechaEvento.format(MX);
    String fechaCompraStr = (fechaCompra == null) ? "" : fechaCompra.format(MX);

    // ======================== O B S E Q U I O S ========================
    String obsequiosTexto = "";
    if (obsequiosCodigos != null && !obsequiosCodigos.isEmpty()) {
        // normalizar códigos
        java.util.List<String> codigos = new java.util.ArrayList<>();
        for (String raw : obsequiosCodigos) {
            if (raw == null) continue;
            String c = raw.trim();
            if (!c.isEmpty()) codigos.add(c);
        }

        if (!codigos.isEmpty()) {
            java.util.Map<String,String> descPorCodigo = new java.util.HashMap<>();

            StringBuilder sql = new StringBuilder(
                    "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
                    "FROM InventarioObsequios WHERE codigo_articulo IN ("
            );
            for (int i = 0; i < codigos.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(')');

            try (Connection cn = Conexion.Conecta.getConnection();
                 PreparedStatement ps = cn.prepareStatement(sql.toString())) {

                for (int i = 0; i < codigos.size(); i++) {
                    ps.setString(i + 1, codigos.get(i));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String c      = rs.getString("codigo_articulo");
                        String nombre = n(rs.getString("articulo"));
                        String marca  = n(rs.getString("marca"));
                        String modelo = n(rs.getString("modelo"));
                        String talla  = n(rs.getString("talla"));
                        String color  = n(rs.getString("color"));

                        StringBuilder desc = new StringBuilder();
                        if (!nombre.isBlank()) {
                            desc.append(nombre);
                        }

                        // Marca / modelo opcional entre paréntesis
                        String mm = "";
                        if (!marca.isBlank()) mm = marca;
                        if (!modelo.isBlank()) {
                            if (!mm.isEmpty()) mm += " ";
                            mm += modelo;
                        }
                        if (!mm.isEmpty()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append('(').append(mm).append(')');
                        }

                        if (!talla.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Talla ").append(talla);
                        }
                        if (!color.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Color ").append(color);
                        }

                        descPorCodigo.put(c, desc.toString());
                    }
                }
            } catch (SQLException ex) {
                // si truena, al menos se imprimen los códigos solos
            }

            StringBuilder sb = new StringBuilder();
            for (String c : codigos) {
                if (sb.length() > 0) sb.append('\n');
                String desc = descPorCodigo.get(c);
                if (desc == null || desc.isBlank()) {
                    sb.append(c);
                } else {
                    sb.append(c).append(" - ").append(desc);
                }
            }
            obsequiosTexto = sb.toString();
        }
    }
    // ==================== FIN OBSEQUIOS =====================

    String obs = (observaciones == null) ? "" : observaciones;

    for (NotaDetalle d : detsPrint) {
        String art = d.getArticulo();
        int repeticiones = esArticuloVestido(art) ? 2 : 1;   // 2 tarjetas si es Vestido, 1 si no

        for (int i = 0; i < repeticiones; i++) {
            TarjetaVentaData t = new TarjetaVentaData();
            t.razonSocial    = razon;
            t.cliente        = clienteNombre == null ? "" : clienteNombre;
            t.fechaEvento    = fechaEventoStr;
            t.fechaCompra    = fechaCompraStr;
            t.asesor         = asesorNombre == null ? "" : asesorNombre;
            t.codigoArticulo = d.getCodigoArticulo() == null ? "" : d.getCodigoArticulo();
            t.articulo       = art == null ? "" : art;
            t.marca          = d.getMarca() == null ? "" : d.getMarca();
            t.modelo         = d.getModelo() == null ? "" : d.getModelo();
            t.talla          = d.getTalla() == null ? "" : d.getTalla();
            t.color          = d.getColor() == null ? "" : d.getColor();
            t.obsequios      = obsequiosTexto;   // aquí ya va "COD - DESC" por línea
            t.observaciones  = obs;
            tarjetas.add(t);
        }
    }

    return tarjetas;
}

private void imprimirTarjetasVenta(
        EmpresaInfo emp,
        String clienteNombre,
        LocalDate fechaEvento,
        LocalDate fechaCompra,
        String asesorNombre,
        java.util.List<NotaDetalle> detsPrint,
        java.util.List<String> obsequiosCodigos,
        String observaciones) {

    java.util.List<TarjetaVentaData> tarjetas = construirTarjetasVenta(
            emp, clienteNombre, fechaEvento, fechaCompra,
            asesorNombre, detsPrint, obsequiosCodigos, observaciones
    );

    if (tarjetas.isEmpty()) return;

    try {
        TarjetasVentaPrinter.imprimir(this, tarjetas);
    } catch (PrinterException ex) {
        JOptionPane.showMessageDialog(this,
                "Las tarjetas no se pudieron imprimir: " + ex.getMessage(),
                "Impresión de tarjetas", JOptionPane.WARNING_MESSAGE);
    }
}
}