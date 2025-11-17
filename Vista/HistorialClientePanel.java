package Vista;

import Controlador.HistorialClienteDAO;
import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.HistorialCliente;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class HistorialClientePanel extends JPanel {

    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private final clienteDAO cliDao           = new clienteDAO();
    private final HistorialClienteDAO histDao = new HistorialClienteDAO();
    private final NotasDAO notasDao           = new NotasDAO();

    private JTextField txtTel1;
    private JTextField txtNombre;
    private JTextField txtFechaEvento;

    private JTextField txtSaldoMigrado;
    private JFormattedTextField txtFechaSaldo;
    private JTextArea txtObsequios;
    private JTextArea txtObservacion;

    // Siempre en dígitos, sin guiones
    private String telefonoActual = null;

    public HistorialClientePanel() {
        setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int y = 0;

        // ===== Teléfono + botón buscar =====
        txtTel1 = new JTextField();
        // NUEVO: formato 123-456-7890, pero solo permite dígitos internamente
        applyTelefonoFormat(txtTel1, 15);

        JButton btBuscar = new JButton("Buscar cliente");
        btBuscar.addActionListener(_e -> cargarCliente());

        addRow(p, c, y++,
                new JLabel("Teléfono 1*:"), txtTel1,
                new JLabel(""), btBuscar);

        // ===== Nombre y fecha evento (solo lectura) =====
        txtNombre = roField();
        txtFechaEvento = roField();

        addRow(p, c, y++,
                new JLabel("Nombre del cliente:"), txtNombre,
                new JLabel("Fecha de evento:"), txtFechaEvento);

        // ===== Saldo histórico y fecha del saldo =====
        txtSaldoMigrado = new JTextField();
        applyDecimalOnly(txtSaldoMigrado, 12);
        txtSaldoMigrado.setToolTipText("Saldo histórico del cliente (antes del sistema).");

        txtFechaSaldo = createDateFieldMX();
        txtFechaSaldo.setToolTipText("Fecha en que existía ese saldo (DD-MM-YYYY).");

        addRow(p, c, y++,
                new JLabel("Saldo histórico ($):"), txtSaldoMigrado,
                new JLabel("Fecha del saldo (DD-MM-YYYY):"), txtFechaSaldo);

        // ===== Obsequios =====
        txtObsequios = new JTextArea(3, 40);
        txtObsequios.setLineWrap(true);
        txtObsequios.setWrapStyleWord(true);

        addRowFull(p, c, y++,
                new JLabel("Obsequios otorgados:"), new JScrollPane(txtObsequios));

        // ===== Observación =====
        txtObservacion = new JTextArea(3, 40);
        txtObservacion.setLineWrap(true);
        txtObservacion.setWrapStyleWord(true);

        addRowFull(p, c, y++,
                new JLabel("Observación especial:"), new JScrollPane(txtObservacion));

        add(p, BorderLayout.CENTER);

        // ===== Botones =====
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btGuardar = new JButton("Guardar historial");
        JButton btLimpiar = new JButton("Limpiar");

        btGuardar.addActionListener(_e -> guardarHistorial());
        btLimpiar.addActionListener(_e -> limpiarTodo());

        actions.add(btGuardar);
        actions.add(btLimpiar);

        add(actions, BorderLayout.SOUTH);
    }

    // ================== LÓGICA ==================

    private void cargarCliente() {
        // USAR SIEMPRE SOLO DÍGITOS
        String tel = phoneDigits(txtTel1.getText());
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Captura el teléfono del cliente.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            ClienteResumen cr = cliDao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                telefonoActual = null;
                limpiarCabecera();
                limpiarDetalle();
                JOptionPane.showMessageDialog(this,
                        "No se encontró un cliente con ese teléfono.",
                        "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Guardamos el teléfono REAL (solo dígitos)
            telefonoActual = tel;

            txtNombre.setText(cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto());
            if (cr.getFechaEvento() != null) {
                txtFechaEvento.setText(cr.getFechaEvento().format(MX));
            } else {
                txtFechaEvento.setText("");
            }

            // Cargar historial si existe
            HistorialCliente h = histDao.cargarPorTelefono(tel);
            if (h != null) {
                if (h.getSaldoMigrado() != null) {
                    txtSaldoMigrado.setText(String.format("%,.2f", h.getSaldoMigrado()));
                } else {
                    txtSaldoMigrado.setText("");
                }
                if (h.getFechaSaldo() != null) {
                    txtFechaSaldo.setText(h.getFechaSaldo().format(MX));
                } else {
                    txtFechaSaldo.setText("");
                }
                txtObsequios.setText(h.getObsequios() == null ? "" : h.getObsequios());
                txtObservacion.setText(h.getObservacion() == null ? "" : h.getObservacion());
            } else {
                limpiarDetalle();
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error SQL al buscar cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarHistorial() {
        // De nuevo, solo dígitos
        String tel = phoneDigits(txtTel1.getText());
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Captura el teléfono del cliente.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (telefonoActual == null || !tel.equals(telefonoActual)) {
            JOptionPane.showMessageDialog(this,
                    "Primero busca y carga el cliente antes de guardar.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Parsear saldo
        Double saldo = null;
        try {
            saldo = parseNullableDouble(txtSaldoMigrado);
            if (saldo != null && saldo < 0) {
                JOptionPane.showMessageDialog(this,
                        "El saldo no puede ser negativo.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Número inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Parsear fecha del saldo
        LocalDate fechaSaldo = null;
        try {
            fechaSaldo = parseFecha(txtFechaSaldo);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Fecha inválida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (saldo != null && fechaSaldo == null) {
            JOptionPane.showMessageDialog(this,
                    "Si capturas un saldo histórico, la fecha del saldo es obligatoria.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object[] opts = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(
                this,
                "Se guardará el historial del cliente.\n" +
                "Si capturaste saldo, se generará / actualizará una nota de crédito de migración.\n\n" +
                "¿Deseas continuar?",
                "Confirmación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[1]);
        if (r != JOptionPane.YES_OPTION) return;

        HistorialCliente h = new HistorialCliente();
        h.setTelefono1(tel);  // GUARDAMOS SOLO DÍGITOS
        h.setSaldoMigrado(saldo);
        h.setFechaSaldo(fechaSaldo);
        h.setObsequios(nullIfBlank(txtObsequios.getText()));
        h.setObservacion(nullIfBlank(txtObservacion.getText()));

        try {
            histDao.guardar(h);
            // Actualizar nota de migración (tel limpio)
            notasDao.registrarNotaMigracion(tel, fechaSaldo, saldo);

            JOptionPane.showMessageDialog(this,
                    "Historial guardado correctamente.",
                    "Listo", JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error SQL al guardar historial: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================== Helpers UI / parsing ==================

    private void limpiarCabecera() {
        txtNombre.setText("");
        txtFechaEvento.setText("");
    }

    private void limpiarDetalle() {
        txtSaldoMigrado.setText("");
        txtFechaSaldo.setValue(null);
        txtObsequios.setText("");
        txtObservacion.setText("");
    }

    private void limpiarTodo() {
        txtTel1.setText("");
        telefonoActual = null;
        limpiarCabecera();
        limpiarDetalle();
        txtTel1.requestFocus();
    }

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

    private JTextField roField() {
        JTextField t = new JTextField();
        t.setEditable(false);
        Color bg = UIManager.getColor("TextField.inactiveBackground");
        if (bg == null) bg = new Color(235, 235, 235);
        t.setBackground(bg);
        return t;
    }

    // ====== Formato de teléfono ======

    /** Devuelve solo los dígitos del texto (sin guiones, espacios, etc.) */
    private static String phoneDigits(String s) {
        if (s == null) return "";
        return s.replaceAll("[^0-9]", "");
    }

    /** Formatea 1234567890 -> 123-456-7890 (si hay menos dígitos, hace lo posible) */
    private static String formatPhone(String digits) {
        if (digits == null) return "";
        digits = phoneDigits(digits);
        int len = digits.length();
        if (len <= 3) return digits;
        if (len <= 6) return digits.substring(0, 3) + "-" + digits.substring(3);
        if (len <= 10) return digits.substring(0, 3) + "-" +
                         digits.substring(3, 6) + "-" +
                         digits.substring(6);
        // Si hay más de 10 dígitos, lo dejamos tal cual (o podrías ajustar)
        return digits;
    }

    /** Aplica el filtro de formato de teléfono a un JTextField. */
    private void applyTelefonoFormat(JTextField field, int maxDigits) {
        ((AbstractDocument) field.getDocument())
                .setDocumentFilter(new TelefonoFormatterFilter(maxDigits));
    }

    // ===== filtros para otros campos =====

    private void applyDigitsOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument())
                .setDocumentFilter(new DigitsOnlyFilter(maxLen));
    }

    private void applyDecimalOnly(JTextField field, int maxLen) {
        ((AbstractDocument) field.getDocument())
                .setDocumentFilter(new DecimalFilter(maxLen));
    }

    private JFormattedTextField createDateFieldMX() {
        try {
            MaskFormatter mf = new MaskFormatter("##-##-####"); // DD-MM-YYYY
            mf.setPlaceholderCharacter('_');
            JFormattedTextField f = new JFormattedTextFieldFixed(mf);
            f.setColumns(10);
            return f;
        } catch (Exception e) {
            return new JFormattedTextField();
        }
    }

    private Double parseNullableDouble(JTextField t) {
        String s = t.getText();
        if (s == null) return null;
        s = s.trim().replace(",", "");
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor numérico inválido: " + s);
        }
    }

    private LocalDate parseFecha(JFormattedTextField f) {
        String s = f.getText();
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.contains("_")) return null;
        try {
            return LocalDate.parse(s, MX);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Fecha inválida: " + s);
        }
    }

    private String nullIfBlank(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    // ===== DocumentFilters =====

    /** Solo dígitos (0-9) con longitud máxima (para otros usos, no teléfonos). */
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

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset) +
                    (text == null ? "" : text) +
                    cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }
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
                if (ch == ',') continue; // permitir comas como separador de miles
                return false;
            }
            return true;
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset) +
                    (text == null ? "" : text) +
                    cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }
    }

    /** Filtro para teléfonos: solo dígitos, aplica formato 123-456-7890. */
    static class TelefonoFormatterFilter extends DocumentFilter {
        private final int maxDigits;

        TelefonoFormatterFilter(int maxDigits) {
            this.maxDigits = maxDigits;
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length,
                            String text, AttributeSet attrs) throws BadLocationException {
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset) +
                    (text == null ? "" : text) +
                    cur.substring(offset + length);

            String digits = phoneDigits(next);
            if (digits.length() > maxDigits) return;

            String pretty = formatPhone(digits);
            super.replace(fb, 0, doc.getLength(), pretty, attrs);
        }

        @Override
        public void insertString(FilterBypass fb, int offset,
                                 String text, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }
    }

    /** Arreglo a problemas de borrado con MaskFormatter */
    static class JFormattedTextFieldFixed extends JFormattedTextField {
        public JFormattedTextFieldFixed(AbstractFormatter formatter) {
            super(formatter);
        }
        @Override
        public void processKeyEvent(java.awt.event.KeyEvent e) {
            super.processKeyEvent(e);
            this.setCaretPosition(Math.min(getCaretPosition(), getText().length()));
        }
    }

    // ---- main de prueba opcional ----
    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(i.getName())) {
                    UIManager.setLookAndFeel(i.getClassName());
                    break;
                }
        } catch (Exception ignore) {}

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Historial de cliente (test)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(800, 600);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(new HistorialClientePanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
