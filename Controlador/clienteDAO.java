package Controlador;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.sql.*;
import Conexion.Conecta;
import Modelo.ClienteResumen;
import Modelo.cliente;
import java.util.ArrayList;
import java.util.List;
import Modelo.CitaDia;


public class clienteDAO {

    private static final String INSERT_SQL =
            "INSERT INTO Clientes(" +
            "telefono1, telefono2, parentesco_tel2, nombre, apellido_paterno, apellido_materno, edad, " +
            "como_se_entero, fecha_evento, lugar_evento, fecha_prueba1, fecha_prueba2, fecha_entrega, " +
            "busto, cintura, cadera, status, situacion_evento) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

public boolean crear(cliente c) throws SQLException {
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(INSERT_SQL)) {

        ps.setString(1,  c.getTelefono1());
        ps.setString(2,  vacioANull(c.getTelefono2()));
        ps.setString(3,  vacioANull(c.getParentescoTel2()));  // NUEVO
        ps.setString(4,  c.getNombre());
        ps.setString(5,  vacioANull(c.getApellidoPaterno()));
        ps.setString(6,  vacioANull(c.getApellidoMaterno()));

        if (c.getEdad() == null) ps.setNull(7, java.sql.Types.INTEGER);
        else ps.setInt(7, c.getEdad());

        ps.setString(8,  vacioANull(c.getComoSeEntero())); // ENUM
        ps.setString(9,  vacioANull(c.getFechaEvento()));  // YYYY-MM-DD
        ps.setString(10, vacioANull(c.getLugarEvento()));  // ENUM
        ps.setString(11, vacioANull(c.getFechaPrueba1()));
        ps.setString(12, vacioANull(c.getFechaPrueba2()));
        ps.setString(13, vacioANull(c.getFechaEntrega()));

        if (c.getBusto() == null)   ps.setNull(14, java.sql.Types.DECIMAL);
        else ps.setDouble(14, c.getBusto());

        if (c.getCintura() == null) ps.setNull(15, java.sql.Types.DECIMAL);
        else ps.setDouble(15, c.getCintura());

        if (c.getCadera() == null)  ps.setNull(16, java.sql.Types.DECIMAL);
        else ps.setDouble(16, c.getCadera());

        ps.setString(17, (c.getStatus()==null || c.getStatus().isBlank()) ? "A" : c.getStatus());
        ps.setString(18, (c.getSituacionEvento()==null || c.getSituacionEvento().isBlank())
                            ? "NORMAL" : c.getSituacionEvento());

        return ps.executeUpdate() == 1;
    }
}

    private String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
    /** Devuelve un resumen del cliente por teléfono1 o null si no existe. */
