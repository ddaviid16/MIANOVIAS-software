package Impresion;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class AjusteImprimible implements Printable {

    public static class Datos {
        public String sucursalTitulo = "MIANOVIAS QUERÉTARO";
        public String subtitulo      = "AUTORIZACIÓN AJUSTE DE VESTIDO";

        public String nombreNovia;
        public String modeloVestido;
        public String talla;
        public String color;

        public LocalDate fechaEvento;
        public LocalDate fechaAjuste1;
        public LocalDate fechaAjuste2;
        public LocalDate fechaEntrega;
        public String otrasEspecificaciones = "";

    }

    private final Datos d;
    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    // Tipografías (ligeramente más chicas)
    private final Font fTitulo = new Font("SansSerif", Font.BOLD, 11);
    private final Font fSubt   = new Font("SansSerif", Font.PLAIN, 9);
    private final Font fLbl    = new Font("SansSerif", Font.PLAIN, 7);
    private final Font fCampo  = new Font("SansSerif", Font.PLAIN, 8);
    private final Font fChico  = new Font("SansSerif", Font.PLAIN, 6);

    // Utils
    private static double mm(double v){ return v * 72.0 / 25.4; }
    private static int i(double v){ return (int)Math.round(v); }
    private static String nz(String s){ return s == null ? "" : s; }
    private static String format(LocalDate d){ return d == null ? "" : d.format(MX); }

    public AjusteImprimible(Datos d){ this.d = d; }

    @Override public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        if (pageIndex > 0) return NO_SUCH_PAGE;

        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.black);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setStroke(new BasicStroke(0.5f));

        final double X0 = pf.getImageableX();
        final double Y0 = pf.getImageableY();
        final double W  = pf.getImageableWidth();
        final double M  = mm(10);
        final double X  = X0 + M;
        final double CW = W  - 2*M;
        double y = Y0 + M;

        // Encabezado
        g2.setFont(fTitulo);
        drawCentered(g2, d.sucursalTitulo, X, y, CW);
        y += g2.getFontMetrics().getHeight() + mm(1.2);

        g2.setFont(fSubt);
        drawCentered(g2, d.subtitulo, X, y, CW);
        y += g2.getFontMetrics().getHeight() + mm(2);

        g2.drawLine(i(X), i(y), i(X+CW), i(y));
        y += mm(4);

        // Alturas y gaps
        final double hCampo = mm(7);
        final double gapW   = mm(6);

        // ==== CUATRO COLUMNAS CON MISMO ANCHO ====
        final double colW = (CW - (3 * gapW)) / 4.0;  // 4 columnas iguales
        final double x1 = X;
        final double x2 = x1 + colW + gapW;
        final double x3 = x2 + colW + gapW;
        final double x4 = x3 + colW + gapW;

        // ===== Nombre
        y = drawLabeledBox(g2, "NOMBRE DE LA NOVIA:", nz(d.nombreNovia), X, y, CW, hCampo);
        y += mm(3.5);

        // ===== Modelo / Talla / Color (alineados con 4 columnas: x1, x2, x3)
        drawLabeledBox(g2, "MODELO DEL VESTIDO:", nz(d.modeloVestido), x1, y, colW, hCampo);
        drawLabeledBox(g2, "TALLA",               nz(d.talla),       x2, y, colW, hCampo);
        drawLabeledBox(g2, "COLOR",               nz(d.color),       x3, y, colW, hCampo);

        // Fechas: baja 2 mm para no tocar los recuadros de arriba
        y += hCampo + mm(3.5 + 2);

        // ===== Fechas (cada una en su columna: evento=x1, aj1=x2, aj2=x3, entrega=x4)
        drawLabeledBox(g2, "FECHA DE EVENTO:", format(d.fechaEvento),  x1, y, colW, hCampo);
        drawLabeledBox(g2, "FECHA AJUSTE 1:",  format(d.fechaAjuste1), x2, y, colW, hCampo);
        drawLabeledBox(g2, "FECHA AJUSTE 2:",  format(d.fechaAjuste2), x3, y, colW, hCampo);
        drawLabeledBox(g2, "FECHA ENTREGA:",   format(d.fechaEntrega), x4, y, colW, hCampo);

        // Bloque inferior un pelín más abajo
        y += hCampo + mm(8);

        // ===== Descripción del ajuste
        g2.setFont(fLbl);
        g2.drawString("DESCRIPCIÓN DEL AJUSTE:", i(X), i(y));
        y += mm(4);

        String[] rubros = {
            "TALLE", "COPA", "CINTURA", "CADERA", "HOMBROS / TIRANTES",
            "MANGA", "LARGO DE FALDA", "CAUDA"
        };
        y = drawLabelAndLineBlockAligned(g2, rubros, X, y, CW, mm(7.8));

        // ===== Crinolinas
        y += mm(3);
        g2.drawString("EL VESTIDO SE PROBÓ PARA AJUSTE CON:", i(X), i(y));
        y += mm(6);
        double cx = X + mm(8), cy = y - mm(4.2);
        drawCheckbox(g2, cx,           cy, "CRINOLINA ARO");  cx += mm(46);
        drawCheckbox(g2, cx,           cy, "CRINOLINA TUL");  cx += mm(46);
        drawCheckbox(g2, cx,           cy, "AMBAS");          cx += mm(32);
        drawCheckbox(g2, cx,           cy, "SIN CRINOLINA");

        // ===== Otras especificaciones (imprimir texto + líneas)
        y += mm(8);
        g2.setFont(fLbl);
        String lblOtras = "OTRAS ESPECIFICACIONES ESPECIALES:";
        g2.drawString(lblOtras, i(X), i(y));
        y += mm(3);

        // Medimos con fLbl porque así se dibuja el label
        double startOtras = X + g2.getFontMetrics().stringWidth(lblOtras) + mm(5);

        // Texto que llega desde el panel (obsequios + observaciones)
        String otras = nz(d.otrasEspecificaciones).replaceAll("\\s+", " ").trim();

        // 1ra línea empieza después del label; líneas 2..4 desde el margen X (se ve más pro)
        y = drawOtrasEspecificaciones(g2, otras, X, startOtras, y, X + CW, mm(6.0), 4);


        // ===== Medidas + confirmación
        y += mm(6);
        String ley = "CONFIRMO QUE MIS MEDIDAS CORPORALES AL DÍA DE HOY";
        g2.drawString(ley, i(X), i(y));
        double baseX = X + g2.getFontMetrics().stringWidth(ley) + mm(4);

        // Líneas + “DE / DEL / SON:” exactamente en los puntos pedidos
        double[] centers = drawDateTripletWithCenters(g2, baseX, y + mm(2.0)); // ↓ líneas 2 mm más abajo
        drawCenteredSmall(g2, "DE",  centers[0], y + mm(0.9));                 // ↑ textos 1 mm más arriba
        drawCenteredSmall(g2, "DEL", centers[1], y + mm(0.9));
        g2.drawString("SON:", i(centers[2] + mm(2.5)), i(y));

        y += mm(8.5);
        y = drawMeasuresList(g2, X + mm(4), y);

        // ===== Texto legal
        y += mm(2);
        g2.setFont(fChico);
        drawCentered(g2,
            "CONFIRMO QUE TODAS LAS ESPECIFICACIONES AQUÍ DESCRITAS SON TODO LO QUE AUTORIZO SE LE HAGA A MI VESTIDO DE NOVIA.",
            X, y, CW);

        // ===== Firma a la IZQUIERDA + Fecha/Lugar a la DERECHA (para que no se corte)
        y += mm(8);

        // Línea y etiqueta de firma (lado izquierdo – zona amarilla)
        double sigX = X + mm(3);
        double sigW = mm(92);
        g2.drawLine(i(sigX), i(y), i(sigX + sigW), i(y));
        g2.setFont(fLbl);
        drawCentered(g2, "NOMBRE Y FIRMA DE LA NOVIA", sigX, y + mm(3.5), sigW);

        // “QUERÉTARO, QRO. A ___ DE ___ DEL ___” (lado derecho)
        String pie = "QUERÉTARO, QRO. A";
        double pieX = sigX + sigW + mm(3);           // arranca después de la firma
        double pieY = y + mm(5.5);                    // texto en línea con la etiqueta de firma
        g2.drawString(pie, i(pieX), i(pieY));

        double tripletBase = y + mm(5.3);            // líneas un poco por debajo del texto
        double afterPie = pieX + g2.getFontMetrics().stringWidth(pie) + mm(4);

        // >>> ÚNICO CAMBIO: usar líneas MÁS CORTAS con la sobrecarga (seg=14mm, gap=8mm)
        double[] c2 = drawDateTripletWithCenters(g2, afterPie, tripletBase, mm(14), mm(8));
        drawCenteredSmall(g2, "DE",  c2[0], pieY);
        drawCenteredSmall(g2, "DEL", c2[1], pieY);

        return PAGE_EXISTS;
    }

    // ===== helpers de dibujo =====

    private void drawCentered(Graphics2D g2, String s, double x, double y, double w){
        FontMetrics fm = g2.getFontMetrics();
        int tx = i(x + (w - fm.stringWidth(s)) / 2.0);
        int ty = i(y + fm.getAscent());
        g2.drawString(s, tx, ty);
    }

    private void drawCenteredSmall(Graphics2D g2, String s, double cx, double y){
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, i(cx - fm.stringWidth(s)/2.0), i(y));
    }

    private double drawLabeledBox(Graphics2D g2, String label, String value, double x, double y, double w, double h){
        g2.setFont(fLbl);
        g2.drawString(label, i(x), i(y));
        double top = y + mm(2);
        g2.drawRect(i(x), i(top), i(w), i(h));
        if (value != null && !value.isBlank()){
            g2.setFont(fCampo);
            FontMetrics fm = g2.getFontMetrics();
            int tx = i(x + mm(3));
            int ty = i(top + (h + fm.getAscent())/2.0 - 1);
            g2.drawString(value, tx, ty);
        }
        return top + h;
    }

    private double drawLabelAndLineBlockAligned(Graphics2D g2, String[] labels, double x, double y, double w, double hLine){
        g2.setFont(fLbl);
        double yRow = y;
        for (String lab : labels){
            g2.drawString(lab, i(x), i(yRow));
            double start = x + g2.getFontMetrics().stringWidth(lab) + mm(5);
            double ly = yRow + mm(2.0);
            g2.drawLine(i(start), i(ly), i(x+w), i(ly));
            yRow += hLine;
        }
        return yRow;
    }

    private void drawCheckbox(Graphics2D g2, double x, double y, String text){
        int box = i(mm(4.2));
        g2.drawRect(i(x), i(y), box, box);
        g2.setFont(fLbl);
        g2.drawString(text, i(x + box + mm(2)), i(y + box - mm(0.8)));
    }

    private double drawEmptyLinesFrom(Graphics2D g2, double xStart, double y, double xEnd, double sep, int n){
        for (int k=0;k<n;k++){
            double yy = y + mm(2.0);
            g2.drawLine(i(xStart), i(yy), i(xEnd), i(yy));
            y += sep;
        }
        return y;
    }

    private double drawMeasuresList(Graphics2D g2, double x, double y){
        g2.setFont(fLbl);
        String[] m = {
            "CONTORNO BUSTO:", "CONTORNO CINTURA:", "CONTORNO CADERA:",
            "CONTORNO BRAZO:", "LARGO DE BRAZO:", "CONTORNO DE MANGA:",
            "LARGO DE VESTIDO CON ZAPATOS:"
        };
        double wLine = mm(42);
        double sep   = mm(6.0);
        for (String lab : m){
            g2.drawString(lab, i(x), i(y));
            double lx = x + mm(55);
            double ly = y + mm(2.0);
            g2.drawLine(i(lx), i(ly), i(lx + wLine), i(ly));
            y += sep;
        }
        return y;
    }

    /** Dibuja ___   ___   ___ y devuelve los centros de los huecos: [c1, c2, c3] */
    private double[] drawDateTripletWithCenters(Graphics2D g2, double x, double yBase){
        double seg = mm(18);
        double gap = mm(10);

        g2.drawLine(i(x), i(yBase), i(x+seg), i(yBase));
        double c1 = x + seg + gap/2.0;
        x += seg + gap;

        g2.drawLine(i(x), i(yBase), i(x+seg), i(yBase));
        double c2 = x + seg + gap/2.0;
        x += seg + gap;

        g2.drawLine(i(x), i(yBase), i(x+seg), i(yBase));
        double c3 = x + seg;

        return new double[]{c1, c2, c3};
    }

    // >>> NUEVA SOBRECARGA (para líneas más cortas en el pie)
    private double[] drawDateTripletWithCenters(Graphics2D g2, double x, double yBase, double seg, double gap){
        g2.drawLine(i(x), i(yBase), i(x+seg), i(yBase));
        double c1 = x + seg + gap/2.0;
        x += seg + gap;

        g2.drawLine(i(x), i(yBase), i(x+seg), i(yBase));
        double c2 = x + seg + gap/2.0;
        x += seg + gap;

        g2.drawLine(i(x), i(yBase), i(x+seg), i(yBase));
        double c3 = x + seg;

        return new double[]{c1, c2, c3};
    }
    private double drawOtrasEspecificaciones(Graphics2D g2, String text,
                                        double xLeft, double xStartFirst,
                                        double y, double xEnd,
                                        double sep, int maxLines) {

    // Primero: preparar líneas envueltas (wrap)
    g2.setFont(fCampo);
    FontMetrics fm = g2.getFontMetrics();

    int w1 = i(xEnd - xStartFirst); // ancho disponible en línea 1 (después del label)
    int wN = i(xEnd - xLeft);       // ancho disponible en líneas 2..N (desde margen)
    List<String> lines = wrapTextVariable(text, fm, w1, wN, maxLines);

    // Dibujar líneas + texto (texto un poquito arriba para que no lo tache la raya)
    double underlineOffset = mm(2.0);
    double lift = mm(0.8);

    for (int k = 0; k < maxLines; k++) {
        double xStart = (k == 0) ? xStartFirst : xLeft;

        double underlineY = y + underlineOffset;
        g2.drawLine(i(xStart), i(underlineY), i(xEnd), i(underlineY));

        if (k < lines.size()) {
            String s = lines.get(k);
            if (s != null && !s.isBlank()) {
                g2.setFont(fCampo);
                g2.drawString(s, i(xStart), i(underlineY - lift));
            }
        }

        y += sep;
    }
    return y;
}

