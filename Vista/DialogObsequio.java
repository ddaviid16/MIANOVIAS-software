package Vista;

import Controlador.ObsequioInvDAO;
import Modelo.ObsequioInv;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/** Diálogo para crear/modificar registros del InventarioObsequios. */
public class DialogObsequio extends JDialog {

    // --- Campos UI (solo lo necesario)
    private JTextField txtCodigo;
    private JTextField txtArticulo;
    private JTextField txtFechaRegistro;

    // --- Estado
    private boolean guardado = false;
    private final ObsequioInvDAO dao = new ObsequioInvDAO();
    private final boolean edicion;
    private final ObsequioInv original;   // para conservar valores antiguos en edición

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    // Alta
    public DialogObsequio(Frame owner) {
        this(owner, null);
    }

    // Edición
    public DialogObsequio(Frame owner, ObsequioInv ob) {
        super(owner, ob == null ? "Registrar obsequio" : "Modificar obsequio", true);
        this.edicion = (ob != null);
        this.original = ob;

        setSize(420, 240);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // ===== UI =====
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;

        txtCodigo = new JTextField();
        txtArticulo = new JTextField();
        txtFechaRegistro = new JTextField();
        txtFechaRegistro.setEditable(false);

        addRow(p, c, y++, new JLabel("Código*:"), txtCodigo);
        addRow(p, c, y++, new JLabel("Artículo*:"), txtArticulo);
        addRow(p, c, y++, new JLabel("Fecha registro:"), txtFechaRegistro);

        // Si estamos en edición, precargar datos y bloquear código
        if (edicion && original != null) {
            cargar(original);
            txtCodigo.setEditable(false);
        } else {
            // En alta, solo informativo
            txtFechaRegistro.setText("Se asignará automáticamente");
        }

        // Botones
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = new JButton(edicion ? "Actualizar" : "Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        actions.add(btnGuardar);
        actions.add(btnCancelar);

        btnGuardar.addActionListener(_e -> guardar());
        btnCancelar.addActionListener(_e -> dispose());

        add(p, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    // ====== Helpers de Layout ======
    private void addRow(JPanel p, GridBagConstraints c, int y, JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l, c);
        c.gridx = 1; c.gridy = y; c.gridwidth = 3; p.add(f, c);
        c.gridwidth = 1;
    }

    // ====== Cargar / Guardar ======
    private void cargar(ObsequioInv ob) {
        txtCodigo.setText(ob.getCodigoArticulo());
        txtArticulo.setText(nz(ob.getArticulo()));
        txtFechaRegistro.setText(
                ob.getFechaRegistro() == null ? "" : ob.getFechaRegistro().format(DF)
        );
    }

    private void guardar() {
        if (txtCodigo.getText().isBlank()) {
            warn("Código es obligatorio", txtCodigo);
            return;
        }
        if (txtArticulo.getText().isBlank()) {
            warn("Artículo es obligatorio", txtArticulo);
            return;
        }

        Object[] ops = {"SI", "NO"};
        int r = JOptionPane.showOptionDialog(this,
                edicion ? "¿Guardar cambios?" : "¿La información es correcta?",
                "Confirmación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            ObsequioInv ob;

            if (edicion && original != null) {
                // Reutilizamos el objeto original para no perder marca/modelo/etc. que ya existan
                ob = original;
            } else {
                ob = new ObsequioInv();
            }

            ob.setCodigoArticulo(txtCodigo.getText().trim());
            ob.setArticulo(txtArticulo.getText().trim());
            // NO tocamos marca, modelo, talla, color, precio, descuento, existencia, status:
            //   - En alta se quedan como null (el DAO pone status='A' por defecto)
            //   - En edición conservan el valor que tenían en BD

            boolean ok = edicion ? dao.actualizar(ob) : dao.insertar(ob);

            if (ok) {
                guardado = true;
                JOptionPane.showMessageDialog(this,
                        edicion ? "Obsequio actualizado" : "Obsequio registrado");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "No se guardó el registro",
                        "Atención",
                        JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            if (!edicion && msg != null && msg.toLowerCase().contains("duplicate")) {
                JOptionPane.showMessageDialog(this,
                        "El código ya existe.",
                        "Duplicado",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Error SQL: " + msg,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isGuardado() { return guardado; }

    // ===== Helpers =====
    private void warn(String msg, JComponent focus) {
        JOptionPane.showMessageDialog(this, msg, "Validación", JOptionPane.WARNING_MESSAGE);
        focus.requestFocus();
    }
    private String nz(String s) { return s == null ? "" : s; }
}
