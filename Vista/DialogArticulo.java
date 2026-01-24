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
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import Controlador.InventarioDAO;
import Modelo.Inventario;
import Utilidades.SeguridadUI;

public class DialogArticulo extends JDialog {

    private JTextField txtCodigo, txtArticulo, txtMarca, txtModelo, txtTalla, txtColor;
    private JTextField txtDesc1, txtDesc2;
    private JTextField txtPrecio, txtDescuento, txtPrecioFinal, txtExistencia, txtFechaRegistro;
    private JTextField txtConteo;
    private JTextField txtCostoIva;
    private JTextField txtRemision, txtFactura, txtFechaPago;
    private JTextField txtNombreNovia;   // ← NUEVO
    private JComboBox<String> cbStatus;

    // --- EXISTENCIA protegida ---
    private boolean existenciaDesbloqueada = false;
    private Integer existenciaOriginal = null;
    private Color bgExistenciaNormal;


    private boolean guardado = false;
    private final InventarioDAO dao = new InventarioDAO();

    private final boolean edicion;
    private final String codigoOriginal;
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd-MM-uuuu");


    // bandera interna: ya se validó la clave maestra en este diálogo
    private boolean camposAdminDesbloqueados = false;

    // ---- Constructor para ALTA ----
    public DialogArticulo(Frame owner) {
        this(owner, null);
    }

