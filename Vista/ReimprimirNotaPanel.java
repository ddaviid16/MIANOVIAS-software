    package Vista;

    import Controlador.NotasDAO;
    import Modelo.NotaDetalle;
    import Modelo.Nota;
    import Controlador.FormasPagoDAO;


    import javax.imageio.ImageIO;
    import javax.swing.*;
    import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

    import java.awt.*;
    import java.awt.image.BufferedImage;
    import java.awt.print.PageFormat;
    import java.awt.print.Printable;
    import java.awt.print.PrinterException;
    import java.awt.print.PrinterJob;
    import java.io.ByteArrayInputStream;
    import java.sql.Connection;
    import java.sql.PreparedStatement;
    import java.sql.ResultSet;
    import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
    import java.time.LocalDate;
    import java.util.ArrayList;
    import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

    // === imports de tus modelos/daos usados por las utilidades de impresión ===
    import Controlador.EmpresaDAO;
    import Modelo.ClienteResumen;
    import Modelo.Empresa;
    import Modelo.PagoFormas;
    import Utilidades.TelefonosUI;

    /**
     * Re-imprimir nota:
     * - Buscar por teléfono del cliente.
     * - Tabla superior: todas las notas del cliente (venta, abono, cancelación, etc.).
     * - Al seleccionar una nota, se muestra su detalle en la tabla inferior.
     * - Botón "Re-imprimir" llama al motor de impresión integrado (o al callback si te interesa inyectarlo).
     *
     * No agrega título "Menú", usa solo el contenido del panel.
     */
    public class ReimprimirNotaPanel extends JPanel {
        private static String tipoKey(String s){
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD);
        t = t.replaceAll("\\p{InCombiningDiacriticalMarks}+", ""); // quita acentos
        return t.trim().toUpperCase();
    }
        // ===== Usuario en sesión (cajera) =====
    private int cajeraCodigo;
    private String cajeraNombre;
    LocalDate fechaNota;

    public void setCajeraActual(int codigoEmpleado, String nombreCompleto) {
        this.cajeraCodigo = codigoEmpleado;
        this.cajeraNombre = nombreCompleto;
    }
// Fecha larga con mes en español, estilo "12-diciembre-2025"
private static final java.util.Locale LOCALE_ES_MX = java.util.Locale.of("es", "MX");
private static final java.time.format.DateTimeFormatter MX_LARGO =
        java.time.format.DateTimeFormatter.ofPattern("dd-MMMM-yyyy", LOCALE_ES_MX);
        

