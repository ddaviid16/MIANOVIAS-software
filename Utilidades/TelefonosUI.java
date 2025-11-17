package Utilidades;

import javax.swing.*;
import javax.swing.text.*;

public final class TelefonosUI {

    private TelefonosUI() {}

    /** Solo dígitos, para combinar con TelefonoUtil.aBD si quieres. */
    public static String soloDigitos(String s) {
        if (s == null) return "";
        return s.replaceAll("\\D", "");
    }

    /** 1234567890 -> 123-456-7890 */
    public static String formatear(String s) {
        String digits = soloDigitos(s);
        int len = digits.length();
        if (len <= 3)  return digits;
        if (len <= 6)  return digits.substring(0,3) + "-" + digits.substring(3);
        if (len <= 10) return digits.substring(0,3) + "-" +
                                   digits.substring(3,6) + "-" +
                                   digits.substring(6);
        return digits;
    }

    /** Instala el filtro en un campo de teléfono (por defecto 10 dígitos). */
    public static void instalar(JTextField field) {
        instalar(field, 10);
    }

    /** Instala el filtro en un campo de teléfono con máximo de dígitos. */
    public static void instalar(JTextField field, int maxDigits) {
        AbstractDocument doc = (AbstractDocument) field.getDocument();
        doc.setDocumentFilter(new TelefonoFormatterFilter(maxDigits));
    }

    /** Filtro que mete guiones y solo deja dígitos. */
    public static class TelefonoFormatterFilter extends DocumentFilter {
        private final int maxDigits;
        private boolean updating = false;

        public TelefonoFormatterFilter(int maxDigits) {
            this.maxDigits = maxDigits;
        }

        private void actualizarTexto(FilterBypass fb, String nuevo, AttributeSet attrs)
                throws BadLocationException {

            String digits = soloDigitos(nuevo);
            if (digits.length() > maxDigits) return;

            String pretty = formatear(digits);

            updating = true;
            fb.replace(0, fb.getDocument().getLength(), pretty, attrs);
            updating = false;
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length,
                            String text, AttributeSet attrs) throws BadLocationException {
            if (updating) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String next = cur.substring(0, offset)
                        + (text == null ? "" : text)
                        + cur.substring(offset + length);
            actualizarTexto(fb, next, attrs);
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length)
                throws BadLocationException {
            replace(fb, offset, length, "", null);
        }
    }
}
