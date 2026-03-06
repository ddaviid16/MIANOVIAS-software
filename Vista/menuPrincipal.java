package Vista;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
    private final Map<String, Supplier<JComponent>> lazyFactories = new HashMap<>();
    private final Set<String> loadedCards = new HashSet<>();

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
        addEagerCard(CARD_HOME, buildHomeCard());           // Menú principal

        // Submenú Inventario + destinos reales
        addEagerCard(CARD_INV_MENU, buildInventarioMenu());
        registerLazyCard(CARD_INV_ART, InventarioPanel::new);   // "inventario_articulos"
        registerLazyCard(CARD_INV_OBS, ObsequiosInvPanel::new); // "inventario_obsequios"

        // Submenú de clientes
        addEagerCard(CARD_CLIENTES, new ClientesSubmenuPanel(card -> showCard(card, tituloDe(card))));
        registerLazyCard(ClientesSubmenuPanel.CARD_CLIENTES_REGISTRO, ClientesPanel::new);
        registerLazyCard(ClientesSubmenuPanel.CARD_CLIENTES_EDITAR, EditarClientePanel::new);
        registerLazyCard(ClientesSubmenuPanel.CARD_CLIENTES_CITAS, AgendaPanel::new);
        registerLazyCard(ClientesSubmenuPanel.CARD_CLIENTES_HIST, HistorialClientePanel::new);


        // Submenú Operaciones (usa IDs de texto EXACTOS que emite OperacionesPanel)
        addEagerCard(CARD_OPER, new OperacionesPanel(card -> showCard(card, tituloDe(card))));
        registerLazyCard("Venta de contado", () -> {
            VentaContadoPanel p = new VentaContadoPanel();
            if (usuarioActual != null) {
                p.setCajeraActual(usuarioActual.getNumeroEmpleado(), usuarioActual.getNombreCompleto());
            }
            return p;
        });
        registerLazyCard("Venta de crédito", () -> {
            VentaCreditoPanel p = new VentaCreditoPanel();
            if (usuarioActual != null) {
                p.setCajeraActual(usuarioActual.getNumeroEmpleado(), usuarioActual.getNombreCompleto());
            }
            return p;
        });
        registerLazyCard("Abono", () -> {
            AbonoPanel p = new AbonoPanel();
            if (usuarioActual != null) {
                p.setCajeraActual(usuarioActual.getNumeroEmpleado(), usuarioActual.getNombreCompleto());
            }
            return p;
        });
        registerLazyCard("Devoluciones", DevolucionPanel::new);
        registerLazyCard("Cancelación de notas", CancelarNotaPanel::new);
        registerLazyCard("Cambio de fecha de evento", CambioFechaEventoPanel::new);
        registerLazyCard("Hoja de entrega", HojaEntregaPanel::new);
        registerLazyCard("Agregar obsequios a nota", AgregarObsequiosNotaPanel::new);
        registerLazyCard("Cambio de código de artículo", CambioCodigoArticuloPanel::new);
        registerLazyCard("Agregar datos de factura", FacturarPorFolioPanel::new);

        // Submenú Empresa (mismo patrón que Operaciones)
        addEagerCard(CARD_EMPRESA, new EmpresaSubmenuPanel(card -> showCard(card, tituloDe(card))));
        registerLazyCard("Información de la empresa", EmpresaPanel::new);
        registerLazyCard("Asignación de Folios", PanelFoliosIniciales::new);
        registerLazyCard("Empleados", AsesoresPanel::new);
        registerLazyCard("Condiciones de venta", CondicionesEmpresaPanel::new);

        // Submenú Reportes (mismo patrón que Operaciones)
        addEagerCard(CARD_REPORTES, new ReportesPanel(card -> showCard(card, tituloDe(card))));
        // Tarjetas destino:
        registerLazyCard(ReportesPanel.CARD_REP_GASTOS, PagoGastosPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_CORTE, CorteCajaPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_REIMPR, ReimprimirNotaPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_DETCLI, DetalleClienteReportePanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_ENTREGAS, EntregasVestidosPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_PEDIR, ArticulosAPedirPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_AJUSTES, HojasAjustePanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_OBSEQ, ReporteObsequiosPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_VENTVEND, VentasPorVendedorPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_NOTAS_MES, NotasPorMesPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_VENTAS, ReporteVentasPanel::new);
        registerLazyCard(ReportesPanel.CARD_REP_MODISTAS, ReporteModistasPanel::new);


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
        crearMenuConPantallaCarga(usuario);
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

    private void addEagerCard(String id, JComponent comp) {
        mainPanel.add(comp, id);
        loadedCards.add(id);
    }

    private void registerLazyCard(String id, Supplier<JComponent> factory) {
        lazyFactories.put(id, factory);
    }

    private void ensureCardLoaded(String card) {
        if (loadedCards.contains(card)) return;
        Supplier<JComponent> factory = lazyFactories.get(card);
        if (factory == null) return;

        JComponent comp = factory.get();
        if (comp != null) {
            mainPanel.add(comp, card);
            loadedCards.add(card);
        }
    }

    // ====== Navegación
    private void showCard(String card, String titulo) {
        ensureCardLoaded(card);
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
        ensureCardLoaded(prev);
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
            case ReportesPanel.CARD_REP_MODISTAS:  return "Reporte de artículos modistas";
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
            case "Agregar datos de factura":
            
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
            // Aquí NO usamos pantalla de carga, no vale la pena complicarlo.
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

        // Aquí usamos la pantalla de carga (asíncrona dentro del EDT)
        crearMenuConPantallaCarga(usuario);
    });
}
// ====== Pantalla de carga ======
private static JDialog crearDialogoCarga() {
    JDialog dlg = new JDialog((Frame) null, false); // no modal
    dlg.setUndecorated(true);                      // sin barra de título

    // Panel raíz
    JPanel root = new JPanel(new BorderLayout());
    root.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 130, 170), 2, true),
            BorderFactory.createEmptyBorder(14, 18, 16, 18)
    ));
    root.setBackground(new Color(252, 248, 252)); // casi blanco, tono lila
    root.setPreferredSize(new Dimension(420, 160)); // tamaño decente

    // Panel de contenido vertical
    JPanel content = new JPanel();
    content.setOpaque(false);
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

    JLabel lblTitulo = new JLabel("MIANOVIAS", SwingConstants.CENTER);
    lblTitulo.setAlignmentX(Component.CENTER_ALIGNMENT);
    lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 22f));
    lblTitulo.setForeground(new Color(130, 70, 130));

    JLabel lblSub = new JLabel("Cargando el sistema, por favor espera…", SwingConstants.CENTER);
    lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
    lblSub.setFont(lblSub.getFont().deriveFont(Font.PLAIN, 13f));

    JProgressBar bar = new JProgressBar();
    bar.setIndeterminate(true);
    bar.setAlignmentX(Component.CENTER_ALIGNMENT);
    bar.setPreferredSize(new Dimension(260, 18));
    bar.setMaximumSize(new Dimension(260, 18));

    JLabel lblHint = new JLabel("Preparando módulos de ventas, clientes e inventario…");
    lblHint.setAlignmentX(Component.CENTER_ALIGNMENT);
    lblHint.setFont(lblHint.getFont().deriveFont(Font.ITALIC, 11f));
    lblHint.setForeground(new Color(120, 120, 120));

    content.add(Box.createVerticalStrut(4));
    content.add(lblTitulo);
    content.add(Box.createVerticalStrut(6));
    content.add(lblSub);
    content.add(Box.createVerticalStrut(14));
    content.add(bar);
    content.add(Box.createVerticalStrut(8));
    content.add(lblHint);

    root.add(content, BorderLayout.CENTER);

    dlg.setContentPane(root);
    dlg.pack();
    dlg.setLocationRelativeTo(null); // centro de la pantalla
    dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    dlg.setResizable(false);
    dlg.setAlwaysOnTop(true);

    return dlg;
}


/**
 * Muestra la pantalla de carga y, en el siguiente ciclo del EDT,
 * construye el menú principal y cierra el diálogo.
 */
private static void crearMenuConPantallaCarga(final Modelo.Asesor usuario) {
    // Este método se está llamando desde el EDT en tu main y en cerrarSesion
    final JDialog dlg = crearDialogoCarga();
    dlg.setVisible(true);   // se agenda la pintura del diálogo

    // Dejamos que el EDT pinte el diálogo, y en el siguiente "turno"
    // construimos el menú principal (que sí tarda) y cerramos la pantalla de carga.
    SwingUtilities.invokeLater(() -> {
        try {
            new menuPrincipal(usuario);  // el constructor YA hace setVisible(true)
        } catch (Throwable ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Ocurrió un error al abrir el sistema:\n" + ex.getMessage(),
                    "Error de inicio",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        } finally {
            dlg.dispose();
        }
    });
}

}
