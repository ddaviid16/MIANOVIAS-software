package Vista;

import Controlador.CambioFechaEventoService;
import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Panel unificado (sin tabs ni título interno). */
public class CambioFechaEventoPanel extends JPanel {

    // ======= formato fecha dd-MM-aaaa
    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    // ======= Campos (antes “pestaña Cliente/global”)
    private final JTextField txtTelCli = new JTextField();
    private final JTextField txtNombre = ro();
    private final JTextField txtFechaActual = ro();          // evento (ya existe)
    private final JTextField txtFechaEntregaAct = ro();      // NUEVO: entrega
    private final JTextField txtNuevaFecha = new JTextField();
    private final JTextField txtNuevaEntrega = new JTextField();

    private final JCheckBox chkActualizarNotas = new JCheckBox("¿Actualizar fecha en notas del cliente?");
    private final JPanel panelNotas = new JPanel(new BorderLayout());
    private final DefaultTableModel modelNotas = new DefaultTableModel(
        new String[]{"Sel","Nota","Folio","Tipo","Fecha de evento en nota", "Fecha de entrega en nota",
                    "Nuevo evento (dd-MM-aaaa)","Nueva entrega (dd-MM-aaaa)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c==0 || c==6 || c==7; }
        @Override public Class<?> getColumnClass(int c) { return c==0 ? Boolean.class : Object.class; }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    private final JButton btAplicarGlobal = new JButton("Aplicar cambio");

    // ======= (se mantiene el código de “Por nota” por si lo vuelves a usar)
    private final JTextField txtTelNota = new JTextField();
    private final JTextField txtNumNota = new JTextField();
    private final JTextField txtNuevaFechaNota = new JTextField();
    private final JButton btAplicarNota = new JButton("Actualizar fecha de la nota");

    public CambioFechaEventoPanel() {
        // Antes: BorderLayout + título + tabs
        // Ahora: el contenido directo, sin título interno ni tabs.
        setLayout(new BorderLayout());
        add(buildTabCliente(), BorderLayout.CENTER);
    }

    // ============================ “Cliente (global)” (sin tab) ============================
    private JPanel buildTabCliente() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;

        addCell(p,c,0,y,new JLabel("Teléfono cliente:"),1,false);
        addCell(p,c,1,y,txtTelCli,2,true);
        JButton btCargar = new JButton("Cargar");
        btCargar.addActionListener(_e -> cargarCliente());
        addCell(p,c,3,y,btCargar,1,false);
        y++;

        addCell(p,c,0,y,new JLabel("Nombre:"),1,false);
        addCell(p,c,1,y,txtNombre,3,true);
        y++;

        addCell(p,c,0,y,new JLabel("Fecha actual evento:"),1,false);
        addCell(p,c,1,y,txtFechaActual,3,true);
        y++;

        addCell(p,c,0,y,new JLabel("Fecha actual entrega:"),1,false);
        addCell(p,c,1,y,txtFechaEntregaAct,3,true);
        y++;

        addCell(p,c,0,y,new JLabel("Nueva fecha (dd-MM-aaaa):"),1,false);
        addCell(p,c,1,y,txtNuevaFecha,3,true);
        y++;

        addCell(p,c,0,y,new JLabel("Nueva entrega (dd-MM-aaaa):"),1,false);
        addCell(p,c,1,y,txtNuevaEntrega,3,true); 
        y++;

        chkActualizarNotas.setSelected(false);
        chkActualizarNotas.addActionListener(_e -> {
            boolean on = chkActualizarNotas.isSelected();
            panelNotas.setVisible(on);
            if (on) cargarNotasDiferentes();
        });
        addCell(p,c,0,y,chkActualizarNotas,4,false);
        y++;

        // panel de tabla de notas
        panelNotas.setBorder(BorderFactory.createTitledBorder("Notas con fecha distinta a la del cliente"));
        tbNotas.setRowHeight(24);
        panelNotas.add(new JScrollPane(tbNotas), BorderLayout.CENTER);
        panelNotas.setVisible(false);
        c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        addCell(p,c,0,y,panelNotas,4,true);
        y++;
        c.weighty = 0; c.fill = GridBagConstraints.HORIZONTAL;

        btAplicarGlobal.addActionListener(_e -> aplicarCambioGlobal());
        addCell(p,c,0,y,btAplicarGlobal,4,false);

        // auto hooks
        txtTelCli.addActionListener(_e -> cargarCliente());
        ((AbstractDocument)txtTelCli.getDocument()).addDocumentListener(
                (VentaContadoPanel.SimpleDocListener) this::cargarCliente
        );

        return p;
    }

    private void cargarCliente() {
        String tel = txtTelCli.getText().trim();
        if (tel.isEmpty()) { limpiarCliente(); return; }
        try {
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) { return; }
            txtNombre.setText(nullToEmpty(cr.getNombreCompleto()));
            txtFechaActual.setText(cr.getFechaEvento()==null ? "" : cr.getFechaEvento().format(MX));
            txtFechaEntregaAct.setText(cr.getFechaEntrega()==null ? "" : cr.getFechaEntrega().format(MX));


            if (chkActualizarNotas.isSelected()) cargarNotasDiferentes();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando cliente: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarCliente() {
        txtNombre.setText("");
        txtFechaActual.setText("");
        modelNotas.setRowCount(0);
    }

    private void cargarNotasDiferentes() {
        modelNotas.setRowCount(0);
        String tel = txtTelCli.getText().trim();
        LocalDate fCliente = parse(txtFechaActual.getText().trim());
        if (tel.isEmpty() || fCliente == null) return;

        try {
            NotasDAO dao = new NotasDAO();
            List<NotasDAO.NotaFechaRow> lista = dao.notasConFechaEventoDistinta(tel, fCliente);
            for (NotasDAO.NotaFechaRow r : lista) {
                modelNotas.addRow(new Object[] {
                    Boolean.FALSE,
                    r.numero,
                    emptyIfNull(r.folio),
                    emptyIfNull(r.tipo),
                    (r.fechaEntrega == null ? "— mixta —" : r.fechaEntrega.format(MX)),
                    "",   // Nueva evento (dd-MM-aaaa)
                    ""   // Nueva entrega (dd-MM-aaaa)
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error cargando notas: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aplicarCambioGlobal() {
    String tel = txtTelCli.getText().trim();
    if (tel.isEmpty()) { JOptionPane.showMessageDialog(this,"Captura el teléfono del cliente."); return; }

    LocalDate nuevaGlobEvento  = parse(txtNuevaFecha.getText().trim());   // puede ser null
    LocalDate nuevaGlobEntrega = parse(txtNuevaEntrega.getText().trim()); // puede ser null

    java.util.List<CambioFechaEventoService.CambioNota> cambios = new java.util.ArrayList<>();
    if (chkActualizarNotas.isSelected()) {
        for (int r=0; r<modelNotas.getRowCount(); r++) {
            if (Boolean.TRUE.equals(modelNotas.getValueAt(r,0))) {
                int numero = Integer.parseInt(String.valueOf(modelNotas.getValueAt(r,1)));

                String sEv = emptyIfNull(modelNotas.getValueAt(r,6)).trim();
                String sEn = emptyIfNull(modelNotas.getValueAt(r,7)).trim();

                LocalDate ev = sEv.isBlank() ? nuevaGlobEvento  : parse(sEv);
                LocalDate en = sEn.isBlank() ? nuevaGlobEntrega : parse(sEn);

                if (ev == null && en == null) {
                    JOptionPane.showMessageDialog(this,
                        "Indica al menos una fecha (evento o entrega) para la nota " + numero +
                        " o captura la(s) global(es).");
                    return;
                }

                CambioFechaEventoService.CambioNota cn = new CambioFechaEventoService.CambioNota();
                cn.numeroNota = numero;
                cn.nuevaFechaEvento  = ev;
                cn.nuevaFechaEntrega = en;
                cambios.add(cn);
            }
        }
    }

    if (nuevaGlobEvento == null && nuevaGlobEntrega == null && cambios.isEmpty()) {
        JOptionPane.showMessageDialog(this, "No hay cambios por aplicar.");
        return;
    }

    Object[] ops = {"SI","NO"};
    int r = JOptionPane.showOptionDialog(this,
            "¿Aplicar cambio?\n" +
            "Fecha evento cliente: "  + (nuevaGlobEvento  == null ? "(sin cambio)" : form(nuevaGlobEvento))  + "\n" +
            "Fecha entrega cliente: " + (nuevaGlobEntrega == null ? "(sin cambio)" : form(nuevaGlobEntrega)) +
            (cambios.isEmpty() ? "" : ("\nNotas a actualizar: " + cambios.size())),
            "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
    if (r != JOptionPane.YES_OPTION) return;

    try {
        CambioFechaEventoService svc = new CambioFechaEventoService();
        if (nuevaGlobEvento != null || nuevaGlobEntrega != null) {
            svc.cambiarClienteYNotas(tel, nuevaGlobEvento, nuevaGlobEntrega, cambios);
        } else {
            svc.actualizarSoloNotas(cambios);
        }
        JOptionPane.showMessageDialog(this, "Cambios aplicados correctamente.");
        txtNuevaFecha.setText(""); txtNuevaEntrega.setText("");
        cargarCliente();
        if (chkActualizarNotas.isSelected()) cargarNotasDiferentes();
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Error al aplicar cambios: "+ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}



    // ============================ “Por nota” (se conserva, no se usa) ============================
    private JPanel buildTabNota() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;

        addCell(p,c,0,y,new JLabel("Teléfono cliente:"),1,false);
        addCell(p,c,1,y,txtTelNota,2,true); y++;

        addCell(p,c,0,y,new JLabel("Número de nota:"),1,false);
        addCell(p,c,1,y,txtNumNota,2,true); y++;

        addCell(p,c,0,y,new JLabel("Nueva fecha (dd-MM-aaaa):"),1,false);
        addCell(p,c,1,y,txtNuevaFechaNota,2,true); y++;

        btAplicarNota.addActionListener(_e -> actualizarSoloNota());
        addCell(p,c,1,y,btAplicarNota,2,false);

        return p;
    }

    private void actualizarSoloNota() {
        String tel = txtTelNota.getText().trim();
        String sn = txtNumNota.getText().trim();
        LocalDate f = parse(txtNuevaFechaNota.getText().trim());
        if (tel.isEmpty() || sn.isEmpty() || f == null) {
            JOptionPane.showMessageDialog(this, "Captura teléfono, número de nota y fecha (dd-MM-aaaa).");
            return;
        }
        int num;
        try { num = Integer.parseInt(sn); }
        catch (Exception e) { JOptionPane.showMessageDialog(this, "Número de nota inválido."); return; }

        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Actualizar fecha de la nota " + num + " a " + form(f) + " ?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            CambioFechaEventoService.CambioNota cn = new CambioFechaEventoService.CambioNota();
            cn.numeroNota = num;
            cn.nuevaFechaEntrega = f;
            cn.nuevaFechaEvento = f;
            List<CambioFechaEventoService.CambioNota> cambios = new ArrayList<>();
            cambios.add(cn);

            new CambioFechaEventoService().actualizarSoloNotas(cambios);

            JOptionPane.showMessageDialog(this, "Fecha de nota actualizada.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============================ helpers UI ============================
    private static JTextField ro(){ JTextField t=new JTextField(); t.setEditable(false);
        Color ro = UIManager.getColor("TextField.inactiveBackground");
        if (ro==null) ro = new Color(235,235,235);
        t.setBackground(ro); return t; }

    private void addCell(JPanel p, GridBagConstraints c, int x,int y,JComponent comp,int span,boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }
    private static String nullToEmpty(String s){ return s==null? "" : s; }
    private static String emptyIfNull(Object o){ return o==null? "" : String.valueOf(o); }
    private static String form(LocalDate d){ return d==null? "" : d.format(MX); }
    private static LocalDate parse(String s){
        if (s==null || s.isBlank()) return null;
        try { return LocalDate.parse(s, MX); } catch(Throwable t){ return null; }
    }
}
