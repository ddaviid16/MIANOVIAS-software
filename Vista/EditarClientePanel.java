package Vista;

import Controlador.clienteDAO;
import Modelo.cliente;
import Utilidades.TelefonosUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;


import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Panel para editar la información de un cliente existente.
 *
 * Flujo:
 * 1) Capturas teléfono 1 y presionas "Cargar".
 * 2) Se llenan todos los campos con los datos actuales del cliente.
 * 3) Editas lo necesario.
 * 4) Al guardar pide doble confirmación y ejecuta UPDATE sobre Clientes.
 */
public class EditarClientePanel extends JPanel {

    private boolean clienteCargado = false;
    
    private boolean cargandoDatos = false; // cuando estamos haciendo setText() al cargar
    private boolean dirty = false;         // hubo cambios del usuario
    
    private String lastTelefonoConsultado = null; // como en VentaContadoPanel

    private JButton btnGuardar;
    private JButton btnCargar;
    // Campos de texto
    private JTextField txtTel1, txtTel2, txtNombre, txtApPat, txtApMat;
    private JTextField txtBusto, txtCintura, txtCadera, txtEdad;

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

    // 1) PRIMERO crear los campos
    txtTel1 = new JTextField();
    txtTel2 = new JTextField();

    // 2) Aplicar formato de teléfono (guiones, solo dígitos, etc.)
    TelefonosUI.instalar(txtTel1, 10);  // máximo 10 dígitos
    TelefonosUI.instalar(txtTel2, 10);

    // 3) Opcional: tooltips
    txtTel1.setToolTipText("Teléfono 1 del cliente (PK).");
    txtTel2.setToolTipText("Teléfono 2 del cliente.");

    // 4) Ya después armas el panel, gridbag, etc.
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(6, 6, 6, 6);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1.0;

    int y = 0;

    // ===== Teléfono 1 + botón Cargar / Teléfono 2
    btnCargar = new JButton("Cargar");
    btnCargar.addActionListener(_e -> cargarCliente());

    // fila: Teléfono1 | campo | Teléfono2 | botón Cargar
    c.gridx = 0; c.gridy = y; c.gridwidth = 1;
    p.add(new JLabel("Teléfono 1*:"), c);
    c.gridx = 1;
    p.add(txtTel1, c);
    c.gridx = 2;
    p.add(new JLabel("Teléfono 2:"), c);
    c.gridx = 3;
    p.add(txtTel2, c);
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

        // ===== Fechas con formato mexicano (DD-MM-YYYY)
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
        btnGuardar.setEnabled(false); // o como se llame tu botón

        JButton btnLimpiar = new JButton("Limpiar");

        actions.add(btnCargar);   // también aquí, para tenerlo a la vista
        actions.add(btnGuardar);
        actions.add(btnLimpiar);

        add(actions, BorderLayout.SOUTH);

        btnGuardar.addActionListener(_e -> guardarCambios());
        btnLimpiar.addActionListener(_e -> limpiarTodo());

        // Estado inicial: solo puedo escribir teléfono y darle a Cargar
        setCamposEdicionHabilitados(false);
        btnGuardar.setEnabled(false);

        registrarListenersDeCambio();
            // Hooks automáticos tipo VentaContadoPanel
        cargarClienteAutoHooks();

        // después de construir, colocamos el botón Cargar al lado correcto
        // (ya está referenciado arriba, no hay extra magia)
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
    // ---------------------- Cargar cliente

    private void cargarCliente() {
        cargarCliente(true);  // por defecto, con mensajes de validación
    }

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

        // Si ya consultamos este teléfono y no ha cambiado, no vuelvas a pegarle a la BD
        if (tel.equals(lastTelefonoConsultado)) {
            return;
        }

