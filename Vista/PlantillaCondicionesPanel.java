package Vista;

import Utilidades.PlantillaCondicionesConfig;
import Utilidades.SeguridadUI;

import javax.swing.*;
import java.awt.*;

public class PlantillaCondicionesPanel extends JPanel {

    private final JTextArea txtIntro = new JTextArea(4, 80);
    private final JTextField txtNombreNovia = new JTextField();
    private final JTextField txtAcuerdo = new JTextField();
    private final JTextField txtFirma = new JTextField();

    private final JButton btnEditar = new JButton("Editar");
    private final JButton btnGuardar = new JButton("Guardar cambios");
    private final JButton btnCancelar = new JButton("Cancelar");

    public PlantillaCondicionesPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titulo = new JLabel("Personalización de hoja de condiciones", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        add(titulo, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;

        form.add(new JLabel("Mensaje superior:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        txtIntro.setLineWrap(true);
        txtIntro.setWrapStyleWord(true);
        form.add(new JScrollPane(txtIntro), gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0;
        form.add(new JLabel("Etiqueta campo novia:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        form.add(txtNombreNovia, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0;
        form.add(new JLabel("Texto de acuerdo:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        form.add(txtAcuerdo, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0;
        form.add(new JLabel("Texto de firma:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        form.add(txtFirma, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        JTextArea ayuda = new JTextArea(
                "Notas:\n" +
                "- Los valores (nombre, fechas, modelo, etc.) se seguirán llenando automáticamente.\n" +
                "- Las líneas de subrayado permanecen iguales.\n" +
                "- Esto NO requiere cambios de estructura en la base de datos.");
        ayuda.setEditable(false);
        ayuda.setBackground(new Color(245, 245, 245));
        ayuda.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(ayuda, gc);

        add(form, BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnEditar);
        botones.add(btnGuardar);
        botones.add(btnCancelar);
        add(botones, BorderLayout.SOUTH);

        btnEditar.addActionListener(_e -> entrarEdicion());
        btnCancelar.addActionListener(_e -> cargarDatos());
        btnGuardar.addActionListener(_e -> guardarDatos());

        cargarDatos();
    }

    private void entrarEdicion() {
        if (SeguridadUI.pedirYValidarClave(this)) {
            setEditMode(true);
            txtIntro.requestFocus();
        }
    }

    private void cargarDatos() {
        PlantillaCondicionesConfig.Datos d = PlantillaCondicionesConfig.cargar();
        txtIntro.setText(d.intro);
        txtNombreNovia.setText(d.lblNombreNovia);
        txtAcuerdo.setText(d.lblAcuerdo);
        txtFirma.setText(d.lblFirma);
        setEditMode(false);
    }

    private void guardarDatos() {
        String intro = txtIntro.getText().trim();
        String lblNovia = txtNombreNovia.getText().trim();
        String lblAcuerdo = txtAcuerdo.getText().trim();
        String lblFirma = txtFirma.getText().trim();

        if (intro.isEmpty() || lblNovia.isEmpty() || lblAcuerdo.isEmpty() || lblFirma.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ningún campo puede estar vacío.");
            return;
        }

        PlantillaCondicionesConfig.Datos d = new PlantillaCondicionesConfig.Datos();
        d.intro = intro;
        d.lblNombreNovia = lblNovia;
        d.lblAcuerdo = lblAcuerdo;
        d.lblFirma = lblFirma;

        try {
            PlantillaCondicionesConfig.guardar(d);
            JOptionPane.showMessageDialog(this, "Plantilla guardada correctamente.");
            setEditMode(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setEditMode(boolean enable) {
        txtIntro.setEditable(enable);
        txtNombreNovia.setEditable(enable);
        txtAcuerdo.setEditable(enable);
        txtFirma.setEditable(enable);

        Color bg = enable ? Color.WHITE : new Color(235, 235, 235);
        txtIntro.setBackground(bg);
        txtNombreNovia.setBackground(bg);
        txtAcuerdo.setBackground(bg);
        txtFirma.setBackground(bg);

        btnEditar.setEnabled(!enable);
        btnGuardar.setEnabled(enable);
        btnCancelar.setEnabled(enable);
    }
}
