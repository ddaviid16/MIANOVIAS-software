// VentaContadoPanel.java
package Vista;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import Controlador.NotasMemoDAO;

import java.awt.FlowLayout;
import Controlador.FacturaDatosDAO;
import java.util.Locale;


import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import Controlador.ManufacturasDAO;
import Controlador.NotasDAO;
import Controlador.PedidosDAO;
import Controlador.VentaContadoService;
import Controlador.clienteDAO;
import Controlador.EmpresaDAO;

import Modelo.ClienteResumen;
import Modelo.Inventario;
import Modelo.Manufactura;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Modelo.PagoFormas;
import Utilidades.TelefonosUI;
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

    private JButton btFactura;
    private DlgFactura.CapturaFactura facturaDraft; // nulo si el usuario no capturó
    private JLabel lbFacturaBadge;                  // pequeño indicador visual

    
     private JButton btnCambiarFechaVenta;
    private JLabel lblFechaVenta;

    private LocalDate fechaVentaSeleccionada = null;
    private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private JButton btnAplicarDV;
    private java.util.List<Modelo.PagoDV> dvAplicadas = new java.util.ArrayList<>();

    private final Navigator nav;

    private java.util.List<String> obsequiosSel = new java.util.ArrayList<>();
    private JLabel lblObsequios;
        // ===== Usuario en sesión (cajera) =====
    private int cajeraCodigo;
    private String cajeraNombre;



    // ========= Campos cabecera
    private JTextField txtTelefono, txtTelefono2, txtNombreCompleto;
    private JTextField txtFechaEvento, txtFechaPrueba1, txtFechaPrueba2, txtFechaEntrega, txtUltimaNota;
    private JComboBox<Modelo.Asesor> cbAsesor;
    private JButton btnRegistrarCliente;
    
    private JButton btnBuscarCliente;

    // === Control de fecha_evento y fecha_entrega por venta
    private JCheckBox chkUsarFechaCliente;
    private JTextField txtFechaEventoVenta;   // dd-MM-yyyy
    private JTextField txtFechaEntregaVenta;  // dd-MM-yyyy

    // ========= Carrito
    private JTextField txtCod;
    private JTable tb;
    private DefaultTableModel model;

    private final JButton btRegistrarPedido = new JButton("Registrar artículo");
    private final JButton btRegistrarManufactura = new JButton("Registrar modista");

    


    // ========= Totales y pagos
    private JTextField txtSubtotal, txtTotal;
    private JTextField txtTC, txtTD, txtAMX, txtTRF, txtDEP, txtEFE;
    // === Pago con Devolución ===
    private JComboBox<DVOption> cbFolioDV; // Folio de devolución
    private JTextField txtMontoDV;       // Monto de devolución
    private double montoDVAplicado = 0;  // Valor interno aplicado



    private final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Locale LOCALE_ES_MX = Locale.of("es", "MX");
    private static final DateTimeFormatter MX_LARGO =
        DateTimeFormatter.ofPattern("dd-MMMM-yyyy", LOCALE_ES_MX);

/** 29-Noviembre-2025 */
private String fechaLarga(LocalDate fecha) {
    if (fecha == null) return "";
    // "29-noviembre-2025"
    String raw = fecha.format(MX_LARGO);

    int guion1 = raw.indexOf('-');
    int guion2 = raw.lastIndexOf('-');
    if (guion1 <= 0 || guion2 <= guion1) return raw;

    String dia  = raw.substring(0, guion1);
    String mes  = raw.substring(guion1 + 1, guion2);  // "noviembre"
    String anio = raw.substring(guion2 + 1);

    mes = mes.isEmpty()
            ? mes
            : mes.substring(0, 1).toUpperCase(LOCALE_ES_MX) + mes.substring(1);

    return dia + "-" + mes + "-" + anio;
}

    private String lastTelefonoConsultado = null;
    private boolean updatingTable = false;


    private double clamp0a100(double v){ if (v<0) return 0; if (v>100) return 100; return v; }
    public void setCajeraActual(int codigoEmpleado, String nombreCompleto) {
        this.cajeraCodigo = codigoEmpleado;
        this.cajeraNombre = nombreCompleto;
    }

    public VentaContadoPanel(Navigator navigator) {
        this.nav = navigator;
        buildUI();
        cargarAsesores();
        cargarClienteAutoHooks();
    }
    public VentaContadoPanel() { this(null); }

    private void buildUI() {
        setLayout(new BorderLayout());

        // 1) Crear el campo
        txtTelefono = new JTextField();

        // 2) Aplicar formato de teléfono (guiones, filtro, etc.)
        TelefonosUI.instalar(txtTelefono, 10);
        
        // 3) Ya después construyes el panel
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;

        // Fila 0: Teléfono1 y Teléfono2
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
        // Buscar por apellido
        btnBuscarCliente = new JButton("Buscar por nombre o apellido…");
        btnBuscarCliente.addActionListener(_e -> seleccionarClientePorApellido());

        // Misma fila: registrar + buscar
        addCell(top, c, 0, y, btnRegistrarCliente, 1, false);
        addCell(top, c, 1, y, btnBuscarCliente, 1, false);
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
        JPanel pnlRegistrarEspecial = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        // Botón PEDIR
        btRegistrarPedido.setVisible(false);
        btRegistrarPedido.addActionListener(_e -> abrirDialogoPedido());
        addCell(top, c, 0, y, btRegistrarPedido, 1, false);
        
        btRegistrarManufactura.setVisible(false);
        btRegistrarManufactura.addActionListener(_e -> abrirDialogoManufactura());

        pnlRegistrarEspecial.add(btRegistrarPedido);
        pnlRegistrarEspecial.add(btRegistrarManufactura);

        addCell(top, c, 0, y, pnlRegistrarEspecial, 1, false);

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
            String val = (s == null) ? "" : s.trim().toUpperCase();


            
    btRegistrarManufactura.setVisible("MODISTA".equals(val));
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

        JPanel panelTabla = new JPanel(new BorderLayout());
        panelTabla.add(tb.getTableHeader(), BorderLayout.NORTH);
        panelTabla.add(tb, BorderLayout.CENTER);

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

        btnObservaciones = new JButton("Observaciones…");
        btnObservaciones.addActionListener(_e -> editarObservaciones());
        addCell(bottom, d, 1, r, btnObservaciones, 1, false);

        
        JButton btGuardar = new JButton("Registrar venta");
        btGuardar.addActionListener(_e -> guardarVenta());



        // Coloca el botón a la izquierda del Guardar (ajusta columnas si quieres)
        addCell(bottom,d,2,r,btnCondiciones,1,false);
        addCell(bottom,d,3,r,btGuardar,1,false);
        r++;
        lblFechaVenta = new JLabel("Fecha de venta: " + getFechaVentaEfectiva().format(MX));
        btnCambiarFechaVenta = new JButton("Cambiar fecha venta");
        btnCambiarFechaVenta.setVisible(false); // solo se ve con "admin"
        btnCambiarFechaVenta.addActionListener(_e -> cambiarFechaVenta());

        addCell(bottom, d, 2, r, lblFechaVenta, 1, false);
        addCell(bottom, d, 3, r, btnCambiarFechaVenta, 1, false);
        // 1) Panel que contiene todo el formulario
JPanel contenido = new JPanel(new BorderLayout());
contenido.add(top, BorderLayout.NORTH);
contenido.add(panelTabla, BorderLayout.CENTER);
contenido.add(bottom, BorderLayout.SOUTH);

// 2) Scroll vertical para todo
JScrollPane scroll = new JScrollPane(
        contenido,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
);
scroll.getVerticalScrollBar().setUnitIncrement(16);
scroll.setBorder(BorderFactory.createEmptyBorder());

// 3) El panel principal ahora solo tiene el scroll en CENTER
add(scroll, BorderLayout.CENTER);
        cargarAsesores();
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
    ctx.put("FECHA", getFechaVentaEfectiva().format(MX));

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

        if (rs.next()) {
            String texto = rs.getString("texto");

            // 🧹 Normalización y restauración de saltos de línea
            if (texto != null) {
                texto = texto
                        .replace("\\r\\n", "\n")  // por si se guardaron escapados
                        .replace("\\n", "\n")     // interpreta saltos reales
                        .replace("\\r", "\n")
                        .replaceAll("(?<!\\n)([.!?])(?=\\S)", "$1 ") // espacio tras punto
                        .replaceAll("(?<=:)(?=\\S)", " ")            // espacio tras dos puntos
                        .replaceAll("(?<=\\p{Ll})(?=\\p{Lu})", "\n") // salto antes de mayúscula
                        .trim();
            }

            return texto;
        }

    } catch (SQLException ex) {
        System.err.println("Error leyendo condiciones predeterminadas: " + ex.getMessage());
    }

    return ""; // fallback vacío si no hay registro
}


