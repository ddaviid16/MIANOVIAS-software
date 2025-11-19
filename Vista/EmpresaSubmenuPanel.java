package Vista;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class EmpresaSubmenuPanel extends JPanel {

    private final Consumer<String> navigate; // recibe el id de la tarjeta

    // Asegúrate de que estos IDs coincidan con los registrados en tu CardLayout
    private static final String CARD_EMPRESA_INFO     = "Información de la empresa";
    private static final String CARD_EMPRESA_FOLIOS   = "Asignación de Folios";
    private static final String CARD_EMPRESA_ASESORES = "Empleados";
    private static final String CARD_EMPRESA_CONDICIONES = "Condiciones de venta";

    public EmpresaSubmenuPanel(Consumer<String> navigate) {
        this.navigate = navigate;
        setLayout(new BorderLayout());

        // Cuadrícula limpia: 2 filas × 2 columnas
        JPanel center = new JPanel(new GridLayout(2, 2, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 60, 20, 60));

        JButton btInfo   = botonGrande("Información de la empresa");
        JButton btFolios = botonGrande("Asignación de Folios");
        JButton btAses   = botonGrande("Empleados");
        JButton btCond   = botonGrande("Condiciones de venta");

        btInfo.addActionListener(_e   -> navigate.accept(CARD_EMPRESA_INFO));
        btFolios.addActionListener(_e -> navigate.accept(CARD_EMPRESA_FOLIOS));
        btAses.addActionListener(_e   -> navigate.accept(CARD_EMPRESA_ASESORES));
        btCond.addActionListener(_e   -> navigate.accept(CARD_EMPRESA_CONDICIONES));

        center.add(btInfo);
        center.add(btFolios);
        center.add(btAses);
        center.add(btCond);

        add(center, BorderLayout.CENTER);
    }

    /** Botón “grande” consistente con Operaciones. */
    private JButton botonGrande(String texto) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 100));
        return b;
    }
}
