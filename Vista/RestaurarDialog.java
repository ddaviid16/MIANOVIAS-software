package Vista;

import Utilidades.BackupService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Diálogo para restaurar la base de datos desde un archivo .sql.
 *
 * Flujo esperado antes de abrir este diálogo:
 *   1. Mostrar aviso de advertencia al usuario.
 *   2. Validar la clave del sistema (SeguridadUI.pedirYValidarClave).
 *   3. Mostrar este diálogo para elegir el archivo y ejecutar la restauración.
 */
public class RestaurarDialog extends JDialog {

    private final JTextField   txtArchivo;
    private final JButton      btnRestaurar;
    private final JProgressBar barra;
    private final JLabel       lblEstado;

    public RestaurarDialog(Frame parent) {
        super(parent, "Restaurar base de datos", true);

        // ── Aviso interno (recordatorio visual dentro del diálogo) ───────────
        JLabel lblAviso = new JLabel(
            "<html><b>Selecciona el archivo de respaldo (.sql) que deseas restaurar.</b><br>" +
            "Los datos actuales serán reemplazados por los del archivo seleccionado.</html>"
        );
        lblAviso.setForeground(new Color(140, 60, 0));
        lblAviso.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 120, 0), 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        // ── Selector de archivo ──────────────────────────────────────────────
        JPanel panelArchivo = new JPanel(new BorderLayout(6, 0));
        panelArchivo.add(new JLabel("Archivo de respaldo: "), BorderLayout.WEST);
        txtArchivo = new JTextField(38);
        txtArchivo.setEditable(false);
        panelArchivo.add(txtArchivo, BorderLayout.CENTER);
        JButton btnExaminar = new JButton("Examinar…");
        panelArchivo.add(btnExaminar, BorderLayout.EAST);

        // ── Progreso + estado ────────────────────────────────────────────────
        barra = new JProgressBar(0, 100);
        barra.setStringPainted(false);
        barra.setVisible(false);

        lblEstado = new JLabel(" ");

        JPanel panelCentro = new JPanel();
        panelCentro.setLayout(new BoxLayout(panelCentro, BoxLayout.Y_AXIS));
        panelCentro.add(Box.createVerticalStrut(6));
        panelCentro.add(panelArchivo);
        panelCentro.add(Box.createVerticalStrut(10));
        panelCentro.add(barra);
        panelCentro.add(Box.createVerticalStrut(4));
        panelCentro.add(lblEstado);

        // ── Botones ──────────────────────────────────────────────────────────
        btnRestaurar = new JButton("Restaurar");
        btnRestaurar.setFont(btnRestaurar.getFont().deriveFont(Font.BOLD));
        btnRestaurar.setEnabled(false);   // se habilita cuando hay archivo elegido
        JButton btnCerrar = new JButton("Cerrar");

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panelBotones.add(btnRestaurar);
        panelBotones.add(btnCerrar);

        // ── Composición ─────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(10, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        root.add(lblAviso,      BorderLayout.NORTH);
        root.add(panelCentro,   BorderLayout.CENTER);
        root.add(panelBotones,  BorderLayout.SOUTH);
        add(root);

        // ── Listeners ───────────────────────────────────────────────────────
        btnExaminar.addActionListener(_e -> elegirArchivo());
        btnRestaurar.addActionListener(_e -> iniciarRestauracion());
        btnCerrar.addActionListener(_e -> dispose());

        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    // ── Selección del archivo .sql ───────────────────────────────────────────

    private void elegirArchivo() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Selecciona el archivo de respaldo");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos SQL (*.sql)", "sql"));
        fc.setAcceptAllFileFilterUsed(false);

        // Iniciar en la carpeta de respaldos predeterminada si existe
        java.nio.file.Path defCarpeta = java.nio.file.Paths.get(
            System.getProperty("user.home"), "Desktop", "Respaldos MIANOVIAS");
        if (java.nio.file.Files.isDirectory(defCarpeta))
            fc.setCurrentDirectory(defCarpeta.toFile());

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtArchivo.setText(fc.getSelectedFile().getAbsolutePath());
            btnRestaurar.setEnabled(true);
            lblEstado.setText(" ");
            barra.setVisible(false);
            pack();
        }
    }

    // ── Ejecución de la restauración ─────────────────────────────────────────

    private void iniciarRestauracion() {
        String ruta = txtArchivo.getText().trim();
        if (ruta.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Elige el archivo de respaldo primero.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path archivo = java.nio.file.Paths.get(ruta);

        btnRestaurar.setEnabled(false);
        barra.setVisible(true);
        barra.setIndeterminate(true);
        lblEstado.setForeground(new Color(80, 80, 80));
        lblEstado.setText("Restaurando base de datos…");
        pack();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                BackupService.restaurar(archivo);
                return null;
            }

            @Override
            protected void done() {
                barra.setIndeterminate(false);
                barra.setValue(100);
                btnRestaurar.setEnabled(true);
                try {
                    get();   // lanza ExecutionException si doInBackground falló
                    lblEstado.setForeground(new Color(0, 120, 0));
                    lblEstado.setText("Restauración completada correctamente.");
                    JOptionPane.showMessageDialog(
                        RestaurarDialog.this,
                        "<html>La base de datos fue restaurada correctamente.<br><br>" +
                        "<b>Se recomienda reiniciar la aplicación</b> para que<br>" +
                        "los cambios sean visibles en todos los módulos.</html>",
                        "Restauración exitosa",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    lblEstado.setForeground(Color.RED);
                    lblEstado.setText(
                        "<html>" + causa.getMessage().replace("\n", "<br>") + "</html>");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setForeground(Color.RED);
                    lblEstado.setText("Restauración interrumpida.");
                }
                pack();
            }
        }.execute();
    }
}