private List<String> wrapTextVariable(String text, FontMetrics fm, int widthFirst, int widthNext, int maxLines) {
    List<String> out = new ArrayList<>();
    if (text == null) return out;

    String t = text.trim();
    if (t.isEmpty()) return out;

    String[] words = t.split("\\s+");
    StringBuilder line = new StringBuilder();

    int iWord = 0;
    for (int lineIdx = 0; lineIdx < maxLines && iWord < words.length; lineIdx++) {
        int maxW = (lineIdx == 0) ? widthFirst : widthNext;

        line.setLength(0);
        while (iWord < words.length) {
            String w = words[iWord];
            String cand = line.length() == 0 ? w : line + " " + w;

            if (fm.stringWidth(cand) <= maxW) {
                line.setLength(0);
                line.append(cand);
                iWord++;
            } else {
                break;
            }
        }

        // Si no cupo ni una palabra (palabra larguísima), truncamos esa palabra
        if (line.length() == 0 && iWord < words.length) {
            String trunc = truncateToWidth(words[iWord], fm, maxW);
            out.add(trunc);
            iWord++;
        } else {
            out.add(line.toString());
        }
    }

    // Si sobró texto, pon "…" al final de la última línea
    if (iWord < words.length && !out.isEmpty()) {
        int last = out.size() - 1;
        String s = out.get(last);
        String withEllipsis = addEllipsisToFit(s, fm, (last == 0) ? widthFirst : widthNext);
        out.set(last, withEllipsis);
    }

    return out;
}

private String truncateToWidth(String s, FontMetrics fm, int maxW) {
    if (s == null) return "";
    if (fm.stringWidth(s) <= maxW) return s;

    String ell = "…";
    int n = s.length();
    while (n > 0 && fm.stringWidth(s.substring(0, n) + ell) > maxW) n--;
    return (n <= 0) ? ell : s.substring(0, n) + ell;
}

private String addEllipsisToFit(String s, FontMetrics fm, int maxW) {
    if (s == null) return "";
    String ell = "…";
    if (fm.stringWidth(s + ell) <= maxW) return s + ell;

    int n = s.length();
    while (n > 0 && fm.stringWidth(s.substring(0, n) + ell) > maxW) n--;
    return (n <= 0) ? ell : s.substring(0, n) + ell;
}

}