private String fechaLarga(java.time.LocalDate f) {
    if (f == null) return "";
    return f.format(MX_LARGO);
}
    /** Formatea teléfonos a 123-456-7890 si tienen 10 dígitos; si no, los deja como vienen. */
    private static String formatTelefono10(String valor) {
        if (valor == null) return "";
        String dig = TelefonosUI.soloDigitos(valor);
        if (dig == null || dig.isEmpty()) return "";
        if (dig.length() == 10) {
            return dig.substring(0, 3) + "-" + dig.substring(3, 6) + "-" + dig.substring(6);
        }
        return valor.trim();
    }
    /** dd-MMMM-yyyy con mes capitalizado: 05-Diciembre-2025 */
    private static String formatFechaLarga(LocalDate fecha) {
        if (fecha == null) return "";
        String base = MX_LARGO.format(fecha);   // ej. "05-diciembre-2025"
        String[] parts = base.split("-");
        if (parts.length < 3) {
            return base;
        }
        String mes = parts[1];
        if (!mes.isEmpty()) {
            mes = mes.substring(0, 1).toUpperCase(LOCALE_ES_MX) + mes.substring(1);
        }
        return parts[0] + "-" + mes + "-" + parts[2];
    }


        private final JTextField txtTelefono = new JTextField();
        private final JButton btBuscar = new JButton("Buscar");

        private final JButton btBuscarApellido = new JButton("Buscar por nombre o apellido");
        private final JTextField txtFolio = new JTextField();
        private final JButton btBuscarFolio = new JButton("Buscar por Folio");

       // Tabla de cabecera (notas)
        private final DefaultTableModel modelNotas = new DefaultTableModel(
                new String[]{"# Nota", "Tipo", "Folio", "Fecha", "Total", "Saldo de la nota", "Folio ref", "Status"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }

            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 0 -> Integer.class; // # Nota
                    case 4, 5 -> Double.class; // Total, Saldo
                    default -> String.class;
                };
            }
        };

        private final JTable tbNotas = new JTable(modelNotas);

        // Tabla de detalle (renglones de la nota)
        private final DefaultTableModel modelDet = new DefaultTableModel(
                new String[]{"Código", "Artículo", "Marca", "Modelo", "Talla", "Color", "Precio", "%Desc", "Subtotal"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return (c==0) ? Integer.class : (c>=6 ? Double.class : String.class);
            }
        };
        private final JTable tbDetalle = new JTable(modelDet);

        private final JButton btReimprimir = new JButton("Re-imprimir");

        /** Callback opcional: (numeroNota, tipo) -> imprimir */
        private final BiConsumer<Integer, String> onReimprimir;

        /** Constructor por defecto: reimpresión integrada. */
        public ReimprimirNotaPanel() {
            this(null);
        }

        /** Constructor con callback de impresión (si quieres inyectar tu propio motor). */
        public ReimprimirNotaPanel(BiConsumer<Integer, String> onReimprimir) {
            this.onReimprimir = (onReimprimir != null) ? onReimprimir : this::reimprimirDesdeUI;
            buildUI();
            hookEvents();
        }

        private void buildUI() {
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            TelefonosUI.instalar(txtTelefono, 10);

            // Filtro superior
            JPanel filtro = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4,4,4,4);
            c.fill = GridBagConstraints.HORIZONTAL;

            // Etiqueta "Teléfono:"
            c.weightx = 0;
            c.gridx = 0;
            filtro.add(new JLabel("Teléfono:"), c);

            // Campo de teléfono
            c.weightx = 1;
            c.gridx = 1;
            filtro.add(txtTelefono, c);

            // Botón Buscar (por teléfono)
            c.weightx = 0;
            c.gridx = 2;
            filtro.add(btBuscar, c);

            // Botón Buscar por nombre o apellido
            c.gridx = 3;
            filtro.add(btBuscarApellido, c);

            // NUEVO: etiqueta "Buscar por Folio:"
            c.weightx = 0;
            c.gridx = 4;
            filtro.add(new JLabel("Buscar por Folio:"), c);

            // NUEVO: campo de folio
            c.weightx = 0.6;
            c.gridx = 5;
            filtro.add(txtFolio, c);

            // NUEVO: botón "Buscar por Folio"
            c.weightx = 0;
            c.gridx = 6;
            filtro.add(btBuscarFolio, c);


            add(filtro, BorderLayout.NORTH);

            // Master/Detail
            tbNotas.setRowHeight(22);
            tbNotas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tbNotas.setAutoCreateRowSorter(true);
            TableRowSorter<?> sorter = (TableRowSorter<?>) tbNotas.getRowSorter();
            sorter.toggleSortOrder(3); // primero ASC
            sorter.toggleSortOrder(3); // ahora DESC

            JScrollPane spNotas = new JScrollPane(tbNotas);
            spNotas.setBorder(BorderFactory.createTitledBorder("Notas del cliente"));

            tbDetalle.setRowHeight(22);
            JScrollPane spDet = new JScrollPane(tbDetalle);
            spDet.setBorder(BorderFactory.createTitledBorder("Detalle de la nota seleccionada"));

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spNotas, spDet);
            split.setResizeWeight(0.5);
            add(split, BorderLayout.CENTER);

            // Botonera inferior
            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(btReimprimir);
            add(south, BorderLayout.SOUTH);
        }

        private void hookEvents() {
            btBuscar.addActionListener(_e -> cargarNotas());
            btBuscarApellido.addActionListener(_e -> seleccionarClientePorApellido());
            txtTelefono.addActionListener(_e -> cargarNotas());
                    // NUEVO: buscar por folio
            btBuscarFolio.addActionListener(_e -> buscarPorFolio());
            txtFolio.addActionListener(_e -> buscarPorFolio());


            tbNotas.getSelectionModel().addListSelectionListener(_e -> {
                if (!_e.getValueIsAdjusting()) cargarDetalleSeleccionada();
            });

            btReimprimir.addActionListener(_e -> {
                int row = tbNotas.getSelectedRow();
                if (row < 0) {
                    JOptionPane.showMessageDialog(this, "Selecciona una nota.", "Atención",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int modelRow = tbNotas.convertRowIndexToModel(row);
                // ==== FECHA ORIGINAL DE LA NOTA (columna "Fecha") ====
                fechaNota = null;
                Object fechaCell = modelNotas.getValueAt(modelRow, 3);  // columna "Fecha"
                if (fechaCell != null) {
                    try {
                        fechaNota = LocalDate.parse(fechaCell.toString());  // viene como "yyyy-MM-dd"
                    } catch (Exception ignore) { }
                }
                Integer numero = (Integer) modelNotas.getValueAt(modelRow, 0);
                String tipo = (String) modelNotas.getValueAt(modelRow, 1);
                try {
                    onReimprimir.accept(numero, tipo); // usa el integrado (o tu callback inyectado)
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error al re-imprimir: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }
    /** Busca una nota específica por folio y la muestra en la tabla. */
    private void buscarPorFolio() {
        modelNotas.setRowCount(0);
        modelDet.setRowCount(0);

        String folioBuscar = txtFolio.getText();
        if (folioBuscar == null || folioBuscar.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Captura un folio para buscar.",
                    "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        folioBuscar = folioBuscar.trim();

        final String sql =
                "SELECT numero_nota, tipo, folio, DATE(fecha_registro) AS fecha, " +
                "       COALESCE(total,0) AS total, COALESCE(saldo,0) AS saldo, " +
                "       status, telefono " +
                "FROM Notas " +
                "WHERE folio = ?";

        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, folioBuscar);

            try (ResultSet rs = ps.executeQuery()) {
                boolean hay = false;
                while (rs.next()) {
                    hay = true;

                    int numero      = rs.getInt("numero_nota");
                    String tipo     = rs.getString("tipo");
                    String folio    = rs.getString("folio");
                    java.sql.Date f = rs.getDate("fecha");
                    String fechaStr = (f == null) ? "" : f.toLocalDate().toString();
                    Double total    = rs.getDouble("total");
                    Double saldo    = rs.getDouble("saldo");
                    String status   = rs.getString("status");

                    // Igual que en cargarNotas(): folio de referencia si es abono
                    String folioRef = "";
                    String t = tipoKey(tipo);
                    boolean esAbono =
                            t.equals("AB") ||
                            t.contains("ABONO") ||
                            t.contains("PAGO PARCIAL");

                    if (esAbono) {
                        Integer numOrigen = leerNotaOrigenPorAbono(numero);
                        if (numOrigen != null) {
                            NotaHead head = leerNotaHead(numOrigen);
                            if (head != null) {
                                folioRef = nullToEmpty(head.folio);
                            }
                        }
                    }

                    modelNotas.addRow(new Object[]{
                            numero,
                            nullToEmpty(tipo),
                            nullToEmpty(folio),
                            fechaStr,
                            total,
                            saldo,
                            folioRef,
                            nullToEmpty(status)
                    });

                    // Opcional pero útil: llenar el teléfono del cliente para otras funciones
                    String telDb = rs.getString("telefono");
                    String telDig = TelefonosUI.soloDigitos(telDb);
                    if (telDig != null && !telDig.isEmpty()) {
                        txtTelefono.setText(telDig);
                    }
                }

                if (!hay) {
                    JOptionPane.showMessageDialog(this,
                            "No se encontró ninguna nota con el folio indicado.",
                            "Sin resultados",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    ocultarColumnaNumeroNota();   // misma lógica que en cargarNotas()
                    tbNotas.setRowSelectionInterval(0, 0);
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error buscando por folio: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

private void cargarNotas() {
    modelNotas.setRowCount(0);
    modelDet.setRowCount(0);

    String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
    if (tel == null || tel.isEmpty()) return;

    try {
        NotasDAO dao = new NotasDAO();
        List<NotasDAO.NotaResumen> lista = dao.listarNotasPorTelefonoResumen(tel);

        for (NotasDAO.NotaResumen r : lista) {

            Double total = (r.total == null ? 0.0 : r.total);
            // IMPORTANTE: usamos el saldo calculado en NotasDAO (incluye lógica secuencial de abonos)
            Double saldoMostrar = (r.saldo == null ? 0.0 : r.saldo);
            String folioRef = "";   // aquí guardaremos el folio referenciado

            String t = tipoKey(r.tipo);
            boolean esAbono =
                    t.equals("AB") ||
                    t.contains("ABONO") ||
                    t.contains("PAGO PARCIAL");

            if (esAbono) {
                // nota origen (número interno)
                Integer numOrigen = leerNotaOrigenPorAbono(r.numero);
                if (numOrigen != null) {
                    NotaHead head = leerNotaHead(numOrigen);
                    if (head != null) {
                        // SOLO usamos el folio del crédito como referencia
                        folioRef = nullToEmpty(head.folio);
                        // NO tocamos saldoMostrar, ya viene bien calculado
                    }
                }
            }

            modelNotas.addRow(new Object[]{
                    r.numero,                                // # Nota (oculta)
                    r.tipo,
                    nullToEmpty(r.folio),                    // folio de esta nota
                    (r.fecha == null ? "" : r.fecha.toString()),
                    total,
                    saldoMostrar,                            // saldo histórico / secuencial
                    folioRef,                                // FOLIO referenciado (nota origen)
                    nullToEmpty(r.status)
            });
        }

        if (modelNotas.getRowCount() > 0) {
            tbNotas.setRowSelectionInterval(0, 0);
        } else {
            JOptionPane.showMessageDialog(this, "El cliente no tiene notas registradas.",
                    "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
        }

        ocultarColumnaNumeroNota();  // <- importante

    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Error consultando notas: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

        private void cargarDetalleSeleccionada() {
            modelDet.setRowCount(0);
            int row = tbNotas.getSelectedRow();
            if (row < 0) return;

            int modelRow = tbNotas.convertRowIndexToModel(row);
            Integer numero = (Integer) modelNotas.getValueAt(modelRow, 0);

            try {
                List<NotaDetalle> det = new NotasDAO().listarDetalleDeNota(numero);
                for (NotaDetalle d : det) {
                    modelDet.addRow(new Object[]{
                            d.getCodigoArticulo(),
                            nullToEmpty(d.getArticulo()),
                            nullToEmpty(d.getMarca()),
                            nullToEmpty(d.getModelo()),
                            nullToEmpty(d.getTalla()),
                            nullToEmpty(d.getColor()),
                            zero(d.getPrecio()),
                            zero(d.getDescuento()),
                            zero(d.getSubtotal())
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error cargando detalle: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private static String nullToEmpty(String s){ return s==null ? "" : s; }
        private static Double zero(Double d){ return d==null? 0.0 : d; }

        /* ===================================================================
        * ===============  PEGAMENTO DE REIMPRESIÓN (integrado)  =============
        * =================================================================== */
        // ===== Tarjetas por artículo (¼ de hoja carta) – reimpresión =====
        private static class TarjetaVentaData {
            String razonSocial;
            String cliente;
            String fechaEvento;
            String fechaCompra;
            String asesor;
            String codigoArticulo;
            String articulo;
            String marca;
            String modelo;
            String talla;
            String color;
            String obsequios;
            String observaciones;
        }

        private static class TarjetasVentaPrinter implements Printable {

            private final java.util.List<TarjetaVentaData> tarjetas;

            TarjetasVentaPrinter(java.util.List<TarjetaVentaData> tarjetas) {
                this.tarjetas = tarjetas;
            }

            @Override
            public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                int tarjetasPorPagina = 4; // 2 x 2
                int start = pageIndex * tarjetasPorPagina;
                if (start >= tarjetas.size()) {
                    return NO_SUCH_PAGE;
                }

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(
                        java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                );

                double iw = pf.getImageableWidth();
                double ih = pf.getImageableHeight();
                double ix = pf.getImageableX();
                double iy = pf.getImageableY();

                double cardW = iw / 2.0;
                double cardH = ih / 2.0;

                for (int i = 0; i < tarjetasPorPagina; i++) {
                    int idx = start + i;
                    if (idx >= tarjetas.size()) break;

                    TarjetaVentaData t = tarjetas.get(idx);
                    int row = i / 2;
                    int col = i % 2;
                    double x = ix + col * cardW;
                    double y = iy + row * cardH;

                    dibujarTarjeta(g2, t, x, y, cardW, cardH);
                }

                return PAGE_EXISTS;
            }

            private void dibujarTarjeta(Graphics2D g2, TarjetaVentaData t,
                                        double x, double y, double w, double h) {

                int ix = (int) Math.round(x);
                int iy = (int) Math.round(y);
                int iw = (int) Math.round(w);
                int ih = (int) Math.round(h);

                // Marco
                g2.drawRect(ix, iy, iw, ih);

                int margin = 10;
                int lineH  = 12;

                Font base = g2.getFont();
                Font titleFont = base.deriveFont(Font.BOLD, 12f);
                Font textFont  = base.deriveFont(10f);

                // RAZÓN SOCIAL centrada
                g2.setFont(titleFont);
                String rs = safe(t.razonSocial);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int titleX = ix + (iw - fm.stringWidth(rs)) / 2;
                int yCursor = iy + margin + fm.getAscent();
                g2.drawString(rs, titleX, yCursor);

                // Resto de texto
                g2.setFont(textFont);
                yCursor += lineH * 2;
                int textX = ix + margin;

                g2.drawString("Cliente: " + safe(t.cliente), textX, yCursor);          yCursor += lineH;
                g2.drawString("Fecha Evento: " + safe(t.fechaEvento), textX, yCursor); yCursor += lineH;
                g2.drawString("Fecha Compra: " + safe(t.fechaCompra), textX, yCursor); yCursor += lineH;
                g2.drawString("Asesora: " + safe(t.asesor), textX, yCursor);            yCursor += lineH;

                yCursor += lineH / 2;
                g2.drawString("Código Artículo: " + safe(t.codigoArticulo), textX, yCursor); yCursor += lineH;
                g2.drawString("Artículo: " + safe(t.articulo), textX, yCursor);               yCursor += lineH;
                g2.drawString("Marca: " + safe(t.marca), textX, yCursor);                     yCursor += lineH;
                g2.drawString("Modelo: " + safe(t.modelo), textX, yCursor);                   yCursor += lineH;
                g2.drawString("Talla: " + safe(t.talla) + "   Color: " + safe(t.color),
                            textX, yCursor);
                yCursor += lineH * 2;

                // Obsequios
    g2.drawString("Obsequios:", textX, yCursor);
    yCursor += lineH;

    String ob = safe(t.obsequios);
    if (!ob.isEmpty()) {
        String[] lineas = ob.split("\\r?\\n");
        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            // igual que el formato original: indentado y una línea por obsequio
            g2.drawString("    " + linea, textX, yCursor);
            yCursor += lineH;
        }
    } else {
        yCursor += lineH;
    }


                // Observaciones
                yCursor += lineH / 2;
                g2.drawString("Observaciones:", textX, yCursor);
                yCursor += lineH;
                String obs = safe(t.observaciones);
                if (!obs.isEmpty()) {
                    yCursor = drawWrappedSimple(g2, obs, textX + 10, yCursor, iw - 2 * margin - 10);
                }
            }

            private static String safe(String s) {
                return (s == null) ? "" : s;
            }

            static void imprimir(Component parent, java.util.List<TarjetaVentaData> tarjetas) throws PrinterException {
                if (tarjetas == null || tarjetas.isEmpty()) return;
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintable(new TarjetasVentaPrinter(tarjetas));
                if (!job.printDialog()) return;
                job.print();
            }
        }

        /** Wrap simple para tarjetas. */
        private static int drawWrappedSimple(Graphics2D g2, String text, int x, int y, int maxWidth) {
            if (text == null) return y;
            text = text.trim();
            if (text.isEmpty()) return y;

            java.awt.FontMetrics fm = g2.getFontMetrics();
            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            int yy = y;

            for (String w : words) {
                String tryLine = (line.length() == 0 ? w : line + " " + w);
                if (fm.stringWidth(tryLine) <= maxWidth) {
                    line.setLength(0);
                    line.append(tryLine);
                } else {
                    g2.drawString(line.toString(), x, yy);
                    yy += fm.getHeight();
                    line.setLength(0);
                    line.append(w);
                }
            }
            if (line.length() > 0) {
                g2.drawString(line.toString(), x, yy);
                yy += fm.getHeight();
            }
            return yy;
        }

        // ¿Este artículo cuenta como "Vestido"?
        private boolean esArticuloVestido(String articulo) {
            if (articulo == null) return false;
            String s = articulo.toLowerCase();
            return s.contains("vestido");
        }

        private java.util.List<TarjetaVentaData> construirTarjetasVenta(
                EmpresaInfo emp,
                String clienteNombre,
                LocalDate fechaEvento,
                LocalDate fechaCompra,
                String asesorNombre,
                java.util.List<NotaDetalle> detsPrint,
                java.util.List<String> obsequiosCodigos,
                String observaciones) {

            java.util.List<TarjetaVentaData> tarjetas = new java.util.ArrayList<>();

            String razon = (emp.razonSocial != null && !emp.razonSocial.isBlank())
                    ? emp.razonSocial
                    : (emp.nombreFiscal != null ? emp.nombreFiscal : "");

            java.time.format.DateTimeFormatter MX =
                    java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

            String fechaEventoStr = (fechaEvento == null) ? "" : fechaEvento.format(MX);
            String fechaCompraStr = (fechaCompra == null) ? "" : fechaCompra.format(MX);

    // ======================== O B S E Q U I O S ========================
    String obsequiosTexto = "";
    if (obsequiosCodigos != null && !obsequiosCodigos.isEmpty()) {

        java.util.List<String> codigos = new java.util.ArrayList<>();
        java.util.Map<String,String> descManual = new java.util.HashMap<>();

        for (String raw : obsequiosCodigos) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String codigo = s;
            String desc   = "";

            int guion = s.indexOf('-');
            if (guion > 0) {
                codigo = s.substring(0, guion).trim();
                desc   = s.substring(guion + 1).trim();
            }

            codigos.add(codigo);
            if (!desc.isEmpty()) descManual.put(codigo, desc);
        }

        java.util.Map<String,String> descPorCodigo = new java.util.HashMap<>();

        if (!codigos.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
                    "FROM InventarioObsequios WHERE codigo_articulo IN ("
            );
            for (int i = 0; i < codigos.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(')');

            try (Connection cn = Conexion.Conecta.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql.toString())) {

                for (int i = 0; i < codigos.size(); i++) {
                    ps.setString(i + 1, codigos.get(i));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String c      = rs.getString("codigo_articulo");
                        String nombre = n(rs.getString("articulo"));
                        String marca  = n(rs.getString("marca"));
                        String modelo = n(rs.getString("modelo"));
                        String talla  = n(rs.getString("talla"));
                        String color  = n(rs.getString("color"));

                        StringBuilder desc = new StringBuilder();
                        if (!nombre.isBlank()) desc.append(nombre);
                        String mm = "";
                        if (!marca.isBlank()) mm = marca;
                        if (!modelo.isBlank()) {
                            if (!mm.isEmpty()) mm += " ";
                            mm += modelo;
                        }
                        if (!mm.isEmpty()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append('(').append(mm).append(')');
                        }
                        if (!talla.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Talla ").append(talla);
                        }
                        if (!color.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Color ").append(color);
                        }

                        descPorCodigo.put(c, desc.toString());
                    }
                }
            } catch (SQLException ex) {
                // Si esto truena, al menos nos quedamos con descManual
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String codigo : codigos) {
            if (sb.length() > 0) sb.append('\n');

            String desc = descPorCodigo.get(codigo);
            if (desc == null || desc.isBlank()) {
                desc = descManual.getOrDefault(codigo, codigo);
            }

            if (desc.equals(codigo)) sb.append(codigo);
            else sb.append(codigo).append(" - ").append(desc);
        }
        obsequiosTexto = sb.toString();
    }

    // ==================== FIN OBSEQUIOS =====================



            String obs = (observaciones == null) ? "" : observaciones;

            for (NotaDetalle d : detsPrint) {
                String art = d.getArticulo();
                int repeticiones = esArticuloVestido(art) ? 2 : 1;   // 2 tarjetas si es Vestido, 1 si no

                for (int i = 0; i < repeticiones; i++) {
                    TarjetaVentaData t = new TarjetaVentaData();
                    t.razonSocial    = razon;
                    t.cliente        = clienteNombre == null ? "" : clienteNombre;
                    t.fechaEvento    = fechaEventoStr;
                    t.fechaCompra    = fechaCompraStr;
                    t.asesor         = asesorNombre == null ? "" : asesorNombre;
                    t.codigoArticulo = d.getCodigoArticulo() == null ? "" : d.getCodigoArticulo();
                    t.articulo       = art == null ? "" : art;
                    t.marca          = d.getMarca() == null ? "" : d.getMarca();
                    t.modelo         = d.getModelo() == null ? "" : d.getModelo();
                    t.talla          = d.getTalla() == null ? "" : d.getTalla();
                    t.color          = d.getColor() == null ? "" : d.getColor();
                    t.obsequios      = obsequiosTexto;   // aquí ya va "COD - DESC" por línea
                    t.observaciones  = obs;
                    tarjetas.add(t);
                }
            }

            return tarjetas;
        }

        private void imprimirTarjetasVenta(
                EmpresaInfo emp,
                String clienteNombre,
                LocalDate fechaEvento,
                LocalDate fechaCompra,
                String asesorNombre,
                java.util.List<NotaDetalle> detsPrint,
                java.util.List<String> obsequiosCodigos,
                String observaciones) {

            java.util.List<TarjetaVentaData> tarjetas = construirTarjetasVenta(
                    emp, clienteNombre, fechaEvento, fechaCompra,
                    asesorNombre, detsPrint, obsequiosCodigos, observaciones
            );

            if (tarjetas.isEmpty()) return;

            try {
                TarjetasVentaPrinter.imprimir(this, tarjetas);
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this,
                        "Las tarjetas no se pudieron imprimir: " + ex.getMessage(),
                        "Impresión de tarjetas", JOptionPane.WARNING_MESSAGE);
            }
        }


    private void reimprimirDesdeUI(Integer numeroNota, String tipoRaw) {
        int row = tbNotas.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona una nota.", "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tbNotas.convertRowIndexToModel(row);

        String folio   = String.valueOf(modelNotas.getValueAt(modelRow, 2));
        Double total   = asDouble(modelNotas.getValueAt(modelRow, 4));
        Double saldo   = asDouble(modelNotas.getValueAt(modelRow, 5));
        String tipoCelda = (String) modelNotas.getValueAt(modelRow, 1);
        // Columna 6 = "Folio ref" (folio de la nota de crédito origen)
        String folioRef = "";
        Object frObj = modelNotas.getValueAt(modelRow, 6);
        if (frObj != null) {
            folioRef = frObj.toString();
        }


        // 1) Inferimos tipo de nota a partir de la columna "Tipo" y total/saldo
        TipoNota tipo = inferirTipoDesdeFila(tipoCelda, total, saldo);

        // 2) Fallback extra: si el folio empieza con "e", la tratamos como devolución
        String folioU = (folio == null) ? "" : folio.trim().toUpperCase();
        if (folioU.startsWith("E")) {
            tipo = TipoNota.DEVOLUCION;
        }

        // 3) Cargar detalle de la nota seleccionada
        List<NotaDetalle> dets;
        try {
            dets = new NotasDAO().listarDetalleDeNota(numeroNota);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error cargando detalle: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 4) Armar la cabecera Nota:
        String tel = TelefonosUI.soloDigitos(txtTelefono.getText());
        Nota nota = null;
        try {
            if (!folioU.isEmpty()) {
                NotasDAO dao = new NotasDAO();
                Nota nDb = dao.buscarNotaPorFolio(folio);
                if (nDb != null) {
                    nota = nDb;
                }
            }
        } catch (SQLException ex) {
            // Si truena, seguimos con una nota mínima
        }
        if (nota == null) {
            nota = new Nota();
            nota.setNumeroNota(numeroNota);
            nota.setTotal(total);
            nota.setSaldo(saldo);
            nota.setTelefono(tel);
        }
        setNotaFechaSeguro(nota, fechaNota);

        // 5) Observaciones
        String observacionesTexto = "";
        try {
            Controlador.NotasObservacionesDAO obsDAO = new Controlador.NotasObservacionesDAO();
            observacionesTexto = obsDAO.getByNota(numeroNota);
        } catch (Exception ex) {
            observacionesTexto = "";
        }

        // 6) Obsequios de la nota (se usarán en contado/crédito)
        java.util.List<String> obsequiosLineas = cargarObsequiosDeNota(numeroNota);

        // 7) Pagos: primero intentamos leer los reales, si no, usamos la heurística anterior
        String tipoStrParaPagos = switch (tipo) {
            case ABONO      -> "ABONO";
            case ANTICIPO   -> "ANTICIPO";
            case DEVOLUCION -> "DEVOLUCION";
            default         -> "CONTADO";
        };
        PagoFormas pagos = cargarPagoFormasDesdeDAO(numeroNota, total, saldo, tipoStrParaPagos);


        // 8) Datos de empresa
        EmpresaInfo emp = cargarEmpresaInfo();

        // 9) Elegir el formato correcto según el tipo
        Printable printable = null;

        switch (tipo) {

            case DEVOLUCION: {
                double montoDV = 0d;
                if (dets != null) {
                    for (NotaDetalle d : dets) {
                        montoDV += (d.getSubtotal() == null ? 0d : d.getSubtotal());
                    }
                }
                montoDV = Math.round(montoDV * 100.0) / 100.0;

                double saldoAFavor = montoDV;

                Integer origen = leerNotaOrigenPorDV(numeroNota);
                if (origen != null) {
                    NotaHead oh = leerNotaHead(origen);
                    if (oh != null && "CR".equalsIgnoreCase(oh.tipo)) {
                        double pagadoActual = Math.max(0d, oh.total - oh.saldo);
                        saldoAFavor = Math.min(montoDV, pagadoActual);
                    }
                }

                setNotaTotalSeguro(nota, saldoAFavor);

                printable = construirPrintableDevolucion(emp, nota, dets, folio, fechaNota,
                        cajeraCodigo, cajeraNombre);
                break;
            }

            case ABONO: {
                double abonoReal  = (total == null ? 0d : total);
                double saldoAbono = (saldo == null ? 0d : saldo);

                Integer notaOrigen = null;
                try (java.sql.Connection cn = Conexion.Conecta.getConnection();
                    java.sql.PreparedStatement ps = cn.prepareStatement(
                            "SELECT nota_relacionada FROM Notas WHERE numero_nota = ?")) {
                    ps.setInt(1, numeroNota);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int v = rs.getInt(1);
                            if (!rs.wasNull()) notaOrigen = v;
                        }
                    }
                } catch (Exception ignore) { }

                List<NotaDetalle> detsOrigen = java.util.Collections.emptyList();
                NotaHead headOrigen = null;
                if (notaOrigen != null) {
                    try {
                        detsOrigen = new NotasDAO().listarDetalleDeNota(notaOrigen);
                    } catch (SQLException ex) {
                        detsOrigen = java.util.Collections.emptyList();
                    }
                    headOrigen = leerNotaHead(notaOrigen);
                }

                String observacionesOriginal = "";
                if (notaOrigen != null) {
                    try {
                        Controlador.NotasObservacionesDAO obsDAO = new Controlador.NotasObservacionesDAO();
                        observacionesOriginal = obsDAO.getByNota(notaOrigen);
                    } catch (Exception ignore) { }
                }

                double saldoPosterior;
                if (headOrigen != null) {
                    setNotaTotalSeguro(nota, headOrigen.total);
                    saldoPosterior = headOrigen.saldo;
                } else {
                    saldoPosterior = saldoAbono;
                }
                double saldoAnterior = saldoPosterior + abonoReal;
                // opcional: por seguridad, que no pase del total de la venta
                if (nota.getTotal() != null && saldoAnterior > nota.getTotal() + 0.01) {
                saldoAnterior = nota.getTotal();
            }
                PagoFormas pagosAbono =
                        cargarPagoFormasDesdeDAO(numeroNota, abonoReal, saldoPosterior, "ABONO");

                printable = construirPrintableAbono(
                        emp,
                        nota,
                        detsOrigen,
                        pagosAbono,
                        abonoReal,
                        saldoPosterior,
                        saldoAnterior,
                        folio,
                        folioRef,
                        observacionesOriginal,
                        fechaNota,
                        cajeraCodigo,
                        cajeraNombre
                );
                break;
            }

    case ANTICIPO: {
                // *** AQUÍ ES DONDE USAMOS VentaCreditoPanel.reimprimirNotaCreditoDesdeDatos ***

                ClienteResumen cli = null;
                LocalDate fechaEvento = null;
                LocalDate fechaEntrega = null;
                String clienteNombre = "";
                String tel2 = "";

                try {
                    Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                    cli = cdao.buscarResumenPorTelefono(nota.getTelefono());
                } catch (Exception ignore) { }

                if (cli != null) {
                    if (cli.getNombreCompleto() != null && !cli.getNombreCompleto().isBlank()) {
                        clienteNombre = cli.getNombreCompleto();
                    }
                    if (cli.getTelefono2() != null) {
                        tel2 = cli.getTelefono2();
                    }
                    if (cli.getFechaEvento() != null) {
                        fechaEvento = cli.getFechaEvento();
                    }
                    if (cli.getFechaEntrega() != null) {
                        fechaEntrega = cli.getFechaEntrega();
                    }
                }

                if (fechaEvento == null) {
                    try {
                        NotasDAO dao = new NotasDAO();
                        fechaEvento = dao.obtenerFechaEventoDeNota(numeroNota);
                    } catch (Exception ignore) { }
                }

                String nombreAsesor = "";
                try {
                    NotasDAO dao = new NotasDAO();
                    nombreAsesor = leerAsesorNombre(dao, folio);
                    if ((nombreAsesor == null || nombreAsesor.isBlank()) && nota.getAsesor() != null) {
                        String tmp = dao.obtenerNombreAsesor(nota.getAsesor());
                        if (tmp != null) nombreAsesor = tmp;
                    }
                } catch (Exception ignore) { }

                // Memo de condiciones guardado en BD (si lo tienes). De momento lo dejamos vacío
                String memoTextoBD = cargarMemoDesdeBD(numeroNota);
                // Si en tu BD tienes el memo en alguna tabla, este es el lugar
                // para leerlo sin inventar columnas.


                // Usamos el módulo de VentaCreditoPanel
                VentaCreditoPanel.reimprimirNotaCreditoDesdeDatos(
                        this,
                        nota,
                        dets,
                        pagos,
                        nombreAsesor,
                        fechaEvento,
                        fechaEntrega,
                        clienteNombre,
                        tel2,
                        memoTextoBD,
                        observacionesTexto,
                        obsequiosLineas,
                        
                        cajeraCodigo,
                        cajeraNombre
                );

                // Importante: este helper YA maneja imprimir ticket + condiciones + tarjetas
                // y su propio imprimirYConfirmarAsync, así que aquí terminamos.
                return;
            }       
            case CONTADO: {
                // === Datos de cliente (igual que en ANTICIPO) ===
                ClienteResumen cli = null;
                LocalDate fechaEvento = null;
                LocalDate fechaEntrega = null;
                String clienteNombre = "";
                String tel2 = "";

                try {
                    Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                    cli = cdao.buscarResumenPorTelefono(nota.getTelefono());
                } catch (Exception ignore) { }

                if (cli != null) {
                    if (cli.getNombreCompleto() != null && !cli.getNombreCompleto().isBlank()) {
                        clienteNombre = cli.getNombreCompleto();
                    }
                    if (cli.getTelefono2() != null) {
                        tel2 = cli.getTelefono2();
                    }
                    if (cli.getFechaEvento() != null) {
                        fechaEvento = cli.getFechaEvento();
                    }
                    if (cli.getFechaEntrega() != null) {
                        fechaEntrega = cli.getFechaEntrega();
                    }
                }

                // Fecha de evento “rescatada” de la nota, si no vino del cliente
                if (fechaEvento == null) {
                    try {
                        NotasDAO dao = new NotasDAO();
                        fechaEvento = dao.obtenerFechaEventoDeNota(numeroNota);
                    } catch (Exception ignore) { }
                }

                // === Asesor ===
                String nombreAsesor = "";
                try {
                    NotasDAO dao = new NotasDAO();
                    nombreAsesor = leerAsesorNombre(dao, folio);
                    if ((nombreAsesor == null || nombreAsesor.isBlank()) && nota.getAsesor() != null) {
                        String tmp = dao.obtenerNombreAsesor(nota.getAsesor());
                        if (tmp != null) nombreAsesor = tmp;
                    }
                } catch (Exception ignore) { }

                // Memo / condiciones guardadas (lo sigues pudiendo cargar aunque no lo uses aquí)
                String memoTextoBD = "";
                try {
                    memoTextoBD = cargarMemoDesdeBD(numeroNota);
                } catch (Exception ignore) {
                    memoTextoBD = "";
                }

                // Usamos el módulo de VentaCreditoPanel
                VentaContadoPanel.reimprimirNotaContadoDesdeDatos(
                        this,
                        nota,
                        dets,
                        pagos,
                        nombreAsesor,
                        fechaEvento,
                        fechaEntrega,
                        clienteNombre,
                        tel2,
                        memoTextoBD,
                        observacionesTexto,
                        obsequiosLineas,
                        
                        cajeraCodigo,
                        cajeraNombre
                );

                break;
            }
        default: {
            // Si por alguna razón cae en default, usamos el formato contado empresarial como respaldo
            LocalDate fEntrega = null, fEvento = null;
            String asesor = "";
            printable = construirPrintableContadoEmpresarial(
                    emp,
                        nota,
                        dets,
                        pagos,
                        fEntrega,
                        fEvento,
                        asesor,
                        folio,
                        "",
                        tel,
                        observacionesTexto,
                        obsequiosLineas,
                        fechaNota,        // LocalDate de la nota
                        cajeraCodigo,
                        cajeraNombre
            );
            break;
        }
            }
        
            // 10) Lanzar impresión sólo si tenemos algo que imprimir aquí
if (printable == null) {
    return;
}
        // 10) Lanzar impresión
        imprimirYConfirmarAsync(
                printable,
                () -> JOptionPane.showMessageDialog(this, "Re-impresión completada.",
                        "Listo", JOptionPane.INFORMATION_MESSAGE),
                () -> JOptionPane.showMessageDialog(this, "Impresión cancelada o fallida.",
                        "Aviso", JOptionPane.WARNING_MESSAGE)
        );
    }

        /** Re-imprime tarjetas de la nota seleccionada (solo CONTADO / CRÉDITO). */
        /** Re-imprime tarjetas de la nota seleccionada (solo CONTADO / CRÉDITO). */
        private void reimprimirTarjetasSeleccionada() {
            int row = tbNotas.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Selecciona una nota.", "Atención",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = tbNotas.convertRowIndexToModel(row);

    String folio   = String.valueOf(modelNotas.getValueAt(modelRow, 2));
    Double total   = asDouble(modelNotas.getValueAt(modelRow, 4));
    Double saldo   = asDouble(modelNotas.getValueAt(modelRow, 5));
    String tipoCelda = (String) modelNotas.getValueAt(modelRow, 1);

    // ==== FECHA ORIGINAL DE LA NOTA (columna "Fecha") ====
    fechaNota = null;
    Object fechaCell = modelNotas.getValueAt(modelRow, 3);  // columna "Fecha"
    if (fechaCell != null) {
        try {
            fechaNota = LocalDate.parse(fechaCell.toString());  // viene como "yyyy-MM-dd"
        } catch (Exception ignore) { }
    }

            
            // Datos básicos de la fila seleccionada
            Integer numero   = (Integer) modelNotas.getValueAt(modelRow, 0);  // numero_nota

            // 1) Determinar tipo de nota
            TipoNota tipo = inferirTipoDesdeFila(tipoCelda, total, saldo);
            String folioU = (folio == null) ? "" : folio.trim().toUpperCase();
            if (folioU.startsWith("E")) {
                tipo = TipoNota.DEVOLUCION;
            }

            // Tarjetas solo tienen sentido para ventas (CONTADO / ANTICIPO-CRÉDITO)
            if (tipo != TipoNota.CONTADO && tipo != TipoNota.ANTICIPO) {
                JOptionPane.showMessageDialog(this,
                        "Las tarjetas solo aplican a notas de venta (contado o crédito).",
                        "No aplica",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            NotasDAO dao = new NotasDAO();

            // 2) Detalle de la nota
            java.util.List<NotaDetalle> dets;
            try {
                dets = dao.listarDetalleDeNota(numero);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error cargando detalle: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (dets == null || dets.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "La nota no tiene detalle para generar tarjetas.",
                        "Sin datos", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 3) Empresa
            EmpresaInfo emp = cargarEmpresaInfo();

            // 4) Cliente (por teléfono del filtro)
            String tel = TelefonosUI.soloDigitos(txtTelefono.getText());
            String clienteNombre = "";
            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
                if (cr != null && cr.getNombreCompleto() != null &&
                        !cr.getNombreCompleto().isBlank()) {
                    clienteNombre = cr.getNombreCompleto();
                }
            } catch (Exception ignore) { }

            // 5) Observaciones de la nota
            String observaciones = "";
            try {
                observaciones = dao.obtenerObservacionesDeNota(numero);
                if (observaciones == null) observaciones = "";
            } catch (Exception ignore) { }

            // 6) Fecha de evento de la nota (si la tienes guardada)
            LocalDate fechaEvento = null;
            try {
                fechaEvento = dao.obtenerFechaEventoDeNota(numero);
            } catch (Exception ignore) { }

            // 7) Fecha de compra:
            //    intentamos leer fecha_registro (TIMESTAMP) de Notas y convertirla a LocalDate.
            //    Si no hay nada, caemos en hoy.
            LocalDate fechaCompra = LocalDate.now();
            try (java.sql.Connection cn = Conexion.Conecta.getConnection();
                java.sql.PreparedStatement ps = cn.prepareStatement(
                        "SELECT fecha_registro FROM Notas WHERE numero_nota = ?")) {

                ps.setInt(1, numero);

                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Timestamp ts = rs.getTimestamp(1);
                        if (ts != null) {
                            fechaCompra = ts.toLocalDateTime().toLocalDate();
                        }
                    }
                }
            } catch (Exception ignore) { }

            // 8) Asesor: intentamos por folio y, si no sale, por número de nota
            String asesorNombre = "";
            try {
                Integer asesorId = null;  // ID numérico del asesor

                // 8.1 si hay folio, intenta obtener la nota por folio
                if (folio != null && !folio.trim().isEmpty()) {
                    Nota notaPorFolio = dao.buscarNotaPorFolio(folio.trim());
                    if (notaPorFolio != null && notaPorFolio.getAsesor() != null) {
                        asesorId = notaPorFolio.getAsesor();   // Integer
                    }
                }

                // 8.2 si no hay folio o no se obtuvo asesor, intenta leer el asesor directo de la tabla Notas
                if (asesorId == null) {
                    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
                        java.sql.PreparedStatement ps = cn.prepareStatement(
                                "SELECT asesor FROM Notas WHERE numero_nota = ?")) {

                        ps.setInt(1, numero);

                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                int v = rs.getInt(1);
                                if (!rs.wasNull()) {
                                    asesorId = v;
                                }
                            }
                        }
                    }
                }

                // 8.3 traducir id de asesor a NOMBRE usando tu método de NotasDAO
                if (asesorId != null) {
                    String nom = dao.obtenerNombreAsesor(asesorId); // método espera int
                    if (nom != null && !nom.isBlank()) {
                        asesorNombre = nom;
                    } else {
                        asesorNombre = "ID " + asesorId;
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }

            // 9) Obsequios guardados para esa nota
            List<String> obsequiosCodigos;
            try {
                String textoObsequios = dao.obtenerObsequiosDeNota(numero); // devuelve String

                obsequiosCodigos = new ArrayList<>();

                if (textoObsequios != null && !textoObsequios.isBlank()) {
                    for (String linea : textoObsequios.split("\\r?\\n")) {
                        if (!linea.isBlank()) {
                            obsequiosCodigos.add(linea.trim());
                        }
                    }
                }
            } catch (Exception ex) {
                obsequiosCodigos = java.util.Collections.emptyList();
            }

            // 10) Mandar todo al motor de tarjetas
            imprimirTarjetasVenta(
                    emp,
                    clienteNombre,
                    fechaEvento,
                    fechaCompra,
                    asesorNombre,
                    dets,
                    obsequiosCodigos,
                    observaciones
            );
        }



        private static Double asDouble(Object v) {
            if (v == null) return 0d;
            if (v instanceof Double d) return d;
            if (v instanceof Number n) return n.doubleValue();
            try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0d; }
        }
        private enum TipoNota { CONTADO, ANTICIPO, ABONO, DEVOLUCION }
    /** Lee el nombre del asesor asociado a una nota usando el folio. */
    private String leerAsesorNombre(NotasDAO dao, String folio) {
        if (dao == null) return "";
        String folioU = (folio == null) ? "" : folio.trim();
        if (folioU.isEmpty()) return "";

        try {
            Nota nota = dao.buscarNotaPorFolio(folioU);   // ya existe este método en tu DAO
            if (nota != null && nota.getAsesor() != null) {
                String nom = dao.obtenerNombreAsesor(nota.getAsesor());
                return (nom == null) ? "" : nom;
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private TipoNota inferirTipoDesdeFila(String tipoCelda, Double total, Double saldo) {
        String t = normaliza(tipoCelda); // mayúsculas, sin acentos

        // === Devolución ===
        if (t.contains("DEVOLUCION") || t.contains("DEVOLUCIÓN") || t.contains("CAMBIO")
            || t.equals("DV")) {
            return TipoNota.DEVOLUCION;
        }

        // === Abono ===
        if (t.contains("ABONO") || t.contains("PAGO PARCIAL") || t.equals("AB") || t.equals("D")) {
            return TipoNota.ABONO;
        }

        // === Anticipo / Crédito ===
        if (t.contains("CREDITO") || t.contains("ANTICIPO") || t.contains("APARTADO")
            || t.contains("SEPARADO") || t.equals("CR")) {
            return TipoNota.ANTICIPO;
        }

        // === Contado ===
        if (t.equals("CN") || t.equals("C") || t.contains("CONTADO")) {
            return TipoNota.CONTADO;
        }

        // Heurística de respaldo: si hay saldo pendiente, trátalo como ANTICIPO
        double s = (saldo == null ? 0d : saldo);
        double tot = (total == null ? 0d : total);
        if (s > 0.0009 && s <= Math.max(tot, 0d)) return TipoNota.ANTICIPO;

        return TipoNota.CONTADO;
    }


    /** Quita acentos/espacios raros y pone en mayúsculas. */
    private String normaliza(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");             // sin acentos
        n = n.toUpperCase().replaceAll("\\s+", " ").trim();
        return n;
    }


        /** Si no tienes el desglose, usa esta aproximación (no cambia tus formatos). */
        private PagoFormas crearPagoFormasParaReimpresion(Double total, Double saldo, String tipo) {
            double t = (total == null ? 0d : total);
            double s = (saldo == null ? 0d : saldo);
            double efectivo;
            switch ((tipo == null ? "" : tipo).toUpperCase()) {
                case "ABONO":    efectivo = t; break;                // monto abonado
                case "CREDITO":
                case "ANTICIPO": efectivo = Math.max(0d, t - s); break; // anticipo estimado
                default:         efectivo = t; break;                // contado (total pagado)
            }
            PagoFormas p = new PagoFormas();
            try { p.setTarjetaCredito(0d); } catch (Exception ignore) {}
            try { p.setTarjetaDebito(0d); } catch (Exception ignore) {}
            try { p.setAmericanExpress(0d); } catch (Exception ignore) {}
            try { p.setTransferencia(0d); } catch (Exception ignore) {}
            try { p.setDeposito(0d); } catch (Exception ignore) {}
            try { p.setEfectivo(efectivo); } catch (Exception ignore) {}
            return p;
        }

        /* ==========================================================
        ========== UTILIDADES DE IMPRESIÓN (AS IS, TUS FORMATOS) ===
        ========================================================== */

        /** Diálogo de impresión con espera y confirmación. */
        private void imprimirYConfirmarAsync(Printable printable, Runnable onOk, Runnable onFail) {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintable(printable);
                if (!job.printDialog()) { onFail.run(); return; }

                JDialog wait = new JDialog(SwingUtilities.getWindowAncestor(this), "Imprimiendo…",
                        Dialog.ModalityType.DOCUMENT_MODAL);
                JPanel p = new JPanel(new BorderLayout(10,10));
                p.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));
                p.add(new JLabel("Esperando confirmación de la impresora…"), BorderLayout.NORTH);
                javax.swing.JProgressBar bar = new javax.swing.JProgressBar(); bar.setIndeterminate(true);
                p.add(bar, BorderLayout.CENTER);
                wait.setContentPane(p);
                wait.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                wait.setSize(360,110);
                wait.setLocationRelativeTo(this);

                new Thread(() -> {
                    try {
                        job.print();
                        SwingUtilities.invokeLater(() -> { wait.dispose(); onOk.run(); });
                    } catch (PrinterException ex) {
                        SwingUtilities.invokeLater(() -> { wait.dispose(); onFail.run(); });
                    }
                }, "reprint-job").start();

                wait.setVisible(true);
            } catch (Exception ex) {
                onFail.run();
            }
        }

        // ---------- Empresa ----------
        private static class EmpresaInfo {
            String razonSocial, nombreFiscal, rfc;
            String calleNumero, colonia, cp, ciudad, estado;
            String whatsapp, telefono, instagram, facebook, tiktok, correo, web;
            BufferedImage logo;
        }

        /** Lee la nota origen de una DV desde la tabla Devoluciones. */
    private Integer leerNotaOrigenPorDV(int numeroDv) {
        final String sql = "SELECT nota_origen FROM Devoluciones WHERE numero_nota_dv=?";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
            java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroDv);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }
    /** Lee la nota origen de un abono desde la tabla Notas (campo nota_relacionada). */
    private Integer leerNotaOrigenPorAbono(int numeroAbono) {
        final String sql = "SELECT nota_relacionada FROM Notas WHERE numero_nota = ?";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
            java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroAbono);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }



    /** Cabecera mínima de una nota para calcular pagos actuales. */
    private static class NotaHead { String tipo; double total; double saldo; String folio; }

private NotaHead leerNotaHead(int numeroNota) {
    final String sql = "SELECT tipo, COALESCE(total,0), COALESCE(saldo,0), folio " +
                       "FROM Notas WHERE numero_nota=?";
    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setInt(1, numeroNota);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                NotaHead h = new NotaHead();
                h.tipo  = rs.getString(1);
                h.total = rs.getDouble(2);
                h.saldo = rs.getDouble(3);
                h.folio = rs.getString(4);   // <-- aquí llenas el folio
                return h;
            }
        }
    } catch (Exception ignore) { }
    return null;
}
 /** Hace setTotal(...) ya sea con Double o con double por reflexión. */
    private void setNotaTotalSeguro(Object nota, double valor) {
        try {
            nota.getClass().getMethod("setTotal", Double.class).invoke(nota, valor);
            return;
        } catch (Exception ignore) { }
        try {
            nota.getClass().getMethod("setTotal", double.class).invoke(nota, valor);
        } catch (Exception ignore) { }
    }

        // Lee de BD el número (PK) de la empresa a usar para impresión. Toma la primera fila.
        private Integer obtenerNumeroEmpresaBD() {
            final String sql = "SELECT numero_empresa FROM Empresa ORDER BY numero_empresa LIMIT 1";
            try (java.sql.Connection cn = Conexion.Conecta.getConnection();
                java.sql.PreparedStatement ps = cn.prepareStatement(sql);
                java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                }
            } catch (Exception ignore) { }
            return null;
        }

        private EmpresaInfo cargarEmpresaInfo() {
            EmpresaInfo info = new EmpresaInfo();
            try {
                EmpresaDAO edao = new EmpresaDAO();
                Integer numEmpresa = obtenerNumeroEmpresaBD();
                Empresa e = edao.buscarPorNumero(numEmpresa);
                if (e != null) {
                    info.razonSocial  = n(e.getRazonSocial());
                    info.nombreFiscal = n(e.getNombreFiscal());
                    info.rfc          = n(e.getRfc());
                    info.calleNumero  = n(e.getCalleNumero());
                    info.colonia      = n(e.getColonia());
                    info.cp           = n(e.getCodigoPostal());
                    info.ciudad       = n(e.getCiudad());
                    info.estado       = n(e.getEstado());
                    info.whatsapp     = n(e.getWhatsapp());
                    info.telefono     = n(e.getTelefono());
                    info.instagram    = n(e.getInstagram());
                    info.facebook     = n(e.getFacebook());
                    info.tiktok       = n(e.getTiktok());
                    info.correo       = n(e.getCorreo());
                    info.web          = n(e.getPaginaWeb());
                    if (e.getLogo()!=null) {
                        try { info.logo = ImageIO.read(new ByteArrayInputStream(e.getLogo())); } catch(Exception ignore){}
                    }
                }
            } catch (Exception ex) {
                info.razonSocial = "MIANOVIAS";
            }
            return info;
        }

        /* ==========================================================
        =============== FORMATOS (CONTADO / CRÉDITO) =============
        ========================================================== */

    // ===== CONTADO ===== (formato tal cual)
        private Printable construirPrintableContadoEmpresarial(
                EmpresaInfo emp, Modelo.Nota n, List<Modelo.NotaDetalle> dets, Modelo.PagoFormas p,
                java.time.LocalDate fechaEntregaMostrar, java.time.LocalDate fechaEventoMostrar,
                String asesorNombre, String folioTxt,
                String clienteNombreFallback, String tel2Fallback, String observacionesTexto, java.util.List<String> obsequiosLineas, java.time.LocalDate fechaNotaCr, int cajeraCodigo, String cajeraNombre) {

            final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

            final String tel1      = (n.getTelefono() == null) ? "" : n.getTelefono();
            
            final String fechaVentaStr =
                (fechaNotaCr != null) ? fechaNotaCr.format(MX)
                                    : java.time.LocalDate.now().format(MX);
            final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaEntregaMostrar.format(MX);
            final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaEventoMostrar.format(MX);
            final double total     = (n.getTotal() == null) ? 0d : n.getTotal();

            final double tc = (p.getTarjetaCredito()   == null) ? 0d : p.getTarjetaCredito();
            final double td = (p.getTarjetaDebito()    == null) ? 0d : p.getTarjetaDebito();
            final double am = (p.getAmericanExpress()  == null) ? 0d : p.getAmericanExpress();
            final double tr = (p.getTransferencia()    == null) ? 0d : p.getTransferencia();
            final double dp = (p.getDeposito()         == null) ? 0d : p.getDeposito();
            final double ef = (p.getEfectivo()         == null) ? 0d : p.getEfectivo();

            final String cliNombre;
            final String cliTel2;
            final String cliPrueba1;
            final String cliPrueba2;
            String tmpNombre  = (clienteNombreFallback == null) ? "" : clienteNombreFallback;
            String tmpTel2    = (tel2Fallback == null) ? "" : tel2Fallback;
            String tmpPrueba1 = "";
            String tmpPrueba2 = "";
            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
                if (cr != null) {
                    if (cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank())
                        tmpNombre = cr.getNombreCompleto();
                    if (cr.getTelefono2() != null) tmpTel2 = cr.getTelefono2();
                    if (cr.getFechaPrueba1() != null) tmpPrueba1 = cr.getFechaPrueba1().format(MX);
                    if (cr.getFechaPrueba2() != null) tmpPrueba2 = cr.getFechaPrueba2().format(MX);
                }
            } catch (Exception ignore) { }
            cliNombre = tmpNombre;
            cliTel2   = tmpTel2;
            cliPrueba1 = tmpPrueba1;
            cliPrueba2 = tmpPrueba2;

            String medBusto = "", medCintura = "", medCadera = "";
            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
                if (raw != null) {
                    for (java.util.Map.Entry<String,String> e : raw.entrySet()) {
                        String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                        String v = e.getValue() == null ? "" : e.getValue();
                        if (k.equals("busto"))      medBusto   = v;
                        else if (k.equals("cintura")) medCintura = v;
                        else if (k.equals("cadera"))  medCadera  = v;
                    }
                }
            } catch (Exception ignore) { }

            StringBuilder _med = new StringBuilder();
            if (!medBusto.isBlank())   { _med.append("Busto: ").append(medBusto); }
            if (!medCintura.isBlank()){ if (_med.length()>0) _med.append("   "); _med.append("Cintura: ").append(medCintura); }
            if (!medCadera.isBlank())  { if (_med.length()>0) _med.append("   "); _med.append("Cadera: ").append(medCadera); }
            final String medidasFmt = _med.toString();

            final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;


            return new Printable() {
                @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                    if (pageIndex > 0) return NO_SUCH_PAGE;
                    Graphics2D g2 = (Graphics2D) g;

                    final int MARGIN = 36;
                    final int x0 = (int) Math.round(pf.getImageableX());
                    final int y0 = (int) Math.round(pf.getImageableY());
                    final int W  = (int) Math.round(pf.getImageableWidth());
                    int x = x0 + MARGIN;
                    int y = y0 + MARGIN;
                    int w = W  - (MARGIN * 2);

                    final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                    final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                    final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                    final Font fText    = g2.getFont().deriveFont(10.2f);
                    final Font fSmall   = g2.getFont().deriveFont(8.8f);

                    int leftTextX = x;
                    int headerH   = 0;
                    if (emp.logo != null) {
                        int hLogo = 56;
                        int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                        g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                        leftTextX = x + wLogo + 16;
                        headerH   = Math.max(headerH, hLogo);
                    }

                    int infoRightWidth = w - (leftTextX - x);
                    int infoTextWidth  = infoRightWidth - 160;

                    g2.setFont(fH1);
                    int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                    g2.setFont(fSmall);
                    yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                    yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);

                    String dir = joinNonBlank(", ",
                            emp.calleNumero, emp.colonia,
                            (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                            emp.ciudad, emp.estado);
                    yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);

                    yy = drawWrapped(g2, joinNonBlank("   ",
                            labelIf("Tel: ", emp.telefono),
                            labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

                    g2.setFont(fH1);
                    rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                    final int rightColW = 120;
                    final int xRight    = x + w - rightColW;
                    int yRightHdr       = y + 22;

                    g2.setFont(fSmall);
                    yRightHdr = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRightHdr, rightColW);
                    yRightHdr = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRightHdr, rightColW);

                    BufferedImage igIcon = loadIcon("instagram.png");
                    BufferedImage fbIcon = loadIcon("facebook.png");
                    BufferedImage ttIcon = loadIcon("tiktok.png");

                    yRightHdr = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRightHdr, rightColW, 12, 6);
                    yRightHdr = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRightHdr, rightColW, 12, 6);
                    yRightHdr = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRightHdr, rightColW, 12, 6);

                    int leftBlockH  = Math.max(yy - y, headerH);
                    int rightBlockH = Math.max(0, yRightHdr - y);
                    int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;
                    int afterTail   = y + usedHeader;

                    g2.setFont(fTitle);
                    center(g2, "NOTA DE VENTA CONTADO", x, w, afterTail + 14);
                    y = afterTail + 32;

                    g2.setFont(fSection);
                    g2.drawString("Datos del cliente", x, y);
                    y += 13;

                    final int gapCols = 24;
                    final int leftW   = (w - gapCols) / 2;
                    final int rightW  = w - gapCols - leftW;

                    int yLeft  = y;
                    int yRight2 = y;

                    g2.setFont(fText);
                    yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(cliNombre)), x, yLeft, leftW);
                    yLeft  = drawWrapped(g2, joinNonBlank("   ",
                            labelIf("Teléfono: ", safe(tel1)),
                            labelIf("Teléfono 2: ", safe(cliTel2))), x, yLeft + 2, leftW);
                    if (!medidasFmt.isBlank()) yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);

                    yRight2 = drawWrapped(g2, labelIf("Fecha de venta: ", fechaVentaStr), x + leftW + gapCols, yRight2, rightW);
                    if (!fEvento.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de evento: ", fEvento), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!cliPrueba1.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", cliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!cliPrueba2.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", cliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!fEntrega.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de entrega: ", fEntrega), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (asesorNombre != null && !asesorNombre.isBlank())
                        yRight2 = drawWrapped(g2, labelIf("Asesora: ", asesorNombre), x + leftW + gapCols, yRight2 + 2, rightW);

                    y = Math.max(yLeft, yRight2) + 10;

                    final int colSubW  = 95;
                    final int colDescW = 70;
                    final int colPreW  = 100;
                    final int gap = 16;
                    final int colArtW = w - (colSubW + colDescW + colPreW + (gap * 3));
                    final int xArt = x;
                    final int xPre = xArt + colArtW + gap;
                    final int xDes = xPre + colPreW + gap;
                    final int xSub = xDes + colDescW + gap;

                    g2.drawLine(x, y, x + w, y); y += 15;
                    g2.setFont(fText);
                    g2.drawString("Artículo", xArt, y);
                    g2.drawString("Precio",   xPre, y);
                    g2.drawString("%Desc",    xDes, y);
                    g2.drawString("Subtotal", xSub, y);
                    y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                    for (Modelo.NotaDetalle d : dets) {
                        String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                                ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                        String detalle = (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty() ? d.getCodigoArticulo() + " · " : "")
                                + safe(artBase)
                                + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                                + " | " + labelIf("Color: ", safe(d.getColor()))
                                + " | " + labelIf("Talla: ", safe(d.getTalla()));

                        int yRowStart = y;
                        int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                        g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                        g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                        g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                        y = yAfter + 12;
                    }
    // === Observaciones (si existen) ===
    if (observacionesTexto != null && !observacionesTexto.isBlank()) {
        y += 10;
        g2.setFont(fSection);
        g2.drawString("Observaciones", x, y);
        y += 12;
        g2.setFont(fText);
        y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
        y += 10;
    }

                    y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                    
                    // TOTAL a la derecha
                    g2.setFont(fH1);
                    rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y);
                    y += 22;
                    // Total con letra (debajo del total, a la izquierda)
                    g2.setFont(fText);
                    java.math.BigDecimal bdTotal = java.math.BigDecimal
                    .valueOf(total)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
                    String pagadoLetra = numeroALetras(bdTotal.doubleValue());
                    int anchoLetras = w - 230;
                    int yInicioTotales = y - (24 + 14 + 22);
                    drawWrapped(g2, "Cantidad pagada con letra: " + pagadoLetra, x, yInicioTotales + 2, anchoLetras);

                    g2.setFont(fSection);
                    g2.drawString("Pagos", x, y); y += 12;
                    g2.setFont(fText);

                    final int gapCols2 = 24;
                    final int leftW2   = (w - gapCols2) / 2;
                    final int rightW2  = w - gapCols2 - leftW2;

                    int yPLeft  = y;
                    int yPRight = y;

                    yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW2);
                    yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW2);
                    yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW2);

                    yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW2 + gapCols2, yPRight, rightW2);
                    yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                    yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW2 + gapCols2, yPRight + 2, rightW2);

                    y = Math.max(yPLeft, yPRight) + 8;

                    final java.util.List<String> obsequiosPrint =
                (obsequiosLineas == null) ? java.util.Collections.emptyList() : obsequiosLineas;



    if (obsequiosPrint != null && !obsequiosPrint.isEmpty()) {
        y += 10;
        g2.drawLine(x, y, x + w, y);
        y += 16;

        g2.setFont(fSection);
        g2.drawString("Obsequios incluidos", x, y);
        y += 12;

        g2.setFont(fText);

        final int gap2    = 16;
        final int colCodW = 80;
        final int colNomW = w - colCodW - gap2;

        final int xCod = x;
        final int xNom = xCod + colCodW + gap2;

        g2.drawString("Código",   xCod, y);
        g2.drawString("Obsequio", xNom, y);
        y += 10;
        g2.drawLine(x, y, x + w, y);
        y += 14;

        // 1) separar códigos y descripción manual
        java.util.List<String> codigos = new java.util.ArrayList<>();
        java.util.Map<String,String> descManual = new java.util.HashMap<>();

        for (String raw : obsequiosPrint) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String codigo = s;
            String desc   = "";

            int guion = s.indexOf('-');
            if (guion > 0) {
                codigo = s.substring(0, guion).trim();
                desc   = s.substring(guion + 1).trim();
            }

            codigos.add(codigo);
            if (!desc.isEmpty()) {
                descManual.put(codigo, desc);
            }
        }

        // 2) intentar completar desde InventarioObsequios
        java.util.Map<String,String> info = new java.util.HashMap<>();
        if (!codigos.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
                    "FROM InventarioObsequios WHERE codigo_articulo IN (");
            for (int i = 0; i < codigos.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(')');

            try (Connection cn = Conexion.Conecta.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql.toString())) {

                for (int i = 0; i < codigos.size(); i++) {
                    ps.setString(i + 1, codigos.get(i));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String c      = rs.getString("codigo_articulo");
                        String nombre = safe(rs.getString("articulo"));
                        String marca  = safe(rs.getString("marca"));
                        String modelo = safe(rs.getString("modelo"));
                        String talla  = safe(rs.getString("talla"));
                        String color  = safe(rs.getString("color"));

                        StringBuilder desc = new StringBuilder();
                        if (!nombre.isBlank()) desc.append(nombre);

                        String mm = "";
                        if (!marca.isBlank()) mm = marca;
                        if (!modelo.isBlank()) {
                            if (!mm.isEmpty()) mm += " ";
                            mm += modelo;
                        }
                        if (!mm.isEmpty()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append('(').append(mm).append(')');
                        }
                        if (!talla.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Talla ").append(talla);
                        }
                        if (!color.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Color ").append(color);
                        }
                        info.put(c, desc.toString());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace(); // si truena, al menos usamos lo manual
            }
        }

        // 3) pintar filas
        for (String raw : obsequiosPrint) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String codigo = s;
            String descManualRow = "";
            int guion = s.indexOf('-');
            if (guion > 0) {
                codigo = s.substring(0, guion).trim();
                descManualRow = s.substring(guion + 1).trim();
            }

            String descripcion = info.get(codigo);
            if (descripcion == null || descripcion.isBlank()) {
                if (!descManualRow.isEmpty())      descripcion = descManualRow;
                else if (!s.equals(codigo))        descripcion = s;
                else                               descripcion = "";
            }

            int yCod = drawWrapped(g2, codigo,      xCod, y, colCodW);
            int yNom = drawWrapped(g2, descripcion, xNom, y, colNomW);
            y = Math.max(yCod, yNom);
        }
    }

                    // Línea de cajera (si hay datos)
    y += 10;
    g2.setFont(fText);

    boolean tieneCodigo = cajeraCodigo > 0;
    boolean tieneNombre = (cajeraNombre != null && !cajeraNombre.isBlank());

    String cajeraLine = "Cajera: ";
    if (tieneCodigo) {
        cajeraLine += cajeraCodigo;
    }
    if (tieneNombre) {
        if (tieneCodigo) {
            cajeraLine += " - " + cajeraNombre;
        } else {
            cajeraLine += cajeraNombre;
        }
    }

    g2.drawString(cajeraLine, x, y);

                    return PAGE_EXISTS;
                }

                // helpers (idénticos a Contado)
                private String safe(String s){ return (s == null) ? "" : s.trim(); }
                private String fmt2(Double v){ return fmtMoneda(v); }
                private String coalesce(String a, String b, String def){
                    if (a != null && !a.isBlank()) return a;
                    if (b != null && !b.isBlank()) return b;
                    return (def == null) ? "" : def;
                }
                private String labelIf(String label, String val){
                    if (val == null || val.isBlank()) return "";
                    return label + val;
                }
                private String trimJoin(String sep, String... vals){
                    StringBuilder sb = new StringBuilder();
                    for (String v : vals){
                        v = (v==null? "": v.trim());
                        if (!v.isEmpty()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(v);
                        }
                    }
                    return sb.toString();
                }
                private String joinNonBlank(String sep, String... parts){
                    StringBuilder sb = new StringBuilder();
                    for (String s : parts){
                        if (s != null && !s.isBlank()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(s);
                        }
                    }
                    return sb.toString();
                }
                private void center(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int cx = x + (w - fm.stringWidth(s)) / 2;
                    g2.drawString(s, cx, y);
                }
                private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int rx = x + w - fm.stringWidth(s);
                    g2.drawString(s, rx, y);
                }
                private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                    if (text == null) return y;
                    text = text.trim();
                    if (text.isEmpty()) return y;
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    String[] words = text.split("\\s+");
                    StringBuilder line = new StringBuilder();
                    for (String w : words){
                        String tryLine = (line.length()==0 ? w : line + " " + w);
                        if (fm.stringWidth(tryLine) <= maxWidth){
                            line.setLength(0); line.append(tryLine);
                        } else {
                            g2.drawString(line.toString(), x, y);
                            y += 12;
                            line.setLength(0); line.append(w);
                        }
                    }
                    if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                    return y + 12;
                }
                private BufferedImage loadIcon(String pathOrName) {
                    try {
                        String name = pathOrName;
                        int slash = name.lastIndexOf('/');
                        if (slash >= 0) name = name.substring(slash + 1);

                        java.nio.file.Path override = java.nio.file.Paths.get(System.getProperty("user.dir"), "icons", name);
                        if (java.nio.file.Files.exists(override)) {
                            javax.imageio.ImageIO.setUseCache(false);
                            try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                                return trimTransparent(javax.imageio.ImageIO.read(in));
                            }
                        }

                        String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                        try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                            if (in == null) return null;
                            javax.imageio.ImageIO.setUseCache(false);
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    } catch (Exception e) { return null; }
                }
                private BufferedImage trimTransparent(BufferedImage src) {
                    if (src == null) return null;
                    int w = src.getWidth(), h = src.getHeight();
                    int minX = w, minY = h, maxX = -1, maxY = -1;
                    int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                    for (int y = 0; y < h; y++) {
                        int off = y * w;
                        for (int x = 0; x < w; x++) {
                            if (((p[off + x] >>> 24) & 0xFF) != 0) {
                                if (x < minX) minX = x; if (x > maxX) maxX = x;
                                if (y < minY) minY = y; if (y > maxY) maxY = y;
                            }
                        }
                    }
                    if (maxX < minX || maxY < minY) return src;
                    BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                            minX, minY, maxX + 1, maxY + 1, null);
                    g.dispose();
                    return out;
                }
                private String numeroALetras(double valor) {
                    long pesos = Math.round(Math.floor(valor + 1e-6));
                    int centavos = (int) Math.round((valor - pesos) * 100.0);
                    if (centavos == 100) { pesos += 1; centavos = 0; }

                    if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

                    final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                            "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
                    final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
                    final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                            "seiscientos", "setecientos", "ochocientos", "novecientos"};

                    java.util.function.Function<Integer,String> tres = n -> {
                        if (n == 0) return "";
                        if (n == 100) return "cien";
                        int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
                        String s = (c > 0 ? CE[c] : "");
                        if (r > 0) {
                            if (!s.isEmpty()) s += " ";
                            if (r < 30) s += UN[r];
                            else {
                                s += DE[d];
                                if (u > 0) s += " y " + UN[u];
                            }
                        }
                        return s.trim();
                    };

                    StringBuilder sb = new StringBuilder();
                    int millones = (int)((pesos / 1_000_000) % 1000);
                    int miles    = (int)((pesos / 1_000) % 1000);
                    int cientos  = (int)(pesos % 1000);

                    long milesDeMillones = pesos / 1_000_000_000L;
                    if (milesDeMillones > 0) {
                        sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
                        if (millones>0 || miles>0 || cientos>0) sb.append(" ");
                    }
                    if (millones > 0) {
                        sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
                        if (miles>0 || cientos>0) sb.append(" ");
                    }
                    if (miles > 0) {
                        if (miles == 1) sb.append("mil");
                        else sb.append(tres.apply(miles)).append(" mil");
                        if (cientos>0) sb.append(" ");
                    }
                    if (cientos > 0) sb.append(tres.apply(cientos));

                    String texto = sb.toString().trim();
                    texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
                    texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

                    return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
                }
                private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                        int x, int y, int maxWidth, int iconSize, int gapPx) {
                    if ((text == null || text.isBlank()) && icon == null) return y;
                    int tx = x;
                    int baseline = y;
                    if (icon != null) {
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY  = baseline - Math.min(iconSize, ascent);
                        g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                        tx += iconSize + gapPx;
                    }
                    return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
                }
            };
        }


                // ===== helpers =====
                private String safe(String s){ return (s == null) ? "" : s.trim(); }
                private String fmt2(Double v){ return fmtMoneda(v); }
                private String coalesce(String a, String b, String def){
                    if (a != null && !a.isBlank()) return a;
                    if (b != null && !b.isBlank()) return b;
                    return (def == null) ? "" : def;
                }
                private String labelIf(String label, String val){
                    if (val == null || val.isBlank()) return "";
                    return label + val;
                }
                private String trimJoin(String sep, String... vals){
                    StringBuilder sb = new StringBuilder();
                    for (String v : vals){
                        v = safe(v);
                        if (!v.isBlank()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(v);
                        }
                    }
                    return sb.toString();
                }
                private String joinNonBlank(String sep, String... parts){
                    StringBuilder sb = new StringBuilder();
                    for (String s : parts){
                        if (s != null && !s.isBlank()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(s);
                        }
                    }
                    return sb.toString();
                }
                private void center(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int cx = x + (w - fm.stringWidth(s)) / 2;
                    g2.drawString(s, cx, y);
                }
                private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int rx = x + w - fm.stringWidth(s);
                    g2.drawString(s, rx, y);
                }
                private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                    if (text == null) return y;
                    text = text.trim();
                    if (text.isEmpty()) return y;
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    String[] words = text.split("\\s+");
                    StringBuilder line = new StringBuilder();
                    for (String w : words){
                        String tryLine = (line.length()==0 ? w : line + " " + w);
                        if (fm.stringWidth(tryLine) <= maxWidth){
                            line.setLength(0); line.append(tryLine);
                        } else {
                            g2.drawString(line.toString(), x, y);
                            y += 12;
                            line.setLength(0); line.append(w);
                        }
                    }
                    if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                    return y + 12;
                }
                private BufferedImage loadIcon(String pathOrName) {
                    try {
                        String name = pathOrName;
                        int slash = name.lastIndexOf('/');
                        if (slash >= 0) name = name.substring(slash + 1);

                        java.nio.file.Path override = java.nio.file.Paths.get(
                                System.getProperty("user.dir"), "icons", name);
                        if (java.nio.file.Files.exists(override)) {
                            javax.imageio.ImageIO.setUseCache(false);
                            try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                                return trimTransparent(javax.imageio.ImageIO.read(in));
                            }
                        }

                        String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                        try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                            if (in == null) return null;
                            javax.imageio.ImageIO.setUseCache(false);
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    } catch (Exception e) { return null; }
                }
                private static BufferedImage trimTransparent(BufferedImage src) {
                    if (src == null) return null;
                    int w = src.getWidth(), h = src.getHeight();
                    int minX = w, minY = h, maxX = -1, maxY = -1;
                    int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                    for (int y = 0; y < h; y++) {
                        int off = y * w;
                        for (int x = 0; x < w; x++) {
                            if (((p[off + x] >>> 24) & 0xFF) != 0) {
                                if (x < minX) minX = x; if (x > maxX) maxX = x;
                                if (y < minY) minY = y; if (y > maxY) maxY = y;
                            }
                        }
                    }
                    if (maxX < minX || maxY < minY) return src;
                    BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                            minX, minY, maxX + 1, maxY + 1, null);
                    g.dispose();
                    return out;
                }
                private String numeroALetras(double valor) {
                    long pesos = Math.round(Math.floor(valor + 1e-6));
                    int centavos = (int) Math.round((valor - pesos) * 100.0);
                    if (centavos == 100) { pesos += 1; centavos = 0; }

                    if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

                    final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                            "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
                    final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
                    final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                            "seiscientos", "setecientos", "ochocientos", "novecientos"};

                    java.util.function.Function<Integer,String> tres = n -> {
                        if (n == 0) return "";
                        if (n == 100) return "cien";
                        int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
                        String s = (c > 0 ? CE[c] : "");
                        if (r > 0) {
                            if (!s.isEmpty()) s += " ";
                            if (r < 30) s += UN[r];
                            else {
                                s += DE[d];
                                if (u > 0) s += " y " + UN[u];
                            }
                        }
                        return s.trim();
                    };

                    StringBuilder sb = new StringBuilder();
                    int millones = (int)((pesos / 1_000_000) % 1000);
                    int miles    = (int)((pesos / 1_000) % 1000);
                    int cientos  = (int)(pesos % 1000);

                    long milesDeMillones = pesos / 1_000_000_000L;
                    if (milesDeMillones > 0) {
                        sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
                        if (millones>0 || miles>0 || cientos>0) sb.append(" ");
                    }
                    if (millones > 0) {
                        sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
                        if (miles>0 || cientos>0) sb.append(" ");
                    }
                    if (miles > 0) {
                        if (miles == 1) sb.append("mil");
                        else sb.append(tres.apply(miles)).append(" mil");
                        if (cientos>0) sb.append(" ");
                    }
                    if (cientos > 0) sb.append(tres.apply(cientos));

                    String texto = sb.toString().trim();
                    texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
                    texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

                    return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
                }
                private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                        int x, int y, int maxWidth, int iconSize, int gapPx) {
                    if ((text == null || text.isBlank()) && icon == null) return y;
                    int tx = x;
                    int baseline = y;
                    if (icon != null) {
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY  = baseline - Math.min(iconSize, ascent);
                        g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                        tx += iconSize + gapPx;
                    }
                    return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
                }
            
        

        // ===== CRÉDITO (ANTICIPO) ===== (formato tal cual)
        private Printable construirPrintableCreditoEmpresarial(
                EmpresaInfo emp, Modelo.Nota n, List<Modelo.NotaDetalle> dets, Modelo.PagoFormas p,
                java.time.LocalDate fechaEntregaMostrar, java.time.LocalDate fechaEventoMostrar,
                String asesorNombre, String folioTxt,
                String clienteNombreFallback, String tel2Fallback, String observacionesTexto, java.util.List<String> obsequiosLineas, java.time.LocalDate fechaNotaCr, int cajeraCodigo, String cajeraNombre) {

            final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

            final String tel1      = (n.getTelefono() == null) ? "" : n.getTelefono();
            
            final String fechaVentaStr =
                (fechaNotaCr != null) ? fechaNotaCr.format(MX)
                                    : java.time.LocalDate.now().format(MX);
            final String fEntrega  = (fechaEntregaMostrar == null) ? "" : fechaEntregaMostrar.format(MX);
            final String fEvento   = (fechaEventoMostrar  == null) ? "" : fechaEventoMostrar.format(MX);
            final double total     = (n.getTotal() == null) ? 0d : n.getTotal();

            final double tc = (p.getTarjetaCredito()   == null) ? 0d : p.getTarjetaCredito();
            final double td = (p.getTarjetaDebito()    == null) ? 0d : p.getTarjetaDebito();
            final double am = (p.getAmericanExpress()  == null) ? 0d : p.getAmericanExpress();
            final double tr = (p.getTransferencia()    == null) ? 0d : p.getTransferencia();
            final double dp = (p.getDeposito()         == null) ? 0d : p.getDeposito();
            final double ef = (p.getEfectivo()         == null) ? 0d : p.getEfectivo();
            final double anticipo = tc + td + am + tr + dp + ef;
            final double saldo    = (n.getSaldo() != null) ? n.getSaldo() : Math.max(0d, total - anticipo);

            final String cliNombre;
            final String cliTel2;
            final String cliPrueba1;
            final String cliPrueba2;
            String tmpNombre  = (clienteNombreFallback == null) ? "" : clienteNombreFallback;
            String tmpTel2    = (tel2Fallback == null) ? "" : tel2Fallback;
            String tmpPrueba1 = "";
            String tmpPrueba2 = "";
            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
                if (cr != null) {
                    if (cr.getNombreCompleto() != null && !cr.getNombreCompleto().isBlank())
                        tmpNombre = cr.getNombreCompleto();
                    if (cr.getTelefono2() != null) tmpTel2 = cr.getTelefono2();
                    if (cr.getFechaPrueba1() != null) tmpPrueba1 = cr.getFechaPrueba1().format(MX);
                    if (cr.getFechaPrueba2() != null) tmpPrueba2 = cr.getFechaPrueba2().format(MX);
                }
            } catch (Exception ignore) { }
            cliNombre = tmpNombre;
            cliTel2   = tmpTel2;
            cliPrueba1 = tmpPrueba1;
            cliPrueba2 = tmpPrueba2;

            String medBusto = "", medCintura = "", medCadera = "";
            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
                if (raw != null) {
                    for (java.util.Map.Entry<String,String> e : raw.entrySet()) {
                        String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                        String v = e.getValue() == null ? "" : e.getValue();
                        if (k.equals("busto"))      medBusto   = v;
                        else if (k.equals("cintura")) medCintura = v;
                        else if (k.equals("cadera"))  medCadera  = v;
                    }
                }
            } catch (Exception ignore) { }

            StringBuilder _med = new StringBuilder();
            if (!medBusto.isBlank())   { _med.append("Busto: ").append(medBusto); }
            if (!medCintura.isBlank()){ if (_med.length()>0) _med.append("   "); _med.append("Cintura: ").append(medCintura); }
            if (!medCadera.isBlank())  { if (_med.length()>0) _med.append("   "); _med.append("Cadera: ").append(medCadera); }
            final String medidasFmt = _med.toString();

            final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;


            return new Printable() {
                @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                    if (pageIndex > 0) return NO_SUCH_PAGE;
                    Graphics2D g2 = (Graphics2D) g;

                    final int MARGIN = 36;
                    final int x0 = (int) Math.round(pf.getImageableX());
                    final int y0 = (int) Math.round(pf.getImageableY());
                    final int W  = (int) Math.round(pf.getImageableWidth());
                    int x = x0 + MARGIN;
                    int y = y0 + MARGIN;
                    int w = W  - (MARGIN * 2);

                    final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                    final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                    final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                    final Font fText    = g2.getFont().deriveFont(10.2f);
                    final Font fSmall   = g2.getFont().deriveFont(8.8f);

                    int leftTextX = x;
                    int headerH   = 0;
                    if (emp.logo != null) {
                        int hLogo = 56;
                        int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                        g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                        leftTextX = x + wLogo + 16;
                        headerH   = Math.max(headerH, hLogo);
                    }

                    int infoRightWidth = w - (leftTextX - x);
                    int infoTextWidth  = infoRightWidth - 160;

                    g2.setFont(fH1);
                    int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                    g2.setFont(fSmall);
                    yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                    yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);

                    String dir = joinNonBlank(", ",
                            emp.calleNumero, emp.colonia,
                            (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                            emp.ciudad, emp.estado);
                    yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);

                    yy = drawWrapped(g2, joinNonBlank("   ",
                            labelIf("Tel: ", emp.telefono),
                            labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

                    g2.setFont(fH1);
                    rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                    final int rightColW = 120;
                    final int xRight    = x + w - rightColW;
                    int yRightHdr       = y + 22;

                    g2.setFont(fSmall);
                    yRightHdr = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRightHdr, rightColW);
                    yRightHdr = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRightHdr, rightColW);

                    BufferedImage igIcon = loadIcon("instagram.png");
                    BufferedImage fbIcon = loadIcon("facebook.png");
                    BufferedImage ttIcon = loadIcon("tiktok.png");

                    yRightHdr = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRightHdr, rightColW, 12, 6);
                    yRightHdr = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRightHdr, rightColW, 12, 6);
                    yRightHdr = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRightHdr, rightColW, 12, 6);

                    int leftBlockH  = Math.max(yy - y, headerH);
                    int rightBlockH = Math.max(0, yRightHdr - y);
                    int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;
                    int afterTail   = y + usedHeader;

                    g2.setFont(fTitle);
                    center(g2, "NOTA DE VENTA CRÉDITO", x, w, afterTail + 14);
                    y = afterTail + 32;

                    g2.setFont(fSection);
                    g2.drawString("Datos del cliente", x, y);
                    y += 13;

                    final int gapCols = 24;
                    final int leftW   = (w - gapCols) / 2;
                    final int rightW  = w - gapCols - leftW;

                    int yLeft  = y;
                    int yRight2 = y;

                    g2.setFont(fText);
                    yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(cliNombre)), x, yLeft, leftW);
                    yLeft  = drawWrapped(g2, joinNonBlank("   ",
                            labelIf("Teléfono: ", safe(tel1)),
                            labelIf("Teléfono 2: ", safe(cliTel2))), x, yLeft + 2, leftW);
                    if (!medidasFmt.isBlank()) yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);

                    yRight2 = drawWrapped(g2, labelIf("Fecha de venta: ", fechaVentaStr), x + leftW + gapCols, yRight2, rightW);
                    if (!fEvento.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de evento: ", fEvento), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!cliPrueba1.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", cliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!cliPrueba2.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", cliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!fEntrega.isEmpty())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de entrega: ", fEntrega), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (asesorNombre != null && !asesorNombre.isBlank())
                        yRight2 = drawWrapped(g2, labelIf("Asesora: ", asesorNombre), x + leftW + gapCols, yRight2 + 2, rightW);

                    y = Math.max(yLeft, yRight2) + 10;

                    final int colSubW  = 95;
                    final int colDescW = 70;
                    final int colPreW  = 100;
                    final int gap = 16;
                    final int colArtW = w - (colSubW + colDescW + colPreW + (gap * 3));
                    final int xArt = x;
                    final int xPre = xArt + colArtW + gap;
                    final int xDes = xPre + colPreW + gap;
                    final int xSub = xDes + colDescW + gap;

                    g2.drawLine(x, y, x + w, y); y += 15;
                    g2.setFont(fText);
                    g2.drawString("Artículo", xArt, y);
                    g2.drawString("Precio",   xPre, y);
                    g2.drawString("%Desc",    xDes, y);
                    g2.drawString("Subtotal", xSub, y);
                    y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                    for (Modelo.NotaDetalle d : dets) {
                        String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                                ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                        String detalle = (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty() ? d.getCodigoArticulo() + " · " : "")
                                + safe(artBase)
                                + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                                + " | " + labelIf("Color: ", safe(d.getColor()))
                                + " | " + labelIf("Talla: ", safe(d.getTalla()));

                        int yRowStart = y;
                        int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                        g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                        g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                        g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                        y = yAfter + 12;
                    }
    // === Observaciones (si existen) ===
    if (observacionesTexto != null && !observacionesTexto.isBlank()) {
        y += 10;
        g2.setFont(fSection);
        g2.drawString("Observaciones", x, y);
        y += 12;
        g2.setFont(fText);
        y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
        y += 10;
    }

                    y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                    g2.setFont(fH1);
                    rightAlign(g2, "TOTAL: $" + fmt2(total), x, w, y); y += 22;

                    g2.setFont(fText);
                    rightAlign(g2, "Anticipo: $" + fmt2(anticipo), x, w, y); y += 14;

                    g2.setFont(fH1);
                    rightAlign(g2, "SALDO RESTANTE: $" + fmt2(saldo), x, w, y); y += 22;

                    g2.setFont(fText);
                    java.math.BigDecimal bdAnticipo = java.math.BigDecimal
                            .valueOf(anticipo)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    String pagadoLetra = numeroALetras(bdAnticipo.doubleValue());
                    int anchoLetras = w - 230;
                    int yInicioTotales = y - (24 + 14 + 22);
                    drawWrapped(g2, "Cantidad pagada con letra: " + pagadoLetra, x, yInicioTotales + 2, anchoLetras);

                    g2.setFont(fSection);
                    g2.drawString("Pagos", x, y); y += 12;
                    g2.setFont(fText);

                    final int gapCols2 = 24;
                    final int leftW2   = (w - gapCols2) / 2;
                    final int rightW2  = w - gapCols2 - leftW2;

                    int yPLeft  = y;
                    int yPRight = y;

                    yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW2);
                    yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW2);
                    yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW2);

                    yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW2 + gapCols2, yPRight, rightW2);
                    yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                    yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW2 + gapCols2, yPRight + 2, rightW2);

                    y = Math.max(yPLeft, yPRight) + 8;

                    final java.util.List<String> obsequiosPrint =
                (obsequiosLineas == null) ? java.util.Collections.emptyList() : obsequiosLineas;



    if (obsequiosPrint != null && !obsequiosPrint.isEmpty()) {
        y += 10;
        g2.drawLine(x, y, x + w, y);
        y += 16;

        g2.setFont(fSection);
        g2.drawString("Obsequios incluidos", x, y);
        y += 12;

        g2.setFont(fText);

        final int gap2    = 16;
        final int colCodW = 80;
        final int colNomW = w - colCodW - gap2;

        final int xCod = x;
        final int xNom = xCod + colCodW + gap2;

        g2.drawString("Código",   xCod, y);
        g2.drawString("Obsequio", xNom, y);
        y += 10;
        g2.drawLine(x, y, x + w, y);
        y += 14;

        // 1) separar códigos y descripción manual
        java.util.List<String> codigos = new java.util.ArrayList<>();
        java.util.Map<String,String> descManual = new java.util.HashMap<>();

        for (String raw : obsequiosPrint) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String codigo = s;
            String desc   = "";

            int guion = s.indexOf('-');
            if (guion > 0) {
                codigo = s.substring(0, guion).trim();
                desc   = s.substring(guion + 1).trim();
            }

            codigos.add(codigo);
            if (!desc.isEmpty()) {
                descManual.put(codigo, desc);
            }
        }

        // 2) intentar completar desde InventarioObsequios
        java.util.Map<String,String> info = new java.util.HashMap<>();
        if (!codigos.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT codigo_articulo, articulo, marca, modelo, talla, color " +
                    "FROM InventarioObsequios WHERE codigo_articulo IN (");
            for (int i = 0; i < codigos.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(')');

            try (Connection cn = Conexion.Conecta.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql.toString())) {

                for (int i = 0; i < codigos.size(); i++) {
                    ps.setString(i + 1, codigos.get(i));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String c      = rs.getString("codigo_articulo");
                        String nombre = safe(rs.getString("articulo"));
                        String marca  = safe(rs.getString("marca"));
                        String modelo = safe(rs.getString("modelo"));
                        String talla  = safe(rs.getString("talla"));
                        String color  = safe(rs.getString("color"));

                        StringBuilder desc = new StringBuilder();
                        if (!nombre.isBlank()) desc.append(nombre);

                        String mm = "";
                        if (!marca.isBlank()) mm = marca;
                        if (!modelo.isBlank()) {
                            if (!mm.isEmpty()) mm += " ";
                            mm += modelo;
                        }
                        if (!mm.isEmpty()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append('(').append(mm).append(')');
                        }
                        if (!talla.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Talla ").append(talla);
                        }
                        if (!color.isBlank()) {
                            if (desc.length() > 0) desc.append(" ");
                            desc.append("Color ").append(color);
                        }
                        info.put(c, desc.toString());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace(); // si truena, al menos usamos lo manual
            }
        }

        // 3) pintar filas
        for (String raw : obsequiosPrint) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String codigo = s;
            String descManualRow = "";
            int guion = s.indexOf('-');
            if (guion > 0) {
                codigo = s.substring(0, guion).trim();
                descManualRow = s.substring(guion + 1).trim();
            }

            String descripcion = info.get(codigo);
            if (descripcion == null || descripcion.isBlank()) {
                if (!descManualRow.isEmpty())      descripcion = descManualRow;
                else if (!s.equals(codigo))        descripcion = s;
                else                               descripcion = "";
            }

            int yCod = drawWrapped(g2, codigo,      xCod, y, colCodW);
            int yNom = drawWrapped(g2, descripcion, xNom, y, colNomW);
            y = Math.max(yCod, yNom);
        }
    }

                    // Línea de cajera (si hay datos)
    y += 10;
    g2.setFont(fText);

    boolean tieneCodigo = cajeraCodigo > 0;
    boolean tieneNombre = (cajeraNombre != null && !cajeraNombre.isBlank());

    String cajeraLine = "Cajera: ";
    if (tieneCodigo) {
        cajeraLine += cajeraCodigo;
    }
    if (tieneNombre) {
        if (tieneCodigo) {
            cajeraLine += " - " + cajeraNombre;
        } else {
            cajeraLine += cajeraNombre;
        }
    }

    g2.drawString(cajeraLine, x, y);

                    return PAGE_EXISTS;
                }

                // helpers (idénticos a Contado)
                private String safe(String s){ return (s == null) ? "" : s.trim(); }
                private String fmt2(Double v){ if (v == null) v = 0d; return String.format("%.2f", v); }
                private String coalesce(String a, String b, String def){
                    if (a != null && !a.isBlank()) return a;
                    if (b != null && !b.isBlank()) return b;
                    return (def == null) ? "" : def;
                }
                private String labelIf(String label, String val){
                    if (val == null || val.isBlank()) return "";
                    return label + val;
                }
                private String trimJoin(String sep, String... vals){
                    StringBuilder sb = new StringBuilder();
                    for (String v : vals){
                        v = (v==null? "": v.trim());
                        if (!v.isEmpty()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(v);
                        }
                    }
                    return sb.toString();
                }
                private String joinNonBlank(String sep, String... parts){
                    StringBuilder sb = new StringBuilder();
                    for (String s : parts){
                        if (s != null && !s.isBlank()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(s);
                        }
                    }
                    return sb.toString();
                }
                private void center(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int cx = x + (w - fm.stringWidth(s)) / 2;
                    g2.drawString(s, cx, y);
                }
                private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int rx = x + w - fm.stringWidth(s);
                    g2.drawString(s, rx, y);
                }
                private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                    if (text == null) return y;
                    text = text.trim();
                    if (text.isEmpty()) return y;
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    String[] words = text.split("\\s+");
                    StringBuilder line = new StringBuilder();
                    for (String w : words){
                        String tryLine = (line.length()==0 ? w : line + " " + w);
                        if (fm.stringWidth(tryLine) <= maxWidth){
                            line.setLength(0); line.append(tryLine);
                        } else {
                            g2.drawString(line.toString(), x, y);
                            y += 12;
                            line.setLength(0); line.append(w);
                        }
                    }
                    if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                    return y + 12;
                }
                private BufferedImage loadIcon(String pathOrName) {
                    try {
                        String name = pathOrName;
                        int slash = name.lastIndexOf('/');
                        if (slash >= 0) name = name.substring(slash + 1);

                        java.nio.file.Path override = java.nio.file.Paths.get(System.getProperty("user.dir"), "icons", name);
                        if (java.nio.file.Files.exists(override)) {
                            javax.imageio.ImageIO.setUseCache(false);
                            try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                                return trimTransparent(javax.imageio.ImageIO.read(in));
                            }
                        }

                        String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                        try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                            if (in == null) return null;
                            javax.imageio.ImageIO.setUseCache(false);
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    } catch (Exception e) { return null; }
                }
                private BufferedImage trimTransparent(BufferedImage src) {
                    if (src == null) return null;
                    int w = src.getWidth(), h = src.getHeight();
                    int minX = w, minY = h, maxX = -1, maxY = -1;
                    int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                    for (int y = 0; y < h; y++) {
                        int off = y * w;
                        for (int x = 0; x < w; x++) {
                            if (((p[off + x] >>> 24) & 0xFF) != 0) {
                                if (x < minX) minX = x; if (x > maxX) maxX = x;
                                if (y < minY) minY = y; if (y > maxY) maxY = y;
                            }
                        }
                    }
                    if (maxX < minX || maxY < minY) return src;
                    BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                            minX, minY, maxX + 1, maxY + 1, null);
                    g.dispose();
                    return out;
                }
                private String numeroALetras(double valor) {
                    long pesos = Math.round(Math.floor(valor + 1e-6));
                    int centavos = (int) Math.round((valor - pesos) * 100.0);
                    if (centavos == 100) { pesos += 1; centavos = 0; }

                    if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

                    final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                            "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
                    final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
                    final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                            "seiscientos", "setecientos", "ochocientos", "novecientos"};

                    java.util.function.Function<Integer,String> tres = n -> {
                        if (n == 0) return "";
                        if (n == 100) return "cien";
                        int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
                        String s = (c > 0 ? CE[c] : "");
                        if (r > 0) {
                            if (!s.isEmpty()) s += " ";
                            if (r < 30) s += UN[r];
                            else {
                                s += DE[d];
                                if (u > 0) s += " y " + UN[u];
                            }
                        }
                        return s.trim();
                    };

                    StringBuilder sb = new StringBuilder();
                    int millones = (int)((pesos / 1_000_000) % 1000);
                    int miles    = (int)((pesos / 1_000) % 1000);
                    int cientos  = (int)(pesos % 1000);

                    long milesDeMillones = pesos / 1_000_000_000L;
                    if (milesDeMillones > 0) {
                        sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
                        if (millones>0 || miles>0 || cientos>0) sb.append(" ");
                    }
                    if (millones > 0) {
                        sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
                        if (miles>0 || cientos>0) sb.append(" ");
                    }
                    if (miles > 0) {
                        if (miles == 1) sb.append("mil");
                        else sb.append(tres.apply(miles)).append(" mil");
                        if (cientos>0) sb.append(" ");
                    }
                    if (cientos > 0) sb.append(tres.apply(cientos));

                    String texto = sb.toString().trim();
                    texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
                    texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

                    return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
                }
                private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                        int x, int y, int maxWidth, int iconSize, int gapPx) {
                    if ((text == null || text.isBlank()) && icon == null) return y;
                    int tx = x;
                    int baseline = y;
                    if (icon != null) {
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY  = baseline - Math.min(iconSize, ascent);
                        g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                        tx += iconSize + gapPx;
                    }
                    return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
                }
            };
        }

        /* ==========================================================
        ========================= ABONO ==========================
        ========================================================== */

        private Printable construirPrintableAbono(
                EmpresaInfo emp,
                Modelo.Nota notaBase,                  // contiene total/saldo/telefono/numero
                List<Modelo.NotaDetalle> dets,         // detalle de artículos vendidos
                Modelo.PagoFormas pagos,               // lo que se abonó por forma
                double abonoRealizado,                 // suma de pagos capturados
                double saldoRestante,                  // nuevo saldo después del abono
                double saldoAnterior,                  // saldo antes del abono
                String folioTxt, String folioRefTxt, String observacionesTexto, java.time.LocalDate fechaNotaAbono, int cajeraCodigo, String cajeraNombre) {

            // Teléfono crudo de la nota
final String tel1Raw  = (notaBase.getTelefono() == null) ? "" : notaBase.getTelefono();
// Teléfono formateado para impresión
final String fTel1Print = formatTelefono10(tel1Raw);

// Fecha de abono con mes en español
final String fechaAbonoStr = fechaLarga(
        (fechaNotaAbono != null) ? fechaNotaAbono : java.time.LocalDate.now()
);

final double total    = (notaBase.getTotal() == null) ? 0d : notaBase.getTotal();

        // pagos hechos ANTES del abono actual
        final double pagosPrevios = Math.max(0d, total - saldoAnterior);


            final double tc = pagos.getTarjetaCredito()   == null ? 0d : pagos.getTarjetaCredito();
            final double td = pagos.getTarjetaDebito()    == null ? 0d : pagos.getTarjetaDebito();
            final double am = pagos.getAmericanExpress()  == null ? 0d : pagos.getAmericanExpress();
            final double tr = pagos.getTransferencia()    == null ? 0d : pagos.getTransferencia();
            final double dp = pagos.getDeposito()         == null ? 0d : pagos.getDeposito();
            final double ef = pagos.getEfectivo()         == null ? 0d : pagos.getEfectivo();

            String cliNombre      = "";
            String cliTel2        = "";
            String cliPrueba1     = "";
            String cliPrueba2     = "";
            String cliFechaEvento = "";

            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1Raw);
                if (cr != null) {
                    cliNombre = (cr.getNombreCompleto() == null) ? "" : cr.getNombreCompleto();

                    cliTel2 = (cr.getTelefono2() == null) ? "" : cr.getTelefono2();
                    cliTel2 = formatTelefono10(cliTel2);

                    if (cr.getFechaEvento()  != null) cliFechaEvento = fechaLarga(cr.getFechaEvento());
                    if (cr.getFechaPrueba1() != null) cliPrueba1     = fechaLarga(cr.getFechaPrueba1());
                    if (cr.getFechaPrueba2() != null) cliPrueba2     = fechaLarga(cr.getFechaPrueba2());
                }
            } catch (Exception ignore) { }

            try {
                Controlador.clienteDAO cdao = new Controlador.clienteDAO();
                java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1Raw);
                if (raw != null) {
                }
            } catch (Exception ignore) { }


            final String folio = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;
            final String folioRef = folioRefTxt;
            final String fCliNombre = cliNombre, fCliTel2 = cliTel2, fCliPrueba1 = cliPrueba1, fCliPrueba2 = cliPrueba2;
            final String fCliFechaEvento = cliFechaEvento;

            return new Printable() {
                @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                    if (pageIndex > 0) return NO_SUCH_PAGE;
                    Graphics2D g2 = (Graphics2D) g;

                    final int MARGIN = 36;
                    final int x0 = (int) Math.round(pf.getImageableX());
                    final int y0 = (int) Math.round(pf.getImageableY());
                    final int W  = (int) Math.round(pf.getImageableWidth());
                    int x = x0 + MARGIN;
                    int y = y0 + MARGIN;
                    int w = W  - (MARGIN * 2);

                    final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                    final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                    final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                    final Font fText    = g2.getFont().deriveFont(10.2f);
                    final Font fSmall   = g2.getFont().deriveFont(8.8f);

                    int leftTextX = x;
                    int headerH   = 0;
                    if (emp.logo != null) {
                        int hLogo = 56;
                        int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                        g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                        leftTextX = x + wLogo + 16;
                        headerH   = Math.max(headerH, hLogo);
                    }
                    int infoRightWidth = w - (leftTextX - x);
                    int infoTextWidth  = infoRightWidth - 160;

                    g2.setFont(fH1);
                    int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                    g2.setFont(fSmall);
                    yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                    yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);

                    String dir = joinNonBlank(", ",
                            emp.calleNumero, emp.colonia,
                            (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                            emp.ciudad, emp.estado);
                    yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);
                    String telEmpPrint = formatTelefono10(emp.telefono);
                    String waEmpPrint  = formatTelefono10(emp.whatsapp);
                    yy = drawWrapped(g2, joinNonBlank("   ",
                            labelIf("Tel: ", telEmpPrint),
                            labelIf("WhatsApp: ", waEmpPrint)), leftTextX, yy + 2, infoTextWidth);


                    g2.setFont(fH1);
                    rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                    final int rightColW = 120;
                    final int xRight    = x + w - rightColW;
                    int yRight          = y + 22;

                    g2.setFont(fSmall);
                    yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
                    yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

                    BufferedImage igIcon = loadIcon("instagram.png");
                    BufferedImage fbIcon = loadIcon("facebook.png");
                    BufferedImage ttIcon = loadIcon("tiktok.png");

                    yRight = drawIconLine(g2, igIcon, safe(emp.instagram), xRight, yRight, rightColW, 12, 6);
                    yRight = drawIconLine(g2, fbIcon, safe(emp.facebook),  xRight, yRight, rightColW, 12, 6);
                    yRight = drawIconLine(g2, ttIcon, safe(emp.tiktok),    xRight, yRight, rightColW, 12, 6);

                    int leftBlockH  = Math.max(yy - y, headerH);
                    int rightBlockH = Math.max(0, yRight - y);
                    int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;

                    int afterTail = y + usedHeader;

                    g2.setFont(fTitle);
                    center(g2, "NOTA DE ABONO", x, w, afterTail + 14);
                    y = afterTail + 32;

                    g2.setFont(fSection);
                    g2.drawString("Datos del cliente", x, y);
                    y += 13;

                    final int gapCols = 24;
                    final int leftW   = (w - gapCols) / 2;
                    final int rightW  = w - gapCols - leftW;

                    int yLeft  = y;
                    int yRight2 = y;

                    g2.setFont(fText);
                    yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(fCliNombre)), x, yLeft, leftW);
                    yLeft  = drawWrapped(g2, joinNonBlank("   ",
                            labelIf("Teléfono: ", safe(fTel1Print)),
                            labelIf("Teléfono 2: ", safe(fCliTel2))), x, yLeft + 2, leftW);

                    yRight2 = drawWrapped(g2, labelIf("Fecha de abono: ", fechaAbonoStr), x + leftW + gapCols, yRight2, rightW);
                    if (!fCliFechaEvento.isBlank())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de evento: ", fCliFechaEvento), x + leftW + gapCols, yRight2 + 2, rightW);

                    if (!fCliPrueba1.isBlank())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", fCliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                    if (!fCliPrueba2.isBlank())
                        yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", fCliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);

                    y = Math.max(yLeft, yRight2) + 10;

                    final int colSubW  = 95, colDescW = 70, colPreW = 100;
                    final int gap = 16;
                    final int colArtW = w - (colSubW + colDescW + colPreW + (gap * 3));
                    final int xArt = x, xPre = xArt + colArtW + gap, xDes = xPre + colPreW + gap, xSub = xDes + colDescW + gap;

                    g2.drawLine(x, y, x + w, y); y += 15;
                    g2.setFont(fText);
                    g2.drawString("Artículo", xArt, y);
                    g2.drawString("Precio",   xPre, y);
                    g2.drawString("%Desc",    xDes, y);
                    g2.drawString("Subtotal", xSub, y);
                    y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                    if (dets != null && !dets.isEmpty()) {
                        for (Modelo.NotaDetalle d : dets) {
                            String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                                    ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                            String detalle = (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty() ? d.getCodigoArticulo() + " · " : "")
                                    + safe(artBase)
                                    + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                                    + " | " + labelIf("Color: ", safe(d.getColor()))
                                    + " | " + labelIf("Talla: ", safe(d.getTalla()));

                            int yRowStart = y;
                            int yAfter     = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);

                            g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                            g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                            g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);

                            y = yAfter + 12;
                        }
                    } else {
                        y = drawWrapped(g2, "(Detalle no disponible)", xArt, y, colArtW);
                    }

                    // === Observaciones (de la venta original) ===
                    if (observacionesTexto != null && !observacionesTexto.isBlank()) {
                        y += 10;
                        g2.setFont(fSection);
                        g2.drawString("Observaciones", x, y);
                        y += 12;
                        g2.setFont(fText);
                        y = drawWrapped(g2, observacionesTexto, x + 4, y, w - 8);
                        y += 10;
                    }

                    y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                                        // TOTAL de la compra original
                    g2.setFont(fH1);
                    rightAlign(g2, "TOTAL DE COMPRA: $" + fmt2(total), x, w, y);
                    y += 22;

                    // Abono en letras (igual que en AbonoPanel)
                    g2.setFont(fText);
                    java.math.BigDecimal bdAbono = java.math.BigDecimal
                            .valueOf(abonoRealizado)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    String abonoLetra = numeroALetras(bdAbono.doubleValue());
                    int anchoLetras = w - 230;
                    int yInicioTotales = y - 24; // solo hubo una línea (TOTAL)
                    drawWrapped(g2, "Abono en letra: " + abonoLetra, x, yInicioTotales, anchoLetras);
                    y += 22;

                    // === Bloque de totales de la nota (igual que AbonoPanel original) ===
                    g2.setFont(fText);

                    // "Pagos efectuados a la nota {folio}: $XXX.XX"
                    String etiquetaPagos = "Pagos efectuados a la nota";
                    if (!"—".equals(folioRef)) {
                        etiquetaPagos += " " + folioRef;
                    }
                    etiquetaPagos += ": $" + fmt2(pagosPrevios);
                    rightAlign(g2, etiquetaPagos, x, w, y);
                    y += 14;

                    // Saldo previo al abono actual
                    rightAlign(g2, "Saldo por pagar: $" + fmt2(saldoAnterior), x, w, y);
                    y += 14;

                    // Monto del abono de esta nota
                    rightAlign(g2, "Su abono: $" + fmt2(abonoRealizado), x, w, y);
                    y += 14;

                    // Nuevo saldo después del abono
                    g2.setFont(fH1);
                    rightAlign(g2, "NUEVO SALDO RESTANTE: $" + fmt2(saldoRestante), x, w, y);
                    y += 22;



                    if (Math.abs(saldoRestante) < 0.005) {
                        g2.setFont(fH1);
                        center(g2, "Venta liquidada", x, w, y);
                        y += 18;
                    }

                    g2.setFont(fSection);
                    g2.drawString("Pagos", x, y); y += 12; g2.setFont(fText);

                    final int gapCols2 = 24;
                    final int leftW2   = (w - gapCols2) / 2;
                    final int rightW2  = w - gapCols2 - leftW2;

                    int yPLeft = y, yPRight = y;
                    yPLeft  = drawWrapped(g2, "T. Crédito: $"   + fmt2(tc), x, yPLeft, leftW2);
                    yPLeft  = drawWrapped(g2, "AMEX: $"         + fmt2(am), x, yPLeft + 2, leftW2);
                    yPLeft  = drawWrapped(g2, "Depósito: $"     + fmt2(dp), x, yPLeft + 2, leftW2);

                    yPRight = drawWrapped(g2, "T. Débito: $"    + fmt2(td), x + leftW2 + gapCols2, yPRight, rightW2);
                    yPRight = drawWrapped(g2, "Transferencia: $" + fmt2(tr), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                    yPRight = drawWrapped(g2, "Efectivo: $"     + fmt2(ef), x + leftW2 + gapCols2, yPRight + 2, rightW2);
                    y = Math.max(yPLeft, yPRight) + 10;

                    // Línea de cajera
                    g2.setFont(fText);
                    boolean tieneCodigo = cajeraCodigo > 0;
                    boolean tieneNombre = (cajeraNombre != null && !cajeraNombre.isBlank());

                    String cajeraLine = "Cajera: ";
                    if (tieneCodigo) {
                        cajeraLine += cajeraCodigo;
                    }
                    if (tieneNombre) {
                        if (tieneCodigo) {
                            cajeraLine += " - " + cajeraNombre;
                        } else {
                            cajeraLine += cajeraNombre;
                        }
                    }
                    g2.drawString(cajeraLine, x, y);

                    return PAGE_EXISTS;
                }

                private String safe(String s){ return (s == null) ? "" : s.trim(); }
                private String fmt2(Double v){ return fmtMoneda(v);}
                private String coalesce(String a, String b, String def){
                    if (a != null && !a.isBlank()) return a;
                    if (b != null && !b.isBlank()) return b;
                    return (def == null) ? "" : def;
                }
                private String labelIf(String label, String val){
                    if (val == null || val.isBlank()) return "";
                    return label + val;
                }
                private String trimJoin(String sep, String... vals){
                    StringBuilder sb = new StringBuilder();
                    for (String v : vals){
                        v = safe(v);
                        if (!v.isBlank()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(v);
                        }
                    }
                    return sb.toString();
                }
                private String joinNonBlank(String sep, String... parts){
                    StringBuilder sb = new StringBuilder();
                    for (String s : parts){
                        if (s != null && !s.isBlank()){
                            if (sb.length() > 0) sb.append(sep);
                            sb.append(s);
                        }
                    }
                    return sb.toString();
                }
                private void center(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int cx = x + (w - fm.stringWidth(s)) / 2;
                    g2.drawString(s, cx, y);
                }
                private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    int rx = x + w - fm.stringWidth(s);
                    g2.drawString(s, rx, y);
                }
                private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                    if (text == null) return y;
                    text = text.trim();
                    if (text.isEmpty()) return y;
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    String[] words = text.split("\\s+");
                    StringBuilder line = new StringBuilder();
                    for (String w : words){
                        String tryLine = (line.length()==0 ? w : line + " " + w);
                        if (fm.stringWidth(tryLine) <= maxWidth){
                            line.setLength(0); line.append(tryLine);
                        } else {
                            g2.drawString(line.toString(), x, y);
                            y += 12;
                            line.setLength(0); line.append(w);
                        }
                    }
                    if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                    return y + 12;
                }

                private BufferedImage loadIcon(String pathOrName) {
                    try {
                        String name = pathOrName;
                        int slash = name.lastIndexOf('/');
                        if (slash >= 0) name = name.substring(slash + 1);

                        java.nio.file.Path override = java.nio.file.Paths.get(
                                System.getProperty("user.dir"), "icons", name);
                        if (java.nio.file.Files.exists(override)) {
                            javax.imageio.ImageIO.setUseCache(false);
                            try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                                return trimTransparent(javax.imageio.ImageIO.read(in));
                            }
                        }

                        String cpPath = pathOrName.startsWith("/") ? pathOrName : ("/icons/" + name);
                        try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cpPath)) {
                            if (in == null) return null;
                            javax.imageio.ImageIO.setUseCache(false);
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    } catch (Exception e) { return null; }
                }

                private static BufferedImage trimTransparent(BufferedImage src) {
                    if (src == null) return null;
                    int w = src.getWidth(), h = src.getHeight();
                    int minX = w, minY = h, maxX = -1, maxY = -1;
                    int[] p = src.getRGB(0, 0, w, h, null, 0, w);
                    for (int y = 0; y < h; y++) {
                        int off = y * w;
                        for (int x = 0; x < w; x++) {
                            if (((p[off + x] >>> 24) & 0xFF) != 0) {
                                if (x < minX) minX = x; if (x > maxX) maxX = x;
                                if (y < minY) minY = y; if (y > maxY) maxY = y;
                            }
                        }
                    }
                    
                    if (maxX < minX || maxY < minY) return src;
                    BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(),
                            minX, minY, maxX + 1, maxY + 1, null);
                    g.dispose();
                    return out;

                    
                }

                private int drawIconLine(Graphics2D g2, BufferedImage icon, String text,
                                        int x, int y, int maxWidth, int iconSize, int gapPx) {
                    if ((text == null || text.isBlank()) && icon == null) return y;
                    int tx = x;
                    int baseline = y;
                    if (icon != null) {
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY  = baseline - Math.min(iconSize, ascent);
                        g2.drawImage(icon, x, iconY, iconSize, iconSize, null);
                        tx += iconSize + gapPx;
                    }
                    return drawWrapped(g2, text == null ? "" : text.trim(), tx, baseline, maxWidth - (tx - x));
                }

                private String numeroALetras(double valor) {
        long pesos = Math.round(Math.floor(valor + 1e-6));
        int centavos = (int) Math.round((valor - pesos) * 100.0);
        if (centavos == 100) { pesos += 1; centavos = 0; }

        if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

        final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
        final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
        final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                "seiscientos", "setecientos", "ochocientos", "novecientos"};

        java.util.function.Function<Integer,String> tres = n -> {
            if (n == 0) return "";
            if (n == 100) return "cien";
            int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
            String s = (c > 0 ? CE[c] : "");
            if (r > 0) {
                if (!s.isEmpty()) s += " ";
                if (r < 30) s += UN[r];
                else {
                    s += DE[d];
                    if (u > 0) s += " y " + UN[u];
                }
            }
            return s.trim();
        };

        StringBuilder sb = new StringBuilder();
        int millones = (int)((pesos / 1_000_000) % 1000);
        int miles    = (int)((pesos / 1_000) % 1000);
        int cientos  = (int)(pesos % 1000);

        long milesDeMillones = pesos / 1_000_000_000L;
        if (milesDeMillones > 0) {
            sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
            if (millones>0 || miles>0 || cientos>0) sb.append(" ");
        }
        if (millones > 0) {
            sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
            if (miles>0 || cientos>0) sb.append(" ");
        }
        if (miles > 0) {
            if (miles == 1) sb.append("mil");
            else sb.append(tres.apply(miles)).append(" mil");
            if (cientos>0) sb.append(" ");
        }
        if (cientos > 0) sb.append(tres.apply(cientos));

        String texto = sb.toString().trim();
        texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
        texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

        return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
    }

            };
        }
        // ====== NOTA DE DEVOLUCIÓN (FIX: vars final + sin drawIconLine) ======
    private Printable construirPrintableDevolucion(
            EmpresaInfo emp,
            Modelo.Nota n,                       // usa: telefono, total
            List<Modelo.NotaDetalle> dets,       // artículos devueltos (si los tienes)
            String folioTxt, java.time.LocalDate fechaNotaDv, int cajeraCodigo, String cajeraNombre) {

        final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

        final String tel1     = (n.getTelefono() == null) ? "" : n.getTelefono();
        final String fechaEmisionStr =
            (fechaNotaDv != null) ? fechaNotaDv.format(MX)
                                : java.time.LocalDate.now().format(MX);

        final double total    = (n.getTotal() == null) ? 0d : n.getTotal();

        final String folio    = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;

        // Cliente (nombre/tel2 y pruebas si existen)
        String _cliNombre = "", _cliTel2 = "", _cliPrueba1 = "", _cliPrueba2 = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            Modelo.ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
            if (cr != null) {
                _cliNombre  = cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto();
                _cliTel2    = cr.getTelefono2() == null ? "" : cr.getTelefono2();
                if (cr.getFechaPrueba1()!=null) _cliPrueba1 = cr.getFechaPrueba1().format(MX);
                if (cr.getFechaPrueba2()!=null) _cliPrueba2 = cr.getFechaPrueba2().format(MX);
            }
        } catch (Exception ignore) { }
        // → finales para la clase interna
        final String cliNombre  = _cliNombre;
        final String cliTel2    = _cliTel2;
        final String cliPrueba1 = _cliPrueba1;
        final String cliPrueba2 = _cliPrueba2;

        // Medidas (opcional)
        String medBusto = "", medCintura = "", medCadera = "";
        try {
            Controlador.clienteDAO cdao = new Controlador.clienteDAO();
            java.util.Map<String,String> raw = cdao.detalleGenericoPorTelefono(tel1);
            if (raw != null) {
                for (var e : raw.entrySet()) {
                    String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                    String v = e.getValue() == null ? "" : e.getValue();
                    if (k.equals("busto"))      medBusto   = v;
                    else if (k.equals("cintura")) medCintura = v;
                    else if (k.equals("cadera"))  medCadera  = v;
                }
            }
        } catch (Exception ignore) { }
        final String medidasFmt = (medBusto.isBlank() && medCintura.isBlank() && medCadera.isBlank())
                ? ""
                : ( (medBusto.isBlank()? "" : "Busto: " + medBusto)
                + (medCintura.isBlank()? "" : ( (medBusto.isBlank()? "" : "   ") + "Cintura: " + medCintura))
                + (medCadera.isBlank()? "" : ( (!medBusto.isBlank()||!medCintura.isBlank()? "   " : "") + "Cadera: " + medCadera)) );

        return new Printable() {
            @Override public int print(Graphics g, PageFormat pf, int pageIndex) {
                if (pageIndex > 0) return NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) g;

                // Márgenes y tipografías (idénticos a otros formatos)
                final int MARGIN = 36;
                final int x0 = (int) Math.round(pf.getImageableX());
                final int y0 = (int) Math.round(pf.getImageableY());
                final int W  = (int) Math.round(pf.getImageableWidth());
                int x = x0 + MARGIN, y = y0 + MARGIN, w = W - (MARGIN * 2);

                final Font fTitle   = g2.getFont().deriveFont(Font.BOLD, 15f);
                final Font fH1      = g2.getFont().deriveFont(Font.BOLD, 12.5f);
                final Font fSection = g2.getFont().deriveFont(Font.BOLD, 12f);
                final Font fText    = g2.getFont().deriveFont(10.2f);
                final Font fSmall   = g2.getFont().deriveFont(8.8f);

                // Encabezado empresa
                int leftTextX = x, headerH = 0;
                if (emp.logo != null) {
                    int hLogo = 56;
                    int wLogo = Math.max(100, (int) (emp.logo.getWidth() * (hLogo / (double) emp.logo.getHeight())));
                    g2.drawImage(emp.logo, x, y - 8, wLogo, hLogo, null);
                    leftTextX = x + wLogo + 16;
                    headerH   = Math.max(headerH, hLogo);
                }
                int infoRightWidth = w - (leftTextX - x);
                int infoTextWidth  = infoRightWidth - 160;

                g2.setFont(fH1);
                int yy = drawWrapped(g2, coalesce(emp.razonSocial, emp.nombreFiscal, "—"), leftTextX, y + 2, infoTextWidth);
                g2.setFont(fSmall);
                yy = drawWrapped(g2, labelIf("Nombre fiscal: ", emp.nombreFiscal), leftTextX, yy + 2, infoTextWidth);
                yy = drawWrapped(g2, labelIf("RFC: ", emp.rfc), leftTextX, yy + 1, infoTextWidth);
                String dir = joinNonBlank(", ",
                        emp.calleNumero, emp.colonia,
                        (emp.cp == null || emp.cp.isBlank()) ? "" : ("CP " + emp.cp),
                        emp.ciudad, emp.estado);
                yy = drawWrapped(g2, dir, leftTextX, yy + 2, infoTextWidth);
                yy = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Tel: ", emp.telefono),
                        labelIf("WhatsApp: ", emp.whatsapp)), leftTextX, yy + 2, infoTextWidth);

                g2.setFont(fH1);
                rightAlign(g2, "FOLIO: " + folio, x, w, y + 2);

                // Columna derecha (correo, web, redes) — sin helper drawIconLine
                final int rightColW = 120;
                final int xRight    = x + w - rightColW;
                int yRight          = y + 22;

                g2.setFont(fSmall);
                yRight = drawWrapped(g2, labelIf("Correo: ", safe(emp.correo)), xRight, yRight, rightColW);
                yRight = drawWrapped(g2, labelIf("Web: ",    safe(emp.web)),    xRight, yRight, rightColW);

                BufferedImage igIcon = loadIcon("instagram.png");
                BufferedImage fbIcon = loadIcon("facebook.png");
                BufferedImage ttIcon = loadIcon("tiktok.png");

                // Instagram
                {
                    int tx = xRight;
                    if (igIcon != null) {
                        int iconSize = 12;
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY = yRight - Math.min(iconSize, ascent);
                        g2.drawImage(igIcon, xRight, iconY, iconSize, iconSize, null);
                        tx += iconSize + 6;
                    }
                    yRight = drawWrapped(g2, safe(emp.instagram), tx, yRight, rightColW - (tx - xRight));
                }
                // Facebook
                {
                    int tx = xRight;
                    if (fbIcon != null) {
                        int iconSize = 12;
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY = yRight - Math.min(iconSize, ascent);
                        g2.drawImage(fbIcon, xRight, iconY, iconSize, iconSize, null);
                        tx += iconSize + 6;
                    }
                    yRight = drawWrapped(g2, safe(emp.facebook), tx, yRight, rightColW - (tx - xRight));
                }
                // TikTok
                {
                    int tx = xRight;
                    if (ttIcon != null) {
                        int iconSize = 12;
                        int ascent = g2.getFontMetrics().getAscent();
                        int iconY = yRight - Math.min(iconSize, ascent);
                        g2.drawImage(ttIcon, xRight, iconY, iconSize, iconSize, null);
                        tx += iconSize + 6;
                    }
                    yRight = drawWrapped(g2, safe(emp.tiktok), tx, yRight, rightColW - (tx - xRight));
                }

                int leftBlockH  = Math.max(yy - y, headerH);
                int rightBlockH = Math.max(0, yRight - y);
                int usedHeader  = Math.max(leftBlockH, rightBlockH) + 6;
                int afterTail   = y + usedHeader;

                // Título
                g2.setFont(fTitle);
                center(g2, "NOTA DE DEVOLUCIÓN", x, w, afterTail + 14);
                y = afterTail + 32;

                // Datos del cliente
                g2.setFont(fSection);
                g2.drawString("Datos del cliente", x, y);
                y += 13;

                final int gapCols = 24;
                final int leftW   = (w - gapCols) / 2;
                final int rightW  = w - gapCols - leftW;

                int yLeft = y, yRight2 = y;
                g2.setFont(fText);
                yLeft  = drawWrapped(g2, labelIf("Nombre: ", safe(cliNombre)), x, yLeft, leftW);
                yLeft  = drawWrapped(g2, joinNonBlank("   ",
                        labelIf("Teléfono: ", safe(tel1)),
                        labelIf("Teléfono 2: ", safe(cliTel2))), x, yLeft + 2, leftW);
                if (!medidasFmt.isBlank()) yLeft = drawWrapped(g2, medidasFmt, x, yLeft + 2, leftW);

                yRight2 = drawWrapped(g2, labelIf("Fecha de emisión: ", fechaEmisionStr), x + leftW + gapCols, yRight2, rightW);
                if (!cliPrueba1.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", cliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!cliPrueba2.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", cliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);

                y = Math.max(yLeft, yRight2) + 10;

                // Tabla de artículos devueltos (igual que venta)
                final int colSubW  = 95, colDescW = 70, colPreW = 100, gap = 16;
                final int colArtW  = w - (colSubW + colDescW + colPreW + (gap * 3));
                final int xArt = x, xPre = xArt + colArtW + gap, xDes = xPre + colPreW + gap, xSub = xDes + colDescW + gap;

                g2.drawLine(x, y, x + w, y); y += 15;
                g2.setFont(fText);
                g2.drawString("Artículo", xArt, y);
                g2.drawString("Precio",   xPre, y);
                g2.drawString("%Desc",    xDes, y);
                g2.drawString("Subtotal", xSub, y);
                y += 10; g2.drawLine(x, y, x + w, y); y += 14;

                if (dets != null && !dets.isEmpty()) {
                    for (Modelo.NotaDetalle d : dets) {
                        String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                                ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                        String detalle = (d.getCodigoArticulo() != null && !d.getCodigoArticulo().isEmpty() ? d.getCodigoArticulo() + " · " : "")
                                + safe(artBase)
                                + " | " + trimJoin(" ", safe(d.getMarca()), safe(d.getModelo()))
                                + " | " + labelIf("Color: ", safe(d.getColor()))
                                + " | " + labelIf("Talla: ", safe(d.getTalla()));
                        int yRowStart = y;
                        int yAfter = drawWrapped(g2, detalle, xArt, yRowStart, colArtW);
                        g2.drawString(fmt2(d.getPrecio()),    xPre, yRowStart);
                        g2.drawString(fmt2(d.getDescuento()), xDes, yRowStart);
                        g2.drawString(fmt2(d.getSubtotal()),  xSub, yRowStart);
                        y = yAfter + 12;
                    }
                } else {
                    y = drawWrapped(g2, "(Detalle no disponible)", xArt, y, colArtW);
                }

                y += 6; g2.drawLine(x, y, x + w, y); y += 16;

                // Saldo a favor
                g2.setFont(fH1);
                rightAlign(g2, "SALDO A FAVOR: $" + fmt2(total), x, w, y);
                y += 22;

                // Leyenda
                g2.setFont(fSmall);
                y = drawWrapped(g2,
                        "Esta nota de devolución puede utilizarse como método de pago en compras futuras. "
                    + "No es canjeable por efectivo.", x, y, w);

                return PAGE_EXISTS;
            }

            // ===== Helpers locales =====
            private String safe(String s){ return (s == null) ? "" : s.trim(); }
            private String fmt2(Double v){ return fmtMoneda(v);}
            private String coalesce(String a, String b, String def){
                if (a != null && !a.isBlank()) return a;
                if (b != null && !b.isBlank()) return b;
                return (def == null) ? "" : def;
            }
            private String labelIf(String label, String val){
                if (val == null || val.isBlank()) return "";
                return label + val;
            }
            private String trimJoin(String sep, String... vals){
                StringBuilder sb = new StringBuilder();
                for (String v : vals){
                    v = (v==null? "": v.trim());
                    if (!v.isEmpty()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(v);
                    }
                }
                return sb.toString();
            }
            private String joinNonBlank(String sep, String... parts){
                StringBuilder sb = new StringBuilder();
                for (String s : parts){
                    if (s != null && !s.isBlank()){
                        if (sb.length() > 0) sb.append(sep);
                        sb.append(s);
                    }
                }
                return sb.toString();
            }
            private void center(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int cx = x + (w - fm.stringWidth(s)) / 2;
                g2.drawString(s, cx, y);
            }
            private void rightAlign(Graphics2D g2, String s, int x, int w, int y){
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int rx = x + w - fm.stringWidth(s);
                g2.drawString(s, rx, y);
            }
            private int drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth){
                if (text == null) return y;
                text = text.trim();
                if (text.isEmpty()) return y;
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String[] words = text.split("\\s+");
                StringBuilder line = new StringBuilder();
                for (String w : words){
                    String tryLine = (line.length()==0 ? w : line + " " + w);
                    if (fm.stringWidth(tryLine) <= maxWidth){
                        line.setLength(0); line.append(tryLine);
                    } else {
                        g2.drawString(line.toString(), x, y);
                        y += 12;
                        line.setLength(0); line.append(w);
                    }
                }
                if (line.length()>0){ g2.drawString(line.toString(), x, y); }
                return y + 12;
            }
            private BufferedImage loadIcon(String name) {
                try {
                    int slash = name.lastIndexOf('/'); if (slash>=0) name = name.substring(slash+1);
                    java.nio.file.Path override = java.nio.file.Paths.get(System.getProperty("user.dir"), "icons", name);
                    if (java.nio.file.Files.exists(override)) {
                        javax.imageio.ImageIO.setUseCache(false);
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(override)) {
                            return trimTransparent(javax.imageio.ImageIO.read(in));
                        }
                    }
                    String cp = "/icons/" + name;
                    try (java.io.InputStream in = ReimprimirNotaPanel.class.getResourceAsStream(cp)) {
                        if (in == null) return null;
                        javax.imageio.ImageIO.setUseCache(false);
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                } catch(Exception e){ return null; }
            }
            private static BufferedImage trimTransparent(BufferedImage src) {
                if (src == null) return null;
                int w = src.getWidth(), h = src.getHeight();
                int minX = w, minY = h, maxX = -1, maxY = -1;
                int[] p = src.getRGB(0,0,w,h,null,0,w);
                for (int y = 0; y < h; y++) {
                    int off = y * w;
                    for (int x = 0; x < w; x++) {
                        if (((p[off + x] >>> 24) & 0xFF) != 0) {
                            if (x < minX) minX = x; if (x > maxX) maxX = x;
                            if (y < minY) minY = y; if (y > maxY) maxY = y;
                        }
                    }
                }
                if (maxX < minX || maxY < minY) return src;
                BufferedImage out = new BufferedImage(maxX-minX+1, maxY-minY+1, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D gg = out.createGraphics();
                gg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                gg.drawImage(src, 0,0,out.getWidth(),out.getHeight(), minX,minY,maxX+1,maxY+1, null);
                gg.dispose();
                return out;
            }
            private String numeroALetras(double valor) {
        long pesos = Math.round(Math.floor(valor + 1e-6));
        int centavos = (int) Math.round((valor - pesos) * 100.0);
        if (centavos == 100) { pesos += 1; centavos = 0; }

        if (pesos == 0) return "cero pesos " + String.format("%02d", centavos) + "/100 M.N.";

        final String[] UN = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
                "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
                "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
        final String[] DE = {"", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
        final String[] CE = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                "seiscientos", "setecientos", "ochocientos", "novecientos"};

        java.util.function.Function<Integer,String> tres = n -> {
            if (n == 0) return "";
            if (n == 100) return "cien";
            int c = n / 100, r = n % 100, d = r / 10, u = r % 10;
            String s = (c > 0 ? CE[c] : "");
            if (r > 0) {
                if (!s.isEmpty()) s += " ";
                if (r < 30) s += UN[r];
                else {
                    s += DE[d];
                    if (u > 0) s += " y " + UN[u];
                }
            }
            return s.trim();
        };

        StringBuilder sb = new StringBuilder();
        int millones = (int)((pesos / 1_000_000) % 1000);
        int miles    = (int)((pesos / 1_000) % 1000);
        int cientos  = (int)(pesos % 1000);

        long milesDeMillones = pesos / 1_000_000_000L;
        if (milesDeMillones > 0) {
            sb.append(tres.apply((int)(milesDeMillones % 1000))).append(" mil millones");
            if (millones>0 || miles>0 || cientos>0) sb.append(" ");
        }
        if (millones > 0) {
            sb.append(tres.apply(millones)).append(millones==1 ? " millón" : " millones");
            if (miles>0 || cientos>0) sb.append(" ");
        }
        if (miles > 0) {
            if (miles == 1) sb.append("mil");
            else sb.append(tres.apply(miles)).append(" mil");
            if (cientos>0) sb.append(" ");
        }
        if (cientos > 0) sb.append(tres.apply(cientos));

        String texto = sb.toString().trim();
        texto = texto.replaceAll("\\buno(?=\\s+(mil|millón|millones|pesos)\\b)", "un");
        texto = texto.replaceAll("\\bveintiuno(?=\\s+(mil|millón|millones|pesos)\\b)", "veintiún");

        return texto + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
    }

        };
    }



        /* Helper mínimo que usan cargarEmpresaInfo y otros */
        private String n(String s){ return s==null? "": s; }
        /** Lee los obsequios de la nota desde NotasDAO.obtenerObsequiosDeNota(numero_nota). */
    private java.util.List<String> cargarObsequiosDeNota(int numeroNota) {
        try {
            NotasDAO dao = new NotasDAO();
            String texto = dao.obtenerObsequiosDeNota(numeroNota); // ESTE método ya lo usas en reimprimirTarjetasSeleccionada
            java.util.List<String> lista = new java.util.ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                for (String linea : texto.split("\\r?\\n")) {
                    if (linea != null) {
                        String s = linea.trim();
                        if (!s.isEmpty()) lista.add(s);
                    }
                }
            }
            return lista;
        } catch (Exception ex) {
            return java.util.Collections.emptyList();
        }
    }
    /** Lee el memo / condiciones de la nota desde la tabla donde los guardas. */
    private String cargarMemoDesdeBD(int numeroNota) {
        // AJUSTA el nombre de la tabla si la tuya se llama distinto
        final String sql = "SELECT memo FROM Notas_memo WHERE numero_nota = ?";

        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
            java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, numeroNota);

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String memo = rs.getString(1);
                    return memo == null ? "" : memo;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * Intenta obtener las formas de pago reales desde NotasDAO mediante reflexión.
     * Si no encuentra ningún método compatible, regresa el cálculo aproximado anterior.
     *
     * Opciones que intenta:
     *   - NotasDAO.obtenerPagoFormasDeNota(int numeroNota)
     *   - NotasDAO.leerPagoFormas(int numeroNota)
     *
     * Si en tu proyecto el método se llama distinto, ajustas SOLO este helper.
     */
    /** Lee las formas de pago reales desde formas_pago.
     *  Si no hay registro o algo falla, cae al cálculo aproximado anterior. */
    private Modelo.PagoFormas cargarPagoFormasDesdeDAO(
            Integer numeroNota,
            Double total,
            Double saldo,
            String tipoHeuristico
    ) {
        // Sin número de nota, no hay nada que leer
        if (numeroNota == null) {
            return crearPagoFormasParaReimpresion(total, saldo, tipoHeuristico);
        }

        try {
            FormasPagoDAO fpDao = new FormasPagoDAO();
            FormasPagoDAO.FormasPagoRow row = fpDao.obtenerPorNota(numeroNota);
            if (row != null) {
                Modelo.PagoFormas p = new Modelo.PagoFormas();

                try { p.setTarjetaCredito(nz(row.tarjetaCredito)); }   catch (Exception ignore) {}
                try { p.setTarjetaDebito(nz(row.tarjetaDebito)); }     catch (Exception ignore) {}
                try { p.setAmericanExpress(nz(row.americanExpress)); } catch (Exception ignore) {}
                try { p.setTransferencia(nz(row.transferencia)); }     catch (Exception ignore) {}
                try { p.setDeposito(nz(row.deposito)); }               catch (Exception ignore) {}
                try { p.setEfectivo(nz(row.efectivo)); }               catch (Exception ignore) {}

                // Si tu clase PagoFormas tiene campo para devoluciones, aquí lo rellenas:
                // try { p.setDevolucion(nz(row.devolucion)); } catch (Exception ignore) {}

                return p;
            }
        } catch (java.sql.SQLException ex) {
            ex.printStackTrace();   // para depurar; no queremos que truene la reimpresión
        } catch (Exception ignore) {
        }

        // Fallback: lo de antes (todo a efectivo, según tipoHeuristico)
        return crearPagoFormasParaReimpresion(total, saldo, tipoHeuristico);
    }

    /** Null → 0.0 */
    private static double nz(Double v) {
        return v == null ? 0d : v;
    }
    /** Intenta hacer setFecha*(LocalDate) en Nota si existe ese método. No truena si no. */
    private void setNotaFechaSeguro(Object nota, java.time.LocalDate fecha) {
        if (nota == null || fecha == null) return;
        Class<?> c = nota.getClass();
        try {
            c.getMethod("setFecha", java.time.LocalDate.class).invoke(nota, fecha);
            return;
        } catch (Exception ignore) { }
        try {
            c.getMethod("setFechaVenta", java.time.LocalDate.class).invoke(nota, fecha);
            return;
        } catch (Exception ignore) { }
        try {
            c.getMethod("setFechaRegistro", java.time.LocalDate.class).invoke(nota, fecha);
        } catch (Exception ignore) { }
    }
