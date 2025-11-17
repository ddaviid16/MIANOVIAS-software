package Controlador;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.sql.*;
import Conexion.Conecta;
import Modelo.ClienteResumen;
import Modelo.cliente;

public class clienteDAO {

    private static final String INSERT_SQL =
        "INSERT INTO Clientes(telefono1, telefono2, nombre, apellido_paterno, apellido_materno, edad, " +
        "como_se_entero, fecha_evento, lugar_evento, fecha_prueba1, fecha_prueba2, fecha_entrega, " +
        "busto, cintura, cadera, status, situacion_evento) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public boolean crear(cliente c) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(INSERT_SQL)) {

            ps.setString(1,  c.getTelefono1());
            ps.setString(2,  vacioANull(c.getTelefono2()));
            ps.setString(3,  c.getNombre());
            ps.setString(4,  vacioANull(c.getApellidoPaterno()));
            ps.setString(5,  vacioANull(c.getApellidoMaterno()));

            if (c.getEdad() == null) ps.setNull(6, java.sql.Types.INTEGER);
            else ps.setInt(6, c.getEdad());

            ps.setString(7,  vacioANull(c.getComoSeEntero())); // debe coincidir con ENUM
            ps.setString(8,  vacioANull(c.getFechaEvento()));  // YYYY-MM-DD
            ps.setString(9,  vacioANull(c.getLugarEvento()));  // debe coincidir con ENUM
            ps.setString(10, vacioANull(c.getFechaPrueba1()));
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

            return ps.executeUpdate() == 1;
        }
    }

    private String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
    /** Devuelve un resumen del cliente por teléfono1 o null si no existe. */
    public ClienteResumen buscarResumenPorTelefono(String telefono1) throws SQLException {
        String sql = "SELECT telefono1, telefono2, nombre, apellido_paterno, apellido_materno, " +
                     "fecha_evento, fecha_prueba1, fecha_prueba2, fecha_entrega " +
                     "FROM Clientes WHERE telefono1 = ?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, telefono1);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ClienteResumen cr = new ClienteResumen();
                cr.setTelefono1(rs.getString("telefono1"));
                cr.setTelefono2(rs.getString("telefono2"));
                String nombre = n(rs.getString("nombre"));
                String apPat  = n(rs.getString("apellido_paterno"));
                String apMat  = n(rs.getString("apellido_materno"));
                String full   = (nombre + " " + apPat + " " + apMat).trim().replaceAll("\\s+"," ");
                cr.setNombreCompleto(full.isBlank() ? null : full);
                cr.setFechaEvento(toLocal(rs.getDate("fecha_evento")));
                cr.setFechaPrueba1(toLocal(rs.getDate("fecha_prueba1")));
                cr.setFechaPrueba2(toLocal(rs.getDate("fecha_prueba2")));
                cr.setFechaEntrega(toLocal(rs.getDate("fecha_entrega")));
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

    private String n(String s) { return s == null ? "" : s.trim(); }
    private LocalDate toLocal(Date d) { return d == null ? null : d.toLocalDate(); }

        /** Convierte Date -> "yyyy-MM-dd" o null. */
    private String toStr(java.sql.Date d) {
        return (d == null) ? null : d.toLocalDate().toString();
    }

    /** Devuelve el cliente COMPLETO por telefono1, o null si no existe. */
    public cliente buscarClientePorTelefono1(String telefono1) throws SQLException {
        String sql =
            "SELECT telefono1, telefono2, nombre, apellido_paterno, apellido_materno, edad, " +
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
            "telefono2 = ?, nombre = ?, apellido_paterno = ?, apellido_materno = ?, edad = ?, " +
            "como_se_entero = ?, fecha_evento = ?, lugar_evento = ?, " +
            "fecha_prueba1 = ?, fecha_prueba2 = ?, fecha_entrega = ?, " +
            "busto = ?, cintura = ?, cadera = ?, status = ?, situacion_evento = ? " +
            "WHERE telefono1 = ?";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1,  vacioANull(c.getTelefono2()));
            ps.setString(2,  c.getNombre());
            ps.setString(3,  vacioANull(c.getApellidoPaterno()));
            ps.setString(4,  vacioANull(c.getApellidoMaterno()));

            if (c.getEdad() == null) ps.setNull(5, java.sql.Types.INTEGER);
            else ps.setInt(5, c.getEdad());

            ps.setString(6,  vacioANull(c.getComoSeEntero()));
            ps.setString(7,  vacioANull(c.getFechaEvento()));
            ps.setString(8,  vacioANull(c.getLugarEvento()));
            ps.setString(9,  vacioANull(c.getFechaPrueba1()));
            ps.setString(10, vacioANull(c.getFechaPrueba2()));
            ps.setString(11, vacioANull(c.getFechaEntrega()));

            if (c.getBusto() == null)   ps.setNull(12, java.sql.Types.DECIMAL);
            else ps.setDouble(12, c.getBusto());

            if (c.getCintura() == null) ps.setNull(13, java.sql.Types.DECIMAL);
            else ps.setDouble(13, c.getCintura());

            if (c.getCadera() == null)  ps.setNull(14, java.sql.Types.DECIMAL);
            else ps.setDouble(14, c.getCadera());

            ps.setString(15, (c.getStatus()==null || c.getStatus().isBlank()) ? "A" : c.getStatus());
            ps.setString(16, (c.getSituacionEvento()==null || c.getSituacionEvento().isBlank())
                                ? "NORMAL" : c.getSituacionEvento());

            ps.setString(17, c.getTelefono1());   // WHERE

            return ps.executeUpdate() >= 1;
        }
    }

}

