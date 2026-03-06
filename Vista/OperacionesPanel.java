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
    private static final String CARD_CAMBIO_CODIGO_ART    = "Cambio de código de artículo";

    public OperacionesPanel(Consumer<String> navigate) {
        this.navigate = navigate;
        setLayout(new BorderLayout());
        MenuTheme.styleAppBackground(this);

        JPanel center = new JPanel(new GridLayout(2, 4, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        MenuTheme.styleMenuPanel(center);

        JButton btContado     = botonGrande("Venta de Contado", MenuTheme.textIcon("$"));
        JButton btCredito     = botonGrande("Venta de Crédito", MenuTheme.textIcon("¢"));
        JButton btAbono       = botonGrande("Abono", MenuTheme.textIcon("↘"));
        JButton btDevolucion  = botonGrande("Devoluciones", MenuTheme.textIcon("↺"));
        JButton btCancelacion = botonGrande("Cancelación de Notas", MenuTheme.textIcon("✕"));
        JButton btCambioFecha = botonGrande("Cambio de Fecha de Evento", MenuTheme.textIcon("◴"));
        JButton btHojaEntrega = botonGrande("Hoja de entrega", MenuTheme.textIcon("☑"));
        JButton btAgregarObsequios = botonGrande("Agregar obsequios a nota", MenuTheme.textIcon("✦"));
        JButton btCambioCodigoArticulo = botonGrande("Cambio de código de artículo", MenuTheme.textIcon("⟲"));
        JButton btAgregarFactura = botonGrande("Agregar datos de factura", MenuTheme.textIcon("⌘"));

        // Navegación (cambia por tus constantes si son distintas)
        btContado.addActionListener(_e    -> navigate.accept(CARD_VENTA_CONTADO));
        btCredito.addActionListener(_e    -> navigate.accept(CARD_VENTA_CREDITO));
        btAbono.addActionListener(_e      -> navigate.accept(CARD_ABONO));
        btDevolucion.addActionListener(_e -> navigate.accept(CARD_DEVOLUCION));
        btCancelacion.addActionListener(_e-> navigate.accept(CARD_CANCELACION));
        btCambioFecha.addActionListener(_e-> navigate.accept(CARD_CAMBIO_FECHA));
        btHojaEntrega.addActionListener(_e-> navigate.accept(CARD_HOJA_ENTREGA));
        btAgregarObsequios.addActionListener(_e -> navigate.accept(CARD_AGREGAR_OBSEQUIOS));
        btCambioCodigoArticulo.addActionListener(_e -> navigate.accept(CARD_CAMBIO_CODIGO_ART));
        btAgregarFactura.addActionListener(_e -> navigate.accept("Agregar datos de factura"));
        

        center.add(btContado);
        center.add(btCredito);
        center.add(btAbono);
        center.add(btDevolucion);
        center.add(btCancelacion);
        center.add(btCambioFecha);
        center.add(btHojaEntrega);
        center.add(btAgregarObsequios);
        center.add(btCambioCodigoArticulo);
        center.add(btAgregarFactura);

        add(center, BorderLayout.CENTER);
    }

    /** Helper local para crear botones “grandes” con estilo consistente. */
    private JButton botonGrande(String texto, Icon icon) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 100)); // ajusta tamaño a gusto
        MenuTheme.styleButton(b, icon);
        return b;
    }
}
