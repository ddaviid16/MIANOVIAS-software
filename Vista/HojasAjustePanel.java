// Vista/HojasAjustePanel.java
package Vista;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
// Calendario tipo grid (igual estilo al de VentasPorVendedorPanel)
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
// Utilidades para filtrar/ordenar
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;

import Controlador.NotasDAO;
import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Modelo.NotaDetalle;

public class HojasAjustePanel extends JPanel {

    private static final DateTimeFormatter MX = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");

    // === FILTRO POR RANGO DE FECHAS (usa fecha_prueba1 del cliente) ===
    private final DateField dfDesde = new DateField();
    private final DateField dfHasta = new DateField();

    // Solo informativo (nombre del cliente de la nota seleccionada)
    private final JTextField txtNombre = ro();

    private final DefaultTableModel modelNotas = new DefaultTableModel(
        new Object[]{"Nota","Cliente","Tipo","Folio","Fecha"}, 0){
        @Override public boolean isCellEditable(int r, int c){ return false; }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    private final DefaultTableModel modelArt = new DefaultTableModel(
            new Object[]{"ID","Artículo","Marca","Modelo","Talla","Color","Precio",""}, 0){
        @Override public boolean isCellEditable(int r, int c){ return c==7; }
    };
    private final JTable tbArt = new JTable(modelArt);

    // Datos sugeridos del cliente (según la nota seleccionada)
    private String nombreCliente = "";
    private LocalDate sugEvento   = null;
    private LocalDate sugAjuste1  = null; // fecha_prueba1
    private LocalDate sugAjuste2  = null; // fecha_prueba2
    private LocalDate sugEntrega  = null;
    private int notaSeleccionada = -1;
    private String otrasEspecificaciones = "";


    public HojasAjustePanel(){
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // ====== TOP: Rango de fechas + Buscar ======
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;
        addCell(top,c,0,y,new JLabel("Desde:"),1,false);
        addCell(top,c,1,y,dfDesde.panel(),1,true);
        addCell(top,c,2,y,new JLabel("Hasta:"),1,false);
        addCell(top,c,3,y,dfHasta.panel(),1,true);

        JButton btBuscar = new JButton("Buscar");
        btBuscar.addActionListener(_e -> cargarNotasPorRango());
        addCell(top,c,4,y,btBuscar,1,false); y++;

        addCell(top,c,0,y,new JLabel("Cliente (nota seleccionada):"),1,false);
        addCell(top,c,1,y,txtNombre,4,true);
        y++;

        add(top, BorderLayout.NORTH);

        // ====== CENTER: Notas arriba / Artículos abajo ======
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.45);

        tbNotas.setRowHeight(22);
        tbNotas.getSelectionModel().addListSelectionListener(_e -> {
            if (!_e.getValueIsAdjusting()) cargarArticulosYCliente();
        });
        // Ocultar la columna "Nota" (modelo col 0) en la VISTA
        tbNotas.getColumnModel().removeColumn(tbNotas.getColumnModel().getColumn(0));

        split.setTopComponent(new JScrollPane(tbNotas));

        tbArt.setRowHeight(24);
        tbArt.getColumnModel().getColumn(7).setCellRenderer(new ButtonRenderer());
        tbArt.getColumnModel().getColumn(7).setCellEditor(new ButtonEditor(new JCheckBox(), this::imprimirFila));
        split.setBottomComponent(new JScrollPane(tbArt));

        add(split, BorderLayout.CENTER);

        // Rango inicial (últimos 90 días) y primera carga
        dfHasta.set(LocalDate.now());
        dfDesde.set(LocalDate.now().minusDays(90));
        cargarNotasPorRango();
    }

