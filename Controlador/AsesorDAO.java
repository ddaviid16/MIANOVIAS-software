package Controlador;

import Conexion.Conecta;
import Modelo.Asesor;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AsesorDAO {

    public List<Integer> listarActivos() throws SQLException {
        String sql = "SELECT numero_empleado FROM Asesor WHERE status='A' ORDER BY numero_empleado";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Integer> lista = new ArrayList<>();
            while (rs.next()) lista.add(rs.getInt(1));
            return lista;
        }
    }

    public List<Asesor> listarTodos() throws SQLException {
        String sql = "SELECT numero_empleado, nombre_completo, fecha_alta, fecha_baja, status " +
                     "FROM Asesor ORDER BY status DESC, numero_empleado";
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
                a.setStatus(rs.getString("status"));
                lista.add(a);
            }
            return lista;
        }
    }

    /** Activos, ordenados por nombre (para combos/listas) */
    public List<Asesor> listarActivosDetalle() throws SQLException {
        String sql = "SELECT numero_empleado, nombre_completo " +
                     "FROM Asesor WHERE status='A' ORDER BY nombre_completo";
        List<Asesor> out = new ArrayList<>();
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Asesor a = new Asesor();
                a.setNumeroEmpleado(rs.getInt("numero_empleado"));
                a.setNombreCompleto(rs.getString("nombre_completo"));
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
        String sql = "INSERT INTO Asesor (numero_empleado, nombre_completo, fecha_alta, status) " +
                     "VALUES (?,?,?, 'A')";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, a.getNumeroEmpleado());
            ps.setString(2, a.getNombreCompleto());
            // Si viene null, usar hoy:
            LocalDate fa = (a.getFechaAlta() == null) ? LocalDate.now() : a.getFechaAlta();
            ps.setDate(3, Date.valueOf(fa));
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
}
