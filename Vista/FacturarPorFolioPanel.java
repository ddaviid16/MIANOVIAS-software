package Vista;

import Controlador.FacturaDatosDAO;
import Controlador.clienteDAO;
import Conexion.Conecta;
import Utilidades.CatalogoCFDI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.format.DateTimeFormatter;

public class FacturarPorFolioPanel extends JPanel {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JTextField tfFolio = new JTextField(12);
    private final JButton btBuscar = new JButton("Buscar folio");
    private final JButton btEditar = new JButton("Capturar / editar datos de factura");

    private final JLabel lbNotaInfo = new JLabel(" ", SwingConstants.LEFT);

    private final DefaultTableModel modelFactura = new DefaultTableModel(
            new String[]{"Campo", "Valor"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tbFactura = new JTable(modelFactura);

    private Integer notaActual = null;

    public FacturarPorFolioPanel() {
        setLayout(new BorderLayout(8, 8));

        // ====== Norte: búsqueda por folio ======
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        north.add(new JLabel("Folio:"));
        north.add(tfFolio);
        north.add(btBuscar);
        north.add(btEditar);

        btEditar.setEnabled(false);

        add(north, BorderLayout.NORTH);

        // ====== Centro: info de nota + tabla de factura ======
        lbNotaInfo.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JScrollPane spFactura = new JScrollPane(tbFactura);
        tbFactura.setRowHeight(22);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(lbNotaInfo, BorderLayout.NORTH);
        center.add(spFactura, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        // listeners
        btBuscar.addActionListener(_e -> buscarPorFolio());
        btEditar.addActionListener(_e -> editarFacturaDeNota());
    }

    // ================= BÚSQUEDA =================

    private void buscarPorFolio() {
        String folio = tfFolio.getText().trim();
        if (folio.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Captura un folio.");
            return;
        }

        String sql = """
            SELECT numero_nota, tipo, folio, telefono, fecha_registro, total, status
            FROM Notas
            WHERE folio = ?
            ORDER BY numero_nota DESC
            LIMIT 1
        """;

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, folio);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    notaActual = null;
                    btEditar.setEnabled(false);
                    lbNotaInfo.setText("No se encontró ninguna nota con ese folio.");
                    modelFactura.setRowCount(0);
                    return;
                }

                notaActual = rs.getInt("numero_nota");
                String tipo    = rs.getString("tipo");
                String tel     = rs.getString("telefono");
                Date f         = rs.getDate("fecha_registro");
                double total   = rs.getDouble("total");
                String status  = rs.getString("status");

                String nombre = obtenerNombreClientePorTelefono(tel);
                String fecha  = (f == null) ? "" : f.toLocalDate().format(DF);

                lbNotaInfo.setText(String.format(
                        "Nota %d   Folio: %s   Tipo: %s   Fecha: %s   Cliente: %s   Total: %.2f   Status: %s",
                        notaActual, folio, tipo, fecha, nombre, total, status
                ));

                btEditar.setEnabled(true);
                cargarFactura(notaActual);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al buscar folio: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String obtenerNombreClientePorTelefono(String tel) {
        if (tel == null || tel.isBlank()) return "";
        try {
            var cr = new clienteDAO().buscarResumenPorTelefono(tel);
            if (cr != null && cr.getNombreCompleto() != null) {
                return cr.getNombreCompleto();
            }
        } catch (Exception ignore) {}
        return tel; // si todo falla, al menos ver el teléfono
    }

    // ================= FACTURA (mostrar) =================

    private void cargarFactura(int numeroNota) {
        modelFactura.setRowCount(0);

        try {
            FacturaDatosDAO dao = new FacturaDatosDAO();
            FacturaDatosDAO.Row fd = dao.obtenerPorNota(numeroNota);

            if (fd == null) {
                addKV("Persona", "");
                addKV("RFC", "");
                addKV("Régimen fiscal", "");
                addKV("Uso del CFDI", "");
                addKV("Código postal", "");
                addKV("Correo", "");
                return;
            }

            String personaFmt =
                    (fd.persona == null) ? "" :
                            (fd.persona.equalsIgnoreCase("PM") ? "Persona moral" :
                                    fd.persona.equalsIgnoreCase("PF") ? "Persona física" : fd.persona);

            addKV("Persona", personaFmt);
            addKV("RFC", safe(fd.rfc));

            String regClave = safe(fd.regimen);
            String usoClave = safe(fd.usoCfdi);

            CatalogoCFDI.Regimen reg = CatalogoCFDI.buscarRegimenPorClave(regClave);
            CatalogoCFDI.UsoCfdi uso = CatalogoCFDI.buscarUsoPorClave(usoClave);

            String regFmt = (reg == null ? regClave : reg.toString());
            String usoFmt = (uso == null ? usoClave : uso.toString());

            addKV("Régimen fiscal", regFmt);
            addKV("Uso del CFDI", usoFmt);
            addKV("Código postal", safe(fd.codigoPostal));
            addKV("Correo", safe(fd.correo));

            if (fd.createdAt != null) addKV("Capturado", fd.createdAt.toString());
            if (fd.updatedAt != null) addKV("Actualizado", fd.updatedAt.toString());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error cargando datos de factura: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addKV(String k, String v) {
        modelFactura.addRow(new Object[]{k, v == null ? "" : v});
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ================= FACTURA (capturar / editar) =================

    private void editarFacturaDeNota() {
        if (notaActual == null) {
            JOptionPane.showMessageDialog(this, "Primero busca un folio válido.");
            return;
        }

        FacturaDatosDAO fdao = new FacturaDatosDAO();
        FacturaDatosDAO.Row fdActual;
        try {
            fdActual = fdao.obtenerPorNota(notaActual);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error leyendo datos de factura: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Precarga para el diálogo
        VentaContadoPanel.DlgFactura.CapturaFactura init = null;
        if (fdActual != null) {
            init = new VentaContadoPanel.DlgFactura.CapturaFactura();
            init.persona = fdActual.persona;
            init.rfc     = fdActual.rfc;
            init.regimen = fdActual.regimen;
            init.usoCfdi = fdActual.usoCfdi;
            init.codigoPostal = fdActual.codigoPostal;
            init.correo  = fdActual.correo;
        }

        // Dueño de la ventana
        Window w = SwingUtilities.getWindowAncestor(this);
        Frame owner = (w instanceof Frame) ? (Frame) w : null;

        // Usamos el MISMO diálogo que en VentaContadoPanel
        VentaContadoPanel.DlgFactura dlg = new VentaContadoPanel.DlgFactura(owner, init);
        VentaContadoPanel.DlgFactura.CapturaFactura res = dlg.showDialog();

        try {
            if (res == null) {
                // ¿El usuario eligió "Quitar captura"?
                if (dlg.fueLimpiado()) {
                    fdao.eliminarPorNota(notaActual);
                    cargarFactura(notaActual);
                    JOptionPane.showMessageDialog(this, "Se eliminaron los datos de factura de esta nota.");
                }
                return; // cancelado
            }

            // Guardar / actualizar
            fdao.upsert(
                    notaActual,
                    res.persona,
                    res.rfc,
                    res.regimen,
                    res.usoCfdi,
                    res.codigoPostal,
                    res.correo
            );

            cargarFactura(notaActual);
            JOptionPane.showMessageDialog(this, "Datos de factura guardados correctamente.");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error guardando datos de factura: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
