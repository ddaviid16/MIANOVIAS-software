package Vista;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

public final class ConfirmClose {

    private ConfirmClose() {}

    /** Agrega confirmación de cierre (SI/NO) a un JFrame o JDialog. */
    public static void attach(Window w, String message) {
        if (w instanceof JFrame jf) jf.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        if (w instanceof JDialog jd) jd.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        w.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                Object[] ops = {"SI","NO"};
                int r = JOptionPane.showOptionDialog(w, message, "Confirmación",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, ops, ops[1]);
                if (r == JOptionPane.YES_OPTION) {
                    w.dispose();  // solo cierra esa ventana
                }
            }
        });
    }
}
