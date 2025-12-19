package Vista;

import Controlador.clienteDAO;
import Modelo.ClienteResumen;
import Utilidades.SeguridadUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.TableModelEvent;

/**
 * Agenda diaria de pruebas / entregas.
 *
 * Horarios fijos de 10:00 a 17:00, 3 citas por hora.
 * Muestra:
 *   Hora, Nombre novia, Concepto, Asesor/Modista, Fecha evento, Teléfono, Observación (editable).
 *
 * Si haces doble click en un renglón abre un diálogo con RegistroCitasPanel
 * para registrar o editar una cita; al cerrar, la agenda se recarga.
 */
public class AgendaPanel extends JPanel {

    private static final LocalTime FIRST_HOUR = LocalTime.of(10, 0);
    private static final LocalTime LAST_HOUR  = LocalTime.of(17, 30);
    private static final int SLOTS_PER_HOUR = 3;
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final java.util.Locale ES_MX = java.util.Locale.forLanguageTag("es-MX");

    private final clienteDAO cliDao = new clienteDAO();

    private LocalDate selectedDate = LocalDate.now();
    private final JButton btFecha = new JButton();

    private final DefaultTableModel model;
    private final JTable tb;
    private final List<Slot> slots = new ArrayList<>();

    
    public AgendaPanel() {
        setLayout(new BorderLayout(8,8));

        // ====== TOP: fecha con calendario ======
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBorder(BorderFactory.createEmptyBorder(8,8,0,8));
        top.add(new JLabel("Fecha:"));

        actualizarTextoFecha();
        btFecha.setPreferredSize(new Dimension(160, 26));
        btFecha.addActionListener(_e -> {
            DayPopup dp = new DayPopup(selectedDate, d -> {
                selectedDate = d;
                actualizarTextoFecha();
                cargarAgenda();
            });
            dp.show(btFecha, 0, btFecha.getHeight());
        });
        top.add(btFecha);

        JButton btHoy = new JButton("Hoy");
        btHoy.addActionListener(_e -> {
            selectedDate = LocalDate.now();
            actualizarTextoFecha();
            cargarAgenda();
        });
        top.add(btHoy);

        add(top, BorderLayout.NORTH);

        // ====== CENTER: tabla ======
        String[] cols = {"Hora", "Nombre novia", "Concepto",
                         "Asesor/Modista", "Fecha evento", "Teléfono", "Observación"};

        model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Solo la última columna (Observación) editable
                return column == 6;
            }
        };
        tb = new JTable(model);
        tb.setRowHeight(24);
        tb.setAutoCreateRowSorter(false);

        // Guardar cambios en la columna "Observación" directamente en BD