    // ---- Constructor para EDICIÓN ----
    public DialogArticulo(Frame owner, Inventario inv) {
        super(owner, inv == null ? "Registrar artículo" : "Modificar artículo", true);
        this.edicion = (inv != null);
        this.codigoOriginal = edicion ? inv.getCodigoArticulo() : null;

        setSize(620, 560);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;

        txtCodigo = new JTextField();
        txtArticulo = new JTextField();
        txtDesc1 = new JTextField();
        txtDesc2 = new JTextField();
        txtMarca = new JTextField();
        txtModelo = new JTextField();
        txtTalla = new JTextField();
        txtColor = new JTextField();
        txtNombreNovia = new JTextField();   // ← NUEVO

        txtPrecio = new JTextField();
        applyDecimalOnly(txtPrecio, 10);
        txtDescuento = new JTextField();
        applyDecimalOnly(txtDescuento, 6);

        txtPrecioFinal = new JTextField();
        txtPrecioFinal.setEditable(false);
        txtPrecioFinal.setForeground(new Color(0, 102, 0));

        txtExistencia = new JTextField();
        bgExistenciaNormal = txtExistencia.getBackground();

        applyDigitsOnly(txtExistencia, 10);
        if (!edicion) {
            txtExistencia.setText("1");
        }
        // Existencia: en ALTA editable, en EDICIÓN bloqueada hasta clave
        if (edicion) {
            setExistenciaEditable(false);
        } else {
            setExistenciaEditable(true);
        }

        txtConteo = new JTextField();
        applyDigitsOnly(txtConteo, 10);

        txtCostoIva = new JTextField();
        applyDecimalOnly(txtCostoIva, 10);
        txtRemision = new JTextField();
        txtFactura = new JTextField();
        txtFechaPago = new JTextField();
        applyDateMask(txtFechaPago);


        cbStatus = new JComboBox<>(new String[] { "A", "C" });
        txtFechaRegistro = new JTextField();
        txtFechaRegistro.setEditable(false);

        // al inicio, los campos de administración se bloquean
        habilitarCamposAdmin(false);

        // ========= FILAS DEL FORMULARIO =========
        addRow(p, c, y++, new JLabel("Código artículo*:"), txtCodigo);
        addRow(p, c, y++, new JLabel("Artículo*:"), txtArticulo);
        addRow(p, c, y++, new JLabel("Descripción 1:"), txtDesc1);
        addRow(p, c, y++, new JLabel("Descripción 2:"), txtDesc2);
        addRow(p, c, y++, new JLabel("Marca:"), txtMarca);
        addRow(p, c, y++, new JLabel("Modelo:"), txtModelo);
        addRow(p, c, y++, new JLabel("Talla:"), txtTalla);
        addRow(p, c, y++, new JLabel("Color:"), txtColor);
        addRow(p, c, y++, new JLabel("Nombre novia:"), txtNombreNovia);

        // Precio / Descuento / Precio final
        y = addRowTriple(p, c, y,
                new JLabel("Precio*:"), txtPrecio,
                new JLabel("Descuento (%):"), txtDescuento,
                new JLabel("Precio final:"), txtPrecioFinal);

        // Existencia / Conteo / Status
        y = addRowTriple(p, c, y,
                new JLabel("Existencia:"), txtExistencia,
                new JLabel("Conteo:"), txtConteo,
                new JLabel("Status:"), cbStatus);
                if (edicion) {
    JButton btnExistencia = new JButton("Desbloquear existencia");
    c.gridx = 0;
    c.gridy = y++;
    c.gridwidth = 4;
    p.add(btnExistencia, c);
    c.gridwidth = 1;

    btnExistencia.addActionListener(_e -> {
        if (existenciaDesbloqueada) {
            JOptionPane.showMessageDialog(this,
                    "La existencia ya está desbloqueada.",
                    "Información",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (SeguridadUI.pedirYValidarClave(this)) {
            existenciaDesbloqueada = true;
            setExistenciaEditable(true);
        }
    });
}

        // Fecha registro / Costo IVA / Fecha pago
        y = addRowTriple(p, c, y,
                new JLabel("Fecha registro:"), txtFechaRegistro,
                new JLabel("Costo c/IVA:"), txtCostoIva,
                new JLabel("F. pago:"), txtFechaPago);

        // Remisión
        addRow(p, c, y++, new JLabel("Remisión:"), txtRemision);
        // Factura
        addRow(p, c, y++, new JLabel("Factura:"), txtFactura);

        // Botón de desbloqueo con SeguridadUI
        JButton btnAdmin = new JButton("Desbloquear datos de factura");
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 4;
        p.add(btnAdmin, c);
        c.gridwidth = 1;

        btnAdmin.addActionListener(_e -> {
            if (camposAdminDesbloqueados) {
                JOptionPane.showMessageDialog(this,
                        "Los campos de factura ya están desbloqueados.",
                        "Información",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // reutilizamos la misma lógica de EmpresaPanel
            if (SeguridadUI.pedirYValidarClave(this)) {
                camposAdminDesbloqueados = true;
                habilitarCamposAdmin(true);
            }
        });

        // ===== SCROLL PARA todo EL FORMULARIO =====
        JScrollPane scroll = new JScrollPane(
                p,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(null);

        // Listeners para recalcular precio final
        DocumentListener recalc = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { calcFinal(); }
            @Override public void removeUpdate(DocumentEvent e) { calcFinal(); }
            @Override public void changedUpdate(DocumentEvent e) { calcFinal(); }
        };
        txtPrecio.getDocument().addDocumentListener(recalc);
        txtDescuento.getDocument().addDocumentListener(recalc);

        // Botones inferiores
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = new JButton(edicion ? "Actualizar" : "Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        actions.add(btnGuardar);
        actions.add(btnCancelar);

        btnGuardar.addActionListener(_e -> guardar());
        btnCancelar.addActionListener(_e -> dispose());

        add(scroll, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        // Si estamos en edición, precargar datos y bloquear el código
        if (edicion) {
            cargar(inv);
            txtCodigo.setEditable(false);
        }

        calcFinal();
    }

    private void addRow(JPanel p, GridBagConstraints c, int y, JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l, c);
        c.gridx = 1; c.gridy = y; c.gridwidth = 3; p.add(f, c);
        c.gridwidth = 1;
    }

    private int addRowTriple(JPanel p, GridBagConstraints c, int y,
                             JComponent l1, JComponent f1,
                             JComponent l2, JComponent f2,
                             JComponent l3, JComponent f3) {
        // fila 1
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l1, c);
        c.gridx = 1; p.add(f1, c);
        c.gridx = 2; p.add(l2, c);
        c.gridx = 3; p.add(f2, c);

        // fila 2
        y++;
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l3, c);
        c.gridx = 1; p.add(f3, c);
        c.gridx = 2; c.gridwidth = 2; p.add(Box.createHorizontalStrut(0), c);
        c.gridwidth = 1;

        return y + 1;
    }

    private void cargar(Inventario inv) {
        txtCodigo.setText(inv.getCodigoArticulo());
        txtArticulo.setText(inv.getArticulo());
        txtDesc1.setText(n(inv.getDescripcion1()));
        txtDesc2.setText(n(inv.getDescripcion2()));
        txtMarca.setText(n(inv.getMarca()));
        txtModelo.setText(n(inv.getModelo()));
        txtTalla.setText(n(inv.getTalla()));
        txtColor.setText(n(inv.getColor()));
        txtNombreNovia.setText(n(inv.getNombreNovia()));   // ← NUEVO

        txtPrecio.setText(inv.getPrecio() == null ? "" : String.valueOf(inv.getPrecio()));
        txtDescuento.setText(inv.getDescuento() == null ? "" : String.valueOf(inv.getDescuento()));
        txtExistencia.setText(inv.getExistencia() == null ? "" : String.valueOf(inv.getExistencia()));
        txtConteo.setText(inv.getInventarioConteo() == null ? "" : String.valueOf(inv.getInventarioConteo()));

        txtCostoIva.setText(inv.getCostoIva() == null ? "" : String.valueOf(inv.getCostoIva()));
        txtRemision.setText(n(inv.getRemision()));
        txtFactura.setText(n(inv.getFactura()));
        txtFechaPago.setText(inv.getFechaPago() == null ? "" : inv.getFechaPago().format(DF));

        cbStatus.setSelectedItem(inv.getStatus() == null ? "A" : inv.getStatus());
        txtFechaRegistro.setText(inv.getFechaRegistro() == null ? "" : inv.getFechaRegistro().format(DF));

        // al abrir, siempre bloqueados de nuevo
        habilitarCamposAdmin(false);
        camposAdminDesbloqueados = false;
        existenciaOriginal = inv.getExistencia();
        setExistenciaEditable(false);
        existenciaDesbloqueada = false;

    }

    private void guardar() {
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
            inv.setDescripcion1(blankToNull(txtDesc1));
            inv.setDescripcion2(blankToNull(txtDesc2));
            inv.setMarca(blankToNull(txtMarca));
            inv.setModelo(blankToNull(txtModelo));
            inv.setTalla(blankToNull(txtTalla));
            inv.setColor(blankToNull(txtColor));
            inv.setPrecio(Double.parseDouble(txtPrecio.getText().trim()));
            inv.setDescuento(parseNullableDouble(txtDescuento));
            inv.setExistencia(parseNullableInt(txtExistencia));
            inv.setInventarioConteo(parseNullableInt(txtConteo));
            inv.setStatus((String) cbStatus.getSelectedItem());
            
            inv.setNombreNovia(blankToNull(txtNombreNovia));   // ← NUEVO
            inv.setCostoIva(parseNullableDouble(txtCostoIva));
            inv.setRemision(blankToNull(txtRemision));
            inv.setFactura(blankToNull(txtFactura));
            inv.setFechaPago(parseNullableDate(txtFechaPago));

            boolean ok;
            if (edicion) ok = dao.actualizar(inv);
            else         ok = dao.insertar(inv);

            if (ok) {
                guardado = true;
                JOptionPane.showMessageDialog(this, edicion ? "Artículo actualizado" : "Artículo registrado");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "No se guardó el registro",
                        "Atención", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            if (!edicion && msg != null && msg.toLowerCase().contains("duplicate")) {
                JOptionPane.showMessageDialog(this, "El código de artículo ya existe.",
                        "Duplicado", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Error SQL: " + msg,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this,
                    "Revisa los campos numéricos (precio, descuento, existencia, conteo, costo c/IVA).",
                    "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void calcFinal() {
        try {
            double precio = txtPrecio.getText().isBlank() ? 0.0 :
                    Double.parseDouble(txtPrecio.getText().trim());
            double desc = txtDescuento.getText().isBlank() ? 0.0 :
                    Double.parseDouble(txtDescuento.getText().trim());
            double finalP = precio * (1.0 - (desc / 100.0));
            txtPrecioFinal.setText(String.format("%.2f", finalP));
        } catch (NumberFormatException nfe) {
            txtPrecioFinal.setText("");
        }
    }

    public boolean isGuardado() { return guardado; }

    // ==== helpers básicos ====
    private void warn(String msg, JComponent focus) {
        JOptionPane.showMessageDialog(this, msg, "Validación", JOptionPane.WARNING_MESSAGE);
        focus.requestFocus();
    }
    private String n(String s) { return s == null ? "" : s; }
    private String blankToNull(JTextField t) {
        String s = t.getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private Integer parseNullableInt(JTextField t) {
        return t.getText().isBlank() ? null : Integer.parseInt(t.getText().trim());
    }
    private Double parseNullableDouble(JTextField t) {
        return t.getText().isBlank() ? null : Double.parseDouble(t.getText().trim());
    }
    private java.time.LocalDate parseNullableDate(JTextField t) {
    String s = t.getText();
    if (s == null || s.isBlank()) return null;
    return java.time.LocalDate.parse(s.trim(), DF); // DF = dd-MM-uuuu
}


    // ==== filtros ====
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
        @Override public void replace(FilterBypass fb, int offset, int length,
                                      String text, AttributeSet attrs) throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset)
                    + (text == null ? "" : text)
                    + cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset,
                                           String text, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }
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
                if (ch == '.') {
                    if (++dots > 1) return false;
                    continue;
                }
                return false;
            }
            return true;
        }
        @Override public void replace(FilterBypass fb, int offset, int length,
                                      String text, AttributeSet attrs) throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset)
                    + (text == null ? "" : text)
                    + cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset,
                                           String text, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }
    }

