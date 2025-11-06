package Vista;

import Controlador.PedidosDAO;
import Controlador.ExportadorCSV;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.List;
import java.sql.SQLException;
import java.util.*;

public class ArticulosAPedirPanel extends JPanel {

    // ===== Modelos de cada pestaña =====
    // Pendientes: SOLO editable la col 0 (En tienda)
    private final DefaultTableModel modelPend = new DefaultTableModel(
            new String[]{"En tienda","Folio","Fecha reg.","Artículo","Marca","Modelo","Talla","Color",
                    "Precio","Desc. %","Fecha entrega","Teléfono","Código art.","Status","#Nota(oculta)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) { case 0 -> Boolean.class; case 8,9 -> Double.class; default -> String.class; };
        }
    };

    // En tienda: NO editable el check, SÍ editable el Código art. (col 12)
    private final DefaultTableModel modelEn = new DefaultTableModel(
            new String[]{"En tienda","Folio","Fecha reg.","Artículo","Marca","Modelo","Talla","Color",
                    "Precio","Desc. %","Fecha entrega","Teléfono","Código art.","Status","#Nota(oculta)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 12; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) { case 0 -> Boolean.class; case 8,9 -> Double.class; default -> String.class; };
        }
    };

    private final JTable tbPend   = new JTable(modelPend);
    private final JTable tbEn     = new JTable(modelEn);
    private final JTabbedPane tabs = new JTabbedPane();

    // Estados originales para detectar cambios
    private final Map<Integer, Boolean> originalPend = new HashMap<>();     // numero_nota -> enTienda
    private final Map<Integer, Integer> originalCod  = new HashMap<>();     // numero_nota -> codigo_articulo

