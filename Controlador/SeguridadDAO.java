package Controlador;

import Conexion.Conecta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Arrays;

/**
 * Maneja la clave de acceso de la aplicación.
 * Guarda solo un hash + salt en la tabla seguridad_app.
 */
public class SeguridadDAO {

    private static final String TABLE_NAME = "seguridad_app";
    private static final int    RECORD_ID  = 1;
    private static final int    SALT_LEN   = 16;

    private static final String SELECT_SQL =
            "SELECT pass_hash, salt FROM " + TABLE_NAME + " WHERE id=?";
    private static final String INSERT_OR_UPDATE_SQL =
            "INSERT INTO " + TABLE_NAME + "(id, pass_hash, salt) VALUES(?,?,?) " +
            "ON DUPLICATE KEY UPDATE pass_hash=VALUES(pass_hash), salt=VALUES(salt)";

    /** ¿Ya hay una clave configurada? */
    public boolean hayPassword() throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT 1 FROM " + TABLE_NAME + " WHERE id=?")) {
            ps.setInt(1, RECORD_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Valida la contraseña ingresada contra el hash almacenado. */
    public boolean validarPassword(char[] password) throws Exception {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(SELECT_SQL)) {
            ps.setInt(1, RECORD_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false; // no configurada
                byte[] hash = rs.getBytes("pass_hash");
                byte[] salt = rs.getBytes("salt");
                byte[] calc = hashPassword(password, salt);
                boolean ok = MessageDigest.isEqual(hash, calc);
                Arrays.fill(calc, (byte) 0);
                return ok;
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Establece una nueva contraseña (sin pedir la anterior). Úsalo solo para configuración inicial. */
    public void establecerNuevaPassword(char[] nuevaPassword) throws Exception {
        byte[] salt = generarSalt();
        byte[] hash = hashPassword(nuevaPassword, salt);

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(INSERT_OR_UPDATE_SQL)) {
            ps.setInt(1, RECORD_ID);
            ps.setBytes(2, hash);
            ps.setBytes(3, salt);
            ps.executeUpdate();
        } finally {
            Arrays.fill(nuevaPassword, '\0');
        }
    }

    /** Cambia la contraseña verificando primero la actual. */
    public boolean cambiarPassword(char[] actual, char[] nueva) throws Exception {
        // copiamos porque validarPassword limpia el arreglo
        char[] copiaActual = Arrays.copyOf(actual, actual.length);
        if (!validarPassword(copiaActual)) {
            Arrays.fill(actual, '\0');
            Arrays.fill(nueva, '\0');
            return false;
        }
        establecerNuevaPassword(nueva);
        Arrays.fill(actual, '\0');
        return true;
    }

    private static byte[] generarSalt() {
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] hashPassword(char[] password, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        byte[] passBytes = new String(password).getBytes(StandardCharsets.UTF_8);
        Arrays.fill(password, '\0');
        return md.digest(passBytes);
    }
}
