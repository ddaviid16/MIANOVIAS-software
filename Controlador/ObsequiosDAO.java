package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class ObsequiosDAO {

    // INSERT legado (sólo nombres)
    private static final String INSERT_SQL_LEGACY =
        "INSERT INTO Obsequios " +
        "(numero_nota, telefono, fecha_operacion, " +
        " obsequio1, obsequio2, obsequio3, obsequio4, obsequio5, " +
        " tipo_operacion, asesor, status, fecha_evento) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    // INSERT nuevo: nombres + códigos (obsequio1_cod..obsequio5_cod)
    // *IMPORTANTE*: obsequio*_cod debería ser VARCHAR si quieres guardar guiones/letras
    private static final String INSERT_SQL_WITH_CODES =
        "INSERT INTO Obsequios " +
        "(numero_nota, telefono, fecha_operacion, " +
        " obsequio1, obsequio2, obsequio3, obsequio4, obsequio5, " +
        " obsequio1_cod, obsequio2_cod, obsequio3_cod, obsequio4_cod, obsequio5_cod, " +
        " tipo_operacion, asesor, status, fecha_evento) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * Inserta los obsequios elegidos para una nota y descuenta existencia en InventarioObsequios.
     *
     * @param numeroNota   nota a la que pertenecen
     * @param telefono     teléfono del cliente (puede ser null)
     * @param fechaOp      fecha de operación (si es null se usa hoy)
     * @param obsequios    LISTA de códigos de obsequio (tal como los capturas, ej. "456-1234")
     * @param tipoOperacion "CN", "CR", etc.
     * @param asesor       número de empleado
     * @param status       "A", etc.
     * @param fechaEvento  fecha de evento (puede ser null)
     */
    public void insertarParaNota(int numeroNota,
                                 String telefono,
                                 LocalDate fechaOp,
                                 List<String> obsequios,
                                 String tipoOperacion,
                                 int asesor,
                                 String status,
                                 LocalDate fechaEvento) throws SQLException {

        if (obsequios == null || obsequios.isEmpty()) {
            // nada que guardar, salimos en paz
            return;
        }

        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                // 1) Normalizar códigos y obtener nombre desde InventarioObsequios
                List<String> codigos = new ArrayList<>();
                List<String> nombres = new ArrayList<>();

                try (PreparedStatement psNom = cn.prepareStatement(
                        "SELECT articulo FROM InventarioObsequios WHERE codigo_articulo = ?")) {

                    for (String raw : obsequios) {
                        if (raw == null) continue;
                        String code = raw.trim();
                        if (code.isEmpty()) continue;

                        // Acepta letras, dígitos y guiones. Si quieres menos permisivo, ajusta esto.
                        if (!code.matches("[A-Za-z0-9-]+")) {
                            throw new SQLException("Código de obsequio inválido: " + code);
                        }

                        // Buscar nombre del obsequio
                        psNom.setString(1, code);
                        try (ResultSet rs = psNom.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("No existe el obsequio código: " + code);
                            }
                            String nombre = rs.getString(1);
                            codigos.add(code);
                            nombres.add(nombre);
                        }

                        // La tabla sólo tiene 5 columnas de obsequios, así que cortamos en 5
                        if (codigos.size() == 5) break;
                    }
                }

                // Rellenar a 5 posiciones con null
                while (nombres.size() < 5) nombres.add(null);

                // 2) INSERT en Obsequios (primero intenta con columnas *_cod; si no existen, usa legado)
                boolean inserted = false;
                LocalDate fOp = (fechaOp != null) ? fechaOp : LocalDate.now();

                try (PreparedStatement ps = cn.prepareStatement(INSERT_SQL_WITH_CODES)) {
                    int k = 1;
                    ps.setInt(k++, numeroNota);

                    if (telefono == null || telefono.isBlank()) {
                        ps.setNull(k++, Types.VARCHAR);
                    } else {
                        ps.setString(k++, telefono);
                    }

                    ps.setDate(k++, Date.valueOf(fOp));

                    // Nombres (5)
                    for (int i = 0; i < 5; i++) {
                        String v = nombres.get(i);
                        if (v == null) ps.setNull(k++, Types.VARCHAR);
                        else           ps.setString(k++, v);
                    }

                    // Códigos (5). Si hay menos de 5, el resto va null
                    for (int i = 0; i < 5; i++) {
                        String cod = (i < codigos.size()) ? codigos.get(i) : null;
                        if (cod == null) ps.setNull(k++, Types.VARCHAR);
                        else             ps.setString(k++, cod);
                    }

                    ps.setString(k++, tipoOperacion);
                    ps.setInt(k++, asesor);
                    ps.setString(k++, status);
                    if (fechaEvento == null) {
                        ps.setNull(k++, Types.DATE);
                    } else {
                        ps.setDate(k++, Date.valueOf(fechaEvento));
                    }

                    ps.executeUpdate();
                    inserted = true;

                } catch (SQLException ex) {
                    // Si la BD no tiene aún las columnas *_cod, caemos al INSERT "viejo"
                    if (!isUnknownColumnError(ex)) {
                        throw ex;
                    }

                    try (PreparedStatement ps = cn.prepareStatement(INSERT_SQL_LEGACY)) {
                        int k = 1;
                        ps.setInt(k++, numeroNota);

                        if (telefono == null || telefono.isBlank()) {
                            ps.setNull(k++, Types.VARCHAR);
                        } else {
                            ps.setString(k++, telefono);
                        }

                        ps.setDate(k++, Date.valueOf(fOp));

                        // Sólo nombres
                        for (int i = 0; i < 5; i++) {
                            String v = nombres.get(i);
                            if (v == null) ps.setNull(k++, Types.VARCHAR);
                            else           ps.setString(k++, v);
                        }

                        ps.setString(k++, tipoOperacion);
                        ps.setInt(k++, asesor);
                        ps.setString(k++, status);
                        if (fechaEvento == null) {
                            ps.setNull(k++, Types.DATE);
                        } else {
                            ps.setDate(k++, Date.valueOf(fechaEvento));
                        }

                        ps.executeUpdate();
                        inserted = true;
                    }
                }

                // 3) Descontar existencia DE InventarioObsequios por código
                if (inserted && !codigos.isEmpty()) {
                    descontarExistenciaBatch(cn, codigos);
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

    /** Descuenta 1 unidad por cada código en InventarioObsequios. */
    private void descontarExistenciaBatch(Connection cn, List<String> codigos) throws SQLException {
        if (codigos == null || codigos.isEmpty()) return;

        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE InventarioObsequios " +
                "SET existencia = existencia - 1 " +
                "WHERE codigo_articulo = ?")) {

            for (String cod : codigos) {
                if (cod == null || cod.isBlank()) continue;
                ps.setString(1, cod.trim());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------- helpers ----------
    private static boolean isUnknownColumnError(SQLException ex) {
        // MySQL Unknown column: errorCode=1054, SQLState="42S22"
        return "42S22".equals(ex.getSQLState())
                || ex.getErrorCode() == 1054
                || (ex.getMessage() != null
                    && ex.getMessage().toLowerCase().contains("unknown column"));
    }
}