private void ocultarColumnaNumeroNota() {
    // columna 0: "# Nota"
    TableColumn col = tbNotas.getColumnModel().getColumn(0);
    col.setMinWidth(0);
    col.setMaxWidth(0);
    col.setPreferredWidth(0);
}
private void seleccionarClientePorApellido() {
    java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
    DialogBusquedaCliente dlg = new DialogBusquedaCliente(owner);
    dlg.setLocationRelativeTo(this);
    dlg.setVisible(true);

    ClienteResumen cr = dlg.getSeleccionado();
    if (cr != null) {
        String tel = TelefonosUI.soloDigitos(cr.getTelefono1());
        if (tel != null && !tel.isEmpty()) {
            txtTelefono.setText(tel);
            // aquí recargamos las notas de ese cliente
            cargarNotas();
        }
    }
}
/** Diálogo para buscar cliente por apellido y devolver un ClienteResumen. */
private static class DialogBusquedaCliente extends JDialog {

    private JTextField txtApellido;
    private JTable tabla;
    private DefaultTableModel modelo;
    private java.util.List<ClienteResumen> resultados = new java.util.ArrayList<>();
    private ClienteResumen seleccionado;

    public DialogBusquedaCliente(java.awt.Window owner) {
        super(owner, "Buscar cliente por nombre o apellido", ModalityType.APPLICATION_MODAL);
        construirUI();
    }

