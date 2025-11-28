package Vista;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ClientesSubmenuPanel extends JPanel {

    private final Consumer<String> navigate;

    // IDs de tarjetas reales dentro del CardLayout
    public static final String CARD_CLIENTES_REGISTRO = "Registro de Clientes";
    public static final String CARD_CLIENTES_EDITAR   = "Editar información de Cliente";
    public static final String CARD_CLIENTES_CITAS    = "Agenda y Registro de Citas";
    public static final String CARD_CLIENTES_HIST     = "Registrar historial de cliente";

    public ClientesSubmenuPanel(Consumer<String> navigate) {
        this.navigate = navigate;
        setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridLayout(2, 2, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 60, 20, 60));

        JButton btRegistro  = botonGrande("Registro de clientes");
        JButton btEditar    = botonGrande("Editar información de Cliente");
        JButton btCitas     = botonGrande("Agenda y Registro de Citas");
        JButton btHistorial = botonGrande("Registrar historial de cliente");

        btRegistro.addActionListener(_e  -> navigate.accept(CARD_CLIENTES_REGISTRO));
        btEditar.addActionListener(_e    -> navigate.accept(CARD_CLIENTES_EDITAR));
        btCitas.addActionListener(_e     -> navigate.accept(CARD_CLIENTES_CITAS));
        btHistorial.addActionListener(_e -> navigate.accept(CARD_CLIENTES_HIST));

        center.add(btRegistro);
        center.add(btEditar);
        center.add(btCitas);
        center.add(btHistorial);

        add(center, BorderLayout.CENTER);
    }

    private JButton botonGrande(String texto) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 100));
        return b;
    }
}
