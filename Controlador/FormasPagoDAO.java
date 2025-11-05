package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Lecturas de formas de pago y devoluciones usadas como pago. */
public class FormasPagoDAO {

    public static class FormasPagoRow {
        public Double efectivo;
        public Double tarjetaCredito;
        public Double tarjetaDebito;
        public Double americanExpress;
        public Double transferencia;
        public Double deposito;
        public Double devolucion;
        public String referenciaDV;      // texto libre en formas_pago.referencia_dv
        public String tipoOperacion;     // CN/CR/AB/DV, si lo manejas
        public String status;            // A/C
        public Timestamp fecha;
    }

    /** Devuelve el registro de formas_pago de la venta (por numero_nota).
     *  Si hubiera más de uno para la misma venta, toma el más reciente. */
    public FormasPagoRow obtenerPorNota(int numeroNota) throws SQLException {
        String sql = "SELECT tarjeta_credito, tarjeta_debito, american_express, " +
                     "       transferencia_bancaria, deposito_bancario, efectivo, devolucion, referencia_dv, tipo_operacion, status, fecha_operacion " +
                     "FROM formas_pago " +
                     "WHERE numero_nota=? " +
                     "ORDER BY fecha_operacion DESC LIMIT 1";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                FormasPagoRow r = new FormasPagoRow();
                r.efectivo        = getD(rs,"efectivo");
                r.tarjetaCredito  = getD(rs,"tarjeta_credito");
                r.tarjetaDebito   = getD(rs,"tarjeta_debito");
                r.americanExpress = getD(rs,"american_express");
                r.transferencia   = getD(rs,"transferencia_bancaria");
                r.deposito        = getD(rs,"deposito_bancario");
                r.devolucion      = getD(rs,"devolucion");
                r.referenciaDV    = rs.getString("referencia_dv");
                r.tipoOperacion   = rs.getString("tipo_operacion");
                r.status          = rs.getString("status");
                r.fecha           = rs.getTimestamp("fecha_operacion");
                return r;
            }
        }
    }

    public static class PagoDVRow {
        public int numeroNotaDV;
        public Double montoUsado;
        public Timestamp fecha;
    }

    /** Lista los folios de Devolución (DV) usados como pago para la nota destino. */
    public List<PagoDVRow> listarDVUsadosComoPago(int numeroNotaDestino) throws SQLException {
        String sql = "SELECT numero_nota_dv, monto_usado, fecha " +
                     "FROM Devoluciones " +
                     "WHERE nota_origen=? " +
                     "ORDER BY fecha";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNotaDestino);
            try (ResultSet rs = ps.executeQuery()) {
                List<PagoDVRow> out = new ArrayList<>();
                while (rs.next()) {
                    PagoDVRow r = new PagoDVRow();
                    r.numeroNotaDV = rs.getInt("numero_nota_dv");
                    r.montoUsado   = getD(rs,"monto_usado");
                    r.fecha        = rs.getTimestamp("fecha");
                    out.add(r);
                }
                return out;
            }
        }
    }

    private static Double getD(ResultSet rs, String col) throws SQLException {
        try {
            java.math.BigDecimal bd = rs.getBigDecimal(col);
            return (bd == null ? null : bd.doubleValue());
        } catch (Throwable t) {
            double v = rs.getDouble(col);
            return rs.wasNull() ? null : v;
        }
    }
}
