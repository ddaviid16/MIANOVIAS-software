package Vista;

import Controlador.ReporteModistasDAO;
import Controlador.ExportadorCSV;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class ReporteModistasPanel extends JPanel {

    private YearMonth selectedYM = YearMonth.now();
    private LocalDate startDate  = selectedYM.atDay(1);
    private LocalDate endDate    = selectedYM.atEndOfMonth();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{
                    "ID", "Folio", "Cliente", "Fecha evento",
                    "Artículo", "Descripción",
                    "Precio", "Desc.%", "Precio final",
                    "Fecha registro", "Fecha entrega",
                    "Teléfono", "Observaciones"
            }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    private final JTable tb = new JTable(model);
    private final JLabel lbMes = new JLabel();

    public ReporteModistasPanel() {
        setLayout(new BorderLayout());

        tb.setRowHeight(22);
        JScrollPane sp = new JScrollPane(tb);
        add(sp, BorderLayout.CENTER);

        // Ocultar ID
        var colId = tb.getColumnModel().getColumn(0);
        colId.setMinWidth(0);
        colId.setMaxWidth(0);
        colId.setPreferredWidth(0);
        colId.setWidth(0);
        colId.setResizable(false);

        JButton btMes = new JButton("Seleccionar mes");
        btMes.addActionListener(_e -> {
            MonthPopup mp = new MonthPopup(selectedYM, ym -> {
                selectedYM = ym;
                startDate  = selectedYM.atDay(1);
                endDate    = selectedYM.atEndOfMonth();
                actualizarEtiquetaMes();
                cargarDatos();
            });
            mp.show(btMes, 0, btMes.getHeight());
        });

        JButton btExportar = new JButton("Exportar CSV");
        btExportar.addActionListener(_e -> {
            if (Utilidades.SeguridadUI.pedirYValidarClave(this)) {
                exportarCSV();
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(btMes);
        top.add(new JLabel("Mes:"));
        top.add(lbMes);
        top.add(btExportar);

        add(top, BorderLayout.NORTH);

        actualizarEtiquetaMes();
        cargarDatos();
    }

    private void actualizarEtiquetaMes() {
        lbMes.setText(selectedYM.toString());
    }

    private void cargarDatos() {
        model.setRowCount(0);
        try {
            ReporteModistasDAO dao = new ReporteModistasDAO();
            List<ReporteModistasDAO.ModistaRow> lista =
                    dao.listarPorRango(startDate, endDate);

            for (ReporteModistasDAO.ModistaRow r : lista) {
                double precio = (r.precio == null ? 0.0 : r.precio);
                double desc   = (r.descuento == null ? 0.0 : r.descuento);
                Double precioFinal = (r.precio == null)
                        ? null
                        : precio * (1.0 - desc / 100.0);

                model.addRow(new Object[]{
                        r.idManufactura,
                        safe(r.folio),
                        safe(r.cliente),
                        r.fechaEvento == null ? "" : r.fechaEvento.toString(),
                        safe(r.articulo),
                        safe(r.descripcion),
                        r.precio == null ? "" : String.format(java.util.Locale.US, "%.2f", r.precio),
                        r.descuento == null ? "" : String.format(java.util.Locale.US, "%.2f", r.descuento),
                        precioFinal == null ? "" : String.format(java.util.Locale.US, "%.2f", precioFinal),
                        r.fechaRegistro == null ? "" : r.fechaRegistro.toString(),
                        r.fechaEntrega == null ? "" : r.fechaEntrega.toString(),
                        safe(r.telefono),
                        safe(r.observaciones)
                });
            }

            if (lista.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No hay artículos de modistas para el mes seleccionado.",
                        "Sin datos", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar reporte de modistas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------- Exportar a CSV usando ExportadorCSV --------

    private void exportarCSV() {
        try {
            java.util.List<CsvRow> rows = new java.util.ArrayList<>();

            for (int i = 0; i < model.getRowCount(); i++) {
                CsvRow r = new CsvRow();
                r.folio         = String.valueOf(model.getValueAt(i, 1));
                r.cliente       = String.valueOf(model.getValueAt(i, 2));
                r.fechaEvento   = String.valueOf(model.getValueAt(i, 3));
                r.articulo      = String.valueOf(model.getValueAt(i, 4));
                r.descripcion   = String.valueOf(model.getValueAt(i, 5));
                r.precio        = toDouble(model.getValueAt(i, 6));
                r.descuento     = toDouble(model.getValueAt(i, 7));
                r.precioFinal   = toDouble(model.getValueAt(i, 8));
                r.fechaRegistro = String.valueOf(model.getValueAt(i, 9));
                r.fechaEntrega  = String.valueOf(model.getValueAt(i, 10));
                r.telefono      = String.valueOf(model.getValueAt(i, 11));
                r.observaciones = String.valueOf(model.getValueAt(i, 12));
                rows.add(r);
            }

            if (rows.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No hay datos para exportar.",
                        "Aviso", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String baseName = "reporte_modistas_" + startDate + "_a_" + endDate;

            ExportadorCSV.guardarListaCSV(
                    rows, baseName,
                    "folio", "cliente", "fechaEvento",
                    "articulo", "descripcion",
                    "precio", "descuento", "precioFinal",
                    "fechaRegistro", "fechaEntrega",
                    "telefono", "observaciones"
            );

            JOptionPane.showMessageDialog(this,
                    "CSV generado correctamente.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al exportar CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Clase que usa ExportadorCSV (por reflexión con getters)
    private static class CsvRow {
        public String folio;
        public String cliente;
        public String fechaEvento;
        public String articulo;
        public String descripcion;
        public Double precio;
        public Double descuento;
        public Double precioFinal;
        public String fechaRegistro;
        public String fechaEntrega;
        public String telefono;
        public String observaciones;

        public String getFolio() { return folio; }
        public String getCliente() { return cliente; }
        public String getFechaEvento() { return fechaEvento; }
        public String getArticulo() { return articulo; }
        public String getDescripcion() { return descripcion; }
        public Double getPrecio() { return precio; }
        public Double getDescuento() { return descuento; }
        public Double getPrecioFinal() { return precioFinal; }
        public String getFechaRegistro() { return fechaRegistro; }
        public String getFechaEntrega() { return fechaEntrega; }
        public String getTelefono() { return telefono; }
        public String getObservaciones() { return observaciones; }
    }

    // -------- Utilidades internas --------

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Double d) return d;
        try {
            String s = v.toString().trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return null;
        }
    }

    // ======= Popup de selección de MES (igual que en ReporteObsequios) =======
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

            lbYear.setFont(lbYear.getFont().deriveFont(
                    Font.BOLD, lbYear.getFont().getSize()+1f));
            header.add(prev, BorderLayout.WEST);
            header.add(lbYear, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(3,4,6,6));
            String[] meses = {"Ene","Feb","Mar","Abr","May","Jun",
                              "Jul","Ago","Sep","Oct","Nov","Dic"};
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