        try {
            cliente cli = dao.buscarClientePorTelefono1(tel);
            lastTelefonoConsultado = tel;

            if (cli == null) {
                // No se encontró: limpiamos campos y deshabilitamos edición
                limpiarInfoCliente();
                if (showMessages) {
                    JOptionPane.showMessageDialog(this,
                            "No se encontró un cliente con ese teléfono.",
                            "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }

            // Estamos llenando datos desde BD -> no disparar "dirty"
            // Estamos llenando datos desde BD -> no disparar "dirty"
cargandoDatos = true;
clienteActual = cli;
clienteCargado = true;

// Tel1 es la PK -> no se edita
// txtTel1.setText(cli.getTelefono1());   // <-- QUITA ESTA LÍNEA
txtTel1.setEditable(false);

txtTel2.setText(n(cli.getTelefono2()));
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

// Recién cargado: todavía no hay cambios del usuario
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
        if (value == null || value.isBlank()) {
            cb.setSelectedIndex(0);
            return;
        }
        cb.setSelectedItem(value);
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

    // ---------------------- Guardar cambios (doble confirmación)
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

        // Validar fechas
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

        // Confirmación 1
        int ch1 = JOptionPane.showOptionDialog(this,
                "¿La información capturada es correcta?",
                "Confirmación",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);
        if (ch1 != JOptionPane.YES_OPTION) return;

        // Confirmación 2 (para evitar manoseos accidentales)
        int ch2 = JOptionPane.showOptionDialog(this,
                "Esta acción actualizará los datos del cliente en el sistema.\n" +
                "¿Deseas continuar?",
                "Confirmar actualización",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, opts, opts[1]);
        if (ch2 != JOptionPane.YES_OPTION) return;

        try {
            cliente cli = new cliente();
            // PK NO se cambia
            cli.setTelefono1(clienteActual.getTelefono1());
            cli.setTelefono2(blankToNull(txtTel2));
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

                // Dejamos consistente el estado interno
                clienteActual = cli;
                dirty = false;

                // Pedido tuyo: después de guardar, limpiar toda la info mostrada
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

    /** Convierte campo con máscara DD-MM-YYYY a "YYYY-MM-DD" (para MySQL). */
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

    // ===== Helpers de fechas (dd-MM-uuuu) =====
    private static final DateTimeFormatter MX_D = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    /** Parsea "dd-MM-uuuu". Devuelve null si está vacío o con guiones bajos. */
    private static LocalDate parseMX(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.contains("_")) return null;
        return LocalDate.parse(s, MX_D);
    }

    /** Devuelve null si todo OK; si hay error, devuelve el mensaje. */
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
                return "Si capturas la fecha de prueba 2, también debes capturar la prueba 1.";
            if (!p2.isAfter(p1))
                return "La fecha de prueba 2 debe ser mayor a la fecha de prueba 1.";
            if (!p2.isBefore(evento))
                return "La fecha de prueba 2 debe ser menor a la fecha de evento.";
        }

        if (entrega != null) {
            if (p2 == null)
                return "Si capturas la fecha de entrega, también debes capturar la prueba 2.";
            if (!entrega.isAfter(p2))
                return "La fecha de entrega debe ser mayor a la fecha de prueba 2.";
            if (!entrega.isBefore(evento))
                return "La fecha de entrega debe ser menor a la fecha de evento.";
        }

        return null; // OK
    }
    private void registrarListenersDeCambio() {
    // Todos los campos editables (menos tel1, que es PK)
    addDirtyListener(txtTel2);
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
    // Si estamos llenando datos desde la BD, ignorar
    if (cargandoDatos) return;
    // Si no hay cliente cargado, no tiene sentido
    if (!clienteCargado || clienteActual == null) return;

    dirty = true;
    btnGuardar.setEnabled(true);
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

        // ================== AUTO-CARGA COMO EN VentaContadoPanel ==================

private void cargarClienteAutoHooks() {
    // Enter en el teléfono -> intentar cargar
    txtTel1.addActionListener(_e -> cargarCliente());

    // Al ir escribiendo, cuando tenga >= 7 dígitos intenta cargar
    ((AbstractDocument) txtTel1.getDocument()).addDocumentListener((SimpleDocListener) () -> {
        String t = txtTel1.getText().trim();
        if (t.length() >= 7) {
            if (lastTelefonoConsultado == null || !lastTelefonoConsultado.equals(t)) {
                cargarCliente(false); // sin mensaje de "no encontrado" para no molestar mientras escribe
            }
        } else {
            // Si borra o está muy corto, limpiamos la info del cliente pero NO el teléfono
            limpiarInfoCliente();
            lastTelefonoConsultado = null;
        }
    });

    // Al salir del campo, SOLO valida si hay algo escrito
    txtTel1.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override public void focusLost(java.awt.event.FocusEvent e) {
            String t = txtTel1.getText().trim();
            if (t.isEmpty()) {
                // si quieres ser más paranoico:
                // lastTelefonoConsultado = null;
                return;  // nada que validar, no molestamos al usuario
            }
            if (!isShowing()) return;    // opcional, por si cambias de panel
            cargarCliente();             // versión con mensajes
        }
    });
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
    /** Limpia los datos del cliente pero NO borra el teléfono, ni el foco. */
    private void limpiarInfoCliente() {
        clienteActual      = null;
        clienteCargado     = false;
        cargandoDatos      = false;
        dirty              = false;

        txtTel2.setText("");
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

        // Tel1 se queda editable mientras no haya cliente cargado
        txtTel1.setEditable(true);
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
        @FunctionalInterface
    interface SimpleDocListener extends DocumentListener {
        void on();
        @Override default void insertUpdate(DocumentEvent e) { on(); }
        @Override default void removeUpdate(DocumentEvent e) { on(); }
        @Override default void changedUpdate(DocumentEvent e) { on(); }
    }

}
