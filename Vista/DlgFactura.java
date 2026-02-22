package Vista;

import javax.swing.*;

import Utilidades.CatalogoCFDI;

import java.awt.*;
import java.util.regex.Pattern;

public class DlgFactura extends JDialog {

    public static class CapturaFactura {
        public String rfc;
        public String tipoPersona; // FISICA|MORAL
        public String regimen;     // p.ej. 601, 605, ...
        public String usoCfdi;     // p.ej. G01, G03, P01 (según catálogo vigente)
        public String codigoPostal;
        public String correo;
    }

    private final JTextField txtRFC     = new JTextField();
    private final JComboBox<String> cbTipo = new JComboBox<>(new String[]{"FISICA","MORAL"});
    private final JComboBox<Utilidades.CatalogoCFDI.Regimen> cbRegimen = new JComboBox<>();
    private final JComboBox<Utilidades.CatalogoCFDI.UsoCfdi> cbUso = new JComboBox<>();
    private final JTextField txtCorreo  = new JTextField();
    private final JTextField txtCodigoPostal = new JTextField();


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
        add(formRow(form, c, 0, y++, new JLabel("Régimen fiscal:"), cbRegimen));
        add(formRow(form, c, 0, y++, new JLabel("Uso de CFDI:"), cbUso));
        add(formRow(form, c, 0, y++, new JLabel("Código postal:"), txtCodigoPostal));
        add(formRow(form, c, 0, y++, new JLabel("Correo receptor:"), txtCorreo));

        add(form, BorderLayout.CENTER);

        JButton btOk = new JButton("Guardar");
        JButton btCancel = new JButton("Cancelar");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,8));
        south.add(btCancel); south.add(btOk);
        add(south, BorderLayout.SOUTH);

        // catálogo mínimo por ahora (puedes reemplazarlo por tu catálogo oficial)
        inicializarCatalogos();

        // precarga opcional
        if (precargada != null) {
            cbTipo.setSelectedItem(precargada.tipoPersona==null? "FISICA":precargada.tipoPersona);
            txtRFC.setText(precargada.rfc==null? "" : precargada.rfc);
            cbRegimen.setSelectedItem(precargada.regimen==null? null : new Utilidades.CatalogoCFDI.Regimen(precargada.regimen, "", ""));
            txtCodigoPostal.setText(precargada.codigoPostal==null? "" : precargada.codigoPostal);
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

        CatalogoCFDI.Regimen regItem = (CatalogoCFDI.Regimen) cbRegimen.getSelectedItem();
        CatalogoCFDI.UsoCfdi usoItem = (CatalogoCFDI.UsoCfdi) cbUso.getSelectedItem();

        String reg = (regItem == null ? "" : regItem.clave);
        String uso = (usoItem == null ? "" : usoItem.clave);

        String cp = txtCodigoPostal.getText().trim();
        String mail = txtCorreo.getText().trim();

        // Validación RFC (igual que antes)
        Pattern RFC_FIS = Pattern.compile("^[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3}$");
        Pattern RFC_MOR = Pattern.compile("^[A-ZÑ&]{3}\\d{6}[A-Z0-9]{3}$");
        boolean ok = ("FISICA".equals(tipo) ? RFC_FIS.matcher(rfc).matches()
                                            : RFC_MOR.matcher(rfc).matches());
        if (!ok) { JOptionPane.showMessageDialog(this, "RFC inválido para el tipo seleccionado."); return; }
        if (reg.isEmpty()) { JOptionPane.showMessageDialog(this, "Captura el régimen fiscal."); return; }
        if (uso == null || uso.isBlank()) { JOptionPane.showMessageDialog(this, "Selecciona el uso de CFDI."); return; }
        if (!cp.matches("^\\d{5}$")) { JOptionPane.showMessageDialog(this, "El código postal debe tener 5 dígitos."); return; }
        if (!mail.isBlank() && !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(this, "Correo inválido."); return;
        }

        result = new CapturaFactura();
        result.tipoPersona = tipo;
        result.rfc = rfc;
        result.regimen = reg;     // solo la CLAVE (4 chars)
        result.usoCfdi = uso;     // solo la CLAVE (3 chars)
        result.codigoPostal = cp;
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

// === Carga de catálogos y filtros ===

private void inicializarCatalogos() {
    // Cuando cambie FISICA/MORAL, recargamos regímenes
    cbTipo.addActionListener(_e -> recargarRegimenesSegunTipoPersona());
    // Cuando cambie el régimen, recargamos usos compatibles
    cbRegimen.addActionListener(_e -> recargarUsosSegunRegimen());
    // Primera carga
    recargarRegimenesSegunTipoPersona();
}

private void recargarRegimenesSegunTipoPersona() {
    String tipo = String.valueOf(cbTipo.getSelectedItem()); // "FISICA" / "MORAL"
    String persona = "PF";
    if ("MORAL".equals(tipo)) persona = "PM";

    cbRegimen.removeAllItems();
    for (CatalogoCFDI.Regimen r : CatalogoCFDI.listarRegimenesPorPersona(persona)) {
        cbRegimen.addItem(r);
    }
    if (cbRegimen.getItemCount() > 0) {
        cbRegimen.setSelectedIndex(0);
    }
    recargarUsosSegunRegimen();
}

private void recargarUsosSegunRegimen() {
    cbUso.removeAllItems();
    CatalogoCFDI.Regimen r = (CatalogoCFDI.Regimen) cbRegimen.getSelectedItem();
    if (r == null) return;

    for (CatalogoCFDI.UsoCfdi u : CatalogoCFDI.listarUsosParaRegimen(r.clave)) {
        cbUso.addItem(u);
    }
}

// Para precargar valores desde BD
private void seleccionarRegimenPorClave(String clave) {
    if (clave == null) return;
    for (int i = 0; i < cbRegimen.getItemCount(); i++) {
        CatalogoCFDI.Regimen r = cbRegimen.getItemAt(i);
        if (r.clave.equalsIgnoreCase(clave)) {
            cbRegimen.setSelectedIndex(i);
            recargarUsosSegunRegimen();
            return;
        }
    }
}

private void seleccionarUsoPorClave(String clave) {
    if (clave == null) return;
    for (int i = 0; i < cbUso.getItemCount(); i++) {
        CatalogoCFDI.UsoCfdi u = cbUso.getItemAt(i);
        if (u.clave.equalsIgnoreCase(clave)) {
            cbUso.setSelectedIndex(i);
            return;
        }
    }
}

}
