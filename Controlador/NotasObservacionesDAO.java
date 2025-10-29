package Controlador;

import Conexion.Conecta;
import java.sql.*;

public class NotasObservacionesDAO {

    public void upsert(int numeroNota, String texto) throws SQLException {
        if (texto == null || texto.trim().isEmpty()) return;
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                 INSERT INTO notas_observaciones (numero_nota, observaciones)
                 VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE observaciones = VALUES(observaciones)
             """)) {
            ps.setInt(1, numeroNota);
            ps.setString(2, texto);
            ps.executeUpdate();
        }
    }

    public String getByNota(int numeroNota) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                 "SELECT observaciones FROM notas_observaciones WHERE numero_nota=?")) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
