package Vista;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import Controlador.ExportadorCSV;
import Controlador.ObsequioInvDAO;
import Modelo.ObsequioInv;

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
        JButton btNuevo = new JButton("Registrar obsequio");
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

        // Actualizado el texto para que coincida con lo que realmente busca el DAO
        rightPanel.add(new JLabel("Buscar (código / artículo / marca / modelo / talla / color):"));
        rightPanel.add(txtFiltro);
        rightPanel.add(btnBuscar);
        rightPanel.add(btnActualizar);

        top.add(rightPanel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        // === Listeners de búsqueda / actualización ===
        btnBuscar.addActionListener(_e -> cargar(txtFiltro.getText()));
        btnActualizar.addActionListener(_e -> {
            txtFiltro.setText("");
            cargar(null);   // sin filtro, lista completa
        });

        // ENTER en el buscador = como si se presionara "Buscar"
        txtFiltro.addActionListener(_e -> btnBuscar.doClick());

        // Nuevo obsequio
        btNuevo.addActionListener(_e -> {
            Window w = SwingUtilities.getWindowAncestor(ObsequiosInvPanel.this);
            Frame owner = (w instanceof Frame) ? (Frame) w : null;

            DialogObsequio dlg = new DialogObsequio(owner, null);
            dlg.setVisible(true);
            if (dlg.isGuardado()) cargar(txtFiltro.getText());
        });

        // ====== Tabla (solo columnas necesarias)
        String[] cols = {"Código","Artículo","Registro","Editar"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int col) {
                // Solo la columna "Editar" es editable (botón)
                return col == 3;
            }
        };

        tb = new JTable(model);
        tb.setRowHeight(26);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);

        TableColumnModel cm = tb.getColumnModel();
        cm.getColumn(0).setCellRenderer(center); // Código
        cm.getColumn(2).setCellRenderer(center); // Registro
        // La columna 3 es el botón, no necesita renderer especial aquí


        // AHORA: columna 3
        new ButtonColumn(tb, new AbstractAction("Editar") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = Integer.parseInt(e.getActionCommand());
                int mr  = tb.convertRowIndexToModel(row);
                String codigo = model.getValueAt(mr, 0).toString();
                try {
                    ObsequioInvDAO dao = new ObsequioInvDAO();
                    ObsequioInv o = dao.obtener(codigo);

                    Window w = SwingUtilities.getWindowAncestor(ObsequiosInvPanel.this);
                    Frame owner = (w instanceof Frame) ? (Frame) w : null;

                    DialogObsequio dlg = new DialogObsequio(owner, o);
                    dlg.setVisible(true);
                    if (dlg.isGuardado()) cargar(txtFiltro.getText());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ObsequiosInvPanel.this,
                            "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }, 3);


        add(new JScrollPane(tb), BorderLayout.CENTER);

        // Carga inicial sin filtro
        cargar(null);
    }

    // === Carga con el texto actual del filtro (por comodidad) ===
    private void cargar() {
        cargar(txtFiltro.getText());
    }

    // === Carga desde DAO usando el filtro (incluye código) ===
    private void cargar(String filtro) {
        try {
            ObsequioInvDAO dao = new ObsequioInvDAO();
            List<ObsequioInv> lista = dao.listar(filtro);

            model.setRowCount(0);

            for (ObsequioInv o : lista) {
            model.addRow(new Object[]{
                    o.getCodigoArticulo(),          // Código
                    nz(o.getArticulo()),            // Artículo
                    fmt(o.getFechaRegistro()),      // Registro
                    "Editar"                        // Botón
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
            // exporta todo el inventario de obsequios (sin filtro)
            List<ObsequioInv> lista = new ObsequioInvDAO().listar();

            ExportadorCSV.guardarListaCSV(
                    lista,
                    "inventario_obsequios",
                    "codigoArticulo", "articulo", "marca", "modelo",
                    "talla", "color", "precio", "descuento", "existencia"
            );

            JOptionPane.showMessageDialog(this,
                    "Archivo exportado exitosamente.",
                    "Exportación exitosa",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error al exportar CSV: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
