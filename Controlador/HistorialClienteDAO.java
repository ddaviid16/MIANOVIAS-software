package Controlador;

import Conexion.Conecta;
import Modelo.HistorialCliente;

import java.sql.*;

public class HistorialClienteDAO {

    /** Carga historial por teléfono; devuelve null si no existe. */
    public HistorialCliente cargarPorTelefono(String telefono1) throws SQLException {
        String sql = "SELECT telefono1, saldo_migrado, fecha_saldo, obsequios, observacion " +
                     "FROM HistorialCliente WHERE telefono1 = ?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, telefono1);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                HistorialCliente h = new HistorialCliente();
                h.setTelefono1(rs.getString("telefono1"));

                java.math.BigDecimal bd = rs.getBigDecimal("saldo_migrado");
                h.setSaldoMigrado(bd == null ? null : bd.doubleValue());

                Date d = rs.getDate("fecha_saldo");
                h.setFechaSaldo(d == null ? null : d.toLocalDate());

                h.setObsequios(rs.getString("obsequios"));
                h.setObservacion(rs.getString("observacion"));
                return h;
            }
        }
    }

    /** Inserta o actualiza el historial (upsert por telefono1). */
    public boolean guardar(HistorialCliente h) throws SQLException {
        String sqlUpdate =
            "UPDATE HistorialCliente " +
            "SET saldo_migrado=?, fecha_saldo=?, obsequios=?, observacion=? " +
            "WHERE telefono1=?";
        String sqlInsert =
            "INSERT INTO HistorialCliente(telefono1, saldo_migrado, fecha_saldo, obsequios, observacion) " +
            "VALUES (?,?,?,?,?)";

        try (Connection cn = Conecta.getConnection()) {

            // 1) Intentar UPDATE
            try (PreparedStatement ps = cn.prepareStatement(sqlUpdate)) {
                setParams(ps, h);
                ps.setString(5, h.getTelefono1());
                int n = ps.executeUpdate();
                if (n > 0) return true;
            }

            // 2) Si no actualizó ninguna fila, hacer INSERT
            try (PreparedStatement ps = cn.prepareStatement(sqlInsert)) {
                ps.setString(1, h.getTelefono1());
                if (h.getSaldoMigrado() == null) {
                    ps.setNull(2, Types.DECIMAL);
                } else {
                    ps.setBigDecimal(2, new java.math.BigDecimal(h.getSaldoMigrado()));
                }
                if (h.getFechaSaldo() == null) {
                    ps.setNull(3, Types.DATE);
                } else {
                    ps.setDate(3, Date.valueOf(h.getFechaSaldo()));
                }
                ps.setString(4, h.getObsequios());
                ps.setString(5, h.getObservacion());
                int n = ps.executeUpdate();
                return n > 0;
            }
        }
    }

    private void setParams(PreparedStatement ps, HistorialCliente h) throws SQLException {
        if (h.getSaldoMigrado() == null) {
            ps.setNull(1, Types.DECIMAL);
        } else {
            ps.setBigDecimal(1, new java.math.BigDecimal(h.getSaldoMigrado()));
        }
        if (h.getFechaSaldo() == null) {
            ps.setNull(2, Types.DATE);
        } else {
            ps.setDate(2, Date.valueOf(h.getFechaSaldo()));
        }
        ps.setString(3, h.getObsequios());
        ps.setString(4, h.getObservacion());
    }
}
