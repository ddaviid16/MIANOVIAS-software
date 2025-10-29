package Vista;

import Controlador.ReporteObsequiosDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;

import static javax.swing.SwingConstants.CENTER;

/** Panel de reporte de obsequios por venta. */
public class ReporteObsequiosPanel extends JPanel {

    // Tabla superior: ventas
    private final DefaultTableModel modelVentas = new DefaultTableModel(
            new String[]{"Nota","Folio","Teléfono","Fecha"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbVentas = new JTable(modelVentas);

    // Tabla inferior: obsequios normalizados
    private final DefaultTableModel modelObs = new DefaultTableModel(
            new String[]{"Código","Descripción","Tipo de operación"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return String.class; }
    };
    private final JTable tbObs = new JTable(modelObs);

    private final JLabel lbMsg = new JLabel(" ", SwingConstants.CENTER);

    // === NUEVO: contenedor con CardLayout para mostrar tabla o mensaje ===
    private final JPanel cardObs = new JPanel(new CardLayout());
    private final JScrollPane spObs = new JScrollPane(tbObs);

    public ReporteObsequiosPanel() {
        setLayout(new BorderLayout());

        tbVentas.setRowHeight(22);
        tbObs.setRowHeight(22);
        tbObs.setFillsViewportHeight(true); // queda mejor cuando hay pocas filas

        // --- NUEVO: armado del card inferior (tabla / mensaje) ---
        JPanel msgPanel = new JPanel(new BorderLayout());
        msgPanel.add(lbMsg, BorderLayout.CENTER);
        cardObs.setLayout(new CardLayout());
        cardObs.add(spObs, "TABLA");
        cardObs.add(msgPanel, "MSG");

        // Dividido en dos: arriba ventas, abajo obsequios
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tbVentas),
                cardObs); // <-- NUEVO: en lugar de new JScrollPane(tbObs)
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        // (Quitado) add(lbMsg, BorderLayout.SOUTH);  // ya no se usa abajo

        // Centrar texto en las tres columnas de obsequios
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(CENTER);
        tbObs.setAutoCreateColumnsFromModel(true);
        // aplicar cuando ya existe el column model
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < modelObs.getColumnCount(); i++) {
                tbObs.getColumnModel().getColumn(i).setCellRenderer(center);
            }
        });

        // Al seleccionar una venta, cargar sus obsequios
        tbVentas.getSelectionModel().addListSelectionListener(_e -> cargarObsequiosDeSeleccion());

        // Carga inicial: últimas 4 semanas
        cargarVentasRecientes();

        // Mostrar mensaje inicial dentro del recuadro
        mostrarMensaje(" ");
    }

    private void cargarVentasRecientes() {
        modelVentas.setRowCount(0);
        modelObs.setRowCount(0);
        mostrarMensaje(" ");
        try {
            LocalDate hoy = LocalDate.now();
            for (ReporteObsequiosDAO.VentaRow r :
                    new ReporteObsequiosDAO().listarVentas(hoy.minusDays(30), hoy)) {
                modelVentas.addRow(new Object[]{ r.numeroNota, r.folio, r.telefono, r.fecha });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarObsequiosDeSeleccion() {
        int row = tbVentas.getSelectedRow();
        modelObs.setRowCount(0);
        if (row < 0) { mostrarMensaje(" "); return; }

        int nota = Integer.parseInt(String.valueOf(modelVentas.getValueAt(row,0)));
        try {
            var lista = new ReporteObsequiosDAO().listarObsequiosDeNota(nota);
            if (lista.isEmpty()) {
                mostrarMensaje("<html><i>No se han incluido obsequios en esta venta.</i></html>");
            } else {
                for (ReporteObsequiosDAO.ObsItem r : lista) {
                    modelObs.addRow(new Object[]{ r.codigo, r.descripcion, r.tipoOperacion });
                }
                mostrarTabla();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            mostrarMensaje("<html><i>Error al cargar obsequios.</i></html>");
        }
    }

    // ==== NUEVO: helpers para cambiar la tarjeta ====
    private void mostrarTabla() {
        ((CardLayout) cardObs.getLayout()).show(cardObs, "TABLA");
    }
    private void mostrarMensaje(String html) {
        lbMsg.setText(html);
        ((CardLayout) cardObs.getLayout()).show(cardObs, "MSG");
    }
}
