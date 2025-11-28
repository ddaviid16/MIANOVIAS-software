package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.time.LocalDate;

public class CancelacionService {

    public static class ResultadoCancelacion {
        public int numeroNota;
        public String folio;
        public String tipo;
    }

    /** Cancela una nota CN/CR:
     *  - Valida que esté activa.
     *  - Repone existencias de artículos del detalle.
     *  - (Opcional) repone existencias de obsequios si tienes columnas *_cod.
     *  - Marca en 'C' la nota y todas sus tablas relacionadas.
     *  - Inserta/actualiza bitácora en Cancelados.
     */
    public ResultadoCancelacion cancelarNota(int numeroNota, Integer asesor, String motivo) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                // 1) Leer y bloquear la nota
                String folio, tipo, status;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT folio, tipo, status FROM Notas WHERE numero_nota=? FOR UPDATE")) {
                    ps.setInt(1, numeroNota);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Nota no encontrada: " + numeroNota);
                        folio  = rs.getString("folio");
                        tipo   = rs.getString("tipo");
                        status = rs.getString("status");
                    }
                }
                if (!"A".equalsIgnoreCase(status))
                    throw new SQLException("La nota ya no está activa (status=" + status + ").");
                if (!"CN".equalsIgnoreCase(tipo) && !"CR".equalsIgnoreCase(tipo))
                    throw new SQLException("Solo se pueden cancelar notas de contado o crédito (CN/CR).");

                // 2) Reponer existencias de los ARTÍCULOS vendidos en la nota
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT codigo_articulo, COUNT(*) AS piezas " +
                        "FROM Nota_Detalle " +
                        "WHERE numero_nota=? AND status<>'C' " +
                        "GROUP BY codigo_articulo")) {
                    ps.setInt(1, numeroNota);
                    try (ResultSet rs = ps.executeQuery()) {
                        try (PreparedStatement up = cn.prepareStatement(
                                "UPDATE Inventarios " +
                                "SET existencia = COALESCE(existencia,0) + ? " +
                                "WHERE codigo_articulo=?")) {
                            while (rs.next()) {
                                String cod = (String) rs.getObject("codigo_articulo");
                                int piezas = rs.getInt("piezas");
                                if (cod == null || piezas <= 0) continue;
                                up.setInt(1, piezas);
                                up.setString(2, cod);
                                up.addBatch();
                            }
                            up.executeBatch();
                        }
                    }
                }

                // 3) (Opcional) Reponer existencias de OBSEQUIOS si guardas códigos en Obsequios
                //     Columnas esperadas: obsequio1_cod..obsequio5_cod (INT, FK a InventarioObsequios)
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT obsequio1_cod, obsequio2_cod, obsequio3_cod, obsequio4_cod, obsequio5_cod " +
                        "FROM Obsequios WHERE numero_nota=? AND status='A'")) {
                    ps.setInt(1, numeroNota);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            try (PreparedStatement up = cn.prepareStatement(
                                    "UPDATE InventarioObsequios " +
                                    "SET existencia = COALESCE(existencia,0) + 1 " +
                                    "WHERE codigo_articulo=?")) {
                                for (int i = 1; i <= 5; i++) {
                                    String cod = (String) rs.getObject("obsequio" + i + "_cod");
                                    if (cod != null) {
                                        up.setString(1, cod);
                                        up.addBatch();
                                    }
                                }
                                up.executeBatch();
                            }
                        }
                    }
                } catch (SQLException ignoreIfNoColumns) {
                    // Si aún no agregas las columnas *_cod, este SELECT fallará.
                    // Lo ignoramos para que la cancelación siga funcionando sin reponer obsequios.
                }

                // 4) Encabezado -> status='C'
                try (PreparedStatement up = cn.prepareStatement(
                        "UPDATE Notas SET status='C' WHERE numero_nota=?")) {
                    up.setInt(1, numeroNota);
                    up.executeUpdate();
                }

                // 5) Relacionadas -> status='C'
                execUpdateSafe(cn, "UPDATE Nota_Detalle SET status='C' WHERE numero_nota=? AND status<>'C'", numeroNota);
                execUpdateSafe(cn, "UPDATE Formas_Pago SET status='C' WHERE numero_nota=? AND status<>'C'", numeroNota);
                execUpdateSafe(cn, "UPDATE Obsequios SET status='C' WHERE numero_nota=? AND status<>'C'", numeroNota);
                execUpdateSafe(cn, "UPDATE Cambios_Fecha_Evento SET status='C' WHERE numero_nota=? AND status<>'C'", numeroNota);
                execUpdateSafe(cn, "UPDATE Cancelacion_Eventos SET status='C' WHERE numero_nota=? AND status<>'C'", numeroNota);

                // 6) Bitácora de cancelación (upsert)
                try (PreparedStatement ins = cn.prepareStatement(
                        "INSERT INTO Cancelados (numero_nota, fecha_cancelacion, motivo_cancelacion, status, asesor) " +
                        "VALUES (?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  fecha_cancelacion=VALUES(fecha_cancelacion), " +
                        "  motivo_cancelacion=VALUES(motivo_cancelacion), " +
                        "  status=VALUES(status), " +
                        "  asesor=VALUES(asesor)")) {
                    ins.setInt(1, numeroNota);
                    ins.setDate(2, Date.valueOf(LocalDate.now()));
                    if (motivo == null || motivo.isBlank()) ins.setNull(3, Types.VARCHAR); else ins.setString(3, motivo.trim());
                    ins.setString(4, "A");
                    if (asesor == null) ins.setNull(5, Types.INTEGER); else ins.setInt(5, asesor);
                    ins.executeUpdate();
                }

                ResultadoCancelacion out = new ResultadoCancelacion();
                out.numeroNota = numeroNota;
                out.folio = folio;
                out.tipo  = tipo;

                cn.commit();
                return out;

            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            }
        }
    }

    private static void execUpdateSafe(Connection cn, String sql, int numeroNota) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            ps.executeUpdate();
        }
    }

    /** Para tu diálogo “¿Esta es la nota correcta?” */
    public String resumenNota(int numeroNota) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            StringBuilder sb = new StringBuilder();

            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT folio, tipo, total, saldo FROM Notas WHERE numero_nota=?")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sb.append("Nota ").append(rs.getString("tipo"))
                          .append(" #").append(numeroNota)
                          .append("  Folio: ").append(rs.getString("folio"))
                          .append("\nTotal: ").append(String.format("%.2f", rs.getDouble("total")))
                          .append("   Saldo: ").append(String.format("%.2f", rs.getDouble("saldo")))
                          .append("\n");
                    }
                }
            }
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT articulo, COUNT(*) as piezas, SUM(subtotal) as importe " +
                    "FROM Nota_Detalle WHERE numero_nota=? GROUP BY articulo ORDER BY articulo")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    sb.append("\nArtículos:\n");
                    while (rs.next()) {
                        sb.append("• ").append(rs.getString("articulo"))
                          .append(" (").append(rs.getInt("piezas")).append(" pza)  $")
                          .append(String.format("%.2f", rs.getDouble("importe"))).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }
}
