package Vista;

import Controlador.PedidosDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Panel para revisar pedidos y marcar si ya están en tienda. */
public class ArticulosAPedirPanel extends JPanel {

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{
                    "En tienda",     // 0 (checkbox)
                    "Nota",          // 1
                    "Fecha reg.",    // 2
                    "Artículo",      // 3
                    "Marca",         // 4
                    "Modelo",        // 5
                    "Talla",         // 6
                    "Color",         // 7
                    "Precio",        // 8
                    "Desc. %",       // 9
                    "Fecha entrega", // 10 
                    "Teléfono",      // 11
                    "Código art.",   // 12
                    "Status"         // 13
            }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 0; } // checkbox
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Boolean.class;
                case 8, 9 -> Double.class; // se renderiza numérico
                default -> String.class;
            };
        }
    };

    private final JTable tabla = new JTable(model);
    private final Map<Integer, Boolean> original = new HashMap<>(); // nota -> enTienda

    public ArticulosAPedirPanel() {
        setLayout(new BorderLayout());

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton btCargar  = new JButton("Cargar");
        JButton btGuardar = new JButton("Guardar cambios");
        btCargar.addActionListener(_e -> cargar());
        btGuardar.addActionListener(_e -> guardarCambios());
        north.add(btCargar);
        north.add(btGuardar);
        add(north, BorderLayout.NORTH);

        tabla.setRowHeight(22);
        alignRight(8); alignRight(9);
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        cargar(); // inicial
    }

    private void cargar() {
        model.setRowCount(0);
        original.clear();
        try {
            // Solo pendientes
            List<PedidosDAO.PedidoRow> lista = new PedidosDAO().listarPendientes();
            for (PedidosDAO.PedidoRow r : lista) {
                model.addRow(new Object[]{
                        r.enTienda,
                        String.valueOf(r.numeroNota),
                        str(r.fechaRegistro),
                        str(r.articulo),
                        str(r.marca),
                        str(r.modelo),
                        str(r.talla),
                        str(r.color),
                        r.precio == null ? null : r.precio.doubleValue(),
                        r.descuento == null ? null : r.descuento.doubleValue(),
                        str(r.fechaEntrega), // <-- AHORA MOSTRAMOS FECHA ENTREGA
                        str(r.telefono),
                        r.codigoArticulo == null ? "" : String.valueOf(r.codigoArticulo),
                        str(r.status)
                });
                original.put(r.numeroNota, r.enTienda);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error cargando pedidos: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarCambios() {
        try {
            PedidosDAO dao = new PedidosDAO();
            int n = model.getRowCount();
            int aplicados = 0;
            for (int i = 0; i < n; i++) {
                Object notaObj = model.getValueAt(i, 1);
                if (notaObj == null) continue;
                int nota = Integer.parseInt(notaObj.toString());
                boolean nuevo = Boolean.TRUE.equals(model.getValueAt(i, 0));
                Boolean anterior = original.get(nota);
                if (anterior == null || anterior != nuevo) {
                    dao.setPedidoEnTienda(nota, nuevo);
                    aplicados++;
                    original.put(nota, nuevo);
                }
            }
            JOptionPane.showMessageDialog(this,
                    (aplicados == 0 ? "No hay cambios por guardar." : ("Cambios guardados: " + aplicados)));
            cargar();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error guardando cambios: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===== helpers =====
    private void alignRight(int colIndex) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        tabla.getColumnModel().getColumn(colIndex).setCellRenderer(r);
    }

    private static String str(java.util.Date d) {
        return d == null ? "" : new java.sql.Date(d.getTime()).toString();
    }
    private static String str(String s) { return s == null ? "" : s; }
}
