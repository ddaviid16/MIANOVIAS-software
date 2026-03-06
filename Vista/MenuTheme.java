package Vista;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;

final class MenuTheme {

    private static final Color BG_APP = new Color(242, 245, 249);
    private static final Color BG_PANEL = new Color(232, 237, 243);
    private static final Color BTN_BG = new Color(58, 74, 97);
    private static final Color BTN_BG_HOVER = new Color(73, 90, 113);
    private static final Color BTN_FG = new Color(245, 247, 250);
    private static final Color BORDER = new Color(174, 184, 198);

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
                BorderFactory.createLineBorder(new Color(47, 62, 82), 1, true),
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

    static Icon textIcon(String symbol) {
        int w = 28;
        int h = 28;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(235, 240, 246));
        g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g.setColor(new Color(44, 58, 78));
        g.setFont(new Font("Dialog", Font.PLAIN, 16));
        FontMetrics fm = g.getFontMetrics();
        int x = (w - fm.stringWidth(symbol)) / 2;
        int y = ((h - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(symbol, Math.max(x, 2), y);
        g.dispose();
        return new ImageIcon(img);
    }
}
