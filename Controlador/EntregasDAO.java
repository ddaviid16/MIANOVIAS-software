package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Entregas de vestidos: se arman desde ventas + fecha_evento del cliente.
 *  Entregas_Vestidos persiste sólo el estado de entrega.
 */
public class EntregasDAO {

    public static class EntregaRow {
        public int numeroNota;         // para guardar estado (oculto en la UI)
        public String folio;           // <-- NUEVO: se muestra en la tabla
        public String cliente;         // nombre completo
        public int codigoArticulo;
        public String articulo;
        public String color;
        public LocalDate fechaEntrega; // = fecha_evento del cliente
        public boolean entregado;
    }

    /** Lista vestidos a entregar en el mes/año indicado. */
    public List<EntregaRow> listarPorMes(YearMonth ym) throws SQLException {
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.plusMonths(1).atDay(1);

        // Preferente: con Inventarios (filtra por familia/categoría)
        final String SQL_CON_INV =
            "SELECT n.numero_nota AS numero_nota, n.folio AS folio, " +
            "       TRIM(CONCAT_WS(' ', NULLIF(c.nombre,''), NULLIF(c.apellido_paterno,''), NULLIF(c.apellido_materno,''))) AS cliente, " +
            "       nd.codigo_articulo, nd.articulo, nd.color, " +
            "       c.fecha_evento AS fecha_entrega, " +
            "       CASE WHEN ev.entregado='S' THEN 1 ELSE 0 END AS entregado " +
            "FROM Notas n " +
            "JOIN Nota_Detalle nd ON nd.numero_nota = n.numero_nota AND nd.status='A' " +
            "LEFT JOIN Inventarios inv ON inv.codigo_articulo = nd.codigo_articulo " +
            "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
            "LEFT JOIN Entregas_Vestidos ev ON ev.numero_nota = n.numero_nota AND ev.articulo = nd.articulo " +
            "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
            "  AND c.fecha_evento >= ? AND c.fecha_evento < ? " +
            "  AND ( UPPER(inv.familia) LIKE 'VESTIDO%' OR UPPER(inv.categoria) LIKE 'VEST%' ) " +
            "ORDER BY c.fecha_evento, n.numero_nota";

        // Fallback: sin Inventarios -> LIKE sobre la descripción del renglón
        final String SQL_SIN_INV =
            "SELECT n.numero_nota AS numero_nota, n.folio AS folio, " +
            "       TRIM(CONCAT_WS(' ', NULLIF(c.nombre,''), NULLIF(c.apellido_paterno,''), NULLIF(c.apellido_materno,''))) AS cliente, " +
            "       nd.codigo_articulo, nd.articulo, nd.color, " +
            "       c.fecha_evento AS fecha_entrega, " +
            "       CASE WHEN ev.entregado='S' THEN 1 ELSE 0 END AS entregado " +
            "FROM Notas n " +
            "JOIN Nota_Detalle nd ON nd.numero_nota = n.numero_nota AND nd.status='A' " +
            "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
            "LEFT JOIN Entregas_Vestidos ev ON ev.numero_nota = n.numero_nota AND ev.articulo = nd.articulo " +
            "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
            "  AND c.fecha_evento >= ? AND c.fecha_evento < ? " +
            "  AND UPPER(nd.articulo) LIKE '%VESTIDO%' " +
            "ORDER BY c.fecha_evento, n.numero_nota";

        try (Connection cn = Conecta.getConnection()) {
            // Intento 1: con Inventarios
            try (PreparedStatement ps = cn.prepareStatement(SQL_CON_INV)) {
                ps.setDate(1, Date.valueOf(start));
                ps.setDate(2, Date.valueOf(end));
                try (ResultSet rs = ps.executeQuery()) {
                    List<EntregaRow> out = readRows(rs);
                    if (!out.isEmpty()) return out;
                }
            } catch (SQLException ignore) { }

            // Intento 2: fallback
            try (PreparedStatement ps = cn.prepareStatement(SQL_SIN_INV)) {
                ps.setDate(1, Date.valueOf(start));
                ps.setDate(2, Date.valueOf(end));
                try (ResultSet rs = ps.executeQuery()) {
                    return readRows(rs);
                }
            }
        }
    }

    private static List<EntregaRow> readRows(ResultSet rs) throws SQLException {
        List<EntregaRow> out = new ArrayList<>();
        while (rs.next()) {
            Date f = rs.getDate("fecha_entrega");
            if (f == null) continue;
            EntregaRow r = new EntregaRow();
            r.numeroNota     = rs.getInt("numero_nota");
            r.folio          = rs.getString("folio");
            r.cliente        = rs.getString("cliente");
            r.codigoArticulo = rs.getInt("codigo_articulo");
            r.articulo       = rs.getString("articulo");
            r.color          = rs.getString("color");
            r.fechaEntrega   = f.toLocalDate();
            r.entregado      = rs.getInt("entregado") == 1;
            out.add(r);
        }
        return out;
    }

    /** Upsert del estado entregado. */
    public void marcarEntregado(int numeroNota, String articulo, String cliente,
                                LocalDate fechaEntrega, boolean entregado) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            int updated;
            try (PreparedStatement up = cn.prepareStatement(
                    "UPDATE Entregas_Vestidos SET entregado=?, entregado_ts=NOW() " +
                    "WHERE numero_nota=? AND articulo=?")) {
                up.setString(1, entregado ? "S" : "N");
                up.setInt(2, numeroNota);
                up.setString(3, articulo);
                updated = up.executeUpdate();
            }
            if (updated > 0) return;

            try (PreparedStatement ins = cn.prepareStatement(
                    "INSERT INTO Entregas_Vestidos (numero_nota, cliente, articulo, fecha_entrega, entregado, entregado_ts) " +
                    "VALUES (?,?,?,?,?, NOW())")) {
                ins.setInt(1, numeroNota);
                ins.setString(2, cliente);
                ins.setString(3, articulo);
                ins.setDate(4, Date.valueOf(fechaEntrega != null ? fechaEntrega : LocalDate.now()));
                ins.setString(5, entregado ? "S" : "N");
                ins.executeUpdate();
            }
        }
    }
}
