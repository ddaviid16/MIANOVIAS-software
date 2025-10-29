package Vista;

import Controlador.PedidosDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntregasVestidosPanel extends JPanel {

    private final JButton btMes = new JButton();
    private YearMonth selectedYM = YearMonth.now();
    private static final java.util.Locale ES_MX = java.util.Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final JTabbedPane tabs = new JTabbedPane();

    // Pendientes (con checkbox)
    private final DefaultTableModel modelPend = new DefaultTableModel(
            new String[]{"Folio","Artículo","Marca","Modelo","Talla","Color","Fecha entrega","Entregado","_num","_det","_src"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c==7; }
        @Override public Class<?> getColumnClass(int c) { return c==7 ? Boolean.class : Object.class; }
    };
    private final JTable tbPend = new JTable(modelPend);

    // Entregados (solo lectura) -> precio/desc son números
    private final DefaultTableModel modelEnt = new DefaultTableModel(
            new String[]{"Folio","Artículo","Marca","Modelo","Talla","Color","Fecha entrega"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return (c>=7 && c<=8) ? Double.class : Object.class; }
    };
    private final JTable tbEnt = new JTable(modelEnt);

    private final Map<String, Boolean> original = new HashMap<>();

    public EntregasVestidosPanel() {
        setLayout(new BorderLayout());

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        north.add(new JLabel("Mes:"));
        actualizarTextoMes();
        btMes.addActionListener(_e -> {
            MonthPopup mp = new MonthPopup(selectedYM, ym -> {
                selectedYM = ym; actualizarTextoMes(); cargar();
            });
            mp.show(btMes, 0, btMes.getHeight());
        });
        north.add(btMes);

        JButton btCargar = new JButton("Cargar");
        btCargar.addActionListener(_e -> cargar());
        north.add(btCargar);

        JButton btGuardar = new JButton("Guardar entregas");
        btGuardar.addActionListener(_e -> guardarEntregas());
        north.add(btGuardar);

        add(north, BorderLayout.NORTH);

        // Tabs
        tbPend.setRowHeight(22);
        JScrollPane spPend = new JScrollPane(tbPend);
        ocultarClaves(tbPend);
        tabs.addTab("Pendientes", spPend);

        tbEnt.setRowHeight(22);
        JScrollPane spEnt = new JScrollPane(tbEnt);
        tabs.addTab("Entregados", spEnt);

        add(tabs, BorderLayout.CENTER);

        cargar();
    }

    private void ocultarClaves(JTable t) {
        TableColumnModel cm = t.getColumnModel();
        for (int k=0; k<3; k++) {
            int last = cm.getColumnCount()-1;
            if (last >= 0) cm.removeColumn(cm.getColumn(last));
        }
    }

    private void cargar() {
        modelPend.setRowCount(0);
        modelEnt.setRowCount(0);
        original.clear();

        LocalDate ini = selectedYM.atDay(1);
        LocalDate fin = ini.plusMonths(1);

        try {
            PedidosDAO dao = new PedidosDAO();

            // -------- Pendientes (no entregados) --------
            List<PedidosDAO.EntregaVestidoRow> pend = dao.listarEntregasVestidos(ini, fin);
            for (PedidosDAO.EntregaVestidoRow r : pend) {
                if (Boolean.TRUE.equals(r.enTienda)) continue; // solo no entregados
                LocalDate fEnt = toLocalDate(r.fechaEntrega);
                if (fEnt == null) continue;

                Object[] row = new Object[]{
                        (r.folio == null ? String.valueOf(r.numeroNota) : r.folio),
                        safe(r.articulo),
                        safe(r.marca),
                        safe(r.modelo),
                        safe(r.talla),
                        safe(r.color),
                        fEnt.format(MX),
                        false,
                        r.numeroNota,
                        r.detId,
                        r.fromPedidos ? "PD" : "ND"
                };
                modelPend.addRow(row);
                original.put(keyOf(row), false);
            }

            // -------- Entregados (desde entregas_vestidos) --------
            List<PedidosDAO.EntregaVestidoRow> ents = dao.listarEntregadosVestidos(ini, fin);
            for (PedidosDAO.EntregaVestidoRow r : ents) {
                LocalDate fEnt = toLocalDate(r.fechaEntrega);
                if (fEnt == null) continue;


                modelEnt.addRow(new Object[]{
                        r.numeroNota,
                        safe(r.articulo),
                        safe(r.marca),
                        safe(r.modelo),
                        safe(r.talla),
                        safe(r.color),
                        fEnt.format(MX)
                });
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar entregas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarEntregas() {
        StringBuilder resumen = new StringBuilder();
        java.util.List<int[]> cambios = new java.util.ArrayList<>();

        for (int i=0; i<modelPend.getRowCount(); i++) {
            boolean nuevo = Boolean.TRUE.equals(modelPend.getValueAt(i, 7));
            String k = keyOfRow(i);
            boolean antes = original.getOrDefault(k, false);
            if (nuevo && !antes) {
                String linea = "Nota " + modelPend.getValueAt(i,0) + " - "
                        + modelPend.getValueAt(i,4) + " T" + modelPend.getValueAt(i,5)
                        + " " + modelPend.getValueAt(i,6)
                        + " (" + modelPend.getValueAt(i,1) + ") - Entrega " + modelPend.getValueAt(i,7);
                resumen.append("• ").append(linea).append("\n");
                cambios.add(new int[]{ i });
            }
        }

        if (cambios.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay cambios por guardar.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de marcar los siguientes artículos como entregados?\n\n" + resumen,
                "Confirmar entregas", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            PedidosDAO dao = new PedidosDAO();
            for (int[] idx : cambios) {
                int i = idx[0];
                Integer numeroNota = parseInt(modelPend.getValueAt(i, 8));
                Integer detId      = parseInt(modelPend.getValueAt(i, 9));
                String src         = String.valueOf(modelPend.getValueAt(i, 10));
                if (numeroNota == null) continue;

                if ("PD".equalsIgnoreCase(src)) dao.setPedidoEnTienda(numeroNota, true);
                else if (detId != null)        dao.setEntregaEnTienda(detId, numeroNota, true);
            }
            JOptionPane.showMessageDialog(this, "Entregas guardadas correctamente.");
            cargar();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar entregas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarTextoMes() {
        String mes = selectedYM.getMonth().getDisplayName(java.time.format.TextStyle.FULL, ES_MX);
        mes = mes.substring(0,1).toUpperCase(ES_MX) + mes.substring(1);
        btMes.setText(mes + " " + selectedYM.getYear());
    }

    private static LocalDate toLocalDate(java.util.Date d) {
        if (d == null) return null;
        if (d instanceof java.sql.Date) return ((java.sql.Date) d).toLocalDate();
        return java.time.Instant.ofEpochMilli(d.getTime())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
    }
    private static Integer parseInt(Object v){
        try { return v==null? null : Integer.parseInt(v.toString()); } catch(Exception e){ return null; }
    }
    private static String safe(String s){ return s==null?"":s; }

    private static String keyOf(Object[] row) {
        Object n = row[8], d = row[9], s = row[10];
        return (n==null?"":n)+"|"+(d==null?"":d)+"|"+(s==null?"":s);
    }
    private String keyOfRow(int i){
        Object n = modelPend.getValueAt(i,8);
        Object d = modelPend.getValueAt(i,9);
        Object s = modelPend.getValueAt(i,10);
        return (n==null?"":n)+"|"+(d==null?"":d)+"|"+(s==null?"":s);
    }

    // ======= Popup de selección de MES =======
    static class MonthPopup extends JPopupMenu {
        private int year;
        private final JLabel lbYear = new JLabel("", SwingConstants.CENTER);
        private final java.util.function.Consumer<YearMonth> onPick;
        MonthPopup(YearMonth initial, java.util.function.Consumer<YearMonth> onPick) {
            this.year = (initial == null ? YearMonth.now() : initial).getYear();
            this.onPick = onPick;

            setLayout(new BorderLayout(6,6));
            JPanel header = new JPanel(new BorderLayout());
            JButton prev = new JButton("◀"), next = new JButton("▶");
            prev.addActionListener(_e -> { year--; refreshYear(); });
            next.addActionListener(_e -> { year++; refreshYear(); });

            lbYear.setFont(lbYear.getFont().deriveFont(Font.BOLD, lbYear.getFont().getSize()+1f));
            header.add(prev, BorderLayout.WEST);
            header.add(lbYear, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(3,4,6,6));
            String[] meses = {"Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"};
            for (int m = 1; m <= 12; m++) {
                final int mm = m;
                JButton b = new JButton(meses[m-1]);
                b.addActionListener(_e -> {
                    if (onPick != null) onPick.accept(YearMonth.of(year, mm));
                    setVisible(false);
                });
                grid.add(b);
            }
            add(grid, BorderLayout.CENTER);

            refreshYear();
        }
        private void refreshYear() { lbYear.setText(String.valueOf(year)); pack(); }
    }
}
