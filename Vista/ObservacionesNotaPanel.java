package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Utilidades.TelefonosUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ObservacionesNotaPanel extends JPanel {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Búsqueda por folio
    private final JTextField txtFolio   = new JTextField(14);
    private final JButton    btnFolio   = new JButton("Cargar por folio");

    // Búsqueda por teléfono / nombre
    private final JTextField txtTelefono = new JTextField(12);
    private final JButton    btnTelefono = new JButton("Buscar por teléfono");
    private final JButton    btnNombre   = new JButton("Buscar por nombre…");

    // Tabla de notas del cliente
    private final DefaultTableModel modelNotas = new DefaultTableModel(
            new String[]{"#", "Folio", "Tipo", "Fecha", "Total", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    // Tabla de artículos de la nota seleccionada
    private final DefaultTableModel modelDetalle = new DefaultTableModel(
            new String[]{"Artículo", "Marca", "Talla", "Color", "Precio", "Subtotal", "St."}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbDetalle = new JTable(modelDetalle);

    // Info + observaciones
    private final JLabel    lblInfo       = new JLabel("Sin nota cargada");
    private final JTextArea txaObs        = new JTextArea(5, 30);
    private final JButton   btnGuardar    = new JButton("Guardar observaciones");
    private final JButton   btnReimprimir = new JButton("Reimprimir nota");
    private final JLabel    lblStatus     = new JLabel(" ");

    private Integer numeroNotaActual = null;
    private String  folioActual      = null;
    private final NotasDAO dao = new NotasDAO();

    public ObservacionesNotaPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Búsqueda ──────────────────────────────────────────────────────────
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel filaFolio = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filaFolio.add(new JLabel("Folio:"));
        filaFolio.add(txtFolio);
        filaFolio.add(btnFolio);

        JPanel filaTel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filaTel.add(new JLabel("Teléfono cliente:"));
        filaTel.add(txtTelefono);
        filaTel.add(btnTelefono);
        filaTel.add(btnNombre);

        top.add(filaFolio);
        top.add(filaTel);
        add(top, BorderLayout.NORTH);

        TelefonosUI.instalar(txtTelefono);

        // ── Tabla de notas (izquierda) ────────────────────────────────────────
        tbNotas.setRowHeight(22);
        tbNotas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tbNotas.getColumnModel().getColumn(0).setMinWidth(0);
        tbNotas.getColumnModel().getColumn(0).setMaxWidth(0);
        tbNotas.getColumnModel().getColumn(0).setPreferredWidth(0);

        JPanel panelNotas = new JPanel(new BorderLayout());
        panelNotas.setBorder(BorderFactory.createTitledBorder("Notas del cliente"));
        panelNotas.add(new JScrollPane(tbNotas), BorderLayout.CENTER);

        // ── Panel derecho: info + detalle + observaciones ─────────────────────
        JPanel panelDerecho = new JPanel(new BorderLayout(6, 6));
        panelDerecho.setBorder(BorderFactory.createTitledBorder("Detalle de la nota"));

        lblInfo.setFont(lblInfo.getFont().deriveFont(Font.BOLD));
        panelDerecho.add(lblInfo, BorderLayout.NORTH);

        // Tabla de artículos
        tbDetalle.setRowHeight(21);
        tbDetalle.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tbDetalle.getColumnModel().getColumn(6).setPreferredWidth(30); // columna St.
        JPanel panelDetalle = new JPanel(new BorderLayout());
        panelDetalle.setBorder(BorderFactory.createTitledBorder("Artículos de la nota"));
        panelDetalle.add(new JScrollPane(tbDetalle), BorderLayout.CENTER);

        // Área de observaciones
        JPanel panelObsForm = new JPanel(new BorderLayout(4, 4));
        panelObsForm.setBorder(BorderFactory.createTitledBorder("Observaciones"));

        txaObs.setLineWrap(true);
        txaObs.setWrapStyleWord(true);
        txaObs.setEnabled(false);
        panelObsForm.add(new JScrollPane(txaObs), BorderLayout.CENTER);

        btnGuardar.setEnabled(false);
        btnGuardar.setFont(btnGuardar.getFont().deriveFont(Font.BOLD));
        btnReimprimir.setEnabled(false);

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        panelBoton.add(btnReimprimir);
        panelBoton.add(btnGuardar);
        panelObsForm.add(panelBoton, BorderLayout.SOUTH);

        // Split vertical: artículos arriba, observaciones abajo
        JSplitPane innerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelDetalle, panelObsForm);
        innerSplit.setResizeWeight(0.55);
        panelDerecho.add(innerSplit, BorderLayout.CENTER);

        // ── Split horizontal: lista de notas | detalle+obs ────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelNotas, panelDerecho);
        split.setResizeWeight(0.36);
        add(split, BorderLayout.CENTER);

        // ── Estado ────────────────────────────────────────────────────────────
        lblStatus.setForeground(new Color(90, 90, 90));
        add(lblStatus, BorderLayout.SOUTH);

        // ── Eventos ───────────────────────────────────────────────────────────
        txtFolio.addActionListener(_e    -> buscarPorFolio());
        btnFolio.addActionListener(_e    -> buscarPorFolio());
        txtTelefono.addActionListener(_e -> buscarPorTelefonoAccion());
        btnTelefono.addActionListener(_e -> buscarPorTelefonoAccion());
        btnNombre.addActionListener(_e   -> seleccionarPorNombre());
        btnGuardar.addActionListener(_e    -> guardar());
        btnReimprimir.addActionListener(_e -> guardarYReimprimir());

        tbNotas.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) cargarNotaSeleccionada();
        });
    }

    // ── Búsqueda por folio ─────────────────────────────────────────────────────

    private void buscarPorFolio() {
        String folio = txtFolio.getText().trim();
        if (folio.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe el folio de la nota.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Nota n = dao.buscarNotaPorFolio(folio);
            if (n == null) {
                limpiarNota();
                JOptionPane.showMessageDialog(this,
                        "No se encontró ninguna nota con folio: " + folio,
                        "No encontrado", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String fecha = n.getFechaRegistro() != null
                    ? n.getFechaRegistro().toLocalDate().format(FMT) : "—";
            String total = n.getTotal() != null
                    ? String.format("$%,.2f", n.getTotal()) : "—";
            cargarNota(n.getNumeroNota(), n.getFolio(), n.getTipo(), fecha, total);
            lblStatus.setForeground(new Color(90, 90, 90));
            lblStatus.setText("Nota " + n.getFolio() + " cargada por folio.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Búsqueda por teléfono ──────────────────────────────────────────────────

    private void buscarPorTelefonoAccion() {
        String tel = TelefonosUI.soloDigitos(txtTelefono.getText().trim());
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe el teléfono del cliente.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        buscarPorTelefono(tel);
    }

    private void buscarPorTelefono(String tel) {
        limpiarNota();
        modelNotas.setRowCount(0);
        try {
            List<NotasDAO.NotaResumen> lista = dao.listarNotasPorTelefonoResumen(tel);
            if (lista.isEmpty()) {
                lblStatus.setForeground(new Color(90, 90, 90));
                lblStatus.setText("No se encontraron notas para el teléfono: "
                        + TelefonosUI.formatear(tel));
                return;
            }
            for (NotasDAO.NotaResumen r : lista) {
                modelNotas.addRow(new Object[]{
                        r.numero,
                        r.folio  != null ? r.folio : "",
                        r.tipo,
                        r.fecha  != null ? r.fecha.format(FMT) : "—",
                        r.total  != null ? String.format("$%,.2f", r.total) : "—",
                        r.status
                });
            }
            lblStatus.setForeground(new Color(90, 90, 90));
            lblStatus.setText(lista.size() + " nota(s) encontrada(s). Selecciona una para ver su detalle.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar notas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Búsqueda por nombre ────────────────────────────────────────────────────

    private void seleccionarPorNombre() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        DlgBusquedaCliente dlg = new DlgBusquedaCliente(owner);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        ClienteResumen cr = dlg.getSeleccionado();
        if (cr == null) return;
        String tel = TelefonosUI.soloDigitos(cr.getTelefono1());
        if (tel != null && !tel.isEmpty()) {
            txtTelefono.setText(tel); // el filtro lo formatea automáticamente
            buscarPorTelefono(tel);
        } else {
            JOptionPane.showMessageDialog(this,
                    "El cliente seleccionado no tiene teléfono registrado.",
                    "Sin teléfono", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ── Selección en tabla de notas ────────────────────────────────────────────

    private void cargarNotaSeleccionada() {
        int fila = tbNotas.getSelectedRow();
        if (fila < 0) { limpiarNota(); return; }
        int    num   = (int)    modelNotas.getValueAt(fila, 0);
        String folio = (String) modelNotas.getValueAt(fila, 1);
        String tipo  = (String) modelNotas.getValueAt(fila, 2);
        String fecha = (String) modelNotas.getValueAt(fila, 3);
        String total = (String) modelNotas.getValueAt(fila, 4);
        cargarNota(num, folio, tipo, fecha, total);
    }

    private void cargarNota(int num, String folio, String tipo, String fecha, String total) {
        numeroNotaActual = num;
        folioActual      = folio;
        lblInfo.setText("Nota #" + num + "  |  Folio: " + folio
                + "  |  Tipo: " + tipo + "  |  Fecha: " + fecha + "  |  Total: " + total);

        // ── Cargar artículos de la nota ────────────────────────────────────────
        modelDetalle.setRowCount(0);
        try {
            List<NotaDetalle> detalles = dao.listarDetalleDeNota(num);
            for (NotaDetalle d : detalles) {
                modelDetalle.addRow(new Object[]{
                    d.getArticulo() != null ? d.getArticulo() : "",
                    d.getMarca()    != null ? d.getMarca()    : "",
                    d.getTalla()    != null ? d.getTalla()    : "",
                    d.getColor()    != null ? d.getColor()    : "",
                    d.getPrecio()   != null ? String.format("$%,.2f", d.getPrecio())    : "",
                    d.getSubtotal() != null ? String.format("$%,.2f", d.getSubtotal()) : "",
                    d.getStatus()   != null ? d.getStatus()  : "A"
                });
            }
        } catch (SQLException ex) {
            lblStatus.setForeground(Color.RED);
            lblStatus.setText("Error al cargar detalle: " + ex.getMessage());
        }

        // ── Las notas tipo AB no llevan observaciones ──────────────────────────
        boolean esAB = "AB".equalsIgnoreCase(tipo != null ? tipo.trim() : "");
        if (esAB) {
            txaObs.setText("Las notas de tipo Abono no requieren observaciones.");
            txaObs.setEnabled(false);
            btnGuardar.setEnabled(false);
            btnReimprimir.setEnabled(true);   // se puede reimprimir aunque no tenga obs
            return;
        }

        // ── Cargar observaciones existentes ────────────────────────────────────
        try {
            String obs = dao.obtenerObservacionesDeNota(num);
            txaObs.setText(obs);
            txaObs.setCaretPosition(0);
            txaObs.setEnabled(true);
            btnGuardar.setEnabled(true);
            btnReimprimir.setEnabled(true);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar observaciones: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarNota() {
        numeroNotaActual = null;
        folioActual      = null;
        lblInfo.setText("Sin nota cargada");
        modelDetalle.setRowCount(0);
        txaObs.setText("");
        txaObs.setEnabled(false);
        btnGuardar.setEnabled(false);
        btnReimprimir.setEnabled(false);
    }

    // ── Guardar y reimprimir ───────────────────────────────────────────────────

    private void guardarYReimprimir() {
        if (numeroNotaActual == null || folioActual == null || folioActual.isBlank()) {
            JOptionPane.showMessageDialog(this, "No hay nota cargada.",
                    "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 1) Guardar observaciones (si el área está habilitada)
        if (txaObs.isEnabled()) {
            guardar();
        }
        // 2) Reimprimir usando la lógica existente de ReimprimirNotaPanel
        ReimprimirNotaPanel rp = new ReimprimirNotaPanel();
        rp.reimprimirPorFolio(folioActual);
    }

    // ── Guardar ────────────────────────────────────────────────────────────────

    private void guardar() {
        if (numeroNotaActual == null) return;
        try {
            dao.guardarObservacionesDeNota(numeroNotaActual, txaObs.getText().trim());
            lblStatus.setForeground(new Color(0, 120, 0));
            lblStatus.setText("Observaciones guardadas correctamente.");
        } catch (SQLException ex) {
            lblStatus.setForeground(Color.RED);
            lblStatus.setText("Error al guardar: " + ex.getMessage());
        }
    }

    // ── Diálogo de búsqueda de cliente por nombre ──────────────────────────────

    private static class DlgBusquedaCliente extends JDialog {

        private final JTextField txtNombre = new JTextField();
        private final DefaultTableModel modelo = new DefaultTableModel(
                new String[]{"Nombre completo", "Teléfono", "Teléfono 2",
                             "Evento", "Prueba 1", "Prueba 2", "Entrega"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        private final JTable tabla = new JTable(modelo);
        private List<ClienteResumen> resultados = new ArrayList<>();
        private ClienteResumen seleccionado;

        DlgBusquedaCliente(Window owner) {
            super(owner, "Buscar cliente por nombre / apellido",
                    ModalityType.APPLICATION_MODAL);
            construirUI();
        }

        private void construirUI() {
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel top = new JPanel(new BorderLayout(5, 0));
            top.add(new JLabel("Nombre / Apellido:"), BorderLayout.WEST);
            top.add(txtNombre, BorderLayout.CENTER);
            JButton btnBuscar = new JButton("Buscar");
            top.add(btnBuscar, BorderLayout.EAST);

            tabla.setRowHeight(22);
            tabla.setAutoCreateRowSorter(true);

            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnSel    = new JButton("Seleccionar");
            JButton btnCancel = new JButton("Cancelar");
            botones.add(btnCancel);
            botones.add(btnSel);

            main.add(top,                    BorderLayout.NORTH);
            main.add(new JScrollPane(tabla), BorderLayout.CENTER);
            main.add(botones,                BorderLayout.SOUTH);
            setContentPane(main);
            setSize(800, 400);

            btnBuscar.addActionListener(_e -> buscar());
            txtNombre.addActionListener(_e -> buscar());
            btnSel.addActionListener(_e    -> seleccionar());
            btnCancel.addActionListener(_e -> dispose());
            tabla.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) seleccionar();
                }
            });
        }

        private void buscar() {
            String filtro = txtNombre.getText().trim();
            if (filtro.isEmpty()) return;
            try {
                resultados = new clienteDAO().buscarOpcionesPorNombreOApellidos(filtro);
                modelo.setRowCount(0);
                DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                for (ClienteResumen cr : resultados) {
                    modelo.addRow(new Object[]{
                            cr.getNombreCompleto(),
                            cr.getTelefono1(),
                            cr.getTelefono2() != null ? cr.getTelefono2() : "",
                            cr.getFechaEvento()  != null ? cr.getFechaEvento().format(f)  : "",
                            cr.getFechaPrueba1() != null ? cr.getFechaPrueba1().format(f) : "",
                            cr.getFechaPrueba2() != null ? cr.getFechaPrueba2().format(f) : "",
                            cr.getFechaEntrega() != null ? cr.getFechaEntrega().format(f) : ""
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error al buscar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void seleccionar() {
            int fila = tabla.getSelectedRow();
            if (fila < 0) return;
            int modelRow = tabla.convertRowIndexToModel(fila);
            if (modelRow < resultados.size()) {
                seleccionado = resultados.get(modelRow);
            }
            dispose();
        }

        ClienteResumen getSeleccionado() { return seleccionado; }
    }
}
