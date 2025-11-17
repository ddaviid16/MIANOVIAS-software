package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.util.*;

public class NotasDVDAO {

    public static class DVDisponible {
        public int numeroNotaDV;
        public String folio;
        public String origenTipo;    // CN o CR
        public int numeroNotaOrigen;
        public double montoBase;     // total DV (o anticipo si origen=CR)
        public double aplicado;      // ya aplicado en otras ventas
        public double disponible;    // montoBase - aplicado
    }

    /** Lista DV del cliente con "disponible" > 0.
     *  Si la DV proviene de CR: montoBase = MIN(totalDV, anticipoOrigen). */
    public List<DVDisponible> listarDisponiblesPorTelefono(String telefono) throws SQLException {
        String sql =
            "SELECT dv.numero_nota AS dv_num, dv.folio AS dv_folio, " +
            "       dv.total AS dv_total, " +                             // valor nominal de la DV
            "       n0.tipo AS origen_tipo, n0.numero_nota AS origen_num, " +
            "       COALESCE(fp.anticipo,0) AS anticipo, " +
            "       COALESCE(d.monto_usado,0) AS aplicado " +             // ← usa Devoluciones.monto_usado
            "FROM Notas dv " +
            "JOIN Devoluciones d ON d.numero_nota_dv = dv.numero_nota " +
            "JOIN Notas n0 ON n0.numero_nota = d.nota_origen " +
            "LEFT JOIN ( " +
            "   SELECT numero_nota, " +
            "          COALESCE(tarjeta_credito,0)+COALESCE(tarjeta_debito,0)+COALESCE(american_express,0)+ " +
            "          COALESCE(transferencia_bancaria,0)+COALESCE(deposito_bancario,0)+COALESCE(efectivo,0) AS anticipo " +
            "   FROM Formas_Pago WHERE tipo_operacion='CR' " +
            ") fp ON fp.numero_nota = n0.numero_nota " +
            "WHERE dv.tipo='DV' AND dv.status='A' AND dv.telefono=? " +
            "ORDER BY dv.numero_nota DESC";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                List<DVDisponible> out = new ArrayList<>();
                while (rs.next()) {
                    DVDisponible r = new DVDisponible();
                    r.numeroNotaDV     = rs.getInt("dv_num");
                    r.folio            = rs.getString("dv_folio");
                    r.origenTipo       = rs.getString("origen_tipo");
                    r.numeroNotaOrigen = rs.getInt("origen_num");

                    double totalDV  = rs.getBigDecimal("dv_total")   == null ? 0 : rs.getBigDecimal("dv_total").doubleValue();
                    double anticipo = rs.getBigDecimal("anticipo")   == null ? 0 : rs.getBigDecimal("anticipo").doubleValue();
                    r.aplicado      = rs.getBigDecimal("aplicado")   == null ? 0 : rs.getBigDecimal("aplicado").doubleValue();

                    // Si la DV viene de una venta CR, no puede valer más que lo que se pagó de anticipo
                    double base = "CR".equalsIgnoreCase(r.origenTipo)
                            ? Math.min(totalDV, anticipo)
                            : totalDV;

                    r.montoBase  = base;
                    r.disponible = Math.max(0, base - r.aplicado);

                    if (r.disponible > 0.005) out.add(r);
                }
                return out;
            }
        }
    }

    public void aplicarAVenta(int numeroNota, String tipo, java.util.List<Modelo.PagoDV> items) throws SQLException {
        if (items == null || items.isEmpty()) return;

        Connection cn = null;
        boolean prevAuto = true;
        try {
            cn = Conexion.Conecta.getConnection();
            prevAuto = cn.getAutoCommit();
            cn.setAutoCommit(false);

            for (Modelo.PagoDV dv : items) {
                if (dv == null) continue;
                double monto = dv.monto;
                if (monto <= 0.0) continue;
                aplicarDV(cn, dv.numeroNotaDV, monto);
            }

            cn.commit();
        } catch (SQLException ex) {
            if (cn != null) try { cn.rollback(); } catch (Exception ignore) {}
            throw ex;
        } finally {
            if (cn != null) {
                try { cn.setAutoCommit(prevAuto); } catch (Exception ignore) {}
                try { cn.close(); } catch (Exception ignore) {}
            }
        }
    }

    private void aplicarDV(Connection cn, int numeroNotaDV, double monto) throws SQLException {
        String sqlUpd = """
            UPDATE Devoluciones
            SET monto_usado = COALESCE(monto_usado, 0) + ?
            WHERE numero_nota_dv = ?
            """;
        try (PreparedStatement ps = cn.prepareStatement(sqlUpd)) {
            ps.setDouble(1, monto);
            ps.setInt(2, numeroNotaDV);
            int rows = ps.executeUpdate();
            if (rows == 0)
                throw new SQLException("No se encontró la devolución con numero_nota_dv=" + numeroNotaDV);
        }
    }
}
