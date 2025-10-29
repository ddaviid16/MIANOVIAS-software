package Vista;

import Controlador.PagoGastosDAO;
import Controlador.CorteCajaDAO;
import Utilidades.EventBus;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PagoGastosPanel extends JPanel {

    private static final NumberFormat NF_DISPLAY = buildFmt();
    private static NumberFormat buildFmt() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setGroupingUsed(true);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf;
    }

    private final JTextField txtFecha = ro();
    private final JFormattedTextField txtMonto = moneyField();
    private final JTextField txtMotivo = new JTextField();
    private final JTextField txtEfectivoDia = ro();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Hora", "Retiro", "Motivo"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int col) {
            return col == 1 ? BigDecimal.class : String.class;
        }
    };

    private final JTable tabla = new JTable(model);
    private final DateTimeFormatter HHmm = DateTimeFormatter.ofPattern("HH:mm");

    public PagoGastosPanel() {
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;

        txtFecha.setText(LocalDate.now().toString());
        addCell(form, c, 0, y, new JLabel("Fecha:"), 1, false);
        addCell(form, c, 1, y, txtFecha, 1, true); y++;

        addCell(form, c, 0, y, new JLabel("Efectivo disponible (hoy):"), 1, false);
        addCell(form, c, 1, y, txtEfectivoDia, 1, true); y++;

        addCell(form, c, 0, y, new JLabel("Monto a retirar:"), 1, false);
        addCell(form, c, 1, y, txtMonto, 1, true); y++;

        addCell(form, c, 0, y, new JLabel("Motivo:"), 1, false);
        addCell(form, c, 1, y, txtMotivo, 1, true); y++;

        JButton btGuardar = new JButton("Registrar retiro");
        btGuardar.addActionListener(_e -> guardar());
        addCell(form, c, 1, y, btGuardar, 1, false);

        add(form, BorderLayout.NORTH);

        tabla.setRowHeight(22);

        final NumberFormat nfCol = NumberFormat.getNumberInstance(Locale.US);
        nfCol.setMinimumFractionDigits(2);
        nfCol.setMaximumFractionDigits(2);

        javax.swing.table.DefaultTableCellRenderer moneyLeft =
                new javax.swing.table.DefaultTableCellRenderer() {
                    @Override protected void setValue(Object value) {
                        if (value instanceof Number) {
                            setText(nfCol.format(((Number) value).doubleValue()));
                        } else {
                            setText(value == null ? "" : value.toString());
                        }
                    }
                };
        moneyLeft.setHorizontalAlignment(SwingConstants.LEFT);
        tabla.getColumnModel().getColumn(1).setCellRenderer(moneyLeft);
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        cargarDia(LocalDate.now());
        actualizarDisponibleDeHoy();

        // Escucha actualizaciones globales (sincronización con CorteCajaPanel)
        EventBus.addCorteCajaListener(this::refrescarDespuesDeOperacion);
    }

    /** Actualiza el efectivo disponible desde la BD (usado en inicialización y refresco). */
    private void actualizarDisponibleDeHoy() {
        LocalDate hoy = LocalDate.now();
        try {
            CorteCajaDAO.Totales t = new CorteCajaDAO().leerTotalesDia(hoy);
            BigDecimal retiros = new PagoGastosDAO().totalRetirado(hoy);
            if (retiros == null) retiros = BigDecimal.ZERO;
            BigDecimal disponible = t.efectivo.subtract(retiros).max(BigDecimal.ZERO);
            txtEfectivoDia.setText(NF_DISPLAY.format(disponible));
        } catch (SQLException ex) {
            txtEfectivoDia.setText("0.00");
            System.err.println("efectivoDisponible(hoy): " + ex.getMessage());
        }
    }

    /** Registra el retiro y notifica a CorteCajaPanel. */
    private void guardar() {
        BigDecimal retiro = parseMoney(txtMonto);
        if (retiro == null || retiro.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this, "Captura un monto de retiro > 0.");
            txtMonto.requestFocus();
            return;
        }

        BigDecimal disponible = BigDecimal.ZERO;
        try {
            LocalDate hoy = LocalDate.now();
            CorteCajaDAO.Totales t = new CorteCajaDAO().leerTotalesDia(hoy);
            BigDecimal retiros = new PagoGastosDAO().totalRetirado(hoy);
            if (retiros == null) retiros = BigDecimal.ZERO;
            disponible = t.efectivo.subtract(retiros).max(BigDecimal.ZERO);
        } catch (SQLException ignore) {}

        if (retiro.compareTo(disponible) > 0) {
            JOptionPane.showMessageDialog(this,
                    "No puedes retirar más del efectivo disponible.\nDisponible: " + NF_DISPLAY.format(disponible));
            return;
        }

        String motivo = txtMotivo.getText().trim();
        BigDecimal efec = parseMoney(txtEfectivoDia);

        Object[] ops = {"SI", "NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Registrar retiro de $" + NF_DISPLAY.format(retiro) +
                        (motivo.isBlank() ? "" : " por: " + motivo) + "?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            new PagoGastosDAO().registrar(java.time.LocalDateTime.now(), efec, retiro, motivo);
            JOptionPane.showMessageDialog(this, "Retiro registrado correctamente.");
            txtMotivo.setText("");
            txtMonto.setValue(null);
            cargarDia(LocalDate.now());
            actualizarDisponibleDeHoy();

            // 🔥 Notificar a todos los paneles (CorteCajaPanel, etc.)
            EventBus.notificarOperacionFinalizada();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al registrar retiro: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Carga los retiros del día seleccionado. */
    private void cargarDia(LocalDate fecha) {
        model.setRowCount(0);
        try {
            for (PagoGastosDAO.Retiro r : new PagoGastosDAO().listarPorDia(fecha)) {
                model.addRow(new Object[]{
                        r.ts.format(HHmm),
                        r.monto,
                        r.motivo == null ? "" : r.motivo
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Refresca el efectivo disponible cuando se notifica una operación externa. */
    private void refrescarDespuesDeOperacion() {
        SwingUtilities.invokeLater(this::actualizarDisponibleDeHoy);
    }

    // ===== helpers UI =====
    private static JTextField ro() {
        JTextField t = new JTextField();
        t.setEditable(false);
        t.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        return t;
    }

    private static void addCell(JPanel p, GridBagConstraints c, int x, int y, JComponent comp, int span, boolean growX) {
        c.gridx = x; c.gridy = y; c.gridwidth = span; c.weightx = growX ? 1 : 0;
        p.add(comp, c); c.gridwidth = 1;
    }

    private static JFormattedTextField moneyField() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        NumberFormatter fmt = new NumberFormatter(nf);
        fmt.setValueClass(Double.class);
        fmt.setAllowsInvalid(false);
        fmt.setMinimum(0.00);
        JFormattedTextField f = new JFormattedTextField(fmt);
        f.setColumns(12);
        return f;
    }

    private static BigDecimal parseMoney(JTextField f) {
        try {
            String s = f.getText().trim().replace(",", "");
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}
