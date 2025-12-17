package Vista;

import Controlador.CambioCodigoService;
import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Utilidades.TelefonosUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel para cambiar el origen de un artículo de una nota:
 *   - Inventario -> Pedido
 *   - Pedido -> Inventario
 */
public class CambioCodigoArticuloPanel extends JPanel {

    private final JTextField txtTel = new JTextField();
    private final JTextField txtNom = ro();

    private final JComboBox<Nota> cbNotas = new JComboBox<>();

    private final DefaultTableModel model;
    private final JTable tb;

    private final JButton btnBuscarCliente = new JButton("Buscar por nombre o apellido…");
    private final JButton btnToPedido     = new JButton("Pasar a PEDIDO");
    private final JButton btnToInventario = new JButton("Pasar a INVENTARIO");

    private final NotasDAO notasDAO = new NotasDAO();
    private final clienteDAO cdao   = new clienteDAO();
    private final CambioCodigoService cambioService = new CambioCodigoService();

    // Mapeo de filas de la tabla a info interna
    private final List<LineaNota> lineas = new ArrayList<>();

    private enum OrigenLinea { INVENTARIO, PEDIDO, MODISTA }

    private static class LineaNota {
        int id;                    // id de Nota_Detalle o de Pedidos
        OrigenLinea origen;
        NotaDetalle detalle;
    }

