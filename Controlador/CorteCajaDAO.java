package Controlador;

import Conexion.Conecta;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CorteCajaDAO {

    // Totales leídos de formas_pago para un día (por método)
    public static class Totales {
        public BigDecimal tarjetaDebito       = BigDecimal.ZERO;
        public BigDecimal tarjetaCredito      = BigDecimal.ZERO;
        public BigDecimal americanExpress     = BigDecimal.ZERO;
        public BigDecimal transferenciaBanc   = BigDecimal.ZERO;
        public BigDecimal depositoBancario    = BigDecimal.ZERO;
        public BigDecimal efectivo            = BigDecimal.ZERO;
    }

    // NUEVO: ingresos por tipo de operación (CN/CR/AB) para el día
    public static class Ingresos {
        public BigDecimal contado = BigDecimal.ZERO;   // CN
        public BigDecimal credito = BigDecimal.ZERO;   // CR
        public BigDecimal abonos  = BigDecimal.ZERO;   // AB
    }

    // Corte a persistir en Cortes_Caja
    public static class Corte {
        public LocalDate fecha;
        public BigDecimal tarjetaDebito, tarjetaCredito, americanExpress,
                           transferenciaBanc, depositoBancario, efectivo;
        public BigDecimal retiros;
        public BigDecimal efectivoNeto;
    }

    /** Helper: verifica si existe una columna en una tabla (tolerante a mayúsculas/minúsculas). */
    private static boolean hasColumn(Connection cn, String table, String column) {
        String sql = "SELECT * FROM " + table + " WHERE 1=0";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ResultSetMetaData md = ps.getMetaData();
            int cols = md.getColumnCount();
            for (int i = 1; i <= cols; i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(column)) return true;
            }
        } catch (SQLException ignore) {}
        return false;
    }

    /** Devuelve la expresión SQL SUM(...) + SUM(...) con los nombres de columna que realmente existan. */
    private static String buildSumExpr(Connection cn) throws SQLException {
        // columnas “seguras”
        List<String> cols = new ArrayList<>();
        cols.add("tarjeta_debito");
        cols.add("tarjeta_credito");
        cols.add("american_express");
        // transferencia: bancaria / normal
        if (hasColumn(cn, "formas_pago", "transferencia_bancaria")) cols.add("transferencia_bancaria");
        else if (hasColumn(cn, "formas_pago", "transferencia"))     cols.add("transferencia");
        // deposito: bancario / normal
        if (hasColumn(cn, "formas_pago", "deposito_bancario")) cols.add("deposito_bancario");
        else if (hasColumn(cn, "formas_pago", "deposito"))     cols.add("deposito");
        // efectivo
        cols.add("efectivo");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append("COALESCE(SUM(").append(cols.get(i)).append("),0)");
        }
        return sb.toString();
    }

    /** Lee los totales del día desde la tabla formas_pago (status='A'), por método. */
    public Totales leerTotalesDia(LocalDate fecha) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            boolean tBanc = hasColumn(cn, "formas_pago", "transferencia_bancaria");
            boolean dBanc = hasColumn(cn, "formas_pago", "deposito_bancario");
            String colTransf = tBanc ? "transferencia_bancaria" : "transferencia";
            String colDepo   = dBanc ? "deposito_bancario"      : "deposito";

            String sql =
                "SELECT " +
                " COALESCE(SUM(tarjeta_debito),0)         AS t_deb, " +
                " COALESCE(SUM(tarjeta_credito),0)        AS t_cre, " +
                " COALESCE(SUM(american_express),0)       AS amex, " +
                " COALESCE(SUM(" + colTransf + "),0)      AS transf, " +
                " COALESCE(SUM(" + colDepo   + "),0)      AS depo, " +
                " COALESCE(SUM(efectivo),0)               AS efec " +
                "FROM formas_pago " +
                "WHERE fecha_operacion = ? AND status = 'A'";

            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(fecha));
                try (ResultSet rs = ps.executeQuery()) {
                    Totales t = new Totales();
                    if (rs.next()) {
                        t.tarjetaDebito     = rs.getBigDecimal("t_deb");
                        t.tarjetaCredito    = rs.getBigDecimal("t_cre");
                        t.americanExpress   = rs.getBigDecimal("amex");
                        t.transferenciaBanc = rs.getBigDecimal("transf");
                        t.depositoBancario  = rs.getBigDecimal("depo");
                        t.efectivo          = rs.getBigDecimal("efec");
                    }
                    return t;
                }
            }
        }
    }

    /** NUEVO: Suma ingresos por tipo_operacion (CN, CR, AB) para el día, usando formas_pago. */
    public Ingresos leerIngresosPorOperacion(LocalDate fecha) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            String sumExpr = buildSumExpr(cn); // SUM(tarjeta_debito)+... con columnas existentes

            String sql = "SELECT tipo_operacion AS tipo, " + sumExpr + " AS total " +
                         "FROM formas_pago " +
                         "WHERE fecha_operacion = ? AND status='A' " +
                         "GROUP BY tipo_operacion";

            Ingresos ing = new Ingresos();
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(fecha));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tipo = rs.getString("tipo");
                        BigDecimal total = rs.getBigDecimal("total");
                        if ("CN".equalsIgnoreCase(tipo))      ing.contado = total;
                        else if ("CR".equalsIgnoreCase(tipo)) ing.credito = total;
                        else if ("AB".equalsIgnoreCase(tipo)) ing.abonos  = total;
                    }
                }
            }
            return ing;
        }
    }

    public Corte leerCortePorFecha(LocalDate fecha) throws SQLException {
    String sql = """
        SELECT tarjeta_debito, tarjeta_credito, american_express,
               transferencia_bancaria, deposito_bancario, efectivo,
               retiros, efectivo_neto
        FROM cortes_caja
        WHERE fecha = ?
    """;
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setDate(1, java.sql.Date.valueOf(fecha));
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Corte c = new Corte();
                c.fecha = fecha;
                c.tarjetaDebito = rs.getBigDecimal("tarjeta_debito");
                c.tarjetaCredito = rs.getBigDecimal("tarjeta_credito");
                c.americanExpress = rs.getBigDecimal("american_express");
                c.transferenciaBanc = rs.getBigDecimal("transferencia_bancaria");
                c.depositoBancario = rs.getBigDecimal("deposito_bancario");
                c.efectivo = rs.getBigDecimal("efectivo");
                c.retiros = rs.getBigDecimal("retiros");
                c.efectivoNeto = rs.getBigDecimal("efectivo_neto");
                return c;
            }
        }
    }
    return null;
}

    /** Inserta el corte en la tabla Cortes_Caja (según tu estructura). */
    public void guardar(Corte c) throws SQLException {
    try (Connection cn = Conecta.getConnection()) {
        // 1) Intentar ACTUALIZAR el corte del mismo día
        String up = "UPDATE cortes_caja SET " +
                "tarjeta_debito=?, tarjeta_credito=?, american_express=?, " +
                "transferencia_bancaria=?, deposito_bancario=?, efectivo=?, " +
                "retiros=?, efectivo_neto=? " +
                "WHERE fecha=?";
        try (PreparedStatement ps = cn.prepareStatement(up)) {
            ps.setBigDecimal(1, c.tarjetaDebito);
            ps.setBigDecimal(2, c.tarjetaCredito);
            ps.setBigDecimal(3, c.americanExpress);
            ps.setBigDecimal(4, c.transferenciaBanc);
            ps.setBigDecimal(5, c.depositoBancario);
            ps.setBigDecimal(6, c.efectivo);
            ps.setBigDecimal(7, c.retiros);
            ps.setBigDecimal(8, c.efectivoNeto);
            ps.setDate(9, java.sql.Date.valueOf(c.fecha));

            int updated = ps.executeUpdate();
            if (updated > 0) return; // ya existía: quedó sobreescrito
        }

        // 2) Si no existía, INSERTAR
        String ins = "INSERT INTO cortes_caja(" +
    "fecha, tarjeta_debito, tarjeta_credito, american_express, " +
    "transferencia_bancaria, deposito_bancario, efectivo, retiros, efectivo_neto) " +
    "VALUES (?,?,?,?,?,?,?,?,?) " +
    "ON DUPLICATE KEY UPDATE " +
    "tarjeta_debito=VALUES(tarjeta_debito), " +
    "tarjeta_credito=VALUES(tarjeta_credito), " +
    "american_express=VALUES(american_express), " +
    "transferencia_bancaria=VALUES(transferencia_bancaria), " +
    "deposito_bancario=VALUES(deposito_bancario), " +
    "efectivo=VALUES(efectivo), " +
    "retiros=VALUES(retiros), " +
    "efectivo_neto=VALUES(efectivo_neto)";
        try (PreparedStatement ps = cn.prepareStatement(ins)) {
            ps.setDate(1, java.sql.Date.valueOf(c.fecha));
            ps.setBigDecimal(2, c.tarjetaDebito);
            ps.setBigDecimal(3, c.tarjetaCredito);
            ps.setBigDecimal(4, c.americanExpress);
            ps.setBigDecimal(5, c.transferenciaBanc);
            ps.setBigDecimal(6, c.depositoBancario);
            ps.setBigDecimal(7, c.efectivo);
            ps.setBigDecimal(8, c.retiros);
            ps.setBigDecimal(9, c.efectivoNeto);
            ps.executeUpdate();
        }
    }
}
}
