package Vista;

import Controlador.AsesorDAO;
import Modelo.Asesor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AsesoresPanel extends JPanel {

    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private JTextField txtNumero;
    private JTextField txtNombre;
    private JTextField txtFechaAlta; // NUEVO

    private JTable tabla;
    private DefaultTableModel modelo;

    public AsesoresPanel() {
        setLayout(new BorderLayout());

        // --- Encabezado
        JLabel title = new JLabel("Administración de Asesores", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setBorder(BorderFactory.createEmptyBorder(12,12,6,12));
        add(title, BorderLayout.NORTH);

        // --- Panel de alta
        JPanel alta = new JPanel(new GridBagLayout());
        alta.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Nuevo asesor",
                TitledBorder.LEFT, TitledBorder.TOP));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        txtNumero = new JTextField();
        ((AbstractDocument) txtNumero.getDocument()).setDocumentFilter(new DigitsOnlyFilter(9));

        txtNombre = new JTextField();
        ((AbstractDocument) txtNombre.getDocument()).setDocumentFilter(new LettersSpacesFilter(80));

        txtFechaAlta = new JTextField(); // NUEVO
        txtFechaAlta.setToolTipText("dd-MM-aaaa (si se deja vacío, se usará la fecha de hoy)");

        int y=0;
        addCell(alta, c, 0,y, new JLabel("Número de empleado*:"),1,false);
        addCell(alta, c, 1,y, txtNumero,1,true); y++;

        addCell(alta, c, 0,y, new JLabel("Nombre completo*:"),1,false);
        addCell(alta, c, 1,y, txtNombre,1,true); y++;

        addCell(alta, c, 0,y, new JLabel("Fecha de alta (dd-MM-aaaa):"),1,false);      // NUEVO
        addCell(alta, c, 1,y, txtFechaAlta,1,true); y++;

        JButton btGuardar = new JButton("Guardar");
        JButton btLimpiar = new JButton("Limpiar");
        btGuardar.addActionListener(_e -> guardar());
        btLimpiar.addActionListener(_e -> limpiar());
        addCell(alta, c, 1,y, wrapRight(btGuardar, btLimpiar),1,true);

        alta.setPreferredSize(new Dimension(360, 220));
        add(alta, BorderLayout.WEST);

        // --- Tabla
        String[] cols = {"Número", "Nombre", "Fecha alta", "Fecha baja", "Status"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int col) { return false; }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Asesores registrados",
                TitledBorder.LEFT, TitledBorder.TOP));
        add(sp, BorderLayout.CENTER);

        // --- Acciones sobre seleccionados
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btDesactivar = new JButton("Desactivar");
        JButton btReactivar = new JButton("Reactivar");
        JButton btRefrescar = new JButton("Refrescar");
        btDesactivar.addActionListener(_e -> cambiarEstado(false));
        btReactivar.addActionListener(_e -> cambiarEstado(true));
        btRefrescar.addActionListener(_e -> cargarTabla());
        acciones.add(btRefrescar);
        acciones.add(btDesactivar);
        acciones.add(btReactivar);
        add(acciones, BorderLayout.SOUTH);

        // Cargar datos iniciales
        cargarTabla();
    }

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
                        a.getStatus()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al cargar asesores: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardar() {
        String snum = txtNumero.getText().trim();
        String nom  = txtNombre.getText().trim();
        String sfa  = txtFechaAlta.getText().trim(); // NUEVO

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

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this, "¿Guardar asesor?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            AsesorDAO dao = new AsesorDAO();
            if (dao.existeNumero(numero)) {
                JOptionPane.showMessageDialog(this, "Ya existe un asesor con ese número.",
                        "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Asesor a = new Asesor();
            a.setNumeroEmpleado(numero);
            a.setNombreCompleto(nom);
            a.setFechaAlta(fechaAlta);     // si viene null, DAO usará hoy
            a.setStatus("A");
            dao.insertar(a);

            JOptionPane.showMessageDialog(this, "Asesor guardado correctamente.");
            limpiar();
            cargarTabla();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cambiarEstado(boolean activar) {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un asesor en la tabla.",
                    "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int numero = Integer.parseInt(modelo.getValueAt(row,0).toString());
        String nombre = modelo.getValueAt(row,1).toString();

        if (!activar) {
            // Pedir fecha de baja
            JTextField tf = new JTextField(LocalDate.now().format(MX));
            Object[] msg = {
                    "¿Deseas desactivar al asesor " + nombre + " (" + numero + ")?",
                    "Fecha de baja (dd-MM-aaaa):", tf
            };
            Object[] ops = {"Aceptar","Cancelar"};
            int rr = JOptionPane.showOptionDialog(this, msg, "Confirmación",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, ops, ops[0]);
            if (rr != JOptionPane.OK_OPTION) return;

            LocalDate fBaja;
            try { fBaja = LocalDate.parse(tf.getText().trim(), MX); }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fecha de baja inválida. Usa dd-MM-aaaa.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                AsesorDAO dao = new AsesorDAO();
                dao.desactivar(numero, fBaja);    // usa la fecha elegida
                cargarTabla();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Error al desactivar: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        // Reactivar
        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Deseas reactivar al asesor " + nombre + " (" + numero + ")?",
                "Confirmación", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            AsesorDAO dao = new AsesorDAO();
            dao.reactivar(numero);
            cargarTabla();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al reactivar: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiar() {
        txtNumero.setText("");
        txtNombre.setText("");
        txtFechaAlta.setText("");
        txtNumero.requestFocus();
    }

    // ---------- helpers UI ----------
    private void addCell(JPanel p, GridBagConstraints c, int x, int y, JComponent comp, int span, boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }
    private JPanel wrapRight(JButton... btns) {
        JPanel pan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (JButton b : btns) pan.add(b);
        return pan;
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

    // Harness opcional
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Asesores (test panel)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(820,560);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(new AsesoresPanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
