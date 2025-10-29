package Vista;

import Controlador.FoliosDAO;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class PanelFoliosIniciales extends JPanel {

    // Mapeo para mostrar el nombre amigable en la tabla
    private static final Map<String,String> TIPO_NOMBRE = Map.of(
            "AB","Abono",
            "CN","Contado",
            "CR","Crédito",
            "DV","Devolución"
    );

    // Guardamos los registros originales para recuperar el código de tipo al guardar
    private final java.util.List<FoliosDAO.FolioRec> rows = new ArrayList<>();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Tipo", "Prefijo", "Último emitido", "Nuevo último emitido"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 1 || c == 3; } // prefijo y nuevo último
        @Override public Class<?> getColumnClass(int c) { return (c == 2 || c == 3) ? Integer.class : String.class; }
    };
    private final JTable table = new JTable(model);

    public PanelFoliosIniciales() {
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Asignación de Folios", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setBorder(BorderFactory.createEmptyBorder(18, 10, 8, 10));
        add(title, BorderLayout.NORTH);

        table.setRowHeight(24);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Asignación de Folios",
                TitledBorder.LEFT, TitledBorder.TOP));
        add(sp, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btRefrescar = new JButton("Refrescar");
        JButton btGuardar   = new JButton("Guardar");
        btRefrescar.addActionListener(_e -> cargar());
        btGuardar.addActionListener(_e -> guardar());
        south.add(btRefrescar);
        south.add(btGuardar);
        add(south, BorderLayout.SOUTH);

        cargar();
    }

    private void cargar() {
        try {
            model.setRowCount(0);
            rows.clear();
            List<FoliosDAO.FolioRec> lista = new FoliosDAO().listar();
            // orden estable por código de tipo
            lista.sort(Comparator.comparing(fr -> fr.tipo));
            rows.addAll(lista);

            for (FoliosDAO.FolioRec r : lista) {
                String nombre = TIPO_NOMBRE.getOrDefault(r.tipo, r.tipo);
                model.addRow(new Object[]{nombre, r.prefijo, r.ultimo, r.ultimo});
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar folios: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardar() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();

        // 1) Leer y normalizar
        List<FoliosDAO.FolioRec> nuevos = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            String pref = String.valueOf(model.getValueAt(i, 1)).trim();
            Integer ult = toInt(model.getValueAt(i, 3));
            if (pref.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El prefijo no puede estar vacío (fila " + (i+1) + ").",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ult == null || ult < 0) {
                JOptionPane.showMessageDialog(this, "Valor inválido en “Nuevo último emitido” (fila " + (i+1) + ").",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // normalizamos prefijo a MAYÚSCULAS sin espacios
            pref = pref.toUpperCase(Locale.ROOT);

            FoliosDAO.FolioRec base = rows.get(i); // contiene el código de tipo real
            FoliosDAO.FolioRec n = new FoliosDAO.FolioRec();
            n.tipo    = base.tipo;   // CN/CR/AB/DV (NO se edita)
            n.prefijo = pref;        // editable
            n.ultimo  = ult;         // editable
            nuevos.add(n);
        }

        // 2) Validaciones de prefijos: únicos y sin solaparse
        String err = validarPrefijos(nuevos);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err, "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 3) Confirmación
        Object[] ops = {"SI", "NO"};
        int resp = JOptionPane.showOptionDialog(this,
                "¿Actualizar prefijos y números de folio?\n" +
                        "Se usarán los valores de las columnas “Prefijo” y “Nuevo último emitido”.",
                "Confirmación", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
        if (resp != JOptionPane.YES_OPTION) return;

        // 4) Guardar
        try {
            new FoliosDAO().actualizarVarios(nuevos);
            JOptionPane.showMessageDialog(this, "Folios actualizados correctamente.");
            cargar();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        try { return Integer.valueOf(o.toString().trim()); }
        catch (Exception e) { return null; }
    }

    /** Prefijos no vacíos, únicos y sin solaparse (p.ej., no permitir "A" y "AB"). */
    private String validarPrefijos(List<FoliosDAO.FolioRec> lista) {
        // unicidad
        Set<String> set = new HashSet<>();
        for (FoliosDAO.FolioRec r : lista) {
            if (!set.add(r.prefijo))
                return "El prefijo '" + r.prefijo + "' está duplicado.";
        }
        // solapamiento: A vs AB, 15 vs 150, etc.
        for (int i=0;i<lista.size();i++) {
            for (int j=i+1;j<lista.size();j++) {
                String a = lista.get(i).prefijo;
                String b = lista.get(j).prefijo;
                if (a.startsWith(b) || b.startsWith(a)) {
                    return "Los prefijos '" + a + "' y '" + b + "' se solapan. Usa prefijos que no empiecen uno por el otro.";
                }
            }
        }
        return null;
    }
}
