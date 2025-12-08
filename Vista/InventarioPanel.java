package Vista;

import Controlador.ExportadorCSV;
import Controlador.InventarioDAO;
import Modelo.Inventario;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.List;

public class InventarioPanel extends JPanel {

    private final InventarioDAO dao = new InventarioDAO();

    private JTextField txtBuscar;
    private JTable tabla;
    private DefaultTableModel modeloTabla;

    public InventarioPanel() {
        setLayout(new BorderLayout());

        // ---- Barra superior ----
        JPanel top = new JPanel(new BorderLayout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnNuevo = new JButton("Registrar artículo");
        left.add(btnNuevo);
        top.add(left, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        txtBuscar = new JTextField(30);
        JButton btnBuscar = new JButton("Buscar");
        JButton btnActualizar = new JButton("Actualizar");
        rightPanel.add(new JLabel("Buscar (código / artículo / talla / color):"));
        rightPanel.add(txtBuscar);
        rightPanel.add(btnBuscar);
        rightPanel.add(btnActualizar);
        top.add(rightPanel, BorderLayout.EAST);

        JButton btExportar = new JButton("Exportar CSV");
        btExportar.addActionListener(_e -> exportarCSV());

        // Aquí agregamos el botón de exportar
        left.add(btExportar);
        top.add(left, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);

        // ---- Tabla ----
        String[] cols = {
            "Código", "Artículo", "Desc. 1", "Desc. 2",
            "Marca", "Modelo", "Talla", "Color",
            "Precio", "Desc.%", "Precio final",
            "Exist.", "Novia", "Conteo",
            "Status", "Registro",
            "Remisión", "Factura", "F. pago",
            "Modificar"
    };

        modeloTabla = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return column == 19; // solo la columna del botón "Modificar"
            }
        };
        tabla = new JTable(modeloTabla);

        tabla.setRowHeight(26);
        tabla.setAutoCreateRowSorter(true);
        tabla.getTableHeader().setReorderingAllowed(false);

        // Render numérico alineado a la derecha
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        tabla.getColumnModel().getColumn(8).setCellRenderer(rightRenderer);  // Precio
        tabla.getColumnModel().getColumn(9).setCellRenderer(rightRenderer);  // Desc.%
        tabla.getColumnModel().getColumn(10).setCellRenderer(rightRenderer); // Precio final
        tabla.getColumnModel().getColumn(11).setCellRenderer(rightRenderer); // Exist.
        tabla.getColumnModel().getColumn(13).setCellRenderer(rightRenderer); // Conteo


        new ButtonColumn(tabla, new AbstractAction("Modificar") {
        @Override public void actionPerformed(ActionEvent e) {
            int row = Integer.parseInt(e.getActionCommand());
            int modelRow = tabla.convertRowIndexToModel(row);
            String codigo = (String) modeloTabla.getValueAt(modelRow, 0);
            abrirDialogoModificar(codigo);
        }
    }, 19);   // <-- índice nuevo de "Modificar"


        add(new JScrollPane(tabla), BorderLayout.CENTER);

        // ---- Eventos ----
        btnNuevo.addActionListener(_e -> abrirDialogoNuevo());
        btnBuscar.addActionListener(_e -> cargarTabla(txtBuscar.getText()));
        btnActualizar.addActionListener(_e -> { txtBuscar.setText(""); cargarTabla(null); });
        txtBuscar.addActionListener(_e -> btnBuscar.doClick()); // Enter = buscar

        // Primera carga
        cargarTabla(null);
    }

