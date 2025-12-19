package Vista;

import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.cliente;
import Utilidades.TelefonosUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;

import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class EditarClientePanel extends JPanel {

    private boolean clienteCargado = false;
    private boolean cargandoDatos = false; // cuando estamos haciendo setText() al cargar
    private boolean dirty = false;         // hubo cambios del usuario
    private String lastTelefonoConsultado = null;

    private JButton btnGuardar;
    private JButton btnCargar;
    private JButton btnBuscarNombre;
    // Campos de texto
    private JTextField txtTel1, txtTel2, txtNombre, txtApPat, txtApMat;
    private JTextField txtBusto, txtCintura, txtCadera, txtEdad;
    private JTextField txtParenTel2;   // NUEVO

    // Fechas (máscara mexicana DD-MM-YYYY)
    private JFormattedTextField txtFechaEvento, txtPrueba1, txtPrueba2, txtEntrega;

    // Combos ENUM
    private JComboBox<String> cbComoSeEntero, cbLugar, cbStatus, cbSituacion;

    private final clienteDAO dao = new clienteDAO();
    private cliente clienteActual;     // el que se está editando

    // Formateadores de fecha
    private static final DateTimeFormatter MX  = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final DateTimeFormatter SQL = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    public EditarClientePanel() {
        setLayout(new BorderLayout());

        // 1) Crear campos
        txtTel1 = new JTextField();
        txtTel2 = new JTextField();
        txtParenTel2 = new JTextField(); // NUEVO

        // 2) Formato de teléfono
        TelefonosUI.instalar(txtTel1, 10);
        TelefonosUI.instalar(txtTel2, 10);

        txtTel1.setToolTipText("Teléfono 1 del cliente (PK).");
        txtTel2.setToolTipText("Teléfono 2 del cliente.");

        // Parentesco: solo letras/espacios
        ((AbstractDocument) txtParenTel2.getDocument())
                .setDocumentFilter(new LettersOnlyFilter(40)); // NUEVO
        txtParenTel2.setToolTipText("Parentesco del contacto de Teléfono 2 (ej. mamá, hermana, amiga).");

        // Panel y layout
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int y = 0;

// ===== Teléfono 1 + Teléfono 2 =====
btnCargar = new JButton("Cargar");
btnCargar.addActionListener(_e -> cargarCliente());

// botón NUEVO: buscar cliente por nombre
btnBuscarNombre = new JButton("Buscar por nombre…");
btnBuscarNombre.addActionListener(_e -> buscarClientePorNombre());

c.gridx = 0; c.gridy = y; c.gridwidth = 1;
p.add(new JLabel("Teléfono 1*:"), c);

        // Teléfono 1
        c.gridx = 1;
        p.add(txtTel1, c);

        // Panel con Cargar + Buscar por nombre (misma fila que el teléfono)
        JPanel pnlBotonesTel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pnlBotonesTel.add(btnCargar);
        pnlBotonesTel.add(btnBuscarNombre);

        c.gridx = 2;
        p.add(pnlBotonesTel, c);

        // Teléfono 2 a la derecha
        c.gridx = 3;
        p.add(txtTel2, c);
        y++;


        // Fila separada: Parentesco tel. 2 (lado derecho)  // NUEVO
        c.gridx = 2; c.gridy = y; c.gridwidth = 1;
        p.add(new JLabel("Parentesco tel. 2:"), c);
        c.gridx = 3;
        p.add(txtParenTel2, c);
        y++;

        // ===== Nombre / Apellido paterno
        txtNombre = new JTextField();
        ((AbstractDocument) txtNombre.getDocument()).setDocumentFilter(new LettersOnlyFilter(60));

        txtApPat = new JTextField();
        ((AbstractDocument) txtApPat.getDocument()).setDocumentFilter(new LettersOnlyFilter(60));

        addRow(p, c, y++, new JLabel("Nombre*:"), txtNombre,
                new JLabel("Apellido paterno:"), txtApPat);

        // ===== Apellido materno / Edad
        txtApMat = new JTextField();
        ((AbstractDocument) txtApMat.getDocument()).setDocumentFilter(new LettersOnlyFilter(60));

        txtEdad = new JTextField();
        ((AbstractDocument) txtEdad.getDocument()).setDocumentFilter(new DigitsOnlyFilter(3));

        addRow(p, c, y++, new JLabel("Apellido materno:"), txtApMat,
                new JLabel("Edad:"), txtEdad);

        // ===== ¿Cómo se enteró? / Lugar del evento
        cbComoSeEntero = new JComboBox<>(new String[]{"", "UBICACION", "RECOMENDACION", "GOOGLE MAPS", "TIKTOK"});
        cbLugar        = new JComboBox<>(new String[]{"", "HACIENDA", "JARDIN", "SALON", "PLAYA"});
        addRow(p, c, y++, new JLabel("¿Cómo se enteró?"), cbComoSeEntero,
                new JLabel("Lugar del evento:"), cbLugar);

        // ===== Fechas
        txtFechaEvento = createDateFieldMX();
        addRowFull(p, c, y++, new JLabel("Fecha del evento (DD-MM-YYYY):"), txtFechaEvento);

        txtPrueba1 = createDateFieldMX();
        addRowFull(p, c, y++, new JLabel("Fecha de prueba 1 (DD-MM-YYYY):"), txtPrueba1);

        txtPrueba2 = createDateFieldMX();
        addRowFull(p, c, y++, new JLabel("Fecha de prueba 2 (DD-MM-YYYY):"), txtPrueba2);

        txtEntrega = createDateFieldMX();
        addRowFull(p, c, y++, new JLabel("Fecha de entrega (DD-MM-YYYY):"), txtEntrega);

        // ===== Medidas
        txtBusto   = new JTextField();
        txtCintura = new JTextField();
        txtCadera  = new JTextField();
        ((AbstractDocument) txtBusto.getDocument()).setDocumentFilter(new DecimalFilter(6));
        ((AbstractDocument) txtCintura.getDocument()).setDocumentFilter(new DecimalFilter(6));
        ((AbstractDocument) txtCadera.getDocument()).setDocumentFilter(new DecimalFilter(6));

        addRow(p, c, y++, new JLabel("Busto (cm):"), txtBusto,
                new JLabel("Cintura (cm):"), txtCintura);
        addRowFull(p, c, y++, new JLabel("Cadera (cm):"), txtCadera);

        // ===== Status / Situación
        cbStatus   = new JComboBox<>(new String[]{"A", "C"});
        cbSituacion = new JComboBox<>(new String[]{"NORMAL", "CANCELA DEFINITIVO", "POSPONE BODA INDEFINIDO"});
        addRow(p, c, y++, new JLabel("Status:"), cbStatus,
                new JLabel("Situación del evento:"), cbSituacion);

        add(p, BorderLayout.CENTER);

        // ===== Botones
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnGuardar = new JButton("Guardar cambios");
        btnGuardar.setEnabled(false);
        JButton btnLimpiar = new JButton("Limpiar");

        actions.add(btnGuardar);
        actions.add(btnLimpiar);

        add(actions, BorderLayout.SOUTH);


        btnGuardar.addActionListener(_e -> guardarCambios());
        btnLimpiar.addActionListener(_e -> limpiarTodo());

        setCamposEdicionHabilitados(false);
        btnGuardar.setEnabled(false);

        registrarListenersDeCambio();
        cargarClienteAutoHooks();
    }

    // ---------------------- Layout helpers
    private void addRow(JPanel p, GridBagConstraints c, int y,
                        JComponent l1, JComponent f1, JComponent l2, JComponent f2) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        p.add(l1, c);
        c.gridx = 1;
        p.add(f1, c);
        c.gridx = 2;
        p.add(l2, c);
        c.gridx = 3;
        p.add(f2, c);
    }

    private void addRowFull(JPanel p, GridBagConstraints c, int y,
                            JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        p.add(l, c);
        c.gridx = 1; c.gridwidth = 3;
        p.add(f, c);
        c.gridwidth = 1;
    }

    // ---------------------- Cargar cliente
    private void cargarCliente() { cargarCliente(true); }

    private void cargarCliente(boolean showMessages) {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTel1.getText());

        if (tel.isEmpty()) {
            if (showMessages) {
                JOptionPane.showMessageDialog(this,
                        "Captura el teléfono 1 del cliente.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
            }
            limpiarInfoCliente();
            lastTelefonoConsultado = null;
            return;
        }

        if (tel.equals(lastTelefonoConsultado)) return;

        try {
            cliente cli = dao.buscarClientePorTelefono1(tel);
            lastTelefonoConsultado = tel;

            if (cli == null) {
                limpiarInfoCliente();
                if (showMessages) {
                    JOptionPane.showMessageDialog(this,
                            "No se encontró un cliente con ese teléfono.",
                            "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }

            cargandoDatos = true;
            clienteActual = cli;
            clienteCargado = true;

            // Tel1 es PK
            txtTel1.setEditable(false);

            txtTel2.setText(n(cli.getTelefono2()));
            txtParenTel2.setText(n(cli.getParentescoTel2())); // NUEVO
            txtNombre.setText(n(cli.getNombre()));
            txtApPat.setText(n(cli.getApellidoPaterno()));
            txtApMat.setText(n(cli.getApellidoMaterno()));
            txtEdad.setText(cli.getEdad() == null ? "" : String.valueOf(cli.getEdad()));

            setCombo(cbComoSeEntero, cli.getComoSeEntero());
            setCombo(cbLugar, cli.getLugarEvento());

            setDateFromSql(txtFechaEvento, cli.getFechaEvento());
            setDateFromSql(txtPrueba1,    cli.getFechaPrueba1());
            setDateFromSql(txtPrueba2,    cli.getFechaPrueba2());
            setDateFromSql(txtEntrega,    cli.getFechaEntrega());

            txtBusto.setText(cli.getBusto()   == null ? "" : String.valueOf(cli.getBusto()));
            txtCintura.setText(cli.getCintura()== null ? "" : String.valueOf(cli.getCintura()));
            txtCadera.setText(cli.getCadera() == null ? "" : String.valueOf(cli.getCadera()));

            setCombo(cbStatus,    cli.getStatus());
            setCombo(cbSituacion, cli.getSituacionEvento());

            setCamposEdicionHabilitados(true);

            dirty = false;
            btnGuardar.setEnabled(false);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error SQL al cargar cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            cargandoDatos = false;
        }
    }

    private void setCombo(JComboBox<String> cb, String value) {
        if (value == null || value.isBlank()) cb.setSelectedIndex(0);
        else cb.setSelectedItem(value);
    }

    private void setDateFromSql(JFormattedTextField field, String sqlDate) {
        if (sqlDate == null || sqlDate.isBlank()) {
            field.setValue(null);
            return;
        }
        LocalDate d = LocalDate.parse(sqlDate, SQL);
        field.setText(d.format(MX));
    }

    private void setCamposEdicionHabilitados(boolean enable) {
        setEditableField(txtTel2, enable);
        setEditableField(txtParenTel2, enable); // NUEVO
        setEditableField(txtNombre, enable);
        setEditableField(txtApPat, enable);
        setEditableField(txtApMat, enable);
        setEditableField(txtEdad, enable);
        setEditableField(txtFechaEvento, enable);
        setEditableField(txtPrueba1, enable);
        setEditableField(txtPrueba2, enable);
        setEditableField(txtEntrega, enable);
        setEditableField(txtBusto, enable);
        setEditableField(txtCintura, enable);
        setEditableField(txtCadera, enable);

        cbComoSeEntero.setEnabled(enable);
        cbLugar.setEnabled(enable);
        cbStatus.setEnabled(enable);
        cbSituacion.setEnabled(enable);
    }

    private void setEditableField(JTextComponent t, boolean editable) {
        t.setEditable(editable);
        t.setOpaque(true);
        Color bgEditable  = UIManager.getColor("TextField.background");
        Color bgReadOnly  = UIManager.getColor("TextField.inactiveBackground");
        if (bgEditable == null) bgEditable = Color.WHITE;
        if (bgReadOnly == null) bgReadOnly = new Color(235, 235, 235);
        t.setBackground(editable ? bgEditable : bgReadOnly);
    }

    private String n(String s) { return s == null ? "" : s; }

    // ---------------------- Guardar cambios
    private void guardarCambios() {
        if (clienteActual == null) {
            JOptionPane.showMessageDialog(this,
                    "Primero carga un cliente por teléfono.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (txtNombre.getText().isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "El nombre es obligatorio",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            txtNombre.requestFocus();
            return;
        }

        LocalDate ev  = parseMX(txtFechaEvento.getText());
        LocalDate f1  = parseMX(txtPrueba1.getText());
        LocalDate f2  = parseMX(txtPrueba2.getText());
        LocalDate ent = parseMX(txtEntrega.getText());

        String error = validarFechas(ev, f1, f2, ent);
        if (error != null) {
            JOptionPane.showMessageDialog(this,
                    error,
                    "Fechas inválidas", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object[] opts = {"SI", "NO"};

        int ch1 = JOptionPane.showOptionDialog(this,
                "¿La información capturada es correcta?",
                "Confirmación",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);
        if (ch1 != JOptionPane.YES_OPTION) return;

        int ch2 = JOptionPane.showOptionDialog(this,
                "Esta acción actualizará los datos del cliente en el sistema.\n" +
                "¿Deseas continuar?",
                "Confirmar actualización",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, opts, opts[1]);
        if (ch2 != JOptionPane.YES_OPTION) return;

        try {
            cliente cli = new cliente();
            cli.setTelefono1(clienteActual.getTelefono1());      // PK
            cli.setTelefono2(phoneOrNull(txtTel2));
            cli.setParentescoTel2(blankToNull(txtParenTel2));    // NUEVO
            cli.setNombre(txtNombre.getText().trim());
            cli.setApellidoPaterno(blankToNull(txtApPat));
            cli.setApellidoMaterno(blankToNull(txtApMat));
            cli.setEdad(parseNullableInt(txtEdad));

            cli.setComoSeEntero(comboVal(cbComoSeEntero));
            cli.setLugarEvento(comboVal(cbLugar));

            cli.setFechaEvento(maskMexToSql(txtFechaEvento));
            cli.setFechaPrueba1(maskMexToSql(txtPrueba1));
            cli.setFechaPrueba2(maskMexToSql(txtPrueba2));
            cli.setFechaEntrega(maskMexToSql(txtEntrega));

            cli.setBusto(parseNullableDouble(txtBusto));
            cli.setCintura(parseNullableDouble(txtCintura));
            cli.setCadera(parseNullableDouble(txtCadera));

            cli.setStatus((String) cbStatus.getSelectedItem());
            cli.setSituacionEvento((String) cbSituacion.getSelectedItem());

            boolean ok = dao.actualizar(cli);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Datos del cliente actualizados correctamente.",
                        "Listo", JOptionPane.INFORMATION_MESSAGE);

                clienteActual = cli;
                dirty = false;

                limpiarTodo();
            } else {
                JOptionPane.showMessageDialog(this,
                        "No se actualizó ninguna fila. Verifica el teléfono.",
                        "Atención", JOptionPane.WARNING_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error SQL al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error de validación: " + ex.getMessage(),
                    "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error inesperado: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarTodo() {
        clienteActual      = null;
        clienteCargado     = false;
        cargandoDatos      = false;
        dirty              = false;
        lastTelefonoConsultado = null;

        txtTel1.setText("");
        txtTel2.setText("");
        txtParenTel2.setText("");  // NUEVO
        txtNombre.setText("");
        txtApPat.setText("");
        txtApMat.setText("");
        txtEdad.setText("");

        txtFechaEvento.setValue(null);
        txtPrueba1.setValue(null);
        txtPrueba2.setValue(null);
        txtEntrega.setValue(null);

        txtBusto.setText("");
        txtCintura.setText("");
        txtCadera.setText("");

        cbComoSeEntero.setSelectedIndex(0);
        cbLugar.setSelectedIndex(0);
        cbStatus.setSelectedItem("A");
        cbSituacion.setSelectedItem("NORMAL");

        txtTel1.setEditable(true);
        setCamposEdicionHabilitados(false);
        btnGuardar.setEnabled(false);
        txtTel1.requestFocus();
    }

    // ---------------------- Utilidades varias
    private String blankToNull(JTextField t) {
        String s = t.getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private String phoneOrNull(JTextField t) {
    String dig = TelefonosUI.soloDigitos(
            t.getText() == null ? "" : t.getText()
    );
    return dig.isBlank() ? null : dig;
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

    private static final DateTimeFormatter MX_D = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private static LocalDate parseMX(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.contains("_")) return null;
        return LocalDate.parse(s, MX_D);
    }

    private static String validarFechas(LocalDate evento, LocalDate p1, LocalDate p2, LocalDate entrega) {
        LocalDate hoy = LocalDate.now();
        if (evento == null) return "La fecha de evento es obligatoria.";

        if (!evento.isAfter(hoy))
            return "La fecha de evento debe ser mayor a la fecha actual.";

        if (p1 != null) {
            if (!p1.isAfter(hoy))
                return "La fecha de prueba 1 debe ser mayor a la fecha actual.";
            if (!p1.isBefore(evento))
                return "La fecha de prueba 1 debe ser menor a la fecha de evento.";
        }

        if (p2 != null) {
            if (p1 == null)
                return "Si capturas la fecha de prueba 2, también es necesario capturar la prueba 1.";
            if (!p2.isAfter(p1))
                return "La fecha de prueba 2 debe ser mayor a la fecha de prueba 1.";
            if (!p2.isBefore(evento))
                return "La fecha de prueba 2 debe ser menor a la fecha de evento.";
        }

        if (entrega != null) {
            if (!entrega.isAfter(p2))
                return "La fecha de entrega debe ser mayor a la fecha de prueba 2.";
            if (!entrega.isBefore(evento))
                return "La fecha de entrega debe ser menor a la fecha de evento.";
        }

        return null;
    }

    private void registrarListenersDeCambio() {
        addDirtyListener(txtTel2);
        addDirtyListener(txtParenTel2);   // NUEVO
        addDirtyListener(txtNombre);
        addDirtyListener(txtApPat);
        addDirtyListener(txtApMat);
        addDirtyListener(txtEdad);

        addDirtyListener(txtFechaEvento);
        addDirtyListener(txtPrueba1);
        addDirtyListener(txtPrueba2);
        addDirtyListener(txtEntrega);

        addDirtyListener(txtBusto);
        addDirtyListener(txtCintura);
        addDirtyListener(txtCadera);

        addDirtyListener(cbComoSeEntero);
        addDirtyListener(cbLugar);
        addDirtyListener(cbStatus);
        addDirtyListener(cbSituacion);
    }

    private void addDirtyListener(JTextComponent tc) {
        tc.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { onFieldChanged(); }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
    }

    private void addDirtyListener(JComboBox<?> cb) {
        cb.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                onFieldChanged();
            }
        });
    }

    private void onFieldChanged() {
        if (cargandoDatos) return;
        if (!clienteCargado || clienteActual == null) return;
        dirty = true;
        btnGuardar.setEnabled(true);
    }

    private JFormattedTextField createDateFieldMX() {
        try {
            MaskFormatter mf = new MaskFormatter("##-##-####");
            mf.setPlaceholderCharacter('_');
            JFormattedTextField f = new JFormattedTextFieldFixed(mf);
            f.setColumns(10);
            f.setToolTipText("Formato: DD-MM-YYYY (ej. 15-09-2025)");
            return f;
        } catch (Exception e) {
            return new JFormattedTextField();
        }
    }

    private void cargarClienteAutoHooks() {
        txtTel1.addActionListener(_e -> cargarCliente());

        ((AbstractDocument) txtTel1.getDocument()).addDocumentListener((SimpleDocListener) () -> {
            String t = txtTel1.getText().trim();
            if (t.length() >= 7) {
                if (lastTelefonoConsultado == null || !lastTelefonoConsultado.equals(t)) {
                    cargarCliente(false);
                }
            } else {
                limpiarInfoCliente();
                lastTelefonoConsultado = null;
            }
        });

        txtTel1.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                String t = txtTel1.getText().trim();
                if (t.isEmpty()) return;
                if (!isShowing()) return;
                cargarCliente();
            }
        });
    }

    private void limpiarInfoCliente() {
        clienteActual      = null;
        clienteCargado     = false;
        cargandoDatos      = false;
        dirty              = false;

        txtTel2.setText("");
        txtParenTel2.setText(""); // NUEVO
        txtNombre.setText("");
        txtApPat.setText("");
        txtApMat.setText("");
        txtEdad.setText("");

        txtFechaEvento.setValue(null);
        txtPrueba1.setValue(null);
        txtPrueba2.setValue(null);
        txtEntrega.setValue(null);

        txtBusto.setText("");
        txtCintura.setText("");
        txtCadera.setText("");

        cbComoSeEntero.setSelectedIndex(0);
        cbLugar.setSelectedIndex(0);
        cbStatus.setSelectedItem("A");
        cbSituacion.setSelectedItem("NORMAL");

        setCamposEdicionHabilitados(false);
        btnGuardar.setEnabled(false);
        txtTel1.setEditable(true);
    }

    // DocumentFilters
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

    static class JFormattedTextFieldFixed extends JFormattedTextField {
        public JFormattedTextFieldFixed(AbstractFormatter formatter) { super(formatter); }
        @Override public void processKeyEvent(java.awt.event.KeyEvent e) {
            super.processKeyEvent(e);
            this.setCaretPosition(Math.min(getCaretPosition(), getText().length()));
        }
    }

    @FunctionalInterface
    interface SimpleDocListener extends DocumentListener {
        void on();
        @Override default void insertUpdate(DocumentEvent e) { on(); }
        @Override default void removeUpdate(DocumentEvent e) { on(); }
        @Override default void changedUpdate(DocumentEvent e) { on(); }
    }
    // ================== DIÁLOGO DE BÚSQUEDA POR NOMBRE ==================
private static class DialogBusquedaCliente extends JDialog {

    private final clienteDAO dao;
    private JTextField txtFiltro;
    private JTable tabla;
    private DefaultTableModel modelo;
    private java.util.List<ClienteResumen> resultados = new java.util.ArrayList<>();
    private ClienteResumen seleccionado;

    public DialogBusquedaCliente(Window owner, clienteDAO dao) {
        super(owner, "Buscar cliente por nombre o apellido", ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        construirUI();
    }

    private void construirUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtro
        JPanel pnlFiltro = new JPanel(new BorderLayout(5, 0));
        pnlFiltro.add(new JLabel("Nombre o apellido:"), BorderLayout.WEST);
        txtFiltro = new JTextField();
        pnlFiltro.add(txtFiltro, BorderLayout.CENTER);

        JButton btnBuscar = new JButton("Buscar");
        pnlFiltro.add(btnBuscar, BorderLayout.EAST);

        main.add(pnlFiltro, BorderLayout.NORTH);

        // Tabla
        modelo = new DefaultTableModel(
                new Object[]{
                        "Nombre completo",
                        "Teléfono",
                        "Teléfono 2",
                        "Evento",
                        "Prueba 1",
                        "Prueba 2",
                        "Entrega"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.setAutoCreateRowSorter(true);
        main.add(new JScrollPane(tabla), BorderLayout.CENTER);

        // Botones abajo
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSeleccionar = new JButton("Seleccionar");
        JButton btnCerrar = new JButton("Cancelar");
        pnlBotones.add(btnCerrar);
        pnlBotones.add(btnSeleccionar);

        main.add(pnlBotones, BorderLayout.SOUTH);

        setContentPane(main);
        setSize(800, 400);
        setLocationRelativeTo(getOwner());

        // Eventos
        btnBuscar.addActionListener(_e -> buscar());
        btnSeleccionar.addActionListener(_e -> seleccionarActual());
        btnCerrar.addActionListener(_e -> dispose());

        // Doble clic
        tabla.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    seleccionarActual();
                }
            }
        });

        // Enter en el campo de filtro = buscar
        txtFiltro.addActionListener(_e -> buscar());
    }

    private void buscar() {
        String filtro = txtFiltro.getText().trim();
        if (filtro.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe al menos una parte del nombre o apellido.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);
            modelo.setRowCount(0);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-uuuu");

            for (ClienteResumen cr : resultados) {
                modelo.addRow(new Object[]{
                        cr.getNombreCompleto(),
                        cr.getTelefono1(),
                        cr.getTelefono2(),
                        cr.getFechaEvento()  == null ? "" : cr.getFechaEvento().format(fmt),
                        cr.getFechaPrueba1() == null ? "" : cr.getFechaPrueba1().format(fmt),
                        cr.getFechaPrueba2() == null ? "" : cr.getFechaPrueba2().format(fmt),
                        cr.getFechaEntrega() == null ? "" : cr.getFechaEntrega().format(fmt)
                });
            }

            if (resultados.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No se encontraron clientes con ese nombre o apellido.",
                        "Buscar cliente", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar clientes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seleccionarActual() {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un cliente de la tabla.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tabla.convertRowIndexToModel(row);
        seleccionado = resultados.get(modelRow);
        dispose();
    }

    public ClienteResumen getSeleccionado() {
        return seleccionado;
    }
}
// ================== BÚSQUEDA POR NOMBRE ==================
private void buscarClientePorNombre() {
    Window owner = SwingUtilities.getWindowAncestor(this);
    DialogBusquedaCliente dlg = new DialogBusquedaCliente(owner, dao);
    dlg.setLocationRelativeTo(this);
    dlg.setVisible(true);

    ClienteResumen cr = dlg.getSeleccionado();
    if (cr == null) {
        return; // usuario canceló o no seleccionó nada
    }

    String tel = TelefonosUI.soloDigitos(cr.getTelefono1());
    if (tel == null || tel.isBlank()) {
        JOptionPane.showMessageDialog(this,
                "El cliente seleccionado no tiene Teléfono 1 registrado.",
                "Aviso", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    // Forzamos recarga aunque sea el mismo que antes
    lastTelefonoConsultado = null;

    txtTel1.setText(tel);
    cargarCliente();   // ya tienes toda la lógica de carga aquí
}

}
