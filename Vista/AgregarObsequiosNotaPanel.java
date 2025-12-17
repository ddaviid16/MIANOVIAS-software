package Vista;

import Conexion.Conecta;
import Controlador.ObsequioInvDAO;
import Controlador.clienteDAO;
import Modelo.ObsequioInv;
import Modelo.ClienteResumen;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AgregarObsequiosNotaPanel extends JPanel {

    // === Búsqueda directa de nota (opcional) ===
    private final JTextField txtBuscarNota = new JTextField(15); // número_nota o folio
    private final JButton btBuscarNota = new JButton("Cargar nota");

    // === Búsqueda de cliente ===
    private final JTextField txtTelefono = new JTextField(12);
    private final JButton btBuscarPorTelefono = new JButton("Buscar por teléfono");
    private final JButton btBuscarPorNombre   = new JButton("Buscar por nombre...");

    // === Tabla de notas del cliente ===
    private final DefaultTableModel modelNotas = new DefaultTableModel(
            new String[]{"Nota", "Folio", "Tipo", "Fecha", "Total", "Saldo", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbNotas = new JTable(modelNotas);

    // === Info de nota seleccionada + obsequios ===
    private final JLabel lbInfoNota = new JLabel("Sin nota cargada");

    private final DefaultTableModel modelObsequios = new DefaultTableModel(
            new String[]{"Código", "Obsequio"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbObsequios = new JTable(modelObsequios);

    private final JButton btSeleccionarObsequios = new JButton("Agregar obsequios...");
    private final JButton btQuitarObsequio       = new JButton("Quitar obsequio");
    private final JButton btGuardar              = new JButton("Guardar cambios");

    // === Estado de la nota cargada ===
    private Integer numeroNotaActual = null;
    private String  tipoNotaActual   = null;      // CN / CR / ...
    private String  telefonoActual   = null;
    private Integer asesorActual     = null;

    // Códigos que ya estaban en BD cuando se cargó la nota
    private List<String> codigosOriginales = new ArrayList<>();
    // Códigos que se van a guardar (originales + nuevos - los que quites)
    private List<String> codigosEnEdicion = new ArrayList<>();

    public AgregarObsequiosNotaPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ====== TOP: búsqueda de nota / cliente ======
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        // fila 2: buscar por cliente
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        fila2.add(new JLabel("Teléfono cliente:"));
        fila2.add(txtTelefono);
        fila2.add(btBuscarPorTelefono);
        fila2.add(btBuscarPorNombre);

        top.add(fila2);

        add(top, BorderLayout.NORTH);

        // ====== CENTRO: notas del cliente + obsequios ======
        // Panel de notas
        JPanel panelNotas = new JPanel(new BorderLayout(4, 4));
        panelNotas.setBorder(BorderFactory.createTitledBorder("Notas del cliente (CN/CR)"));

        tbNotas.setRowHeight(22);

        // >>> OCULTAR COLUMNA "Nota" (columna 0 del modelo) <<<
        tbNotas.getColumnModel().getColumn(0).setMinWidth(0);
        tbNotas.getColumnModel().getColumn(0).setMaxWidth(0);
        tbNotas.getColumnModel().getColumn(0).setPreferredWidth(0);

        panelNotas.add(new JScrollPane(tbNotas), BorderLayout.CENTER);


        // Panel de obsequios
        JPanel panelObsequios = new JPanel(new BorderLayout(6, 6));
        lbInfoNota.setFont(lbInfoNota.getFont().deriveFont(Font.BOLD));
        panelObsequios.add(lbInfoNota, BorderLayout.NORTH);

        tbObsequios.setRowHeight(22);
        JScrollPane spTabla = new JScrollPane(tbObsequios);
        spTabla.setBorder(BorderFactory.createTitledBorder("Obsequios ligados a la nota"));
        panelObsequios.add(spTabla, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelNotas, panelObsequios);
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);

        // ====== BOTTOM: acciones ======
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        btSeleccionarObsequios.setEnabled(false);
        btQuitarObsequio.setEnabled(false);
        btGuardar.setEnabled(false);
        bottom.add(btSeleccionarObsequios);
        bottom.add(btQuitarObsequio);
        bottom.add(btGuardar);
        add(bottom, BorderLayout.SOUTH);

        // ====== Eventos ======
        btBuscarNota.addActionListener(_e -> buscarNota());
        txtBuscarNota.addActionListener(_e -> buscarNota());

        btBuscarPorTelefono.addActionListener(_e -> buscarNotasPorTelefonoAccion());
        btBuscarPorNombre.addActionListener(_e -> seleccionarClientePorNombre());

        btSeleccionarObsequios.addActionListener(_e -> abrirDialogSeleccionObsequios());
        btQuitarObsequio.addActionListener(_e -> quitarObsequioSeleccionado());
        btGuardar.addActionListener(_e -> guardarObsequios());

        // Cuando se selecciona una nota en la tabla, cargamos la info + obsequios
        tbNotas.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarNotaDesdeTablaSeleccionada();
            }
        });
    }

    // ================== LÓGICA DE BÚSQUEDA ==================

    /** Búsqueda original: nota por número_nota o folio. */
    private void buscarNota() {
        String txt = txtBuscarNota.getText().trim();
        if (txt.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Captura el número de nota o el folio.",
                    "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try (Connection cn = Conecta.getConnection()) {
            Integer num = null;
            try {
                num = Integer.valueOf(txt);
            } catch (NumberFormatException ignore) {
            }

            String sql;
            PreparedStatement ps;

            if (num != null) {
                sql = "SELECT numero_nota, folio, tipo, telefono, asesor " +
                      "FROM Notas WHERE numero_nota = ?";
                ps = cn.prepareStatement(sql);
                ps.setInt(1, num);
            } else {
                sql = "SELECT numero_nota, folio, tipo, telefono, asesor " +
                      "FROM Notas WHERE folio = ?";
                ps = cn.prepareStatement(sql);
                ps.setString(1, txt);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    limpiarNota();
                    JOptionPane.showMessageDialog(this,
                            "No se encontró ninguna nota con ese dato.",
                            "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                int numero = rs.getInt("numero_nota");
                String folio = rs.getString("folio");
                String tipo = rs.getString("tipo");
                String tel = rs.getString("telefono");
                int asesor = rs.getInt("asesor");
                Integer asesorObj = rs.wasNull() ? null : asesor;

                // Solo permitir CN/CR, que son ventas
                if (!"CN".equalsIgnoreCase(tipo) && !"CR".equalsIgnoreCase(tipo)) {
                    limpiarNota();
                    JOptionPane.showMessageDialog(this,
                            "Solo se pueden asociar obsequios a notas de contado o crédito (CN/CR).",
                            "Tipo de nota no válido", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                this.numeroNotaActual = numero;
                this.tipoNotaActual = tipo;
                this.telefonoActual = tel;
                this.asesorActual = asesorObj;

                lbInfoNota.setText("Folio: " + safe(folio) +
                        "   Tipo: " + safe(tipo) +
                        "   Teléfono: " + safe(tel));

                // Cargar obsequios actuales desde la tabla obsequios
                cargarObsequiosDesdeBD(cn);

                btSeleccionarObsequios.setEnabled(true);
                btQuitarObsequio.setEnabled(true);
                btGuardar.setEnabled(true);
            }

        } catch (SQLException ex) {
            limpiarNota();
            JOptionPane.showMessageDialog(this,
                    "Error al buscar nota: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Búsqueda de notas a partir del teléfono del cliente (para llenar la tabla de notas). */
    private void buscarNotasPorTelefonoAccion() {
        String tel = txtTelefono.getText().trim();
        if (tel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escribe el teléfono del cliente.",
                    "Buscar por teléfono", JOptionPane.WARNING_MESSAGE);
            return;
        }
        buscarNotasPorTelefono(tel);
    }

    private void buscarNotasPorTelefono(String tel) {
        // Limpiar estado de nota actual, pero no el teléfono ni el filtro
        limpiarNota();
        modelNotas.setRowCount(0);

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT numero_nota, folio, tipo, telefono, asesor, fecha_registro, total, saldo, status " +
                     "FROM Notas " +
                     "WHERE telefono = ? AND (tipo = 'CN' OR tipo = 'CR') " +
                     "ORDER BY fecha_registro DESC, numero_nota DESC")) {

            ps.setString(1, tel);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int numero = rs.getInt("numero_nota");
                    String folio = rs.getString("folio");
                    String tipo = rs.getString("tipo");
                    Date fecha = rs.getDate("fecha_registro");
                    BigDecimal total = null;
                    BigDecimal saldo = null;
                    try {
                        total = rs.getBigDecimal("total");
                    } catch (SQLException ignore) {}
                    try {
                        saldo = rs.getBigDecimal("saldo");
                    } catch (SQLException ignore) {}
                    String status = rs.getString("status");

                    modelNotas.addRow(new Object[]{
                            numero,
                            safe(folio),
                            safe(tipo),
                            (fecha == null ? "" : fecha.toString()),
                            total,
                            saldo,
                            safe(status)
                    });
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar notas del cliente: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (modelNotas.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No se encontraron notas de venta (CN/CR) para ese teléfono.",
                    "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Seleccionar la primera nota y disparar la carga
            tbNotas.setRowSelectionInterval(0, 0);
        }
    }

    /** Cuando el usuario selecciona una fila en la tabla de notas. */
    private void cargarNotaDesdeTablaSeleccionada() {
        int row = tbNotas.getSelectedRow();
        if (row < 0) return;

        int modelRow = tbNotas.convertRowIndexToModel(row);
        Object val = modelNotas.getValueAt(modelRow, 0); // columna "Nota"
        if (val == null) return;

        String txt = String.valueOf(val);
        txtBuscarNota.setText(txt);
        buscarNota();
    }

    private void limpiarNota() {
        numeroNotaActual = null;
        tipoNotaActual = null;
        telefonoActual = null;
        asesorActual = null;
        codigosOriginales.clear();
        codigosEnEdicion.clear();
        modelObsequios.setRowCount(0);
        lbInfoNota.setText("Sin nota cargada");
        btSeleccionarObsequios.setEnabled(false);
        btQuitarObsequio.setEnabled(false);
        btGuardar.setEnabled(false);
    }

    /** Carga los obsequios actuales de la nota desde la tabla obsequios. */
    private void cargarObsequiosDesdeBD(Connection cn) throws SQLException {
        modelObsequios.setRowCount(0);
        codigosOriginales.clear();
        codigosEnEdicion.clear();

        if (numeroNotaActual == null) return;

        String sql =
                "SELECT obsequio1, obsequio1_cod, " +
                "       obsequio2, obsequio2_cod, " +
                "       obsequio3, obsequio3_cod, " +
                "       obsequio4, obsequio4_cod, " +
                "       obsequio5, obsequio5_cod " +
                "FROM obsequios " +
                "WHERE numero_nota = ? AND status = 'A'";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNotaActual);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Sin obsequios actualmente
                    modelObsequios.addRow(new Object[]{"(sin obsequios)", ""});
                    return;
                }

                List<String> cods = new ArrayList<>();
                List<String> descs = new ArrayList<>();

                for (int i = 1; i <= 5; i++) {
                    String desc = rs.getString("obsequio" + i);
                    String cod = rs.getString("obsequio" + i + "_cod");
                    if (cod != null && !cod.trim().isEmpty()) {
                        cods.add(cod.trim());
                        descs.add(desc == null ? "" : desc.trim());
                    }
                }

                if (cods.isEmpty()) {
                    modelObsequios.addRow(new Object[]{"(sin obsequios)", ""});
                } else {
                    for (int i = 0; i < cods.size(); i++) {
                        modelObsequios.addRow(new Object[]{cods.get(i), descs.get(i)});
                    }
                }

                codigosOriginales.addAll(cods);
                codigosEnEdicion.addAll(cods);
            }
        }
    }

    /** Abre el dialog de selección y agrega nuevos códigos a los ya existentes. */
    private void abrirDialogSeleccionObsequios() {
        if (numeroNotaActual == null) {
            JOptionPane.showMessageDialog(this,
                    "Primero selecciona una nota del cliente.",
                    "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Window w = SwingUtilities.getWindowAncestor(this);
        Frame owner = (w instanceof Frame) ? (Frame) w : null;

        DialogSeleccionObsequios dlg =
                new DialogSeleccionObsequios(owner, null);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        List<String> nuevos = dlg.getSeleccionados();
        if (nuevos == null || nuevos.isEmpty()) {
            return; // canceló o no eligió nada
        }

        // Mezclar: los actuales + nuevos (sin duplicar, máx 5)
        List<String> resultado = new ArrayList<>(codigosEnEdicion);
        for (String c : nuevos) {
            if (c == null) continue;
            c = c.trim();
            if (c.isEmpty()) continue;
            if (!resultado.contains(c) && resultado.size() < 5) {
                resultado.add(c);
            }
        }

        codigosEnEdicion = resultado;
        refrescarTablaObsequios();
    }

    /** Elimina el obsequio seleccionado de la lista en edición. */
    private void quitarObsequioSeleccionado() {
        int row = tbObsequios.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un obsequio de la tabla.",
                    "Quitar obsequio", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String codigo = String.valueOf(modelObsequios.getValueAt(row, 0));
        if (codigo == null || codigo.startsWith("(")) {
            JOptionPane.showMessageDialog(this,
                    "No hay obsequio válido seleccionado.",
                    "Quitar obsequio", JOptionPane.WARNING_MESSAGE);
            return;
        }

        codigosEnEdicion.remove(codigo);
        refrescarTablaObsequios();
    }

    /** Rellena la tabla con los códigosEnEdicion, buscando descripción en inventario de obsequios. */
    private void refrescarTablaObsequios() {
        modelObsequios.setRowCount(0);
        if (codigosEnEdicion.isEmpty()) {
            modelObsequios.addRow(new Object[]{"(sin obsequios)", ""});
            return;
        }

        Map<String, String> mapaDesc;
        try {
            mapaDesc = cargarDescripcionesObsequios(codigosEnEdicion);
        } catch (Exception ex) {
            mapaDesc = new HashMap<>();
        }

        for (String cod : codigosEnEdicion) {
            String desc = mapaDesc.getOrDefault(cod, "");
            modelObsequios.addRow(new Object[]{cod, desc});
        }
    }

    /**
     * Usa ObsequioInvDAO para obtener las descripciones de los códigos seleccionados.
     * No es lo más eficiente del mundo, pero la tabla de obsequios es chica.
     */
    private Map<String, String> cargarDescripcionesObsequios(List<String> codigos) throws Exception {
        Map<String, String> out = new HashMap<>();
        if (codigos == null || codigos.isEmpty()) return out;

        HashSet<String> set = new HashSet<>();
        for (String c : codigos) {
            if (c != null && !c.trim().isEmpty()) {
                set.add(c.trim());
            }
        }
        if (set.isEmpty()) return out;

        ObsequioInvDAO dao = new ObsequioInvDAO();
        // Traemos todos los activos y filtramos por código
        List<ObsequioInv> lista = dao.listarActivosFiltrado("");
        for (ObsequioInv o : lista) {
            String cod = o.getCodigoArticulo();
            if (cod == null) continue;
            cod = cod.trim();
            if (set.contains(cod)) {
                out.put(cod, o.getArticulo());
            }
        }
        return out;
    }

    /** Guarda en la tabla obsequios; hace INSERT o UPDATE. */
    private void guardarObsequios() {
        if (numeroNotaActual == null) {
            JOptionPane.showMessageDialog(this,
                    "Primero selecciona una nota.",
                    "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validar duplicados (por si acaso)
        HashSet<String> unique = new HashSet<>();
        for (String c : codigosEnEdicion) {
            if (c == null) continue;
            c = c.trim();
            if (c.isEmpty()) continue;
            if (!unique.add(c)) {
                JOptionPane.showMessageDialog(this,
                        "Hay códigos de obsequio repetidos en la nota.\n" +
                        "Verifica la lista antes de guardar.",
                        "Obsequios duplicados", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (codigosEnEdicion.isEmpty()) {
            int r = JOptionPane.showConfirmDialog(this,
                    "La nota quedará sin obsequios.\n¿Seguro que deseas continuar?",
                    "Confirmar", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Códigos nuevos respecto a lo que ya tenía al cargar la nota (por si luego manejas inventario)
        HashSet<String> orig = new HashSet<>(codigosOriginales);
        List<String> codigosNuevos = new ArrayList<>();
        for (String c : codigosEnEdicion) {
            if (c == null) continue;
            c = c.trim();
            if (c.isEmpty()) continue;
            if (!orig.contains(c)) {
                codigosNuevos.add(c);
            }
        }

        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                // Obtener descripciones desde inventario de obsequios
                Map<String, String> mapaDesc = cargarDescripcionesObsequios(codigosEnEdicion);

                // Preparar arrays de 5 posiciones
                String[] cod = new String[5];
                String[] desc = new String[5];
                for (int i = 0; i < 5; i++) {
                    if (i < codigosEnEdicion.size()) {
                        String c = codigosEnEdicion.get(i);
                        cod[i] = c;
                        desc[i] = mapaDesc.get(c);
                    } else {
                        cod[i] = null;
                        desc[i] = null;
                    }
                }

                // ¿ya existe registro en obsequios?
                boolean existe = false;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT 1 FROM obsequios WHERE numero_nota = ?")) {
                    ps.setInt(1, numeroNotaActual);
                    try (ResultSet rs = ps.executeQuery()) {
                        existe = rs.next();
                    }
                }

                if (existe) {
                    try (PreparedStatement ps = cn.prepareStatement(
                            "UPDATE obsequios SET " +
                            " telefono = ?, " +
                            " fecha_operacion = CURRENT_DATE(), " +
                            " obsequio1 = ?, obsequio1_cod = ?, " +
                            " obsequio2 = ?, obsequio2_cod = ?, " +
                            " obsequio3 = ?, obsequio3_cod = ?, " +
                            " obsequio4 = ?, obsequio4_cod = ?, " +
                            " obsequio5 = ?, obsequio5_cod = ?, " +
                            " tipo_operacion = ?, asesor = ?, status = 'A' " +
                            "WHERE numero_nota = ?")) {

                        ps.setString(1, telefonoActual);

                        // obsequio1..5 + cod
                        int idx = 2;
                        for (int i = 0; i < 5; i++) {
                            if (desc[i] == null) ps.setNull(idx++, Types.VARCHAR);
                            else                ps.setString(idx++, desc[i]);

                            if (cod[i] == null)  ps.setNull(idx++, Types.VARCHAR);
                            else                 ps.setString(idx++, cod[i]);
                        }

                        ps.setString(idx++, tipoNotaActual);
                        if (asesorActual == null) ps.setNull(idx++, Types.INTEGER);
                        else                      ps.setInt(idx++, asesorActual);
                        ps.setInt(idx, numeroNotaActual);

                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = cn.prepareStatement(
                            "INSERT INTO obsequios (" +
                            " numero_nota, telefono, fecha_operacion, " +
                            " obsequio1, obsequio1_cod, " +
                            " obsequio2, obsequio2_cod, " +
                            " obsequio3, obsequio3_cod, " +
                            " obsequio4, obsequio4_cod, " +
                            " obsequio5, obsequio5_cod, " +
                            " tipo_operacion, asesor, status" +
                            ") VALUES (?,?,CURRENT_DATE(),?,?,?,?,?,?,?,?,?,?,?,?,'A')")) {

                        ps.setInt(1, numeroNotaActual);
                        ps.setString(2, telefonoActual);

                        int idx = 3;
                        for (int i = 0; i < 5; i++) {
                            if (desc[i] == null) ps.setNull(idx++, Types.VARCHAR);
                            else                ps.setString(idx++, desc[i]);

                            if (cod[i] == null)  ps.setNull(idx++, Types.VARCHAR);
                            else                 ps.setString(idx++, cod[i]);
                        }

                        ps.setString(idx++, tipoNotaActual);
                        if (asesorActual == null) ps.setNull(idx++, Types.INTEGER);
                        else                      ps.setInt(idx++, asesorActual);

                        ps.executeUpdate();
                    }
                }

                cn.commit();

                // Actualizar "originales" para evitar volver a descontar si guardan otra vez
                codigosOriginales = new ArrayList<>(codigosEnEdicion);

                JOptionPane.showMessageDialog(this,
                        "Obsequios guardados correctamente. Puedes reimprimir en Re-imprimir nota.",
                        "Listo", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                cn.rollback();
                throw ex;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al guardar obsequios: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================== BÚSQUEDA DE CLIENTE POR NOMBRE/APELLIDO ==================

    /** Abre un diálogo para buscar cliente por nombre/apellidos y luego carga sus notas por teléfono. */
    private void seleccionarClientePorNombre() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        DialogBusquedaClienteObsequios dlg = new DialogBusquedaClienteObsequios(owner);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        ClienteResumen cr = dlg.getSeleccionado();
        if (cr != null) {
            String tel = Utilidades.TelefonosUI.soloDigitos(cr.getTelefono1());
            if (tel != null && !tel.isEmpty()) {
                txtTelefono.setText(tel);
                buscarNotasPorTelefono(tel);
            } else {
                JOptionPane.showMessageDialog(this,
                        "El cliente seleccionado no tiene teléfono principal registrado.",
                        "Sin teléfono", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    // ================== Diálogo de búsqueda de cliente ==================

    private static class DialogBusquedaClienteObsequios extends JDialog {

        private JTextField txtNombre;
        private JTable tabla;
        private DefaultTableModel modelo;
        private java.util.List<ClienteResumen> resultados = new ArrayList<>();
        private ClienteResumen seleccionado;

        public DialogBusquedaClienteObsequios(Window owner) {
            super(owner, "Buscar cliente por nombre / apellido", ModalityType.APPLICATION_MODAL);
            construirUI();
        }

        private void construirUI() {
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Filtro
            JPanel pnlFiltro = new JPanel(new BorderLayout(5, 0));
            pnlFiltro.add(new JLabel("Nombre / Apellido:"), BorderLayout.WEST);
            txtNombre = new JTextField();
            pnlFiltro.add(txtNombre, BorderLayout.CENTER);

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

            // Enter en el campo de nombre/apellido = buscar
            txtNombre.addActionListener(_e -> buscar());
        }

        private void buscar() {
            String filtro = txtNombre.getText().trim();
            if (filtro.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Escribe al menos una parte del nombre o apellidos.",
                        "Buscar cliente", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                clienteDAO dao = new clienteDAO();
                // Asegúrate de implementar este método en tu clienteDAO:
                // public List<ClienteResumen> buscarOpcionesPorNombreOApellidos(String filtro)
                resultados = dao.buscarOpcionesPorNombreOApellidos(filtro);
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
                            "No se encontraron clientes con ese dato.",
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
