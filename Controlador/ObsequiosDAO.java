package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.util.ArrayList;

public class ObsequiosDAO {

    // INSERT legado (sólo nombres)
    private static final String INSERT_SQL_LEGACY =
        "INSERT INTO Obsequios " +
        "(numero_nota, telefono, fecha_operacion, obsequio1, obsequio2, obsequio3, obsequio4, obsequio5, " +
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

    /** Inserta los obsequios elegidos para una nota y descuenta existencia en InventarioObsequios.
     *  Se esperan CÓDIGOS en la lista 'obsequios' (p.ej. ["651234","651235"]).
     *  - Si tu tabla Obsequios tiene columnas *_cod, las usa.
     *  - Si aún no las tienes, hace fallback al INSERT legado (sólo nombres).
     */
    public void insertarParaNota(int numeroNota,
                                 String telefono,
                                 java.time.LocalDate fechaOp,
                                 java.util.List<String> obsequios, // CÓDIGOS como texto
                                 String tipoOperacion,
                                 int asesor,
                                 String status,
                                 java.time.LocalDate fechaEvento) throws java.sql.SQLException {

        try (java.sql.Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                ObsequioInvDAO inv = new ObsequioInvDAO();

                // ---- 1) Convertir textos -> códigos (valida numéricos) y mapear nombres por código
                java.util.List<Integer> codigos = new ArrayList<>();
                java.util.List<String>  nombres = new ArrayList<>();
                java.util.List<String>  src     = (obsequios == null)
                        ? java.util.Collections.<String>emptyList()
                        : obsequios;

                for (String s : src) {
                    if (s == null || s.trim().isEmpty()) continue;
                    String t = s.trim();
                    if (!t.matches("\\d+"))
                        throw new SQLException("Código de obsequio inválido: " + s);
                    int cod = Integer.parseInt(t);
                    // nombre visible para la nota
                    String nom = inv.obtenerNombrePorCodigo(cn, cod);
                    if (nom == null) throw new SQLException("No existe el obsequio código: " + cod);

                    codigos.add(cod);
                    nombres.add(nom);
                }
                // Rellenar a 5 posiciones (tabla tiene 5 columnas de obsequios)
                while (nombres.size() < 5) nombres.add(null);

                // ---- 2) INSERT en Obsequios (intenta con *_cod; si truena por columna desconocida, usa legado)
                boolean inserted = false;
                try (PreparedStatement ps = cn.prepareStatement(INSERT_SQL_WITH_CODES)) {
                    int k = 1;
                    ps.setInt(k++, numeroNota);
                    if (telefono == null || telefono.isBlank()) ps.setNull(k++, Types.VARCHAR); else ps.setString(k++, telefono);
                    ps.setDate(k++, java.sql.Date.valueOf(fechaOp != null ? fechaOp : java.time.LocalDate.now()));

                    // Nombres (5)
                    for (int i = 0; i < 5; i++) {
                        String v = nombres.get(i);
                        if (v == null) ps.setNull(k++, Types.VARCHAR); else ps.setString(k++, v);
                    }
                    // Códigos (5) — si no hay suficientes, van null
                    for (int i = 0; i < 5; i++) {
                        Integer cod = (i < codigos.size()) ? codigos.get(i) : null;
                        if (cod == null) ps.setNull(k++, Types.INTEGER); else ps.setInt(k++, cod);
                    }

                    ps.setString(k++, tipoOperacion);
                    ps.setInt(k++, asesor);
                    ps.setString(k++, status);
                    if (fechaEvento == null) ps.setNull(k++, Types.DATE); else ps.setDate(k++, java.sql.Date.valueOf(fechaEvento));

                    ps.executeUpdate();
                    inserted = true;
                } catch (SQLException ex) {
                    // Si la tabla no tiene columnas *_cod todavía, harás fallback automáticamente
                    if (isUnknownColumnError(ex)) {
                        try (PreparedStatement ps = cn.prepareStatement(INSERT_SQL_LEGACY)) {
                            int k = 1;
                            ps.setInt(k++, numeroNota);
                            if (telefono == null || telefono.isBlank()) ps.setNull(k++, Types.VARCHAR); else ps.setString(k++, telefono);
                            ps.setDate(k++, java.sql.Date.valueOf(fechaOp != null ? fechaOp : java.time.LocalDate.now()));
                            for (int i = 0; i < 5; i++) {
                                String v = nombres.get(i);
                                if (v == null) ps.setNull(k++, Types.VARCHAR); else ps.setString(k++, v);
                            }
                            ps.setString(k++, tipoOperacion);
                            ps.setInt(k++, asesor);
                            ps.setString(k++, status);
                            if (fechaEvento == null) ps.setNull(k++, Types.DATE); else ps.setDate(k++, java.sql.Date.valueOf(fechaEvento));
                            ps.executeUpdate();
                            inserted = true;
                        }
                    } else {
                        throw ex;
                    }
                }

                // ---- 3) Descontar existencia **por código** (uno por cada seleccionado)
                if (inserted && !codigos.isEmpty()) {
                    inv.descontarExistenciaBatch(cn, codigos);
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

    // ---------- helpers ----------
    private static boolean isUnknownColumnError(SQLException ex) {
        // MySQL Unknown column: errorCode=1054, SQLState="42S22"
        return "42S22".equals(ex.getSQLState()) || String.valueOf(ex.getErrorCode()).equals("1054")
               || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column"));
    }
}