public ClienteResumen buscarResumenPorTelefono(String telefono1) throws SQLException {
    String sql =
        "SELECT telefono1, telefono2, parentesco_tel2, " +
        "       nombre, apellido_paterno, apellido_materno, " +
        "       fecha_evento, " +
        "       fecha_cita1, hora_cita1, asesora_cita1, " +
        "       fecha_cita2, hora_cita2, asesora_cita2, " +
        "       fecha_prueba1, hora_prueba1, modista_prueba1, " +
        "       fecha_prueba2, hora_prueba2, modista_prueba2, " +
        "       fecha_entrega, hora_entrega, asesora_entrega " +
        "FROM Clientes WHERE telefono1 = ?";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setString(1, telefono1);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;

            ClienteResumen cr = new ClienteResumen();
            cr.setTelefono1(rs.getString("telefono1"));
            cr.setTelefono2(rs.getString("telefono2"));
            cr.setParentescoTel2(rs.getString("parentesco_tel2"));

            String nombre = n(rs.getString("nombre"));
            String apPat  = n(rs.getString("apellido_paterno"));
            String apMat  = n(rs.getString("apellido_materno"));
            String full   = (nombre + " " + apPat + " " + apMat)
                              .trim().replaceAll("\\s+"," ");
            cr.setNombreCompleto(full.isBlank() ? null : full);

            // Evento
            cr.setFechaEvento(toLocal(rs.getDate("fecha_evento")));

            // Cita 1
            cr.setFechaCita1(toLocal(rs.getDate("fecha_cita1")));
            cr.setHoraCita1(rs.getString("hora_cita1"));
            cr.setAsesoraCita1(rs.getString("asesora_cita1"));

            // Cita 2
            cr.setFechaCita2(toLocal(rs.getDate("fecha_cita2")));
            cr.setHoraCita2(rs.getString("hora_cita2"));
            cr.setAsesoraCita2(rs.getString("asesora_cita2"));

            // Prueba 1
            cr.setFechaPrueba1(toLocal(rs.getDate("fecha_prueba1")));
            cr.setHoraPrueba1(rs.getString("hora_prueba1"));
            cr.setModistaPrueba1(rs.getString("modista_prueba1"));

            // Prueba 2
            cr.setFechaPrueba2(toLocal(rs.getDate("fecha_prueba2")));
            cr.setHoraPrueba2(rs.getString("hora_prueba2"));
            cr.setModistaPrueba2(rs.getString("modista_prueba2"));

            // Entrega
            cr.setFechaEntrega(toLocal(rs.getDate("fecha_entrega")));
            cr.setHoraEntrega(rs.getString("hora_entrega"));
            cr.setAsesoraEntrega(rs.getString("asesora_entrega"));

            return cr;
        }
    }
}
    /**
     * Actualiza TODA la info de citas del cliente identificado por telefono1.
     *
     * - Guarda fechas de cita1 / cita2 / pruebas / entrega
     * - Guarda horas (HH:mm) y nombres de asesora/modista como texto
     *
     * Las columnas usadas son:
     *   fecha_cita1, hora_cita1, asesora_cita1,
     *   fecha_cita2, hora_cita2, asesora_cita2,
     *   fecha_prueba1, hora_prueba1, modista_prueba1,
     *   fecha_prueba2, hora_prueba2, modista_prueba2,
     *   fecha_entrega, hora_entrega, asesora_entrega
     */
    public boolean actualizarCitasCliente(
            String telefono1,
            LocalDate fechaCita1, String horaCita1, String asesoraCita1,
            LocalDate fechaCita2, String horaCita2, String asesoraCita2,
            LocalDate fechaPrueba1, String horaPrueba1, String modistaPrueba1,
            LocalDate fechaPrueba2, String horaPrueba2, String modistaPrueba2,
            LocalDate fechaEntrega, String horaEntrega, String asesoraEntrega
    ) throws SQLException {

        String sql =
            "UPDATE Clientes SET " +
            "fecha_cita1 = ?, hora_cita1 = ?, asesora_cita1 = ?, " +
            "fecha_cita2 = ?, hora_cita2 = ?, asesora_cita2 = ?, " +
            "fecha_prueba1 = ?, hora_prueba1 = ?, modista_prueba1 = ?, " +
            "fecha_prueba2 = ?, hora_prueba2 = ?, modista_prueba2 = ?, " +
            "fecha_entrega = ?, hora_entrega = ?, asesora_entrega = ? " +
            "WHERE telefono1 = ?";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            setDateOrNull(ps, 1,  fechaCita1);
            setStringOrNull(ps, 2,  horaCita1);
            setStringOrNull(ps, 3,  asesoraCita1);

            setDateOrNull(ps, 4,  fechaCita2);
            setStringOrNull(ps, 5,  horaCita2);
            setStringOrNull(ps, 6,  asesoraCita2);

            setDateOrNull(ps, 7,  fechaPrueba1);
            setStringOrNull(ps, 8,  horaPrueba1);
            setStringOrNull(ps, 9,  modistaPrueba1);

            setDateOrNull(ps, 10, fechaPrueba2);
            setStringOrNull(ps, 11, horaPrueba2);
            setStringOrNull(ps, 12, modistaPrueba2);

            setDateOrNull(ps, 13, fechaEntrega);
            setStringOrNull(ps, 14, horaEntrega);
            setStringOrNull(ps, 15, asesoraEntrega);

            ps.setString(16, telefono1);

            return ps.executeUpdate() >= 1;
        }
    }

    // Helpers internos

    private void setDateOrNull(PreparedStatement ps, int idx, LocalDate d) throws SQLException {
        if (d == null) ps.setNull(idx, java.sql.Types.DATE);
        else ps.setDate(idx, java.sql.Date.valueOf(d));
    }

    private void setStringOrNull(PreparedStatement ps, int idx, String s) throws SQLException {
        if (s == null || s.isBlank()) ps.setNull(idx, java.sql.Types.VARCHAR);
        else ps.setString(idx, s);
    }

