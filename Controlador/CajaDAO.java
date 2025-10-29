package Controlador;

import Conexion.Conecta;
import java.sql.*;
import java.time.LocalDate;

public class CajaDAO {

    /** Efectivo disponible = (efectivo ventas + abonos en efectivo) – retiros. */
    public double efectivoDisponible(LocalDate fecha) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            double efVentas = efectivoVentasHoy(cn, fecha);
            double efAbonos = efectivoAbonosHoy(cn, fecha);
            double retiros  = retirosHoy(cn, fecha);
            return Math.max(0, efVentas + efAbonos - retiros);
        }
    }

    // Efectivo en formas_pago
    private double efectivoVentasHoy(Connection cn, LocalDate fecha) throws SQLException {
        final String sql = "SELECT COALESCE(SUM(efectivo),0) FROM formas_pago " +
                           "WHERE fecha_operacion >= ? AND fecha_operacion < ? AND COALESCE(status,'A')='A'";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            ps.setDate(2, Date.valueOf(fecha.plusDays(1)));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1).doubleValue() : 0d;
            }
        }
    }

    // Efectivo en ABONOS (metodo EF…)
    private double efectivoAbonosHoy(Connection cn, LocalDate fecha) throws SQLException {
        final String sql =
            "SELECT COALESCE(SUM(COALESCE(monto,abono,cantidad,0)),0) " +
            "FROM Abonos " +
            "WHERE DATE(COALESCE(fecha_abono,fecha,created_at,CURRENT_DATE))=? " +
            "  AND UPPER(COALESCE(metodo_pago,metodo,forma_pago,'')) LIKE 'EF%' " +
            "  AND COALESCE(status,'A')='A'";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1).doubleValue() : 0d;
            }
        }
    }

    // Retiros del día
    private double retirosHoy(Connection cn, LocalDate fecha) throws SQLException {
        final String sql = "SELECT COALESCE(SUM(retiro),0) FROM gastos_caja WHERE DATE(ts)=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1).doubleValue() : 0d;
            }
        }
    }
}
