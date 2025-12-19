package Vista;

import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Utilidades.TelefonosUI;
import Controlador.AsesorDAO;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Panel: Registro de Citas de cliente.
 *
 * - Buscar cliente por teléfono (usa Clientes.telefono1).
 * - Muestra nombre completo y fecha de evento.
 * - Captura fechas/horas de 1ª y 2ª cita, pruebas y entrega,
 *   además de asesora/modista (por ahora texto libre).
 *
 * Toda la info se guarda en la tabla Clientes.
 */
public class RegistroCitasPanel extends JPanel {

    private final clienteDAO cliDao = new clienteDAO();

    private static final DateTimeFormatter MX_FECHA = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final DateTimeFormatter MX_HORA  = DateTimeFormatter.ofPattern("HH:mm");

    // --- datos cliente ---
    private JTextField txtTel;
    private JTextField txtNombre;
    private JTextField txtFechaEvento;

    private ClienteResumen clienteActual;

    // --- citas asesoría ---
    private JFormattedTextField txtFechaCita1;
    private JFormattedTextField txtHoraCita1;
    private JComboBox<String> cbAsesora1;

    private JFormattedTextField txtFechaCita2;
    private JFormattedTextField txtHoraCita2;
    private JComboBox<String> cbAsesora2;

    // --- pruebas y entrega ---
    private JFormattedTextField txtFechaPrueba1;
    private JFormattedTextField txtHoraPrueba1;
    private JComboBox<String> cbModista1;

    private JFormattedTextField txtFechaPrueba2;
    private JFormattedTextField txtHoraPrueba2;
    private JComboBox<String> cbModista2;

    private JFormattedTextField txtFechaEntrega;
    private JFormattedTextField txtHoraEntrega;
    private JComboBox<String> cbAsesoraEntrega;

