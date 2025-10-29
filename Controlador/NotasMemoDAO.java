// Controlador/NotasMemoDAO.java
package Controlador;

import java.sql.*;

public class NotasMemoDAO {

    public void upsert(int numeroNota, String memo) throws SQLException {
        if (memo == null) memo = "";
        final String sql = """
            INSERT INTO Notas_Memo (numero_nota, memo)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE memo = VALUES(memo)
        """;
        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            ps.setString(2, memo);
            ps.executeUpdate();
        }
    }

    public String obtener(int numeroNota) throws SQLException {
        final String sql = "SELECT memo FROM Notas_Memo WHERE numero_nota = ?";
        try (Connection cn = Conexion.Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
