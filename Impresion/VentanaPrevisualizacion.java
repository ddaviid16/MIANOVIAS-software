package Impresion;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;   // <-- IMPORTANTE
import java.awt.event.WindowEvent;    // <-- IMPORTANTE
import java.awt.image.BufferedImage;
import java.awt.print.*;

public class VentanaPrevisualizacion extends JDialog {

    private javax.swing.Timer hotplugTimer;
    private final Printable printable;
    private final PrinterJob job = PrinterJob.getPrinterJob();
    private PageFormat formato = job.defaultPage();

    private final JComboBox<PrintService> cbPrinters = new JComboBox<>();
    private final JSpinner spCopias = new JSpinner(new SpinnerNumberModel(1,1,99,1));
    private final JComboBox<String> cbTamanio = new JComboBox<>(new String[]{"Letter (8.5x11)","A4 (210x297)"});
    private final JComboBox<String> cbOrientacion = new JComboBox<>(new String[]{"Vertical","Horizontal"});
    private final JLabel lblPag = new JLabel("1 / 1");
    private final JLabel lienzo = new JLabel("", SwingConstants.CENTER);
    private int paginaActual = 0;
    private int totalPaginas = 1;
    private double zoom = 0.75;

    public VentanaPrevisualizacion(Window owner, String titulo, Printable printable) {
        super(owner, titulo, ModalityType.MODELESS);
        this.printable = printable;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton btConfig = new JButton("Configurar página…");
        JButton btImprimir = new JButton("Imprimir");
        JButton btCerrar = new JButton("Cerrar");
        JButton btPrev = new JButton("◀");
        JButton btNext = new JButton("▶");
        JSlider slZoom = new JSlider(25, 200, (int)(zoom*100));
        slZoom.setPreferredSize(new Dimension(150, 24));

        top.add(new JLabel("Impresora:"));
        cbPrinters.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel l = new JLabel(value==null? "" : value.getName());
            if (isSelected) { l.setOpaque(true); l.setBackground(list.getSelectionBackground()); l.setForeground(list.getSelectionForeground()); }
            return l;
        });
        top.add(cbPrinters);
        top.add(new JLabel("Copias:"));
        top.add(spCopias);
        top.add(new JLabel("Tamaño:"));
        top.add(cbTamanio);
        top.add(new JLabel("Orientación:"));
        top.add(cbOrientacion);
        top.add(btConfig);
        top.add(btPrev); top.add(lblPag); top.add(btNext);
        top.add(new JLabel("Zoom:")); top.add(slZoom);
        add(top, BorderLayout.NORTH);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.add(btImprimir);
        bottom.add(btCerrar);
        add(bottom, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(lienzo);
        add(scroll, BorderLayout.CENTER);

        cbTamanio.addActionListener(_e -> actualizarPaper());
        cbOrientacion.addActionListener(_e -> actualizarOrientacion());
        btConfig.addActionListener(this::abrirConfigSO);
        btImprimir.addActionListener(this::imprimir);
        btCerrar.addActionListener(_e -> dispose());
        btPrev.addActionListener(_e -> { if (paginaActual>0){ paginaActual--; refrescar(); }});
        btNext.addActionListener(_e -> { if (paginaActual+1<totalPaginas){ paginaActual++; refrescar(); }});
        slZoom.addChangeListener(_e -> { zoom = ((JSlider)_e.getSource()).getValue()/100.0; refrescar(); });

        cargarImpresoras();

        hotplugTimer = new javax.swing.Timer(2000, _e -> checarHotplug());
        hotplugTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (hotplugTimer != null) hotplugTimer.stop();
            }
        });

        actualizarPaper();
        refrescar();
    }

    private void actualizarPaper() {
        Paper p = new Paper();
        if (cbTamanio.getSelectedIndex()==0) {
            p.setSize(8.5*72, 11*72);
        } else {
            p.setSize(595, 842);
        }
        int margen = 36;
        p.setImageableArea(margen, margen, p.getWidth()-2*margen, p.getHeight()-2*margen);
        formato.setPaper(p);
        actualizarOrientacion();
    }

    private void actualizarOrientacion() {
        if (cbOrientacion.getSelectedIndex()==0) formato.setOrientation(PageFormat.PORTRAIT);
        else formato.setOrientation(PageFormat.LANDSCAPE);
        paginaActual = 0;
        refrescar();
    }

    /** Vista previa nítida */
    private void refrescar() {
        totalPaginas = contarPaginas(formato);
        lblPag.setText((paginaActual+1) + " / " + totalPaginas);

        double scaleForPreview = Math.max(1.0, zoom) * 2.0;
        int imgW = (int) Math.ceil(formato.getWidth()  * scaleForPreview);
        int imgH = (int) Math.ceil(formato.getHeight() * scaleForPreview);

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setColor(Color.white);
        g2.fillRect(0,0,imgW,imgH);

        g2.scale(scaleForPreview, scaleForPreview);
        try {
            printable.print(g2, formato, paginaActual);
        } catch (PrinterException e) {
            g2.setColor(Color.red);
            g2.drawString("Error renderizando: " + e.getMessage(), 20, 20);
        } finally {
            g2.dispose();
        }

        int showW = (int) (formato.getWidth() * zoom);
        int showH = (int) (formato.getHeight() * zoom);
        Image scaled = img.getScaledInstance(showW, showH, Image.SCALE_SMOOTH);
        lienzo.setIcon(new ImageIcon(scaled));
    }

    private int contarPaginas(PageFormat pf) {
        int count = 0;
        try {
            for (int i = 0; i < 999; i++) {
                int ok = printable.print(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).createGraphics(), pf, i);
                if (ok == Printable.PAGE_EXISTS) count++;
                else break;
            }
        } catch (PrinterException ignored) {}
        return Math.max(1, count);
    }

    private void abrirConfigSO(ActionEvent e) {
        PageFormat pf = job.pageDialog(formato);
        if (pf != null) {
            formato = pf;
            paginaActual = 0;
            refrescar();
        }
    }

    // ============ Impresoras (hot-plug) ============

    private void cargarImpresoras() {
        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        PrintService[] svcs = PrintServiceLookup.lookupPrintServices(flavor, null);

        PrintService antes = (PrintService) cbPrinters.getSelectedItem();
        String nombreAntes = (antes == null) ? null : antes.getName();

        DefaultComboBoxModel<PrintService> model = new DefaultComboBoxModel<>(svcs);
        cbPrinters.setModel(model);

        if (nombreAntes != null) {
            for (PrintService s : svcs) {
                if (s.getName().equals(nombreAntes)) { cbPrinters.setSelectedItem(s); return; }
            }
        }
        PrintService def = PrintServiceLookup.lookupDefaultPrintService();
        if (def != null) {
            for (PrintService s : svcs) {
                if (s.getName().equals(def.getName())) { cbPrinters.setSelectedItem(s); return; }
            }
        }
        if (svcs.length > 0) cbPrinters.setSelectedIndex(0);
    }

    private void checarHotplug() {
        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        PrintService[] svcs = PrintServiceLookup.lookupPrintServices(flavor, null);
        if (!mismaLista(svcs)) cargarImpresoras();
    }
    private boolean mismaLista(PrintService[] svcs) {
        if (svcs.length != cbPrinters.getItemCount()) return false;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (PrintService s : svcs) names.add(s.getName());
        for (int i = 0; i < cbPrinters.getItemCount(); i++) {
            PrintService s = cbPrinters.getItemAt(i);
            if (!names.contains(s.getName())) return false;
        }
        return true;
    }

    private void imprimir(ActionEvent e) {
        try {
            cargarImpresoras(); // por si se conectó justo ahora
            PrintService sel = (PrintService) cbPrinters.getSelectedItem();
            if (sel != null) job.setPrintService(sel);
            job.setJobName("Hoja de ajuste " + System.currentTimeMillis());
            job.setCopies((Integer) spCopias.getValue());
            job.setPrintable(printable, formato);
            job.print();
            JOptionPane.showMessageDialog(this, "Enviado a imprimir.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al imprimir: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
