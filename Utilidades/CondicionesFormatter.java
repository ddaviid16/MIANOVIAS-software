package Utilidades;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public class CondicionesFormatter {

    public static String reemplazarVariables(String plantilla, Map<String, String> valores) {
        if (plantilla == null || plantilla.isBlank()) return "";
        String result = plantilla;
        for (Map.Entry<String, String> e : valores.entrySet()) {
            String clave = "\\{" + e.getKey() + "\\}";
            String valor = e.getValue() == null ? "" : e.getValue();
            result = result.replaceAll(clave, valor);
        }
        return result;
    }

    // formato de fechas uniforme dd-MM-yyyy
    public static String fmtFecha(java.time.LocalDate fecha) {
        if (fecha == null) return "";
        return fecha.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    public static String fmtDinero(Double val) {
        if (val == null) val = 0.0;
        return String.format("$%,.2f", val);
    }
}
