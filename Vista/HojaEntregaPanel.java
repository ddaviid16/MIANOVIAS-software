package Vista;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Controlador.EmpresaDAO;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Utilidades.TelefonosUI;
import Modelo.Empresa;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Panel para generar e imprimir la hoja de entrega de vestidos.
 * Solo permite imprimir para notas liquidadas (saldo = 0).
 */
public class HojaEntregaPanel extends JPanel {

    private final NotasDAO notasDAO = new NotasDAO();
    private final clienteDAO clienteDAO = new clienteDAO();
    private final EmpresaDAO empresaDAO = new EmpresaDAO();

    // Datos cargados
    private Nota notaActual;
    private Empresa empresaActual;

    // Campos UI
    private final JTextField txtTelefono = new JTextField(12);
    private final JComboBox<String> cbFolios = new JComboBox<>();

    // Folio solo informativo dentro de "Datos para la hoja"
    private final JTextField txtFolio = new JTextField(10);

    private final JTextField txtNumeroNota = new JTextField(6);
    private final JTextField txtCliente = new JTextField(30);
    private final JTextField txtModelo = new JTextField(10);
    private final JTextField txtTalla = new JTextField(8);
    private final JTextField txtColor = new JTextField(10);
    private final JTextField txtFechaApartado = new JTextField(10);
    private final JTextField txtFechaEvento = new JTextField(10);
    private final JTextField txtFechaDocumento = new JTextField(10);

    private final JTextArea txtOtrosProductos = new JTextArea(4, 40);

    // Tabla informativa con el detalle de la nota
    private final DefaultTableModel modelDetalle = new DefaultTableModel(
            new String[]{"Artículo", "Modelo", "Talla", "Color"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbDetalle = new JTable(modelDetalle);

    private final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter FECHA_DOC = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public HojaEntregaPanel() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        TelefonosUI.instalar(txtTelefono, 10);
        // Empresa para encabezado; si falla, se usa texto genérico
        try {
            empresaActual = empresaDAO.buscarPorNumero(1);
        } catch (SQLException ex) {
            empresaActual = null;
        }

        JPanel filtros = construirPanelBusqueda();
        JPanel datos = construirPanelDatos();
        JScrollPane detalleScroll = new JScrollPane(tbDetalle);
        detalleScroll.setBorder(new TitledBorder("Detalle de la nota"));

        // Panel vertical: arriba datos, abajo tabla
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(datos, BorderLayout.NORTH);
        center.add(detalleScroll, BorderLayout.CENTER);

        add(filtros, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private JPanel construirPanelBusqueda() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        p.setBorder(new TitledBorder("Búsqueda por cliente"));

        p.add(new JLabel("Teléfono:"));
        txtTelefono.setColumns(12);
        p.add(txtTelefono);

        JButton btBuscarFolios = new JButton("Buscar folios");
        btBuscarFolios.addActionListener(_e -> cargarFoliosPorTelefono());
        p.add(btBuscarFolios);

        p.add(new JLabel("Folio:"));
        cbFolios.setPrototypeDisplayValue("XXXXXXXXXX");
        p.add(cbFolios);

        JButton btCargar = new JButton("Cargar datos");
        btCargar.addActionListener(_e -> buscarPorFolioSeleccionado());
        p.add(btCargar);

        JButton btImprimir = new JButton("Imprimir hoja");
        btImprimir.addActionListener(_e -> imprimirHoja());
        p.add(btImprimir);

        return p;
    }

    private JPanel construirPanelDatos() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Datos para la hoja"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int y = 0;

        // Fila 1: solo folio (numero_nota queda oculto)
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("Folio:"), c);

        c.gridx = 1; 
        c.gridwidth = 3;
        txtFolio.setEditable(false);
        p.add(txtFolio, c);
        c.gridwidth = 1;

        // Campo interno, no visible, pero lo seguimos usando para listarDetalleDeNota
        txtNumeroNota.setEditable(false);
        txtNumeroNota.setVisible(false);

        y++;

        // Fila 2: cliente
        y++;
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("Nombre del cliente:"), c);
        c.gridx = 1; c.gridwidth = 3;
        txtCliente.setEditable(false);
        p.add(txtCliente, c);
        c.gridwidth = 1;

