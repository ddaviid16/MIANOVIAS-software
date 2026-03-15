package Utilidades;

import Conexion.Conecta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

/**
 * Configuración de textos para la hoja de condiciones (sin cambios de esquema).
 * Se guarda en empresa_condiciones con id=2.
 */
public final class PlantillaCondicionesConfig {

    private static final int CONFIG_ID = 2;

    private PlantillaCondicionesConfig() {}

    public static final class Datos {
        public String intro = "En MIANOVIAS, ¡te damos la bienvenida a vivir esta gran experiencia!";
        public String lblNombreNovia = "NOMBRE DE LA NOVIA:";
        public String lblAcuerdo = "ESTOY DE ACUERDO:";
        public String lblFirma = "NOMBRE Y FIRMA DEL CLIENTE";
    }

    public static Datos cargar() {
        Datos out = new Datos();
        String raw = leerRaw();
        if (raw == null || raw.isBlank()) return out;

        try {
            Properties p = new Properties();
            p.load(new java.io.StringReader(raw));

            out.intro = val(p, "intro", out.intro);
            out.lblNombreNovia = val(p, "lblNombreNovia", out.lblNombreNovia);
            out.lblAcuerdo = val(p, "lblAcuerdo", out.lblAcuerdo);
            out.lblFirma = val(p, "lblFirma", out.lblFirma);
        } catch (Exception ignore) {
            // Si no parsea, se mantienen defaults.
        }
        return out;
    }

    public static void guardar(Datos d) throws Exception {
        Properties p = new Properties();
        p.setProperty("intro", safe(d.intro));
        p.setProperty("lblNombreNovia", safe(d.lblNombreNovia));
        p.setProperty("lblAcuerdo", safe(d.lblAcuerdo));
        p.setProperty("lblFirma", safe(d.lblFirma));

        java.io.StringWriter sw = new java.io.StringWriter();
        p.store(sw, "Plantilla hoja condiciones");

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     "INSERT INTO empresa_condiciones (id, texto) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE texto = VALUES(texto)")) {
            ps.setInt(1, CONFIG_ID);
            ps.setString(2, sw.toString());
            ps.executeUpdate();
        }
    }

    private static String leerRaw() {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement("SELECT texto FROM empresa_condiciones WHERE id=?")) {
            ps.setInt(1, CONFIG_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("texto");
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String val(Properties p, String k, String def) {
        String v = p.getProperty(k);
        if (v == null || v.isBlank()) return def;
        return v.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
