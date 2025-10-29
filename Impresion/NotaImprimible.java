package Impresion;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Imprime una nota “provisional” (texto) con salto de línea y paginación básica. */
public class NotaImprimible implements Printable {

    public static class LineaItem {
        public int cantidad;
        public String descripcion;
        public BigDecimal precioUnit;  // opcional
        public BigDecimal importe;     // opcional

        public LineaItem(int cantidad, String descripcion, BigDecimal precioUnit, BigDecimal importe) {
            this.cantidad = cantidad;
            this.descripcion = descripcion == null ? "" : descripcion;
            this.precioUnit = precioUnit;
            this.importe = importe;
        }
    }

    public static class Nota {
        public String folio;                 // CN-0001, CR-..., AB-...
        public String tipoOperacion;         // CONTADO / CREDITO / ABONO / DEVOLUCION / CAMBIO_FECHA / CANCELACION
        public java.time.LocalDateTime fechaHora;
        public String cliente;               // opcional
        public String telefono;              // opcional
        public String observaciones;         // opcional
        public List<LineaItem> items = new ArrayList<>();
        public BigDecimal subtotal = BigDecimal.ZERO;
        public BigDecimal descuento = BigDecimal.ZERO; // total
        public BigDecimal total = BigDecimal.ZERO;
        public String usuario;               // cajero/atendió (opcional)
    }

    // ======== configuración visual “provisional” ========
    private final Nota nota;
    private final Font fTitulo = new Font("SansSerif", Font.BOLD, 14);
    private final Font fLabel  = new Font("SansSerif", Font.PLAIN, 10);
    private final Font fMono   = new Font("Monospaced", Font.PLAIN, 10);
    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));
    private final int margen = 36; // 0.5"

    public NotaImprimible(Nota nota) { this.nota = nota; }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.black);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double x = pf.getImageableX() + margen/2.0;
        double y = pf.getImageableY() + margen/2.0;
        double w = pf.getImageableWidth() - margen;
        double h = pf.getImageableHeight() - margen;

        int lineH = g2.getFontMetrics(fLabel).getHeight();
        int cursorY = (int) y;

        // === Construir las líneas finalizadas (ya envueltas) ===
        List<String> lineas = new ArrayList<>();
        DateTimeFormatter FDT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        lineas.add("MIANOVIAS — Documento provisional");
        lineas.add("Folio: " + nz(nota.folio) + "    Tipo: " + nz(nota.tipoOperacion));
        lineas.add("Fecha/Hora: " + (nota.fechaHora==null? "": nota.fechaHora.format(FDT)));
        if (nz(nota.cliente).length() > 0)   lineas.add("Cliente: " + nota.cliente);
        if (nz(nota.telefono).length() > 0)  lineas.add("Tel.: " + nota.telefono);
        if (nz(nota.usuario).length() > 0)   lineas.add("Atendió: " + nota.usuario);
        lineas.add(repeat('-', 80));

        // Cabecera de items
        String cab = String.format("%-6s %-54s %10s %10s", "Cant.", "Descripción", "P.Unit", "Importe");
        lineas.add(cab);
        lineas.add(repeat('-', 80));

        // Items envueltos
        for (LineaItem it : nota.items) {
            String pUnit = it.precioUnit == null ? "" : money.format(it.precioUnit);
            String imp   = it.importe    == null ? "" : money.format(it.importe);
            String base  = String.format("%-6s %-54s %10s %10s",
                    it.cantidad, trim(it.descripcion,54), pUnit, imp);
            lineas.add(base);

            // Si la descripción excede 54, partirla en varias líneas
            List<String> wraps = wrap(it.descripcion, 54);
            for (int i=1; i<wraps.size(); i++) {
                lineas.add(String.format("%-6s %-54s %10s %10s", "", wraps.get(i), "", ""));
            }
        }

        lineas.add(repeat('-', 80));
        if (nota.subtotal != null)  lineas.add(padLeft("Subtotal: " + money.format(nota.subtotal), 80));
        if (nota.descuento != null && nota.descuento.signum()!=0)
            lineas.add(padLeft("Descuento: " + money.format(nota.descuento), 80));
        if (nota.total != null)     lineas.add(padLeft("TOTAL: " + money.format(nota.total), 80));
        lineas.add(repeat('-', 80));
        if (nz(nota.observaciones).length() > 0) {
            lineas.add("Obs.:");
            for (String wline : wrap(nota.observaciones, 80)) lineas.add("  " + wline);
            lineas.add(repeat('-', 80));
        }
        lineas.add("Este documento es informativo. Formato de impresión provisional.");

        // === Paginación ===
        int maxLinesPerPage = (int) (h / lineH);
        int totalPages = (int) Math.ceil(lineas.size() / (double) maxLinesPerPage);
        if (pageIndex >= totalPages) return NO_SUCH_PAGE;

        int start = pageIndex * maxLinesPerPage;
        int end   = Math.min(lineas.size(), start + maxLinesPerPage);

        // === Dibujo ===
        // título
        g2.setFont(fTitulo);
        g2.drawString("NOTA DE VENTA", (int)x, cursorY);
        cursorY += g2.getFontMetrics().getHeight() + 2;

        g2.setFont(fMono);
        for (int i = start; i < end; i++) {
            cursorY += lineH;
            g2.drawString(lineas.get(i), (int)x, cursorY);
        }

        // pie con número de página
        g2.setFont(fLabel);
        String pie = "Página " + (pageIndex+1) + " / " + Math.max(1,totalPages);
        int pieW = g2.getFontMetrics().stringWidth(pie);
        g2.drawString(pie, (int)(x + w - pieW), (int)(y + h));

        return PAGE_EXISTS;
    }

    // ===== utilitario de envoltura de texto (monoespaciado) =====
    private static List<String> wrap(String txt, int width) {
        List<String> out = new ArrayList<>();
        if (txt == null) { out.add(""); return out; }
        txt = txt.replace("\r","").trim();
        while (txt.length() > width) {
            int cut = txt.lastIndexOf(' ', width);
            if (cut <= 0) cut = width;
            out.add(txt.substring(0, cut).trim());
            txt = txt.substring(cut).trim();
        }
        out.add(txt);
        return out;
    }
    private static String repeat(char c, int n){ return String.valueOf(c).repeat(Math.max(0,n)); }
    private static String padLeft(String s, int width){ return String.format("%" + width + "s", s==null?"":s); }
    private static String trim(String s, int w){ if (s==null) return ""; return s.length()<=w? s : s.substring(0,w-1)+"…"; }
    private static String nz(String s){ return s==null? "" : s; }
}
