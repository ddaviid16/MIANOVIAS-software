package Vista;

import Impresion.AjusteImprimible;
import Impresion.VentanaPrevisualizacion;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EditarHojaAjusteDialog extends JDialog {

    private final JTextField txtNombre  = new JTextField();
    private final JTextField txtModelo  = new JTextField();
    private final JTextField txtTalla   = new JTextField();
    private final JTextField txtColor   = new JTextField();
    private final JTextField txtEvento  = new JTextField();
    private final JTextField txtAjuste1 = new JTextField();
    private final JTextField txtAjuste2 = new JTextField();
    private final JTextField txtEntrega = new JTextField();
    private final JTextArea txtOtras = new JTextArea(4, 40);


    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    public EditarHojaAjusteDialog(Window owner, String titulo){
            super(owner, titulo, ModalityType.APPLICATION_MODAL);

            setLayout(new BorderLayout(10,10));
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(true);

            // Tamaño más cómodo
            setMinimumSize(new Dimension(900, 450));
            setSize(900, 450);
            setLocationRelativeTo(owner);

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6,6,6,6);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            int y=0;

        addCell(form,c,0,y,new JLabel("Nombre de la novia:"),1,false);
        addCell(form,c,1,y,txtNombre,3,true); y++;

        addCell(form,c,0,y,new JLabel("Modelo:"),1,false);
        addCell(form,c,1,y,txtModelo,1,true);
        addCell(form,c,2,y,new JLabel("Talla:"),1,false);
        addCell(form,c,3,y,txtTalla,1,true); y++;

        addCell(form,c,0,y,new JLabel("Color:"),1,false);
        addCell(form,c,1,y,txtColor,1,true);
        addCell(form,c,2,y,new JLabel("Fecha evento (dd-MM-aaaa):"),1,false);
        addCell(form,c,3,y,txtEvento,1,true); y++;

        addCell(form,c,0,y,new JLabel("Fecha ajuste 1 (dd-MM-aaaa):"),1,false);
        addCell(form,c,1,y,txtAjuste1,1,true);
        addCell(form,c,2,y,new JLabel("Fecha ajuste 2 (dd-MM-aaaa):"),1,false);
        addCell(form,c,3,y,txtAjuste2,1,true); y++;

        addCell(form,c,0,y,new JLabel("Fecha entrega (dd-MM-aaaa):"),1,false);
        addCell(form,c,1,y,txtEntrega,1,true); y++;
    txtOtras.setLineWrap(true);
    txtOtras.setWrapStyleWord(true);
    txtOtras.setRows(6);       // más alto por default
    txtOtras.setColumns(60);   // más ancho por default

    JLabel lblOtras = new JLabel("Otras especificaciones especiales:");
    JScrollPane spOtras = new JScrollPane(txtOtras);
    spOtras.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    spOtras.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    // Quita esto: spOtras.setPreferredSize(new Dimension(10, 80));

    // >>> ESTA FILA ES LA CLAVE: fill BOTH + weighty 1
    c.gridx = 0; c.gridy = y; c.gridwidth = 1;
    c.weightx = 0; c.weighty = 0;
    c.fill = GridBagConstraints.HORIZONTAL;
    form.add(lblOtras, c);

    c.gridx = 1; c.gridy = y; c.gridwidth = 3;
    c.weightx = 1; c.weighty = 1;              // <-- para que tenga altura
    c.fill = GridBagConstraints.BOTH;          // <-- para que crezca
    form.add(spOtras, c);

    // reset para filas siguientes
    c.gridwidth = 1;
    c.weighty = 0;
    c.fill = GridBagConstraints.HORIZONTAL;
    y++;

    add(form, BorderLayout.CENTER);

    JButton btImprimir = new JButton("Previsualizar / Imprimir");
    btImprimir.addActionListener(_e -> imprimir());
    JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    south.add(btImprimir);
    add(south, BorderLayout.SOUTH);
    }

    /** Prefill desde la venta/cliente. */
    public void setDatosIniciales(String nombre, String modelo, String talla, String color,
                                  LocalDate fEvento, LocalDate fAjuste1,
                                  LocalDate fAjuste2, LocalDate fEntrega) {
        txtNombre.setText(nz(nombre));
        txtModelo.setText(nz(modelo));
        txtTalla.setText(nz(talla));
        txtColor.setText(nz(color));
        txtEvento.setText(fEvento==null? "" : fEvento.format(MX));
        txtAjuste1.setText(fAjuste1==null? "" : fAjuste1.format(MX));
        txtAjuste2.setText(fAjuste2==null? "" : fAjuste2.format(MX));
        txtEntrega.setText(fEntrega==null? "" : fEntrega.format(MX));
    }
    public void setOtrasEspecificaciones(String texto) {
        txtOtras.setText(nz(texto));
    }

    private void imprimir() {
        AjusteImprimible.Datos d = new AjusteImprimible.Datos();
        d.nombreNovia   = txtNombre.getText().trim();
        d.modeloVestido = txtModelo.getText().trim();
        d.talla         = txtTalla.getText().trim();
        d.color         = txtColor.getText().trim();
        d.otrasEspecificaciones = txtOtras.getText().trim();


        String sEv = txtEvento.getText().trim();
        String sA1 = txtAjuste1.getText().trim();
        String sA2 = txtAjuste2.getText().trim();
        String sEn = txtEntrega.getText().trim();

        d.fechaEvento  = parse(sEv);
        d.fechaAjuste1 = parse(sA1);
        d.fechaAjuste2 = parse(sA2);
        d.fechaEntrega = parse(sEn);

        // Validaciones
        List<String> errs = new ArrayList<>();
        if (!sEv.isBlank() && d.fechaEvento == null)  errs.add("Fecha de evento: formato inválido (usa dd-MM-aaaa).");
        if (!sA1.isBlank() && d.fechaAjuste1 == null) errs.add("Fecha ajuste 1: formato inválido (usa dd-MM-aaaa).");
        if (!sA2.isBlank() && d.fechaAjuste2 == null) errs.add("Fecha ajuste 2: formato inválido (usa dd-MM-aaaa).");
        if (!sEn.isBlank() && d.fechaEntrega == null) errs.add("Fecha de entrega: formato inválido (usa dd-MM-aaaa).");
        if (d.fechaEntrega != null && d.fechaEvento != null && d.fechaEntrega.isAfter(d.fechaEvento))
            errs.add("La FECHA DE ENTREGA no puede ser mayor a la FECHA DE EVENTO.");
        if (d.fechaAjuste1 != null && d.fechaAjuste2 != null && d.fechaAjuste1.isAfter(d.fechaAjuste2))
            errs.add("La FECHA DE AJUSTE 1 no puede ser mayor a la FECHA DE AJUSTE 2.");
        if (d.fechaAjuste2 != null && d.fechaEntrega != null && d.fechaAjuste2.isAfter(d.fechaEntrega))
            errs.add("La FECHA DE AJUSTE 2 no puede ser mayor a la FECHA DE ENTREGA.");
        if (d.fechaAjuste2 != null && d.fechaAjuste1 == null)
            errs.add("Si capturas FECHA DE AJUSTE 2, también debes capturar FECHA DE AJUSTE 1.");

        if (!errs.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", errs), "Corrige las fechas", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // cerrar editor para no ver dos ventanas
        Window owner = SwingUtilities.windowForComponent(this);
        dispose();

        // abrir vista previa (con botón Imprimir)
        VentanaPrevisualizacion vp =
                new VentanaPrevisualizacion(owner, "Hoja de ajuste", new AjusteImprimible(d));
        vp.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        vp.setVisible(true);
    }

    private LocalDate parse(String s){
        if (s==null || s.isBlank()) return null;
        try { return LocalDate.parse(s, MX); } catch (Exception e) { return null; }
    }
    private static void addCell(JPanel p, GridBagConstraints c, int x,int y,JComponent comp,int span,boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx=growX?1:0; p.add(comp,c); c.gridwidth=1;
    }
    private static String nz(String s){ return s==null? "" : s; }
}
