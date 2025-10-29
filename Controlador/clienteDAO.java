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
}