    private JButton btnBuscar;
    private JButton btnBuscarNombre;
    private JButton btnGuardar;
    private JButton btnLimpiar;

public RegistroCitasPanel() {
        setLayout(new BorderLayout());

        // 1) Crear el campo de teléfono
        txtTel = new JTextField();

        // 2) Formato de teléfono con guiones (solo dígitos internamente)
        TelefonosUI.instalar(txtTel, 10);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int y = 0;

        // ================= Cliente =================

// ================= Cliente =================

btnBuscar = new JButton("Buscar");
btnBuscar.addActionListener(_e -> buscarCliente());

// nuevo botón: Buscar cliente por nombre
btnBuscarNombre = new JButton("Buscar cliente por nombre…");
btnBuscarNombre.addActionListener(_e -> buscarClientePorNombre());

txtNombre = new JTextField();
txtNombre.setEditable(false);
txtNombre.setBackground(UIManager.getColor("TextField.inactiveBackground"));

txtFechaEvento = new JTextField();
txtFechaEvento.setEditable(false);
txtFechaEvento.setBackground(UIManager.getColor("TextField.inactiveBackground"));

/*  Fila 1: Teléfono + botones (Buscar / Buscar por nombre)
    columnas:
      0: etiqueta "Teléfono:"
      1: txtTel
      2-3: panel con los dos botones                         */

c.gridx = 0; c.gridy = y; c.gridwidth = 1;
p.add(new JLabel("Teléfono:"), c);

c.gridx = 1;
p.add(txtTel, c);

// Panel con los dos botones alineados a la izquierda
JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
panelBotones.add(btnBuscar);
panelBotones.add(btnBuscarNombre);

c.gridx = 2;
c.gridwidth = 2;        // ocupa las columnas 2 y 3
p.add(panelBotones, c);
c.gridwidth = 1;

y++;

// Fila 2: nombre y fecha de evento, igual que antes
addRow(p, c, y++, new JLabel("Nombre cliente:"), txtNombre,
       new JLabel("Fecha de evento:"), txtFechaEvento);


        // ================= Citas =================
        addSeparator(p, c, y++, "Citas en tienda");

        txtFechaCita1 = createDateFieldMX();
        txtHoraCita1  = createTimeField();
        cbAsesora1    = new JComboBox<>();

        addRow(p, c, y++,
               new JLabel("Fecha 1ª cita:"), txtFechaCita1,
               new JLabel("Hora 1ª cita:"), txtHoraCita1);
        addRowFull(p, c, y++,
               new JLabel("Asesora 1ª cita:"), cbAsesora1);

        txtFechaCita2 = createDateFieldMX();
        txtHoraCita2  = createTimeField();
        cbAsesora2    = new JComboBox<>();

        addRow(p, c, y++,
               new JLabel("Fecha 2ª cita:"), txtFechaCita2,
               new JLabel("Hora 2ª cita:"), txtHoraCita2);
        addRowFull(p, c, y++,
               new JLabel("Asesora 2ª cita:"), cbAsesora2);

        // ================= Pruebas =================
        addSeparator(p, c, y++, "Pruebas de vestido");

        txtFechaPrueba1 = createDateFieldMX();
        txtHoraPrueba1  = createTimeField();
        cbModista1      = new JComboBox<>();

        addRow(p, c, y++,
               new JLabel("Fecha prueba 1:"), txtFechaPrueba1,
               new JLabel("Hora prueba 1:"), txtHoraPrueba1);
        addRowFull(p, c, y++,
               new JLabel("Modista prueba 1:"), cbModista1);

        txtFechaPrueba2 = createDateFieldMX();
        txtHoraPrueba2  = createTimeField();
        cbModista2      = new JComboBox<>();

        addRow(p, c, y++,
               new JLabel("Fecha prueba 2:"), txtFechaPrueba2,
               new JLabel("Hora prueba 2:"), txtHoraPrueba2);
        addRowFull(p, c, y++,
               new JLabel("Modista prueba 2:"), cbModista2);

        // ================= Entrega =================
        addSeparator(p, c, y++, "Entrega");

        txtFechaEntrega   = createDateFieldMX();
        txtHoraEntrega    = createTimeField();
        cbAsesoraEntrega  = new JComboBox<>(new String[]{""});

        addRow(p, c, y++,
               new JLabel("Fecha de entrega:"), txtFechaEntrega,
               new JLabel("Hora entrega:"), txtHoraEntrega);
        addRowFull(p, c, y++,
               new JLabel("Asesora entrega:"), cbAsesoraEntrega);

        add(p, BorderLayout.CENTER);

        // ================= Botones =================
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnGuardar = new JButton("Guardar");
        btnLimpiar = new JButton("Limpiar");

        btnGuardar.addActionListener(_e -> guardar());
        btnLimpiar.addActionListener(_e -> limpiar());

        actions.add(btnGuardar);
        actions.add(btnLimpiar);
        add(actions, BorderLayout.SOUTH);

        // Cargar asesoras y modistas desde BD
        cargarPersonalCitas();

    }
    // ======================================================
    //  LÓGICA
    // ======================================================