model.addTableModelListener(e -> {
    // Solo nos interesan actualizaciones de celdas
    if (e.getType() != TableModelEvent.UPDATE) return;

    int col = e.getColumn();
    if (col != 6) return; // solo columna "Observación"

    int firstRow = e.getFirstRow();
    int lastRow  = e.getLastRow();

    for (int row = firstRow; row <= lastRow; row++) {
        if (row < 0 || row >= slots.size()) continue;

        Slot slot = slots.get(row);
        if (slot.cita == null) continue; // slot vacío, nada que guardar

        Object val = model.getValueAt(row, 6);
        String nuevaObs = (val == null) ? null : val.toString().trim();

        // Actualizar en memoria
        slot.cita.observacion = nuevaObs;

        try {
            // Método en clienteDAO que actualiza la observación
            cliDao.actualizarObservacionCita(
                    slot.cita.telefono1,
                    slot.cita.concepto,   // "Cita 1", "Prueba 1", etc.
                    nuevaObs
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error guardando observación: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
});


        // doble click -> abrir diálogo de registro de citas
        tb.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    abrirDialogoRegistroCitas();
                }
            }
        });

        add(new JScrollPane(tb), BorderLayout.CENTER);

        // ====== SOUTH: botones ======
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btCitas = new JButton("Registrar / editar cita…");
        btCitas.addActionListener(_e -> abrirDialogoRegistroCitas());
        south.add(btCitas);

        JButton btImprimir = new JButton("Imprimir");
        btImprimir.addActionListener(_e -> imprimir());
        south.add(btImprimir);

        JButton btCSV = new JButton("Exportar CSV");
        btCSV.addActionListener(_e -> {
            if (SeguridadUI.pedirYValidarClave(this)) {
                exportarCSV();
            }
        });
        south.add(btCSV);

        add(south, BorderLayout.SOUTH);

        // primera carga
        cargarAgenda();
    }

    // ====== LÓGICA DE AGENDA ======

    private static class Slot {
        final LocalTime hora;
        clienteDAO.AgendaItem cita;   // puede ser null
        Slot(LocalTime hora) { this.hora = hora; }
    }

    private void actualizarTextoFecha() {
        String mes = selectedDate.getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, ES_MX);
        mes = mes.substring(0,1).toUpperCase(ES_MX) + mes.substring(1);
        btFecha.setText(String.format("%02d %s %d",
                selectedDate.getDayOfMonth(), mes, selectedDate.getYear()));
    }

    private void cargarAgenda() {
        model.setRowCount(0);
        slots.clear();

        // 1) Construir todos los slots vacíos
        for (LocalTime h = FIRST_HOUR; !h.isAfter(LAST_HOUR); h = h.plusMinutes(30)) {
            LocalTime hora = h;
            for (int i = 0; i < SLOTS_PER_HOUR; i++) {
                slots.add(new Slot(hora));
            }
        }

        // 2) Leer citas del día desde BD
        List<clienteDAO.AgendaItem> agenda;
        try {
            agenda = cliDao.listarAgendaDia(selectedDate);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar agenda: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            agenda = new ArrayList<>();
        }
        

        // 3) Asignar cada cita al primer slot libre con esa hora
        for (clienteDAO.AgendaItem it : agenda) {
            if (it.hora == null || it.hora.isBlank()) continue;
            LocalTime h;
            try {
                h = LocalTime.parse(it.hora.substring(0,5));
            } catch (Exception e) {
                continue; // hora rara en BD
            }
            for (Slot s : slots) {
                if (s.cita == null && s.hora.equals(h)) {
                    s.cita = it;
                    break;
                }
            }
        }

        // 4) Volcar slots a la tabla
        for (Slot s : slots) {
            String horaTxt = s.hora.format(HORA_FMT);
            if (s.cita == null) {
            model.addRow(new Object[]{
                    horaTxt, "", "", "", "", "", ""
            });
        } else {
            String fechaEv = s.cita.fechaEvento == null
                    ? ""
                    : s.cita.fechaEvento.format(DateTimeFormatter.ofPattern("dd-MM-uuuu"));
            model.addRow(new Object[]{
                    horaTxt,
                    s.cita.nombreCompleto,
                    s.cita.concepto,
                    s.cita.asesorModista,
                    fechaEv,
                    s.cita.telefono1,
                    s.cita.observacion == null ? "" : s.cita.observacion   // <---
            });
        }

        }
    }

    // ====== Diálogo de Registro de Citas ======