// Devuelve todas las columnas de la tabla Clientes en un mapa ordenado (campo -> valor)
public Map<String, String> detalleGenericoPorTelefono(String tel) throws SQLException {
    final String table = "Clientes";
    final String[] candidates = {"telefono", "telefono1", "tel", "telefono_cliente"};

    try (Connection cn = Conecta.getConnection()) {
        // 1) Descubrir qué columnas existen realmente
        java.util.Set<String> cols = new java.util.HashSet<>();
        try (PreparedStatement ps = cn.prepareStatement("SELECT * FROM " + table + " LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                cols.add(md.getColumnLabel(i).toLowerCase());
            }
        }

        // 2) Armar WHERE con todas las columnas telefónicas que existan
        java.util.List<String> phoneCols = new java.util.ArrayList<>();
        for (String c : candidates) if (cols.contains(c.toLowerCase())) phoneCols.add(c);
        if (phoneCols.isEmpty()) {
            // No hay columnas telefónicas conocidas: devuelve null como "no encontrado"
            return null;
        }
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < phoneCols.size(); i++) {
            if (i > 0) where.append(" OR ");
            where.append(phoneCols.get(i)).append("=?");
        }

        // 3) Consultar el registro por teléfono
        String sql = "SELECT * FROM " + table + " WHERE " + where + " LIMIT 1";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 0; i < phoneCols.size(); i++) {
                ps.setString(i + 1, tel);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                // 4) Volcar TODAS las columnas (no sólo las telefónicas)
                Map<String, String> out = new LinkedHashMap<>();
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String col = md.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    out.put(col, val == null ? "" : String.valueOf(val));
                }
                return out;
            }
        }
    }
}
        /** Recoge todas las citas/pruebas/entregas de un día. */
    public List<CitaDia> listarAgendaPorDia(java.time.LocalDate fecha) throws SQLException {
        String sql =
            "SELECT telefono1, nombre, apellido_paterno, apellido_materno, " +
            "       fecha_cita1, hora_cita1, asesora_cita1, " +
            "       fecha_cita2, hora_cita2, asesora_cita2, " +
            "       fecha_prueba1, hora_prueba1, modista_prueba1, " +
            "       fecha_prueba2, hora_prueba2, modista_prueba2, " +
            "       fecha_entrega, hora_entrega, asesora_entrega " +
            "FROM Clientes " +
            "WHERE status = 'A' AND (" +
            "      fecha_cita1   = ? OR " +
            "      fecha_cita2   = ? OR " +
            "      fecha_prueba1 = ? OR " +
            "      fecha_prueba2 = ? OR " +
            "      fecha_entrega = ? ) " +
            "ORDER BY telefono1";

        List<CitaDia> out = new ArrayList<>();

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            java.sql.Date d = java.sql.Date.valueOf(fecha);
            for (int i = 1; i <= 5; i++) {
                ps.setDate(i, d);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tel = rs.getString("telefono1");
                    String nom = (n(rs.getString("nombre")) + " "
                                 + n(rs.getString("apellido_paterno")) + " "
                                 + n(rs.getString("apellido_materno")))
                                 .trim().replaceAll("\\s+", " ");

                    // Cita 1
                    java.sql.Date fc1 = rs.getDate("fecha_cita1");
                    if (fc1 != null && fc1.toLocalDate().equals(fecha)) {
                        CitaDia c = new CitaDia();
                        c.setTelefono1(tel);
                        c.setNombreCliente(nom);
                        c.setFecha(fecha);
                        c.setHora(formatHora(rs.getString("hora_cita1")));
                        c.setTipo("Cita 1");
                        c.setResponsable(rs.getString("asesora_cita1"));
                        out.add(c);
                    }

                    // Cita 2
                    java.sql.Date fc2 = rs.getDate("fecha_cita2");
                    if (fc2 != null && fc2.toLocalDate().equals(fecha)) {
                        CitaDia c = new CitaDia();
                        c.setTelefono1(tel);
                        c.setNombreCliente(nom);
                        c.setFecha(fecha);
                        c.setHora(formatHora(rs.getString("hora_cita2")));
                        c.setTipo("Cita 2");
                        c.setResponsable(rs.getString("asesora_cita2"));
                        out.add(c);
                    }

                    // Prueba 1
                    java.sql.Date fp1 = rs.getDate("fecha_prueba1");
                    if (fp1 != null && fp1.toLocalDate().equals(fecha)) {
                        CitaDia c = new CitaDia();
                        c.setTelefono1(tel);
                        c.setNombreCliente(nom);
                        c.setFecha(fecha);
                        c.setHora(formatHora(rs.getString("hora_prueba1")));
                        c.setTipo("Prueba 1");
                        c.setResponsable(rs.getString("modista_prueba1"));
                        out.add(c);
                    }

                    // Prueba 2
                    java.sql.Date fp2 = rs.getDate("fecha_prueba2");
                    if (fp2 != null && fp2.toLocalDate().equals(fecha)) {
                        CitaDia c = new CitaDia();
                        c.setTelefono1(tel);
                        c.setNombreCliente(nom);
                        c.setFecha(fecha);
                        c.setHora(formatHora(rs.getString("hora_prueba2")));
                        c.setTipo("Prueba 2");
                        c.setResponsable(rs.getString("modista_prueba2"));
                        out.add(c);
                    }

                    // Entrega
                    java.sql.Date fe = rs.getDate("fecha_entrega");
                    if (fe != null && fe.toLocalDate().equals(fecha)) {
                        CitaDia c = new CitaDia();
                        c.setTelefono1(tel);
                        c.setNombreCliente(nom);
                        c.setFecha(fecha);
                        c.setHora(formatHora(rs.getString("hora_entrega")));
                        c.setTipo("Entrega");
                        c.setResponsable(rs.getString("asesora_entrega"));
                        out.add(c);
                    }
                }
            }
        }

        // Ordenar por hora (nulls al final), luego tipo, luego nombre
        out.sort((a, b) -> {
            String ha = a.getHora();
            String hb = b.getHora();
            if (ha == null || ha.isBlank()) {
                if (hb == null || hb.isBlank()) return 0;
                return 1; // a al final
            }
            if (hb == null || hb.isBlank()) return -1;
            int cmp = ha.compareTo(hb);
            if (cmp != 0) return cmp;
            cmp = a.getTipo().compareToIgnoreCase(b.getTipo());
            if (cmp != 0) return cmp;
            return a.getNombreCliente().compareToIgnoreCase(b.getNombreCliente());
        });

        return out;
    }

    /** Formatea TIME/HH:mm:ss a "HH:mm". */
    private String formatHora(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 5) return s.substring(0, 5);
        return s;
    }


    private String n(String s) { return s == null ? "" : s.trim(); }
    private LocalDate toLocal(Date d) { return d == null ? null : d.toLocalDate(); }

        /** Convierte Date -> "yyyy-MM-dd" o null. */
    private String toStr(java.sql.Date d) {
        return (d == null) ? null : d.toLocalDate().toString();
    }

    /** Devuelve el cliente COMPLETO por telefono1, o null si no existe. */
    public cliente buscarClientePorTelefono1(String telefono1) throws SQLException {
        String sql =
            "SELECT telefono1, telefono2, parentesco_tel2,  nombre, apellido_paterno, apellido_materno, edad, " +
            "       como_se_entero, fecha_evento, lugar_evento, fecha_prueba1, fecha_prueba2, fecha_entrega, " +
            "       busto, cintura, cadera, status, situacion_evento " +
            "FROM Clientes WHERE telefono1 = ?";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, telefono1);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                cliente c = new cliente();
                c.setTelefono1(rs.getString("telefono1"));
                c.setTelefono2(rs.getString("telefono2"));
                c.setParentescoTel2(rs.getString("parentesco_tel2"));
                c.setNombre(rs.getString("nombre"));
                c.setApellidoPaterno(rs.getString("apellido_paterno"));
                c.setApellidoMaterno(rs.getString("apellido_materno"));

                int edad = rs.getInt("edad");
                c.setEdad(rs.wasNull() ? null : edad);

                c.setComoSeEntero(rs.getString("como_se_entero"));
                c.setFechaEvento(toStr(rs.getDate("fecha_evento")));
                c.setLugarEvento(rs.getString("lugar_evento"));
                c.setFechaPrueba1(toStr(rs.getDate("fecha_prueba1")));
                c.setFechaPrueba2(toStr(rs.getDate("fecha_prueba2")));
                c.setFechaEntrega(toStr(rs.getDate("fecha_entrega")));

                java.math.BigDecimal bd;

                bd = rs.getBigDecimal("busto");
                c.setBusto(bd == null ? null : bd.doubleValue());

                bd = rs.getBigDecimal("cintura");
                c.setCintura(bd == null ? null : bd.doubleValue());

                bd = rs.getBigDecimal("cadera");
                c.setCadera(bd == null ? null : bd.doubleValue());

                c.setStatus(rs.getString("status"));
                c.setSituacionEvento(rs.getString("situacion_evento"));

                return c;
            }
        }
    }

    /** Actualiza los datos del cliente (no cambia telefono1, que es PK). */
    public boolean actualizar(cliente c) throws SQLException {
        String sql =
            "UPDATE Clientes SET " +
            "telefono2 = ?, parentesco_tel2 = ?, nombre = ?, apellido_paterno = ?, apellido_materno = ?, edad = ?, " +
            "como_se_entero = ?, fecha_evento = ?, lugar_evento = ?, " +
            "fecha_prueba1 = ?, fecha_prueba2 = ?, fecha_entrega = ?, " +
            "busto = ?, cintura = ?, cadera = ?, status = ?, situacion_evento = ? " +
            "WHERE telefono1 = ?";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1,  vacioANull(c.getTelefono2()));
            ps.setString(2,  vacioANull(c.getParentescoTel2()));
            ps.setString(3,  c.getNombre());
            ps.setString(4,  vacioANull(c.getApellidoPaterno()));
            ps.setString(5,  vacioANull(c.getApellidoMaterno()));

            if (c.getEdad() == null) ps.setNull(6, java.sql.Types.INTEGER);
            else ps.setInt(6, c.getEdad());

            ps.setString(7,  vacioANull(c.getComoSeEntero()));
            ps.setString(8,  vacioANull(c.getFechaEvento()));
            ps.setString(9,  vacioANull(c.getLugarEvento()));
            ps.setString(10,  vacioANull(c.getFechaPrueba1()));
            ps.setString(11, vacioANull(c.getFechaPrueba2()));
            ps.setString(12, vacioANull(c.getFechaEntrega()));

            if (c.getBusto() == null)   ps.setNull(13, java.sql.Types.DECIMAL);
            else ps.setDouble(13, c.getBusto());

            if (c.getCintura() == null) ps.setNull(14, java.sql.Types.DECIMAL);
            else ps.setDouble(14, c.getCintura());
            if (c.getCadera() == null)  ps.setNull(15, java.sql.Types.DECIMAL);
            else ps.setDouble(15, c.getCadera());

            ps.setString(16, (c.getStatus()==null || c.getStatus().isBlank()) ? "A" : c.getStatus());
            ps.setString(17, (c.getSituacionEvento()==null || c.getSituacionEvento().isBlank())
                                ? "NORMAL" : c.getSituacionEvento());

            ps.setString(18, c.getTelefono1());   // WHERE

            return ps.executeUpdate() >= 1;
        }
    }
    // ====== AGENDA DIARIA (PRUEBA1, PRUEBA2, ENTREGA) ======

