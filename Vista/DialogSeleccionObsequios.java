package Vista;

import Controlador.ObsequioInvDAO;
import Modelo.ObsequioInv;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DialogSeleccionObsequios extends JDialog {

    private final DefaultTableModel model;
    private final JTable tb;
    private final JTextField txtBuscar = new JTextField();
    private final JLabel lblContador = new JLabel("0 seleccionados (máx 5)");
    private List<String> seleccionados; // aquí guardaremos **códigos** seleccionados

    public DialogSeleccionObsequios(Frame owner, List<String> preSeleccion) {
        super(owner, "Seleccionar obsequios (máx 5)", true);
        setSize(760, 520);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10,10));

        // Cabecera con búsqueda
        JPanel top = new JPanel(new BorderLayout(6,6));
        top.add(new JLabel("Buscar:"), BorderLayout.WEST);
        top.add(txtBuscar, BorderLayout.CENTER);
        JButton btBuscar = new JButton("Buscar");
        btBuscar.addActionListener(_e -> cargar());
        top.add(btBuscar, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Tabla
        String[] cols = {"Sel","Código","Artículo","Talla","Color","Exist.","Status"};
        model = new DefaultTableModel(cols,0){
            @Override public Class<?> getColumnClass(int col){
                return col==0 ? Boolean.class : Object.class;
            }
            @Override public boolean isCellEditable(int r,int c){ return c==0; }
        };
        tb = new JTable(model);
        tb.setRowHeight(24);
        tb.setAutoCreateRowSorter(true);
        add(new JScrollPane(tb), BorderLayout.CENTER);

        // Pie
        JPanel bottom = new JPanel(new BorderLayout(6,6));
        bottom.add(lblContador, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btAceptar = new JButton("Aceptar");
        JButton btCancelar = new JButton("Cancelar");
        actions.add(btCancelar);
        actions.add(btAceptar);
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        btCancelar.addActionListener(_e -> { seleccionados = null; dispose(); });
        btAceptar.addActionListener(_e -> aceptar());

        // precargar
        cargar();

        // aplicar preselección (acepta lista de códigos o nombres)
        if (preSeleccion != null && !preSeleccion.isEmpty()) {
            marcarPreseleccion(preSeleccion);
        }

        actualizarContador();
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
                        i.getArticulo(),
                        i.getTalla(),
                        i.getColor(),
                        i.getExistencia(),
                        i.getStatus()
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error cargando obsequios: "+e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Marca filas por código (preferente) o por nombre si la preselección trae nombres. */
    private void marcarPreseleccion(List<String> pre) {
        for (int r = 0; r < model.getRowCount(); r++) {
            String code = String.valueOf(model.getValueAt(r,1));
            String name = String.valueOf(model.getValueAt(r,2));
            if (pre.contains(code) || pre.contains(name)) {
                model.setValueAt(Boolean.TRUE, r, 0);
            }
        }
        actualizarContador();
    }

    private void actualizarContador() {
        int c = 0;
        for (int r=0; r<model.getRowCount(); r++) {
            if (Boolean.TRUE.equals(model.getValueAt(r,0))) c++;
        }
        lblContador.setText(c + " seleccionados (máx 5)");
    }

    private void aceptar() {
        List<String> out = new ArrayList<>();
        for (int r=0; r<model.getRowCount(); r++) {
            if (Boolean.TRUE.equals(model.getValueAt(r,0))) {
                out.add(String.valueOf(model.getValueAt(r,1))); // **CÓDIGO**
                if (out.size() == 5) break; // máximo 5
            }
        }
        if (out.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecciona al menos un obsequio.");
            return;
        }
        seleccionados = out;
        dispose();
    }

    /** Devuelve los **códigos** de los obsequios seleccionados. */
    public List<String> getSeleccionados() {
        // Si ya aceptaron, devolvemos lo almacenado:
        if (seleccionados != null) return seleccionados;

        // Si alguien llama sin aceptar (raro), leemos directo de la tabla:
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
