package Vista;

import Controlador.EmpresaDAO;
import Modelo.Empresa;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.regex.Pattern;
import Utilidades.SeguridadUI;


public class EmpresaPanel extends JPanel {

    // ===== Campos =====
    private JTextField txtNumEmpresa, txtRazonSocial, txtNombreFiscal, txtRFC;
    private JTextField txtCalleNumero, txtColonia, txtCP, txtCiudad, txtEstado;
    private JTextField txtWhatsapp, txtTelefono;
    private JTextField txtInstagram, txtFacebook, txtTikTok;
    private JTextField txtCorreo, txtPaginaWeb;

    // Logo
    private JLabel lblLogoPreview;
    private JButton btnCargarLogo, btnQuitarLogo;
    private byte[] logoBytes = null;
    private static final int LOGO_W = 250, LOGO_H = 250;

    // Botones de acción
    private JButton btnCargar, btnGuardar, btnLimpiar, btnModificar, btnCancelarEdicion, btnCambiarClave;

    // Estado
    private boolean editMode = false;

    private final EmpresaDAO dao = new EmpresaDAO();

    public EmpresaPanel() {
        setLayout(new BorderLayout());

        // ======= HEADER con LOGO =======
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JPanel logoPanel = new JPanel(new BorderLayout(10, 10));
        lblLogoPreview = new JLabel("Sin logo", SwingConstants.CENTER);
        lblLogoPreview.setPreferredSize(new Dimension(LOGO_W, LOGO_H));
        lblLogoPreview.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        logoPanel.add(lblLogoPreview, BorderLayout.CENTER);

        JPanel logoBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnCargarLogo = new JButton("Seleccionar logo…");
        btnQuitarLogo = new JButton("Quitar logo");
        logoBtns.add(btnCargarLogo);
        logoBtns.add(btnQuitarLogo);
        logoPanel.add(logoBtns, BorderLayout.SOUTH);

        header.add(logoPanel, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        btnCargarLogo.addActionListener(_e -> seleccionarLogo());
        btnQuitarLogo.addActionListener(_e -> quitarLogo());

        // ======= FORM PRINCIPAL =======
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;

        txtNumEmpresa = new JTextField("1");
        ((AbstractDocument) txtNumEmpresa.getDocument()).setDocumentFilter(new DigitsOnlyFilter(9));

        addRow(p, c, y++, new JLabel("Número empresa (PK)*:"), txtNumEmpresa,
                new JLabel("Razón social*:"), txtRazonSocial = new JTextField());

        addRow(p, c, y++, new JLabel("Nombre fiscal:"), txtNombreFiscal = new JTextField(),
                new JLabel("RFC:"), txtRFC = new JTextField());
        ((AbstractDocument) txtRFC.getDocument()).setDocumentFilter(new UppercaseFilter(13));

        addRowFull(p, c, y++, new JLabel("Calle y número:"), txtCalleNumero = new JTextField());
        addRow(p, c, y++, new JLabel("Colonia:"), txtColonia = new JTextField(),
                new JLabel("C.P. (5 dígitos):"), txtCP = new JTextField());
        ((AbstractDocument) txtCP.getDocument()).setDocumentFilter(new DigitsOnlyFilter(5));

        addRow(p, c, y++, new JLabel("Ciudad:"), txtCiudad = new JTextField(),
                new JLabel("Estado:"), txtEstado = new JTextField());

        addRow(p, c, y++, new JLabel("WhatsApp:"), txtWhatsapp = new JTextField(),
                new JLabel("Teléfono:"), txtTelefono = new JTextField());
        ((AbstractDocument) txtWhatsapp.getDocument()).setDocumentFilter(new DigitsOnlyFilter(15));
        ((AbstractDocument) txtTelefono.getDocument()).setDocumentFilter(new DigitsOnlyFilter(15));

        addRow(p, c, y++, new JLabel("Correo:"), txtCorreo = new JTextField(),
                new JLabel("Página web:"), txtPaginaWeb = new JTextField());

        addRow(p, c, y++, new JLabel("Instagram:"), txtInstagram = new JTextField(),
                new JLabel("Facebook:"), txtFacebook = new JTextField());
        addRowFull(p, c, y++, new JLabel("TikTok:"), txtTikTok = new JTextField());

        add(p, BorderLayout.CENTER);

        // ===== Acciones =====
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnCambiarClave     = new JButton("Cambiar clave de acceso");
        btnCargar          = new JButton("Cargar");
        btnModificar       = new JButton("Modificar");
        btnGuardar         = new JButton("Guardar");
        btnCancelarEdicion = new JButton("Cancelar edición");
        btnLimpiar         = new JButton("Limpiar");

        btnCambiarClave.addActionListener(_e -> SeguridadUI.cambiarClaveConDialogo(this));
        btnCargar.addActionListener(_e -> cargar());
        btnModificar.addActionListener(_e -> intentarEntrarEnEdicion());
        btnGuardar.addActionListener(_e -> guardar());
        btnCancelarEdicion.addActionListener(_e -> cancelarEdicion());
        btnLimpiar.addActionListener(_e -> confirmarLimpiar());

        actions.add(btnCambiarClave);
        actions.add(btnCargar);
        actions.add(btnModificar);
        actions.add(btnGuardar);
        actions.add(btnCancelarEdicion);
        actions.add(btnLimpiar);
        add(actions, BorderLayout.SOUTH);

        // ===== Estado inicial: SOLO LECTURA + carga automática
        setEditMode(false);
        cargar();
    }

    // ====== Layout helpers
    private void addRow(JPanel p, GridBagConstraints c, int y,
                        JComponent l1, JComponent f1, JComponent l2, JComponent f2) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l1, c);
        c.gridx = 1; c.gridy = y; p.add(f1, c);
        c.gridx = 2; c.gridy = y; p.add(l2, c);
        c.gridx = 3; c.gridy = y; p.add(f2, c);
    }
    private void addRowFull(JPanel p, GridBagConstraints c, int y,
                            JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; p.add(l, c);
        c.gridx = 1; c.gridy = y; c.gridwidth = 3; p.add(f, c);
        c.gridwidth = 1;
    }

    // ====== Modo edición / solo lectura
    private void setEditMode(boolean enable) {
        this.editMode = enable;

        setEditable(txtNumEmpresa, false);
        setEditable(txtRazonSocial, enable);
        setEditable(txtNombreFiscal, enable);
        setEditable(txtRFC, enable);
        setEditable(txtCalleNumero, enable);
        setEditable(txtColonia, enable);
        setEditable(txtCP, enable);
        setEditable(txtCiudad, enable);
        setEditable(txtEstado, enable);
        setEditable(txtWhatsapp, enable);
        setEditable(txtTelefono, enable);
        setEditable(txtInstagram, enable);
        setEditable(txtFacebook, enable);
        setEditable(txtTikTok, enable);
        setEditable(txtCorreo, enable);
        setEditable(txtPaginaWeb, enable);

        btnGuardar.setEnabled(enable);
        btnLimpiar.setEnabled(enable);
        btnCancelarEdicion.setEnabled(enable);
        btnModificar.setEnabled(!enable);

        btnCargarLogo.setEnabled(enable);
        btnQuitarLogo.setEnabled(enable);

        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }

    private void setEditable(JTextField t, boolean editable) {
        t.setEditable(editable);
        t.setOpaque(true);
        Color bgEditable  = UIManager.getColor("TextField.background");
        Color bgReadOnly  = UIManager.getColor("TextField.inactiveBackground");
        if (bgEditable == null) bgEditable = Color.WHITE;
        if (bgReadOnly == null) bgReadOnly = new Color(235, 235, 235);
        t.setBackground(editable ? bgEditable : bgReadOnly);
        Color fg = UIManager.getColor("TextField.foreground");
        if (fg != null) t.setForeground(fg);
    }

