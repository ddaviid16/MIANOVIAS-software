package Vista;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class DlgFactura extends JDialog {

    public static class CapturaFactura {
        public String rfc;
        public String tipoPersona; // FISICA|MORAL
        public String regimen;     // p.ej. 601, 605, ...
        public String usoCfdi;     // p.ej. G01, G03, P01 (según catálogo vigente)
        public String correo;
    }

    private final JTextField txtRFC     = new JTextField();
    private final JComboBox<String> cbTipo = new JComboBox<>(new String[]{"FISICA","MORAL"});
    private final JTextField txtRegimen = new JTextField(4);
    private final JComboBox<String> cbUso = new JComboBox<>();
    private final JTextField txtCorreo  = new JTextField();

    private CapturaFactura result;

    public DlgFactura(Window owner, CapturaFactura precargada) {
        super(owner, "Datos para facturación", ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;
        add(formRow(form, c, 0, y++, new JLabel("Tipo de persona:"), cbTipo));
        add(formRow(form, c, 0, y++, new JLabel("RFC:"), txtRFC));
        add(formRow(form, c, 0, y++, new JLabel("Régimen fiscal:"), txtRegimen));
        add(formRow(form, c, 0, y++, new JLabel("Uso de CFDI:"), cbUso));
        add(formRow(form, c, 0, y++, new JLabel("Correo receptor:"), txtCorreo));

        add(form, BorderLayout.CENTER);

        JButton btOk = new JButton("Guardar");
        JButton btCancel = new JButton("Cancelar");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,8));
        south.add(btCancel); south.add(btOk);
        add(south, BorderLayout.SOUTH);

        // catálogo mínimo por ahora (puedes reemplazarlo por tu catálogo oficial)
        cargarUsosPorRegimen();

        // precarga opcional
        if (precargada != null) {
            cbTipo.setSelectedItem(precargada.tipoPersona==null? "FISICA":precargada.tipoPersona);
            txtRFC.setText(precargada.rfc==null? "" : precargada.rfc);
            txtRegimen.setText(precargada.regimen==null? "" : precargada.regimen);
            txtCorreo.setText(precargada.correo==null? "" : precargada.correo);
            if (precargada.usoCfdi != null) cbUso.setSelectedItem(precargada.usoCfdi);
        }

        btCancel.addActionListener(_e -> { result = null; dispose(); });
        btOk.addActionListener(_e -> onAceptar());

        pack();
        setMinimumSize(new Dimension(540, getHeight()));
        setLocationRelativeTo(owner);
    }

    private void onAceptar() {
        String tipo = String.valueOf(cbTipo.getSelectedItem());
        String rfc  = txtRFC.getText().trim().toUpperCase();
        String reg  = txtRegimen.getText().trim().toUpperCase();
        String uso  = (String) cbUso.getSelectedItem();
        String mail = txtCorreo.getText().trim();

        // Validación RFC (básica)
        Pattern RFC_FIS = Pattern.compile("^[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3}$");
        Pattern RFC_MOR = Pattern.compile("^[A-ZÑ&]{3}\\d{6}[A-Z0-9]{3}$");
        boolean ok = ("FISICA".equals(tipo) ? RFC_FIS.matcher(rfc).matches()
                                             : RFC_MOR.matcher(rfc).matches());
        if (!ok) { JOptionPane.showMessageDialog(this, "RFC inválido para el tipo seleccionado."); return; }
        if (reg.isEmpty()) { JOptionPane.showMessageDialog(this, "Captura el régimen fiscal."); return; }
        if (uso==null || uso.isBlank()) { JOptionPane.showMessageDialog(this, "Selecciona el uso de CFDI."); return; }
        if (!mail.isBlank() && !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(this, "Correo inválido."); return;
        }

        result = new CapturaFactura();
        result.tipoPersona = tipo;
        result.rfc = rfc;
        result.regimen = reg;
        result.usoCfdi = uso;
        result.correo = mail;
        dispose();
    }

    public CapturaFactura getResult() { return result; }

    private static Component add(JPanel p, GridBagConstraints c, int x, int y, JComponent comp) {
        c.gridx = x; c.gridy = y;
        p.add(comp, c); return comp;
    }
    private static JPanel formRow(JPanel p, GridBagConstraints base, int x, int y, JComponent l, JComponent f) {
        GridBagConstraints c = (GridBagConstraints) base.clone();
        c.weightx = 0; c.gridx=x; c.gridy=y; c.fill=GridBagConstraints.NONE;
        p.add(l, c);
        c = (GridBagConstraints) base.clone();
        c.weightx = 1; c.gridx=x+1; c.gridy=y; c.fill=GridBagConstraints.HORIZONTAL;
        p.add(f, c);
        return p;
    }

    private void cargarUsosPorRegimen() {
        // Ejemplo simple: puedes mapear por régimen; por ahora dejamos un set general práctico
        Map<String,String> usos = new LinkedHashMap<>();
        usos.put("G01","Adquisición de mercancías");
        usos.put("G03","Gastos en general");
        usos.put("I01","Construcciones");
        usos.put("P01","Por definir"); // quítalo si en tu PAC ya no aplica
        cbUso.removeAllItems();
        usos.keySet().forEach(cbUso::addItem);
    }
}