    private void cargarTabla(String filtro) {
        try {
            List<Inventario> lista = dao.listar(filtro);
            modeloTabla.setRowCount(0);
            for (Inventario i : lista) {
                Double precio = i.getPrecio();
                Double desc   = i.getDescuento();
                Double finalP = (precio == null) ? null :
                        precio * (1.0 - ((desc == null ? 0.0 : desc) / 100.0));
                Integer conteo  = i.getInventarioConteo();  // nuevo campo

                Object[] row = {
                    i.getCodigoArticulo(),                         // 0
                    i.getArticulo(),                               // 1
                    n(i.getDescripcion1()),                        // 2
                    n(i.getDescripcion2()),                        // 3
                    n(i.getMarca()),                               // 4
                    n(i.getModelo()),                              // 5
                    n(i.getTalla()),                               // 6
                    n(i.getColor()),                               // 7
                    precio == null ? null : String.format("%.2f", precio),   // 8
                    desc   == null ? null : String.format("%.2f", desc),     // 9
                    finalP == null ? null : String.format("%.2f", finalP),   // 10
                    i.getExistencia(),                                             // 11
                    n(i.getNombreNovia()),                                        // 12  ← NUEVO
                    conteo == null ? 0 : conteo,                                  // 13
                    i.getStatus(),                                                // 14
                    i.getFechaRegistro() == null ? "" : i.getFechaRegistro().toString(), // 15
                    n(i.getRemision()),                                           // 16
                    n(i.getFactura()),                                            // 17
                    i.getFechaPago() == null ? "" : i.getFechaPago().toString(),  // 18
                    "Modificar"                                                   // 19
                };


                modeloTabla.addRow(row);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar inventario: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirDialogoNuevo() {
        // Obtener el Frame dueño para el diálogo
        Window w = SwingUtilities.getWindowAncestor(this);
        Frame owner = (w instanceof Frame) ? (Frame) w : null;

        DialogArticulo dlg = new DialogArticulo(owner);
        dlg.setVisible(true);
        if (dlg.isGuardado()) cargarTabla(txtBuscar.getText());
    }

    private void abrirDialogoModificar(String codigo) {
        try {
            Inventario inv = dao.buscarPorCodigo(codigo);
            if (inv == null) {
                JOptionPane.showMessageDialog(this, "No se encontró el artículo con código: " + codigo,
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Window w = SwingUtilities.getWindowAncestor(this);
            Frame owner = (w instanceof Frame) ? (Frame) w : null;

            DialogArticulo dlg = new DialogArticulo(owner, inv);
            dlg.setVisible(true);
            if (dlg.isGuardado()) cargarTabla(txtBuscar.getText());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al consultar artículo: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String n(String s) { return s == null ? "" : s; }

    // ---- Botón en columna ----
    static class ButtonColumn extends AbstractCellEditor
            implements TableCellRenderer, TableCellEditor, java.awt.event.ActionListener {
        private final JTable table;
        private final Action action;
        private final JButton renderButton = new JButton("Modificar");
        private final JButton editButton   = new JButton("Modificar");

        public ButtonColumn(JTable table, Action action, int column) {
            this.table = table;
            this.action = action;
            editButton.setFocusPainted(false);
            editButton.addActionListener(this);
            TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(column).setCellRenderer(this);
            columnModel.getColumn(column).setCellEditor(this);
        }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            renderButton.setText(value == null ? "Modificar" : value.toString());
            return renderButton;
        }
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int col) {
            editButton.setText(value == null ? "Modificar" : value.toString());
            return editButton;
        }
        @Override public Object getCellEditorValue() { return "Modificar"; }
        @Override public void actionPerformed(ActionEvent e) {
            int row = table.getEditingRow();
            fireEditingStopped();
            action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, String.valueOf(row)));
        }
    }

    // === main de prueba (opcional) ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Inventario");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setExtendedState(JFrame.MAXIMIZED_BOTH); // abrir maximizada
            f.setContentPane(new InventarioPanel());
            f.setVisible(true);
        });
    }
    // Método para exportar a CSV
    private void exportarCSV() {
        try {
            List<Inventario> lista = new InventarioDAO().listar(null); // Obtener la lista de inventarios

            ExportadorCSV.guardarListaCSV(lista, "inventario_articulos", "codigoArticulo", "articulo", "marca", "modelo", "talla", "color", "precio", "descuento", "existencia");

            JOptionPane.showMessageDialog(this, "Archivo exportado exitosamente.", "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
