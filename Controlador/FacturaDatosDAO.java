package Controlador;

import Conexion.Conecta;
import java.sql.*;

public class FacturaDatosDAO {

    public static class Row {
        public Integer numeroNota;
        public String persona;   // 'PF' | 'PM'
        public String rfc;
        public String regimen;   // ej. 601 (tú lo manejas como 4 chars)
        public String usoCfdi;   // ej. G03
        public String codigoPostal;
        public String correo;
        public Timestamp createdAt, updatedAt;
    }

    public Row obtenerPorNota(int numeroNota) throws SQLException {
        String sql = """
            SELECT numero_nota, persona, rfc, regimen, uso_cfdi, codigo_postal, correo, created_at, updated_at
            FROM Factura_Datos WHERE numero_nota=? LIMIT 1
        """;
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public void upsert(int numeroNota, String persona, String rfc,
                       String regimen, String usoCfdi, String codigoPostal, String correo) throws SQLException {
        String sql = """
            INSERT INTO Factura_Datos (numero_nota, persona, rfc, regimen, uso_cfdi, codigo_postal, correo)
            VALUES (?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
              persona=VALUES(persona), rfc=VALUES(rfc),
              regimen=VALUES(regimen), uso_cfdi=VALUES(uso_cfdi),
              codigo_postal=VALUES(codigo_postal),
              correo=VALUES(correo)
        """;
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            int i=1;
            ps.setInt(i++, numeroNota);
            ps.setString(i++, persona);
            ps.setString(i++, rfc);
            ps.setString(i++, regimen);
            ps.setString(i++, usoCfdi);
            ps.setString(i++, codigoPostal);
            ps.setString(i++, correo);
            ps.executeUpdate();
        }
    }

    public void eliminarPorNota(int numeroNota) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement("DELETE FROM Factura_Datos WHERE numero_nota=?")) {
            ps.setInt(1, numeroNota);
            ps.executeUpdate();
        }
    }

    private static Row map(ResultSet rs) throws SQLException {
        Row r = new Row();
        r.numeroNota = rs.getInt("numero_nota");
        r.persona    = rs.getString("persona");
        r.rfc        = rs.getString("rfc");
        r.regimen    = rs.getString("regimen");
        r.usoCfdi    = rs.getString("uso_cfdi");
        r.codigoPostal = rs.getString("codigo_postal");
        r.correo     = rs.getString("correo");
        r.createdAt  = rs.getTimestamp("created_at");
        r.updatedAt  = rs.getTimestamp("updated_at");
        return r;
    }
}