    private void construirUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtro
        JPanel pnlFiltro = new JPanel(new BorderLayout(5, 0));
        pnlFiltro.add(new JLabel("Nombre o apellido:"), BorderLayout.WEST);
        txtApellido = new JTextField();
        pnlFiltro.add(txtApellido, BorderLayout.CENTER);

        JButton btnBuscar = new JButton("Buscar");
        pnlFiltro.add(btnBuscar, BorderLayout.EAST);

        main.add(pnlFiltro, BorderLayout.NORTH);

        // Tabla
        modelo = new DefaultTableModel(
                new Object[]{"Nombre completo", "Teléfono", "Teléfono 2", "Evento", "Prueba 1", "Prueba 2", "Entrega"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.setAutoCreateRowSorter(true);

        main.add(new JScrollPane(tabla), BorderLayout.CENTER);

        // Botones abajo
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSeleccionar = new JButton("Seleccionar");
        JButton btnCerrar = new JButton("Cerrar");
        pnlBotones.add(btnCerrar);
        pnlBotones.add(btnSeleccionar);

        main.add(pnlBotones, BorderLayout.SOUTH);

        setContentPane(main);
        setSize(800, 400);
        setLocationRelativeTo(getOwner());

        // Eventos
        btnBuscar.addActionListener(_e -> buscar());
        btnSeleccionar.addActionListener(_e -> seleccionarActual());
        btnCerrar.addActionListener(_e -> dispose());

        // Doble clic en la tabla
        tabla.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    seleccionarActual();
                }
            }
        });

        // Enter en el campo de apellido = buscar
        txtApellido.addActionListener(_e -> buscar());
    }

    private void buscar() {
        String filtro = txtApellido.getText().trim();
        if (filtro.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe al menos una parte del nombre o apellido.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Controlador.clienteDAO dao = new Controlador.clienteDAO();
            resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);  // ya lo tienes en tu DAO
            modelo.setRowCount(0);

            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

            for (ClienteResumen cr : resultados) {
                modelo.addRow(new Object[]{
                        cr.getNombreCompleto(),
                        cr.getTelefono1(),
                        cr.getTelefono2(),
                        cr.getFechaEvento()   == null ? "" : cr.getFechaEvento().format(fmt),
                        cr.getFechaPrueba1()  == null ? "" : cr.getFechaPrueba1().format(fmt),
                        cr.getFechaPrueba2()  == null ? "" : cr.getFechaPrueba2().format(fmt),
                        cr.getFechaEntrega()  == null ? "" : cr.getFechaEntrega().format(fmt)
                });
            }

            if (resultados.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No se encontraron clientes con ese nombre o apellido.",
                        "Buscar cliente", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar clientes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seleccionarActual() {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un cliente de la tabla.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tabla.convertRowIndexToModel(row);
        seleccionado = resultados.get(modelRow);
        dispose();
    }

    public ClienteResumen getSeleccionado() {
        return seleccionado;
    }
}
private static final DecimalFormat MONEY_FMT;
static {
    DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.US);
    s.setGroupingSeparator(',');
    s.setDecimalSeparator('.');
    MONEY_FMT = new DecimalFormat("#,##0.00", s);
}
private static String fmtMoneda(double v) { synchronized (MONEY_FMT) { return MONEY_FMT.format(v); } }
private static String fmtMoneda(Double v) { if (v == null) v = 0d; return fmtMoneda(v.doubleValue()); }

    }
