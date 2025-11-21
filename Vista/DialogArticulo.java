package Vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import Controlador.InventarioDAO;
import Modelo.Inventario;

public class DialogArticulo extends JDialog {

    private JTextField txtCodigo, txtArticulo, txtMarca, txtModelo, txtTalla, txtColor;
    private JTextField txtPrecio, txtDescuento, txtPrecioFinal, txtExistencia, txtFechaRegistro;
    private JComboBox<String> cbStatus;

    private boolean guardado = false;
    private final InventarioDAO dao = new InventarioDAO();

    private final boolean edicion;             // true si estamos modificando
    private final String codigoOriginal;      // PK (no se modifica)
    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    // ---- Constructor para ALTA ----
    public DialogArticulo(Frame owner) { this(owner, null); }

    // ---- Constructor para EDICIÓN (inv != null) ----
    public DialogArticulo(Frame owner, Inventario inv) {
        super(owner, inv == null ? "Registrar artículo" : "Modificar artículo", true);
        this.edicion = (inv != null);
        this.codigoOriginal = edicion ? inv.getCodigoArticulo() : null;

        setSize(620, 560);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;

        txtCodigo = new JTextField();
        txtArticulo = new JTextField();
        txtMarca = new JTextField();
        txtModelo = new JTextField();
        txtTalla = new JTextField();
        txtColor = new JTextField();
        txtPrecio = new JTextField();   applyDecimalOnly(txtPrecio, 10);
        txtDescuento = new JTextField();applyDecimalOnly(txtDescuento, 6);
        txtPrecioFinal = new JTextField(); txtPrecioFinal.setEditable(false); txtPrecioFinal.setForeground(new Color(0, 102, 0));
        txtExistencia = new JTextField(); applyDigitsOnly(txtExistencia, 10);
        cbStatus = new JComboBox<>(new String[]{"A","C"});
        txtFechaRegistro = new JTextField(); txtFechaRegistro.setEditable(false);

        addRow(p, c, y++, new JLabel("Código artículo*:"), txtCodigo);
        addRow(p, c, y++, new JLabel("Artículo*:"), txtArticulo);
        addRow(p, c, y++, new JLabel("Marca:"), txtMarca);
        addRow(p, c, y++, new JLabel("Modelo:"), txtModelo);
        addRow(p, c, y++, new JLabel("Talla:"), txtTalla);
        addRow(p, c, y++, new JLabel("Color:"), txtColor);

        // Precio y Descuento con Precio Final al lado del descuento
        y = addRowTriple(p, c, y,
        new JLabel("Precio*:"), txtPrecio,
        new JLabel("Descuento (%):"), txtDescuento,
        new JLabel("Precio final:"), txtPrecioFinal);

        y = addRowTriple(p, c, y,
        new JLabel("Existencia:"), txtExistencia,
        new JLabel("Status:"), cbStatus,
        new JLabel("Fecha registro:"), txtFechaRegistro);

        // Listeners para recalcular precio final
        DocumentListener recalc = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { calcFinal(); }
            @Override public void removeUpdate(DocumentEvent e) { calcFinal(); }
            @Override public void changedUpdate(DocumentEvent e) { calcFinal(); }
        };
        txtPrecio.getDocument().addDocumentListener(recalc);
        txtDescuento.getDocument().addDocumentListener(recalc);

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

        // Si estamos en edición, precargar datos y bloquear el código
        if (edicion) {
            cargar(inv);
            txtCodigo.setEditable(false);
        }

