package Vista;

import Controlador.InventarioDAO;
import Modelo.Inventario;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.List;

public class DialogSeleccionArticulo extends JDialog {

    private JTextField txtBuscar;
    private JTable tabla;
    private DefaultTableModel modelo;
    private Inventario seleccionado;


    
    public DialogSeleccionArticulo(Window owner) {
        super(owner, "Seleccionar artículo", ModalityType.APPLICATION_MODAL);
        setSize(900, 520);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout(6,6));
        top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        top.add(new JLabel("Buscar (código, artículo, marca, modelo, talla o color):"), BorderLayout.WEST);
        txtBuscar = new JTextField();
        top.add(txtBuscar, BorderLayout.CENTER);
        JButton btBuscar = new JButton("Buscar");
        btBuscar.addActionListener(_e -> cargar());
        top.add(btBuscar, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        String[] cols = {"Código","Artículo","Marca","Modelo","Talla","Color","Precio","%Desc","Existencia"};
        modelo = new DefaultTableModel(cols,0){
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount()==2) elegir();
            }
        });
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btAgregar = new JButton("Agregar");
        JButton btCancelar = new JButton("Cancelar");
        btAgregar.addActionListener(_e -> elegir());
        btCancelar.addActionListener(_e -> dispose());
        bottom.add(btCancelar);
        bottom.add(btAgregar);
        add(bottom, BorderLayout.SOUTH);

        cargar(); // carga inicial sin filtro
    }

    private void cargar() {
        try {
            InventarioDAO dao = new InventarioDAO();
            List<Inventario> lista = dao.listarActivosFiltrado(txtBuscar.getText());
            modelo.setRowCount(0);
            for (Inventario i : lista) {
                modelo.addRow(new Object[]{
                        i.getCodigoArticulo(), i.getArticulo(), i.getMarca(), i.getModelo(),
                        i.getTalla(), i.getColor(),
                        i.getPrecio()==null?null:String.format("%.2f", i.getPrecio()),
                        i.getDescuento()==null?null:String.format("%.2f", i.getDescuento()),
                        i.getExistencia()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al buscar: "+e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void elegir() {
    int r = tabla.getSelectedRow();
    if (r < 0) { JOptionPane.showMessageDialog(this,"Selecciona un artículo"); return; }

    String codigo = modelo.getValueAt(r,0).toString();
    try {
        InventarioDAO dao = new InventarioDAO();
        Inventario real = dao.buscarPorCodigo(codigo); // ya trae status + existencia
        if (real == null) {
            JOptionPane.showMessageDialog(this,
                "Ese artículo ya no está disponible (sin existencia o inactivo).");
            return;
        }
        seleccionado = real; // devolver el completo
        dispose();
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this,
            "Error consultando inventario: " + ex.getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    public Inventario getSeleccionado() { return seleccionado; }
}

