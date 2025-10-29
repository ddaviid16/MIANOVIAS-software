package Controlador;

import Conexion.Conecta;
import Utilidades.EventBus;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** DAO para retiros de efectivo (pago de gastos). */
public class PagoGastosDAO {

    public static class Retiro {
        public LocalDateTime ts;
        public BigDecimal efectivoDia;
        public BigDecimal monto;
        public String motivo;
    }

    /** Registra un retiro y notifica actualización del corte. */
    public void registrar(LocalDateTime ts, BigDecimal efectivoDia, BigDecimal retiro, String motivo) throws SQLException {
        String sql = "INSERT INTO Gastos_Caja(ts, efectivo_dia, retiro, motivo) VALUES (?,?,?,?)";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(ts));
            if (efectivoDia == null) ps.setNull(2, Types.DECIMAL); else ps.setBigDecimal(2, efectivoDia);
            ps.setBigDecimal(3, retiro);
            if (motivo == null || motivo.isBlank()) ps.setNull(4, Types.VARCHAR); else ps.setString(4, motivo.trim());
            ps.executeUpdate();
        }

        // 🔥 Notificar evento global de actualización de efectivo
        EventBus.notificarOperacionFinalizada();
    }

    public List<Retiro> listarPorDia(LocalDate fecha) throws SQLException {
        String sql = "SELECT ts, efectivo_dia, retiro, motivo FROM Gastos_Caja " +
                "WHERE DATE(ts)=? ORDER BY ts DESC";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                List<Retiro> out = new ArrayList<>();
                while (rs.next()) {
                    Retiro r = new Retiro();
                    r.ts = rs.getTimestamp(1).toLocalDateTime();
                    r.efectivoDia = rs.getBigDecimal(2);
                    r.monto = rs.getBigDecimal(3);
                    r.motivo = rs.getString(4);
                    out.add(r);
                }
                return out;
            }
        }
    }

    /** Retorna la suma total de retiros en una fecha. */
    public BigDecimal totalRetirado(LocalDate fecha) throws SQLException {
        String sql = "SELECT COALESCE(SUM(retiro),0) FROM Gastos_Caja WHERE DATE(ts)=?";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}