    public CambioCodigoArticuloPanel() {
        setLayout(new BorderLayout(10,10));

        TelefonosUI.instalar(txtTel, 10);

        // ===== top =====
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int y = 0;

        addCell(top, c, 0, y, new JLabel("Teléfono:"), 1, false);
        addCell(top, c, 1, y, txtTel, 1, true);
        addCell(top, c, 2, y, new JLabel("Nombre:"), 1, false);
        addCell(top, c, 3, y, txtNom, 1, true);
        y++;

        // Fila con combo de notas y botón buscar cliente  
        btnBuscarCliente.addActionListener(_e -> seleccionarClientePorApellido());
        addCell(top, c, 0, y, btnBuscarCliente, 1, false);

        addCell(top, c, 2, y, new JLabel("Folio:"), 1, false);
        cbNotas.setRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Nota n) {
                    String folio = (n.getFolio()==null || n.getFolio().isBlank()) ? "s/folio" : n.getFolio();
                    setText(folio + "  [" + n.getTipo() + "]  Total: " +
                            fmt(n.getTotal()) + "  Saldo: " + fmt(n.getSaldo()));
                } else if (value == null) {
                    setText("— Selecciona —");
                }
                return this;
            }
        });
        cbNotas.addActionListener(_e -> cargarDetalle());
        addCell(top, c, 3, y, cbNotas, 1, true);
        y++;

        add(top, BorderLayout.NORTH);

        // ===== tabla =====
        String[] cols = {"Origen", "Código / ID", "Artículo", "Modelo", "Talla", "Color",
                         "Precio", "%Desc", "Subtotal"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int col) { return false; }
        };
        tb = new JTable(model);
        tb.setRowHeight(24);
        tb.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        tb.getColumnModel().getColumn(6).setCellRenderer(right);
        tb.getColumnModel().getColumn(7).setCellRenderer(right);
        tb.getColumnModel().getColumn(8).setCellRenderer(right);

        // Selección -> habilitar / deshabilitar botones
        tb.getSelectionModel().addListSelectionListener(_e -> actualizarBotones());

        add(new JScrollPane(tb), BorderLayout.CENTER);

        // ===== bottom =====
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnToPedido.addActionListener(this::accionPasarAPedido);
        btnToInventario.addActionListener(this::accionPasarAInventario);
        bottom.add(btnToPedido);
        bottom.add(btnToInventario);

        add(bottom, BorderLayout.SOUTH);

        // hooks de teléfono
        txtTel.addActionListener(_e -> cargarClienteYNotas());
        ((AbstractDocument) txtTel.getDocument())
                .addDocumentListener((SimpleDocListener) this::cargarClienteYNotas);

        actualizarBotones();
    }

    // ================== LÓGICA UI ==================

    private void cargarClienteYNotas() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTel.getText());
        if (tel == null) tel = "";
        if (tel.isEmpty()) {
            limpiar();
            return;
        }

        try {
            // cliente
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                txtNom.setText("— no registrado —");
            } else {
                txtNom.setText(cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto());
            }

            // notas del cliente (CN/CR activas)
            cbNotas.removeAllItems();
            NotasDAO ndao = notasDAO;
            List<Nota> lista = ndao.listarNotasClienteParaDevolucion(tel);
            if (lista.isEmpty()) {
                cbNotas.addItem(null);
            } else {
                for (Nota n : lista) cbNotas.addItem(n);
            }

            cargarDetalle();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error consultando cliente/notas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDetalle() {
        model.setRowCount(0);
        lineas.clear();

        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) {
            actualizarBotones();
            return;
        }

        try {
            List<NotaDetalle> det = notasDAO.listarDetalleDeNota(sel.getNumeroNota());
            if (det == null) det = List.of();

            for (NotaDetalle d : det) {
                String status = nz(d.getStatus());
                if (!status.isEmpty() && !"A".equalsIgnoreCase(status)) {
                    // ignorar renglones cancelados / dados de baja
                    continue;
                }

                OrigenLinea origen = determinarOrigen(d);
                LineaNota ln = new LineaNota();
                ln.id      = d.getId();
                ln.origen  = origen;
                ln.detalle = d;
                lineas.add(ln);

                String cod = (d.getCodigoArticulo() == null || d.getCodigoArticulo().isBlank())
                        ? ("#" + d.getId())
                        : d.getCodigoArticulo();

                model.addRow(new Object[]{
                        origenToLabel(origen),
                        cod,
                        nz(d.getArticulo()),
                        nz(d.getModelo()),
                        nz(d.getTalla()),
                        nz(d.getColor()),
                        fmt(d.getPrecio()),
                        fmt(d.getDescuento()),
                        fmt(d.getSubtotal())
                });
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cargar el detalle: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        actualizarBotones();
    }

    private OrigenLinea determinarOrigen(NotaDetalle d) {
        String cod = d.getCodigoArticulo();
        String art = nz(d.getArticulo()).toUpperCase();

        if (cod != null && !cod.isBlank()) {
            return OrigenLinea.INVENTARIO;
        }
        if (art.startsWith("PEDIDO")) {
            return OrigenLinea.PEDIDO;
        }
        if (art.startsWith("MODISTA")) {
            return OrigenLinea.MODISTA;
        }
        // fallback: lo tratamos como inventario
        return OrigenLinea.INVENTARIO;
    }

    private String origenToLabel(OrigenLinea o) {
        return switch (o) {
            case INVENTARIO -> "INV";
            case PEDIDO     -> "PED";
            case MODISTA    -> "MOD";
        };
    }

    private void actualizarBotones() {
        int row = tb.getSelectedRow();
        if (row < 0 || row >= lineas.size()) {
            btnToPedido.setEnabled(false);
            btnToInventario.setEnabled(false);
            return;
        }
        int modelRow = tb.convertRowIndexToModel(row);
        LineaNota ln = lineas.get(modelRow);

        btnToPedido.setEnabled(ln.origen == OrigenLinea.INVENTARIO);
        btnToInventario.setEnabled(ln.origen == OrigenLinea.PEDIDO);
    }

    // ===== acciones de botones =====

    private void accionPasarAPedido(ActionEvent ev) {
        int row = tb.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un renglón de la tabla.");
            return;
        }
        int modelRow = tb.convertRowIndexToModel(row);
        LineaNota ln = lineas.get(modelRow);
        if (ln.origen != OrigenLinea.INVENTARIO) {
            JOptionPane.showMessageDialog(this,
                    "Solo puedes pasar a PEDIDO renglones de inventario.",
                    "Operación no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Selecciona primero una nota.");
            return;
        }

        int r = JOptionPane.showConfirmDialog(this,
                "¿Pasar este artículo de INVENTARIO a PEDIDO?\n\n" +
                "Artículo: " + nz(ln.detalle.getArticulo()) + "\n" +
                "Modelo: " + nz(ln.detalle.getModelo()) + "   Talla: " + nz(ln.detalle.getTalla()),
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            cambioService.convertirInventarioAPedido(sel.getNumeroNota(), ln.id);
            JOptionPane.showMessageDialog(this,
                    "El artículo se convirtió a PEDIDO y se regresó al inventario.",
                    "Operación completada", JOptionPane.INFORMATION_MESSAGE);
            cargarDetalle();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al convertir a PEDIDO: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void accionPasarAInventario(ActionEvent ev) {
        int row = tb.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un renglón de la tabla.");
            return;
        }
        int modelRow = tb.convertRowIndexToModel(row);
        LineaNota ln = lineas.get(modelRow);
        if (ln.origen != OrigenLinea.PEDIDO) {
            JOptionPane.showMessageDialog(this,
                    "Solo puedes pasar a INVENTARIO renglones de PEDIDO.",
                    "Operación no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Selecciona primero una nota.");
            return;
        }

        int r = JOptionPane.showConfirmDialog(this,
                "¿Pasar este PEDIDO a INVENTARIO?\n\n" +
                "Artículo: " + nz(ln.detalle.getArticulo()) + "\n" +
                "Modelo: " + nz(ln.detalle.getModelo()) + "   Talla: " + nz(ln.detalle.getTalla()),
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            cambioService.convertirPedidoAInventario(sel.getNumeroNota(), ln.id);
            JOptionPane.showMessageDialog(this,
                    "El pedido se convirtió a INVENTARIO y se descontó una existencia.",
                    "Operación completada", JOptionPane.INFORMATION_MESSAGE);
            cargarDetalle();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al convertir a INVENTARIO: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================== BÚSQUEDA POR NOMBRE ==================

    private void seleccionarClientePorApellido() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        DialogBusquedaCliente dlg = new DialogBusquedaCliente(owner);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        ClienteResumen cr = dlg.getSeleccionado();
        if (cr != null) {
            String tel = Utilidades.TelefonosUI.soloDigitos(cr.getTelefono1());
            if (tel != null && !tel.isEmpty()) {
                txtTel.setText(tel);
                cargarClienteYNotas();
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
            JPanel main = new JPanel(new BorderLayout(8,8));
            main.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

            JPanel pnlFiltro = new JPanel(new BorderLayout(5,0));
            pnlFiltro.add(new JLabel("Nombre o apellido:"), BorderLayout.WEST);
            txtApellido = new JTextField();
            pnlFiltro.add(txtApellido, BorderLayout.CENTER);
            JButton btnBuscar = new JButton("Buscar");
            pnlFiltro.add(btnBuscar, BorderLayout.EAST);
            main.add(pnlFiltro, BorderLayout.NORTH);

            modelo = new DefaultTableModel(
                    new Object[]{"Nombre completo", "Teléfono", "Teléfono 2",
                                 "Evento", "Prueba 1", "Prueba 2", "Entrega"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            tabla = new JTable(modelo);
            tabla.setRowHeight(22);
            tabla.setAutoCreateRowSorter(true);
            main.add(new JScrollPane(tabla), BorderLayout.CENTER);

            JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnSeleccionar = new JButton("Seleccionar");
            JButton btnCerrar      = new JButton("Cancelar");
            pnlBotones.add(btnCerrar);
            pnlBotones.add(btnSeleccionar);
            main.add(pnlBotones, BorderLayout.SOUTH);

            setContentPane(main);
            setSize(800, 400);
            setLocationRelativeTo(getOwner());

            btnBuscar.addActionListener(_e -> buscar());
            btnSeleccionar.addActionListener(_e -> seleccionarActual());
            btnCerrar.addActionListener(_e -> dispose());

            tabla.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                        seleccionarActual();
                    }
                }
            });

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
                resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);
                modelo.setRowCount(0);

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                for (ClienteResumen cr : resultados) {
                    modelo.addRow(new Object[]{
                            cr.getNombreCompleto(),
                            cr.getTelefono1(),
                            cr.getTelefono2(),
                            cr.getFechaEvento()  == null ? "" : cr.getFechaEvento().format(fmt),
                            cr.getFechaPrueba1() == null ? "" : cr.getFechaPrueba1().format(fmt),
                            cr.getFechaPrueba2() == null ? "" : cr.getFechaPrueba2().format(fmt),
                            cr.getFechaEntrega() == null ? "" : cr.getFechaEntrega().format(fmt)
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

        public ClienteResumen getSeleccionado() { return seleccionado; }
    }

    // ================== HELPERS GENERALES ==================

    private void limpiar() {
        txtNom.setText("— no registrado —");
        cbNotas.removeAllItems();
        model.setRowCount(0);
        lineas.clear();
        actualizarBotones();
    }

    private JTextField ro() {
        JTextField t = new JTextField();
        t.setEditable(false);
        t.setBackground(new Color(235,235,235));
        return t;
    }

    private void addCell(JPanel p, GridBagConstraints c,
                         int x, int y, JComponent comp,
                         int span, boolean growX) {
        c.gridx = x; c.gridy = y;
        c.gridwidth = span;
        c.weightx = growX ? 1 : 0;
        p.add(comp, c);
        c.gridwidth = 1;
    }

    private String fmt(Double v) {
        return v == null ? "0.00" : String.format("%.2f", v);
    }

    private String nz(String s) {
        return s == null ? "" : s.trim();
    }

    @FunctionalInterface
    private interface SimpleDocListener extends javax.swing.event.DocumentListener {
        void on();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e){ on(); }
    }
}
