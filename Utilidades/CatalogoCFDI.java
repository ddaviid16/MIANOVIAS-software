package Utilidades;

import java.util.*;

public final class CatalogoCFDI {

    // === Tipos que usaremos en los combos ===
    public static final class Regimen {
        public final String clave;       // p.ej. "601"
        public final String descripcion; // texto completo
        public final String persona;     // "PF" o "PM"

        public Regimen(String clave, String descripcion, String persona) {
            this.clave = clave;
            this.descripcion = descripcion;
            this.persona = persona;
        }

        @Override
        public String toString() {
            // Lo que verá el usuario en el combo
            return clave + " - " + descripcion;
        }
    }

    public static final class UsoCfdi {
        public final String clave;       // p.ej. "G03"
        public final String descripcion; // texto completo

        public UsoCfdi(String clave, String descripcion) {
            this.clave = clave;
            this.descripcion = descripcion;
        }

        @Override
        public String toString() {
            return clave + " - " + descripcion;
        }
    }

    // Catálogos básicos
    private static final Map<String, Regimen> REGIMENES = new LinkedHashMap<>();
    private static final Map<String, UsoCfdi> USOS     = new LinkedHashMap<>();

    // Compatibilidad: régimen -> lista de claves de uso_cfdi
    private static final Map<String, List<String>> COMPATIBILIDAD = new HashMap<>();