    public ArticulosAPedirPanel() {
        setLayout(new BorderLayout());

        JButton btCargar   = new JButton("Cargar");
        JButton btGuardar  = new JButton("Guardar cambios");
        JButton btExportar = new JButton("Exportar CSV");
        btCargar.addActionListener(_e -> cargar());
        btGuardar.addActionListener(_e -> guardarCambios());
        btExportar.addActionListener(_e -> exportarCsv());

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        north.add(btCargar); north.add(btGuardar); north.add(btExportar);
        add(north, BorderLayout.NORTH);

        configurarTabla(tbPend, true);
        configurarTabla(tbEn,   false);
        tbEn.getColumnModel().getColumn(12).setCellEditor(new DefaultCellEditor(new JTextField()) {
    @Override public boolean stopCellEditing() {
        String s = String.valueOf(getCellEditorValue()).trim();
        if (!s.isEmpty()) {
            try { Integer.parseInt(s); }
            catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(ArticulosAPedirPanel.this,
                        "El código debe ser numérico.", "Atención",
                        JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        return super.stopCellEditing();
    }
});


        tabs.addTab("Pedidos pendientes", new JScrollPane(tbPend));
        tabs.addTab("En tienda",          new JScrollPane(tbEn));
        add(tabs, BorderLayout.CENTER);

        cargar();
    }

    private void configurarTabla(JTable t, boolean pendientes) {
        t.setRowHeight(22);
        alignRight(t, 8); alignRight(t, 9);
        // Ocultar la última columna (#Nota) sólo en la vista
        TableColumnModel cm = t.getColumnModel();
        if (cm.getColumnCount() == 15) { // por si ya está configurada
            cm.removeColumn(cm.getColumn(14));
        }
    }

    private void cargar() {
        modelPend.setRowCount(0);
        modelEn.setRowCount(0);
        originalPend.clear();
        originalCod.clear();

        try {
            PedidosDAO dao = new PedidosDAO();

            // PESTAÑA PENDIENTES
            for (PedidosDAO.PedidoRow r : dao.listarPendientes()) {
                modelPend.addRow(new Object[]{
                        Boolean.FALSE,                 // aún no en tienda (por definición del filtro)
                        nz(r.folio),
                        str(r.fechaRegistro),
                        nz(r.articulo), nz(r.marca), nz(r.modelo), nz(r.talla), nz(r.color),
                        n(r.precio), n(r.descuento),
                        str(r.fechaEntrega),
                        nz(r.telefono),
                        r.codigoArticulo == null ? "" : String.valueOf(r.codigoArticulo),
                        nz(r.status),
                        String.valueOf(r.numeroNota)   // oculta
                });
                originalPend.put(r.numeroNota, false);
            }

            // PESTAÑA EN TIENDA
            for (PedidosDAO.PedidoRow r : dao.listarEnTienda()) {
                modelEn.addRow(new Object[]{
                        Boolean.TRUE,                  // ya en tienda
                        nz(r.folio),
                        str(r.fechaRegistro),
                        nz(r.articulo), nz(r.marca), nz(r.modelo), nz(r.talla), nz(r.color),
                        n(r.precio), n(r.descuento),
                        str(r.fechaEntrega),
                        nz(r.telefono),
                        r.codigoArticulo == null ? "" : String.valueOf(r.codigoArticulo), // editable
                        nz(r.status),
                        String.valueOf(r.numeroNota)   // oculta
                });
                originalCod.put(r.numeroNota, r.codigoArticulo);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error cargando pedidos: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

private void guardarCambios() {
    try {
        PedidosDAO dao = new PedidosDAO();
        int aplicados = 0;

        // Cambios en “Pendientes”: marcar en_tienda (checkbox)
        for (int i = 0; i < modelPend.getRowCount(); i++) {
            boolean nuevo = Boolean.TRUE.equals(modelPend.getValueAt(i, 0));
            int numero = Integer.parseInt(String.valueOf(modelPend.getValueAt(i, 14))); // #Nota (oculta)
            boolean anterior = originalPend.getOrDefault(numero, false);
            if (nuevo != anterior) {
                dao.setPedidoEnTienda(numero, nuevo);
                originalPend.put(numero, nuevo);
                aplicados++;
            }
        }

        // Cambios en “En tienda”: actualizar código_artículo (col 12)
        for (int i = 0; i < modelEn.getRowCount(); i++) {
            String codTxt = String.valueOf(modelEn.getValueAt(i, 12)).trim();
            Integer nuevoCod = codTxt.isEmpty() ? null : parseIntSafe(codTxt);
            if (!codTxt.isEmpty() && nuevoCod == null) {
                JOptionPane.showMessageDialog(this,
                        "El código debe ser numérico (fila " + (i + 1) + ").",
                        "Atención", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            int numero = Integer.parseInt(String.valueOf(modelEn.getValueAt(i, 14))); // #Nota (oculta)
            Integer anterior = originalCod.get(numero);

            if (!Objects.equals(nuevoCod, anterior)) {
                // <- usa el método que añadiste al DAO (Opción B)
                dao.actualizarCodigoArticulo(numero, nuevoCod);
                originalCod.put(numero, nuevoCod);
                aplicados++;
            }
        }

        JOptionPane.showMessageDialog(this,
                aplicados == 0 ? "No hay cambios por guardar." : ("Cambios guardados: " + aplicados));
        cargar();
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Error guardando cambios: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    // ===== Exportar CSV (elige pestaña) =====
    private void exportarCsv() {
        String[] opciones = {"Pedidos pendientes", "En tienda", "Cancelar"};
        int op = JOptionPane.showOptionDialog(this, "¿Qué deseas exportar?",
                "Exportar a CSV", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[0]);
        if (op == 2 || op == JOptionPane.CLOSED_OPTION) return;

        boolean pendientes = (op == 0);
        try {
            List<CsvRow> rows = new ArrayList<>();
            DefaultTableModel m = pendientes ? modelPend : modelEn;
            for (int i = 0; i < m.getRowCount(); i++) {
                CsvRow r = new CsvRow();
                r.en_tienda   = String.valueOf(m.getValueAt(i,0));
                r.folio       = String.valueOf(m.getValueAt(i,1));
                r.fecha_reg   = String.valueOf(m.getValueAt(i,2));
                r.articulo    = String.valueOf(m.getValueAt(i,3));
                r.marca       = String.valueOf(m.getValueAt(i,4));
                r.modelo      = String.valueOf(m.getValueAt(i,5));
                r.talla       = String.valueOf(m.getValueAt(i,6));
                r.color       = String.valueOf(m.getValueAt(i,7));
                r.precio      = String.valueOf(m.getValueAt(i,8));
                r.descuento   = String.valueOf(m.getValueAt(i,9));
                r.fecha_ent   = String.valueOf(m.getValueAt(i,10));
                r.telefono    = String.valueOf(m.getValueAt(i,11));
                r.codigo_art  = String.valueOf(m.getValueAt(i,12));
                r.status      = String.valueOf(m.getValueAt(i,13));
                r.numero_nota = String.valueOf(m.getValueAt(i,14)); // oculta (útil para cruces)
                rows.add(r);
            }

            String fname = pendientes ? "Pedidos_pendientes" : "Pedidos_en_tienda";
            ExportadorCSV.guardarListaCSV(
                    rows, fname,
                    "en_tienda","folio","fecha_reg","articulo","marca","modelo","talla","color",
                    "precio","descuento","fecha_ent","telefono","codigo_art","status","numero_nota"
            );
            JOptionPane.showMessageDialog(this, "CSV generado correctamente.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===== Helpers =====
    private static void alignRight(JTable t, int col) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        t.getColumnModel().getColumn(col).setCellRenderer(r);
    }
    private static String nz(String s){ return s==null? "" : s; }
    private static String str(java.util.Date d){ return d==null? "" : new java.sql.Date(d.getTime()).toString(); }
    private static Double n(java.math.BigDecimal b){ return b==null? null : b.doubleValue(); }
    private static Integer parseIntSafe(String s){
        try { return Integer.valueOf(s); } catch(Exception e){ return null; }
    }

    // POJO para ExportadorCSV (usa reflexión por nombre de campo)
    public static class CsvRow {
        public String en_tienda, folio, fecha_reg, articulo, marca, modelo, talla, color;
        public String precio, descuento, fecha_ent, telefono, codigo_art, status, numero_nota;
    }
}
