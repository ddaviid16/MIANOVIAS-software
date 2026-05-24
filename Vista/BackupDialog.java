package Vista;

import Utilidades.BackupService;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.ExecutionException;

public class BackupDialog extends JDialog {

    private final JTextField   txtCarpeta;
    private final JButton      btnRespaldar;
    private final JProgressBar barra;
    private final JLabel       lblEstado;

    public BackupDialog(Frame parent) {
        super(parent, "Respaldar base de datos", true);

        Path defCarpeta = Paths.get(System.getProperty("user.home"), "Desktop", "Respaldos MIANOVIAS");

        // ── Carpeta destino ──────────────────────────────────────────────────
        JPanel panelCarpeta = new JPanel(new BorderLayout(6, 0));
        panelCarpeta.add(new JLabel("Guardar en: "), BorderLayout.WEST);
        txtCarpeta = new JTextField(defCarpeta.toString(), 38);
        panelCarpeta.add(txtCarpeta, BorderLayout.CENTER);
        JButton btnExaminar = new JButton("Examinar…");
        panelCarpeta.add(btnExaminar, BorderLayout.EAST);

        // ── Progreso + estado ────────────────────────────────────────────────
        barra = new JProgressBar(0, 100);
        barra.setStringPainted(false);
        barra.setVisible(false);

        lblEstado = new JLabel(" ");

        JPanel panelCentro = new JPanel();
        panelCentro.setLayout(new BoxLayout(panelCentro, BoxLayout.Y_AXIS));
        panelCentro.add(barra);
        panelCentro.add(Box.createVerticalStrut(6));
        panelCentro.add(lblEstado);

        // ── Botones ──────────────────────────────────────────────────────────
        btnRespaldar = new JButton("Respaldar");
        btnRespaldar.setFont(btnRespaldar.getFont().deriveFont(Font.BOLD));
        JButton btnCerrar = new JButton("Cerrar");

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panelBotones.add(btnRespaldar);
        panelBotones.add(btnCerrar);

        // ── Composición ─────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(10, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        root.add(panelCarpeta, BorderLayout.NORTH);
        root.add(panelCentro,  BorderLayout.CENTER);
        root.add(panelBotones, BorderLayout.SOUTH);
        add(root);

        // ── Listeners ───────────────────────────────────────────────────────
        btnExaminar.addActionListener(_e -> {
            JFileChooser fc = new JFileChooser(txtCarpeta.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Selecciona carpeta de respaldos");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                txtCarpeta.setText(fc.getSelectedFile().getAbsolutePath());
        });

        btnRespaldar.addActionListener(_e -> iniciarRespaldo());
        btnCerrar.addActionListener(_e -> dispose());

        getRootPane().setDefaultButton(btnRespaldar);
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void iniciarRespaldo() {
        String texto = txtCarpeta.getText().trim();
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Elige una carpeta de destino.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path carpeta = Paths.get(texto);

        btnRespaldar.setEnabled(false);
        barra.setVisible(true);
        barra.setIndeterminate(true);
        lblEstado.setForeground(new Color(80, 80, 80));
        lblEstado.setText("Generando respaldo…");
        pack();

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return BackupService.respaldar(carpeta);
            }

            @Override
            protected void done() {
                barra.setIndeterminate(false);
                barra.setValue(100);
                btnRespaldar.setEnabled(true);
                try {
                    Path resultado = get();
                    lblEstado.setForeground(new Color(0, 120, 0));
                    lblEstado.setText("Respaldo guardado: " + resultado.getFileName());
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    lblEstado.setForeground(Color.RED);
                    lblEstado.setText("<html>" + causa.getMessage().replace("\n", "<br>") + "</html>");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setForeground(Color.RED);
                    lblEstado.setText("Respaldo interrumpido.");
                }
                pack();
            }
        }.execute();
    }
}