    private void habilitarCamposAdmin(boolean enabled) {
        txtCostoIva.setEditable(enabled);
        txtRemision.setEditable(enabled);
        txtFactura.setEditable(enabled);
        txtFechaPago.setEditable(enabled);
    }
    // ==== máscara de fecha dd-MM-aaaa para txtFechaPago ====
private void applyDateMask(JTextField field) {
    ((AbstractDocument) field.getDocument()).setDocumentFilter(new DateFieldFilter());
}

static class DateFieldFilter extends DocumentFilter {

    @Override
    public void replace(FilterBypass fb, int offset, int length,
                        String text, AttributeSet attrs) throws BadLocationException {
        String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
        String next = cur.substring(0, offset)
                + (text == null ? "" : text)
                + cur.substring(offset + length);
        String formatted = formatDigits(next);
        if (formatted != null) {
            super.replace(fb, 0, fb.getDocument().getLength(), formatted, attrs);
        }
    }

    @Override
    public void insertString(FilterBypass fb, int offset,
                             String text, AttributeSet attrs) throws BadLocationException {
        replace(fb, offset, 0, text, attrs);
    }

    private String formatDigits(String input) {
        // Sólo dígitos
        String digits = input.replaceAll("\\D", "");
        if (digits.length() > 8) {
            digits = digits.substring(0, 8); // ddMMyyyy
        }
        int len = digits.length();
        if (len == 0) return "";
        if (len <= 2) return digits;                            // d, dd
        if (len <= 4) return digits.substring(0,2) + "-" + digits.substring(2); // dd-MM
        // dd-MM-yyyy
        return digits.substring(0,2) + "-" +
               digits.substring(2,4) + "-" +
               digits.substring(4);
    }
}
private void setExistenciaEditable(boolean editable) {
    txtExistencia.setEditable(editable);
    if (editable) {
        txtExistencia.setBackground(bgExistenciaNormal);
    } else {
        txtExistencia.setBackground(UIManager.getColor("TextField.inactiveBackground"));
    }
}

}