    // ================== CARGA POR RANGO (usa fecha_prueba1) ==================
    private void cargarNotasPorRango() {
        modelNotas.setRowCount(0);
        modelArt.setRowCount(0);
        txtNombre.setText("");
        nombreCliente = "";
        sugEvento = sugAjuste1 = sugAjuste2 = sugEntrega = null;

        LocalDate desde = dfDesde.get();
        LocalDate hasta = dfHasta.get();
        if (desde == null || hasta == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un rango de fechas válido (Desde/Hasta).");
            return;
        }
        if (hasta.isBefore(desde)) {
            JOptionPane.showMessageDialog(this, "La fecha 'Hasta' no puede ser anterior a 'Desde'.");
            return;
        }

        // 1) Traemos notas CN/CR con su teléfono (sin usar fecha de la nota)
        final String sql =
                "SELECT numero_nota, tipo, folio, telefono " +
                "FROM Notas " +
                "WHERE (tipo='CN' OR tipo='CR') " +
                "ORDER BY numero_nota DESC";

        // 2) Cache por teléfono para no repetir consultas a clienteDAO
        Map<String, ClienteResumen> cache = new HashMap<>();

        // 3) Acumulamos filas ya filtradas por fecha_prueba1 y luego ordenamos por esa fecha desc
        class Row { int numero; String cliente; String tipo; String folio; LocalDate fecha; }
        List<Row> rows = new ArrayList<>();

        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int    numero = rs.getInt("numero_nota");
                String tipo   = safe(rs.getString("tipo"));
                String folio  = safe(rs.getString("folio"));
                String tel    = safe(rs.getString("telefono"));

                // Leemos ClienteResumen por teléfono desde cache/DAO
                ClienteResumen cr = cache.get(tel);
                if (cr == null && !tel.isEmpty()) {
                    try {
                        cr = new clienteDAO().buscarResumenPorTelefono(tel);
                    } catch (SQLException ignore) { /* si falla, no incluimos */ }
                    if (cr != null) cache.put(tel, cr);
                }

                LocalDate fPrueba1 = (cr == null) ? null : cr.getFechaPrueba1();
                if (fPrueba1 == null) continue; // si no tiene prueba1, no se considera para el filtro

                if (!fPrueba1.isBefore(desde) && !fPrueba1.isAfter(hasta)) {
                    Row r = new Row();
                    r.numero = numero; r.tipo = tipo; r.folio = folio; r.fecha = fPrueba1;
                    rows.add(r);
                    // Nombre del cliente (si no hay, cae al teléfono)
                    String nom = (cr == null ? "" : safe(cr.getNombreCompleto()));
                    if (nom.isEmpty()) nom = tel;
                    r.cliente = nom;
                }
                
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Error listando notas: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 4) Orden por fecha_prueba1 DESC y luego por número DESC
rows.sort(
    Comparator.<Row,LocalDate>comparing(r -> r.fecha,
        Comparator.nullsLast(Comparator.naturalOrder()))
    .reversed()
    .thenComparingInt(r -> r.numero).reversed()
);


// 6) Poblar la tabla
for (Row r : rows) {
    modelNotas.addRow(new Object[]{
        r.numero,
        r.cliente,
        r.tipo,
        r.folio,
        (r.fecha==null? "" : r.fecha.format(MX))
    });
}

    }

    // Al cambiar de nota: cargar cliente y artículos
    private void cargarArticulosYCliente() {
        modelArt.setRowCount(0);
        int row = tbNotas.getSelectedRow();
        if (row < 0) return;

        int numero = Integer.parseInt(String.valueOf(modelNotas.getValueAt(row,0)));
        notaSeleccionada = numero;
        otrasEspecificaciones = construirTextoOtrasEspecificaciones(numero);

        // Cargar cliente (para mostrar su nombre en el campo y tener sugerencias)
        String tel = leerTelefonoDeNota(numero);
        if (tel != null && !tel.isBlank()) {
            try {
                clienteDAO cdao = new clienteDAO();
                ClienteResumen cr = cdao.buscarResumenPorTelefono(tel);
                if (cr != null) {
                    nombreCliente = cr.getNombreCompleto()==null? "" : cr.getNombreCompleto();
                    sugEvento   = cr.getFechaEvento();
                    sugAjuste1  = cr.getFechaPrueba1();  // <- esta es la que usamos para filtrar/mostrar
                    sugAjuste2  = cr.getFechaPrueba2();
                    sugEntrega  = cr.getFechaEntrega();
                    txtNombre.setText(nombreCliente);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,"Error cargando cliente: "+ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // Cargar artículos de la nota
        try {
            NotasDAO dao = new NotasDAO();
            List<NotaDetalle> dets = dao.listarDetalleDeNota(numero);
            for (NotaDetalle d : dets) {
                modelArt.addRow(new Object[]{
                        d.getId(),
                        n(d.getArticulo()),
                        n(d.getMarca()),
                        n(d.getModelo()),
                        n(d.getTalla()),
                        n(d.getColor()),
                        d.getPrecio()==null? "" : String.format("%,.2f", d.getPrecio()),
                        "Imprimir"
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,"Error cargando artículos: "+ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Obtiene el teléfono del cliente de una nota. */
    private String leerTelefonoDeNota(int numeroNota) {
        final String sql = "SELECT telefono FROM Notas WHERE numero_nota = ?";
        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return safe(rs.getString(1));
            }
        } catch (Exception ignore) { }
        return null;
    }

    /** Click en “Imprimir” */
    private void imprimirFila(int row) {
        if (row < 0) return;

        Object idObj = modelArt.getValueAt(row, 0);
        if (idObj == null) {
            JOptionPane.showMessageDialog(this, "Este renglón no es imprimible (no pertenece a Nota_Detalle).");
            return;
        }
        int idDetalle;
        try {
            idDetalle = Integer.parseInt(String.valueOf(idObj));
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "ID de renglón inválido.");
            return;
        }

        String modelo = String.valueOf(modelArt.getValueAt(row, 3));
        String talla  = String.valueOf(modelArt.getValueAt(row, 4));
        String color  = String.valueOf(modelArt.getValueAt(row, 5));

        LocalDate fEv = null, fEnt = null;
        try {
            NotasDAO.FechasRenglon fr = new NotasDAO().leerFechasDeRenglon(idDetalle);
            if (fr != null) { fEv = fr.fechaEvento; fEnt = fr.fechaEntrega; }
        } catch (SQLException ignore){}

        EditarHojaAjusteDialog ed = new EditarHojaAjusteDialog(
                SwingUtilities.windowForComponent(this),
                "Hoja de ajuste");

        ed.setDatosIniciales(
                nombreCliente, modelo, talla, color,
                fEv,
                sugAjuste1,
                sugAjuste2,
                fEnt
        );
        ed.setOtrasEspecificaciones(otrasEspecificaciones);
        ed.setVisible(true);
    }

    // ===== helpers UI =====
    private static String n(Object o){ return o==null? "" : String.valueOf(o); }
    private static String safe(String s){ return s==null? "" : s.trim(); }
    private static JTextField ro(){ JTextField t=new JTextField(); t.setEditable(false);
        t.setBackground(UIManager.getColor("TextField.inactiveBackground")); return t; }
    private static void addCell(JPanel p, GridBagConstraints c,int x,int y,JComponent comp,int span,boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx=growX?1:0; p.add(comp,c); c.gridwidth=1; }

    // ==== Render/editor con botón en tabla ====
    private static class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer(){ setOpaque(true); setText("Imprimir"); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column){ return this; }
    }
    private static class ButtonEditor extends DefaultCellEditor {
        private final JButton button = new JButton("Imprimir");
        private int row = -1; private final IntConsumer onClick;
        public ButtonEditor(JCheckBox cb, IntConsumer onClick){
            super(cb); this.onClick = onClick;
            button.addActionListener(_e -> { fireEditingStopped(); onClick.accept(row); });
        }
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column){
            this.row = row; return button;
        }
        @Override public Object getCellEditorValue(){ return "Imprimir"; }
    }

    // ========= Campo de fecha con botón calendario (abre DayPopup) =========
    private static class DateField {
        private final JTextField txt = new JTextField(10);
        private final JButton bt = new JButton("📅");

        DateField() {
            txt.setHorizontalAlignment(SwingConstants.CENTER);
            ((AbstractDocument) txt.getDocument()).setDocumentFilter(new DateMaskFilter(10));
            bt.addActionListener(_e -> openPicker());
        }
        JPanel panel() {
            JPanel p = new JPanel(new BorderLayout(4,0));
            p.add(txt, BorderLayout.CENTER);
            p.add(bt, BorderLayout.EAST);
            return p;
        }
        void set(LocalDate d){ txt.setText(d==null? "" : d.format(MX)); }
        LocalDate get(){
            String s = txt.getText().trim();
            if (s.isEmpty()) return null;
            try { return LocalDate.parse(s, MX); } catch (Exception e){ return null; }
        }
        private void openPicker() {
            LocalDate base = get()==null? LocalDate.now() : get();
            DayPopup dp = new DayPopup(base, this::set);
            dp.show(bt, 0, bt.getHeight());
        }
    }

    private static class DateMaskFilter extends DocumentFilter {
        private final int maxLen;
        DateMaskFilter(int maxLen){ this.maxLen = maxLen; }

        @Override public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            if (string == null) return;
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + string + cur.substring(offset);
            if (isValid(next)) super.insertString(fb, offset, string, attr);
        }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                throws javax.swing.text.BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text==null?"":text) + cur.substring(offset+length);
            if (isValid(next)) super.replace(fb, offset, length, text, attrs);
        }
        private boolean isValid(String s){
            return s.length() <= maxLen && s.matches("[0-9\\-]*");
        }
    }

    // ========= Calendario emergente de 1 mes/días =========
    static class DayPopup extends JPopupMenu {
        private YearMonth ym;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        private final Consumer<LocalDate> onPick;

        DayPopup(LocalDate initial, Consumer<LocalDate> onPick) {
            LocalDate base = initial == null ? LocalDate.now() : initial;
            this.ym = YearMonth.of(base.getYear(), base.getMonth());
            this.onPick = onPick;

            setLayout(new BorderLayout(6, 6));
            JPanel header = new JPanel(new BorderLayout());

            JButton prev = new JButton("◀");
            JButton next = new JButton("▶");
            prev.addActionListener(_e -> { ym = ym.minusMonths(1); refresh(); });
            next.addActionListener(_e -> { ym = ym.plusMonths(1);  refresh(); });

            title.setFont(title.getFont().deriveFont(Font.BOLD));
            header.add(prev, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);

            add(header, BorderLayout.NORTH);

            JPanel dow = new JPanel(new GridLayout(1, 7, 4, 4));
            DayOfWeek[] order = {
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
            };
            for (DayOfWeek d : order) {
                JLabel l = new JLabel(d.getDisplayName(TextStyle.SHORT, ES_MX), SwingConstants.CENTER);
                l.setFont(l.getFont().deriveFont(Font.PLAIN));
                dow.add(l);
            }
            add(dow, BorderLayout.CENTER);
            add(grid, BorderLayout.SOUTH);

            refresh();
        }

        private void refresh() {
            String mes = ym.getMonth().getDisplayName(TextStyle.FULL, ES_MX);
            mes = mes.substring(0, 1).toUpperCase(ES_MX) + mes.substring(1);
            title.setText(mes + " " + ym.getYear());

            grid.removeAll();

            int firstDow = ym.atDay(1).getDayOfWeek().getValue(); // 1..7 (lun..dom)
            int blanks = (firstDow == 7) ? 6 : firstDow - 1;
            for (int i = 0; i < blanks; i++) grid.add(new JLabel(""));

            int len = ym.lengthOfMonth();
            for (int d = 1; d <= len; d++) {
                int day = d;
                JButton b = new JButton(String.valueOf(day));
                b.addActionListener(_e -> {
                    LocalDate pick = ym.atDay(day);
                    if (onPick != null) onPick.accept(pick);
                    setVisible(false);
                });
                grid.add(b);
            }

            pack();
            revalidate();
            repaint();
        }
    }
    private String leerObservacionesDeNota(int numeroNota) {
    try (Connection cn = Conexion.Conecta.getConnection()) {

        String obs = null;

        // 1) Tabla nueva
        final String sql1 = "SELECT observaciones FROM notas_observaciones WHERE numero_nota = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql1)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) obs = rs.getString(1);
            }
        }

        // 2) Fallback a Notas.observaciones
        if (obs == null || obs.trim().isEmpty()) {
            final String sql2 = "SELECT observaciones FROM Notas WHERE numero_nota = ?";
            try (PreparedStatement ps = cn.prepareStatement(sql2)) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) obs = rs.getString(1);
                }
            }
        }

        if (obs == null) return "";
        return obs.replaceAll("\\s+", " ").trim();

    } catch (Exception e) {
        return "";
    }
}

