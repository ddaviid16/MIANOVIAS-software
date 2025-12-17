package Vista;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class OperacionesPanel extends JPanel {

    private final Consumer<String> navigate; // recibe el id de la tarjeta

    
    private static final String CARD_VENTA_CONTADO   = "Venta de contado";
    private static final String CARD_VENTA_CREDITO   = "Venta de crédito";
    private static final String CARD_ABONO           = "Abono";
    private static final String CARD_DEVOLUCION      = "Devoluciones";
    private static final String CARD_CANCELACION     = "Cancelación de notas";
    private static final String CARD_CAMBIO_FECHA    = "Cambio de fecha de evento";
    private static final String CARD_HOJA_ENTREGA    = "Hoja de entrega";
    private static final String CARD_AGREGAR_OBSEQUIOS = "Agregar obsequios a nota";
    

    public OperacionesPanel(Consumer<String> navigate) {
        this.navigate = navigate;
        setLayout(new BorderLayout());


        JPanel center = new JPanel(new GridLayout(2, 3, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton btContado     = botonGrande("Venta de Contado");
        JButton btCredito     = botonGrande("Venta de Crédito");
        JButton btAbono       = botonGrande("Abono");
        JButton btDevolucion  = botonGrande("Devoluciones");
        JButton btCancelacion = botonGrande("Cancelación de Notas");
        JButton btCambioFecha = botonGrande("Cambio de Fecha de Evento");
        JButton btHojaEntrega = botonGrande("Hoja de entrega");
        JButton btAgregarObsequios = botonGrande("Agregar obsequios a nota");

        // Navegación (cambia por tus constantes si son distintas)
        btContado.addActionListener(_e    -> navigate.accept(CARD_VENTA_CONTADO));
        btCredito.addActionListener(_e    -> navigate.accept(CARD_VENTA_CREDITO));
        btAbono.addActionListener(_e      -> navigate.accept(CARD_ABONO));
        btDevolucion.addActionListener(_e -> navigate.accept(CARD_DEVOLUCION));
        btCancelacion.addActionListener(_e-> navigate.accept(CARD_CANCELACION));
        btCambioFecha.addActionListener(_e-> navigate.accept(CARD_CAMBIO_FECHA));
        btHojaEntrega.addActionListener(_e-> navigate.accept(CARD_HOJA_ENTREGA));
        btAgregarObsequios.addActionListener(_e -> navigate.accept(CARD_AGREGAR_OBSEQUIOS));


        center.add(btContado);
        center.add(btCredito);
        center.add(btAbono);
        center.add(btDevolucion);
        center.add(btCancelacion);
        center.add(btCambioFecha);
        center.add(btHojaEntrega);
        center.add(btAgregarObsequios);

        add(center, BorderLayout.CENTER);
    }

    /** Helper local para crear botones “grandes” con estilo consistente. */
    private JButton botonGrande(String texto) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 100)); // ajusta tamaño a gusto
        return b;
    }
}
