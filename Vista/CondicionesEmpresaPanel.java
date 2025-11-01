package Vista;

import Conexion.Conecta;
import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class CondicionesEmpresaPanel extends JPanel {

    private static final String ACCESS_KEY = "050607"; // misma clave de seguridad
    private JTextArea txtCondiciones;
    private JButton btnGuardar, btnEditar, btnCancelar;
    private boolean editMode = false;

    public CondicionesEmpresaPanel() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JLabel titulo = new JLabel("Formato predeterminado de condiciones de venta", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        add(titulo, BorderLayout.NORTH);

        txtCondiciones = new JTextArea(20, 80);
        txtCondiciones.setWrapStyleWord(true);
        txtCondiciones.setLineWrap(true);
        txtCondiciones.setEditable(false);
        JScrollPane scroll = new JScrollPane(txtCondiciones);
        add(scroll, BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnEditar   = new JButton("Editar");
        btnGuardar  = new JButton("Guardar cambios");
        btnCancelar = new JButton("Cancelar");
        botones.add(btnEditar);
        botones.add(btnGuardar);
        botones.add(btnCancelar);
        add(botones, BorderLayout.SOUTH);

        btnGuardar.setEnabled(false);
        btnCancelar.setEnabled(false);

        btnEditar.addActionListener(_e -> intentarEntrarEnEdicion());
        btnCancelar.addActionListener(_e -> cancelarEdicion());
        btnGuardar.addActionListener(_e -> guardarCondiciones());

        cargarCondiciones();
    }

    private void cargarCondiciones() {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement("SELECT texto FROM empresa_condiciones WHERE id=1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                txtCondiciones.setText(rs.getString("texto"));
            } else {
                txtCondiciones.setText("");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar condiciones: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        setEditMode(false);
    }

    private void intentarEntrarEnEdicion() {
        JPasswordField pf = new JPasswordField();
        Object[] msg = {"Ingresa la clave de acceso:", pf};
        int r = JOptionPane.showConfirmDialog(this, msg, "Autorización",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r == JOptionPane.OK_OPTION) {
            String typed = new String(pf.getPassword());
            if (ACCESS_KEY.equals(typed)) {
                setEditMode(true);
                txtCondiciones.requestFocus();
            } else {
                JOptionPane.showMessageDialog(this, "Clave incorrecta", "Acceso denegado", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void guardarCondiciones() {
        String texto = txtCondiciones.getText().trim();
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El texto no puede estar vacío.");
            return;
        }

        try (Connection cn = Conecta.getConnection()) {
            PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO empresa_condiciones (id, texto) VALUES (1, ?) " +
                    "ON DUPLICATE KEY UPDATE texto = VALUES(texto)"
            );
            ps.setString(1, texto);
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Formato de condiciones guardado correctamente.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        setEditMode(false);
    }

    private void cancelarEdicion() {
        cargarCondiciones();
        setEditMode(false);
    }

    private void setEditMode(boolean enable) {
        this.editMode = enable;
        txtCondiciones.setEditable(enable);
        btnGuardar.setEnabled(enable);
        btnCancelar.setEnabled(enable);
        btnEditar.setEnabled(!enable);
        txtCondiciones.setBackground(enable ? Color.WHITE : new Color(235,235,235));
    }
}
