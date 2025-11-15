package Utilidades;

import Controlador.SeguridadDAO;

import javax.swing.*;
import java.util.Arrays;

/**
 * Utilidades de UI para pedir / cambiar la clave de acceso.
 */
public final class SeguridadUI {

    private SeguridadUI() {}

    /** 
     * Pide la clave.
     * Si no hay clave configurada, obliga a crear una primera vez.
     */
    public static boolean pedirYValidarClave(java.awt.Component parent) {
        try {
            SeguridadDAO dao = new SeguridadDAO();
            if (!dao.hayPassword()) {
                int r = JOptionPane.showConfirmDialog(parent,
                        "Aún no hay una clave de acceso configurada.\n" +
                        "¿Deseas crearla ahora?",
                        "Configurar clave", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.YES_OPTION) return false;
                return configurarClaveInicial(parent, dao);
            }

            JPasswordField pf = new JPasswordField();
            Object[] msg = {"Ingresa la clave de acceso:", pf};
            int r = JOptionPane.showConfirmDialog(parent, msg, "Autorización",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return false;

            char[] pass = pf.getPassword();
            boolean ok = dao.validarPassword(pass);
            if (!ok) {
                JOptionPane.showMessageDialog(parent, "Clave incorrecta",
                        "Acceso denegado", JOptionPane.ERROR_MESSAGE);
            }
            return ok;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Error al validar la clave: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** Diálogo para cambiar la clave (actual + nueva + confirmación). */
public static void cambiarClaveConDialogo(java.awt.Component parent) {
    try {
        SeguridadDAO dao = new SeguridadDAO();
        if (!dao.hayPassword()) {
            // Si no hay clave, aprovechamos para configurarla aquí mismo
            int r = JOptionPane.showConfirmDialog(parent,
                    "No hay una clave configurada aún.\n" +
                    "¿Deseas crearla ahora?",
                    "Configurar clave",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;

            configurarClaveInicial(parent, dao);
            return;
        }

        // Sí hay clave: pedir ACTUAL + NUEVA + CONFIRMACIÓN
        JPasswordField pfActual = new JPasswordField();
        JPasswordField pfNueva1 = new JPasswordField();
        JPasswordField pfNueva2 = new JPasswordField();

        Object[] msg = {
                "Clave actual:", pfActual,
                "Nueva clave:", pfNueva1,
                "Confirmar nueva clave:", pfNueva2
        };

        int r = JOptionPane.showConfirmDialog(parent, msg,
                "Cambiar clave de acceso",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (r != JOptionPane.OK_OPTION) {
            // usuario canceló
            Arrays.fill(pfActual.getPassword(), '\0');
            Arrays.fill(pfNueva1.getPassword(), '\0');
            Arrays.fill(pfNueva2.getPassword(), '\0');
            return;
        }

        char[] actual = pfActual.getPassword();
        char[] n1     = pfNueva1.getPassword();
        char[] n2     = pfNueva2.getPassword();

        // 1) validar que la nueva no esté vacía
        if (n1.length == 0) {
            JOptionPane.showMessageDialog(parent,
                    "La nueva clave no puede estar vacía.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            Arrays.fill(actual, '\0');
            Arrays.fill(n1, '\0');
            Arrays.fill(n2, '\0');
            return;
        }

        // 2) validar confirmación
        if (!Arrays.equals(n1, n2)) {
            JOptionPane.showMessageDialog(parent,
                    "La nueva clave y su confirmación no coinciden.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            Arrays.fill(actual, '\0');
            Arrays.fill(n1, '\0');
            Arrays.fill(n2, '\0');
            return;
        }

        // 3) validar clave ACTUAL
        if (!dao.validarPassword(actual)) {
            JOptionPane.showMessageDialog(parent,
                    "La clave actual es incorrecta.",
                    "Acceso denegado", JOptionPane.ERROR_MESSAGE);
            Arrays.fill(actual, '\0');
            Arrays.fill(n1, '\0');
            Arrays.fill(n2, '\0');
            return;
        }

        // 4) guardar nueva clave
        dao.establecerNuevaPassword(n1);
        Arrays.fill(actual, '\0');
        Arrays.fill(n1, '\0');
        Arrays.fill(n2, '\0');

        JOptionPane.showMessageDialog(parent,
                "Clave actualizada correctamente.",
                "Listo", JOptionPane.INFORMATION_MESSAGE);

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(parent,
                "Error al cambiar la clave: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}



    private static boolean configurarClaveInicial(java.awt.Component parent, SeguridadDAO dao) {
        JPasswordField pfNueva1 = new JPasswordField();
        JPasswordField pfNueva2 = new JPasswordField();

        Object[] msg = {
                "Define una nueva clave de acceso:", pfNueva1,
                "Confirma la nueva clave:", pfNueva2
        };

        int r = JOptionPane.showConfirmDialog(parent, msg,
                "Configurar clave", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;

        char[] n1 = pfNueva1.getPassword();
        char[] n2 = pfNueva2.getPassword();

        if (!Arrays.equals(n1, n2)) {
            JOptionPane.showMessageDialog(parent,
                    "La clave y su confirmación no coinciden.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (n1.length == 0) {
            JOptionPane.showMessageDialog(parent,
                    "La clave no puede estar vacía.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            dao.establecerNuevaPassword(n1);
            Arrays.fill(n1, '\0');
            Arrays.fill(n2, '\0');
            JOptionPane.showMessageDialog(parent,
                    "Clave configurada correctamente.",
                    "Listo", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Error al guardar la clave: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
