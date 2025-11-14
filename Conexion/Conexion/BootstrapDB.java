package Conexion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

public final class BootstrapDB {

    private BootstrapDB() {}

    public static void ensure() {
        // 1) Intenta conectar normalmente (DB ya creada)
        try (Connection ignored = Conecta.getConnection()) {
            return; // ya existe, no hay nada que hacer
        } catch (SQLException ex) {
            // Si es "Unknown database", seguimos con bootstrap. Otros errores: re-lanzar.
            if (!isUnknownDatabase(ex)) {
                // Puedes loguear si quieres
                return; // no reventemos la UI por aquí; tus DAOs ya mostrarán el error.
            }
        }

        // 2) Cargar props igual que Conecta
        Props cfg = loadPropsLikeConecta();

        // 3) Construir URL a nivel servidor (sin schema)
        String serverUrl = toServerUrl(cfg.url);

        // 4) Leer el SQL del classpath
        String sql = readResource("/sql/schema-tienda_vestidos.sql");
        if (sql == null || sql.isBlank()) return;

        // 5) Ejecutar statements uno por uno
        try (Connection cn = DriverManager.getConnection(serverUrl, cfg.user, cfg.pass);
             Statement st = cn.createStatement()) {

            for (String stmt : splitSql(sql)) {
                String s = stmt.trim();
                if (s.isEmpty()) continue;
                st.execute(s);
            }
        } catch (SQLException e) {
            // aquí podrías guardar un log en %APPDATA% si lo necesitas
            return;
        }

        // 6) Segundo intento ya contra la DB creada (opcional)
        try (Connection ignored = Conecta.getConnection()) {
            // ok
        } catch (SQLException ignore) {
        }
    }

    /* ===== Helpers ===== */

    private static boolean isUnknownDatabase(SQLException ex) {
        // MySQL: error 1049, SQLState "42000"
        SQLException e = ex;
        while (e != null) {
            if (e.getErrorCode() == 1049) return true;
            e = e.getNextException();
        }
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase().contains("unknown database");
    }

    private static String toServerUrl(String url) {
        // jdbc:mysql://host:port/db?params  -> jdbc:mysql://host:port/?params
        if (url == null) return "jdbc:mysql://localhost:3306/";
        int q = url.indexOf('?');
        String base = (q >= 0) ? url.substring(0, q) : url;
        int slash = base.lastIndexOf('/');
        if (slash > "jdbc:mysql://".length()-1) {
            String prefix = base.substring(0, slash + 1); // incluye la barra final
            String tail = (q >= 0) ? url.substring(q) : "";
            return prefix + tail;
        }
        return url; // peor caso
    }

    private static String readResource(String path) {
        try (InputStream in = BootstrapDB.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String[] splitSql(String sql) {
        // elimina comentarios de línea simples y divide por ';'
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new StringReader(sql))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.startsWith("--") || s.startsWith("#")) continue;
                sb.append(line).append('\n');
            }
        } catch (IOException ignore) {}
        return sb.toString().split(";(\\s*\\r?\\n)");
    }

    private record Props(String url, String user, String pass) {}

    private static Props loadPropsLikeConecta() {
        // Mismo orden que Conecta.java
        Properties out = new Properties();
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = new Path[] {
            cwd.resolve("config/db.properties"),
            cwd.resolve("app/config/db.properties"),
            Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "TiendaVestidos", "db.properties")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) { out.load(in); break; } catch (IOException ignore) {}
            }
        }
        String url  = out.getProperty("url",  "jdbc:mysql://localhost:3306/tienda_vestidos?allowPublicKeyRetrieval=true&useSSL=false");
        String user = out.getProperty("user", "root");
        String pass = out.getProperty("password", "MIA1234");
        return new Props(url, user, pass);
    }
}