        // Fila 3: datos del vestido
        y++;
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("Modelo:"), c);
        c.gridx = 1;
        txtModelo.setEditable(false);
        p.add(txtModelo, c);

        c.gridx = 2;
        p.add(new JLabel("Talla:"), c);
        c.gridx = 3;
        txtTalla.setEditable(false);
        p.add(txtTalla, c);

        // Fila 4: color
        y++;
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("Color:"), c);
        c.gridx = 1; c.gridwidth = 3;
        txtColor.setEditable(false);
        p.add(txtColor, c);
        c.gridwidth = 1;

        // Fechas
        y++;
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("Fecha del apartado:"), c);
        c.gridx = 1;
        txtFechaApartado.setEditable(false);
        p.add(txtFechaApartado, c);

        c.gridx = 2;
        p.add(new JLabel("Fecha del evento:"), c);
        c.gridx = 3;
        txtFechaEvento.setEditable(false);
        p.add(txtFechaEvento, c);

        y++;
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("Fecha del documento:"), c);
        c.gridx = 1;
        txtFechaDocumento.setEditable(true);
        txtFechaDocumento.setText(FECHA_DOC.format(LocalDate.now()));
        p.add(txtFechaDocumento, c);

        // Otros productos
        y++;
        c.gridx = 0; c.gridy = y;
        c.anchor = GridBagConstraints.NORTHWEST;
        p.add(new JLabel("Otros productos entregados:"), c);
        c.gridx = 1; c.gridwidth = 3;
        txtOtrosProductos.setLineWrap(true);
        txtOtrosProductos.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(txtOtrosProductos);
        sp.setPreferredSize(new Dimension(300,80));
        p.add(sp, c);

        return p;
    }

    // ================== BÚSQUEDA ==================

    /** Carga en el combo todos los folios liquidados del teléfono indicado. */
    private void cargarFoliosPorTelefono() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (tel == null || tel.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Capture el teléfono del cliente.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        tel = tel.trim();

        try {
            List<Nota> notas = notasDAO.listarNotasLiquidadasPorTelefono(tel);
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (Nota n : notas) {
                String folio = n.getFolio();
                if (folio != null && !folio.isBlank()) {
                    model.addElement(folio.trim());
                }
            }
            cbFolios.setModel(model);

            if (model.getSize() == 0) {
                JOptionPane.showMessageDialog(this,
                        "No se encontraron folios liquidados para ese teléfono.",
                        "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
                limpiarCampos();
            } else {
                cbFolios.setSelectedIndex(0);
                buscarPorFolioSeleccionado();   // carga el primero
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al buscar folios: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void buscarPorFolioSeleccionado() {
        Object sel = cbFolios.getSelectedItem();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un folio.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String folio = sel.toString().trim();
        if (folio.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un folio válido.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        buscarPorFolio(folio);
    }

    private void buscarPorFolio(String folio) {
        try {
            Nota n = notasDAO.buscarNotaPorFolio(folio);
            if (n == null) {
                JOptionPane.showMessageDialog(this, "No se encontró una nota activa con ese folio.",
                        "Aviso", JOptionPane.INFORMATION_MESSAGE);
                limpiarCampos();
                return;
            }
            Double saldo = n.getSaldo();
            if (saldo != null && Math.abs(saldo) > 0.009) {
                JOptionPane.showMessageDialog(this,
                        "El folio aún tiene saldo (" + String.format("%.2f", saldo) + ").\n" +
                        "Solo se pueden imprimir hojas de entrega de notas liquidadas.",
                        "No permitido", JOptionPane.WARNING_MESSAGE);
                limpiarCampos();
                return;
            }

            this.notaActual = n;
            llenarCamposDesdeNota(n);
            cargarDetalle(n);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al buscar el folio: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarCampos() {
        notaActual = null;
        txtNumeroNota.setText("");
        txtFolio.setText("");
        txtCliente.setText("");
        txtModelo.setText("");
        txtTalla.setText("");
        txtColor.setText("");
        txtFechaApartado.setText("");
        txtFechaEvento.setText("");
        txtOtrosProductos.setText("");
        modelDetalle.setRowCount(0);
    }

    private void llenarCamposDesdeNota(Nota n) throws SQLException {
        txtNumeroNota.setText(n.getNumeroNota() == null ? "" : String.valueOf(n.getNumeroNota()));
        txtFolio.setText(n.getFolio() == null ? "" : n.getFolio());

        // Cliente
        String telefono = n.getTelefono();
        String nombreCompleto = "";
        String fechaEventoStr = "";
        if (telefono != null && !telefono.isBlank()) {
            Map<String,String> det = clienteDAO.detalleGenericoPorTelefono(telefono);
            if (det != null) {
                String nombre = n(det.get("nombre"));
                String apPat  = n(det.get("apellido_paterno"));
                String apMat  = n(det.get("apellido_materno"));
                nombreCompleto = (nombre + " " + apPat + " " + apMat).trim().replaceAll("\\s+"," ");
                fechaEventoStr = n(det.get("fecha_evento"));
            }
        }
        txtCliente.setText(nombreCompleto);

        // Fechas
        LocalDate fechaApartado = null;
        LocalDateTime fr = n.getFechaRegistro();
        if (fr != null) fechaApartado = fr.toLocalDate();
        txtFechaApartado.setText(fechaApartado == null ? "" : FECHA_CORTA.format(fechaApartado));

        LocalDate fechaEvento = null;
        if (fechaEventoStr != null && !fechaEventoStr.isBlank()) {
            try {
                fechaEvento = LocalDate.parse(fechaEventoStr);
            } catch (Exception ignore) {}
        }
        txtFechaEvento.setText(fechaEvento == null ? "" : FECHA_CORTA.format(fechaEvento));
    }

    private void cargarDetalle(Nota n) throws SQLException {
        modelDetalle.setRowCount(0);
        txtOtrosProductos.setText("");

        List<NotaDetalle> det = notasDAO.listarDetalleDeNota(n.getNumeroNota());
        if (det == null || det.isEmpty()) return;

        NotaDetalle principal = null;
        StringBuilder otros = new StringBuilder();

        for (NotaDetalle d : det) {
            String articulo = n(d.getArticulo());
            String modelo = n(d.getModelo());
            String talla = n(d.getTalla());
            String color = n(d.getColor());

            modelDetalle.addRow(new Object[]{articulo, modelo, talla, color});

            boolean esVestido = articulo.toUpperCase().contains("VESTIDO");
            if (principal == null && esVestido) {
                principal = d;
            } else {
                if (!articulo.isBlank()) {
                    if (otros.length() > 0) otros.append("\n");
                    otros.append(articulo);
                }
            }
        }

        if (principal == null) {
            principal = det.get(0);
        }

        if (principal != null) {
            txtModelo.setText(n(principal.getModelo()));
            txtTalla.setText(n(principal.getTalla()));
            txtColor.setText(n(principal.getColor()));
        }

        txtOtrosProductos.setText(otros.toString());
    }

    // ================== IMPRESIÓN ==================

    private void imprimirHoja() {
        if (notaActual == null) {
            JOptionPane.showMessageDialog(this, "Primero seleccione un folio válido y liquidado.",
                    "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Printable printable = crearPrintable();

        imprimirYConfirmarAsync(printable,
                () -> JOptionPane.showMessageDialog(this, "Impresión finalizada correctamente.",
                        "Impresión", JOptionPane.INFORMATION_MESSAGE),
                () -> JOptionPane.showMessageDialog(this, "La impresora no confirmó la impresión.",
                        "Impresión", JOptionPane.WARNING_MESSAGE));
    }

    private Printable crearPrintable() {
        // Copia local de los datos para congelarlos al momento de imprimir
        final Empresa emp = empresaActual;

        final String numeroNota = txtNumeroNota.getText();
        final String cliente = txtCliente.getText();
        final String modelo = txtModelo.getText();
        final String talla = txtTalla.getText();
        final String color = txtColor.getText();
        final String fechaApartado = txtFechaApartado.getText();
        final String fechaEvento = txtFechaEvento.getText();
        final String fechaDoc = txtFechaDocumento.getText();
        final String otros = txtOtrosProductos.getText();

        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE;

            Graphics2D g = (Graphics2D) graphics;
            g.setColor(Color.BLACK);
            g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int y = 40;
            int xLeft = 40;
            int xRight = (int) pageFormat.getImageableWidth() - 40;

            // Encabezado empresa
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            String nombreEmp = (emp != null && emp.getRazonSocial() != null && !emp.getRazonSocial().isBlank())
                    ? emp.getRazonSocial()
                    : "MIANOVIAS";
            drawCentered(g, nombreEmp, (int) pageFormat.getImageableWidth() / 2, y);
            y += 22;

            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String direccion = "";
            if (emp != null) {
                String calle = n(emp.getCalleNumero());
                String col = n(emp.getColonia());
                String cd = n(emp.getCiudad());
                String edo = n(emp.getEstado());
                String cp = n(emp.getCodigoPostal());
                direccion = (calle + ", " + col + ", C.P. " + cp + ", " + cd + ", " + edo)
                        .replaceAll(",\\s*,", ", ");
            }
            drawCentered(g, direccion.trim(), (int) pageFormat.getImageableWidth() / 2, y);
            y += 30;

            // Título documento
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            drawCentered(g, "RECIBO DE ENTREGA DE VESTIDOS AL CLIENTE",
                    (int) pageFormat.getImageableWidth() / 2, y);

            // Nota ref a la derecha
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String ref = "NOTA REF.: " + (numeroNota.isBlank() ? "-" : numeroNota);
            int refWidth = g.getFontMetrics().stringWidth(ref);
            g.drawString(ref, xRight - refWidth, y - 16);

            y += 25;

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));

            // Nombre del cliente
            g.drawString("NOMBRE DEL CLIENTE:", xLeft, y);
            g.drawRect(xLeft + 130, y - 12, 350, 18);
            g.drawString(cliente, xLeft + 135, y + 2);
            y += 28;

            // Datos del vestido
            g.drawString("DATOS DEL VESTIDO", xLeft, y);
            y += 16;

            g.drawString("MODELO:", xLeft, y);
            g.drawRect(xLeft + 70, y - 12, 100, 18);
            g.drawString(modelo, xLeft + 75, y + 2);

            g.drawString("TALLA:", xLeft + 190, y);
            g.drawRect(xLeft + 240, y - 12, 60, 18);
            g.drawString(talla, xLeft + 245, y + 2);

            g.drawString("COLOR:", xLeft + 320, y);
            g.drawRect(xLeft + 370, y - 12, 110, 18);
            g.drawString(color, xLeft + 375, y + 2);
            y += 30;

            // Fechas
            g.drawString("FECHA DEL APARTADO:", xLeft, y);
            g.drawRect(xLeft + 150, y - 12, 120, 18);
            g.drawString(fechaApartado, xLeft + 155, y + 2);

            g.drawString("FECHA DEL EVENTO:", xLeft + 290, y);
            g.drawRect(xLeft + 420, y - 12, 120, 18);
            g.drawString(fechaEvento, xLeft + 425, y + 2);
            y += 30;

            // Otros productos
            g.drawString("OTROS PRODUCTOS ENTREGADOS:", xLeft, y);
            y += 8;
            int altoCaja = 60;
            g.drawRect(xLeft, y, xRight - xLeft, altoCaja);
            drawTextMultiline(g, otros, xLeft + 4, y + 14, xRight - xLeft - 8, 14);
            y += altoCaja + 30;

            // Párrafo
            String parrafo = "HE RECIBIDO EL VESTIDO AQUÍ DESCRITO, EL CUAL SE ME ESTÁ ENTREGANDO EN " +
                    "TIEMPO Y FORMA EN PERFECTAS CONDICIONES, AJUSTADA A MI DESEO, LIMPIO Y PLANCHADO. " +
                    "EL CUAL HE REVISADO Y ME HE CERCIORADO QUE CUMPLE CON LAS CONDICIONES ESPECIFICADAS.";
            drawTextMultiline(g, parrafo, xLeft, y, xRight - xLeft, 14);
            y += 60;

            // Firma de cliente
            g.drawString("RECIBÍ DE CONFORMIDAD", xLeft + 160, y);
            y += 45;
            int lineaAncho = 280;
            int lineaX = xLeft + 120;
            g.drawLine(lineaX, y, lineaX + lineaAncho, y);
            g.drawString(cliente, lineaX + 10, y + 12);
            g.drawString("FIRMA Y NOMBRE", lineaX + 80, y + 24);
            y += 50;

            // Fecha y asesora
            g.drawString("EN ________________________ A:", xLeft, y);
            g.drawRect(xLeft + 180, y - 12, 120, 18);
            g.drawString(fechaDoc, xLeft + 185, y + 2);
            y += 40;

            int xLineaAsesora = xLeft + 360;
            g.drawLine(xLineaAsesora, y, xLineaAsesora + 180, y);
            g.drawString("ENTREGADO POR ASESORA", xLineaAsesora + 20, y + 14);

            return Printable.PAGE_EXISTS;
        };
    }

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
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
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
            }, "hoja-entrega-print-job").start();

            wait.setVisible(true);
        } catch (Exception ex) {
            onFail.run();
        }
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int y) {
        if (text == null) text = "";
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - w / 2, y);
    }

    private void drawTextMultiline(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null) return;
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            String test = line.length() == 0 ? w : line + " " + w;
            int wPix = g.getFontMetrics().stringWidth(test);
            if (wPix > maxWidth && line.length() > 0) {
                g.drawString(line.toString(), x, y);
                y += lineHeight;
                line.setLength(0);
                line.append(w);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(w);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, y);
        }
    }

    private String n(String s) {
        return (s == null) ? "" : s.trim();
    }
}