public static class AgendaItem {
    public String telefono1;
    public String nombreCompleto;
    public LocalDate fechaEvento;
    public String concepto;        // "Prueba 1", "Prueba 2", "Entrega"
    public String hora;            // "HH:mm"
    public String asesorModista;   // modista o asesora
    public String observacion;
}

/**
 * Devuelve todas las citas de PRUEBA1 / PRUEBA2 / ENTREGA para un día dado.
 * Solo usa la tabla Clientes y las columnas:
 *  - fecha_prueba1, hora_prueba1, modista_prueba1
 *  - fecha_prueba2, hora_prueba2, modista_prueba2
 *  - fecha_entrega, hora_entrega, asesora_entrega
 */
public java.util.List<AgendaItem> listarAgendaDia(LocalDate dia) throws SQLException {
    java.util.List<AgendaItem> out = new java.util.ArrayList<>();
    if (dia == null) return out;

    String sql =
        "SELECT telefono1, nombre, apellido_paterno, apellido_materno, fecha_evento, " +
        "       fecha_cita1,   hora_cita1,   asesora_cita1,   obs_cita1, " +      // CAMBIAR nombres si hace falta
        "       fecha_cita2,   hora_cita2,   asesora_cita2,   obs_cita2, " +
        "       fecha_prueba1, hora_prueba1, modista_prueba1, obs_prueba1, " +
        "       fecha_prueba2, hora_prueba2, modista_prueba2, obs_prueba2, " +
        "       fecha_entrega, hora_entrega, asesora_entrega, obs_entrega " +
        "FROM Clientes " +
        "WHERE status = 'A' " +
        "  AND (situacion_evento IS NULL OR situacion_evento = 'NORMAL') " +
        "  AND (fecha_cita1   = ? " +
        "       OR fecha_cita2   = ? " +
        "       OR fecha_prueba1 = ? " +
        "       OR fecha_prueba2 = ? " +
        "       OR fecha_entrega = ?)";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        java.sql.Date d = java.sql.Date.valueOf(dia);
        ps.setDate(1, d);
        ps.setDate(2, d);
        ps.setDate(3, d);
        ps.setDate(4, d);
        ps.setDate(5, d);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String tel = rs.getString("telefono1");
                String nombre = n(rs.getString("nombre"));
                String apPat  = n(rs.getString("apellido_paterno"));
                String apMat  = n(rs.getString("apellido_materno"));
                String full   = (nombre + " " + apPat + " " + apMat)
                                   .trim().replaceAll("\\s+"," ");
                LocalDate fechaEv = toLocal(rs.getDate("fecha_evento"));

                // ===== CITA 1 =====
                java.sql.Date fc1 = rs.getDate("fecha_cita1");
                if (fc1 != null && fc1.toLocalDate().equals(dia)) {
                    AgendaItem it = new AgendaItem();
                    it.telefono1      = tel;
                    it.nombreCompleto = full;
                    it.fechaEvento    = fechaEv;
                    it.concepto       = "Cita 1";
                    it.hora           = formatHora(rs.getString("hora_cita1"));
                    it.asesorModista  = rs.getString("asesora_cita1");
                    it.observacion    = rs.getString("obs_cita1");   // <---
                    out.add(it);
                }

                // ===== CITA 2 =====
                java.sql.Date fc2 = rs.getDate("fecha_cita2");
                if (fc2 != null && fc2.toLocalDate().equals(dia)) {
                    AgendaItem it = new AgendaItem();
                    it.telefono1      = tel;
                    it.nombreCompleto = full;
                    it.fechaEvento    = fechaEv;
                    it.concepto       = "Cita 2";
                    it.hora           = formatHora(rs.getString("hora_cita2"));
                    it.asesorModista  = rs.getString("asesora_cita2");
                    it.observacion    = rs.getString("obs_cita2");
                    out.add(it);
                }

                // ===== PRUEBA 1 =====
                java.sql.Date fp1 = rs.getDate("fecha_prueba1");
                if (fp1 != null && fp1.toLocalDate().equals(dia)) {
                    AgendaItem it = new AgendaItem();
                    it.telefono1      = tel;
                    it.nombreCompleto = full;
                    it.fechaEvento    = fechaEv;
                    it.concepto       = "Prueba 1";
                    it.hora           = formatHora(rs.getString("hora_prueba1"));
                    it.asesorModista  = rs.getString("modista_prueba1");
                    it.observacion    = rs.getString("obs_prueba1");
                    out.add(it);
                }

                // ===== PRUEBA 2 =====
                java.sql.Date fp2 = rs.getDate("fecha_prueba2");
                if (fp2 != null && fp2.toLocalDate().equals(dia)) {
                    AgendaItem it = new AgendaItem();
                    it.telefono1      = tel;
                    it.nombreCompleto = full;
                    it.fechaEvento    = fechaEv;
                    it.concepto       = "Prueba 2";
                    it.hora           = formatHora(rs.getString("hora_prueba2"));
                    it.asesorModista  = rs.getString("modista_prueba2");
                    it.observacion    = rs.getString("obs_prueba2");
                    out.add(it);
                }

                // ===== ENTREGA =====
                java.sql.Date fe = rs.getDate("fecha_entrega");
                if (fe != null && fe.toLocalDate().equals(dia)) {
                    AgendaItem it = new AgendaItem();
                    it.telefono1      = tel;
                    it.nombreCompleto = full;
                    it.fechaEvento    = fechaEv;
                    it.concepto       = "Entrega";
                    it.hora           = formatHora(rs.getString("hora_entrega"));
                    it.asesorModista  = rs.getString("asesora_entrega");
                    it.observacion    = rs.getString("obs_entrega");
                    out.add(it);
                }
            }
        }
    }

    return out;
}
/**
 * Actualiza solo la observación de una cita/prueba/entrega del cliente.
 * Usa el concepto para decidir qué columna tocar.
 */
public void actualizarObservacionCita(String telefono1,
                                      String concepto,
                                      String observacion) throws SQLException {

    if (telefono1 == null || concepto == null) return;

    // Mapea el concepto a la columna de BD
    String col;
    switch (concepto) {
        case "Cita 1"   -> col = "obs_cita1";
        case "Cita 2"   -> col = "obs_cita2";
        case "Prueba 1" -> col = "obs_prueba1";
        case "Prueba 2" -> col = "obs_prueba2";
        case "Entrega"  -> col = "obs_entrega";
        default         -> { return; } // concepto raro, mejor no tocar
    }

    String sql = "UPDATE Clientes SET " + col + " = ? WHERE telefono1 = ?";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        if (observacion == null || observacion.trim().isEmpty()) {
            ps.setNull(1, Types.VARCHAR);
        } else {
            ps.setString(1, observacion.trim());
        }
        ps.setString(2, telefono1);
        ps.executeUpdate();
    }
}

}