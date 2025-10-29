// src/Impresion/PrintHelper.java
package Impresion;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.*;
import java.awt.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

public final class PrintHelper {

    public enum Resultado { OK, CANCELADO, FALLO }

    private PrintHelper(){}

    /** Muestra el diálogo de impresión del SO y espera a que el trabajo termine.
     *  Devuelve OK si el trabajo terminó correctamente, CANCELADO si el usuario canceló,
     *  y FALLO si la impresora reportó error. */
    public static Resultado imprimirConDialogoYEsperar(Component parent, Printable printable, String titulo) {
    try {
        PrinterJob pj = PrinterJob.getPrinterJob();
        pj.setJobName(titulo);
        pj.setPrintable(printable); // ← clave: fija el Printable antes del diálogo

        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        if (!pj.printDialog(attrs)) return Resultado.CANCELADO;

        pj.print(attrs);            // ← imprime con el mismo PrinterJob del diálogo
        return Resultado.OK;
    } catch (PrinterException ex) {
        return Resultado.FALLO;
    } catch (Exception ex) {
        return Resultado.FALLO;
    }
}

    private static JDialog crearDialogoProgreso(Component parent, String titulo, String msg) {
        Window owner = parent==null?null:SwingUtilities.windowForComponent(parent);
        JDialog d = new JDialog(owner, titulo, Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(10,10));
        d.add(new JLabel("  " + msg + "  "), BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true);
        d.add(bar, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        return d;
    }
}