        // cálculo inicial
        calcFinal();
    }

    private void addRow(JPanel p, GridBagConstraints c, int y, JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l, c);
        c.gridx = 1; c.gridy = y; c.gridwidth = 3; p.add(f, c);
        c.gridwidth = 1;
    }

    // 3 pares (l1,f1) (l2,f2) (l3,f3) en una fila
    private int addRowTriple(JPanel p, GridBagConstraints c, int y,
                         JComponent l1, JComponent f1,
                         JComponent l2, JComponent f2,
                         JComponent l3, JComponent f3) {
        // Fila 1: (l1,f1) (l2,f2)
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l1, c);
        c.gridx = 1;                 p.add(f1, c);
        c.gridx = 2;                 p.add(l2, c);
        c.gridx = 3;                 p.add(f2, c);

        // Fila 2: (l3,f3)
        y++;
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l3, c);
        c.gridx = 1;                 p.add(f3, c);
        // Relleno para cuadrar la grilla
        c.gridx = 2; c.gridwidth = 2; p.add(Box.createHorizontalStrut(0), c);
        c.gridwidth = 1;

        // Devuelve la siguiente fila libre
        return y + 1;
    }

    private void cargar(Inventario inv) {
        txtCodigo.setText(inv.getCodigoArticulo().toString());
        txtArticulo.setText(inv.getArticulo());
        txtMarca.setText(n(inv.getMarca()));
        txtModelo.setText(n(inv.getModelo()));
        txtTalla.setText(n(inv.getTalla()));
        txtColor.setText(n(inv.getColor()));
        txtPrecio.setText(inv.getPrecio() == null ? "" : String.valueOf(inv.getPrecio()));
        txtDescuento.setText(inv.getDescuento() == null ? "" : String.valueOf(inv.getDescuento()));
        txtExistencia.setText(inv.getExistencia() == null ? "" : String.valueOf(inv.getExistencia()));
        cbStatus.setSelectedItem(inv.getStatus() == null ? "A" : inv.getStatus());
        txtFechaRegistro.setText(inv.getFechaRegistro() == null ? "" : inv.getFechaRegistro().format(DF));
    }

    private void guardar() {
        // Validaciones mínimas
        if (txtCodigo.getText().isBlank()) { warn("Código de artículo es obligatorio", txtCodigo); return; }
        if (txtArticulo.getText().isBlank()) { warn("Nombre del artículo es obligatorio", txtArticulo); return; }
        if (txtPrecio.getText().isBlank()) { warn("Precio es obligatorio", txtPrecio); return; }

        Object[] opts = {"SI","NO"};
        int choice = JOptionPane.showOptionDialog(this,
                edicion ? "¿Guardar cambios en el artículo?" : "¿La información es correcta?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            Inventario inv = new Inventario();
            inv.setCodigoArticulo(txtCodigo.getText().trim());
            inv.setArticulo(txtArticulo.getText().trim());
            inv.setMarca(blankToNull(txtMarca));
            inv.setModelo(blankToNull(txtModelo));
            inv.setTalla(blankToNull(txtTalla));
            inv.setColor(blankToNull(txtColor));
            inv.setPrecio(Double.parseDouble(txtPrecio.getText().trim()));
            inv.setDescuento(parseNullableDouble(txtDescuento));
            inv.setExistencia(parseNullableInt(txtExistencia));
            inv.setStatus((String) cbStatus.getSelectedItem());

            boolean ok;
            if (edicion) ok = dao.actualizar(inv);
            else         ok = dao.insertar(inv);

            if (ok) {
                guardado = true;
                JOptionPane.showMessageDialog(this, edicion ? "Artículo actualizado" : "Artículo registrado");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "No se guardó el registro", "Atención", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            if (!edicion && msg != null && msg.toLowerCase().contains("duplicate")) {
                JOptionPane.showMessageDialog(this, "El código de artículo ya existe.", "Duplicado", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Error SQL: " + msg, "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Revisa los campos numéricos (código, precio, descuento, existencia).",
                    "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void calcFinal() {
        try {
            double precio = txtPrecio.getText().isBlank() ? 0.0 : Double.parseDouble(txtPrecio.getText().trim());
            double desc = txtDescuento.getText().isBlank() ? 0.0 : Double.parseDouble(txtDescuento.getText().trim());
            double finalP = precio * (1.0 - (desc / 100.0));
            txtPrecioFinal.setText(String.format("%.2f", finalP));
        } catch (NumberFormatException nfe) {
            txtPrecioFinal.setText("");
        }
    }

    public boolean isGuardado() { return guardado; }

    // ---- helpers
    private void warn(String msg, JComponent focus) {
        JOptionPane.showMessageDialog(this, msg, "Validación", JOptionPane.WARNING_MESSAGE);
        focus.requestFocus();
    }
    private String n(String s) { return s == null ? "" : s; }
    private String blankToNull(JTextField t) {
        String s = t.getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private Integer parseNullableInt(JTextField t) { return t.getText().isBlank() ? null : Integer.parseInt(t.getText().trim()); }
    private Double parseNullableDouble(JTextField t) { return t.getText().isBlank() ? null : Double.parseDouble(t.getText().trim()); }

    // ---- filtros
    private void applyDigitsOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DigitsOnlyFilter(maxLen));
    }
    private void applyDecimalOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DecimalFilter(maxLen));
    }

    static class DigitsOnlyFilter extends DocumentFilter {
        private final int maxLen;
        DigitsOnlyFilter(int maxLen) { this.maxLen = maxLen; }
        private boolean valid(String text) {
            if (text == null) return true;
            if (text.length() > maxLen) return false;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch < '0' || ch > '9') return false;
            }
            return true;
        }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text==null?"":text) + cur.substring(offset+length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    static class DecimalFilter extends DocumentFilter {
        private final int maxLen;
        DecimalFilter(int maxLen) { this.maxLen = maxLen; }
        private boolean valid(String text) {
            if (text == null) return true;
            if (text.length() > maxLen) return false;
            int dots = 0;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch >= '0' && ch <= '9') continue;
                if (ch == '.') { if (++dots > 1) return false; continue; }
                return false;
            }
            return true;
        }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text==null?"":text) + cur.substring(offset+length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }
}
