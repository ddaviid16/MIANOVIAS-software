package Vista;

import Controlador.CambioFechaEventoNotaService;
import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.Nota;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class CambioFechaEventoPorNotaPanel extends JPanel {

    private final JTextField txtTelefono = new JTextField();
    private final JTextField txtNombre   = ro();
    private final JComboBox<Nota> cbNotas = new JComboBox<>();

    private final JTextArea  txtPreview  = roArea();
    private final JTextField txtNueva    = new JTextField(10);
    private final JCheckBox  chkLimpiar  = new JCheckBox("Limpiar fecha (dejar NULL)");
    private final JTextField txtNuevaEntrega = new JTextField(10);


    private String telCargado = null;

    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public CambioFechaEventoPorNotaPanel() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;
        addCell(top,c,0,y,new JLabel("Teléfono cliente:"),1,false);
        addCell(top,c,1,y,txtTelefono,1,true); y++;

        addCell(top,c,0,y,new JLabel("Nombre:"),1,false);
        addCell(top,c,1,y,txtNombre,1,true); y++;

        addCell(top,c,0,y,new JLabel("Nota:"),1,false);
        cbNotas.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean isSelected,boolean cellHasFocus){
                super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                if (value instanceof Nota n) {
                    setText(String.format("%s #%d %s  Total: %.2f  Saldo: %.2f",
                            n.getTipo(), n.getNumeroNota(),
                            n.getFolio()==null?"":("("+n.getFolio()+")"),
                            n.getTotal()==null?0:n.getTotal(),
                            n.getSaldo()==null?0:n.getSaldo()));
                } else if (value == null) setText("— Selecciona —");
                return this;
            }
        });
        cbNotas.addActionListener(_e -> cargarPreviewNota());
        addCell(top,c,1,y,cbNotas,1,true); y++;

        add(top, BorderLayout.NORTH);

        JPanel mid = new JPanel(new GridBagLayout());
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(6,6,6,6);
        d.fill = GridBagConstraints.HORIZONTAL;
        d.weightx = 1;
        int r=0;

        addCell(mid,d,0,r,new JLabel("Resumen de renglones:"),1,false); r++;
        addCell(mid,d,0,r,new JScrollPane(txtPreview),1,true); r++;

        JPanel fechaBox = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        fechaBox.add(new JLabel("Nueva fecha evento (dd-MM-aaaa):"));
        fechaBox.add(txtNueva);
        fechaBox.add(new JLabel("Nueva entrega (dd-MM-aaaa):"));
        fechaBox.add(txtNuevaEntrega);
        fechaBox.add(chkLimpiar);

        addCell(mid,d,0,r,fechaBox,1,true); r++;

        JButton btAplicar = new JButton("Aplicar a la NOTA seleccionada");
        btAplicar.addActionListener(_e -> aplicar());
        addCell(mid,d,0,r,btAplicar,1,false);

        add(mid, BorderLayout.CENTER);

        // hooks
        txtTelefono.addActionListener(_e -> cargarClienteYNotas());
        ((AbstractDocument) txtTelefono.getDocument()).addDocumentListener((VentaContadoPanel.SimpleDocListener) this::cargarClienteYNotas);
        chkLimpiar.addActionListener(_e -> txtNueva.setEnabled(!chkLimpiar.isSelected()));
    }

    private void cargarClienteYNotas() {
        String tel = txtTelefono.getText().trim();
        if (tel.isEmpty() || tel.equals(telCargado)) return;

        try {
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                txtNombre.setText("— no registrado —");
                cbNotas.removeAllItems();
                txtPreview.setText("");
                telCargado = tel;
                return;
            }
            txtNombre.setText(cr.getNombreCompleto()==null?"":cr.getNombreCompleto());

            NotasDAO ndao = new NotasDAO();
            List<Nota> notas = ndao.listarNotasClienteParaDevolucion(tel);
            cbNotas.removeAllItems();
            if (notas.isEmpty()) cbNotas.addItem(null);
            else for (Nota n : notas) cbNotas.addItem(n);

            telCargado = tel;
            cargarPreviewNota();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Error consultando: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarPreviewNota() {
        txtPreview.setText("");
        Nota n = (Nota) cbNotas.getSelectedItem();
        if (n == null) return;

        try {
            CambioFechaEventoNotaService svc = new CambioFechaEventoNotaService();
            var pv = svc.previsualizar(n.getNumeroNota());

            StringBuilder sb = new StringBuilder();
            sb.append("Nota ").append(pv.tipo).append(" #").append(n.getNumeroNota())
              .append(pv.folio==null?"":"  Folio: "+pv.folio).append("\n\n")
              .append("Renglones totales: ").append(pv.totalRenglones).append("\n")
              .append("Con fecha: ").append(pv.conFecha).append("\n")
              .append("Sin fecha: ").append(pv.sinFecha).append("\n");
            if (!pv.fechasDistintas.isEmpty()) {
                sb.append("\nFechas distintas encontradas:\n");
                for (var f : pv.fechasDistintas) sb.append(" • ").append(f.format(MX)).append("\n");
            }
            txtPreview.setText(sb.toString());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Error cargando resumen: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aplicar() {
        Nota n = (Nota) cbNotas.getSelectedItem();
        if (n == null) { JOptionPane.showMessageDialog(this,"Selecciona una nota."); return; }

        LocalDate nueva = null;
        if (!chkLimpiar.isSelected()) {
            String s = txtNueva.getText().trim();
            if (s.isEmpty()) { JOptionPane.showMessageDialog(this,"Escribe la nueva fecha o marca \"Limpiar fecha\"."); return; }
            try { nueva = LocalDate.parse(s, MX); }
            catch (DateTimeParseException ex) { JOptionPane.showMessageDialog(this,"Formato inválido. Usa dd-MM-aaaa."); return; }
        }

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Aplicar nueva fecha de evento a TODOS los artículos de la nota?\n" +
                "Nota #" + n.getNumeroNota() + "  (" + (n.getFolio()==null?"s/folio":n.getFolio()) + ")\n" +
                "Nueva fecha: " + (nueva==null?"(NULL)":nueva.format(MX)),
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            CambioFechaEventoNotaService svc = new CambioFechaEventoNotaService();
            LocalDate nuevaEntrega = null;
            String sEntrega = txtNuevaEntrega.getText().trim();
            if (!sEntrega.isEmpty()) {
                try {
                    nuevaEntrega = LocalDate.parse(sEntrega, MX);
                } catch (DateTimeParseException ex) {
                    JOptionPane.showMessageDialog(this, "Formato inválido en nueva fecha de entrega.");
                    return;
                }
            }

            var res = svc.aplicar(n.getNumeroNota(), nueva, nuevaEntrega);


            JOptionPane.showMessageDialog(this,
                    "Aplicado.\nRenglones actualizados: " + res.renglonesActualizados);

            txtNueva.setText("");
            chkLimpiar.setSelected(false);
            txtNueva.setEnabled(true);
            cargarPreviewNota();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Error aplicando: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // helpers UI
    private static JTextField ro(){ JTextField t=new JTextField(); t.setEditable(false);
        Color ro = UIManager.getColor("TextField.inactiveBackground");
        t.setBackground(ro==null?new Color(235,235,235):ro); return t; }
    private static JTextArea roArea(){ JTextArea a=new JTextArea(6,40); a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true); return a; }
    private static void addCell(JPanel p, GridBagConstraints c,int x,int y,JComponent comp,int span,boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx=growX?1:0; p.add(comp,c); c.gridwidth=1; }
}
