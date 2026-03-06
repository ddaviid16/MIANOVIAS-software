package Vista;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

final class MenuTheme {

    private static final Color BG_APP = new Color(30, 36, 45);
    private static final Color BG_PANEL = new Color(40, 47, 58);
    private static final Color BTN_BG = new Color(57, 66, 79);
    private static final Color BTN_BG_HOVER = new Color(69, 79, 94);
    private static final Color BTN_FG = new Color(232, 236, 242);
    private static final Color BORDER = new Color(84, 94, 110);

    private MenuTheme() {}

    static void styleAppBackground(JComponent c) {
        c.setBackground(BG_APP);
        c.setOpaque(true);
    }

    static void styleMenuPanel(JComponent c) {
        c.setBackground(BG_PANEL);
        c.setOpaque(true);
        Border b = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        );
        c.setBorder(BorderFactory.createCompoundBorder(c.getBorder(), b));
    }

    static void styleButton(JButton b, Icon icon) {
        b.setBackground(BTN_BG);
        b.setForeground(BTN_FG);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(92, 104, 122), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        b.setIcon(icon);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setIconTextGap(8);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setContentAreaFilled(true);
        b.setOpaque(true);
        b.setRolloverEnabled(true);
        b.addChangeListener(_e -> {
            ButtonModel m = b.getModel();
            if (m.isRollover()) {
                b.setBackground(BTN_BG_HOVER);
            } else {
                b.setBackground(BTN_BG);
            }
        });
    }

    static Icon textIcon(String key) {
        int w = 28;
        int h = 28;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(215, 221, 230, 30));
        g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));

        g.setColor(new Color(225, 231, 238));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (key) {
            case "reports": drawBars(g); break;
            case "company": drawBuilding(g); break;
            case "clients": drawUsers(g); break;
            case "inventory": drawBox(g); break;
            case "operations": drawGear(g); break;
            case "register": drawDocument(g); break;
            case "edit": drawPencil(g); break;
            case "agenda": drawClock(g); break;
            case "history": drawList(g); break;
            case "info": drawInfo(g); break;
            case "folios": drawHash(g); break;
            case "employees": drawUsers(g); break;
            case "conditions": drawCheck(g); break;
            case "cash": drawMoney(g); break;
            case "credit": drawCard(g); break;
            case "refund": drawReturn(g); break;
            case "cancel": drawClose(g); break;
            case "calendar": drawCalendar(g); break;
            case "delivery": drawClipboard(g); break;
            case "gift": drawGift(g); break;
            case "code": drawTag(g); break;
            case "invoice": drawReceipt(g); break;
            case "print": drawPrinter(g); break;
            case "detail": drawUserCard(g); break;
            case "request": drawClipboard(g); break;
            case "adjust": drawWrench(g); break;
            case "sales": drawTrend(g); break;
            case "month": drawCalendar(g); break;
            case "modista": drawScissors(g); break;
            default: drawDot(g); break;
        }

        g.dispose();
        return new ImageIcon(img);
    }

    private static void drawBars(Graphics2D g) { g.drawLine(7, 20, 7, 12); g.drawLine(14, 20, 14, 8); g.drawLine(21, 20, 21, 14); }
    private static void drawBuilding(Graphics2D g) { g.drawRect(8, 9, 12, 11); g.drawLine(7, 9, 14, 4); g.drawLine(14, 4, 21, 9); }
    private static void drawUsers(Graphics2D g) { g.drawOval(8, 8, 5, 5); g.drawOval(15, 8, 5, 5); g.drawLine(7, 19, 13, 19); g.drawLine(14, 19, 21, 19); }
    private static void drawBox(Graphics2D g) { g.drawRect(7, 10, 14, 10); g.drawLine(7, 10, 14, 6); g.drawLine(14, 6, 21, 10); }
    private static void drawGear(Graphics2D g) { g.drawOval(10, 10, 8, 8); g.drawLine(14, 6, 14, 9); g.drawLine(14, 19, 14, 22); g.drawLine(6, 14, 9, 14); g.drawLine(19, 14, 22, 14); }
    private static void drawDocument(Graphics2D g) { g.drawRect(8, 6, 12, 16); g.drawLine(11, 11, 17, 11); g.drawLine(11, 15, 17, 15); }
    private static void drawPencil(Graphics2D g) { g.drawLine(8, 20, 19, 9); g.drawLine(17, 8, 20, 11); g.drawLine(7, 21, 10, 20); }
    private static void drawClock(Graphics2D g) { g.drawOval(7, 7, 14, 14); g.drawLine(14, 14, 14, 10); g.drawLine(14, 14, 18, 16); }
    private static void drawList(Graphics2D g) { g.drawLine(8, 9, 20, 9); g.drawLine(8, 14, 20, 14); g.drawLine(8, 19, 20, 19); }
    private static void drawInfo(Graphics2D g) { g.drawOval(12, 7, 4, 4); g.drawLine(14, 13, 14, 20); }
    private static void drawHash(Graphics2D g) { g.drawLine(10, 7, 8, 21); g.drawLine(18, 7, 16, 21); g.drawLine(7, 11, 21, 11); g.drawLine(6, 17, 20, 17); }
    private static void drawCheck(Graphics2D g) { g.drawLine(8, 15, 12, 19); g.drawLine(12, 19, 20, 9); }
    private static void drawMoney(Graphics2D g) { g.drawRect(6, 9, 16, 10); g.drawOval(12, 12, 4, 4); }
    private static void drawCard(Graphics2D g) { g.drawRect(6, 9, 16, 10); g.drawLine(6, 12, 22, 12); }
    private static void drawReturn(Graphics2D g) { g.drawArc(8, 8, 12, 12, 40, 260); g.drawLine(9, 9, 7, 14); g.drawLine(7, 14, 12, 14); }
    private static void drawClose(Graphics2D g) { g.drawLine(9, 9, 19, 19); g.drawLine(19, 9, 9, 19); }
    private static void drawCalendar(Graphics2D g) { g.drawRect(7, 8, 14, 13); g.drawLine(7, 12, 21, 12); g.drawLine(10, 6, 10, 10); g.drawLine(18, 6, 18, 10); }
    private static void drawClipboard(Graphics2D g) { g.drawRect(8, 7, 12, 15); g.drawRect(11, 5, 6, 3); }
    private static void drawGift(Graphics2D g) { g.drawRect(7, 11, 14, 10); g.drawLine(14, 11, 14, 21); g.drawLine(7, 15, 21, 15); }
    private static void drawTag(Graphics2D g) { Path2D p = new Path2D.Float(); p.moveTo(8, 10); p.lineTo(16, 10); p.lineTo(20, 14); p.lineTo(16, 18); p.lineTo(8, 18); p.closePath(); g.draw(p); g.drawOval(10, 12, 2, 2); }
    private static void drawReceipt(Graphics2D g) { g.drawRect(9, 6, 10, 16); g.drawLine(11, 10, 17, 10); g.drawLine(11, 14, 17, 14); }
    private static void drawPrinter(Graphics2D g) { g.drawRect(8, 11, 12, 8); g.drawRect(10, 6, 8, 5); }
    private static void drawUserCard(Graphics2D g) { g.drawRect(6, 8, 16, 12); g.drawOval(8, 11, 4, 4); g.drawLine(14, 12, 20, 12); g.drawLine(14, 16, 20, 16); }
    private static void drawWrench(Graphics2D g) { g.drawLine(9, 19, 19, 9); g.drawOval(7, 17, 4, 4); g.drawArc(16, 6, 6, 6, 50, 220); }
    private static void drawTrend(Graphics2D g) { g.drawLine(7, 20, 7, 9); g.drawLine(7, 20, 21, 20); g.drawLine(9, 17, 13, 13); g.drawLine(13, 13, 17, 15); g.drawLine(17, 15, 21, 10); }
    private static void drawScissors(Graphics2D g) { g.drawOval(8, 15, 4, 4); g.drawOval(14, 15, 4, 4); g.drawLine(10, 15, 19, 8); g.drawLine(16, 15, 19, 12); }
    private static void drawDot(Graphics2D g) { g.fillOval(12, 12, 4, 4); }
}
