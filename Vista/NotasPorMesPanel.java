package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Controlador.FormasPagoDAO;
import Controlador.ExportadorCSV;
import Controlador.FacturaDatosDAO;
import Conexion.Conecta;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class NotasPorMesPanel extends JPanel {

    private final JLabel lbAbonaA = new JLabel(" ");

    private final JButton btMes = new JButton();
    private final JButton btCargar = new JButton("Cargar");
    private final JButton btExportar = new JButton("Exportar CSV");

    private YearMonth selectedYM = YearMonth.now();
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final java.util.Locale ES_MX = java.util.Locale.forLanguageTag("es-MX");

    // -------- Notas (arriba)
    private final DefaultTableModel modelNotas = new DefaultTableModel(
            new String[]{"# Nota","Tipo","Folio","Fecha","Total","Saldo","Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Integer.class;
                case 4,5 -> Double.class;
                default -> String.class;
            };
        }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    // --- NUEVO: cache para acceder al NotaResumen seleccionado ---
private final Map<Integer, NotasDAO.NotaResumen> cacheNotas = new HashMap<>();

// --- NUEVO: tabla Factura (clave/valor) ---
private final DefaultTableModel modelFactura = new DefaultTableModel(
        new String[]{"Campo", "Valor"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
};
private final JTable tbFactura = new JTable(modelFactura);


    // -------- Tabs (abajo)
    // Detalle de artículos
    private final DefaultTableModel modelDet = new DefaultTableModel(
            new String[]{"Código","Artículo","Marca","Modelo","Talla","Color","Precio","Desc","Subtotal"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return (c>=6 && c<=8)? Double.class : Object.class; }
    };
    private final JTable tbDet = new JTable(modelDet);

    // Pagos (resumen) + DVs usados como pago
    private final DefaultTableModel modelPago = new DefaultTableModel(
            new String[]{"Efectivo","T. Crédito","T. Débito","AmEx","Transfer","Depósito","Devolución","Ref. DV"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return c==7 ? String.class : Double.class; }
    };
    private final JTable tbPago = new JTable(modelPago);

    private final DefaultTableModel modelDV = new DefaultTableModel(
            new String[]{"# Nota DV","Monto usado"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return c==0 ? Integer.class : Double.class; }
    };
    private final JTable tbDV = new JTable(modelDV);

    // Cliente (clave/valor)
    private final DefaultTableModel modelCliente = new DefaultTableModel(
            new String[]{"Campo","Valor"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbCliente = new JTable(modelCliente);

    private final JTabbedPane tabs = new JTabbedPane();
    


    public NotasPorMesPanel() {
        setLayout(new BorderLayout());

        // ---------- Header
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        actualizarTextoMes();
        btMes.addActionListener(_e -> {
            MonthPopup mp = new MonthPopup(selectedYM, ym -> {
                selectedYM = ym; actualizarTextoMes();
            });
            mp.show(btMes, 0, btMes.getHeight());
        });
        north.add(new JLabel("Mes:"));
        north.add(btMes);

        btCargar.addActionListener(_e -> cargar());
        north.add(btCargar);

        btExportar.addActionListener(_e -> {
        if (Utilidades.SeguridadUI.pedirYValidarClave(this)) {
            exportarCSV();
        }
    });

        north.add(btExportar);

        add(north, BorderLayout.NORTH);

        // ---------- Center
        tbNotas.setRowHeight(22);
        tbDet.setRowHeight(22);
        tbPago.setRowHeight(22);
        tbDV.setRowHeight(22);
        tbCliente.setRowHeight(22);

        JScrollPane spNotas = new JScrollPane(tbNotas);
        spNotas.setBorder(BorderFactory.createTitledBorder("Notas del mes"));

        // Ocultar la columna # Nota solo en la vista (se seguirá leyendo desde el modelo)
        ocultarColumnaVista(tbNotas, 0);

        // ----- Pestaña Pagos con rótulo "Abona a:"
        JPanel pagosPanel = new JPanel(new BorderLayout(6,6));

        // Estética del label
        lbAbonaA.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));
        lbAbonaA.setFont(lbAbonaA.getFont().deriveFont(Font.BOLD));

        // Contenedor vertical: Label arriba + tabla de pagos
        JPanel pagosTop = new JPanel(new BorderLayout(4,4));
        pagosTop.add(lbAbonaA, BorderLayout.NORTH);

        JScrollPane spPagos = new JScrollPane(tbPago);
        // (tamaño cómodo para que no colapse en NORTH)
        spPagos.setPreferredSize(new Dimension(10, tbPago.getRowHeight() * 3 + 32));
        pagosTop.add(spPagos, BorderLayout.CENTER);

        pagosPanel.add(pagosTop, BorderLayout.NORTH);

        tabs.addTab("Detalle", new JScrollPane(tbDet));
        tabs.addTab("Pagos", pagosPanel);
        tabs.addTab("Cliente", new JScrollPane(tbCliente));
        tbFactura.setRowHeight(22);
        tabs.addTab("Factura", new JScrollPane(tbFactura));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spNotas, tabs);
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);

        // Selección de nota
        tbNotas.getSelectionModel().addListSelectionListener(_e -> {
            if (_e.getValueIsAdjusting()) return;
            Integer num = getNumeroNotaSeleccionada();
            if (num == null) {
                limpiarDetallePagosCliente();
                return;
            }
            cargarDetalleYPagos(num);
            cargarCliente(num);
            cargarFactura(num);
        });

        // Primera carga
        cargar();
    }

    // ====== Cargas ======
    private void cargar() {
        modelNotas.setRowCount(0);
        limpiarDetallePagosCliente();
        cacheNotas.clear();


        LocalDate ini = selectedYM.atDay(1);
        LocalDate fin = ini.plusMonths(1);

        try {
            NotasDAO dao = new NotasDAO();
            List<NotasDAO.NotaResumen> list = dao.listarNotasPorRangoResumen(ini, fin);
            for (NotasDAO.NotaResumen r : list) {
    
    cacheNotas.put(r.numero, r);

    modelNotas.addRow(new Object[]{
            r.numero, r.tipo, safe(r.folio),
            r.fecha == null ? "" : r.fecha.format(DF),
            n(r.total), n(r.saldo), safe(r.status),
    });
}
            if (list.isEmpty()) {
                modelNotas.addRow(new Object[]{"—","—","—","—",0.0,0.0,"Sin registros"});
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error cargando notas del mes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDetalleYPagos(int numeroNota) {
        // Detalle
        modelDet.setRowCount(0);
        try {
            NotasDAO dao = new NotasDAO();
            var det = dao.listarDetalleDeNota(numeroNota);
            for (var d : det) {
                modelDet.addRow(new Object[]{
                        d.getCodigoArticulo(),
                        safe(d.getArticulo()),
                        safe(d.getMarca()),
                        safe(d.getModelo()),
                        safe(d.getTalla()),
                        safe(d.getColor()),
                        n(d.getPrecio()),
                        n(d.getDescuento()),
                        n(d.getSubtotal())
                });
            }
            if (det.isEmpty())
                modelDet.addRow(new Object[]{"—","—","—","—","—","—",0.0,0.0,0.0});
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error cargando detalle de nota: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Pagos
        modelPago.setRowCount(0);
        try {
            FormasPagoDAO fdao = new FormasPagoDAO();
            FormasPagoDAO.FormasPagoRow fp = fdao.obtenerPorNota(numeroNota);
            if (fp != null) {
                modelPago.addRow(new Object[]{
                        n(fp.efectivo), n(fp.tarjetaCredito), n(fp.tarjetaDebito),
                        n(fp.americanExpress), n(fp.transferencia),
                        n(fp.deposito), n(fp.devolucion),
                        (fp.referenciaDV==null ? "" : fp.referenciaDV)
                });
            } else {
                modelPago.addRow(new Object[]{0.0,0.0,0.0,0.0,0.0,0.0,0.0,""});
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error cargando pagos de la nota: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        // ---- Rótulo "Abona a:" (solo para AB)
        String tipoSel = getTipoNotaSeleccionada();
        if ("AB".equalsIgnoreCase(tipoSel)) {
            String folioCR = obtenerFolioCreditoAbonado(numeroNota);
            folioCR = (folioCR == null) ? "" : folioCR.trim();
            lbAbonaA.setText(folioCR.isBlank() ? "Abona a: (sin relación)" : "Abona a: " + folioCR);
        } else {
            lbAbonaA.setText(" ");
        }
    }

    private void cargarCliente(int numeroNota) {
        modelCliente.setRowCount(0);
        try {
            String tel = obtenerTelefonoPorNumeroNota(numeroNota);
            if (tel == null || tel.isBlank()) {
                modelCliente.addRow(new Object[]{"—", "Cliente no encontrado"});
                return;
            }
            Map<String,String> detalle = new clienteDAO().detalleGenericoPorTelefono(tel);
            if (detalle == null || detalle.isEmpty()) {
                modelCliente.addRow(new Object[]{"Teléfono", tel});
                modelCliente.addRow(new Object[]{"—", "Cliente no encontrado"});
                return;
            }
            for (Map.Entry<String,String> e : detalle.entrySet()) {
                modelCliente.addRow(new Object[]{ prettify(e.getKey()), e.getValue() });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error cargando datos del cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarFactura(int numeroNota) {
    modelFactura.setRowCount(0);

    // 2) Datos capturados para facturar desde Factura_Datos
    try {
        Controlador.FacturaDatosDAO dao = new Controlador.FacturaDatosDAO();
        Controlador.FacturaDatosDAO.Row fd = dao.obtenerPorNota(numeroNota);

        if (fd != null) {
            String personaFmt =
                    (fd.persona == null) ? "" :
                    (fd.persona.equalsIgnoreCase("PM") ? "Persona moral" :
                     fd.persona.equalsIgnoreCase("PF") ? "Persona física" : fd.persona);

            addKV(modelFactura, "Persona", personaFmt);
            addKV(modelFactura, "RFC", n(fd.rfc));
            addKV(modelFactura, "Régimen fiscal", n(fd.regimen));
            addKV(modelFactura, "Uso del CFDI", n(fd.usoCfdi));
            addKV(modelFactura, "Correo", n(fd.correo));

            // (Opcional) Timestamps si quieres verlos:
            if (fd.createdAt != null) addKV(modelFactura, "Capturado", fd.createdAt.toString());
            if (fd.updatedAt != null) addKV(modelFactura, "Actualizado", fd.updatedAt.toString());
        } else {
            // Sin captura previa
            addKV(modelFactura, "Persona", "");
            addKV(modelFactura, "RFC", "");
            addKV(modelFactura, "Régimen fiscal", "");
            addKV(modelFactura, "Uso del CFDI", "");
            addKV(modelFactura, "Correo", "");
        }
    } catch (Exception e) {
        // Si prefieres visible:
        // JOptionPane.showMessageDialog(this, "Factura_Datos: " + e.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
    }
}

private static void addKV(DefaultTableModel m, String k, String v) {
    m.addRow(new Object[]{k, (v == null ? "" : v)});
}

private static String n(String s) { return (s == null) ? "" : s; }


    // ====== Exportar CSV ======
    private void exportarCSV() {
        try {
            List<CsvRow> rows = new ArrayList<>();
            NotasDAO ndao = new NotasDAO();
            FormasPagoDAO fdao = new FormasPagoDAO();
            FacturaDatosDAO fddao = new FacturaDatosDAO(); // <-- NUEVO

            for (int vr = 0; vr < tbNotas.getRowCount(); vr++) {
                int mr = tbNotas.convertRowIndexToModel(vr);
                Object numObj = modelNotas.getValueAt(mr, 0);
                if (!(numObj instanceof Integer)) continue;

                int numero   = (Integer) numObj;
                String tipo  = String.valueOf(modelNotas.getValueAt(mr, 1));
                String folio = String.valueOf(modelNotas.getValueAt(mr, 2));
                String fecha = String.valueOf(modelNotas.getValueAt(mr, 3));
                String status= String.valueOf(modelNotas.getValueAt(mr, 6));
                Double saldo = 0.0;
                try { saldo = (Double) modelNotas.getValueAt(mr, 5); } catch (Exception ignore) {}

                // Cliente
                String tel = obtenerTelefonoPorNumeroNota(numero);
                String nombre = "";
                try {
                    var cr = new clienteDAO().buscarResumenPorTelefono(tel);
                    if (cr != null && cr.getNombreCompleto()!=null) nombre = cr.getNombreCompleto();
                } catch (Exception ignore) {}

                 String rfc = "", regimen = "", usoCFDI = "", correo = "";
            try {
                FacturaDatosDAO.Row fd = fddao.obtenerPorNota(numero);
                if (fd != null) {
                    rfc     = safe(fd.rfc);
                    regimen = safe(fd.regimen);
                    usoCFDI = safe(fd.usoCfdi);
                    correo  = safe(fd.correo);
                }
            } catch (Exception ignore) {}

                String folioCreditoAbonado = "";
                if ("AB".equalsIgnoreCase(tipo)) {
                    folioCreditoAbonado = obtenerFolioCreditoAbonado(numero); // ← helper de abajo
                }
                // Pagos
                Double pef=0.0, pcr=0.0, pdb=0.0, pam=0.0, ptr=0.0, pdep=0.0, pdv=0.0;
                String refDV = "";
                try {
                    FormasPagoDAO.FormasPagoRow fp = fdao.obtenerPorNota(numero);
                    if (fp != null) {
                        pef = n(fp.efectivo);
                        pcr = n(fp.tarjetaCredito);
                        pdb = n(fp.tarjetaDebito);
                        pam = n(fp.americanExpress);
                        ptr = n(fp.transferencia);
                        pdep= n(fp.deposito);
                        pdv = n(fp.devolucion);
                        if (fp.referenciaDV != null) refDV = fp.referenciaDV;
                    }
                } catch (Exception ignore) {}

                // Detalle -> una fila por artículo (como ya lo hacías)
                var det = ndao.listarDetalleDeNota(numero);
                if (det.isEmpty()) {
                    CsvRow r = new CsvRow();
                    r.folio=folio; r.numeroNota=numero; r.fecha=fecha; r.tipo=tipo; r.status=status;
                    r.telefono=tel; r.nombre=nombre;
                    r.p_efectivo=pef; r.p_tcredito=pcr; r.p_tdebito=pdb; r.p_amex=pam;
                    r.p_transfer=ptr; r.p_deposito=pdep; r.p_devolucion=pdv; r.p_ref_dv=refDV;
                    r.saldo=saldo; r.folioCreditoAbonado = folioCreditoAbonado;
                    rows.add(r);
                } else {
                    for (var d : det) {
                        CsvRow r = new CsvRow();
                        r.folio=folio; r.numeroNota=numero; r.fecha=fecha; r.tipo=tipo; r.status=status;
                        r.telefono=tel; r.nombre=nombre; r.codigo=d.getCodigoArticulo();
                        r.articulo=safe(d.getArticulo()); r.marca=safe(d.getMarca());
                        r.modelo=safe(d.getModelo()); r.talla=safe(d.getTalla()); r.color=safe(d.getColor());
                        r.precio=n(d.getPrecio()); r.descuento=n(d.getDescuento()); r.subtotal=n(d.getSubtotal());
                        r.p_efectivo=pef; r.p_tcredito=pcr; r.p_tdebito=pdb; r.p_amex=pam;
                        r.p_transfer=ptr; r.p_deposito=pdep; r.p_devolucion=pdv; r.p_ref_dv=refDV;
                        r.saldo=saldo; r.folioCreditoAbonado = folioCreditoAbonado; 
                        // NUEVO: facturación
                        r.rfc = rfc; r.regimenFiscal = regimen; r.usoCFDI = usoCFDI; r.correo = correo;

                        rows.add(r);
                    }
                }
            }

            String fname = "Notas_" + selectedYM.getYear() + "_" + String.format("%02d", selectedYM.getMonthValue());
            ExportadorCSV.guardarListaCSV(
                    rows, fname,
                    "folio","numeroNota","fecha","tipo","status",
                    "telefono","nombre","codigo","articulo","marca","modelo","talla","color",
                    "precio","descuento","subtotal",
                    "p_efectivo","p_tcredito","p_tdebito","p_amex","p_transfer","p_deposito","p_devolucion","p_ref_dv",
                    "saldo", "folioCreditoAbonado", "rfc","regimenFiscal","usoCFDI","correo"
            );

            JOptionPane.showMessageDialog(this, "CSV generado correctamente.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====== Helpers ======
    private String obtenerFolioCreditoAbonado(int numeroNotaAbono) {
        final String sql =
                "SELECT COALESCE(nc.folio, CONCAT('CR ', nc.numero_nota)) AS folio_cr " +
                "FROM Notas na " +
                "LEFT JOIN Notas nc ON nc.numero_nota = na.nota_relacionada AND nc.tipo='CR' " +
                "WHERE na.numero_nota = ? AND na.tipo = 'AB' " +
                "LIMIT 1";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNotaAbono);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("folio_cr");
            }
        } catch (Exception ignore) {}
        return "";
    }

    private void actualizarTextoMes() {
        String mes = selectedYM.getMonth().getDisplayName(java.time.format.TextStyle.FULL, ES_MX);
        mes = mes.substring(0,1).toUpperCase(ES_MX) + mes.substring(1);
        btMes.setText(mes + " " + selectedYM.getYear());
    }

    private static void ocultarColumnaVista(JTable t, int viewIndex) {
        if (viewIndex < 0 || viewIndex >= t.getColumnModel().getColumnCount()) return;
        TableColumnModel cm = t.getColumnModel();
        cm.removeColumn(cm.getColumn(viewIndex));
    }

    private Integer getNumeroNotaSeleccionada() {
        int vr = tbNotas.getSelectedRow();
        if (vr < 0) return null;
        int mr = tbNotas.convertRowIndexToModel(vr);
        Object v = modelNotas.getValueAt(mr, 0);  // SIEMPRE col 0 del MODELO = # Nota
        return (v instanceof Integer) ? (Integer) v : null;
    }

    private String getTipoNotaSeleccionada() {
        int vr = tbNotas.getSelectedRow();
        if (vr < 0) return null;
        int mr = tbNotas.convertRowIndexToModel(vr);
        Object v = modelNotas.getValueAt(mr, 1);
        return v == null ? null : String.valueOf(v);
    }

    private void limpiarDetallePagosCliente() {
        modelDet.setRowCount(0);
        modelPago.setRowCount(0);
        modelDV.setRowCount(0);
        modelCliente.setRowCount(0);
        lbAbonaA.setText(" ");
    }

    private static String safe(String s){ return s==null? "" : s; }
    private static Double n(Double d){ return d==null? 0.0 : d; }

    private static String prettify(String col) {
        String s = col == null ? "" : col.trim().replace('_',' ');
        if (s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    /** Sin tocar tus DAOs: leo el teléfono directamente de Notas. */
    private String obtenerTelefonoPorNumeroNota(int numeroNota) throws SQLException {
        String sql = "SELECT telefono FROM Notas WHERE numero_nota=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // Clase “POJO” para el exportador CSV (usa reflexión por nombre de campo)
    public static class CsvRow {
        public String folio;
        public Integer numeroNota;
        public String fecha;
        public String tipo;
        public String status;
        public String telefono;
        public String nombre;
        public String codigo;
        public String articulo;
        public String marca;
        public String modelo;
        public String talla;
        public String color;
        public Double precio;
        public Double descuento;
        public Double subtotal;
        public Double p_efectivo;
        public Double p_tcredito;
        public Double p_tdebito;
        public Double p_amex;
        public Double p_transfer;
        public Double p_deposito;
        public Double p_devolucion;
        public String  p_ref_dv;    // lista de DVs usados como pago
        public Double saldo;        // saldo de la nota (si hay)
        public String folioCreditoAbonado;
        public String rfc;
        public String regimenFiscal;
        public String usoCFDI;
        public String correo;
    }

    // ======= Popup de selección de MES =======
    static class MonthPopup extends JPopupMenu {
        private int year;
        private final JLabel lbYear = new JLabel("", SwingConstants.CENTER);
        private final java.util.function.Consumer<YearMonth> onPick;
        MonthPopup(YearMonth initial, java.util.function.Consumer<YearMonth> onPick) {
            this.year = (initial==null? YearMonth.now() : initial).getYear();
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
