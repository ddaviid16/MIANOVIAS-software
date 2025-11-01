package Vista;

import Controlador.ExportadorCSV;
import Controlador.ObsequioInvDAO;
import Modelo.ObsequioInv;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ObsequiosInvPanel extends JPanel {

    private JTextField txtFiltro;
    private JTable tb;
    private DefaultTableModel model;
    private final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public ObsequiosInvPanel() {
        setLayout(new BorderLayout());

        // ---- Barra superior ----
        JPanel top = new JPanel(new BorderLayout());

        // Panel izquierdo
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btNuevo = new JButton("Nuevo registro");
        left.add(btNuevo);

        JButton btExportar = new JButton("Exportar CSV");
        btExportar.addActionListener(_e -> exportarCSV());
        left.add(btExportar);

        top.add(left, BorderLayout.WEST);

        // Panel derecho
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        txtFiltro = new JTextField(30);
        JButton btnBuscar = new JButton("Buscar");
        JButton btnActualizar = new JButton("Actualizar");

        rightPanel.add(new JLabel("Buscar (artículo / talla / color):"));
        rightPanel.add(txtFiltro);
        rightPanel.add(btnBuscar);
        rightPanel.add(btnActualizar);

        top.add(rightPanel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        // ENTER en el buscador = Buscar
        txtFiltro.addActionListener(_e -> cargar());
        btNuevo.addActionListener(_e -> {
            // Obtener owner (Frame) desde este panel
            Window w = SwingUtilities.getWindowAncestor(ObsequiosInvPanel.this);
            Frame owner = (w instanceof Frame) ? (Frame) w : null;

            DialogObsequio dlg = new DialogObsequio(owner, null);
            dlg.setVisible(true);
            if (dlg.isGuardado()) cargar();
        });
        

        // ====== Tabla
        String[] cols = {"Código","Artículo","Marca","Modelo","Talla","Color",
                "Precio","Desc.%","Precio final","Exist.","Status","Registro","Editar"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int col) { return col == 12; }
        };
        tb = new JTable(model);
        tb.setRowHeight(26);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);

        TableColumnModel cm = tb.getColumnModel();
        cm.getColumn(0).setCellRenderer(center); // código
        cm.getColumn(6).setCellRenderer(right);  // precio
        cm.getColumn(7).setCellRenderer(right);  // desc.%
        cm.getColumn(8).setCellRenderer(right);  // precio final
        cm.getColumn(9).setCellRenderer(center); // existencia
        cm.getColumn(10).setCellRenderer(center); // status
        cm.getColumn(11).setCellRenderer(center); // registro

        // Botón Editar
        new ButtonColumn(tb, new AbstractAction("Editar") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = Integer.parseInt(e.getActionCommand());
                int mr  = tb.convertRowIndexToModel(row);
                int codigo = Integer.parseInt(model.getValueAt(mr, 0).toString());
                try {
                    ObsequioInvDAO dao = new ObsequioInvDAO();
                    ObsequioInv o = dao.obtener(codigo);

                    Window w = SwingUtilities.getWindowAncestor(ObsequiosInvPanel.this);
                    Frame owner = (w instanceof Frame) ? (Frame) w : null;

                    DialogObsequio dlg = new DialogObsequio(owner, o);
                    dlg.setVisible(true);
                    if (dlg.isGuardado()) cargar();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ObsequiosInvPanel.this,
                            "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }, 12);

        add(new JScrollPane(tb), BorderLayout.CENTER);

        // Carga inicial
        cargar();
    }

    private void cargar() {
        try {
            ObsequioInvDAO dao = new ObsequioInvDAO();
            List<ObsequioInv> lista = dao.listar(); // si prefieres, cambia a listar(filtro) en tu DAO
            String q = txtFiltro.getText().trim().toLowerCase();

            model.setRowCount(0);

            for (ObsequioInv o : lista) {
                // Filtro simple por artículo/talla/color en memoria
                if (!q.isEmpty()) {
                    String haystack = (nz(o.getArticulo()) + " " + nz(o.getTalla()) + " " + nz(o.getColor()))
                            .toLowerCase();
                    if (!haystack.contains(q)) continue;
                }
                double precio = o.getPrecio() == null ? 0.0 : o.getPrecio();
                double pdesc  = o.getDescuento() == null ? 0.0 : o.getDescuento();
                double pfinal = precio * (1 - pdesc / 100.0);

                model.addRow(new Object[]{
                        o.getCodigoArticulo(),
                        nz(o.getArticulo()),
                        nz(o.getMarca()),
                        nz(o.getModelo()),
                        nz(o.getTalla()),
                        nz(o.getColor()),
                        String.format("%.2f", precio),
                        String.format("%.2f", pdesc),
                        String.format("%.2f", pfinal),
                        o.getExistencia() == null ? "" : o.getExistencia(),
                        nz(o.getStatus()),
                        fmt(o.getFechaRegistro()),
                        "Editar"
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar inventario de obsequios: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String nz(String s) { return s == null ? "" : s; }
    private String fmt(LocalDate d) { return d == null ? "" : d.format(MX); }

    // Botón en columna (reutilizable)
    static class ButtonColumn extends AbstractCellEditor
            implements javax.swing.table.TableCellRenderer,
                       javax.swing.table.TableCellEditor,
                       java.awt.event.ActionListener {
        private final JTable table;
        private final Action action;
        private final JButton renderButton = new JButton("Editar");
        private final JButton editButton   = new JButton("Editar");
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
        @Override public Object getCellEditorValue(){ return "Editar"; }
        @Override public void actionPerformed(java.awt.event.ActionEvent e){
            int row = table.getEditingRow();
            fireEditingStopped();
            action.actionPerformed(new java.awt.event.ActionEvent(
                    table, java.awt.event.ActionEvent.ACTION_PERFORMED, String.valueOf(row)));
        }
    }
// Método para exportar a CSV
    private void exportarCSV() {
        try {
            List<ObsequioInv> lista = new ObsequioInvDAO().listar(); // Obtener la lista de obsequios

            ExportadorCSV.guardarListaCSV(lista, "inventario_obsequios", "codigoArticulo", "articulo", "marca", "modelo", "talla", "color", "precio", "descuento", "existencia");

            JOptionPane.showMessageDialog(this, "Archivo exportado exitosamente.", "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
