// Vista/VentasPorVendedorPanel.java
package Vista;

import Controlador.VentasVendedorDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Consumer;

public class VentasPorVendedorPanel extends JPanel {

    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");

    private LocalDate selectedDate = LocalDate.now();
    private final JButton btFecha = new JButton();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"#", "Asesor", "Ventas"}, 0
    ) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0, 2 -> Integer.class;
                default -> String.class;
            };
        }
    };
    private final JTable tabla = new JTable(model);

    public VentasPorVendedorPanel() {
        setLayout(new BorderLayout());

        // --------- Barra superior ----------
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new JLabel("Fecha:"));

        actualizarTextoFecha();
        btFecha.addActionListener(_e -> {
            DayPopup dp = new DayPopup(selectedDate, d -> {
                selectedDate = d;
                actualizarTextoFecha();
            });
            dp.show(btFecha, 0, btFecha.getHeight());
        });
        top.add(btFecha);

        JButton btCargar = new JButton("Cargar");
        btCargar.addActionListener(_e -> cargar());
        top.add(btCargar);

        // <-- NUEVO: Guardar resumen del día en BD
        JButton btGuardar = new JButton("Guardar resumen (día)");
        btGuardar.addActionListener(_e -> guardarResumenDia());
        top.add(btGuardar);

        add(top, BorderLayout.NORTH);

        // --------- Tabla ----------
        tabla.setRowHeight(24);
        tabla.getColumnModel().getColumn(0).setMaxWidth(50);
        tabla.getColumnModel().getColumn(2).setMaxWidth(90);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    private void actualizarTextoFecha() {
        String mes = selectedDate.getMonth().getDisplayName(TextStyle.FULL, ES_MX);
        mes = mes.substring(0, 1).toUpperCase(ES_MX) + mes.substring(1);
        btFecha.setText(String.format("%02d %s %d", selectedDate.getDayOfMonth(), mes, selectedDate.getYear()));
    }

    private void cargar() {
        model.setRowCount(0);
        try {
            var rows = new VentasVendedorDAO().listarDia(selectedDate);
            int i = 1;
            for (var r : rows) {
                model.addRow(new Object[]{
                        i++,
                        r.asesorNombre,
                        r.ventas
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando ventas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // <-- NUEVO: persistir resumen del día seleccionado
    private void guardarResumenDia() {
        try {
            new VentasVendedorDAO().guardarResumenDiario(selectedDate);
            String mes = selectedDate.getMonth().getDisplayName(TextStyle.FULL, ES_MX);
            mes = mes.substring(0, 1).toUpperCase(ES_MX) + mes.substring(1);
            JOptionPane.showMessageDialog(this,
                    "Resumen guardado para el " +
                    String.format("%02d %s %d", selectedDate.getDayOfMonth(), mes, selectedDate.getYear()) + ".",
                    "OK", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar resumen: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ========= Calendario emergente de 1 día =========
    static class DayPopup extends JPopupMenu {
        private YearMonth ym;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        private final Consumer<LocalDate> onPick;

        DayPopup(LocalDate initial, Consumer<LocalDate> onPick) {
            LocalDate base = initial == null ? LocalDate.now() : initial;
            this.ym = YearMonth.of(base.getYear(), base.getMonth());
            this.onPick = onPick;

            setLayout(new BorderLayout(6, 6));
            JPanel header = new JPanel(new BorderLayout());

            JButton prev = new JButton("◀");
            JButton next = new JButton("▶");
            prev.addActionListener(_e -> { ym = ym.minusMonths(1); refresh(); });
            next.addActionListener(_e -> { ym = ym.plusMonths(1);  refresh(); });

            title.setFont(title.getFont().deriveFont(Font.BOLD));
            header.add(prev, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);

            add(header, BorderLayout.NORTH);

            JPanel dow = new JPanel(new GridLayout(1, 7, 4, 4));
            DayOfWeek[] order = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};
            for (DayOfWeek d : order) {
                JLabel l = new JLabel(d.getDisplayName(TextStyle.SHORT, ES_MX), SwingConstants.CENTER);
                l.setFont(l.getFont().deriveFont(Font.PLAIN));
                dow.add(l);
            }
            add(dow, BorderLayout.CENTER);
            add(grid, BorderLayout.SOUTH);

            refresh();
        }

        private void refresh() {
            String mes = ym.getMonth().getDisplayName(TextStyle.FULL, ES_MX);
            mes = mes.substring(0, 1).toUpperCase(ES_MX) + mes.substring(1);
            title.setText(mes + " " + ym.getYear());

            grid.removeAll();

            int firstDow = ym.atDay(1).getDayOfWeek().getValue(); // 1..7 (lun..dom)
            int blanks = (firstDow == 7) ? 6 : firstDow - 1;
            for (int i = 0; i < blanks; i++) grid.add(new JLabel(""));

            int len = ym.lengthOfMonth();
            for (int d = 1; d <= len; d++) {
                int day = d;
                JButton b = new JButton(String.valueOf(day));
                b.addActionListener(_e -> {
                    LocalDate pick = ym.atDay(day);
                    if (onPick != null) onPick.accept(pick);
                    setVisible(false);
                });
                grid.add(b);
            }

            pack();
            revalidate();
            repaint();
        }
    }

    // Main de prueba visual
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Ventas por vendedor (día)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(720, 480);
            f.setLocationRelativeTo(null);
            f.add(new VentasPorVendedorPanel(), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
