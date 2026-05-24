package Conexion;

import java.sql.*;

/**
 * Sistema de migraciones de base de datos.
 * Se ejecuta al arrancar la app (después de BootstrapDB.ensure()).
 * Aplica cada cambio de BD exactamente una vez y lo registra en
 * la tabla schema_migrations para no repetirlo en futuras actualizaciones.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  REGLA DE ORO: SOLO cambios ADITIVOS (ADD COLUMN, MODIFY,       │
 * │  CREATE TABLE IF NOT EXISTS, CREATE INDEX IF NOT EXISTS…).      │
 * │  NUNCA: DROP TABLE, DELETE, TRUNCATE, RENAME que borre datos.   │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Para agregar un cambio en la próxima versión:
 *   1. Aumenta el número de versión.
 *   2. Agrega una nueva Migracion al arreglo MIGRACIONES.
 *   3. Compila y genera el instalador.
 */
public final class MigradorBD {

    private record Migracion(int version, String descripcion, String sql) {}

    // ─────────────────────────────────────────────────────────────────────────
    //  HISTORIAL DE MIGRACIONES
    //  sql = null  →  línea base (no ejecuta SQL, solo registra el número)
    // ─────────────────────────────────────────────────────────────────────────
    private static final Migracion[] MIGRACIONES = {

        new Migracion(1,
            "Schema base — instalación inicial (línea base)",
            null), // El schema inicial lo crea BootstrapDB.ensure()

        new Migracion(2,
            "Agregar FACEBOOK e INSTAGRAM a como_se_entero",
            "ALTER TABLE clientes MODIFY COLUMN como_se_entero " +
            "ENUM('UBICACION','RECOMENDACION','GOOGLE MAPS','TIKTOK','FACEBOOK','INSTAGRAM') " +
            "DEFAULT NULL")

        // ── PRÓXIMAS MIGRACIONES ────────────────────────────────────────────
        // new Migracion(3, "Descripción del cambio", "ALTER TABLE ..."),
    };

    private MigradorBD() {}

    /** Punto de entrada. Llamar una vez al inicio, tras BootstrapDB.ensure(). */
    public static void migrate() {
        try (Connection cn = Conecta.getConnection()) {
            crearTablaHistorial(cn);
            for (Migracion m : MIGRACIONES) {
                if (m.sql() == null) {
                    marcarSiNoEsta(cn, m);
                    continue;
                }
                if (!estaAplicada(cn, m.version())) {
                    aplicar(cn, m);
                }
            }
        } catch (SQLException e) {
            System.err.println("[MigradorBD] Error al migrar: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void crearTablaHistorial(Connection cn) throws SQLException {
        try (Statement st = cn.createStatement()) {
            st.execute(
                "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                "  version     INT          NOT NULL, " +
                "  descripcion VARCHAR(200) NOT NULL, " +
                "  aplicada_en DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "  PRIMARY KEY (version)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    private static boolean estaAplicada(Connection cn, int version) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void marcarSiNoEsta(Connection cn, Migracion m) throws SQLException {
        if (!estaAplicada(cn, m.version())) registrar(cn, m);
    }

    private static void aplicar(Connection cn, Migracion m) throws SQLException {
        try (Statement st = cn.createStatement()) {
            st.execute(m.sql());
        }
        registrar(cn, m);
        System.out.println("[MigradorBD] V" + m.version() + " aplicada: " + m.descripcion());
    }

    private static void registrar(Connection cn, Migracion m) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT IGNORE INTO schema_migrations (version, descripcion) VALUES (?, ?)")) {
            ps.setInt(1, m.version());
            ps.setString(2, m.descripcion());
            ps.executeUpdate();
        }
    }
}
