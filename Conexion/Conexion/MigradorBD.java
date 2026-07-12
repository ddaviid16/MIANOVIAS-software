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
            "DEFAULT NULL"),

        new Migracion(3,
            "Crear tabla manufacturas (artículos MODISTA) si falta",
            "CREATE TABLE IF NOT EXISTS manufacturas (" +
            "  id_manufactura INT NOT NULL AUTO_INCREMENT, " +
            "  numero_nota    INT NOT NULL, " +
            "  articulo       VARCHAR(100) NOT NULL, " +
            "  descripcion    VARCHAR(100) DEFAULT NULL, " +
            "  precio         DECIMAL(10,2) NOT NULL, " +
            "  descuento      DECIMAL(5,2) DEFAULT NULL, " +
            "  fecha_registro DATE NOT NULL, " +
            "  fecha_entrega  DATE DEFAULT NULL, " +
            "  observaciones  VARCHAR(255) DEFAULT NULL, " +
            "  telefono       VARCHAR(15) DEFAULT NULL, " +
            "  status         ENUM('A','C') DEFAULT 'A', " +
            "  PRIMARY KEY (id_manufactura), " +
            "  KEY fk_manufacturas_notas (numero_nota), " +
            "  KEY fk_manufacturas (telefono), " +
            "  CONSTRAINT fk_manufacturas FOREIGN KEY (telefono) REFERENCES clientes (telefono1), " +
            "  CONSTRAINT fk_manufacturas_notas FOREIGN KEY (numero_nota) REFERENCES notas (numero_nota) ON DELETE CASCADE ON UPDATE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"),

        new Migracion(4,
            "Crear tabla historialcliente (saldo/historial migrado) si falta",
            "CREATE TABLE IF NOT EXISTS historialcliente (" +
            "  telefono1     VARCHAR(15) NOT NULL, " +
            "  saldo_migrado DECIMAL(10,2) DEFAULT NULL, " +
            "  fecha_saldo   DATE DEFAULT NULL, " +
            "  obsequios     TEXT, " +
            "  observacion   TEXT, " +
            "  PRIMARY KEY (telefono1), " +
            "  CONSTRAINT fk_hist_cli FOREIGN KEY (telefono1) REFERENCES clientes (telefono1)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci")

        // ── PRÓXIMAS MIGRACIONES ────────────────────────────────────────────
        // new Migracion(5, "Descripción del cambio", "ALTER TABLE ..."),
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
