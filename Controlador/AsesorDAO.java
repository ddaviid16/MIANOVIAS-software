package Controlador;

import Conexion.Conecta;
import Modelo.Asesor;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AsesorDAO {

    // Normaliza tipo: si viene null o raro, lo deja en 'A'
    private String normalizarTipo(String t) {
        if (t == null) return "A";
        t = t.trim().toUpperCase();
        return switch (t) {
            case "A", "M", "MA" -> t;
            default -> "A";
        };
    }

    /** Números de empleados activos que cuentan como ASESORAS (A o MA). */
    public List<Integer> listarActivos() throws SQLException {
        String sql = "SELECT numero_empleado " +
                     "FROM Asesor " +
                     "WHERE status='A' AND tipo_empleado IN ('A','MA') " +
                     "ORDER BY numero_empleado";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Integer> lista = new ArrayList<>();
            while (rs.next()) lista.add(rs.getInt(1));
            return lista;
        }
    }

    /** Todos los empleados. */
    public List<Asesor> listarTodos() throws SQLException {
        String sql = "SELECT numero_empleado, nombre_completo, fecha_alta, fecha_baja, " +
                     "       tipo_empleado, status " +
                     "FROM Asesor " +
                     "ORDER BY status DESC, numero_empleado";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Asesor> lista = new ArrayList<>();
            while (rs.next()) {
                Asesor a = new Asesor();
                a.setNumeroEmpleado(rs.getInt("numero_empleado"));
                a.setNombreCompleto(rs.getString("nombre_completo"));
                Date fa = rs.getDate("fecha_alta");
                Date fb = rs.getDate("fecha_baja");
                a.setFechaAlta(fa == null ? null : fa.toLocalDate());
                a.setFechaBaja(fb == null ? null : fb.toLocalDate());
                a.setTipoEmpleado(rs.getString("tipo_empleado"));
                a.setStatus(rs.getString("status"));
                lista.add(a);
            }
            return lista;
        }
    }

    /** Activas que cuentan como ASESORAS (A o MA), para combos/listas. */
    public List<Asesor> listarActivosDetalle() throws SQLException {
        String sql = "SELECT numero_empleado, nombre_completo, tipo_empleado " +
                     "FROM Asesor " +
                     "WHERE status='A' AND tipo_empleado IN ('A','MA') " +
                     "ORDER BY nombre_completo";
        List<Asesor> out = new ArrayList<>();
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Asesor a = new Asesor();
                a.setNumeroEmpleado(rs.getInt("numero_empleado"));
                a.setNombreCompleto(rs.getString("nombre_completo"));
                a.setTipoEmpleado(rs.getString("tipo_empleado"));
                out.add(a);
            }
        }
        return out;
    }

    /** Modistas (M o MA), por si luego quieres el combo para pruebas. */
    public List<Asesor> listarModistasActivas() throws SQLException {
        String sql = "SELECT numero_empleado, nombre_completo, tipo_empleado " +
                     "FROM Asesor " +
                     "WHERE status='A' AND tipo_empleado IN ('M','MA') " +
                     "ORDER BY nombre_completo";
        List<Asesor> out = new ArrayList<>();
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Asesor a = new Asesor();
                a.setNumeroEmpleado(rs.getInt("numero_empleado"));
                a.setNombreCompleto(rs.getString("nombre_completo"));
                a.setTipoEmpleado(rs.getString("tipo_empleado"));
                out.add(a);
            }
        }
        return out;
    }

    public boolean existeNumero(int numero) throws SQLException {
        String sql = "SELECT 1 FROM Asesor WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insertar(Asesor a) throws SQLException {
        String sql = "INSERT INTO Asesor " +
                     " (numero_empleado, nombre_completo, fecha_alta, tipo_empleado, status) " +
                     "VALUES (?,?,?,?, 'A')";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, a.getNumeroEmpleado());
            ps.setString(2, a.getNombreCompleto());

            LocalDate fa = (a.getFechaAlta() == null) ? LocalDate.now() : a.getFechaAlta();
            ps.setDate(3, Date.valueOf(fa));

            ps.setString(4, normalizarTipo(a.getTipoEmpleado()));

            ps.executeUpdate();
        }
    }

    /** Desactiva con una fecha de baja explícita (si es null, usa hoy). */
    public void desactivar(int numero, LocalDate fechaBaja) throws SQLException {
        String sql = "UPDATE Asesor SET status='C', fecha_baja=? WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            LocalDate fb = (fechaBaja == null) ? LocalDate.now() : fechaBaja;
            ps.setDate(1, Date.valueOf(fb));
            ps.setInt(2, numero);
            ps.executeUpdate();
        }
    }

    /** Mantengo la firma anterior por compatibilidad (usa hoy). */
    public void desactivar(int numero) throws SQLException {
        desactivar(numero, null);
    }

    public void reactivar(int numero) throws SQLException {
        String sql = "UPDATE Asesor SET status='A', fecha_baja=NULL WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numero);
            ps.executeUpdate();
        }
    }

    /** Buscar un empleado por número. */
    public Asesor buscarPorNumero(int numero) throws SQLException {
        String sql = "SELECT numero_empleado, nombre_completo, fecha_alta, fecha_baja, " +
                     "       tipo_empleado, status " +
                     "FROM Asesor WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Asesor a = new Asesor();
                a.setNumeroEmpleado(rs.getInt("numero_empleado"));
                a.setNombreCompleto(rs.getString("nombre_completo"));
                Date fa = rs.getDate("fecha_alta");
                Date fb = rs.getDate("fecha_baja");
                a.setFechaAlta(fa == null ? null : fa.toLocalDate());
                a.setFechaBaja(fb == null ? null : fb.toLocalDate());
                a.setTipoEmpleado(rs.getString("tipo_empleado"));
                a.setStatus(rs.getString("status"));
                return a;
            }
        }
    }

    /** Actualiza nombre, fecha de alta y tipo. No toca status ni fecha_baja. */
    public void actualizarBasico(Asesor a) throws SQLException {
    String sql = "UPDATE Asesor " +
                 "SET nombre_completo=?, fecha_alta=?, tipo_empleado=?, status=?, fecha_baja=? " +
                 "WHERE numero_empleado=?";
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        // 1) Nombre
        ps.setString(1, a.getNombreCompleto());

        // 2) Fecha de alta (si viene null, uso hoy para no dejarla vacía)
        LocalDate fa = (a.getFechaAlta() == null) ? LocalDate.now() : a.getFechaAlta();
        ps.setDate(2, Date.valueOf(fa));

        // 3) Tipo de empleado (A / M / MA)
        ps.setString(3, normalizarTipo(a.getTipoEmpleado()));

        // 4) Status: solo A o C, cualquier otra cosa la normalizo a A
        String st = a.getStatus();
        if (st == null || st.isBlank()) st = "A";
        st = st.trim().toUpperCase();
        if (!"C".equals(st)) st = "A";   // si no es C, lo dejo en A
        ps.setString(4, st);

        // 5) Fecha de baja:
        //    - si status = A → siempre NULL en BD
        //    - si status = C → uso la fecha que venga; si viene null, NO pongo nada (puedes forzar hoy si quieres)
        LocalDate fb = a.getFechaBaja();
        if (!"C".equals(st)) {
            // Activo: fecha_baja debe quedar NULL
            ps.setNull(5, Types.DATE);
        } else {
            if (fb != null) {
                ps.setDate(5, Date.valueOf(fb));
            } else {
                // si quieres que, si no capturan fecha, se guarde hoy, descomenta la siguiente línea:
                // ps.setDate(5, Date.valueOf(LocalDate.now()));
                ps.setNull(5, Types.DATE);
            }
        }

        // 6) WHERE
        ps.setInt(6, a.getNumeroEmpleado());

        ps.executeUpdate();
    }
}
}