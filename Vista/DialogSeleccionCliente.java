package Vista; // cambia el paquete si usas otro

import Modelo.ClienteResumen;
import Controlador.clienteDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DialogSeleccionCliente extends JDialog {

    private final clienteDAO clienteDAO;

    private JTextField txtApellido;
    private JButton btnBuscar;
    private JTable tblClientes;
    private DefaultTableModel modeloTabla;
    private JButton btnAceptar;
    private JButton btnCancelar;

    private List<ClienteResumen> resultadosActuales = new ArrayList<>();
    private ClienteResumen clienteSeleccionado;

    private final DateTimeFormatter fmtFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public DialogSeleccionCliente(Window parent, clienteDAO clienteDAO) {
        super(parent, "Seleccionar cliente", ModalityType.APPLICATION_MODAL);
        this.clienteDAO = clienteDAO;
        initComponents();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(750, 400);
        setLayout(new BorderLayout(5, 5));

        // Panel de búsqueda (arriba)
        JPanel pnlBusqueda = new JPanel(new BorderLayout(5, 5));
        JPanel pnlLabelCampo = new JPanel(new FlowLayout(FlowLayout.LEFT));

        pnlLabelCampo.add(new JLabel("Apellido paterno:"));
        txtApellido = new JTextField(20);
        pnlLabelCampo.add(txtApellido);

        btnBuscar = new JButton("Buscar");
        pnlBusqueda.add(pnlLabelCampo, BorderLayout.CENTER);
        pnlBusqueda.add(btnBuscar, BorderLayout.EAST);

        add(pnlBusqueda, BorderLayout.NORTH);

        // Tabla (centro)
        modeloTabla = new DefaultTableModel(
                new Object[]{"Nombre completo", "Teléfono 1", "Teléfono 2", "Parentesco", "Fecha evento"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // solo selección
            }
        };

        tblClientes = new JTable(modeloTabla);
        tblClientes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(tblClientes);
        add(scroll, BorderLayout.CENTER);

        // Panel de botones (abajo)
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnAceptar = new JButton("Aceptar");
        btnCancelar = new JButton("Cancelar");

        pnlBotones.add(btnAceptar);
        pnlBotones.add(btnCancelar);

        add(pnlBotones, BorderLayout.SOUTH);

        // Eventos
        btnBuscar.addActionListener(e -> buscarClientes());

        txtApellido.addActionListener(e -> buscarClientes()); // Enter en el campo

        btnAceptar.addActionListener(e -> seleccionarYSalir());
        btnCancelar.addActionListener(e -> {
            clienteSeleccionado = null;
            dispose();
        });

        // Doble clic en la tabla
        tblClientes.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblClientes.getSelectedRow() >= 0) {
                    seleccionarYSalir();
                }
            }
        });
    }

    private void buscarClientes() {
        String apPat = txtApellido.getText().trim();
        try {
            List<ClienteResumen> lista = clienteDAO.buscarOpcionesPorApellidoPaterno(apPat);
            cargarResultados(lista);
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Error al buscar clientes:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void cargarResultados(List<ClienteResumen> lista) {
        resultadosActuales.clear();
        modeloTabla.setRowCount(0);

        if (lista == null || lista.isEmpty()) {
            return;
        }

        for (ClienteResumen cr : lista) {
            resultadosActuales.add(cr);

            String nombre = cr.getNombreCompleto();
            String tel1 = cr.getTelefono1();
            String tel2 = cr.getTelefono2();
            String parentesco = cr.getParentescoTel2();

            String fechaEventoStr = "";
            if (cr.getFechaEvento() != null) {
                fechaEventoStr = cr.getFechaEvento().format(fmtFecha);
            }

            modeloTabla.addRow(new Object[]{
                    nombre,
                    tel1,
                    tel2,
                    parentesco,
                    fechaEventoStr
            });
        }
    }

    private void seleccionarYSalir() {
        int fila = tblClientes.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un cliente de la lista.", "Aviso",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (fila >= resultadosActuales.size()) {
            JOptionPane.showMessageDialog(this, "La fila seleccionada no es válida.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        clienteSeleccionado = resultadosActuales.get(fila);
        dispose();
    }

    public ClienteResumen getClienteSeleccionado() {
        return clienteSeleccionado;
    }

    /**
     * Helper estático para usarlo fácil desde cualquier panel/ventana.
     */
    public static ClienteResumen mostrarDialogo(Window parent, clienteDAO clienteDAO) {
        DialogSeleccionCliente dlg = new DialogSeleccionCliente(parent, clienteDAO);
        dlg.setVisible(true); // bloquea hasta cerrar
        return dlg.getClienteSeleccionado();
    }
}
