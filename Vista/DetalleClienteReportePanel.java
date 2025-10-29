package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Detalle de cliente:
 *  - Arriba: campo Teléfono + botón Buscar.
 *  - Centro: Split vertical con:
 *       (a) Tabla de "Datos del cliente" (todas las columnas que existan en la tabla Clientes).
 *       (b) Tabla de "Operaciones del cliente" (todas las notas registradas).
 *  - NO crea botón "Volver" (usa el global del contenedor).
 */
public class DetalleClienteReportePanel extends JPanel {

    private final JTextField txtTel = new JTextField();
    private final JButton btBuscar = new JButton("Buscar");

    // ===== Datos del cliente (clave/valor) =====
    private final DefaultTableModel modelInfo = new DefaultTableModel(
            new String[]{"Campo", "Valor"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbInfo = new JTable(modelInfo);

    // ===== Operaciones del cliente =====
    private final DefaultTableModel modelNotas = new DefaultTableModel(
            new String[]{"# Nota", "Tipo", "Folio", "Fecha", "Total", "Saldo", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Integer.class;
                case 3 -> String.class; // fecha formateada
                case 4, 5 -> Double.class;
                default -> String.class;
            };
        }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DetalleClienteReportePanel() {
        setLayout(new BorderLayout());

        // --------- Encabezado (sin título “Menú”) ----------
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        txtTel.setColumns(22);
        north.add(new JLabel("Teléfono:"));
        north.add(txtTel);
        north.add(btBuscar);
        add(north, BorderLayout.NORTH);

        // --------- Centro: Split con info del cliente + notas ----------
        tbInfo.setRowHeight(22);
        JScrollPane spInfo = new JScrollPane(tbInfo);
        spInfo.setBorder(BorderFactory.createTitledBorder("Datos del cliente"));

        tbNotas.setRowHeight(22);
        JScrollPane spNotas = new JScrollPane(tbNotas);
        spNotas.setBorder(BorderFactory.createTitledBorder("Operaciones del cliente"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spInfo, spNotas);
        split.setResizeWeight(0.38); // deja más espacio al historial si crece
        add(split, BorderLayout.CENTER);

        // Buscar
        btBuscar.addActionListener(_e -> buscar());
        txtTel.addActionListener(_e -> buscar());
    }

    private void limpiar() {
        modelInfo.setRowCount(0);
        modelNotas.setRowCount(0);
    }

    private void buscar() {
        String tel = txtTel.getText().trim();
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Captura el teléfono del cliente.", "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        limpiar();

        // === 1) Datos completos del cliente (todas las columnas) ===
        try {
            Map<String,String> detalle = new clienteDAO().detalleGenericoPorTelefono(tel);
            if (detalle == null) {
                modelInfo.addRow(new Object[]{"—", "Cliente no encontrado"});
            } else {
                for (Map.Entry<String,String> e : detalle.entrySet()) {
                    modelInfo.addRow(new Object[]{ prettify(e.getKey()), e.getValue() });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // === 2) Historial de operaciones (todas las notas del cliente) ===
        try {
            List<NotasDAO.NotaHistRow> list = new NotasDAO().historialPorTelefono(tel);
            for (NotasDAO.NotaHistRow r : list) {
                String f = r.fechaRegistro == null ? "" : r.fechaRegistro.toLocalDateTime().format(DF);
                modelNotas.addRow(new Object[]{
                        r.numeroNota, r.tipo, nullToEmpty(r.folio), f,
                        r.total == null ? 0.0 : r.total,
                        r.saldo == null ? 0.0 : r.saldo,
                        nullToEmpty(r.status)
                });
            }
            if (list.isEmpty()) {
                modelNotas.addRow(new Object[]{"—", "—", "—", "—", 0.0, 0.0, "Sin registros"});
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error cargando operaciones: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- helpers ----------
    private static String nullToEmpty(String s){ return s==null? "" : s; }

    private static String prettify(String col) {
        // "fecha_evento" -> "Fecha evento", "rfc" -> "Rfc"
        String s = col == null ? "" : col.trim().replace('_',' ');
        if (s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
