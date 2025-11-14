package Conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.nio.file.*;
import java.io.InputStream;
import java.io.IOException;

public class Conecta {

    private static final String DEF_URL  = "jdbc:mysql://localhost:3306/tienda_vestidos";
    private static final String DEF_USER = "root";
    private static final String DEF_PASS = "MIA1234";

    private static volatile Properties cached;

    public static Connection getConnection() throws SQLException {
        Properties p = loadProps();

        // Overrides opcionales por variables de entorno
        String envUrl  = System.getenv("TV_DB_URL");
        String envUser = System.getenv("TV_DB_USER");
        String envPass = System.getenv("TV_DB_PASSWORD");

        String url  = pick(envUrl,  p.getProperty("url",  DEF_URL));
        String user = pick(envUser, p.getProperty("user", DEF_USER));
        String pass = pick(envPass, p.getProperty("password", DEF_PASS));

        return DriverManager.getConnection(url, user, pass);
    }

    private static String pick(String a, String b) {
        return (a != null && !a.trim().isEmpty()) ? a.trim() : b;
    }

    private static Properties loadProps() {
        Properties p = cached;
        if (p != null) return p;

        Properties out = new Properties();

        // Orden de búsqueda:
        // 1) ./config/db.properties (junto al .exe instalado)
        // 2) ./app/config/db.properties (cuando se ejecuta desde carpeta build/app)
        // 3) %APPDATA%/TiendaVestidos/db.properties (fallback por usuario)
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = new Path[] {
                cwd.resolve("config/db.properties"),
                cwd.resolve("app/config/db.properties"),
                Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "TiendaVestidos", "db.properties")
        };

        for (Path pth : candidates) {
            if (Files.isRegularFile(pth)) {
                try (InputStream in = Files.newInputStream(pth)) {
                    out.load(in);
                    cached = out;
                    return out;
                } catch (IOException ignore) { /* usa defaults si falla */ }
            }
        }

        // Defaults si no hay archivo
        cached = out;
        return out;
    }
}
