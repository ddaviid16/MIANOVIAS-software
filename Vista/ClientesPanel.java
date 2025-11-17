package Vista;

import Controlador.clienteDAO;
import Modelo.cliente;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ClientesPanel extends JPanel {

    // Campos de texto
    private JTextField txtTel1, txtTel2, txtNombre, txtApPat, txtApMat;
    private JTextField txtBusto, txtCintura, txtCadera, txtEdad;

    // Fechas (máscara mexicana DD-MM-YYYY)
    private JFormattedTextField txtFechaEvento, txtPrueba1, txtPrueba2, txtEntrega;

    // Combos ENUM
    private JComboBox<String> cbComoSeEntero, cbLugar, cbStatus, cbSituacion;

    private final clienteDAO dao = new clienteDAO();

    // Formateadores de fecha
    private static final DateTimeFormatter MX  = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final DateTimeFormatter SQL = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    public ClientesPanel() {
        setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int y = 0;

        // ===== Teléfonos (solo números)
        txtTel1 = new JTextField();
        txtTel2 = new JTextField();
        applyDigitsOnly(txtTel1, 15);
        applyDigitsOnly(txtTel2, 15);
        txtTel1.setToolTipText("Solo números.");
        txtTel2.setToolTipText("Solo números.");
        addRow(p, c, y++, new JLabel("Teléfono 1*:"), txtTel1,
                new JLabel("Teléfono 2:"), txtTel2);

        // ===== Nombre (izq) | Apellido paterno (der)
        txtNombre = new JTextField();
        applyLettersOnly(txtNombre, 60);
        txtNombre.setToolTipText("Solo letras y espacios.");

        txtApPat = new JTextField();
        applyLettersOnly(txtApPat, 60);

        addRow(p, c, y++, new JLabel("Nombre*:"), txtNombre,
                new JLabel("Apellido paterno:"), txtApPat);

        // ===== Apellido materno (debajo izq) | Edad (der)
        txtApMat = new JTextField();
        applyLettersOnly(txtApMat, 60);

        txtEdad = new JTextField();
        applyDigitsOnly(txtEdad, 3);

        addRow(p, c, y++, new JLabel("Apellido materno:"), txtApMat,
                new JLabel("Edad:"), txtEdad);

        // ===== ¿Cómo se enteró? / Lugar del evento
        cbComoSeEntero = new JComboBox<>(new String[]{"", "UBICACION", "RECOMENDACION", "GOOGLE MAPS", "TIKTOK"});
        cbLugar        = new JComboBox<>(new String[]{"", "HACIENDA", "JARDIN", "SALON", "PLAYA"});
        addRow(p, c, y++, new JLabel("¿Cómo se enteró?"), cbComoSeEntero,
                new JLabel("Lugar del evento:"), cbLugar);

        // ===== Fechas con formato mexicano (DD-MM-YYYY)
        txtFechaEvento = createDateFieldMX();
        addRowFull(p, c, y++, new JLabel("Fecha del evento (DD-MM-YYYY):"), txtFechaEvento);

        txtPrueba1 = createDateFieldMX();
        addRowFull(p, c, y++, new JLabel("Fecha de prueba 1 (DD-MM-YYYY):"), txtPrueba1);

        txtPrueba2 = createDateFieldMX(); // debajo de prueba 1
        addRowFull(p, c, y++, new JLabel("Fecha de prueba 2 (DD-MM-YYYY):"), txtPrueba2);

        txtEntrega = createDateFieldMX(); // debajo de prueba 2
        addRowFull(p, c, y++, new JLabel("Fecha de entrega (DD-MM-YYYY):"), txtEntrega);

        // ===== Medidas (solo decimales)
        txtBusto   = new JTextField();
        txtCintura = new JTextField();
        txtCadera  = new JTextField();
        applyDecimalOnly(txtBusto,   6);
        applyDecimalOnly(txtCintura, 6);
        applyDecimalOnly(txtCadera,  6);
        txtBusto.setToolTipText("Ejemplo: 92.5");
        txtCintura.setToolTipText("Ejemplo: 70.0");
        txtCadera.setToolTipText("Ejemplo: 98.2");
        addRow(p, c, y++, new JLabel("Busto (cm):"), txtBusto,
                new JLabel("Cintura (cm):"), txtCintura);
        addRowFull(p, c, y++, new JLabel("Cadera (cm):"), txtCadera);

        // ===== Status / Situación
        cbStatus   = new JComboBox<>(new String[]{"A", "C"});
        cbSituacion = new JComboBox<>(new String[]{"NORMAL", "CANCELA DEFINITIVO", "POSPONE BODA INDEFINIDO"});
        addRow(p, c, y++, new JLabel("Status:"), cbStatus,
                new JLabel("Situación del evento:"), cbSituacion);

        // ===== Botones
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = new JButton("Guardar");
        JButton btnLimpiar = new JButton("Limpiar");
        actions.add(btnGuardar);
        actions.add(btnLimpiar);

        btnGuardar.addActionListener(_e -> guardar());
        btnLimpiar.addActionListener(_e -> confirmarLimpiar());

        add(p, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    /** Constructor con prellenado del teléfono 1 */
    public ClientesPanel(String telefono1Prefill) {
        this();
        if (telefono1Prefill != null) {
            txtTel1.setText(telefono1Prefill.trim());
            txtNombre.requestFocus();
        }
    }

    // ---------------------- Layout helpers
    private void addRow(JPanel p, GridBagConstraints c, int y,
                        JComponent l1, JComponent f1, JComponent l2, JComponent f2) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        p.add(l1, c);
        c.gridx = 1; c.gridy = y;
        p.add(f1, c);
        c.gridx = 2; c.gridy = y;
        p.add(l2, c);
        c.gridx = 3; c.gridy = y;
        p.add(f2, c);
    }
    private void addRowFull(JPanel p, GridBagConstraints c, int y,
                            JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        p.add(l, c);
        c.gridx = 1; c.gridy = y; c.gridwidth = 3;
        p.add(f, c);
        c.gridwidth = 1;
    }

    // ---------------------- Validaciones
    private void applyDigitsOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DigitsOnlyFilter(maxLen));
    }
    private void applyLettersOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new LettersOnlyFilter(maxLen));
    }
    private void applyDecimalOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DecimalFilter(maxLen));
    }
    private JFormattedTextField createDateFieldMX() {
        try {
            MaskFormatter mf = new MaskFormatter("##-##-####"); // DD-MM-YYYY
            mf.setPlaceholderCharacter('_');
            JFormattedTextField f = new JFormattedTextFieldFixed(mf);
            f.setColumns(10);
            f.setToolTipText("Formato: DD-MM-YYYY (ej. 15-09-2025)");
            return f;
        } catch (Exception e) {
            return new JFormattedTextField(); // fallback
        }
    }

    private void limpiar() {
        txtTel1.setText("");
        txtTel2.setText("");
        txtNombre.setText("");
        txtApPat.setText("");
        txtApMat.setText("");
        txtEdad.setText("");

        txtBusto.setText("");
        txtCintura.setText("");
        txtCadera.setText("");

        cbComoSeEntero.setSelectedIndex(0);
        cbLugar.setSelectedIndex(0);
        cbStatus.setSelectedItem("A");
        cbSituacion.setSelectedItem("NORMAL");

        txtFechaEvento.setValue(null);
        txtPrueba1.setValue(null);
        txtPrueba2.setValue(null);
        txtEntrega.setValue(null);

        txtTel1.requestFocus();
    }

    /** Pregunta si desea borrar todo; si responde SI, limpia la información. */
    private void confirmarLimpiar() {
        Object[] opciones = {"SI", "NO"};
        int resp = JOptionPane.showOptionDialog(
                this,
                "¿Deseas borrar la información capturada hasta el momento?",
                "Confirmación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                opciones,
                opciones[1]
        );
        if (resp == 0) limpiar();
    }

    // ---------------------- Guardar (con confirmación y conversión de fechas a MySQL)
    private void guardar() {
        // Requeridos mínimos
    if (txtTel1.getText().isBlank()) {
        JOptionPane.showMessageDialog(this, "Teléfono 1 es obligatorio", "Validación", JOptionPane.WARNING_MESSAGE);
        txtTel1.requestFocus(); return;
    }
    if (txtNombre.getText().isBlank()) {
        JOptionPane.showMessageDialog(this, "El nombre es obligatorio", "Validación", JOptionPane.WARNING_MESSAGE);
        txtNombre.requestFocus(); return;
    }

    // --- 1) Parsear y VALIDAR fechas ANTES de confirmar
    LocalDate ev  = parseMX(txtFechaEvento.getText());
    LocalDate f1  = parseMX(txtPrueba1.getText());
    LocalDate f2  = parseMX(txtPrueba2.getText());
    LocalDate ent = parseMX(txtEntrega.getText());

    String error = validarFechas(ev, f1, f2, ent);
    if (error != null) {
        JOptionPane.showMessageDialog(this, error, "Fechas inválidas", JOptionPane.WARNING_MESSAGE);
        return;
    }

        Object[] opts = {"SI", "NO"};
        int choice = JOptionPane.showOptionDialog(
                this, "¿La información es correcta?", "Confirmación",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            cliente cli = new cliente();
            cli.setTelefono1(txtTel1.getText().trim());
            cli.setTelefono2(blankToNull(txtTel2));
            cli.setNombre(txtNombre.getText().trim());
            cli.setApellidoPaterno(blankToNull(txtApPat));
            cli.setApellidoMaterno(blankToNull(txtApMat));
            cli.setEdad(parseNullableInt(txtEdad));

            cli.setComoSeEntero(comboVal(cbComoSeEntero)); // ENUM o null
            cli.setLugarEvento(comboVal(cbLugar));          // ENUM o null

            // DD-MM-YYYY → YYYY-MM-DD
            cli.setFechaEvento(maskMexToSql(txtFechaEvento));
            cli.setFechaPrueba1(maskMexToSql(txtPrueba1));
            cli.setFechaPrueba2(maskMexToSql(txtPrueba2));
            cli.setFechaEntrega(maskMexToSql(txtEntrega));

            cli.setBusto(parseNullableDouble(txtBusto));
            cli.setCintura(parseNullableDouble(txtCintura));
            cli.setCadera(parseNullableDouble(txtCadera));

            cli.setStatus((String) cbStatus.getSelectedItem());
            cli.setSituacionEvento((String) cbSituacion.getSelectedItem());

            boolean ok = dao.crear(cli);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Cliente guardado correctamente");
                limpiar();
            } else {
                JOptionPane.showMessageDialog(this, "No se insertaron filas. Verifica los datos.",
                        "Atención", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Revisa las fechas (usa DD-MM-YYYY) o los campos numéricos.\nDetalle: " + ex.getMessage(),
                    "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error SQL: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error inesperado: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------- Utilidades
    private String blankToNull(JTextField t) {
        String s = t.getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private String comboVal(JComboBox<String> cb) {
        String v = (String) cb.getSelectedItem();
        return (v == null || v.isBlank()) ? null : v;
    }
    private Integer parseNullableInt(JTextField t) {
        if (t.getText().isBlank()) return null;
        return Integer.parseInt(t.getText().trim());
    }
    private Double parseNullableDouble(JTextField t) {
        if (t.getText().isBlank()) return null;
        return Double.parseDouble(t.getText().trim());
    }

    /**
     * Convierte campo con máscara DD-MM-YYYY a "YYYY-MM-DD" (para MySQL).
     * Devuelve null si está vacío/incompleto.
     */
    private String maskMexToSql(JFormattedTextField f) {
        String s = f.getText();
        if (s == null) return null;
        s = s.trim();
        if (s.contains("_") || s.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(s, MX);
            return d.format(SQL);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida: " + s);
        }
    }

    // ---------------------- DocumentFilters
    /** Solo dígitos (0-9) con longitud máxima */
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
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset) + (text == null ? "" : text) + cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    /** Solo letras (incluye tildes y espacios) con longitud máxima */
    static class LettersOnlyFilter extends DocumentFilter {
        private final int maxLen;
        LettersOnlyFilter(int maxLen) { this.maxLen = maxLen; }
        private boolean isAllowed(char ch) { return Character.isLetter(ch) || Character.isSpaceChar(ch); }
        private boolean valid(String text) {
            if (text == null) return true;
            if (text.length() > maxLen) return false;
            for (int i = 0; i < text.length(); i++) if (!isAllowed(text.charAt(i))) return false;
            return true;
        }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset) + (text == null ? "" : text) + cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    /** Decimal: dígitos + un solo punto, con longitud máxima */
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
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset) + (text == null ? "" : text) + cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    /** Arreglo a problemas de borrado con MaskFormatter */
    static class JFormattedTextFieldFixed extends JFormattedTextField {
        public JFormattedTextFieldFixed(AbstractFormatter formatter) { super(formatter); }
        @Override public void processKeyEvent(java.awt.event.KeyEvent e) {
            super.processKeyEvent(e);
            this.setCaretPosition(Math.min(getCaretPosition(), getText().length()));
        }
    }
// ===== Helpers de fechas (dd-MM-uuuu) =====
private static final DateTimeFormatter MX_D = DateTimeFormatter.ofPattern("dd-MM-uuuu");

/** Parsea "dd-MM-uuuu". Devuelve null si está vacío o con guiones bajos. */
private static LocalDate parseMX(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty() || s.contains("_")) return null;
    return LocalDate.parse(s, MX_D);
}

/** Convierte a "yyyy-MM-dd" (para MySQL). */
private static String toSql(LocalDate d) {
    return d == null ? null : d.format(SQL);
}

/** Devuelve null si todo OK; si hay error, devuelve el mensaje. */
private static String validarFechas(LocalDate evento, LocalDate p1, LocalDate p2, LocalDate entrega) {
    LocalDate hoy = LocalDate.now();
    if (evento == null) return "La fecha de evento es obligatoria.";

    // evento > hoy (solo si se capturó)
    if (evento != null && !evento.isAfter(hoy))
        return "La fecha de evento debe ser mayor a la fecha actual.";

    // p1 > hoy && p1 < evento
        if (!p1.isAfter(hoy))
            return "La fecha de prueba 1 debe ser mayor a la fecha actual.";
        if (!p1.isBefore(evento))
            return "La fecha de prueba 1 debe ser menor a la fecha de evento.";

    // p2 > p1 && p2 < evento
        if (!p2.isAfter(p1))
            return "La fecha de prueba 2 debe ser mayor a la fecha de prueba 1.";
        if (!p2.isBefore(evento))
            return "La fecha de prueba 2 debe ser menor a la fecha de evento.";

    // entrega > p2 && entrega < evento
        if (!entrega.isAfter(p2))
            return "La fecha de entrega debe ser mayor a la fecha de prueba 2.";
        if (!entrega.isBefore(evento))
            return "La fecha de entrega debe ser menor a la fecha de evento.";

    return null; // OK
}

    // ---- main de prueba opcional (panel standalone) ----
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Registro de Clientes (test panel)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(760, 620);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(new ClientesPanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
