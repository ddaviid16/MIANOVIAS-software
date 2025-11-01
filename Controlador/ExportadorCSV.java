package Controlador;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.math.BigDecimal;

public class ExportadorCSV {

    // Modificamos la constante DIR_EXPORT para eliminar la carpeta fija, ya que usaremos JFileChooser
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd_HHmm");

    /** Exporta una lista de objetos a CSV según los campos indicados, con JFileChooser. */
    public static <T> void guardarListaCSV(List<T> lista, String nombreBase, String... campos) {
        if (lista == null || lista.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No hay datos para exportar.");
            return;
        }

        // Usamos JFileChooser para elegir la ubicación y nombre del archivo
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar archivo CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));
        fileChooser.setSelectedFile(new File(nombreBase + ".csv")); // Nombre base para el archivo
        
        int result = fileChooser.showSaveDialog(null); // Abrir el cuadro de diálogo de guardar
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            
            // Asegurarnos de que tenga la extensión .csv
            if (!filePath.endsWith(".csv")) {
                selectedFile = new File(filePath + ".csv");
            }

            try (PrintWriter pw = abrirArchivo(selectedFile.getAbsolutePath())) {
                // Encabezados
                for (int i = 0; i < campos.length; i++) {
                    if (i > 0) pw.print(",");
                    pw.print(esc(campos[i]));
                }
                pw.println();

                // Filas
                for (T obj : lista) {
                    for (int i = 0; i < campos.length; i++) {
                        if (i > 0) pw.print(",");
                        pw.print(esc(toString(leerCampo(obj, campos[i]))));
                    }
                    pw.println();
                }

                JOptionPane.showMessageDialog(null,
                        "Archivo exportado:\n" + selectedFile.getAbsolutePath(),
                        "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Error exportando CSV: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ================== Helpers ==================

    private static PrintWriter abrirArchivo(String ruta) throws IOException {
        return new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(ruta), StandardCharsets.UTF_8));
    }

    private static String toString(Object o) {
        if (o == null) return "";
        if (o instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        return o.toString().replace("\n", " ").trim();
    }

    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static Object leerCampo(Object obj, String nombre) {
        try {
            Field f = obj.getClass().getDeclaredField(nombre);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return "";
        }
    }
}
