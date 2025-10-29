package Vista;

import Controlador.NotasDAO;
import Controlador.CancelacionService;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.Nota;
import Modelo.NotaDetalle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.List;

public class CancelarNotaPanel extends JPanel {

    private final JTextField txtTel   = new JTextField();
    private final JTextField txtNom   = ro();
    private final JTextField txtUlt   = ro();
    private final JComboBox<Nota> cbNotas = new JComboBox<>();
    private final JTextArea txtMotivo = new JTextArea(3, 40);

    private final DefaultTableModel model;
    private final JTable tb;

    public CancelarNotaPanel() {
        setLayout(new BorderLayout(10,10));

        // ===== top
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int y=0;

        addCell(top,c,0,y,new JLabel("Teléfono:"),1,false);
        addCell(top,c,1,y,txtTel,1,true);
        addCell(top,c,2,y,new JLabel("Nombre:"),1,false);
        addCell(top,c,3,y,txtNom,1,true); y++;

        addCell(top,c,2,y,new JLabel("Nota a cancelar:"),1,false);
        cbNotas.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean isSelected,boolean cellHasFocus){
                super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                if (value instanceof Nota n) {
                    setText((n.getFolio()==null?"s/folio":n.getFolio()) +
                            "  ["+n.getTipo()+"]  Total: " + fmt(n.getTotal()) + "  Saldo: " + fmt(n.getSaldo()));
                } else if (value==null) setText("— Selecciona —");
                return this;
            }
        });
        cbNotas.addActionListener(_e -> cargarDetalle());
        addCell(top,c,3,y,cbNotas,1,true); y++;

        add(top, BorderLayout.NORTH);

        // ===== tabla
        String[] cols = {"Código","Artículo","Marca","Modelo","Talla","Color","Precio","%Desc","Subtotal"};
        model = new DefaultTableModel(cols,0){ @Override public boolean isCellEditable(int r,int c){ return false; } };
        tb = new JTable(model);
        tb.setRowHeight(24);
        tb.setAutoCreateRowSorter(true);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        tb.getColumnModel().getColumn(6).setCellRenderer(right);
        tb.getColumnModel().getColumn(7).setCellRenderer(right);
        tb.getColumnModel().getColumn(8).setCellRenderer(right);
        add(new JScrollPane(tb), BorderLayout.CENTER);

        // ===== bottom
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0,10,10,10));
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(6,6,6,6);
        d.fill = GridBagConstraints.HORIZONTAL;
        d.weightx = 1;
        int r=0;

        addCell(bottom,d,0,r,new JLabel("Motivo:"),1,false);
        txtMotivo.setLineWrap(true); txtMotivo.setWrapStyleWord(true);
        addCell(bottom,d,1,r,new JScrollPane(txtMotivo),2,true);

        JButton bt = new JButton("Cancelar nota");
        bt.addActionListener(this::cancelarNota);
        addCell(bottom,d,3,r,bt,1,false);

        add(bottom, BorderLayout.SOUTH);

        // hooks
        txtTel.addActionListener(_e -> cargarClienteYNotas());
        ((AbstractDocument) txtTel.getDocument()).addDocumentListener((SimpleDocListener) this::cargarClienteYNotas);
    }

    // ===== UI actions

    private void cargarClienteYNotas() {
        String tel = txtTel.getText().trim();
        if (tel.isEmpty()) { limpiar(); return; }
        try {
            // cliente
            clienteDAO cdao = new clienteDAO();
            ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
            if (cr == null) {
                txtNom.setText("— no registrado —");
                txtUlt.setText("");
                cbNotas.removeAllItems();
                return;
            }
            txtNom.setText(cr.getNombreCompleto()==null?"":cr.getNombreCompleto());

            // última nota
            NotasDAO ndao = new NotasDAO();

            // notas CN/CR activas
            cbNotas.removeAllItems();
            List<Nota> lista = ndao.listarNotasClienteParaDevolucion(tel); // ya filtra CN/CR y status='A'
            if (lista.isEmpty()) cbNotas.addItem(null);
            else for (Nota n: lista) cbNotas.addItem(n);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error consultando: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDetalle() {
        model.setRowCount(0);
        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) return;
        try {
            NotasDAO ndao = new NotasDAO();
            List<NotaDetalle> det = ndao.listarDetalleDeNota(sel.getNumeroNota());
            for (NotaDetalle d : det) {
                model.addRow(new Object[]{
                        d.getCodigoArticulo(), d.getArticulo(), d.getMarca(), d.getModelo(),
                        d.getTalla(), d.getColor(), fmt(d.getPrecio()), fmt(d.getDescuento()), fmt(d.getSubtotal())
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar el detalle: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelarNota(ActionEvent ev) {
        Nota sel = (Nota) cbNotas.getSelectedItem();
        if (sel == null) { JOptionPane.showMessageDialog(this, "Selecciona una nota."); return; }

        // 1) Confirmación clásica
        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,
                "¿Cancelar la nota "+sel.getNumeroNota()+" ("+(sel.getFolio()==null?"s/folio":sel.getFolio())+")?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        // 2) Resumen y “¿Es la correcta?”
        String resumen = construirResumen(sel);
        r = JOptionPane.showOptionDialog(this,
                "¿Esta es la nota correcta?\n\n" + resumen,
                "Verificación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        // 3) Guardar
        try {
            CancelacionService svc = new CancelacionService();
            // Si tienes asesor logueado, pásalo; si no, manda null:
            CancelacionService.ResultadoCancelacion res =
                    svc.cancelarNota(sel.getNumeroNota(), null, txtMotivo.getText());

            JOptionPane.showMessageDialog(this, "Nota cancelada.\nFolio: "+
                    (res.folio==null? "s/folio" : res.folio) + "  ["+res.tipo+"]");

            // refrescar UI
            cargarDetalle();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al cancelar: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===== helpers

    private String construirResumen(Nota n) {
        StringBuilder sb = new StringBuilder();
        sb.append("Folio: ").append(n.getFolio()==null?"s/folio":n.getFolio())
          .append("   Tipo: ").append(n.getTipo())
          .append("\nTotal: ").append(fmt(n.getTotal()))
          .append("   Saldo: ").append(fmt(n.getSaldo()))
          .append("\n\nArtículos:\n");

        for (int i=0;i<model.getRowCount();i++){
            sb.append("• ").append(model.getValueAt(i,0)).append("  ")
              .append(model.getValueAt(i,1)).append("  ")
              .append(model.getValueAt(i,3)).append("  ")
              .append(model.getValueAt(i,4)).append("  ")
              .append(model.getValueAt(i,5)).append("  ")
              .append(" Subtotal: ").append(model.getValueAt(i,8))
              .append("\n");
        }
        return sb.toString();
    }

    private void limpiar(){
        txtNom.setText("— no registrado —");
        txtUlt.setText("");
        cbNotas.removeAllItems();
        model.setRowCount(0);
        txtMotivo.setText("");
    }

    private JTextField ro(){ JTextField t=new JTextField(); t.setEditable(false); t.setBackground(new Color(235,235,235)); return t; }
    private void addCell(JPanel p, GridBagConstraints c, int x,int y,JComponent comp,int span,boolean growX){ c.gridx=x;c.gridy=y;c.gridwidth=span;c.weightx=growX?1:0;p.add(comp,c);c.gridwidth=1; }
    private String fmt(Double v){ return v==null? "0.00" : String.format("%.2f", v); }

    @FunctionalInterface
    private interface SimpleDocListener extends javax.swing.event.DocumentListener {
        void on();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e){ on(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e){ on(); }
    }
}
