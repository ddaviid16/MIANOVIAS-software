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
    private static final String INSERT_SQL_WITH_CODES =
        "INSERT INTO Obsequios " +
        "(numero_nota, telefono, fecha_operacion, " +
        " obsequio1, obsequio2, obsequio3, obsequio4, obsequio5, " +
        " obsequio1_cod, obsequio2_cod, obsequio3_cod, obsequio4_cod, obsequio5_cod, " +
        " tipo_operacion, asesor, status, fecha_evento) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * Inserta los obsequios elegidos para una nota.
     *
     * YA NO:
     *  - Descuenta existencia de InventarioObsequios.
     *  - Usa el status recibido: siempre guarda "A".
     */
    public void insertarParaNota(int numeroNota,
                                 String telefono,
                                 LocalDate fechaOp,
                                 List<String> obsequios,
                                 String tipoOperacion,
                                 int asesor,
                                 String status,       // se ignora, siempre se usa "A"
                                 LocalDate fechaEvento) throws SQLException {

        if (obsequios == null || obsequios.isEmpty()) {
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

                        if (!code.matches("[A-Za-z0-9-]+")) {
                            throw new SQLException("Código de obsequio inválido: " + code);
                        }

                        psNom.setString(1, code);
                        try (ResultSet rs = psNom.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("No existe el obsequio código: " + code);
                            }
                            String nombre = rs.getString(1);
                            codigos.add(code);
                            nombres.add(nombre);
                        }

                        if (codigos.size() == 5) break;
                    }
                }

                while (nombres.size() < 5) nombres.add(null);

                boolean inserted = false;
                LocalDate fOp = (fechaOp != null) ? fechaOp : LocalDate.now();

                // 2) INSERT en Obsequios (versión con *_cod)
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

                    // Códigos (5)
                    for (int i = 0; i < 5; i++) {
                        String cod = (i < codigos.size()) ? codigos.get(i) : null;
                        if (cod == null) ps.setNull(k++, Types.VARCHAR);
                        else             ps.setString(k++, cod);
                    }

                    ps.setString(k++, tipoOperacion);
                    ps.setInt(k++, asesor);
                    ps.setString(k++, "A");  // <-- SIEMPRE "A"

                    if (fechaEvento == null) {
                        ps.setNull(k++, Types.DATE);
                    } else {
                        ps.setDate(k++, Date.valueOf(fechaEvento));
                    }

                    ps.executeUpdate();
                    inserted = true;

                } catch (SQLException ex) {
                    // Si no existen las columnas *_cod, se usa el INSERT legado
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

                        for (int i = 0; i < 5; i++) {
                            String v = nombres.get(i);
                            if (v == null) ps.setNull(k++, Types.VARCHAR);
                            else           ps.setString(k++, v);
                        }

                        ps.setString(k++, tipoOperacion);
                        ps.setInt(k++, asesor);
                        ps.setString(k++, "A"); // <-- también siempre "A"

                        if (fechaEvento == null) {
                            ps.setNull(k++, Types.DATE);
                        } else {
                            ps.setDate(k++, Date.valueOf(fechaEvento));
                        }

                        ps.executeUpdate();
                        inserted = true;
                    }
                }

                // 3) YA NO SE DESCUENTA EXISTENCIA EN InventarioObsequios

                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
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