private void intentarEntrarEnEdicion() {
    if (SeguridadUI.pedirYValidarClave(this)) {
        setEditMode(true);
        txtRazonSocial.requestFocus();
    }
}


    private void cancelarEdicion() {
        cargar();
        setEditMode(false);
    }

    // ====== Acciones
    public void cargar() {
        try {
            int num = parseIntRequired(txtNumEmpresa, "Número de empresa");
            Empresa e = dao.buscarPorNumero(num);
            if (e == null) {
                limpiarCamposTexto();
                quitarLogo();
                return;
            }
            txtRazonSocial.setText(n(e.getRazonSocial()));
            txtNombreFiscal.setText(n(e.getNombreFiscal()));
            txtRFC.setText(n(e.getRfc()));
            txtCalleNumero.setText(n(e.getCalleNumero()));
            txtColonia.setText(n(e.getColonia()));
            txtCP.setText(n(e.getCodigoPostal()));
            txtCiudad.setText(n(e.getCiudad()));
            txtEstado.setText(n(e.getEstado()));
            txtWhatsapp.setText(n(e.getWhatsapp()));
            txtTelefono.setText(n(e.getTelefono()));
            txtInstagram.setText(n(e.getInstagram()));
            txtFacebook.setText(n(e.getFacebook()));
            txtTikTok.setText(n(e.getTiktok()));
            txtCorreo.setText(n(e.getCorreo()));
            txtPaginaWeb.setText(n(e.getPaginaWeb()));
            this.logoBytes = e.getLogo();
            setLogoPreview(this.logoBytes);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error SQL: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardar() {
        if (!editMode) return;

        if (txtRazonSocial.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Razón social es obligatoria", "Validación", JOptionPane.WARNING_MESSAGE);
            txtRazonSocial.requestFocus(); return;
        }
        String rfc = txtRFC.getText().trim().toUpperCase();
        if (!rfc.isBlank() && !isRFCValid(rfc)) {
            JOptionPane.showMessageDialog(this, "RFC inválido. Ejemplos: ABCD001122XXX / ABC001122XXX",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            txtRFC.requestFocus(); return;
        }
        if (!txtCP.getText().isBlank() && !txtCP.getText().matches("\\d{5}")) {
            JOptionPane.showMessageDialog(this, "Código postal debe ser de 5 dígitos",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            txtCP.requestFocus(); return;
        }
        if (!txtCorreo.getText().isBlank() && !isEmailValid(txtCorreo.getText())) {
            JOptionPane.showMessageDialog(this, "Correo inválido", "Validación", JOptionPane.WARNING_MESSAGE);
            txtCorreo.requestFocus(); return;
        }

        Object[] opts = {"SI","NO"};
        int choice = JOptionPane.showOptionDialog(this, "¿La información es correcta?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            Empresa e = new Empresa();
            e.setNumeroEmpresa(parseIntRequired(txtNumEmpresa, "Número de empresa"));
            e.setRazonSocial(txtRazonSocial.getText().trim());
            e.setNombreFiscal(blankToNull(txtNombreFiscal));
            e.setRfc(blankToNullUpper(txtRFC));
            e.setCalleNumero(blankToNull(txtCalleNumero));
            e.setColonia(blankToNull(txtColonia));
            e.setCodigoPostal(blankToNull(txtCP));
            e.setCiudad(blankToNull(txtCiudad));
            e.setEstado(blankToNull(txtEstado));
            e.setWhatsapp(blankToNull(txtWhatsapp));
            e.setTelefono(blankToNull(txtTelefono));
            e.setInstagram(blankToNull(txtInstagram));
            e.setFacebook(blankToNull(txtFacebook));
            e.setTiktok(blankToNull(txtTikTok));
            e.setCorreo(blankToNull(txtCorreo));
            e.setPaginaWeb(blankToNull(txtPaginaWeb));
            e.setLogo(this.logoBytes);

            boolean ok = dao.guardar(e);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Información guardada/actualizada correctamente");
                setEditMode(false);
                cargar();
            } else {
                JOptionPane.showMessageDialog(this, "No se guardó ningún cambio", "Atención", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error SQL: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmarLimpiar() {
        if (!editMode) return;
        Object[] opciones = {"SI","NO"};
        int resp = JOptionPane.showOptionDialog(this,
                "¿Deseas borrar la información capturada hasta el momento?",
                "Confirmación",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[1]);
        if (resp == 0) limpiarCamposTexto();
    }

    private void limpiarCamposTexto() {
        // NO tocamos numero de empresa
        txtRazonSocial.setText("");
        txtNombreFiscal.setText("");
        txtRFC.setText("");
        txtCalleNumero.setText("");
        txtColonia.setText("");
        txtCP.setText("");
        txtCiudad.setText("");
        txtEstado.setText("");
        txtWhatsapp.setText("");
        txtTelefono.setText("");
        txtInstagram.setText("");
        txtFacebook.setText("");
        txtTikTok.setText("");
        txtCorreo.setText("");
        txtPaginaWeb.setText("");
        quitarLogo();
        txtRazonSocial.requestFocus();
    }

    // ======= Logo =======
    private void seleccionarLogo() {
        if (!editMode) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar logo (PNG/JPG/JPEG/WEBP)");
        fc.setFileFilter(new FileNameExtensionFilter("Imágenes", "png", "jpg", "jpeg", "webp"));

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                long size = Files.size(f.toPath());
                if (size > 2 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(this, "El archivo supera 2MB.",
                            "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                byte[] data = Files.readAllBytes(f.toPath());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) {
                    JOptionPane.showMessageDialog(this, "Archivo no reconocido como imagen.",
                            "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                this.logoBytes = data;
                setLogoPreview(img);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo cargar la imagen: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void quitarLogo() {
        this.logoBytes = null;
        lblLogoPreview.setIcon(null);
        lblLogoPreview.setText("Sin logo");
    }

    private void setLogoPreview(BufferedImage img) {
        Image scaled = img.getScaledInstance(LOGO_W, LOGO_H, Image.SCALE_SMOOTH);
        lblLogoPreview.setText(null);
        lblLogoPreview.setIcon(new ImageIcon(scaled));
    }

    private void setLogoPreview(byte[] data) {
        try {
            if (data == null) { quitarLogo(); return; }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) setLogoPreview(img); else quitarLogo();
        } catch (Exception e) { quitarLogo(); }
    }

    // ======= Validadores / helpers =======
    private String n(String s) { return s == null ? "" : s; }
    private String blankToNull(JTextField t) {
        String s = t.getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private String blankToNullUpper(JTextField t) {
        String s = blankToNull(t);
        return s == null ? null : s.toUpperCase();
    }
    private int parseIntRequired(JTextField t, String nombreCampo) {
        String s = t.getText();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(nombreCampo + " es obligatorio");
        return Integer.parseInt(s.trim());
    }
    private boolean isEmailValid(String email) {
        String regex = "^[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$";
        return Pattern.compile(regex).matcher(email.trim()).matches();
    }
    private boolean isRFCValid(String rfc) {
        String regex = "^[A-ZÑ&]{3}\\d{6}[A-Z0-9]{3}$|^[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3}$";
        return Pattern.compile(regex).matcher(rfc).matches();
    }

    // Filtros de documento
    static class DigitsOnlyFilter extends DocumentFilter {
        private final int maxLen;
        DigitsOnlyFilter(int maxLen) { this.maxLen = maxLen; }
        private boolean valid(String text) {
            if (text == null) return true;
            if (text.length() > maxLen) return false;
            for (int i=0;i<text.length();i++) {
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
    static class UppercaseFilter extends DocumentFilter {
        private final int maxLen;
        UppercaseFilter(int maxLen) { this.maxLen = maxLen; }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text != null) text = text.toUpperCase();
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text==null?"":text) + cur.substring(offset+length);
            if (maxLen <= 0 || next.length() <= maxLen) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    // --- main de prueba (opcional) ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Empresa (test panel)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(880, 720);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(new EmpresaPanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
