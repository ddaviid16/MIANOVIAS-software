package Conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conecta {

    private static final String URL = "jdbc:mysql://localhost:3306/tienda_vestidos";
    private static final String USER = "root";
    private static final String PASSWORD = "MIA1234";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
