package Controlador;

import Conexion.Conecta;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReporteVentasDAO {

    public static class Row {
        public int numeroNota;
        public String nombreCliente;
        public String asesorNombre;
        public LocalDate fecha;
        public double total;
    }

    public List<Row> listarVentas(LocalDate fechaDesde, LocalDate fechaHasta, String articulo, String vendedor, String cliente) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ventas WHERE fecha >= ? AND fecha <= ?");
        // Agregar filtros adicionales según los parámetros
        if (articulo != null && !articulo.isEmpty()) {
            sql.append(" AND articulo = ?");
        }
        if (vendedor != null && !vendedor.isEmpty()) {
            sql.append(" AND vendedor = ?");
        }
        if (cliente != null && !cliente.isEmpty()) {
            sql.append(" AND cliente = ?");
        }

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {

            ps.setDate(1, Date.valueOf(fechaDesde));
            ps.setDate(2, Date.valueOf(fechaHasta));

            // Ajustar los parámetros de la consulta según el filtro
            int paramIndex = 3;
            if (articulo != null && !articulo.isEmpty()) {
                ps.setString(paramIndex++, articulo);
            }
            if (vendedor != null && !vendedor.isEmpty()) {
                ps.setString(paramIndex++, vendedor);
            }
            if (cliente != null && !cliente.isEmpty()) {
                ps.setString(paramIndex++, cliente);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<Row> result = new ArrayList<>();
                while (rs.next()) {
                    Row row = new Row();
                    row.numeroNota = rs.getInt("numero_nota");
                    row.nombreCliente = rs.getString("cliente");
                    row.asesorNombre = rs.getString("vendedor");
                    row.fecha = rs.getDate("fecha").toLocalDate();
                    row.total = rs.getDouble("total");
                    result.add(row);
                }
                return result;
            }
        }
    }
}
