package Utilidades;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public final class BackupService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private BackupService() {}

    /**
     * Genera un respaldo .sql completo en la carpeta indicada.
     * @return Path del archivo .sql creado.
     * @throws IOException si mysqldump no se encuentra o falla.
     */
    public static Path respaldar(Path carpetaDestino) throws IOException {
        Properties props = cargarProps();
        String[] creds  = parsearUrl(props.getProperty("url", "jdbc:mysql://localhost:3306/tienda_vestidos"));
        String host     = creds[0];
        String port     = creds[1];
        String dbName   = creds[2];
        String user     = props.getProperty("user",     "root");
        String pass     = props.getProperty("password", "MIA1234");

        Path mysqldump = encontrarMysqldump(props);
        if (mysqldump == null)
            throw new IOException(
                "No se encontró mysqldump.exe.\n" +
                "Agrega la propiedad mysql.bin=C:\\...\\bin en config/db.properties " +
                "o verifica que MySQL esté instalado.");

        Files.createDirectories(carpetaDestino);
        String nombre  = "respaldo_" + LocalDateTime.now().format(FMT) + ".sql";
        Path   archivo = carpetaDestino.resolve(nombre);

        ProcessBuilder pb = new ProcessBuilder(
            mysqldump.toString(),
            "-h", host,
            "-P", port,
            "-u", user,
            "--password=" + pass,
            "--single-transaction",
            "--routines",
            "--triggers",
            "--result-file=" + archivo.toAbsolutePath(),
            dbName
        );
        pb.redirectErrorStream(false);

        Process proc = pb.start();

        // Consumir stderr para evitar bloqueo del proceso
        StringBuilder errBuf = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Ignorar advertencia de contraseña en línea de comandos
                if (!line.contains("Using a password on the command line"))
                    errBuf.append(line).append("\n");
            }
        }

        int exit;
        try {
            exit = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("El respaldo fue interrumpido.");
        }

        if (exit != 0) {
            throw new IOException("mysqldump terminó con error (código " + exit + "):\n" + errBuf.toString().trim());
        }
        return archivo;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static Path encontrarMysqldump(Properties props) {
        // 1) Ruta configurada por el usuario en db.properties  (mysql.bin = C:\...\bin)
        String mysqlBin = props.getProperty("mysql.bin");
        if (mysqlBin != null && !mysqlBin.isBlank()) {
            Path p = Paths.get(mysqlBin.trim(), "mysqldump.exe");
            if (Files.isExecutable(p)) return p;
        }

        // 2) Rutas comunes en Windows
        String[] dirs = {
            "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin",
            "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin",
            "C:\\Program Files\\MySQL\\MySQL Server 8.3\\bin",
            "C:\\Program Files\\MySQL\\MySQL Server 8.2\\bin",
            "C:\\Program Files\\MySQL\\MySQL Server 8.1\\bin",
            "C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin",
            "C:\\xampp\\mysql\\bin",
            "C:\\wamp64\\bin\\mysql\\mysql8.0.31\\bin",
        };
        for (String dir : dirs) {
            Path p = Paths.get(dir, "mysqldump.exe");
            if (Files.isExecutable(p)) return p;
        }

        // 3) PATH del sistema
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String part : pathEnv.split(File.pathSeparator)) {
                Path p = Paths.get(part.trim(), "mysqldump.exe");
                if (Files.isExecutable(p)) return p;
            }
        }
        return null;
    }

    private static String[] parsearUrl(String url) {
        // jdbc:mysql://host:port/dbname[?params]
        String host = "localhost", port = "3306", db = "tienda_vestidos";
        try {
            String stripped = url.replace("jdbc:mysql://", "");
            int slash = stripped.indexOf('/');
            if (slash > 0) {
                String hostPort = stripped.substring(0, slash);
                String dbPart   = stripped.substring(slash + 1).replaceAll("\\?.*", "");
                if (!dbPart.isBlank()) db = dbPart;
                int colon = hostPort.lastIndexOf(':');
                if (colon > 0) {
                    host = hostPort.substring(0, colon);
                    port = hostPort.substring(colon + 1);
                } else {
                    host = hostPort;
                }
            }
        } catch (Exception ignored) {}
        return new String[]{host, port, db};
    }

    private static Properties cargarProps() {
        Properties out = new Properties();
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
            cwd.resolve("config/db.properties"),
            cwd.resolve("app/config/db.properties"),
            Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "TiendaVestidos", "db.properties")
        };
        for (Path pth : candidates) {
            if (Files.isRegularFile(pth)) {
                try (InputStream in = Files.newInputStream(pth)) {
                    out.load(in);
                    return out;
                } catch (IOException ignored) {}
            }
        }
        return out;
    }
}
