package Vista;

import Controlador.ObsequioInvDAO;
import Modelo.ObsequioInv;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DialogSeleccionObsequios extends JDialog {

    private final DefaultTableModel model;
    private final JTable tb;
    private final JTextField txtBuscar = new JTextField();
    private final JLabel lblContador = new JLabel("0 seleccionados (máx 5)");

    // lista de códigos seleccionados
    private List<String> seleccionados;

    public DialogSeleccionObsequios(Frame owner, List<String> obsequiosSel) {
        super(owner, "Seleccionar obsequios (máx 5)", true);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        );

        // ===== CABECERA BUSCADOR =====
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Buscar:"), BorderLayout.WEST);
        top.add(txtBuscar, BorderLayout.CENTER);
        JButton btBuscar = new JButton("Buscar");
        btBuscar.addActionListener(_e -> cargar());
        top.add(btBuscar, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // ===== TABLA =====
        String[] cols = {"Sel", "Código", "Artículo"};
        model = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : Object.class;
            }
            @Override
            public boolean isCellEditable(int r, int c) {
                return c == 0; // solo checkbox
            }

            // controlamos aquí el máximo de 5 seleccionados
            @Override
            public void setValueAt(Object aValue, int row, int column) {
                if (column == 0) {
                    boolean nuevo = Boolean.TRUE.equals(aValue);
                    boolean actual = Boolean.TRUE.equals(getValueAt(row, column));

                    // si quiere pasar de NO a SÍ, revisamos límite
                    if (!actual && nuevo) {
                        int count = contarSeleccionados();
                        if (count >= 5) {
                            JOptionPane.showMessageDialog(
                                    DialogSeleccionObsequios.this,
                                    "Solo puedes seleccionar hasta 5 obsequios.",
                                    "Límite alcanzado",
                                    JOptionPane.WARNING_MESSAGE
                            );
                            return; // no cambia el valor
                        }
                    }
                }
                super.setValueAt(aValue, row, column);
                actualizarContador();
            }
        };

        tb = new JTable(model);
        tb.setRowHeight(24);
        tb.setAutoCreateRowSorter(true);
        tb.getTableHeader().setReorderingAllowed(false);
        tb.setFillsViewportHeight(true);

        // ajustar anchos de columnas para que se vea como en la captura
        TableColumnModel cm = tb.getColumnModel();
        cm.getColumn(0).setPreferredWidth(40);  // Sel
        cm.getColumn(0).setMaxWidth(50);
        cm.getColumn(1).setPreferredWidth(90);  // Código
        cm.getColumn(1).setMaxWidth(120);
        // Artículo ocupa el resto

        JScrollPane sp = new JScrollPane(tb);
        add(sp, BorderLayout.CENTER);

        // ===== PIE =====
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(lblContador, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btAceptar = new JButton("Aceptar");
        JButton btCancelar = new JButton("Cancelar");
        actions.add(btCancelar);
        actions.add(btAceptar);
        bottom.add(actions, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        btCancelar.addActionListener(_e -> {
            seleccionados = null;
            dispose();
        });
        btAceptar.addActionListener(_e -> aceptar());

        // ENTER en el campo buscar
        txtBuscar.addActionListener(_e -> cargar());

        // Datos iniciales
        cargar();

        // aplicar preselección (si venía algo desde la venta)
        if (obsequiosSel != null && !obsequiosSel.isEmpty()) {
            marcarPreseleccion(obsequiosSel);
        }

        actualizarContador();

        // tamaño parecido al de la captura
        setPreferredSize(new Dimension(950, 600));
        pack();
        setLocationRelativeTo(owner);
    }

    private void cargar() {
        try {
            model.setRowCount(0);
            ObsequioInvDAO dao = new ObsequioInvDAO();
            List<ObsequioInv> lista = dao.listarActivosFiltrado(txtBuscar.getText().trim());
            for (ObsequioInv i : lista) {
                model.addRow(new Object[]{
                        Boolean.FALSE,
                        i.getCodigoArticulo(),
                        i.getArticulo()
                });
            }
            actualizarContador();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error cargando obsequios: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void marcarPreseleccion(List<String> obsequiosSel) {
        if (obsequiosSel == null || obsequiosSel.isEmpty()) return;

        for (int r = 0; r < model.getRowCount(); r++) {
            String code = String.valueOf(model.getValueAt(r, 1));
            String name = String.valueOf(model.getValueAt(r, 2));

            for (String s : obsequiosSel) {
                if (s == null) continue;
                String t = s.trim();
                if (t.equals(code) || t.equalsIgnoreCase(name)) {
                    model.setValueAt(Boolean.TRUE, r, 0);
                    break;
                }
            }
        }
        actualizarContador();
    }

    private int contarSeleccionados() {
        int c = 0;
        for (int r = 0; r < model.getRowCount(); r++) {
            if (Boolean.TRUE.equals(model.getValueAt(r, 0))) c++;
        }
        return c;
    }

    private void actualizarContador() {
        int c = contarSeleccionados();
        lblContador.setText(c + " seleccionados (máx 5)");
    }

    private void aceptar() {
        List<String> out = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            if (Boolean.TRUE.equals(model.getValueAt(r, 0))) {
                out.add(String.valueOf(model.getValueAt(r, 1)).trim());
            }
        }
        if (out.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecciona al menos un obsequio.");
            return;
        }
        // si por bug quedó más de 5, recortamos
        if (out.size() > 5) out = out.subList(0, 5);

        seleccionados = out;
        dispose();
    }

    public List<String> getSeleccionados() {
        if (seleccionados != null) return seleccionados;

        List<String> out = new ArrayList<>();
        for (int r = 0; r < tb.getRowCount(); r++) {
            int mr = tb.convertRowIndexToModel(r);
            if (Boolean.TRUE.equals(model.getValueAt(mr, 0))) {
                Object cod = model.getValueAt(mr, 1);
                if (cod != null) out.add(String.valueOf(cod).trim());
            }
        }
        return out;
    }
}
