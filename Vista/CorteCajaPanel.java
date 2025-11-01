package Vista;

import Controlador.CorteCajaDAO;
import Controlador.PagoGastosDAO;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

/** Corte de caja: lee sistema (formas_pago) y permite conteo manual. */
public class CorteCajaPanel extends JPanel {


    private LocalDate selectedDate = LocalDate.now();
    private final JButton btFecha = new JButton();



    // Ingresos por operación (solo lectura)
    private final JTextField txtCN = ro();
    private final JTextField txtCR = ro();
    private final JTextField txtAB = ro();

    // Sistema (no editables)
    private final JTextField sysDebito  = ro();
    private final JTextField sysCredito = ro();
    private final JTextField sysAmex    = ro();
    private final JTextField sysTransf  = ro();
    private final JTextField sysDepo    = ro();
    private final JTextField sysEfec    = ro();

    // Manual (editables)
    private final JFormattedTextField manDebito  = moneyField();
    private final JFormattedTextField manCredito = moneyField();
    private final JFormattedTextField manAmex    = moneyField();
    private final JFormattedTextField manTransf  = moneyField();
    private final JFormattedTextField manDepo    = moneyField();
    private final JFormattedTextField manEfec    = moneyField();

    private final JTextField txtRetirosHoy  = ro();
    private final JTextField txtEfecNeto    = ro();

private static final Locale ES_MX = Locale.forLanguageTag("es-MX");

private void actualizarTextoFecha() {
    String mes = selectedDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL, ES_MX);
    mes = mes.substring(0, 1).toUpperCase(ES_MX) + mes.substring(1);
    btFecha.setText(String.format("%02d %s %d", selectedDate.getDayOfMonth(), mes, selectedDate.getYear()));
}

    public CorteCajaPanel() {
    Utilidades.EventBus.addCorteCajaListener(() -> {
    SwingUtilities.invokeLater(() -> {
        LocalDate f = obtenerFechaSeleccionada();
        cargarSistemaYRetiros(f);
        cargarIngresosOperacion(f);
    });
});



        setLayout(new BorderLayout());

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y=0;
        addCell(grid,c,0,y,new JLabel("Fecha:"),1,false);

        actualizarTextoFecha();
        btFecha.addActionListener(_e -> {
            DayPopup dp = new DayPopup(selectedDate, d -> {
                selectedDate = d;
                actualizarTextoFecha();
                cargarDatosPorFecha();
            });
            dp.show(btFecha, 0, btFecha.getHeight());
        });

        // tamaño razonable del botón
        btFecha.setPreferredSize(new Dimension(160, 26));

        addCell(grid,c,1,y,btFecha,1,false);
        y++;



        // Botón de refresco manual (auto-refresh siempre activo, sin checkbox)
        JButton btRefrescar = new JButton("Refrescar");
        btRefrescar.addActionListener(_e -> {
            cargarSistemaYRetiros(selectedDate);
            cargarIngresosOperacion(selectedDate);
        });


        addCell(grid,c,0,y,btRefrescar,1,false);
        addCell(grid,c,1,y,Box.createHorizontalStrut(1),3,true); y++;

        // ===== Ingresos por operación =====
        addCell(grid,c,0,y,new JLabel("Ingresos por tipo de operación:"),4,false); y++;

        addCell(grid,c,0,y,new JLabel("Contado:"),1,false);
        addCell(grid,c,1,y,txtCN,1,true);
        addCell(grid,c,2,y,new JLabel("Crédito:"),1,false);
        addCell(grid,c,3,y,txtCR,1,true); y++;

        addCell(grid,c,0,y,new JLabel("Abonos:"),1,false);
        addCell(grid,c,1,y,txtAB,3,true); y++;

        // Encabezados de métodos
        addCell(grid,c,0,y,new JLabel("Método"),1,false);
        addCell(grid,c,1,y,new JLabel("Sistema"),1,false);
        addCell(grid,c,2,y,new JLabel("Conteo manual"),2,false); y++;

        // Filas por método
        fila(grid,c,y++, "Tarjeta Débito", sysDebito,  manDebito);
        fila(grid,c,y++, "Tarjeta Crédito", sysCredito, manCredito);
        fila(grid,c,y++, "American Express", sysAmex,  manAmex);
        fila(grid,c,y++, "Transferencia bancaria", sysTransf, manTransf);
        fila(grid,c,y++, "Depósito bancario", sysDepo, manDepo);
        fila(grid,c,y++, "Efectivo", sysEfec, manEfec);

        // Retiros y neto
        addCell(grid,c,0,y,new JLabel("Retiros del día:"),1,false);
        addCell(grid,c,1,y,txtRetirosHoy,3,true); y++;

        JButton btCalc = new JButton("Calcular efectivo neto");
        btCalc.addActionListener(_e -> {
            recargarRetiros();
            calcularNeto();
        });
        addCell(grid,c,1,y,btCalc,3,false); y++;

        addCell(grid,c,0,y,new JLabel("Efectivo neto:"),1,false);
        addCell(grid,c,1,y,txtEfecNeto,3,true);

        add(grid, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btGuardar = new JButton("Guardar corte");
        btGuardar.addActionListener(_e -> guardar());
        south.add(btGuardar);
        add(south, BorderLayout.SOUTH);

        // Alineación a la IZQUIERDA en todos los campos
        alinearIzquierda(
            txtCN, txtCR, txtAB,
            sysDebito, sysCredito, sysAmex, sysTransf, sysDepo, sysEfec,
            manDebito, manCredito, manAmex, manTransf, manDepo, manEfec,
            txtRetirosHoy, txtEfecNeto
        );

        // Carga inicial de datos y arranque del timer
        LocalDate f = obtenerFechaSeleccionada();
        cargarSistemaYRetiros(f);
        cargarIngresosOperacion(f);


    }
    private void exportarCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar archivo CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));
        fileChooser.setSelectedFile(new File("corte_de_caja.csv")); // Nombre predeterminado

        int result = fileChooser.showSaveDialog(null); // Mostrar el cuadro de diálogo de guardar
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            if (!filePath.endsWith(".csv")) {
                selectedFile = new File(filePath + ".csv");
            }

            try {
                // Exportar resumen del corte
                CorteCajaDAO.Corte corte = new CorteCajaDAO().leerCortePorFecha(selectedDate);
                if (corte == null) {
                    JOptionPane.showMessageDialog(this, "No hay corte guardado para esta fecha.");
                    return;
                }
                java.util.List<CorteCajaDAO.Corte> lista = java.util.Collections.singletonList(corte);
                ExportadorCSV.guardarListaCSV(lista, selectedFile.getAbsolutePath(),
                        "fecha", "tarjetaDebito", "tarjetaCredito", "americanExpress", 
                        "transferenciaBanc", "depositoBancario", "efectivo", "retiros", "efectivoNeto");

                // Exportar también ingresos por tipo de operación (CN/CR/AB)
                CorteCajaDAO.Ingresos ingresos = new CorteCajaDAO().leerIngresosPorOperacion(selectedDate);
                class IngresosWrap {
                    public LocalDate fecha = selectedDate;
                    public BigDecimal contado = ingresos.contado, credito = ingresos.credito, abonos = ingresos.abonos;
                }
                ExportadorCSV.guardarListaCSV(java.util.List.of(new IngresosWrap()), selectedFile.getAbsolutePath(),
                        "fecha", "contado", "credito", "abonos");

                JOptionPane.showMessageDialog(this, "Archivo exportado exitosamente.", "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exportando: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private LocalDate obtenerFechaSeleccionada() {
    return selectedDate;
}


    private void cargarIngresosOperacion(LocalDate fecha) {

        try {
            CorteCajaDAO.Ingresos i = new CorteCajaDAO().leerIngresosPorOperacion(fecha);
            txtCN.setText(fmt(i.contado));
            txtCR.setText(fmt(i.credito));
            txtAB.setText(fmt(i.abonos));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar ingresos por operación: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            txtCN.setText("0.00"); txtCR.setText("0.00"); txtAB.setText("0.00");
        }
    }

    private void cargarDatosPorFecha() {
    try {
        LocalDate fecha = LocalDate.parse(selectedDate.toString().trim());
        LocalDate f = obtenerFechaSeleccionada();
        cargarSistemaYRetiros(f);
        cargarIngresosOperacion(f);


        // Si existe un corte previo en BD, cargarlo en los campos manuales
        CorteCajaDAO.Corte corteGuardado = new CorteCajaDAO().leerCortePorFecha(fecha);
        if (corteGuardado != null) {
            manDebito.setText(fmt(corteGuardado.tarjetaDebito));
            manCredito.setText(fmt(corteGuardado.tarjetaCredito));
            manAmex.setText(fmt(corteGuardado.americanExpress));
            manTransf.setText(fmt(corteGuardado.transferenciaBanc));
            manDepo.setText(fmt(corteGuardado.depositoBancario));
            manEfec.setText(fmt(corteGuardado.efectivo));
            txtRetirosHoy.setText(fmt(corteGuardado.retiros));
            txtEfecNeto.setText(fmt(corteGuardado.efectivoNeto));
        } else {
            // No hay corte previo: limpiar los manuales
            manDebito.setText("");
            manCredito.setText("");
            manAmex.setText("");
            manTransf.setText("");
            manDepo.setText("");
            manEfec.setText("");
            txtEfecNeto.setText("");
        }

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Fecha inválida o error al cargar: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    private void cargarSistemaYRetiros(LocalDate fecha) {
        try {
            CorteCajaDAO.Totales t = new CorteCajaDAO().leerTotalesDia(fecha);
            sysDebito.setText(fmt(t.tarjetaDebito));
            sysCredito.setText(fmt(t.tarjetaCredito));
            sysAmex.setText(fmt(t.americanExpress));
            sysTransf.setText(fmt(t.transferenciaBanc));
            sysDepo.setText(fmt(t.depositoBancario));
            sysEfec.setText(fmt(t.efectivo));

            // Inicializa manual con lo del sistema (el operador puede corregir)
            manDebito.setText(sysDebito.getText());
            manCredito.setText(sysCredito.getText());
            manAmex.setText(sysAmex.getText());
            manTransf.setText(sysTransf.getText());
            manDepo.setText(sysDepo.getText());
            manEfec.setText("0.00");

            // Retiros hoy
            txtRetirosHoy.setText(fmt(new PagoGastosDAO().totalRetirado(fecha)));

            // cálculo inicial del neto
            calcularNeto();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar totales: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

private void calcularNeto() {
    BigDecimal efSis = parse(sysEfec);     // efectivo del sistema (formas_pago)
    BigDecimal efMan = parseMoney(manEfec); // efectivo físico contado por el usuario
    BigDecimal retiros = parse(txtRetirosHoy); // solo referencia

    if (efSis == null) efSis = BigDecimal.ZERO;
    if (efMan == null) efMan = BigDecimal.ZERO;
    if (retiros == null) retiros = BigDecimal.ZERO;

    // Efectivo disponible teórico después de retiros
    BigDecimal efDisponible = efSis.subtract(retiros);
    if (efDisponible.compareTo(BigDecimal.ZERO) < 0) efDisponible = BigDecimal.ZERO;

    // Diferencia física (manual - disponible)
    BigDecimal diferencia = efMan.subtract(efDisponible);

    // Texto de resultado
    String estado;
    int cmp = diferencia.compareTo(BigDecimal.ZERO);
    if (cmp > 0) {
        estado = "→ SOBRANTE de $" + fmt(diferencia);
    } else if (cmp < 0) {
        estado = "→ FALTANTE de $" + fmt(diferencia.abs());
    } else {
        estado = "→ SIN DIFERENCIA";
    }

    BigDecimal neto = efMan; // el efectivo físico contado, no lo restamos
    txtEfecNeto.setText(fmt(neto) + "   " + estado);
}

    private void guardar() {
        Object[] ops = {"SI","NO"};
        int r = JOptionPane.showOptionDialog(this,"¿Guardar corte de caja?",
                "Confirmación", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, ops, ops[0]);
        if (r != JOptionPane.YES_OPTION) return;

        try {
            CorteCajaDAO.Corte c = new CorteCajaDAO.Corte();
            c.fecha            = LocalDate.now();
            c.tarjetaDebito    = valueOrSystem(manDebito,  sysDebito);
            c.tarjetaCredito   = valueOrSystem(manCredito, sysCredito);
            c.americanExpress  = valueOrSystem(manAmex,    sysAmex);
            c.transferenciaBanc= valueOrSystem(manTransf,  sysTransf);
            c.depositoBancario = valueOrSystem(manDepo,    sysDepo);
            c.efectivo         = valueOrSystem(manEfec,    sysEfec);

            // leer retiros actualizados desde BD antes de calcular y guardar
            BigDecimal retirosHoy = new PagoGastosDAO().totalRetirado(LocalDate.now());
            txtRetirosHoy.setText(fmt(retirosHoy));
            c.retiros = (retirosHoy != null) ? retirosHoy : BigDecimal.ZERO;

            c.efectivoNeto     = c.efectivo.subtract(c.retiros).max(BigDecimal.ZERO);

            new CorteCajaDAO().guardar(c);
            JOptionPane.showMessageDialog(this, "Corte guardado.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    // --- Formato con separador de miles para mostrar en JTextField (solo lectura)
    private static final NumberFormat NF_DISPLAY = buildFmt();
    private static NumberFormat buildFmt() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setGroupingUsed(true);           // ← activa "1,234.56"
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf;
    }

    // ===== Helpers UI / Money =====

    private void fila(JPanel p, GridBagConstraints c, int y, String metodo,
                      JComponent sys, JComponent man) {
        addCell(p,c,0,y,new JLabel(metodo+":"),1,false);
        addCell(p,c,1,y,sys,1,true);
        addCell(p,c,2,y,man,2,true);
    }

    private static JTextField ro(){ JTextField t=new JTextField();
        t.setEditable(false);
        t.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        t.setHorizontalAlignment(SwingConstants.LEFT);
        return t;
    }
    private static JTextField ro(String s){ JTextField t=ro(); t.setText(s); return t; }

    private static void addCell(JPanel p, GridBagConstraints c, int x,int y,Component comp,int span,boolean growX){
        c.gridx=x; c.gridy=y; c.gridwidth=span; c.weightx = growX?1:0; p.add(comp,c); c.gridwidth=1;
    }

    private void recargarRetiros() {
        try {
            BigDecimal r = new PagoGastosDAO().totalRetirado(LocalDate.now());
            txtRetirosHoy.setText(fmt(r));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error cargando retiros: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Campo monetario sin flechas, alineado a la izquierda. */
    private static JFormattedTextField moneyField() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        NumberFormatter fmt = new NumberFormatter(nf);
        fmt.setValueClass(Double.class);
        fmt.setAllowsInvalid(false);
        fmt.setMinimum(0.00);
        JFormattedTextField f = new JFormattedTextField(fmt);
        f.setColumns(12);
        f.setHorizontalAlignment(SwingConstants.LEFT); // ← izquierda
        return f;
    }

    private static void alinearIzquierda(JTextField... fields){
        for (JTextField t : fields) t.setHorizontalAlignment(SwingConstants.LEFT);
    }

    private static BigDecimal parseMoney(JFormattedTextField f) {
        try {
            String s = f.getText().trim().replace(",", "");
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) { return null; }
    }
    private static BigDecimal parse(JTextField f) {
        try {
            String s = f.getText().trim().replace(",", "");
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) { return null; }
    }
    private static String fmt(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        return NF_DISPLAY.format(v);
    }
    private static BigDecimal valueOrSystem(JFormattedTextField manual, JTextField sistema) {
        BigDecimal m = parseMoney(manual);
        return (m != null ? m : parse(sistema));
    }
    /** Refresca los datos del corte solo cuando una operación termina (CN, CR, AB, DV, cancelación). */
public void refrescarDespuesDeOperacion() {
    SwingUtilities.invokeLater(() -> {
        LocalDate f = obtenerFechaSeleccionada();
        cargarSistemaYRetiros(f);
        cargarIngresosOperacion(f);

    });
}
// ========= Calendario emergente de 1 día (igual que VentasPorVendedorPanel) =========
static class DayPopup extends JPopupMenu {
    private java.time.YearMonth ym;
    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
    private final java.util.function.Consumer<java.time.LocalDate> onPick;
    private static final java.util.Locale ES_MX = java.util.Locale.forLanguageTag("es-MX");

    DayPopup(java.time.LocalDate initial, java.util.function.Consumer<java.time.LocalDate> onPick) {
        java.time.LocalDate base = initial == null ? java.time.LocalDate.now() : initial;
        this.ym = java.time.YearMonth.of(base.getYear(), base.getMonth());
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
        java.time.DayOfWeek[] order = {
            java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY
        };
        for (java.time.DayOfWeek d : order) {
            JLabel l = new JLabel(d.getDisplayName(java.time.format.TextStyle.SHORT, ES_MX), SwingConstants.CENTER);
            l.setFont(l.getFont().deriveFont(Font.PLAIN));
            dow.add(l);
        }
        add(dow, BorderLayout.CENTER);
        add(grid, BorderLayout.SOUTH);

        refresh();
    }

    private void refresh() {
        String mes = ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, ES_MX);
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
                java.time.LocalDate pick = ym.atDay(day);
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

}