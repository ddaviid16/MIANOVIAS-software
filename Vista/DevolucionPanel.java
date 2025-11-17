// Vista/DevolucionPanel.java
package Vista;

import Controlador.NotasDAO;
import Controlador.DevolucionService;
import Controlador.clienteDAO;
import Controlador.EmpresaDAO;

import Utilidades.TelefonosUI;

import Modelo.ClienteResumen;
import Modelo.Empresa;
import Modelo.Nota;
import Modelo.NotaDetalle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;


import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;

public class DevolucionPanel extends JPanel {

    private final JTextField txtTelefono = new JTextField();
    private final JTextField txtNombre   = ro();
    private final JTextField txtUltimaNota = ro();

    private final JComboBox<Nota> cbNotas = new JComboBox<>();
    private final DefaultTableModel model;
    private final JTable tb;

    private final JTextArea txtMotivo = new JTextArea(3, 40);

    private String lastTel = null;
    private final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public DevolucionPanel() {
        setLayout(new BorderLayout(10,10));

        TelefonosUI.instalar(txtTelefono, 10);
        // === TOP: cliente + notas ===
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int y=0;

        addCell(top,c,0,y,new JLabel("Teléfono cliente:"),1,false);
        addCell(top,c,1,y,txtTelefono,1,true);
        addCell(top,c,2,y,new JLabel("Nombre:"),1,false);
        addCell(top,c,3,y,txtNombre,1,true);
        y++;

        addCell(top,c,2,y,new JLabel("Nota a devolver:"),1,false);
        cbNotas.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Nota n) {
                    String folio = (n.getFolio()==null || n.getFolio().isBlank()) ? "s/folio" : n.getFolio();
                    double total = n.getTotal()==null ? 0.0 : n.getTotal();
                    double saldo = n.getSaldo()==null ? 0.0 : n.getSaldo();
                    setText(String.format("%s  [%s]  Total: %.2f  Saldo: %.2f",
                            folio, n.getTipo(), total, saldo));
                } else if (value == null) {
                    setText("— Selecciona —");
                }
                return this;
            }
        });

        cbNotas.addActionListener(_e -> cargarDetalleNota());
        addCell(top,c,3,y,cbNotas,1,true); y++;

        add(top, BorderLayout.NORTH);

        // === Tabla detalle de la nota ===
        String[] cols = {"Devolver","Código","Artículo","Marca","Modelo","Talla","Color","Precio","%Desc","Desc.$","Subtotal","_ID"};
        model = new DefaultTableModel(cols,0){
            @Override public Class<?> getColumnClass(int col){ return col==0?Boolean.class:Object.class; }
            @Override public boolean isCellEditable(int r,int c){ return c==0; }
        };
        tb = new JTable(model);
        tb.setRowHeight(24);
        tb.setAutoCreateRowSorter(true);
        tb.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        tb.getColumnModel().getColumn(7).setCellRenderer(right);
        tb.getColumnModel().getColumn(8).setCellRenderer(right);
        tb.getColumnModel().getColumn(9).setCellRenderer(right);
        tb.getColumnModel().getColumn(10).setCellRenderer(right);

        // Ocultar la última columna (_ID) en la vista (permanece en el modelo)
        tb.getColumnModel().removeColumn(tb.getColumnModel().getColumn(11));

        add(new JScrollPane(tb), BorderLayout.CENTER);

        // === BOTTOM: motivo + acciones ===
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0,10,10,10));
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(6,6,6,6);
        d.fill = GridBagConstraints.HORIZONTAL;
        d.weightx = 1;
        int r=0;

        addCell(bottom,d,0,r,new JLabel("Motivo de la devolución:"),1,false);
        txtMotivo.setLineWrap(true);
        txtMotivo.setWrapStyleWord(true);
        addCell(bottom,d,1,r,new JScrollPane(txtMotivo),2,true);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        JButton btSelTodo = new JButton("Marcar todo");
        JButton btNinguno = new JButton("Quitar todo");
        actions.add(btSelTodo);
        actions.add(btNinguno);
        JButton btGuardar = new JButton("Registrar DEVOLUCIÓN");
        actions.add(btGuardar);
        addCell(bottom,d,3,r,actions,1,false);
        r++;

        add(bottom, BorderLayout.SOUTH);

        // hooks
        txtTelefono.addActionListener(_e -> cargarClienteYNotas());
        ((AbstractDocument) txtTelefono.getDocument()).addDocumentListener((SimpleDocListener) this::cargarClienteYNotas);

        btSelTodo.addActionListener(_e -> marcar(true));
        btNinguno.addActionListener(_e -> marcar(false));
        btGuardar.addActionListener(this::guardar);
    }

    private void marcar(boolean v){
        if (tb.isEditing()) {
            try { tb.getCellEditor().stopCellEditing(); } catch(Exception ignore){}
        }
        for (int i=0;i<model.getRowCount();i++) model.setValueAt(v,i,0);
    }

    private void cargarClienteYNotas() {
        String tel = Utilidades.TelefonosUI.soloDigitos(txtTelefono.getText());
        if (tel.isEmpty()) { limpiar(); return; }
        if (tel.equals(lastTel)) return;

        try {
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                txtNombre.setText("— no registrado —");
                cbNotas.removeAllItems();
                lastTel = tel;
                return;
            }
            txtNombre.setText(cr.getNombreCompleto()==null?"":cr.getNombreCompleto());

            NotasDAO ndao = new NotasDAO();
            cbNotas.removeAllItems();
            List<Nota> lista = ndao.listarNotasClienteParaDevolucion(tel);
            if (lista.isEmpty()) cbNotas.addItem(null);
            else for (Nota n: lista) cbNotas.addItem(n);

            lastTel = tel;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDetalleNota() {
        model.setRowCount(0);
        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) return;
        try {
            NotasDAO ndao = new NotasDAO();
            List<NotaDetalle> det = ndao.listarDetalleDeNota(sel.getNumeroNota());
            for (NotaDetalle d : det) {
                model.addRow(new Object[]{
                        Boolean.FALSE,
                        d.getCodigoArticulo(),
                        d.getArticulo(),
                        d.getMarca(),
                        d.getModelo(),
                        d.getTalla(),
                        d.getColor(),
                        fmt(d.getPrecio()),
                        fmt(d.getDescuento()),
                        "0.00",
                        fmt(d.getSubtotal()),
                        d.getId()                    // _ID (oculto en la vista)
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar el detalle: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===== GUARDAR (con impresión de la DV) =====
    private void guardar(ActionEvent ev) {
        if (tb.isEditing()) {
            try { tb.getCellEditor().stopCellEditing(); } catch(Exception ignore){}
        }

        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) { JOptionPane.showMessageDialog(this, "Selecciona una nota."); return; }

        // Recolecta IDs de renglón marcados
        List<Integer> idsDetalle = new ArrayList<>();
        for (int r=0; r<model.getRowCount(); r++) {
            Object v = model.getValueAt(r, 0);
            if (Boolean.TRUE.equals(v)) {
                Object idObj = model.getValueAt(r, 11); // _ID oculto en el modelo
                if (idObj != null) idsDetalle.add(Integer.valueOf(String.valueOf(idObj)));
            }
        }
        if (idsDetalle.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Marca al menos un artículo a devolver.");
            return;
        }

        String motivo = txtMotivo.getText()==null? "" : txtMotivo.getText().trim();
        if (motivo.length() < 3) {
            JOptionPane.showMessageDialog(this,"Describe el motivo de la devolución (min. 3 caracteres).");
            return;
        }

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Registrar DEVOLUCIÓN?\nNota origen: "+sel.getNumeroNota()+"\nRenglones: "+idsDetalle.size(),
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            // 1) Registrar DV
            DevolucionService svc = new DevolucionService();
            DevolucionService.ResultadoDev res =
                    svc.registrarDevolucionMoviendoDetalles(sel.getNumeroNota(), idsDetalle, motivo);

            // 2) Leer detalle de la DV y calcular total (solo para listar en impresión)
            NotasDAO ndao = new NotasDAO();
            List<NotaDetalle> detDV = ndao.listarDetalleDeNota(res.numeroNotaDV);
            double totalDV = 0.0;
            for (NotaDetalle d : detDV) totalDV += (d.getSubtotal()==null?0.0:d.getSubtotal());

            // 3) Armar objeto Nota DV para imprimir
            Nota nDV = new Nota();
            nDV.setNumeroNota(res.numeroNotaDV);
            nDV.setTelefono(txtTelefono.getText().trim());
            nDV.setTipo("DV");
            nDV.setTotal(res.saldoAFavor); // <-- CAMBIO: imprimimos el SALDO A FAVOR correcto
            nDV.setSaldo(0.0);
            nDV.setStatus("A");
            nDV.setFolio(res.folio);

            // 4) Empresa e impresión
            EmpresaInfo emp = cargarEmpresaInfo();
            Printable prn = construirPrintableDevolucion(emp, nDV, detDV, res.folio);

            imprimirYConfirmarAsync(
                prn,
                () -> JOptionPane.showMessageDialog(this,
                        "Devolución registrada e impresa.\nFolio DV: " + res.folio + "   Nota DV: " + res.numeroNotaDV),
                () -> JOptionPane.showMessageDialog(this,
                        "Devolución registrada pero la impresión no se confirmó.\n" +
                        "Puedes reimprimirla desde el módulo de notas (Folio: " + res.folio + ").",
                        "Aviso", JOptionPane.WARNING_MESSAGE)
            );

            // 5) Limpiar UI / refrescar
            txtMotivo.setText("");
            cargarDetalleNota();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,"Error al registrar devolución: "+e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiar(){
        txtNombre.setText("— no registrado —");
        txtUltimaNota.setText("");
        cbNotas.removeAllItems();
        model.setRowCount(0);
        lastTel = null;
    }

    // ====== IMPRESIÓN: helpers mínimos (reusados) ======

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
            JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true);
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
            }, "dv-print-job").start();

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

    // Toma la primera fila de Empresa
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
            Integer numEmp = obtenerNumeroEmpresaBD();
            Empresa e = edao.buscarPorNumero(numEmp);
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

    
    // ====== NOTA DE DEVOLUCIÓN ======
    private Printable construirPrintableDevolucion(
            EmpresaInfo emp,
            Nota n,                      // usa: telefono, total
            List<NotaDetalle> dets,      // artículos devueltos (si los tienes)
            String folioTxt) {

        final java.time.format.DateTimeFormatter MX = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

        final String tel1     = (n.getTelefono() == null) ? "" : n.getTelefono();
        final String fechaHoy = LocalDate.now().format(MX);
        final double total    = (n.getTotal() == null) ? 0d : n.getTotal();
        final String folio    = (folioTxt == null || folioTxt.isBlank()) ? "—" : folioTxt;

        // Cliente (nombre/tel2 y pruebas si existen)
        String _cliNombre = "", _cliTel2 = "", _cliPrueba1 = "", _cliPrueba2 = "";
        try {
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel1);
            if (cr != null) {
                _cliNombre  = cr.getNombreCompleto() == null ? "" : cr.getNombreCompleto();
                _cliTel2    = cr.getTelefono2() == null ? "" : cr.getTelefono2();
                if (cr.getFechaPrueba1()!=null) _cliPrueba1 = cr.getFechaPrueba1().format(MX);
                if (cr.getFechaPrueba2()!=null) _cliPrueba2 = cr.getFechaPrueba2().format(MX);
            }
        } catch (Exception ignore) { }
        final String cliNombre  = _cliNombre;
        final String cliTel2    = _cliTel2;
        final String cliPrueba1 = _cliPrueba1;
        final String cliPrueba2 = _cliPrueba2;

        // Medidas (opcional)
        String medBusto = "", medCintura = "", medCadera = "";
        try {
            clienteDAO cdao = new clienteDAO();
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

                // Columna derecha (correo, web, redes)
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

                yRight2 = drawWrapped(g2, labelIf("Fecha de emisión: ", fechaHoy), x + leftW + gapCols, yRight2, rightW);
                if (!cliPrueba1.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 1: ", cliPrueba1), x + leftW + gapCols, yRight2 + 2, rightW);
                if (!cliPrueba2.isEmpty())
                    yRight2 = drawWrapped(g2, labelIf("Fecha de prueba 2: ", cliPrueba2), x + leftW + gapCols, yRight2 + 2, rightW);

                y = Math.max(yLeft, yRight2) + 10;

                // Tabla de artículos devueltos
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
                    for (NotaDetalle d : dets) {
                        String artBase = (d.getArticulo() == null || d.getArticulo().isBlank())
                                ? String.valueOf(d.getCodigoArticulo()) : d.getArticulo();
                        String detalle = (d.getCodigoArticulo() > 0 ? d.getCodigoArticulo() + " · " : "")
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
                    try (java.io.InputStream in = DevolucionPanel.class.getResourceAsStream(cp)) {
                        if (in == null) return null;
                        javax.imageio.ImageIO.setUseCache(false);
                        return trimTransparent(javax.imageio.ImageIO.read(in));
                    }
                } catch(Exception e){ return null; }
            }
            private BufferedImage trimTransparent(BufferedImage src) {
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
        };
    }

    // helpers UI
    private JTextField ro(){ JTextField t=new JTextField(); t.setEditable(false); t.setBackground(new Color(235,235,235)); return t; }
    private void addCell(JPanel p, GridBagConstraints c, int x,int y,JComponent comp,int span,boolean growX){ c.gridx=x;c.gridy=y;c.gridwidth=span;c.weightx=growX?1:0;p.add(comp,c);c.gridwidth=1; }
    private String fmt(Double v){ return v==null? "0.00" : String.format("%.2f", v); }
    private String n(String s){ return s==null?"":s; }

    @FunctionalInterface
    private interface SimpleDocListener extends javax.swing.event.DocumentListener {
        void on();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e){ on(); }
    }
    
}
