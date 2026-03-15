package Vista;

import Utilidades.PlantillaCondicionesConfig;
import Utilidades.SeguridadUI;

import javax.swing.*;
import java.awt.*;

public class PlantillaCondicionesPanel extends JPanel {

    private final JTextArea txtIntro = new JTextArea(4, 80);
    private final JTextField txtNombreNovia = new JTextField();

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
        form.add(new JLabel("Linea \"Nombre de la novia\":"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        form.add(txtNombreNovia, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        JTextArea ayuda = new JTextArea(
                "NOTA: Al modificar estos campos, las siguientes impresiones y la re-impresión " +
                "de notas se imprimirán con la modificación aplicada.\n" +
                "Ejemplo: si cambias el mensaje superior hoy, las próximas notas y reimpresiones " +
                "saldrán con ese nuevo texto.");
        ayuda.setEditable(false);
        ayuda.setLineWrap(true);
        ayuda.setWrapStyleWord(true);
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
        setEditMode(false);
    }

    private void guardarDatos() {
        String intro = txtIntro.getText().trim();
        String lblNovia = txtNombreNovia.getText().trim();

        if (intro.isEmpty() || lblNovia.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ningún campo puede estar vacío.");
            return;
        }

        PlantillaCondicionesConfig.Datos d = new PlantillaCondicionesConfig.Datos();
        d.intro = intro;
        d.lblNombreNovia = lblNovia;

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

        Color bg = enable ? Color.WHITE : new Color(235, 235, 235);
        txtIntro.setBackground(bg);
        txtNombreNovia.setBackground(bg);

        btnEditar.setEnabled(!enable);
        btnGuardar.setEnabled(enable);
        btnCancelar.setEnabled(enable);
    }
}
