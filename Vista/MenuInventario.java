package Vista;

import javax.swing.*;
import java.awt.*;

public class MenuInventario extends JDialog {

    public MenuInventario(Frame owner) {
        super(owner, "Inventario", true);                // modal
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(380, 220);
        setLocationRelativeTo(owner);

        JPanel content = new JPanel(new GridLayout(3,1,10,10));
        content.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JButton btArticulos = new JButton("Inventario de artículos");
        JButton btObsequios = new JButton("Inventario de obsequios");
        JButton btCerrar    = new JButton("Cerrar");

        content.add(btArticulos);
        content.add(btObsequios);
        content.add(btCerrar);
        setContentPane(content);

        // Abre el inventario que YA tienes (no se toca)
        btArticulos.addActionListener(_e -> {
            dispose();                       // cierra el submenú (opcional)
            new InventarioPanel().setVisible(true);
        });

        // Abre el inventario de obsequios (tu nueva pantalla/listado)
        btObsequios.addActionListener(_e -> {
            dispose();
            new ObsequiosInvPanel().setVisible(true);
        });

        btCerrar.addActionListener(_e -> dispose());
    }
}
