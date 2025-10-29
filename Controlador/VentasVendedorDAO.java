package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Resumen de ventas por vendedor (día). */
public class VentasVendedorDAO {

    public static class Row {
        public Integer asesor;        // numero_empleado; puede ser null (Sin asesor)
        public String  asesorNombre;  // nombre completo o "(Sin asesor)"
        public int     ventas;        // tickets del día
    }

    /** Devuelve TODOS los asesores (aunque tengan 0) + "(Sin asesor)". */
    public List<Row> listarDia(LocalDate dia) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            return listarDia(cn, dia);
        }
    }

    private List<Row> listarDia(Connection cn, LocalDate dia) throws SQLException {
        // 1) Conteo de tickets por asesor ese día (incluye NULL)
        final String qCnt =
            "SELECT n.asesor AS num, COUNT(*) AS ventas " +
            "FROM Notas n " +
            "WHERE n.status='A' AND n.tipo IN ('CN','CR') " +
            "  AND DATE(n.fecha_registro)=? " +
            "GROUP BY n.asesor";

        Map<Integer, Integer> conteo = new HashMap<>();

        try (PreparedStatement ps = cn.prepareStatement(qCnt)) {
            ps.setDate(1, Date.valueOf(dia));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int num = rs.getInt("num");
                    int v   = rs.getInt("ventas");
                    if (rs.wasNull()) {
                        conteo.put(null, v);
                    } else {
                        conteo.put(num, v);
                    }
                }
            }
        }

        // 2) Catálogo de asesores
        final String qAses =
            "SELECT a.numero_empleado, a.nombre_completo " +
            "FROM Asesor a WHERE a.status='A' " +
            "ORDER BY a.nombre_completo ASC";

        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(qAses);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Row r = new Row();
                r.asesor = rs.getInt("numero_empleado");
                if (rs.wasNull()) r.asesor = null; // por si acaso
                r.asesorNombre = rs.getString("nombre_completo");
                r.ventas = conteo.getOrDefault(r.asesor, 0);
                out.add(r);
            }
        }


        // 4) Orden sugerido: ventas DESC, nombre ASC
        out.sort(Comparator.<Row>comparingInt(r -> -r.ventas)
                           .thenComparing(r -> r.asesorNombre, Comparator.nullsLast(String::compareTo)));

        return out;
    }

    /** Guarda el resumen del día: borra lo existente y vuelve a insertar todo. */
    public void guardarResumenDiario(LocalDate dia) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                // Recalcular con la misma conexión
                List<Row> rows = listarDia(cn, dia);

                // 1) Borrar resumen previo del día
                try (PreparedStatement del = cn.prepareStatement(
                        "DELETE FROM ventas_vendedor_diario WHERE fecha=?")) {
                    del.setDate(1, Date.valueOf(dia));
                    del.executeUpdate();
                }

                // 2) Insertar el nuevo resumen
                try (PreparedStatement ins = cn.prepareStatement(
                        "INSERT INTO ventas_vendedor_diario " +
                        "(fecha, numero_empleado, nombre, ventas, created_at) " +
                        "VALUES (?,?,?,?, NOW())")) {
                    for (Row r : rows) {
                        ins.setDate(1, Date.valueOf(dia));
                        if (r.asesor == null) ins.setNull(2, Types.INTEGER);
                        else ins.setInt(2, r.asesor);
                        ins.setString(3, r.asesorNombre);
                        ins.setInt(4, r.ventas);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }
}
