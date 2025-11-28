package Controlador;

import Conexion.Conecta;
import Modelo.Asesor;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
                     "       tipo_empleado, status, permiso_cancelar_nota " +
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
                a.setPermisoCancelaNota(rs.getBoolean("permiso_cancelar_nota"));
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
                     "       tipo_empleado, status, permiso_cancelar_nota " +
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
                a.setPermisoCancelaNota(rs.getBoolean("permiso_cancelar_nota"));
                return a;
            }
        }
    }

    /** Actualiza nombre, fechas, tipo, status, baja y permiso. */
    public void actualizarBasico(Asesor a) throws SQLException {
        String sql = "UPDATE Asesor " +
                     "SET nombre_completo=?, fecha_alta=?, tipo_empleado=?, status=?, fecha_baja=?, " +
                     "    permiso_cancelar_nota=? " +
                     "WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            // 1) Nombre
            ps.setString(1, a.getNombreCompleto());

            // 2) Fecha de alta
            LocalDate fa = (a.getFechaAlta() == null) ? LocalDate.now() : a.getFechaAlta();
            ps.setDate(2, Date.valueOf(fa));

            // 3) Tipo
            ps.setString(3, normalizarTipo(a.getTipoEmpleado()));

            // 4) Status
            String st = a.getStatus();
            if (st == null || st.isBlank()) st = "A";
            st = st.trim().toUpperCase();
            if (!"C".equals(st)) st = "A";
            ps.setString(4, st);

            // 5) Fecha baja
            LocalDate fb = a.getFechaBaja();
            if (!"C".equals(st)) {
                ps.setNull(5, Types.DATE);
            } else {
                if (fb != null) {
                    ps.setDate(5, Date.valueOf(fb));
                } else {
                    ps.setNull(5, Types.DATE);
                }
            }

            // 6) Permiso cancelar nota
            ps.setBoolean(6, a.isPermisoCancelaNota());

            // 7) WHERE
            ps.setInt(7, a.getNumeroEmpleado());

            ps.executeUpdate();
        }
    }

    // ========== SECCIÓN LOGIN EMPLEADOS ==========

    /** Hash SHA-256 de la contraseña (char[]) a bytes. */
    private byte[] hashPassword(char[] pass) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new String(pass).getBytes(StandardCharsets.UTF_8);
            Arrays.fill(pass, '\0'); // limpiar lo más posible
            return md.digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar SHA-256", e);
        }
    }

    /** ¿El empleado ya tiene contraseña configurada? */
    public boolean empleadoTienePassword(int numeroEmpleado) throws SQLException {
        String sql = "SELECT password_login IS NOT NULL AS tiene " +
                     "FROM Asesor WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getBoolean(1);
            }
        }
    }

    /** Establece / cambia la contraseña de un empleado (hash en BD). */
    public void establecerPasswordEmpleado(int numeroEmpleado, char[] nueva) throws SQLException {
        byte[] hash = hashPassword(nueva);
        String sql = "UPDATE Asesor SET password_login=? WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setBytes(1, hash);
            ps.setInt(2, numeroEmpleado);
            ps.executeUpdate();
        }
    }

    /** Valida la contraseña de un empleado. */
    public boolean validarPasswordEmpleado(int numeroEmpleado, char[] pass) throws SQLException {
        String sql = "SELECT password_login FROM Asesor WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                byte[] esperado = rs.getBytes(1);
                if (esperado == null) return false;
                byte[] actual = hashPassword(pass);
                return MessageDigest.isEqual(esperado, actual);
            }
        }
    }

    /** Actualiza solo el permiso de cancelar notas. */
    public void actualizarPermisoCancelar(int numeroEmpleado, boolean puedeCancelar) throws SQLException {
        String sql = "UPDATE Asesor SET permiso_cancelar_nota=? WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setBoolean(1, puedeCancelar);
            ps.setInt(2, numeroEmpleado);
            ps.executeUpdate();
        }
    }

    /** Lee el permiso de cancelar notas para un empleado. */
    public boolean puedeCancelarNotas(int numeroEmpleado) throws SQLException {
        String sql = "SELECT permiso_cancelar_nota FROM Asesor WHERE numero_empleado=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getBoolean(1);
            }
        }
    }
}
