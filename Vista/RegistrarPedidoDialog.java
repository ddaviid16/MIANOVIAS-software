package Vista;

import Controlador.PedidosDAO;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

public class RegistrarPedidoDialog extends JDialog {

    private final JTextField txtArticulo = new JTextField();
    private final JTextField txtMarca    = new JTextField();
    private final JTextField txtModelo   = new JTextField();
    private final JTextField txtTalla    = new JTextField();
    private final JTextField txtColor    = new JTextField();
    private final JFormattedTextField txtPrecio =
            new JFormattedTextField(new java.text.DecimalFormat("0.00"));
    private final JFormattedTextField txtDescPct =
            new JFormattedTextField(new java.text.DecimalFormat("0.00"));

    private PedidosDAO.PedidoDraft result;

    public RegistrarPedidoDialog(Window owner) {
        super(owner, "Registrar artículo a pedir", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int y=0;

        addCell(form,c,0,y,new JLabel("Artículo*:"),1,false); addCell(form,c,1,y,txtArticulo,1,true); y++;
        addCell(form,c,0,y,new JLabel("Marca:"),   1,false); addCell(form,c,1,y,txtMarca,   1,true); y++;
        addCell(form,c,0,y,new JLabel("Modelo:"),  1,false); addCell(form,c,1,y,txtModelo,  1,true); y++;
        addCell(form,c,0,y,new JLabel("Talla:"),   1,false); addCell(form,c,1,y,txtTalla,   1,true); y++;
        addCell(form,c,0,y,new JLabel("Color:"),   1,false); addCell(form,c,1,y,txtColor,   1,true); y++;
        addCell(form,c,0,y,new JLabel("Precio:"),  1,false); addCell(form,c,1,y,txtPrecio,  1,true); y++;
        addCell(form,c,0,y,new JLabel("Descuento %:"),1,false); addCell(form,c,1,y,txtDescPct,1,true);

        add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btOk = new JButton("Guardar");
        JButton btCancel = new JButton("Cancelar");
        south.add(btCancel); south.add(btOk);
        add(south, BorderLayout.SOUTH);

        btCancel.addActionListener(_e -> { result = null; dispose(); });
        btOk.addActionListener(_e -> onOk());

        pack();
        setLocationRelativeTo(owner);
    }

    private void onOk() {
        String articulo = txtArticulo.getText().trim();
        if (articulo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El campo Artículo es obligatorio.");
            txtArticulo.requestFocus();
            return;
        }
        PedidosDAO.PedidoDraft p = new PedidosDAO.PedidoDraft();
        p.articulo  = articulo;
        p.marca     = txtMarca.getText().trim();
        p.modelo    = txtModelo.getText().trim();
        p.talla     = txtTalla.getText().trim();
        p.color     = txtColor.getText().trim();
        p.precio    = parseMoney(txtPrecio.getText());
        p.descuento = parseMoney(txtDescPct.getText());
        result = p;
        dispose();
    }

    public PedidosDAO.PedidoDraft getResult(){ return result; }

    private static BigDecimal parseMoney(String s){
        try { s = s.replace(",","").trim(); return s.isEmpty()? BigDecimal.ZERO : new BigDecimal(s); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static void addCell(JPanel p, GridBagConstraints c, int x,int y,JComponent comp,int span, boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }
}
