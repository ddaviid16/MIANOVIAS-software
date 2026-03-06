package Vista;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Submenú de Reportes (mismo estilo que OperacionesPanel). */
public class ReportesPanel extends JPanel {

    private final Consumer<String> navigate;

    // IDs de tarjetas (úsalos en menuPrincipal)
    public static final String CARD_REP_GASTOS     = "Pago de gastos";
    public static final String CARD_REP_CORTE      = "Corte de caja";
    public static final String CARD_REP_REIMPR     = "Re-imprimir nota";
    public static final String CARD_REP_DETCLI     = "Detalle de cliente";
    public static final String CARD_REP_ENTREGAS   = "Entregas de vestidos";
    public static final String CARD_REP_PEDIR      = "Artículos a pedir";
    public static final String CARD_REP_AJUSTES    = "Hojas de ajustes";
    public static final String CARD_REP_OBSEQ      = "Reporte de obsequios";
    public static final String CARD_REP_VENTVEND   = "Ventas por vendedor";
    public static final String CARD_REP_NOTAS_MES = "Notas por mes";
    public static final String CARD_REP_VENTAS     = "Reporte de ventas";
    public static final String CARD_REP_MODISTAS   = "Reporte de artículos modistas";

    public ReportesPanel(Consumer<String> navigate) {
        this.navigate = navigate;
        setLayout(new BorderLayout());
        MenuTheme.styleAppBackground(this);

        JPanel center = new JPanel(new GridLayout(3, 3, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        MenuTheme.styleMenuPanel(center);

        center.add(tile("Pago de Gastos",       CARD_REP_GASTOS, MenuTheme.textIcon("cash")));
        center.add(tile("Corte de Caja",        CARD_REP_CORTE, MenuTheme.textIcon("reports")));
        center.add(tile("Re-Imprimir Nota",     CARD_REP_REIMPR, MenuTheme.textIcon("print")));
        center.add(tile("Detalle de Cliente",   CARD_REP_DETCLI, MenuTheme.textIcon("detail")));
        center.add(tile("Entregas de vestidos", CARD_REP_ENTREGAS, MenuTheme.textIcon("delivery")));
        center.add(tile("Artículos a pedir",    CARD_REP_PEDIR, MenuTheme.textIcon("request")));
        center.add(tile("Hojas de ajustes",     CARD_REP_AJUSTES, MenuTheme.textIcon("adjust")));
        center.add(tile("Reporte de obsequios", CARD_REP_OBSEQ, MenuTheme.textIcon("gift")));
        center.add(tile("Ventas por vendedor",  CARD_REP_VENTVEND, MenuTheme.textIcon("sales")));
        center.add(tile("Notas por mes",       CARD_REP_NOTAS_MES, MenuTheme.textIcon("month")));
        center.add(tile("Reporte de ventas", CARD_REP_VENTAS, MenuTheme.textIcon("sales")));
        center.add(tile("Reporte de artículos modistas", CARD_REP_MODISTAS, MenuTheme.textIcon("modista")));
        add(center, BorderLayout.CENTER);
    }

    private JButton tile(String texto, String cardId, Icon icon) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 100));
        MenuTheme.styleButton(b, icon);
        b.addActionListener(_e -> { if (navigate != null) navigate.accept(cardId); });
        return b;
    }
}
