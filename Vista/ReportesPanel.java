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

    public ReportesPanel(Consumer<String> navigate) {
        this.navigate = navigate;
        setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridLayout(3, 3, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        center.add(tile("Pago de Gastos",       CARD_REP_GASTOS));
        center.add(tile("Corte de Caja",        CARD_REP_CORTE));
        center.add(tile("Re-Imprimir Nota",     CARD_REP_REIMPR));
        center.add(tile("Detalle de Cliente",   CARD_REP_DETCLI));
        center.add(tile("Entregas de vestidos", CARD_REP_ENTREGAS));
        center.add(tile("Artículos a pedir",    CARD_REP_PEDIR));
        center.add(tile("Hojas de ajustes",     CARD_REP_AJUSTES));
        center.add(tile("Reporte de obsequios", CARD_REP_OBSEQ));
        center.add(tile("Ventas por vendedor",  CARD_REP_VENTVEND));

        add(center, BorderLayout.CENTER);
    }

    private JButton tile(String texto, String cardId) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 100));
        b.addActionListener(_e -> { if (navigate != null) navigate.accept(cardId); });
        return b;
    }
}
