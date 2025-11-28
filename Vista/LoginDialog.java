package Vista;

import Controlador.AsesorDAO;
import Modelo.Asesor;
import Modelo.SesionUsuario;
import Utilidades.SeguridadUI;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class LoginDialog extends JDialog {

    private JComboBox<Asesor> cbEmpleado;
    private JPasswordField pfPassword;
    private JButton btEntrar, btCancelar, btOlvido;
    private Asesor usuarioLogueado = null;

    public LoginDialog(Frame owner) {
        super(owner, "Inicio de sesión", true);
        setLayout(new BorderLayout(10,10));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        cbEmpleado = new JComboBox<>();
        cargarEmpleados();

        cbEmpleado.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Asesor) {
                    Asesor a = (Asesor) value;
                    setText(a.getNumeroEmpleado() + " - " + a.getNombreCompleto());
                }
                return this;
            }
        });

        pfPassword = new JPasswordField(15);

        int y=0;
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        center.add(new JLabel("Empleado:"), c);
        c.gridx = 1;
        center.add(cbEmpleado, c); y++;

        c.gridx = 0; c.gridy = y;
        center.add(new JLabel("Contraseña:"), c);
        c.gridx = 1;
        center.add(pfPassword, c);

        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btOlvido  = new JButton("Olvidé mi contraseña");
        btEntrar  = new JButton("Entrar");
        btCancelar= new JButton("Cancelar");

        south.add(btOlvido);
        south.add(btEntrar);
        south.add(btCancelar);

        add(south, BorderLayout.SOUTH);

        btEntrar.addActionListener(_e -> onEntrar());
        btCancelar.addActionListener(_e -> {
            usuarioLogueado = null;
            dispose();
        });
        btOlvido.addActionListener(_e -> onOlvidoPassword());

        pack();
        setLocationRelativeTo(owner);
    }

    private void cargarEmpleados() {
        try {
            AsesorDAO dao = new AsesorDAO();
            List<Asesor> lista = dao.listarTodos(); // o listarActivosDetalle si prefieres
            cbEmpleado.removeAllItems();
            for (Asesor a : lista) {
                if ("A".equalsIgnoreCase(a.getStatus())) { // solo activos
                    cbEmpleado.addItem(a);
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar empleados: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEntrar() {
        Asesor sel = (Asesor) cbEmpleado.getSelectedItem();
        if (sel == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un empleado.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        char[] pass = pfPassword.getPassword();

        try {
            AsesorDAO dao = new AsesorDAO();
            boolean tiene = dao.empleadoTienePassword(sel.getNumeroEmpleado());

            if (!tiene) {
                // Primera vez: crear password
                if (!crearPasswordInicial(sel)) return;
                // Ya creada, se considera login correcto
                usuarioLogueado = sel;
                SesionUsuario.iniciar(sel);
                dispose();
                return;
            }

            // Ya tenía contraseña → validar
            if (!dao.validarPasswordEmpleado(sel.getNumeroEmpleado(), pass)) {
                JOptionPane.showMessageDialog(this,
                        "Contraseña incorrecta.",
                        "Acceso denegado", JOptionPane.ERROR_MESSAGE);
                pfPassword.setText("");
                return;
            }

            usuarioLogueado = sel;
            SesionUsuario.iniciar(sel);
            dispose();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Error al validar acceso: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Primera vez: definición de contraseña */
    private boolean crearPasswordInicial(Asesor sel) {
        JPasswordField pf1 = new JPasswordField();
        JPasswordField pf2 = new JPasswordField();
        Object[] msg = {
                "El empleado aún no tiene contraseña.\n" +
                "Define una nueva contraseña para " + sel.getNombreCompleto() + ":", pf1,
                "Confírmala:", pf2
        };
        int r = JOptionPane.showConfirmDialog(this, msg,
                "Crear contraseña", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;

        char[] p1 = pf1.getPassword();
        char[] p2 = pf2.getPassword();
        if (p1.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "La contraseña no puede estar vacía.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!java.util.Arrays.equals(p1, p2)) {
            JOptionPane.showMessageDialog(this,
                    "Las contraseñas no coinciden.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            AsesorDAO dao = new AsesorDAO();
            dao.establecerPasswordEmpleado(sel.getNumeroEmpleado(), p1);
            JOptionPane.showMessageDialog(this,
                    "Contraseña creada correctamente.",
                    "Listo", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Error al guardar la contraseña: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** Olvidó contraseña: solo se puede resetear si se valida la clave maestra. */
    private void onOlvidoPassword() {
        Asesor sel = (Asesor) cbEmpleado.getSelectedItem();
        if (sel == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un empleado.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Pedir clave maestra de administradora
        if (!SeguridadUI.pedirYValidarClave(this)) {
            return; // acceso denegado
        }

        // Nueva contraseña
        JPasswordField pf1 = new JPasswordField();
        JPasswordField pf2 = new JPasswordField();
        Object[] msg = {
                "Nueva contraseña para " + sel.getNombreCompleto() + ":", pf1,
                "Confirmar nueva contraseña:", pf2
        };
        int r = JOptionPane.showConfirmDialog(this, msg,
                "Reiniciar contraseña", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        char[] p1 = pf1.getPassword();
        char[] p2 = pf2.getPassword();
        if (p1.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "La contraseña no puede estar vacía.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!java.util.Arrays.equals(p1, p2)) {
            JOptionPane.showMessageDialog(this,
                    "Las contraseñas no coinciden.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            AsesorDAO dao = new AsesorDAO();
            dao.establecerPasswordEmpleado(sel.getNumeroEmpleado(), p1);
            JOptionPane.showMessageDialog(this,
                    "Contraseña actualizada.",
                    "Listo", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Error al actualizar la contraseña: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Muestra el diálogo y devuelve el usuario logueado o null si canceló. */
    public static Asesor mostrarLogin(Frame owner) {
        LoginDialog dlg = new LoginDialog(owner);
        dlg.setVisible(true);
        return dlg.usuarioLogueado;
    }
}
