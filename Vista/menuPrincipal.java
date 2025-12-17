package Vista;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.Deque;

import Controlador.AsesorDAO;

public class menuPrincipal extends JFrame {

    // IDs de tarjetas “estructura”
    private static final String CARD_HOME      = "home";
    private static final String CARD_EMPRESA   = "empresa";
    private static final String CARD_CLIENTES  = "clientes";
    private static final String CARD_INV_MENU  = "inventario_menu";
    private static final String CARD_INV_ART   = "inventario_articulos";
    private static final String CARD_INV_OBS   = "inventario_obsequios";
    private static final String CARD_OPER      = "operaciones";
    private static final String CARD_REPORTES  = "reportes";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);
    private final JLabel title = new JLabel("Menú principal", SwingConstants.CENTER);
    private final JButton btBack = new JButton("← Volver");

    // Historial para navegar hacia atrás
    private final Deque<String> history = new ArrayDeque<>();
    private String current = CARD_HOME;
    private final Modelo.Asesor usuarioActual;

    public menuPrincipal(Modelo.Asesor usuarioActual) {
        setTitle("MIANOVIAS");
        this.usuarioActual = usuarioActual;
                // ===== Menú de sistema (cerrar sesión)
        JMenuBar mb = new JMenuBar();
        JMenu menuSis = new JMenu("Sistema");
        JMenuItem miCerrarSesion = new JMenuItem("Cerrar sesión");

        miCerrarSesion.addActionListener(_e -> cerrarSesion());

        menuSis.add(miCerrarSesion);
        mb.add(menuSis);
        setJMenuBar(mb);

        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                Object[] ops = {"SI","NO"};
                int r = JOptionPane.showOptionDialog(menuPrincipal.this,
                        "¿Deseas salir del sistema?", "Confirmación",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, ops, ops[1]);
                if (r == JOptionPane.YES_OPTION) {
                    dispose();
                    System.exit(0);
                }
            }
        });

        // ===== Encabezado con Volver + Título
        JPanel header = new JPanel(new BorderLayout());
        btBack.setVisible(false);
        btBack.setFocusPainted(false);
        btBack.addActionListener(_e -> goBack());

        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        left.add(btBack);
        header.add(left, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);

        // ====== Tarjetas
        mainPanel.add(buildHomeCard(), CARD_HOME);           // Menú principal

        // Submenú Inventario + destinos reales
        mainPanel.add(buildInventarioMenu(), CARD_INV_MENU);
        mainPanel.add(new InventarioPanel(),   CARD_INV_ART); // "inventario_articulos"
        mainPanel.add(new ObsequiosInvPanel(), CARD_INV_OBS); // "inventario_obsequios"

        // Submenú de clientes
        mainPanel.add(new ClientesSubmenuPanel(card -> showCard(card, tituloDe(card))), CARD_CLIENTES);
        mainPanel.add(new ClientesPanel(), ClientesSubmenuPanel.CARD_CLIENTES_REGISTRO);
        
        mainPanel.add(new EditarClientePanel(),   ClientesSubmenuPanel.CARD_CLIENTES_EDITAR);
        mainPanel.add(new AgendaPanel(),   ClientesSubmenuPanel.CARD_CLIENTES_CITAS);
        mainPanel.add(new HistorialClientePanel(),ClientesSubmenuPanel.CARD_CLIENTES_HIST);


        // Submenú Operaciones (usa IDs de texto EXACTOS que emite OperacionesPanel)
        mainPanel.add(new OperacionesPanel(card -> showCard(card, tituloDe(card))), CARD_OPER);
        VentaContadoPanel ventaContado = new VentaContadoPanel();
        VentaCreditoPanel ventaCredito = new VentaCreditoPanel();
        AbonoPanel abonoPanel = new AbonoPanel();
        if (usuarioActual != null) {
            ventaContado.setCajeraActual(
                usuarioActual.getNumeroEmpleado(),
                usuarioActual.getNombreCompleto()
            );
            ventaCredito.setCajeraActual(
                usuarioActual.getNumeroEmpleado(),
                usuarioActual.getNombreCompleto()
            );
            abonoPanel.setCajeraActual(
                usuarioActual.getNumeroEmpleado(),
                usuarioActual.getNombreCompleto()
            );
        }
        mainPanel.add(ventaContado,        "Venta de contado");
        mainPanel.add(ventaCredito,        "Venta de crédito");
        mainPanel.add(abonoPanel,               "Abono");
        mainPanel.add(new DevolucionPanel(),          "Devoluciones");
        mainPanel.add(new CancelarNotaPanel(),        "Cancelación de notas");
        mainPanel.add(new CambioFechaEventoPanel(),   "Cambio de fecha de evento");
        mainPanel.add(new HojaEntregaPanel(),         "Hoja de entrega");
        mainPanel.add(new AgregarObsequiosNotaPanel(), "Agregar obsequios a nota");
        mainPanel.add(new CambioCodigoArticuloPanel(), "Cambio de código de artículo");

        // Submenú Empresa (mismo patrón que Operaciones)
        mainPanel.add(new EmpresaSubmenuPanel(card -> showCard(card, tituloDe(card))), CARD_EMPRESA);
        mainPanel.add(new EmpresaPanel(),        "Información de la empresa");
        mainPanel.add(new PanelFoliosIniciales(),"Asignación de Folios");
        mainPanel.add(new AsesoresPanel(),       "Empleados");
        mainPanel.add(new CondicionesEmpresaPanel(), "Condiciones de venta");

        // Submenú Reportes (mismo patrón que Operaciones)
        mainPanel.add(new ReportesPanel(card -> showCard(card, tituloDe(card))), CARD_REPORTES);
        // Tarjetas destino:
        mainPanel.add(new PagoGastosPanel(),          ReportesPanel.CARD_REP_GASTOS);
        mainPanel.add(new CorteCajaPanel(),           ReportesPanel.CARD_REP_CORTE);
        ReimprimirNotaPanel reimprimirNotaPanel = new ReimprimirNotaPanel();
        if (usuarioActual != null) {
            reimprimirNotaPanel.setCajeraActual(
                usuarioActual.getNumeroEmpleado(),
                usuarioActual.getNombreCompleto()
            );
        }
        mainPanel.add(new DetalleClienteReportePanel(), ReportesPanel.CARD_REP_DETCLI);
        mainPanel.add(new EntregasVestidosPanel(),    ReportesPanel.CARD_REP_ENTREGAS);
        mainPanel.add(new ArticulosAPedirPanel(),     ReportesPanel.CARD_REP_PEDIR);
        mainPanel.add(new HojasAjustePanel(),        ReportesPanel.CARD_REP_AJUSTES);
        mainPanel.add(new ReporteObsequiosPanel(),    ReportesPanel.CARD_REP_OBSEQ);
        mainPanel.add(new VentasPorVendedorPanel(),   ReportesPanel.CARD_REP_VENTVEND);
        mainPanel.add(new NotasPorMesPanel(), ReportesPanel.CARD_REP_NOTAS_MES);
        mainPanel.add(new ReporteVentasPanel(),       ReportesPanel.CARD_REP_VENTAS);


        // Mostrar
        pack();
        setExtendedState(JFrame.MAXIMIZED_BOTH); // abrir maximizada
        setVisible(true);
    }

    // ====== HOME: botones grandes
    private JPanel buildHomeCard() {
        JPanel center = new JPanel(new GridLayout(2, 3, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton btReportes   = botonGrande("Reportes");

        JButton btEmpresa     = botonGrande("Empresa");
        JButton btClientes    = botonGrande("Clientes");
        JButton btInventario  = botonGrande("Inventario");
        JButton btOperaciones = botonGrande("Operaciones");

        btEmpresa.addActionListener(_e   -> showCard(CARD_EMPRESA,   "Empresa"));
        btClientes.addActionListener(_e  -> showCard(CARD_CLIENTES,  "Clientes"));
        btInventario.addActionListener(_e-> showCard(CARD_INV_MENU,  "Inventario"));
        btOperaciones.addActionListener(_e-> showCard(CARD_OPER,     "Operaciones"));
        btReportes.addActionListener(_e -> showCard(CARD_REPORTES, "Reportes"));

        center.add(btEmpresa);
        center.add(btClientes);
        center.add(btInventario);
        center.add(btOperaciones);
        center.add(btReportes);
        center.add(new JLabel());

        JPanel home = new JPanel(new BorderLayout());
        home.add(center, BorderLayout.CENTER);
        return home;
    }

    private JPanel buildClientesMenu() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 24));

        JButton btReg = tileButton("Registro de clientes");
        btReg.addActionListener(_e -> showCard(ClientesSubmenuPanel.CARD_CLIENTES_REGISTRO, "Registro de clientes"));

        JButton btEdit = tileButton("Editar información de cliente");
        btEdit.addActionListener(_e -> showCard(ClientesSubmenuPanel.CARD_CLIENTES_EDITAR, "Editar información de cliente"));

        JButton btCitas = tileButton("Agenda y Registro de citas");
        btCitas.addActionListener(_e -> showCard(ClientesSubmenuPanel.CARD_CLIENTES_CITAS, "Agenda y Registro de citas"));

        JButton btHist = tileButton("Registrar historial de cliente");
        btHist.addActionListener(_e -> showCard(ClientesSubmenuPanel.CARD_CLIENTES_HIST, "Registrar historial de cliente"));

        center.add(btReg);
        center.add(btEdit);
        center.add(btCitas);
        center.add(btHist);

        wrap.add(center, BorderLayout.CENTER);
        return wrap;
    }

    // Submenú Inventario (tiles compactos)
    private JPanel buildInventarioMenu() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 24));

        JButton btArt = tileButton("Inventario de artículos");
        btArt.addActionListener(_e -> showCard(CARD_INV_ART, "Inventario de artículos"));

        JButton btObs = tileButton("Inventario de obsequios");
        btObs.addActionListener(_e -> showCard(CARD_INV_OBS, "Inventario de obsequios"));

        center.add(btArt);
        center.add(btObs);

        wrap.add(center, BorderLayout.CENTER);
        return wrap;
    }
    private void cerrarSesion() {
    Object[] ops = {"SI","NO"};
    int r = JOptionPane.showOptionDialog(this,
            "¿Deseas cerrar tu sesión?",
            "Cerrar sesión",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, ops, ops[1]);
    if (r != JOptionPane.YES_OPTION) return;

    Modelo.SesionUsuario.cerrar();
    dispose();

    SwingUtilities.invokeLater(() -> {
        // Si no hay empleados, saltar login igual que en main
        if (!AsesorDAO.hayEmpleadosRegistrados()) {
            menuPrincipal frame = new menuPrincipal(null);
            JOptionPane.showMessageDialog(
                    frame,
                    "Por favor, registra al menos un empleado en el menú \"Empresa > Empleados\".",
                    "Empleados",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // Flujo normal
        Modelo.Asesor usuario = LoginDialog.mostrarLogin(null);
        if (usuario == null) {
            System.exit(0);
        }
        Modelo.SesionUsuario.iniciar(usuario);
        new menuPrincipal(usuario);
    });
}



    private JButton tileButton(String text) {
        JButton b = new JButton("<html><b>" + text + "</b></html>");
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 16f));
        b.setPreferredSize(new Dimension(260, 140));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        return b;
    }

    // ====== Navegación
    private void showCard(String card, String titulo) {
        if (!CARD_HOME.equals(current)) history.push(current);
        current = card;
        title.setText(titulo);
        btBack.setVisible(!CARD_HOME.equals(card));
        cardLayout.show(mainPanel, card);
    }

    private void goBack() {
        if (history.isEmpty()) {
            current = CARD_HOME;
            title.setText("Menú principal");
            btBack.setVisible(false);
            cardLayout.show(mainPanel, CARD_HOME);
            return;
        }
        String prev = history.pop();
        current = prev;
        btBack.setVisible(!CARD_HOME.equals(prev));
        title.setText(tituloDe(prev));
        cardLayout.show(mainPanel, prev);
    }

    private String tituloDe(String card) {
        switch (card) {
            case CARD_HOME:        return "Menú principal";
            case CARD_EMPRESA:     return "Empresa";
            case CARD_CLIENTES:    return "Clientes";
            case ClientesSubmenuPanel.CARD_CLIENTES_REGISTRO: return "Registro de clientes";
            case ClientesSubmenuPanel.CARD_CLIENTES_EDITAR:   return "Editar información de cliente";
            case ClientesSubmenuPanel.CARD_CLIENTES_CITAS:    return "Agenda y Registro de citas";
            case ClientesSubmenuPanel.CARD_CLIENTES_HIST:     return "Registrar historial de cliente";
            case CARD_INV_MENU:    return "Inventario";
            case CARD_INV_ART:     return "Inventario de artículos";
            case CARD_INV_OBS:     return "Inventario de obsequios";
            case CARD_OPER:        return "Operaciones";
            case CARD_REPORTES: return "Reportes";
            case ReportesPanel.CARD_REP_GASTOS:   return "Pago de gastos";
            case ReportesPanel.CARD_REP_CORTE:    return "Corte de caja";
            case ReportesPanel.CARD_REP_REIMPR:   return "Re-imprimir nota";
            case ReportesPanel.CARD_REP_DETCLI:   return "Detalle de cliente";
            case ReportesPanel.CARD_REP_ENTREGAS: return "Entregas de vestidos";
            case ReportesPanel.CARD_REP_PEDIR:    return "Artículos a pedir";
            case ReportesPanel.CARD_REP_AJUSTES:  return "Hojas de ajustes";
            case ReportesPanel.CARD_REP_OBSEQ:    return "Reporte de obsequios";
            case ReportesPanel.CARD_REP_VENTVEND: return "Ventas por vendedor";
            case ReportesPanel.CARD_REP_NOTAS_MES: return "Notas por mes";
            case ReportesPanel.CARD_REP_VENTAS:    return "Reporte de ventas";
            // Los siguientes ya son “humanos”; devuélvelos tal cual:
            case "Venta de contado":
            case "Venta de crédito":
            case "Abono":
            case "Devoluciones":
            case "Cancelación de notas":
            case "Cambio de fecha de evento":
            case "Información de la empresa":
            case "Asignación de Folios":
            case "Empleados":
            case "Hoja de entrega":
            case "Agregar obsequios a nota":
            case "Cambio de código de artículo":
            
                return card;
        }
        return card;
    }

    private JButton botonGrande(String texto) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 18f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 120));
        return b;
    }

    // ====== Main
    public static void main(String[] args) {
    try {
        for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
            if ("Nimbus".equals(i.getName())) { UIManager.setLookAndFeel(i.getClassName()); break; }
    } catch (Exception ignore) {}

    Conexion.BootstrapDB.ensure();

    SwingUtilities.invokeLater(() -> {

        // 1) Revisar si hay empleados
        if (!AsesorDAO.hayEmpleadosRegistrados()) {
            // No hay nadie: entrar directo al menú sin login
            menuPrincipal frame = new menuPrincipal(null);
            JOptionPane.showMessageDialog(
                    frame,
                    "Por favor, registra al menos un empleado en el menú \"Empresa > Empleados\".",
                    "Empleados",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // 2) Flujo normal: hay empleados, entonces sí pedimos login
        Modelo.Asesor usuario = LoginDialog.mostrarLogin(null);
        if (usuario == null) {
            System.exit(0); // canceló
        }
        Modelo.SesionUsuario.iniciar(usuario);
        new menuPrincipal(usuario);
    });
}

}