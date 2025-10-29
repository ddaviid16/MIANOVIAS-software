package Vista;

import javax.swing.*;
import java.awt.*;

/**
 * Panel único para Operaciones -> Cambio de fecha de evento.
 * Integra:
 *  - Pestaña "Cliente (global)": actualiza la fecha de evento del cliente y (opcional) replica a sus notas.
 *  - Pestaña "Por nota": actualiza/limpia la fecha de evento SOLO en la nota seleccionada.
 *
 * Reutiliza los paneles ya creados:
 *  - CambioFechaEventoPanel           (global por cliente)   
 *  - CambioFechaEventoPorNotaPanel    (por nota)             
 */
public class CambioFechaEventoOperacionesPanel extends JPanel {

    private final JTabbedPane tabs = new JTabbedPane();

    private final CambioFechaEventoPanel           pnlCliente;
    private final CambioFechaEventoPorNotaPanel    pnlPorNota;

    public CambioFechaEventoOperacionesPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Título opcional
        JLabel titulo = new JLabel("Cambio de fecha de evento", SwingConstants.LEFT);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        add(titulo, BorderLayout.NORTH);

        pnlCliente = new CambioFechaEventoPanel();         // el panel existente (global/cliente)
        pnlPorNota = new CambioFechaEventoPorNotaPanel();  // panel por nota 

        tabs.addTab("Cliente (global)", pnlCliente);
        tabs.addTab("Por nota",          pnlPorNota);

        add(tabs, BorderLayout.CENTER);
    }
}