    private void buscarCliente() {
    String tel = TelefonosUI.soloDigitos(txtTel.getText());
    if (tel.isEmpty()) {
        JOptionPane.showMessageDialog(this,
                "Captura el teléfono del cliente.",
                "Validación", JOptionPane.WARNING_MESSAGE);
        return;
    }

    try {
        clienteActual = cliDao.buscarResumenPorTelefono(tel);
            if (clienteActual == null) {
                JOptionPane.showMessageDialog(this,
                        "No se encontró un cliente con ese teléfono.",
                        "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
                limpiarDatosCliente();
                return;
            }

            txtNombre.setText(
                    clienteActual.getNombreCompleto() == null
                            ? "" : clienteActual.getNombreCompleto()
            );

            LocalDate ev = clienteActual.getFechaEvento();
    txtFechaEvento.setText(ev == null ? "" : ev.format(MX_FECHA));

    // ========= CITA 1 =========
    setDateToField(txtFechaCita1, clienteActual.getFechaCita1());
    txtHoraCita1.setText(horaSafe(clienteActual.getHoraCita1()));
    seleccionarEnCombo(cbAsesora1, clienteActual.getAsesoraCita1());

    // ========= CITA 2 =========
    setDateToField(txtFechaCita2, clienteActual.getFechaCita2());
    txtHoraCita2.setText(horaSafe(clienteActual.getHoraCita2()));
    seleccionarEnCombo(cbAsesora2, clienteActual.getAsesoraCita2());

    // ========= PRUEBA 1 =========
    setDateToField(txtFechaPrueba1, clienteActual.getFechaPrueba1());
    txtHoraPrueba1.setText(horaSafe(clienteActual.getHoraPrueba1()));
    seleccionarEnCombo(cbModista1, clienteActual.getModistaPrueba1());

    // ========= PRUEBA 2 =========
    setDateToField(txtFechaPrueba2, clienteActual.getFechaPrueba2());
    txtHoraPrueba2.setText(horaSafe(clienteActual.getHoraPrueba2()));
    seleccionarEnCombo(cbModista2, clienteActual.getModistaPrueba2());

    // ========= ENTREGA =========
    setDateToField(txtFechaEntrega, clienteActual.getFechaEntrega());
    txtHoraEntrega.setText(horaSafe(clienteActual.getHoraEntrega()));
    seleccionarEnCombo(cbAsesoraEntrega, clienteActual.getAsesoraEntrega());

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
private void cargarPersonalCitas() {
    try {
        AsesorDAO ad = new AsesorDAO();

        // ASESORAS: tipo A o MA, status A
        java.util.List<Modelo.Asesor> listaAsesoras = ad.listarActivosDetalle();

        // MODISTAS: tipo M o MA, status A
        java.util.List<Modelo.Asesor> listaModistas = ad.listarModistasActivas();

        // ----- Asesoras -----
        llenarComboPersonal(cbAsesora1, listaAsesoras, "Selecciona asesora");
        llenarComboPersonal(cbAsesora2, listaAsesoras, "Selecciona asesora");
        llenarComboPersonal(cbAsesoraEntrega, listaAsesoras, "Selecciona asesora");

        // ----- Modistas -----
        llenarComboPersonal(cbModista1, listaModistas, "Selecciona modista");
        llenarComboPersonal(cbModista2, listaModistas, "Selecciona modista");

    } catch (SQLException e) {
        JOptionPane.showMessageDialog(
                this,
                "No se pudieron cargar empleados para citas: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }
}

    private void limpiarDatosCliente() {
        clienteActual = null;
        txtNombre.setText("");
        txtFechaEvento.setText("");
        txtFechaPrueba1.setValue(null);
        txtFechaPrueba2.setValue(null);
        txtFechaEntrega.setValue(null);
    }

    private void limpiar() {
        txtTel.setText("");
        limpiarDatosCliente();

        txtFechaCita1.setValue(null);
        txtHoraCita1.setValue(null);
        cbAsesora1.setSelectedIndex(0);

        txtFechaCita2.setValue(null);
        txtHoraCita2.setValue(null);
        cbAsesora2.setSelectedIndex(0);

        txtFechaPrueba1.setValue(null);
        txtHoraPrueba1.setValue(null);
        cbModista1.setSelectedIndex(0);

        txtFechaPrueba2.setValue(null);
        txtHoraPrueba2.setValue(null);
        cbModista2.setSelectedIndex(0);

        txtFechaEntrega.setValue(null);
        txtHoraEntrega.setValue(null);
        cbAsesoraEntrega.setSelectedIndex(0);

        txtTel.requestFocus();
    }

    private void guardar() {
        if (clienteActual == null) {
            JOptionPane.showMessageDialog(this,
                    "Primero busca y selecciona un cliente.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String tel = TelefonosUI.soloDigitos(txtTel.getText());
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "El teléfono es obligatorio.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Fechas
            LocalDate fc1  = parseFechaField(txtFechaCita1);
            LocalDate fc2  = parseFechaField(txtFechaCita2);
            LocalDate fp1  = parseFechaField(txtFechaPrueba1);
            LocalDate fp2  = parseFechaField(txtFechaPrueba2);
            LocalDate fEnt = parseFechaField(txtFechaEntrega);

            // Horas (validan formato HH:mm y devuelven null si está vacío)
            String hc1   = horaFieldToString(txtHoraCita1);
            String hc2   = horaFieldToString(txtHoraCita2);
            String hp1   = horaFieldToString(txtHoraPrueba1);
            String hp2   = horaFieldToString(txtHoraPrueba2);
            String hEnt  = horaFieldToString(txtHoraEntrega);

            // ======= VALIDACIÓN: no permitir horas pasadas para HOY =======
            validarHoraNoPasada(fc1,  hc1,  "1ª cita");
            validarHoraNoPasada(fc2,  hc2,  "2ª cita");
            validarHoraNoPasada(fp1,  hp1,  "prueba 1");
            validarHoraNoPasada(fp2,  hp2,  "prueba 2");
            validarHoraNoPasada(fEnt, hEnt, "entrega");
            // =============================================================

            // Asesoras / modistas (texto)
            String ases1   = comboVal(cbAsesora1);
            String ases2   = comboVal(cbAsesora2);
            String mod1    = comboVal(cbModista1);
            String mod2    = comboVal(cbModista2);
            String asesEnt = comboVal(cbAsesoraEntrega);

            Object[] opts = {"SI", "NO"};
            int r = JOptionPane.showOptionDialog(this,
                    "¿Deseas guardar la información de citas para este cliente?",
                    "Confirmación",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, opts, opts[0]);
            if (r != JOptionPane.YES_OPTION) return;

            boolean ok = cliDao.actualizarCitasCliente(
                    tel,
                    fc1, hc1, ases1,
                    fc2, hc2, ases2,
                    fp1, hp1, mod1,
                    fp2, hp2, mod2,
                    fEnt, hEnt, asesEnt
            );

            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Información de citas/pruebas/entrega guardada correctamente.",
                        "Listo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "No se actualizó ningún registro. Verifica el teléfono.",
                        "Atención", JOptionPane.WARNING_MESSAGE);
            }

        } catch (IllegalArgumentException ex) {
            // Aquí caen tanto fechas/horas inválidas como la validación de hora pasada
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error inesperado: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ======================================================
    //  Helpers UI / fechas / horas
    // ======================================================

    private void addRow(JPanel p, GridBagConstraints c, int y,
                        JComponent l1, JComponent f1,
                        JComponent l2, JComponent f2) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        p.add(l1, c);
        c.gridx = 1;
        p.add(f1, c);
        c.gridx = 2;
        p.add(l2, c);
        c.gridx = 3;
        p.add(f2, c);
    }
private void llenarComboPersonal(JComboBox<String> cb,
                                 java.util.List<Modelo.Asesor> lista,
                                 String placeholder) {
    cb.removeAllItems();
    cb.addItem(placeholder);
    if (lista == null) return;

    for (Modelo.Asesor a : lista) {
        String nombre = a.getNombreCompleto();
        if (nombre != null && !nombre.isBlank()) {
            cb.addItem(nombre);
        }
    }
}

    private void addRowFull(JPanel p, GridBagConstraints c, int y,
                            JComponent l, JComponent f) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1;
        p.add(l, c);
        c.gridx = 1; c.gridwidth = 3;
        p.add(f, c);
        c.gridwidth = 1;
    }

    private void addSeparator(JPanel p, GridBagConstraints c, int y, String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        JSeparator sep = new JSeparator();
        JPanel cont = new JPanel(new BorderLayout(8, 0));
        cont.add(label, BorderLayout.WEST);
        cont.add(sep, BorderLayout.CENTER);

        c.gridx = 0; c.gridy = y; c.gridwidth = 4;
        p.add(cont, c);
        c.gridwidth = 1;
    }

    private JFormattedTextField createDateFieldMX() {
        try {
            MaskFormatter mf = new MaskFormatter("##-##-####");
            mf.setPlaceholderCharacter('_');
            JFormattedTextField f = new JFormattedTextFieldFixed(mf);
            f.setColumns(10);
            f.setToolTipText("DD-MM-YYYY, ej. 24-08-2025");
            return f;
        } catch (Exception e) {
            return new JFormattedTextField();
        }
    }

    private JFormattedTextField createTimeField() {
        try {
            MaskFormatter mf = new MaskFormatter("##:##");
            mf.setPlaceholderCharacter('_');
            JFormattedTextField f = new JFormattedTextFieldFixed(mf);
            f.setColumns(5);
            f.setToolTipText("HH:MM (24h), ej. 16:30");
            return f;
        } catch (Exception e) {
            return new JFormattedTextField();
        }
    }

    private void setDateToField(JFormattedTextField f, LocalDate d) {
        if (d == null) f.setValue(null);
        else f.setText(d.format(MX_FECHA));
    }

    private LocalDate parseFechaField(JFormattedTextField f) {
        String s = f.getText();
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.contains("_")) return null;
        try {
            return LocalDate.parse(s, MX_FECHA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida: " + s);
        }
    }

    private String horaFieldToString(JFormattedTextField f) {
        String s = f.getText();
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.contains("_")) return null;
        try {
            MX_HORA.parse(s); // valida formato
            return s;
        } catch (Exception e) {
            throw new IllegalArgumentException("Hora inválida: " + s);
        }
    }

    private String comboVal(JComboBox<String> cb) {
    Object v = cb.getSelectedItem();
    if (v == null) return null;
    String s = v.toString().trim();
    if (s.isEmpty() || s.startsWith("Selecciona ")) return null;
    return s;
}




    // ===== filtros reutilizables =====

    /** Solo dígitos (0-9) con longitud máxima. */
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
        public void replace(FilterBypass fb, int offset, int length,
                            String text, AttributeSet attrs) throws BadLocationException {
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset)
                    + (text == null ? "" : text)
                    + cur.substring(offset + length);
            if (valid(next)) super.replace(fb, offset, length, text, attrs);
        }

        @Override
        public void insertString(FilterBypass fb, int offset,
                                 String text, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }
    }

    /** Fix al caret cuando se usa MaskFormatter. */
    static class JFormattedTextFieldFixed extends JFormattedTextField {
        public JFormattedTextFieldFixed(AbstractFormatter formatter) {
            super(formatter);
        }
        @Override
        public void processKeyEvent(java.awt.event.KeyEvent e) {
            super.processKeyEvent(e);
            setCaretPosition(Math.min(getCaretPosition(), getText().length()));
        }
    }
    @Override
public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
    if (aFlag) {
        // Cada vez que el panel se hace visible, recarga asesoras y modistas
        cargarPersonalCitas();
    }
}
    /**
     * Valida que una cita NO quede en una hora pasada del día de hoy.
     * - Si la fecha es distinta de hoy, no se valida (se permite pasado / futuro).
     * - Si fecha u hora son null, no se valida.
     */
    private void validarHoraNoPasada(LocalDate fecha, String hora, String etiquetaCampo) {
        if (fecha == null || hora == null) return;

        LocalDate hoy = LocalDate.now();
        if (!fecha.isEqual(hoy)) {
            // Solo nos interesa el caso "hoy con hora en el pasado"
            return;
        }

        LocalTime horaCita = LocalTime.parse(hora, MX_HORA);

        // quitamos segundos/nanos para que 16:00:00.001 no cause tonterías
        LocalTime ahora = LocalTime.now().withSecond(0).withNano(0);

        if (horaCita.isBefore(ahora)) {
            throw new IllegalArgumentException(
                    "La " + etiquetaCampo + " no puede ser en una hora anterior a la actual."
            );
        }
    }
    private String horaSafe(String h) {
    if (h == null) return "";
    h = h.trim();
    // Por si en BD quedó "HH:mm:ss"
    if (h.length() >= 5) return h.substring(0, 5);
    return h;
}

private void seleccionarEnCombo(JComboBox<String> cb, String valor) {
    if (valor == null || valor.isBlank()) {
        cb.setSelectedIndex(0);
        return;
    }
    cb.setSelectedItem(valor);
}
// Dentro de RegistroCitasPanel
public void setClienteDesdeResumen(ClienteResumen cr) {
    if (cr == null) return;

    txtNombre.setText(
            cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto()
    );

    String tel = TelefonosUI.soloDigitos(cr.getTelefono1());
    txtTel.setText(tel == null ? "" : tel);

    if (cr.getFechaEvento() != null) {
        txtFechaEvento.setText(cr.getFechaEvento().format(MX_FECHA));
    } else {
        txtFechaEvento.setText("");
    }
}

private void buscarClientePorNombre() {
    // Usa el diálogo que ya definiste en AgendaPanel
    Window owner = SwingUtilities.getWindowAncestor(this);
    AgendaPanel.DialogBusquedaCliente dlg =
            new AgendaPanel.DialogBusquedaCliente(owner);
    dlg.setLocationRelativeTo(this);
    dlg.setVisible(true);

    ClienteResumen cr = dlg.getSeleccionado();
    if (cr != null) {
        // llenamos teléfono / nombre / fecha evento
        setClienteDesdeResumen(cr);
        // y reutilizamos la lógica normal para cargar todas las citas
        buscarCliente();
    }
}

}