private void abrirDialogoRegistroCitas() {
    int viewRow = tb.getSelectedRow();
    if (viewRow < 0) {
        JOptionPane.showMessageDialog(this,
                "Selecciona un horario en la tabla.",
                "Agenda", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    // === Validar fecha / hora como ya lo tenías ===
    LocalDate hoy = LocalDate.now();
    if (viewRow >= 0 && viewRow < slots.size()) {
        Slot slot = slots.get(viewRow);
        LocalTime horaSlot = slot.hora;

        if (selectedDate.isBefore(hoy)) {
            JOptionPane.showMessageDialog(this,
                    "No puedes agendar citas en una fecha anterior a hoy.",
                    "Agenda", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (selectedDate.isEqual(hoy)) {
            LocalTime ahora = LocalTime.now().withSecond(0).withNano(0);
            if (horaSlot.isBefore(ahora)) {
                JOptionPane.showMessageDialog(this,
                        "No puedes agendar una cita en un horario anterior a la hora actual.",
                        "Agenda", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
    }

    Window owner = SwingUtilities.getWindowAncestor(this);
    JDialog dlg = new JDialog(owner, "Registro de citas", Dialog.ModalityType.APPLICATION_MODAL);
    dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    // SOLO el panel en el centro
    RegistroCitasPanel panel = new RegistroCitasPanel();
    dlg.getContentPane().add(panel, BorderLayout.CENTER);

    dlg.pack();

    int minW = 900;
    int minH = 520;
    int w = Math.max(dlg.getWidth(),  minW);
    int h = Math.max(dlg.getHeight(), minH);

    dlg.setSize(w, h);
    dlg.setMinimumSize(new Dimension(minW, minH));
    dlg.setResizable(true);
    dlg.setLocationRelativeTo(owner);

    dlg.setVisible(true);

    // Recargar agenda
    cargarAgenda();
}

private void imprimir() {
    // Guardar configuración original de la tabla
    Font oldTableFont   = tb.getFont();
    Font oldHeaderFont  = tb.getTableHeader().getFont();
    int  oldRowHeight   = tb.getRowHeight();

    try {
        // Fuente un poco más grande para que se lea en papel
        tb.setFont(oldTableFont.deriveFont(10f));              // cuerpo
        tb.getTableHeader().setFont(oldHeaderFont.deriveFont(
                Font.BOLD, 11f));                              // encabezados
        tb.setRowHeight(22);

        // Preparar trabajo de impresión
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf  = job.defaultPage();
        pf.setOrientation(PageFormat.LANDSCAPE);              // hoja horizontal

        // Reducir márgenes para aprovechar mejor el espacio
        Paper paper = pf.getPaper();
        double margin = 20; // puntos (~7 mm)
        paper.setImageableArea(
                margin,
                margin,
                paper.getWidth()  - 2 * margin,
                paper.getHeight() - 2 * margin
        );
        pf.setPaper(paper);

        MessageFormat header = new MessageFormat(
                "Agenda del " + selectedDate.format(
                        DateTimeFormatter.ofPattern("dd-MM-uuuu"))
        );

        // Usar el Printable estándar de JTable (incluye encabezados)
        Printable printable = tb.getPrintable(JTable.PrintMode.FIT_WIDTH, header, null);
        job.setPrintable(printable, pf);

        // Mostrar diálogo de impresión
        if (job.printDialog()) {
            job.print();
        }

    } catch (PrinterException e) {
        JOptionPane.showMessageDialog(this,
                "Error al imprimir: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    } finally {
        // Volver a dejar la tabla como estaba en pantalla
        tb.setFont(oldTableFont);
        tb.getTableHeader().setFont(oldHeaderFont);
        tb.setRowHeight(oldRowHeight);
    }
}

    // ====== Exportar CSV ======

    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void exportarCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar agenda como CSV");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));
        fc.setSelectedFile(new File("agenda_" + selectedDate.toString() + ".csv"));

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) {
            f = new File(f.getParentFile(), f.getName() + ".csv");
        }

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {

            // encabezados
            for (int col = 0; col < model.getColumnCount(); col++) {
                if (col > 0) pw.print(",");
                pw.print(esc(model.getColumnName(col)));
            }
            pw.println();

            // filas
            for (int r = 0; r < model.getRowCount(); r++) {
                for (int c = 0; c < model.getColumnCount(); c++) {
                    if (c > 0) pw.print(",");
                    Object val = model.getValueAt(r, c);
                    pw.print(esc(val == null ? "" : String.valueOf(val)));
                }
                pw.println();
            }

            JOptionPane.showMessageDialog(this,
                    "Agenda exportada correctamente.",
                    "CSV", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error exportando CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====== Calendario emergente (copiado de CorteCajaPanel) ======

    static class DayPopup extends JPopupMenu {
        private YearMonth ym;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        private final java.util.function.Consumer<LocalDate> onPick;

        DayPopup(LocalDate initial, java.util.function.Consumer<LocalDate> onPick) {
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
            java.time.DayOfWeek[] order = {
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY,
                java.time.DayOfWeek.SUNDAY
            };
            for (java.time.DayOfWeek d : order) {
                JLabel l = new JLabel(
                        d.getDisplayName(java.time.format.TextStyle.SHORT, ES_MX),
                        SwingConstants.CENTER);
                l.setFont(l.getFont().deriveFont(Font.PLAIN));
                dow.add(l);
            }
            add(dow, BorderLayout.CENTER);
            add(grid, BorderLayout.SOUTH);

            refresh();
        }

        private void refresh() {
            String mes = ym.getMonth().getDisplayName(
                    java.time.format.TextStyle.FULL, ES_MX);
            mes = mes.substring(0,1).toUpperCase(ES_MX) + mes.substring(1);
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
    // Abre el diálogo de búsqueda por nombre y devuelve el cliente elegido (o null)
private ClienteResumen seleccionarClientePorNombre(Window owner) {
    DialogBusquedaCliente dlg = new DialogBusquedaCliente(owner);
    dlg.setLocationRelativeTo(owner);
    dlg.setVisible(true);
    return dlg.getSeleccionado();
}
public static class DialogBusquedaCliente extends JDialog {

    private JTextField txtApellido;
    private JTable tabla;
    private DefaultTableModel modelo;
    private java.util.List<ClienteResumen> resultados = new ArrayList<>();
    private ClienteResumen seleccionado;

    public DialogBusquedaCliente(Window owner) {
        super(owner, "Buscar cliente por nombre o apellido", ModalityType.APPLICATION_MODAL);
        construirUI();
    }

    private void construirUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtro
        JPanel pnlFiltro = new JPanel(new BorderLayout(5, 0));
        pnlFiltro.add(new JLabel("Nombre o apellido:"), BorderLayout.WEST);
        txtApellido = new JTextField();
        pnlFiltro.add(txtApellido, BorderLayout.CENTER);

        JButton btnBuscar = new JButton("Buscar");
        pnlFiltro.add(btnBuscar, BorderLayout.EAST);

        main.add(pnlFiltro, BorderLayout.NORTH);

        // Tabla
        modelo = new DefaultTableModel(
                new Object[]{"Nombre completo", "Teléfono", "Teléfono 2", "Evento", "Prueba 1", "Prueba 2", "Entrega"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.setAutoCreateRowSorter(true);

        main.add(new JScrollPane(tabla), BorderLayout.CENTER);

        // Botones abajo
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSeleccionar = new JButton("Seleccionar");
        JButton btnCerrar = new JButton("Cancelar");
        pnlBotones.add(btnCerrar);
        pnlBotones.add(btnSeleccionar);

        main.add(pnlBotones, BorderLayout.SOUTH);

        setContentPane(main);
        setSize(800, 400);
        setLocationRelativeTo(getOwner());

        // Eventos
        btnBuscar.addActionListener(_e -> buscar());
        btnSeleccionar.addActionListener(_e -> seleccionarActual());
        btnCerrar.addActionListener(_e -> dispose());

        // Doble clic en la tabla
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    seleccionarActual();
                }
            }
        });

        // Enter en el campo de apellido = buscar
        txtApellido.addActionListener(_e -> buscar());
    }

    private void buscar() {
        String filtro = txtApellido.getText().trim();
        if (filtro.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe al menos una parte del nombre o apellido.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            clienteDAO dao = new clienteDAO();
            resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);  // <-- método que agregamos al DAO
            modelo.setRowCount(0);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            for (ClienteResumen cr : resultados) {
                modelo.addRow(new Object[]{
                        cr.getNombreCompleto(),
                        cr.getTelefono1(),
                        cr.getTelefono2(),
                        cr.getFechaEvento()   == null ? "" : cr.getFechaEvento().format(fmt),
                        cr.getFechaPrueba1()  == null ? "" : cr.getFechaPrueba1().format(fmt),
                        cr.getFechaPrueba2()  == null ? "" : cr.getFechaPrueba2().format(fmt),
                        cr.getFechaEntrega()  == null ? "" : cr.getFechaEntrega().format(fmt)
                });
            }

            if (resultados.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No se encontraron clientes con ese nombre o apellido.",
                        "Buscar cliente", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar clientes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seleccionarActual() {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un cliente de la tabla.",
                    "Buscar cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tabla.convertRowIndexToModel(row);
        seleccionado = resultados.get(modelRow);
        dispose();
    }

    public ClienteResumen getSeleccionado() {
        return seleccionado;
    }
}

}
