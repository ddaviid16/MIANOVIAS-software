package Vista;

import Controlador.ReporteObsequiosDAO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import Conexion.Conecta;

import java.awt.*;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;


public class ReporteObsequiosPanel extends JPanel {

    
    private YearMonth selectedYM = YearMonth.now();
    // Tabla superior: ventas
    private final DefaultTableModel modelVentas = new DefaultTableModel(
            new String[]{"Nota", "Folio", "Teléfono", "Fecha"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbVentas = new JTable(modelVentas);

    // Tabla inferior: obsequios normalizados
    private final DefaultTableModel modelObs = new DefaultTableModel(
            new String[]{"Código", "Descripción", "Tipo de operación"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbObs = new JTable(modelObs);

    private final JLabel lbMsg = new JLabel(" ", SwingConstants.CENTER);

    // Contenedor con CardLayout para mostrar tabla o mensaje
    private final JPanel cardObs = new JPanel(new CardLayout());
    private final JScrollPane spObs = new JScrollPane(tbObs);

    // Selección de rango de fechas
    private LocalDate startDate = LocalDate.now().minusMonths(1);
    private LocalDate endDate = LocalDate.now();

    public ReporteObsequiosPanel() {
    setLayout(new BorderLayout());

    tbVentas.setRowHeight(22);
    tbObs.setRowHeight(22);
    tbObs.setFillsViewportHeight(true);

    // Armado del card inferior (tabla / mensaje)
    JPanel msgPanel = new JPanel(new BorderLayout());
    msgPanel.add(lbMsg, BorderLayout.CENTER);
    cardObs.setLayout(new CardLayout());
    cardObs.add(spObs, "TABLA");
    cardObs.add(msgPanel, "MSG");

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(tbVentas), cardObs);
    split.setResizeWeight(0.5);
    add(split, BorderLayout.CENTER);

    // Botón para seleccionar el rango de fechas
    JButton btFiltrarFechas = new JButton("Filtrar por fechas");  // Movido dentro del constructor
    btFiltrarFechas.addActionListener(_e -> {
        MonthPopup drp = new MonthPopup(selectedYM, ym -> {
            selectedYM = ym;
            cargarVentas();
        });
        drp.show(btFiltrarFechas, 0, btFiltrarFechas.getHeight());
    });

    JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panelBotones.add(btFiltrarFechas);

    // Botón para exportar CSV
    JButton btExportar = new JButton("Exportar CSV");
    btExportar.addActionListener(_e -> {
    if (Utilidades.SeguridadUI.pedirYValidarClave(this)) {
        exportarCSV();
    }
});

    panelBotones.add(btExportar);

    add(panelBotones, BorderLayout.NORTH);

    // Al seleccionar una venta, cargar sus obsequios
    tbVentas.getSelectionModel().addListSelectionListener(_e -> cargarObsequiosDeSeleccion());

    // Carga inicial
    cargarVentas();
}


    // Método para filtrar ventas por el mes seleccionado
private void cargarVentas() {
    modelVentas.setRowCount(0);
    modelObs.setRowCount(0);
    mostrarMensaje(" ");
    
    // Definir las fechas de inicio y fin del mes seleccionado
    startDate = selectedYM.atDay(1);
    endDate = selectedYM.atEndOfMonth();

    try {
        for (ReporteObsequiosDAO.VentaRow r :
                new ReporteObsequiosDAO().listarVentas(startDate, endDate)) {
            modelVentas.addRow(new Object[]{r.numeroNota, r.folio, r.telefono, r.fecha});
        }
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

// Modificar el botón de filtrado para mostrar el popup de selección de mes
    private void cargarObsequiosDeSeleccion() {
        int row = tbVentas.getSelectedRow();
        modelObs.setRowCount(0);
        if (row < 0) { mostrarMensaje(" "); return; }

        int nota = Integer.parseInt(String.valueOf(modelVentas.getValueAt(row, 0)));
        try {
            var lista = new ReporteObsequiosDAO().listarObsequiosDeNota(nota);
            if (lista.isEmpty()) {
                mostrarMensaje("<html><i>No se han incluido obsequios en esta venta.</i></html>");
            } else {
                for (ReporteObsequiosDAO.ObsItem r : lista) {
                    modelObs.addRow(new Object[]{r.codigo, r.descripcion, r.tipoOperacion});
                }
                mostrarTabla();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            mostrarMensaje("<html><i>Error al cargar obsequios.</i></html>");
        }
    }

    private void mostrarTabla() {
        ((CardLayout) cardObs.getLayout()).show(cardObs, "TABLA");
    }

    private void mostrarMensaje(String html) {
        lbMsg.setText(html);
        ((CardLayout) cardObs.getLayout()).show(cardObs, "MSG");
    }

    // Exportar CSV
private void exportarCSV() {
    try {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar reporte de obsequios");

        // Establecer el nombre predeterminado del archivo con la extensión .csv
        String defaultFileName = "reporte_obsequios_" + LocalDate.now().toString() + ".csv";
        fc.setSelectedFile(new java.io.File(defaultFileName));

        int res = fc.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        // Obtener la ruta seleccionada y asegurarse de que tenga la extensión .csv
        java.io.File file = fc.getSelectedFile();
        String filePath = file.getAbsolutePath();
        if (!filePath.endsWith(".csv")) {
            filePath += ".csv"; // Agregar .csv si no lo tiene
        }

        FileWriter out = new FileWriter(filePath);
        out.write("Fecha,Folio,Telefono,Obsequio1_cod,Obsequio1,Obsequio2_cod,Obsequio2,Obsequio3_cod,Obsequio3,Obsequio4_cod,Obsequio4,Obsequio5_cod,Obsequio5\n");

        // Recorrer ventas y sacar obsequios de cada nota
        ReporteObsequiosDAO dao = new ReporteObsequiosDAO();
        for (int i = 0; i < modelVentas.getRowCount(); i++) {
            int nota = (int) modelVentas.getValueAt(i, 0);
            String folio = String.valueOf(modelVentas.getValueAt(i, 1));
            String tel = String.valueOf(modelVentas.getValueAt(i, 2));
            String fecha = String.valueOf(modelVentas.getValueAt(i, 3));

            // Obtener los códigos y descripciones de los obsequios
            List<String> obs = obtenerObsequiosPlano(dao, nota);

            // Asegurarse de que la lista tenga 10 elementos, si faltan se agrega un string vacío
            while (obs.size() < 10) obs.add(""); // Si faltan columnas, se agregan vacíos

            // Escribir la línea con los datos de la venta y obsequios
            out.write(String.join(",", fecha, folio, tel,
                    obs.get(0), obs.get(1), obs.get(2), obs.get(3), obs.get(4),
                    obs.get(5), obs.get(6), obs.get(7), obs.get(8), obs.get(9)) + "\n");
        }
        out.close();
        JOptionPane.showMessageDialog(this, "Archivo exportado correctamente.");
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Error al exportar: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}



// Método para obtener los obsequios de la base de datos (código y descripción)
private List<String> obtenerObsequiosPlano(ReporteObsequiosDAO dao, int nota) throws SQLException {
    List<String> obs = new ArrayList<>();
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(
                 "SELECT obsequio1_cod, obsequio1, obsequio2_cod, obsequio2, obsequio3_cod, obsequio3, " +
                         "obsequio4_cod, obsequio4, obsequio5_cod, obsequio5 FROM obsequios WHERE numero_nota=?")) {
        ps.setInt(1, nota);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                // Agregar cada código y descripción de los obsequios
                for (int i = 1; i <= 5; i++) {
                    obs.add(rs.getString(i * 2 - 1) == null ? "" : rs.getString(i * 2 - 1)); // código
                    obs.add(rs.getString(i * 2) == null ? "" : rs.getString(i * 2)); // descripción
                }
            }
        }
    }
    return obs;
}
    //Necesito que este popup sea funcional para esta clase.
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