private boolean pedirObservacionesObligatorias() {
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
        return true;
    }
    // Canceló o cerró la ventana
    return false;
}


/** Vista previa/edición antes de imprimir. Devuelve true si el usuario confirmó (OK). */
private boolean editarMemoPrevia(boolean forzarTemplate) {
    Map<String,String> vars = construirVarsDesdeUI();
    String base = (memoEditable == null || memoEditable.isBlank() || forzarTemplate)
            ? obtenerCondicionesPredeterminadas()
            : memoEditable;
    String borrador = renderMemo(base, vars);

    JTextArea area = new JTextArea(borrador, 18, 60);
    area.setLineWrap(true); 
    area.setWrapStyleWord(true);

    int r = JOptionPane.showConfirmDialog(this, new JScrollPane(area),
            "Condiciones de entrega / aceptación",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (r == JOptionPane.OK_OPTION) {
        memoEditable = area.getText().trim();
        if (memoEditable.isEmpty()) memoEditable = null;
        return true;
    }
    return false;
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

// 1) Primero intenta usar un "Vestido" (incluye PEDIDO – Vestido...)
for (NotaDetalle d : dets) {
    if (esArticuloVestido(d.getArticulo())) {
        d0 = d;
        break;
    }
}

// 2) Si no hubo vestido, usa el primer artículo con código (inventario normal)
if (d0 == null) {
    for (NotaDetalle d : dets) {
        if (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty()) {
            d0 = d;
            break;
        }
    }
}

// 3) Si sigue sin haber nada pero la lista no está vacía, usa el primer renglón
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
    final String clausulasTexto = obtenerCondicionesPredeterminadas();
    
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
        final String clausulasRenderizadas = renderMemo(clausulasTexto, vars);
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
// ---- Funciones auxiliares para formateo ----
private static void centerText(Graphics2D g2, String text, int x, int w, int y) {
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int cx = x + (w - fm.stringWidth(text)) / 2;
    g2.drawString(text, cx, y);
}

private static int drawParagraph(Graphics2D g2, String text,
                                 int x, int y, int width, float interline) {
    if (text == null || text.isBlank()) return y;

    java.text.AttributedString attrStr = new java.text.AttributedString(text);
    attrStr.addAttribute(java.awt.font.TextAttribute.FONT, g2.getFont());
    java.text.AttributedCharacterIterator it = attrStr.getIterator();

    java.awt.font.FontRenderContext frc = g2.getFontRenderContext();
    java.awt.font.LineBreakMeasurer measurer =
            new java.awt.font.LineBreakMeasurer(it, frc);

    float wrapWidth = (float) width;
    float drawPosY = (float) y;

    while (measurer.getPosition() < it.getEndIndex()) {
        java.awt.font.TextLayout layout =
                measurer.nextLayout(wrapWidth);

        drawPosY += layout.getAscent();
        layout.draw(g2, x, drawPosY);
        drawPosY += layout.getDescent() + layout.getLeading() + (layout.getAscent() * (interline - 1));
    }

    return (int) drawPosY;
}



// Helpers del printable de condiciones
private static void centerSimple(Graphics2D g2, String s, int x, int w, int y){
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int cx = x + (w - fm.stringWidth(s)) / 2;
    g2.drawString(s, cx, y);
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

// Empaqueta "ticket + condiciones" en un solo Printable
private static Printable combinarEnDosPaginas(Printable p0, Printable p1){
    return (g, pf, pageIndex) -> {
        if (pageIndex == 0) return p0.print(g, pf, 0);
        if (pageIndex == 1) return p1.print(g, pf, 0);
        return Printable.NO_SUCH_PAGE;
    };
}

private static class DVOption {
    final int numeroNotaDV;
    final String folio;
    final double saldoDisponible;

    DVOption(int numeroNotaDV, String folio, double saldoDisponible) {
        this.numeroNotaDV = numeroNotaDV;
        this.folio = folio;
        this.saldoDisponible = saldoDisponible;
    }

    @Override
    public String toString() {
        return folio + " - $" + String.format("%.2f", saldoDisponible);
    }
}


    // ======= Carga de cliente/nota =======
    private void cargarCliente() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (tel == null || tel.isEmpty()) { limpiarInfoCliente(); return; }
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
            n.numero_nota,
            n.folio,
            n.saldo AS saldo_inicial,
            COALESCE(n.saldo - SUM(fp.devolucion), n.saldo) AS saldo_disponible,
            MAX(n.fecha_registro) AS fecha_reciente
        FROM Notas n
        JOIN Devoluciones d 
            ON d.numero_nota_dv = n.numero_nota
        LEFT JOIN Formas_Pago fp
            ON fp.referencia_dv = n.folio
            AND fp.status = 'A'
            AND fp.devolucion IS NOT NULL
        WHERE n.tipo = 'DV'
          AND n.status = 'A'
          AND n.telefono = ?
        GROUP BY n.numero_nota, n.folio, n.saldo
        HAVING COALESCE(n.saldo - SUM(fp.devolucion), n.saldo) > 0
        ORDER BY fecha_reciente DESC
     """)) {

    ps.setString(1, tel);
    try (ResultSet rs = ps.executeQuery()) {
        cbFolioDV.removeAllItems();
        cbFolioDV.addItem(null); // Placeholder para "sin selección"

        while (rs.next()) {
            int numeroNotaDV = rs.getInt("numero_nota");
            String folio = rs.getString("folio");
            double saldoDisp = rs.getDouble("saldo_disponible");

            cbFolioDV.addItem(new DVOption(numeroNotaDV, folio, saldoDisp));
        }
        cbFolioDV.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) setText("--- Pagar con folio de devolución ---");
                return this;
            }
        });
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
    String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
    if (tel == null || tel.isEmpty()) {
        JOptionPane.showMessageDialog(
                this,
                "Ingresa primero el teléfono del cliente.",
                "Atención",
                JOptionPane.WARNING_MESSAGE
        );
        return;
    }

    // Ventana dueña (para que el diálogo quede centrado y modal)
    Window owner = SwingUtilities.getWindowAncestor(this);
    JDialog dlg;

    if (owner instanceof Frame f) {
        dlg = new JDialog(f, "Registro de clientes", Dialog.ModalityType.APPLICATION_MODAL);
    } else if (owner instanceof Dialog d) {
        dlg = new JDialog(d, "Registro de clientes", Dialog.ModalityType.APPLICATION_MODAL);
    } else {
        dlg = new JDialog((Frame) null, "Registro de clientes", Dialog.ModalityType.APPLICATION_MODAL);
    }

    // Panel de clientes con el teléfono prellenado
    ClientesPanel cp = new ClientesPanel(tel);

    // IMPORTANTE: ClientesPanel debe tener este setter y usarlo para cerrar el diálogo al guardar
    cp.setOwnerDialog(dlg);

    dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dlg.getContentPane().setLayout(new BorderLayout());
    dlg.getContentPane().add(cp, BorderLayout.CENTER);
    dlg.pack();
    dlg.setLocationRelativeTo(owner);

    // Bloquea hasta que el usuario cierre el diálogo (guardar o cancelar)
    dlg.setVisible(true);

    // Al cerrar el diálogo:
    // 1) limpiamos el cache del último teléfono consultado
    // 2) llamamos a cargarCliente(), que buscará en BD y llenará los campos si ya existe
    lastTelefonoConsultado = null;
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
        if (chkUsarFechaCliente.isSelected()) {
            return parseFecha(txtFechaEvento.getText());
        } else {
            return parseFecha(txtFechaEventoVenta.getText());
        }
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
            // limpiar campo de código y ocultar botón PEDIR
            txtCod.setText("");
            btRegistrarPedido.setVisible(false);

            dlg.dispose();
        });

        dlg.setVisible(true);
    }

    private void abrirDialogoManufactura() {
    JDialog dlg = new JDialog(
            SwingUtilities.getWindowAncestor(this),
            "Registrar modista",
            Dialog.ModalityType.APPLICATION_MODAL
    );

    JPanel p = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(6, 6, 6, 6);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1;
    int y = 0;

    JTextField tArticulo = new JTextField();
    JTextField tDescripcion = new JTextField();
    JTextField tPrecio = new JTextField();
    ((AbstractDocument) tPrecio.getDocument()).setDocumentFilter(onlyDecimal());
    JTextField tDesc = new JTextField("0");
    ((AbstractDocument) tDesc.getDocument()).setDocumentFilter(onlyDecimal());
    JTextField tFechaEntrega = new JTextField(); // dd-MM-yyyy opcional

    JTextArea tObs = new JTextArea(4, 30);
    tObs.setLineWrap(true);
    tObs.setWrapStyleWord(true);

    addCell(p, c, 0, y, new JLabel("Artículo*:"), 1, false);
    addCell(p, c, 1, y, tArticulo, 1, true); y++;
    addCell(p, c, 0, y, new JLabel("Descripción:"), 1, false);
    addCell(p, c, 1, y, tDescripcion, 1, true); y++;
    addCell(p, c, 0, y, new JLabel("Precio*:"), 1, false);
    addCell(p, c, 1, y, tPrecio, 1, true); y++;
    addCell(p, c, 0, y, new JLabel("% desc.:"), 1, false);
    addCell(p, c, 1, y, tDesc, 1, true); y++;
    addCell(p, c, 0, y, new JLabel("Fecha entrega (dd-MM-yyyy):"), 1, false);
    addCell(p, c, 1, y, tFechaEntrega, 1, true); y++;
    addCell(p, c, 0, y, new JLabel("Observaciones:"), 1, false);
    addCell(p, c, 1, y, new JScrollPane(tObs), 1, true); y++;

    JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton bOk = new JButton("Agregar al carrito");
    JButton bCancel = new JButton("Cancelar");
    south.add(bCancel);
    south.add(bOk);

    dlg.getContentPane().add(p, BorderLayout.CENTER);
    dlg.getContentPane().add(south, BorderLayout.SOUTH);
    dlg.setSize(480, 360);
    dlg.setLocationRelativeTo(this);

    bCancel.addActionListener(_e -> dlg.dispose());
    bOk.addActionListener(_e -> {
        String art = tArticulo.getText().trim();
        double precio = parseMoney(tPrecio.getText());
        double desc = clamp0a100(parseMoney(tDesc.getText()));
        LocalDate fEnt = parseFecha(tFechaEntrega.getText()); // dd-MM-yyyy (mismo formato MX)

        if (art.isEmpty()) {
            JOptionPane.showMessageDialog(dlg, "Captura el nombre del trabajo de modista.");
            return;
        }
        if (precio <= 0) {
            JOptionPane.showMessageDialog(dlg, "Captura un precio válido (>0).");
            return;
        }

        Manufactura m = new Manufactura();
        m.setArticulo(art);
        m.setDescripcion(tDescripcion.getText().trim());
        m.setPrecio(precio);
        m.setDescuento(desc);
        m.setFechaRegistro(getFechaVentaEfectiva());           // fecha de venta
        m.setFechaEntrega(fEnt);
        String obs = tObs.getText().trim();
        m.setObservaciones(obs.isEmpty() ? null : obs);
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        m.setTelefono((tel == null || tel.isEmpty()) ? null : tel);
        m.setStatus("A");

        double monto = precio * (desc / 100.0);
        double sub = precio - monto;
        String fArt = fmt(fechaPreferida());

        model.addRow(new Object[]{
                m,                                  // col 0 = objeto Manufactura
                "MODISTA – " + art,             // col 1
                "",                                 // Marca
                n(m.getDescripcion()),              // Modelo = descripción
                "",                                 // Talla
                "",                                 // Color
                fArt,
                String.format("%.2f", precio),
                String.format("%.2f", desc),
                String.format("%.2f", monto),
                String.format("%.2f", sub),
                "Quitar"
        });
        recalcularTotales();
        txtCod.setText("");
        btRegistrarManufactura.setVisible(false);
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

    // 1) PRIMER DIÁLOGO: confirmar la venta
    Object[] ops = {"SI","NO"};
    int r = JOptionPane.showOptionDialog(this,
            "¿Registrar la venta de contado?",
            "Confirmación",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, ops, ops[0]);
    if (r != JOptionPane.YES_OPTION) {
        // Usuario dio NO o cerró => no se guarda, no se limpia
        return;
    }

    // 2) Validar que el monto de devolución no exceda el disponible
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

    // 3) SEGUNDO DIÁLOGO: OBSERVACIONES (obligatorio OK)
    //    Si el usuario cierra o da Cancelar, se aborta todo.
    if (!pedirObservacionesObligatorias()) {
        // Usuario canceló o cerró el cuadro de Observaciones
            JOptionPane.showMessageDialog(this,
                    "La venta NO se ha registrado.\nDebes confirmar las observaciones para continuar.",
                    "Venta cancelada", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    // 4) TERCER DIÁLOGO: CONDICIONES (solo si hay algún "vestido" en el carrito)
    boolean requiereCondiciones = hayVestidoEnCarrito();
    if (requiereCondiciones) {
        boolean okCond = editarMemoPrevia(false); // muestra condiciones con plantilla o último texto
        if (!okCond) {
            // Canceló / cerró => NO guardar, NO limpiar
            JOptionPane.showMessageDialog(this,
                        "La venta NO se ha registrado.\nDebes confirmar las condiciones para continuar.",
                        "Venta cancelada", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
    }

    try {
        // A partir de aquí ya pasamos las 3 barreras:
        // SI  -> OK en Observaciones -> OK en Condiciones (si aplica)
            Nota n = new Nota();
            String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
            n.setTelefono(tel == null || tel.isEmpty() ? null : tel);
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

            List<NotaDetalle> dets = new ArrayList<>();List<NotaDetalle> detsManufactura = new ArrayList<>();
java.util.List<Manufactura> manufacturas = new ArrayList<>();

for (int i = 0; i < model.getRowCount(); i++) {
    Object codObj = model.getValueAt(i, 0);

    Manufactura manu = null;
    String cod = "";
    if (codObj instanceof Manufactura m) {
        manu = m;
    } else if (codObj != null) {
        cod = codObj.toString().trim();
    }

    boolean esManufactura = (manu != null);
    boolean esPedido = (!esManufactura) && (cod.isEmpty() || "0".equals(cod));
    if (esPedido) {
        // Se manejará aparte en Pedidos
        continue;
    }

    LocalDate fRow = parseFecha(String.valueOf(model.getValueAt(i, 6)));
    LocalDate fechaDet = (fRow != null ? fRow : fechaEventoDefault);

    if (esManufactura) {
        // Completar datos de la manufactura para guardar después
        manu.setTelefono(n.getTelefono());
        if (manu.getFechaRegistro() == null) manu.setFechaRegistro(getFechaVentaEfectiva());
        if (manu.getFechaEntrega() == null) manu.setFechaEntrega(fechaEntregaDefault);
        manu.setPrecio(parseMoney(model.getValueAt(i, 7)));
        manu.setDescuento(parseMoney(model.getValueAt(i, 8)));
        if (manu.getStatus() == null || manu.getStatus().isBlank()) manu.setStatus("A");
        manufacturas.add(manu);

        // Línea para IMPRESIÓN (no va a Nota_Detalle)
        NotaDetalle d = new NotaDetalle();
        d.setCodigoArticulo(null);  // sin código de inventario
        d.setArticulo(String.valueOf(model.getValueAt(i, 1)));   // "MANUFACTURA – ..."
        d.setMarca("");
        d.setModelo(String.valueOf(model.getValueAt(i, 3)));     // usamos la descripción
        d.setTalla("");
        d.setColor("");
        d.setPrecio(parseMoney(model.getValueAt(i, 7)));
        d.setDescuento(parseMoney(model.getValueAt(i, 8)));
        d.setDescuentoMonto(parseMoney(model.getValueAt(i, 9)));
        d.setSubtotal(parseMoney(model.getValueAt(i, 10)));
        d.setFechaEvento(fechaDet);
        detsManufactura.add(d);
    } else {
        // Artículo normal de inventario
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
        d.setFechaEvento(fechaDet);
        dets.add(d);
    }
}


        // ===== NUEVO: lista para IMPRESIÓN (inventario + pedidos) =====
        List<NotaDetalle> detsPrint = new ArrayList<>(dets);
        detsPrint.addAll(detsManufactura);

        // Reusar lo que ya tienes para extraer PEDIR del carrito
        List<PedidosDAO.PedidoDraft> pedidosCarrito = extraerPedidosDelCarrito();
        for (PedidosDAO.PedidoDraft ped : pedidosCarrito) {
            NotaDetalle d = new NotaDetalle();
            d.setCodigoArticulo(""); // marca que es pedido
            // puedes decidir si quieres el prefijo o no
            d.setArticulo("PEDIDO – " + n(ped.articulo));
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

            // fecha_evento para el renglón (útil si luego lo usas)
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
            // CORRECTO: al servicio solo van los artículos reales de inventario
            int numeroNota = svc.crearVentaContado(n, dets, p, fechaVenta, fechaEventoDefault, fechaEntregaDefault);


            n.setNumeroNota(numeroNota); // <-- ¡asigna el número real antes de imprimir!
                        // Guardar manufacturas asociadas a la nota
if (!manufacturas.isEmpty()) {
    try {
        ManufacturasDAO mdao = new ManufacturasDAO();
        for (Manufactura manu : manufacturas) {
            manu.setNumeroNota(numeroNota);
            mdao.insertar(manu);
        }
    } catch (SQLException exManu) {
        JOptionPane.showMessageDialog(this,
                "La venta se registró, pero los trabajos de modista no se pudieron guardar:\n" + exManu.getMessage(),
                "Modistas", JOptionPane.WARNING_MESSAGE);
    }
}


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



            // Folio (preferimos el que retorna el servicio al objeto Nota)
            String folioImpresion = (n.getFolio()==null || n.getFolio().isBlank()) ? "—" : n.getFolio();

            // ======= 1.1) Aplicar devolución usada (si la hubo) =======
if (p.getDevolucion() != null && p.getDevolucion() > 0 &&
    p.getReferenciaDV() != null && !p.getReferenciaDV().isBlank()) {
    try {
        // Obtener el número real de la nota DV desde el combo
        int numeroNotaDV = 0;
        selItem = cbFolioDV.getSelectedItem();
        if (selItem instanceof DVOption opt) {
            numeroNotaDV = opt.numeroNotaDV;
        } else {
            throw new SQLException("No se pudo determinar la nota de devolución seleccionada.");
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

        // Normalizar teléfono igual que en el resto del sistema
        String telObsequio = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (telObsequio != null && telObsequio.isBlank()) {
            telObsequio = null;
        }

        LocalDate fEventoCliente = parseFecha(txtFechaEvento.getText());
        odao.insertarParaNota(
                numeroNota,
                telObsequio,          // <-- ya va limpio, como en clientes.telefono1
                fechaVenta,
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
String tel2 = Utilidades.TelefonosUI.soloDigitos(txtTelefono2.getText());

Printable prn = construirPrintableEmpresarial(
        emp, n, detsPrint, p,
        entregaMostrar, eventoMostrar,
        sel.getNombreCompleto(), folioImpresion,
        clienteNombre, observacionesTexto,tel2, cajeraCodigo, cajeraNombre
);

// === 4.0) Guardar OBSERVACIONES (van en el ticket, siempre) ===
try {
    if (observacionesTexto != null && !observacionesTexto.isBlank()) {
        new Controlador.NotasObservacionesDAO().upsert(numeroNota, observacionesTexto);
    }
} catch (SQLException ex) {
    JOptionPane.showMessageDialog(this,
        "No se pudieron guardar las observaciones: " + ex.getMessage(),
        "Aviso", JOptionPane.WARNING_MESSAGE);
}

// ¿Hay al menos un artículo tipo "Vestido" en la venta (incluye PEDIDOS)?
boolean imprimirCondiciones = detsPrint.stream()
        .anyMatch(d -> esArticuloVestido(d.getArticulo()));

Printable printablePrincipal = prn;

if (imprimirCondiciones) {
    if (memoEditable == null) {
        editarMemoPrevia(true);
    }

    String memoCrudoAGuardar = (memoEditable == null || memoEditable.isBlank())
            ? obtenerCondicionesPredeterminadas()
            : memoEditable;

    try {
        new NotasMemoDAO().upsert(numeroNota, memoCrudoAGuardar);
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this,
            "No se pudo guardar el MEMO de condiciones: " + ex.getMessage(),
            "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // 🔧 AHORA usamos detsPrint, que SÍ incluye los PEDIDO
    java.util.Map<String,String> varsFinal = buildMemoVars(
            emp, n, detsPrint, entregaMostrar, eventoMostrar, sel.getNombreCompleto());

    Printable condiciones = construirPrintableCondiciones(
            emp,
            n,
            detsPrint,   // este parámetro ni lo usas dentro, pero lo dejo coherente
            varsFinal,
            folioImpresion,
            sel.getNombreCompleto(),
            eventoMostrar,
            entregaMostrar,
            clienteNombre
    );

    printablePrincipal = combinarEnDosPaginas(prn, condiciones);
}


// >>> SNAPSHOT PARA TARJETAS (antes de limpiar UI)
java.util.List<String> obsequiosTarjetas = new java.util.ArrayList<>(obsequiosSel);
String obsTarjetas = observacionesTexto;
LocalDate fechaCompraTarjetas = fechaVenta;

// --- Lógica para decidir qué tarjetas imprimir ---
// ¿Hay al menos un "vestido"?
boolean hayVestidoTarjeta = detsPrint.stream()
        .anyMatch(d -> esArticuloVestido(d.getArticulo()));
// ¿Hay artículos que NO son vestido?
boolean hayOtrosTarjeta = detsPrint.stream()
        .anyMatch(d -> !esArticuloVestido(d.getArticulo()));

final boolean imprimirVestidos = hayVestidoTarjeta;
final boolean imprimirOtros;

if (hayOtrosTarjeta) {
    int resp = JOptionPane.showConfirmDialog(
            this,
            "Se han vendido artículos que no son vestido.\n" +
            "¿Deseas imprimir tarjetas para esos artículos?",
            "Imprimir tarjetas de artículos",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
    );
    imprimirOtros = (resp == JOptionPane.YES_OPTION);
} else {
    imprimirOtros = false;
}

// Runnable compartido para imprimir tarjetas (independiente del ticket)
Runnable imprimirTarjetasRunnable = () -> {
    if (!imprimirVestidos && !imprimirOtros) return;
    imprimirTarjetasVenta(
            emp,
            clienteNombre,
            eventoMostrar,
            fechaCompraTarjetas,
            sel.getNombreCompleto(),
            detsPrint,
            obsequiosTarjetas,
            obsTarjetas,
            folioImpresion,
            imprimirVestidos,
            imprimirOtros
    );
};

// === 4.2) IMPRIMIR (ticket +/- condiciones, luego tarjetas) ===
imprimirYConfirmarAsync(
        printablePrincipal,
        () -> {
            // Ticket OK → se mandan tarjetas
            imprimirTarjetasRunnable.run();
            JOptionPane.showMessageDialog(this,
                    "Venta registrada.\nNota No: " + numeroNota + "   Folio: " + folioImpresion);
        },
        () -> {
            // Ticket falló o se canceló → AÚN ASÍ mandamos tarjetas
            imprimirTarjetasRunnable.run();
            JOptionPane.showMessageDialog(this,
                    "La venta se registró, pero la impresión del ticket no se completó.\n" +
                    "Puedes reimprimir el ticket desde el módulo de notas.\n" +
                    "Folio: " + folioImpresion,
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        }
);


// Notificar a Corte de Caja que hubo una operación completada
try {
    Utilidades.EventBus.notificarOperacionFinalizada();
} catch (Exception ignore) {}




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
btRegistrarManufactura.setVisible(false);
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

    // ===== Utilidades =====
    /** Solo para impresión: 10 dígitos -> 123-456-7890; si no, lo deja como viene. */
private String formatearTelefonoImpresion(String tel) {
    if (tel == null) return "";
    String dig = Utilidades.TelefonosUI.soloDigitos(tel);
    if (dig != null && dig.length() == 10) {
        return dig.substring(0, 3) + "-" +
               dig.substring(3, 6) + "-" +
               dig.substring(6);
    }
    return tel.trim();
}
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

        String codStr = String.valueOf(model.getValueAt(i, 0));
        if (codStr == null) codStr = "";
        codStr = codStr.trim();

        // Solo son PEDIDO las filas que tú mismo creas con código "0" o vacío
        boolean esPedido = codStr.isEmpty() || "0".equals(codStr);
        if (!esPedido) {
            continue;
        }

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
        String clienteNombreFallback, String observacionesTexto, String tel2Fallback,
        int cajeraCodigo, String cajeraNombre) {

    // ===== datos base ya calculados (efectivamente finales) =====
    final String tel1Raw      = (n.getTelefono() == null) ? "" : n.getTelefono();
    final String fechaHoy  = fechaLarga(getFechaVentaEfectiva());
    final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaLarga(fechaEntregaMostrar);
    final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaLarga(fechaEventoMostrar);
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
        ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1Raw);
        if (cr != null) {
            if (cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank())
                cliNombre = cr.getNombreCompleto();
            if (cr.getTelefono2() != null) cliTel2 = cr.getTelefono2();
            if (cr.getFechaPrueba1() != null) cliPrueba1 = fechaLarga(cr.getFechaPrueba1());
            if (cr.getFechaPrueba2() != null) cliPrueba2 = fechaLarga(cr.getFechaPrueba2());

        }
    } catch (Exception ignore) { }

    // Medidas (si existen columnas)
    String medBusto = "", medCintura = "", medCadera = "";
    try {
        clienteDAO cdao = new clienteDAO();
        java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono( tel1Raw );
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

    final String fTel1Print = formatearTelefonoImpresion(tel1Raw);
    cliTel2 = formatearTelefonoImpresion(cliTel2);
    final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;
        final String fCajeraCodigo = (cajeraCodigo == 0 ? "" : String.valueOf(cajeraCodigo).trim());
    final String fCajeraNombre = (cajeraNombre == null ? "" : cajeraNombre.trim());

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
            String telEmpPrint = formatearTelefonoImpresion(emp.telefono);
            String waEmpPrint  = formatearTelefonoImpresion(emp.whatsapp);
            yy = drawWrapped(g2, joinNonBlank("   ",
                    labelIf("Tel: ", telEmpPrint),
                    labelIf("WhatsApp: ", waEmpPrint)), leftTextX, yy + 2, infoTextWidth);

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
            center(g2, "NOTA DE VENTA CONTADO", x, w, afterTail + 14);
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
                    labelIf("Teléfono: ", safe(fTel1Print)),
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
                yRight = drawWrapped(g2, labelIf("Asesora: ", asesorNombre), x + leftW + gapCols, yRight + 2, rightW);

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

    // Columnas: Código | Obsequio (el obsequio usa todo el espacio restante)
    final int gap2      = 16;
    final int colCodW   = 70;

    final int xCod      = x;
    final int xNom      = xCod + colCodW + gap2;
    final int anchoNom  = w - (xNom - x);   // todo el espacio que queda a la derecha

    // Encabezado
    g2.drawString("Código",   xCod, y);
    g2.drawString("Obsequio", xNom, y);
    y += 10; 
    g2.drawLine(x, y, x + w, y); 
    y += 14;

    // ---- Precargar detalles de TODOS los obsequios ----
    List<String> codigos = new ArrayList<>();
    for (String raw : obsequiosPrint) {
        if (raw == null) continue;
        String codigo = raw.trim();
        if (!codigo.isEmpty()) {
            codigos.add(codigo);
        }
    }

    Map<String, String[]> info = new HashMap<>();
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

    // ---- Pintar filas usando SOLO código + obsequio, con obsequio ancho ----
    for (String raw : obsequiosPrint) {
        if (raw == null || raw.trim().isEmpty()) continue;

        String codigoTxt = raw.trim();
        String nombre = "";
        String[] row = info.get(codigoTxt);
        if (row != null) {
            nombre = row[0]; // solo el "Obsequio" (articulo)
        }

        // código (columna pequeña)
        int yCodBase = y + g2.getFontMetrics().getAscent();
        g2.drawString("• " + codigoTxt, xCod, yCodBase);

        // obsequio usando todo el ancho libre
        int yNom = drawWrapped(g2, nombre, xNom, yCodBase, anchoNom);

        // siguiente renglón, respetando el más largo
        y = Math.max(yCodBase, yNom) + 4;
    }
}

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
    String folio;
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

    // ===== ENCABEZADO: Razón social centrada + FOLIO a la derecha =====
    g2.setFont(titleFont);
    String rs = safe(t.razonSocial);
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int yTitle = iy + margin + fm.getAscent();

    // Razón social centrada
    int titleX = ix + (iw - fm.stringWidth(rs)) / 2;
    g2.drawString(rs, titleX, yTitle);

        // Folio arriba derecha
        String folioStr = safe(t.folio);
        if (!folioStr.isEmpty()) {
            String etiqueta = "Folio: " + folioStr;
            g2.setFont(textFont);
            java.awt.FontMetrics fmF = g2.getFontMetrics();
            int folioX = ix + iw - margin - fmF.stringWidth(etiqueta);
            int folioY = iy + margin + fmF.getAscent();
            g2.drawString(etiqueta, folioX, folioY);
            g2.setFont(titleFont);
        }

    // A partir de aquí cuerpo
    g2.setFont(textFont);
    fm = g2.getFontMetrics();
    int yCursor = yTitle + lineH * 2;
    int textX = ix + margin;

    g2.drawString("Cliente: " + safe(t.cliente), textX, yCursor);          yCursor += lineH;
    g2.drawString("Fecha Evento: " + safe(t.fechaEvento), textX, yCursor); yCursor += lineH;
    g2.drawString("Fecha Compra: " + safe(t.fechaCompra), textX, yCursor); yCursor += lineH;
    g2.drawString("Asesora: " + safe(t.asesor), textX, yCursor);            yCursor += lineH;

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
/** ¿En el carrito hay al menos un artículo que cuente como "vestido"? */
private boolean hayVestidoEnCarrito() {
    if (model == null) return false;
    for (int i = 0; i < model.getRowCount(); i++) {
        Object artObj = model.getValueAt(i, 1); // columna "Artículo"
        if (artObj != null && esArticuloVestido(artObj.toString())) {
            return true;
        }
    }
    return false;
}

private java.util.List<TarjetaVentaData> construirTarjetasVenta(
        EmpresaInfo emp,
        String clienteNombre,
        LocalDate fechaEvento,
        LocalDate fechaCompra,
        String asesorNombre,
        java.util.List<NotaDetalle> detsPrint,
        java.util.List<String> obsequiosCodigos,
        String observaciones,
        String folio,
        boolean incluirVestidos,
        boolean incluirNoVestidos) {

    java.util.List<TarjetaVentaData> tarjetas = new java.util.ArrayList<>();

    // Razón social que se verá en la tarjeta
    String razon = (emp.razonSocial != null && !emp.razonSocial.isBlank())
            ? emp.razonSocial
            : (emp.nombreFiscal != null ? emp.nombreFiscal : "");

    String fechaEventoStr = (fechaEvento == null) ? "" : fechaLarga(fechaEvento);
    String fechaCompraStr = (fechaCompra == null) ? "" : fechaLarga(fechaCompra);
    String asesor = (asesorNombre == null) ? "" : asesorNombre;
    String obs = (observaciones == null) ? "" : observaciones.trim();
    String folioTxt = (folio == null) ? "" : folio.trim();

    // ===== Texto de obsequios (mismo para todas las tarjetas de la venta) =====
    String obsequiosTexto = "";
    if (obsequiosCodigos != null && !obsequiosCodigos.isEmpty()) {
        java.util.List<String> codigosLimpios = new java.util.ArrayList<>();
        for (String c : obsequiosCodigos) {
            if (c == null) continue;
            String cc = c.trim();
            if (!cc.isEmpty()) codigosLimpios.add(cc);
        }

        if (!codigosLimpios.isEmpty()) {
            java.util.Map<String, String> info = new java.util.HashMap<>();

            StringBuilder sql = new StringBuilder(
                    "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
                    "FROM InventarioObsequios WHERE codigo_articulo IN ("
            );
            for (int i = 0; i < codigosLimpios.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(')');

            try (java.sql.Connection cn = Conexion.Conecta.getConnection();
                 java.sql.PreparedStatement ps = cn.prepareStatement(sql.toString())) {

                for (int i = 0; i < codigosLimpios.size(); i++) {
                    ps.setString(i + 1, codigosLimpios.get(i));
                }

                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String c = rs.getString("codigo_articulo");
                        String nombre = n(rs.getString("articulo"));
                        String marca  = n(rs.getString("marca"));
                        String modelo = n(rs.getString("modelo"));
                        String talla  = n(rs.getString("talla"));
                        String color  = n(rs.getString("color"));

                        String detalle = nombre;
                        String extra = "";
                        if (!marca.isBlank())  extra = marca;
                        if (!modelo.isBlank()) extra = extra.isEmpty() ? modelo : extra + " " + modelo;
                        if (!talla.isBlank())  extra = extra.isEmpty() ? "Talla " + talla : extra + " T" + talla;
                        if (!color.isBlank())  extra = extra.isEmpty() ? "Color " + color : extra + " " + color;

                        if (!extra.isEmpty()) detalle += " (" + extra + ")";
                        info.put(c, detalle);
                    }
                }
            } catch (Exception ex) {
                // Si truena la consulta, nos quedamos solo con códigos
            }

            StringBuilder sb = new StringBuilder();
            for (String cod : codigosLimpios) {
                if (sb.length() > 0) sb.append("\n");
                String det = info.get(cod);
                if (det == null || det.isBlank()) {
                    sb.append(cod);
                } else {
                    sb.append(cod).append(" - ").append(det);
                }
            }
            obsequiosTexto = sb.toString();
        }

        // Fallback si algo salió mal
        if (obsequiosTexto.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String c : obsequiosCodigos) {
                if (c == null) continue;
                String cc = c.trim();
                if (cc.isEmpty()) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(cc);
            }
            obsequiosTexto = sb.toString();
        }
    }

    // ===== Lógica por renglón: 2 tarjetas por vestido, 1 por cualquier otro =====
    if (detsPrint != null) {
        for (NotaDetalle d : detsPrint) {
            if (d == null) continue;

            boolean esVestido = esArticuloVestido(d.getArticulo());
            if (esVestido && !incluirVestidos) {
                continue; // vestidos desactivados
            }
            if (!esVestido && !incluirNoVestidos) {
                continue; // otros artículos desactivados
            }

            // cantidad de tarjetas para este renglón
            int cantidadTarjetas = esVestido ? 2 : 1;

            String codArt = n(d.getCodigoArticulo());
            String articulo = n(d.getArticulo());
            String marca    = n(d.getMarca());
            String modelo   = n(d.getModelo());
            String talla    = n(d.getTalla());
            String color    = n(d.getColor());

            for (int i = 0; i < cantidadTarjetas; i++) {
                TarjetaVentaData t = new TarjetaVentaData();
                t.razonSocial    = razon;
                t.cliente        = n(clienteNombre);
                t.fechaEvento    = fechaEventoStr;
                t.fechaCompra    = fechaCompraStr;
                t.asesor         = asesor;
                t.codigoArticulo = codArt;
                t.articulo       = articulo;
                t.marca          = marca;
                t.modelo         = modelo;
                t.talla          = talla;
                t.color          = color;
                t.obsequios      = obsequiosTexto;
                t.observaciones  = obs;
                t.folio          = folioTxt;    // <<< AQUÍ SE AMARRA EL FOLIO >>>
                tarjetas.add(t);
            }
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
        String observaciones,
        String folio,
        boolean incluirVestidos,
        boolean incluirNoVestidos) {

    java.util.List<TarjetaVentaData> tarjetas = construirTarjetasVenta(
            emp,
            clienteNombre,
            fechaEvento,
            fechaCompra,
            asesorNombre,
            detsPrint,
            obsequiosCodigos,
            observaciones,
            folio,
            incluirVestidos,
            incluirNoVestidos
    );

    if (tarjetas == null || tarjetas.isEmpty()) {
        return; // nada que imprimir
    }

    try {
        TarjetasVentaPrinter.imprimir(this, tarjetas);
    } catch (PrinterException ex) {
        JOptionPane.showMessageDialog(this,
                "La venta se registró, pero no se pudieron imprimir las tarjetas:\n" + ex.getMessage(),
                "Impresión de tarjetas",
                JOptionPane.WARNING_MESSAGE);
    }
}
// ================== REIMPRESIÓN DE VENTA CRÉDITO ==================
public static void reimprimirNotaContadoDesdeDatos(
        Component parent,
        Nota nota,
        java.util.List<NotaDetalle> detsDB,
        PagoFormas pagos,
        String asesorNombre,
        LocalDate fechaEventoMostrar,
        LocalDate fechaEntregaMostrar,
        String clienteNombre,
        String telefono2,
        String memoTextoBD,
        String observacionesBD,
        java.util.List<String> codigosObsequios,
        int cajeraCodigo,
        String cajeraNombre) {

    // Panel "dummy" para reutilizar toda la lógica de impresión ya existente
    VentaContadoPanel dummy = new VentaContadoPanel();

    LocalDate fechaVenta = null;

// 1) Preferir la fecha de la nota (fecha de venta real)
// Usa aquí el/los getters reales que tengas en tu clase Nota
if (nota != null) {
    // ejemplo 1: si tienes un campo LocalDate directamente
    if (nota.getFechaRegistro() != null) {
        fechaVenta = nota.getFechaRegistro().toLocalDate();
    }
    // ejemplo 2: si solo tienes fecha_registro
    else if (nota.getFechaRegistro() != null) {
        // si es LocalDate:
        // fechaVenta = nota.getFechaRegistro();
        // si es LocalDateTime:
        fechaVenta = nota.getFechaRegistro().toLocalDate();
    }
}

// 2) Si la nota no tiene fecha, usar la de PagoFormas
if (fechaVenta == null && pagos != null && pagos.getFechaOperacion() != null) {
    fechaVenta = pagos.getFechaOperacion();
}

// 3) Último recurso: hoy
if (fechaVenta == null) {
    fechaVenta = LocalDate.now();
}

dummy.fechaVentaSeleccionada = fechaVenta;

    // 2) Obsequios y observaciones para que el ticket los imprima igual
dummy.obsequiosSel = new java.util.ArrayList<>();
if (codigosObsequios != null) {
    for (String raw : codigosObsequios) {
        if (raw == null) continue;
        String s = raw.trim();
        if (s.isEmpty()) continue;

        String codigo = s;

        // Si viene "CODIGO - Descripción", toma solo el código
        int idx = s.indexOf(" - ");
        if (idx >= 0) {
            codigo = s.substring(0, idx).trim();
        }

        dummy.obsequiosSel.add(codigo);
    }
}

dummy.observacionesTexto = observacionesBD;


    // 3) Empresa
    EmpresaInfo emp = dummy.cargarEmpresaInfo();
// 4) Detalles a imprimir: lo que viene de la BD
java.util.List<NotaDetalle> detsPrint = new java.util.ArrayList<>(detsDB);

// ¿El detalle ya trae líneas de MODISTA?
boolean yaTieneModistasEnDetalle = detsDB.stream().anyMatch(d -> {
    String art = d.getArticulo();
    return art != null && art.toUpperCase(java.util.Locale.ROOT).contains("MODISTA");
});

// 4.1) Solo agregamos MODISTA desde Manufacturas si NO existen en NotaDetalle
if (!yaTieneModistasEnDetalle) {
    try {
        ManufacturasDAO mdao = new ManufacturasDAO();
        java.util.List<Manufactura> lst = mdao.listarPorNota(nota.getNumeroNota()); // ya lo tienes

        for (Manufactura manu : lst) {
            NotaDetalle d = new NotaDetalle();

            d.setCodigoArticulo(null); // sin código inventario
            d.setArticulo("MODISTA – " + dummy.n(manu.getArticulo()));
            d.setMarca("");
            d.setModelo(dummy.n(manu.getDescripcion()));
            d.setTalla("");
            d.setColor("");

            double precio = manu.getPrecio()    == null ? 0d : manu.getPrecio();
            double desc   = manu.getDescuento() == null ? 0d : manu.getDescuento();
            double monto  = precio * (desc / 100.0);
            double sub    = precio - monto;

            d.setPrecio(precio);
            d.setDescuento(desc);
            d.setDescuentoMonto(monto);
            d.setSubtotal(sub);

            if (manu.getFechaRegistro() != null) {
                d.setFechaEvento(manu.getFechaRegistro());
            } else {
                d.setFechaEvento(fechaEventoMostrar);
            }

            detsPrint.add(d);
        }
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}



    // 5) Folio
    String folioImpresion = (nota.getFolio() == null || nota.getFolio().isBlank())
            ? "—"
            : nota.getFolio();

    // 6) PagoFormas "seguro"
    PagoFormas p = (pagos != null) ? pagos : new PagoFormas();

    // === NUEVO: asegurarnos de que la devolución se cargue para reimpresión ===
    dummy.rellenarDevolucionDesdeBD(nota, p);


    // 7) Fechas para mostrar en ticket / condiciones, con mismos criterios que en guardarVentaCredito
    LocalDate tmpeventoMostrar = fechaEventoMostrar;
    LocalDate entregaMostrar = fechaEntregaMostrar;

    // Si no te pasan las fechas calculadas, intenta reconstruir algo decente
    if (tmpeventoMostrar == null) {
        // Primero la del detalle
        for (NotaDetalle d : detsPrint) {
            if (d.getFechaEvento() != null) {
                tmpeventoMostrar = d.getFechaEvento();
                break;
            }
        }
    }
    LocalDate eventoMostrar = tmpeventoMostrar;
    // Si sigue en null, podrías intentar consultarla del cliente/nota,
    // pero como ya te pasan fechaEventoMostrar desde afuera, aquí no me meto más.

    // Lo mismo para entrega:
    // si no viene, se puede dejar en null. El ticket aguanta vacío.
    if (entregaMostrar == null) {
        // Podrías reconstruir desde la nota o desde el cliente, si tienes esos campos ahí.
        // Para no inventar más lógica, lo dejamos así.
    }

    // 8) Ticket principal (NOTA CONTADO)
    Printable prnTicket = dummy.construirPrintableEmpresarial(
            emp,
            nota,
            detsPrint,
            p,
            entregaMostrar,
            eventoMostrar,
            asesorNombre,
            folioImpresion,
            clienteNombre,
            telefono2,
            observacionesBD,
            cajeraCodigo,
            cajeraNombre
    );

    // 9) ¿Se deben imprimir condiciones?
    boolean hayVestido = detsPrint.stream()
            .anyMatch(d -> dummy.esArticuloVestido(d.getArticulo()));

    // Para reimpresión, SOLO imprimimos condiciones si:
    // - hay vestido, y
    // - existe memo guardado en BD (memoTextoBD no vacío).
    boolean imprimirCondiciones = hayVestido &&
            memoTextoBD != null &&
            !memoTextoBD.isBlank();

    Printable printablePrincipal;

    if (imprimirCondiciones) {
        // Usar el texto que se guardó en BD como "memoEditable"
        dummy.memoEditable = memoTextoBD;

        java.util.Map<String,String> vars = dummy.buildMemoVars(
                emp,
                nota,
                detsPrint,
                entregaMostrar,
                eventoMostrar,
                asesorNombre
        );

        Printable condiciones = dummy.construirPrintableCondiciones(
                emp,
                nota,
                detsPrint,
                vars,
                folioImpresion,
                asesorNombre,
                eventoMostrar,
                entregaMostrar,
                clienteNombre
        );

        printablePrincipal = combinarEnDosPaginas(prnTicket, condiciones);
    } else {
        printablePrincipal = prnTicket;
    }

    // 10) Datos para tarjetas (¼ de hoja) igual que en la venta original
    java.util.List<String> obsequiosTarjetas = new java.util.ArrayList<>(dummy.obsequiosSel);

    String obsTarjetas = observacionesBD;
    LocalDate fechaCompraTarjetas = fechaVenta;

    // 11) Reutilizamos el mismo flujo asíncrono de impresión
    dummy.imprimirYConfirmarAsync(
            printablePrincipal,
            () -> {
                // Primero las tarjetas
                dummy.imprimirTarjetasVenta(
                        emp,
                        clienteNombre,
                        eventoMostrar,
                        fechaCompraTarjetas,
                        asesorNombre,
                        detsPrint,
                        obsequiosTarjetas,
                        obsTarjetas,
                        folioImpresion,
                        true,
                        true
                );

                JOptionPane.showMessageDialog(
                        parent,
                        "Nota de CONTADO reimpresa.\nFolio: " + folioImpresion,
                        "Reimpresión",
                        JOptionPane.INFORMATION_MESSAGE
                );
            },
            () -> JOptionPane.showMessageDialog(
                    parent,
                    "La reimpresión fue cancelada o falló.\n" +
                    "La venta en base de datos NO se modificó.",
                    "Reimpresión",
                    JOptionPane.WARNING_MESSAGE
            )
    );
}
/** 
 * Para reimpresión: si el PagoFormas no trae devolucion, 
 * la buscamos en Formas_Pago (status = 'A', devolucion IS NOT NULL).
 */
private void rellenarDevolucionDesdeBD(Nota nota, PagoFormas p) {
    if (nota == null || p == null) return;

    // Si ya trae devolución, no hacemos nada
    if (p.getDevolucion() != null && p.getDevolucion() > 0.0001) return;

    final String sql = """
        SELECT 
            referencia_dv,
            SUM(COALESCE(devolucion,0)) AS total_dv
        FROM Formas_Pago
        WHERE numero_nota = ?
          AND tipo_operacion = ?
          AND status = 'A'
          AND devolucion IS NOT NULL
        GROUP BY referencia_dv
        """;

    try (Connection cn = Conexion.Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, nota.getNumeroNota());
        ps.setString(2, (nota.getTipo() == null || nota.getTipo().isBlank())
                            ? "CR"   // por si acaso
                            : nota.getTipo());

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double totalDv = rs.getDouble("total_dv");
                if (!rs.wasNull() && totalDv > 0.0001) {
                    String ref = rs.getString("referencia_dv");

                    p.setDevolucion(totalDv);
                    p.setReferenciaDV(ref);
                }
            }
        }
    } catch (SQLException ex) {
        // No revientes la reimpresión por esto, sólo log
        ex.printStackTrace();
    }
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
            // Forzar a que se recargue aunque sea el mismo teléfono
            lastTelefonoConsultado = null;
            txtTelefono.setText(tel);
            // El DocumentListener ya llama a cargarCliente(), pero por si acaso:
            cargarCliente();
        }
    }
}
private static class DialogBusquedaCliente extends JDialog {

    private JTextField txtApellido;
    private JTable tabla;
    private DefaultTableModel modelo;
    private java.util.List<ClienteResumen> resultados = new ArrayList<>();
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
            resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);  // <-- método que agregamos al DAO
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