    static {
        // ================== RÉGIMENES ==================
        // EJEMPLOS: aquí pegas TODOS los que tengas en tu Excel
        addRegimen(new Regimen("601", "General de Ley Personas Morales", "PM"));
        addRegimen(new Regimen("603", "Personas Morales con Fines no Lucrativos", "PM"));
        addRegimen(new Regimen("605", "Sueldos y salarios e ingresos asimilados a salarios", "PF"));
        addRegimen(new Regimen("606", "Arrendamiento", "PF"));
        addRegimen( new Regimen("607", "Régimen de Enajenación o Adquisición de Bienes", "PF"));
        addRegimen(new Regimen("608", "Demás ingresos", "PF"));
        addRegimen(new Regimen("610", "Residentes en el Extranjero sin Establecimiento Permanente en México", "PF"));
        addRegimen(new Regimen("611", "Ingresos por Dividendos (socios y accionistas)", "PF"));
        addRegimen(new Regimen("612", "Personas Físicas con Actividades Empresariales y Profesionales", "PF"));
        addRegimen(new Regimen("614", "Ingresos por intereses", "PF"));
        addRegimen(new Regimen("615", "Régimen de los ingresos por obtención de premios", "PF"));
        addRegimen(new Regimen("616", "Sin obligaciones fiscales", "PF"));
        addRegimen(new Regimen("620", "Sociedades Cooperativas de Producción que optan por diferir sus ingresos", "PM"));
        addRegimen(new Regimen("621", "Incorporación Fiscal", "PF"));
        addRegimen(new Regimen("622", "Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras", "PF"));
        addRegimen(new Regimen("623", "Opcional para Grupos de Sociedades", "PM"));
        addRegimen(new Regimen("624", "Coordinados", "PM"));
        addRegimen(new Regimen("625", "Régimen de las Actividades Empresariales con ingresos a través de Plataformas Tecnológicas", "PF"));
        addRegimen(new Regimen("626", "Régimen Simplificado de Confianza", "PF"));
        
        // ================== USOS CFDI ==================
        addUso(new UsoCfdi("CP01", "Pagos"));
        addUso(new UsoCfdi("G01", "Adquisición de mercancías"));
        addUso(new UsoCfdi("G02", "Devoluciones, descuentos o bonificaciones"));
        addUso(new UsoCfdi("G03", "Gastos en general"));
        addUso(new UsoCfdi("I01", "Construcciones"));
        addUso(new UsoCfdi("I02", "Mobilario y equipo de oficina por inversiones"));
        addUso(new UsoCfdi("I03", "Equipo de transporte"));
        addUso(new UsoCfdi("I04", "Equipo de computo y accesorios"));
        addUso(new UsoCfdi("I05", "Dados, troqueles, moldes, matrices y herramental"));
        addUso(new UsoCfdi("I06", "Comunicaciones telefónicas"));
        addUso(new UsoCfdi("I07", "Comunicaciones satelitales"));
        addUso(new UsoCfdi("I08", "Otra maquinaria y equipo"));
        addUso(new UsoCfdi("S01", "Sin efectos fiscales"));
        addUso(new UsoCfdi("CN01", "Nómina"));
        addUso(new UsoCfdi("D01", "Honorarios médicos, dentales y gastos hospitalarios"));
        addUso(new UsoCfdi("D02", "Gastos médicos por incapacidad o discapacidad"));
        addUso(new UsoCfdi("D03", "Gastos funerarios"));
        addUso(new UsoCfdi("D04", "Donativos"));
        addUso(new UsoCfdi("D05", "Intereses reales efectivamente pagados por créditos hipotecarios (casa habitación)"));
        addUso(new UsoCfdi("D06", "Aportaciones voluntarias al SAR"));
        addUso(new UsoCfdi("D07", "Primas por seguros de gastos médicos"));
        addUso(new UsoCfdi("D08", "Gastos de transportación escolar obligatoria"));
        addUso(new UsoCfdi("D09", "Depósitos en cuentas para el ahorro, primas que tengan como base planes de pensiones"));
        addUso(new UsoCfdi("D10", "Pagos por servicios educativos (colegiaturas)"));
        // ============ COMPATIBILIDAD (EJEMPLOS) ============
        putCompat("601", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("603", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("605", "CN01","CP01","D01", "D02", "D03", "D04", "D05", "D06", "D07", "D08", "D09", "D10", "S01");
        putCompat("606", "CP01", "D01", "D02", "D03", "D04", "D05", "D06", "D07", "D08", "D09", "D10", "G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("607", "CP01","D01","D02","D03", "D04","D05","D06","D07","D08","D09","D10", "S01");
        putCompat("608", "CP01","D01","D02","D03", "D04","D05","D06","D07","D08","D09","D10", "S01");
        putCompat("610", "CP01","S01");
        putCompat("611", "CP01","D01","D02","D03"," D04","D05","D06","D07","D08","D09","D10", "S01");
        putCompat("612", "CP01","D01", "D02", "D03", "D04", "D05", "D06", "D07", "D08", "D09", "D10", "G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("614", "CP01","D01","D02","D03", "D04","D05","D06","D07","D08","D09","D10", "S01");
        putCompat("615", "CP01","D01","D02","D03", " D04","D05","D06","D07","D08","D09","D10", "S01");
        putCompat("616", "CP01","S01");
        putCompat("620", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "i05", "I06", "I07", "I08", "S01");
        putCompat("621", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "i05", "I06", "I07", "I08", "S01");
        putCompat("622", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "i05", "I06", "I07", "I08", "S01");
        putCompat("623", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("624", "CP01","G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("625", "CP01","D01", "D02", "D03", "D04", "D05", "D06", "D07", "D08", "D09", "D10", "G01", "G02", "G03", "I01", "I02", "I03", "I04", "I05", "I06", "I07", "I08", "S01");
        putCompat("626", "CP01","G01","G02","G03","I01","I02","I03","I04","I05","I06","I07","I08","S01");
    }

    private static void addRegimen(Regimen r) { REGIMENES.put(r.clave, r); }
    private static void addUso(UsoCfdi u)      { USOS.put(u.clave, u); }

    private static void putCompat(String regimen, String... usos) {
        List<String> list = new ArrayList<>();
        for (String u : usos) list.add(u);
        COMPATIBILIDAD.put(regimen, Collections.unmodifiableList(list));
    }

    // === APIs que usarán tus pantallas ===

    /** Regímenes por tipo de persona: persona = "PF" o "PM" */
    public static List<Regimen> listarRegimenesPorPersona(String persona) {
        List<Regimen> out = new ArrayList<>();
        for (Regimen r : REGIMENES.values()) {
            if (persona == null || persona.equals(r.persona)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Usos compatibles con un régimen (por clave de régimen). */
    public static List<UsoCfdi> listarUsosParaRegimen(String claveRegimen) {
        List<UsoCfdi> out = new ArrayList<>();
        if (claveRegimen == null) return out;
        List<String> clavesUsos = COMPATIBILIDAD.get(claveRegimen);
        if (clavesUsos == null) return out;
        for (String c : clavesUsos) {
            UsoCfdi u = USOS.get(c);
            if (u != null) out.add(u);
        }
        return out;
    }

    /** Texto completo para mostrar en reportes, tickets, etc. */
    public static String descripcionRegimen(String clave) {
        Regimen r = REGIMENES.get(clave);
        return (r == null) ? clave : r.clave + " - " + r.descripcion;
    }

    public static String descripcionUsoCfdi(String clave) {
        UsoCfdi u = USOS.get(clave);
        return (u == null) ? clave : u.clave + " - " + u.descripcion;
    }

    public static Regimen buscarRegimenPorClave(String clave) {
    if (clave == null) return null;
    String k = clave.trim();
    if (k.isEmpty()) return null;
    for (Regimen r : REGIMENES.values()) {   // usa tu lista real
        if (r.clave.equalsIgnoreCase(k)) return r;
    }
    return null;
}

public static UsoCfdi buscarUsoPorClave(String clave) {
    if (clave == null) return null;
    String k = clave.trim();
    if (k.isEmpty()) return null;
    for (UsoCfdi u : USOS.values()) {        // usa tu lista real
        if (u.clave.equalsIgnoreCase(k)) return u;
    }
    return null;
}

}