private String resumenObsequiosDeNota(int numeroNota) {
    final String sql =
            "SELECT obsequio1, obsequio1_cod, " +
            "       obsequio2, obsequio2_cod, " +
            "       obsequio3, obsequio3_cod, " +
            "       obsequio4, obsequio4_cod, " +
            "       obsequio5, obsequio5_cod " +
            "FROM obsequios WHERE numero_nota = ?";

    List<String> parts = new ArrayList<>();

    try (Connection cn = Conexion.Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, numeroNota);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return "";

            addObsequio(parts, rs.getString("obsequio1_cod"), rs.getString("obsequio1"));
            addObsequio(parts, rs.getString("obsequio2_cod"), rs.getString("obsequio2"));
            addObsequio(parts, rs.getString("obsequio3_cod"), rs.getString("obsequio3"));
            addObsequio(parts, rs.getString("obsequio4_cod"), rs.getString("obsequio4"));
            addObsequio(parts, rs.getString("obsequio5_cod"), rs.getString("obsequio5"));
        }

    } catch (Exception e) {
        return "";
    }

    return String.join("  |  ", parts).trim();
}

private void addObsequio(List<String> out, String cod, String desc) {
    String c = safe(cod);
    String d = safe(desc);
    if (c.isEmpty() && d.isEmpty()) return;

    if (!c.isEmpty() && !d.isEmpty()) out.add(c + " - " + d);
    else if (!d.isEmpty()) out.add(d);
    else out.add(c);
}

private String construirTextoOtrasEspecificaciones(int numeroNota) {
    String ob = resumenObsequiosDeNota(numeroNota);
    String obs = leerObservacionesDeNota(numeroNota);

    StringBuilder sb = new StringBuilder();
    if (!ob.isBlank()) sb.append("Obsequios: ").append(ob);
    if (!obs.isBlank()) {
        if (sb.length() > 0) sb.append("  |  ");
        sb.append("Observaciones: ").append(obs);
    }
    return sb.toString().trim();
}

}
