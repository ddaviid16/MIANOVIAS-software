package Vista;

import Controlador.AsesorDAO;
import Modelo.Asesor;
import Utilidades.SeguridadUI;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AsesoresPanel extends JPanel {

    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    // Códigos / textos de tipo de empleado
    private static final String[] TIPOS_COD   = {"A","M","MA"};
    private static final String[] TIPOS_LABEL = {"Asesora","Modista","Modista / Asesora"};

    private JTextField txtNumero;
    private JTextField txtNombre;
    private JTextField txtFechaAlta;
    private JComboBox<String> cbTipo;   // Puesto

    private JTable tabla;
    private DefaultTableModel modelo;

    public AsesoresPanel() {
        setLayout(new BorderLayout());

        // --- Encabezado
        JLabel title = new JLabel("Administración de Empleados", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setBorder(BorderFactory.createEmptyBorder(12,12,6,12));
        add(title, BorderLayout.NORTH);

        // --- Panel de alta
        JPanel alta = new JPanel(new GridBagLayout());
        alta.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Nuevo empleado",
                TitledBorder.LEFT, TitledBorder.TOP));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        txtNumero = new JTextField();
        ((AbstractDocument) txtNumero.getDocument()).setDocumentFilter(new DigitsOnlyFilter(9));

        txtNombre = new JTextField();
        ((AbstractDocument) txtNombre.getDocument()).setDocumentFilter(new LettersSpacesFilter(80));

        txtFechaAlta = new JTextField();
        txtFechaAlta.setToolTipText("dd-MM-aaaa (si se deja vacío, se usará la fecha de hoy)");
        ((AbstractDocument) txtFechaAlta.getDocument()).setDocumentFilter(new FechaMaskFilter());

        cbTipo = new JComboBox<>(TIPOS_LABEL);

        int y=0;
        addCell(alta, c, 0,y, new JLabel("Número de empleado*:"),1,false);
        addCell(alta, c, 1,y, txtNumero,1,true); y++;

        addCell(alta, c, 0,y, new JLabel("Nombre completo*:"),1,false);
        addCell(alta, c, 1,y, txtNombre,1,true); y++;

        addCell(alta, c, 0,y, new JLabel("Fecha de alta (dd-MM-aaaa):"),1,false);
        addCell(alta, c, 1,y, txtFechaAlta,1,true); y++;

        addCell(alta, c, 0,y, new JLabel("Puesto:"),1,false);
        addCell(alta, c, 1,y, cbTipo,1,true); y++;

        JButton btGuardar = new JButton("Guardar");
        JButton btLimpiar = new JButton("Limpiar");
        btGuardar.addActionListener(_e -> guardar());
        btLimpiar.addActionListener(_e -> limpiar());
        addCell(alta, c, 1,y, wrapRight(btGuardar, btLimpiar),1,true);

        alta.setPreferredSize(new Dimension(360, 240));
        add(alta, BorderLayout.WEST);

        // --- Tabla
        String[] cols = {
                "Número", "Nombre", "Fecha alta", "Fecha baja",
                "Puesto", "Status", "Perm. cancelar", "Modificar"
        };
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int col) { return col == 7; }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Empleados registrados",
                TitledBorder.LEFT, TitledBorder.TOP));
        add(sp, BorderLayout.CENTER);

        // Columna "Modificar" con botón
        new ButtonColumn(tabla, new AbstractAction("Modificar") {
            @Override public void actionPerformed(ActionEvent e) {
                int row = Integer.parseInt(e.getActionCommand());
                int modelRow = tabla.convertRowIndexToModel(row);
                int numero = Integer.parseInt(modelo.getValueAt(modelRow, 0).toString());
                abrirDialogoModificar(numero);
            }
        }, 7);

        // --- Barra inferior (solo refrescar)
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btRefrescar = new JButton("Refrescar");
        btRefrescar.addActionListener(_e -> cargarTabla());
        acciones.add(btRefrescar);
        add(acciones, BorderLayout.SOUTH);

        // Cargar datos iniciales
        cargarTabla();
    }

    // ====== carga de tabla ======
    private void cargarTabla() {
        try {
            AsesorDAO dao = new AsesorDAO();
            List<Asesor> lista = dao.listarTodos();
            modelo.setRowCount(0);
            for (Asesor a : lista) {
                modelo.addRow(new Object[]{
                        a.getNumeroEmpleado(),
                        a.getNombreCompleto(),
                        a.getFechaAlta()==null ? "" : a.getFechaAlta().format(MX),
                        a.getFechaBaja()==null ? "" : a.getFechaBaja().format(MX),
                        tipoLabel(a.getTipoEmpleado()),
                        a.getStatus(),
                        a.isPermisoCancelaNota() ? "SI" : "NO",
                        "Modificar"
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al cargar empleados: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====== alta de empleado ======
    private void guardar() {
        String snum = txtNumero.getText().trim();
        String nom  = txtNombre.getText().trim();
        String sfa  = txtFechaAlta.getText().trim();

        if (snum.isEmpty() || nom.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Número y nombre son obligatorios.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int numero;
        try { numero = Integer.parseInt(snum); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "El número debe ser numérico.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate fechaAlta = null;
        if (!sfa.isBlank()) {
            try { fechaAlta = LocalDate.parse(sfa, MX); }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fecha de alta inválida. Usa dd-MM-aaaa.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        String tipoCod = tipoCodigoDesdeLabel((String) cbTipo.getSelectedItem());

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this, "¿Guardar empleado?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            AsesorDAO dao = new AsesorDAO();
            if (dao.existeNumero(numero)) {
                JOptionPane.showMessageDialog(this, "Ya existe un empleado con ese número.",
                        "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Asesor a = new Asesor();
            a.setNumeroEmpleado(numero);
            a.setNombreCompleto(nom);
            a.setFechaAlta(fechaAlta); // si viene null, DAO usará hoy
            a.setTipoEmpleado(tipoCod);
            a.setStatus("A");
            a.setPermisoCancelaNota(false);   // alta sin permiso por defecto
            dao.insertar(a);

            JOptionPane.showMessageDialog(this, "Empleado guardado correctamente.");
            limpiar();
            cargarTabla();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====== modificar (botón de la tabla) ======
    private void abrirDialogoModificar(int numero) {
        try {
            AsesorDAO dao = new AsesorDAO();
            Asesor a = dao.buscarPorNumero(numero);
            if (a == null) {
                JOptionPane.showMessageDialog(this, "No se encontró el empleado " + numero,
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Window w = SwingUtilities.getWindowAncestor(this);
            Frame owner = (w instanceof Frame) ? (Frame) w : null;

            DialogEmpleado dlg = new DialogEmpleado(owner, a);
            dlg.setVisible(true);
            if (dlg.isGuardado()) {
                cargarTabla();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al consultar empleado: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiar() {
        txtNumero.setText("");
        txtNombre.setText("");
        txtFechaAlta.setText("");
        cbTipo.setSelectedIndex(0);
        txtNumero.requestFocus();
    }

    // ---------- helpers UI ----------
    private void addCell(Container p, GridBagConstraints c,
                         int x, int y, JComponent comp, int span, boolean growX) {
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = span;
        c.weightx = growX ? 1 : 0;
        p.add(comp, c);
        c.gridwidth = 1;
    }

    private JPanel wrapRight(JButton... btns) {
        JPanel pan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (JButton b : btns) pan.add(b);
        return pan;
    }

    private String tipoLabel(String cod) {
        if (cod == null) return "";
        cod = cod.trim().toUpperCase();
        return switch (cod) {
            case "A" -> "Asesora";
            case "M" -> "Modista";
            case "MA" -> "Modista / Asesora";
            default -> "";
        };
    }

    private String tipoCodigoDesdeLabel(String label) {
        if (label == null) return "A";
        label = label.trim();
        for (int i=0;i<TIPOS_LABEL.length;i++) {
            if (TIPOS_LABEL[i].equalsIgnoreCase(label)) return TIPOS_COD[i];
        }
        return "A";
    }

    // ---------- filtros ----------
    /** Solo dígitos, con longitud máxima. */
    static class DigitsOnlyFilter extends DocumentFilter {
        private final int maxLen;
        DigitsOnlyFilter(int maxLen) { this.maxLen = maxLen; }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text==null?"":text) + cur.substring(offset+length);
            if (next.length() <= maxLen && next.matches("\\d*")) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    /** Letras y espacios (incluye acentos). */
    static class LettersSpacesFilter extends DocumentFilter {
        private final int maxLen;
        LettersSpacesFilter(int maxLen) { this.maxLen = maxLen; }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text==null?"":text) + cur.substring(offset+length);
            if (next.length() <= maxLen && next.matches("[\\p{L} .'-]*")) super.replace(fb, offset, length, text, attrs);
        }
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException { replace(fb, offset, 0, text, attr); }
    }

    /** Máscara dd-MM-aaaa: al escribir 25112025 se ve 25-11-2025. */
    static class FechaMaskFilter extends DocumentFilter {

        private String format(String s) {
            if (s == null) return "";
            // Solo dígitos
            StringBuilder digits = new StringBuilder();
            for (int i=0;i<s.length();i++) {
                char ch = s.charAt(i);
                if (ch >= '0' && ch <= '9') digits.append(ch);
            }
            if (digits.length() > 8) digits.setLength(8);

            StringBuilder out = new StringBuilder();
            for (int i=0;i<digits.length();i++) {
                if (i == 2 || i == 4) out.append('-');
                out.append(digits.charAt(i));
            }
            return out.toString();
        }

        private void applyFormatted(FilterBypass fb, String candidate, AttributeSet attrs)
                throws BadLocationException {
            String formatted = format(candidate);
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            fb.replace(0, cur.length(), formatted, attrs);
        }

        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String before = cur.substring(0, offset);
            String after  = cur.substring(offset + length);
            String candidate = before + (text == null ? "" : text) + after;
            applyFormatted(fb, candidate, attrs);
        }

        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override public void remove(FilterBypass fb, int offset, int length)
                throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String before = cur.substring(0, offset);
            String after  = cur.substring(offset + length);
            String candidate = before + after;
            applyFormatted(fb, candidate, null);
        }
    }

    // ---- Botón en columna ----
    static class ButtonColumn extends AbstractCellEditor
            implements TableCellRenderer, TableCellEditor, java.awt.event.ActionListener {
        private final JTable table;
        private final Action action;
        private final JButton renderButton = new JButton("Modificar");
        private final JButton editButton   = new JButton("Modificar");

        public ButtonColumn(JTable table, Action action, int column) {
            this.table = table;
            this.action = action;
            editButton.setFocusPainted(false);
            editButton.addActionListener(this);
            TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(column).setCellRenderer(this);
            columnModel.getColumn(column).setCellEditor(this);
        }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            renderButton.setText(value == null ? "Modificar" : value.toString());
            return renderButton;
        }
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int col) {
            editButton.setText(value == null ? "Modificar" : value.toString());
            return editButton;
        }
        @Override public Object getCellEditorValue() { return "Modificar"; }
        @Override public void actionPerformed(ActionEvent e) {
            int row = table.getEditingRow();
            fireEditingStopped();
            action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, String.valueOf(row)));
        }
    }

    // ---- Diálogo para modificar empleado ----
    private class DialogEmpleado extends JDialog {
        private final JTextField txtNum = new JTextField();
        private final JTextField txtNom = new JTextField();
        private final JTextField txtFa  = new JTextField();
        private final JTextField txtFb  = new JTextField();
        private final JComboBox<String> cbTipoDlg = new JComboBox<>(TIPOS_LABEL);
        private final JComboBox<String> cbStatus  = new JComboBox<>(new String[]{"A","C"});
        private final JCheckBox chkPermiteCancelar = new JCheckBox("Puede cancelar notas");
        private boolean guardado = false;
        private boolean cambiandoStatus = false;
        private boolean permisoActual;

        DialogEmpleado(Frame owner, Asesor a) {
            super(owner, "Modificar empleado", true);
            setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6,6,6,6);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;

            ((AbstractDocument) txtFa.getDocument()).setDocumentFilter(new FechaMaskFilter());
            ((AbstractDocument) txtFb.getDocument()).setDocumentFilter(new FechaMaskFilter());

            int y=0;
            txtNum.setEditable(false);
            txtNum.setText(String.valueOf(a.getNumeroEmpleado()));
            txtNom.setText(a.getNombreCompleto()==null? "": a.getNombreCompleto());
            txtFa.setText(a.getFechaAlta()==null? "": a.getFechaAlta().format(MX));
            txtFb.setText(a.getFechaBaja()==null? "": a.getFechaBaja().format(MX));
            cbTipoDlg.setSelectedItem(tipoLabel(a.getTipoEmpleado()));

            // Status inicial
            String st = a.getStatus();
            if (st == null || st.isBlank()) st = "A";
            st = st.trim().toUpperCase();
            if (!"C".equals(st)) st = "A";
            cbStatus.setSelectedItem(st);
            txtFb.setEnabled("C".equals(st));

            // Permiso inicial
            permisoActual = a.isPermisoCancelaNota();
            chkPermiteCancelar.setSelected(permisoActual);

            cbStatus.addActionListener(_e -> onStatusChanged());

            // listener para pedir clave cuando se pasa de false -> true
            chkPermiteCancelar.addActionListener(_e -> {
                boolean nuevo = chkPermiteCancelar.isSelected();
                if (!permisoActual && nuevo) {
                    // se quiere habilitar: pedir clave maestra
                    if (!SeguridadUI.pedirYValidarClave(this)) {
                        chkPermiteCancelar.setSelected(false);
                        return;
                    }
                }
                permisoActual = nuevo;
            });

            addCell(this, c, 0,y, new JLabel("Número:"),1,false);
            addCell(this, c, 1,y, txtNum,1,true); y++;

            addCell(this, c, 0,y, new JLabel("Nombre completo:"),1,false);
            addCell(this, c, 1,y, txtNom,1,true); y++;

            addCell(this, c, 0,y, new JLabel("Fecha alta (dd-MM-aaaa):"),1,false);
            addCell(this, c, 1,y, txtFa,1,true); y++;

            addCell(this, c, 0,y, new JLabel("Fecha baja (dd-MM-aaaa):"),1,false);
            addCell(this, c, 1,y, txtFb,1,true); y++;

            addCell(this, c, 0,y, new JLabel("Puesto:"),1,false);
            addCell(this, c, 1,y, cbTipoDlg,1,true); y++;

            addCell(this, c, 0,y, new JLabel("Status:"),1,false);
            addCell(this, c, 1,y, cbStatus,1,true); y++;

            addCell(this, c, 0,y, new JLabel("Permiso cancelar notas:"),1,false);
            addCell(this, c, 1,y, chkPermiteCancelar,1,true); y++;

            JButton btGuardar = new JButton("Actualizar");
            JButton btCancelar = new JButton("Cancelar");
            btGuardar.addActionListener(_e -> guardarCambios(a));
            btCancelar.addActionListener(_e -> dispose());
            addCell(this, c, 1,y, wrapRight(btGuardar, btCancelar),1,true);

            pack();
            setLocationRelativeTo(owner);
        }

        private void onStatusChanged() {
            if (cambiandoStatus) return;
            String nuevo = (String) cbStatus.getSelectedItem();
            if (nuevo == null) nuevo = "A";
            nuevo = nuevo.trim().toUpperCase();

            if ("C".equals(nuevo)) {
                Object[] ops = {"Sí","No"};
                int r = JOptionPane.showOptionDialog(this,
                        "¿Deseas dar de baja a este empleado?",
                        "Confirmación", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, ops, ops[1]);
                if (r == JOptionPane.YES_OPTION) {
                    txtFb.setEnabled(true);
                    if (txtFb.getText().isBlank()) {
                        txtFb.setText(LocalDate.now().format(MX));
                    }
                } else {
                    cambiandoStatus = true;
                    cbStatus.setSelectedItem("A");
                    cambiandoStatus = false;
                    txtFb.setText("");
                    txtFb.setEnabled(false);
                }
            } else { // A
                txtFb.setText("");
                txtFb.setEnabled(false);
            }
        }

        private void guardarCambios(Asesor aOriginal) {
            String nom = txtNom.getText().trim();
            String sfa = txtFa.getText().trim();
            String sfb = txtFb.getText().trim();
            if (nom.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El nombre no puede estar vacío.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            LocalDate fa = null;
            if (!sfa.isBlank()) {
                try { fa = LocalDate.parse(sfa, MX); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Fecha de alta inválida. Usa dd-MM-aaaa.",
                            "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            String status = (String) cbStatus.getSelectedItem();
            if (status == null || status.isBlank()) status = "A";
            status = status.trim().toUpperCase();
            if (!"C".equals(status)) status = "A";

            LocalDate fb = null;
            if (!sfb.isBlank()) {
                try { fb = LocalDate.parse(sfb, MX); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Fecha de baja inválida. Usa dd-MM-aaaa.",
                            "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            if ("C".equals(status) && fb == null) {
                JOptionPane.showMessageDialog(this, "Indica la fecha de baja para un empleado con status C.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String tipoCod = tipoCodigoDesdeLabel((String) cbTipoDlg.getSelectedItem());

            Object[] ops = {"SI","NO"};
            int r = JOptionPane.showOptionDialog(this, "¿Actualizar datos del empleado?",
                    "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, ops, ops[0]);
            if (r != JOptionPane.YES_OPTION) return;

            try {
                AsesorDAO dao = new AsesorDAO();
                Asesor a = new Asesor();
                a.setNumeroEmpleado(aOriginal.getNumeroEmpleado());
                a.setNombreCompleto(nom);
                a.setFechaAlta(fa);
                a.setTipoEmpleado(tipoCod);
                a.setStatus(status);
                a.setFechaBaja(fb);
                a.setPermisoCancelaNota(chkPermiteCancelar.isSelected());
                dao.actualizarBasico(a);
                guardado = true;
                dispose();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Error al actualizar: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        public boolean isGuardado() { return guardado; }
    }

    // Harness opcional
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Empleados (test panel)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(900,560);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(new AsesoresPanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
