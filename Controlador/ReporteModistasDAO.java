package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReporteModistasDAO {

    // Una fila del reporte
    public static class ModistaRow {
        public int idManufactura;
        public int numeroNota;
        public String folio;
        public String telefono;
        public LocalDate fechaRegistro;
        public LocalDate fechaEntrega;
        public String articulo;
        public String descripcion;
        public Double precio;
        public Double descuento;
        public String observaciones;

        // Datos del cliente
        public String cliente;       // nombre + apellidos
        public LocalDate fechaEvento;
    }

    /**
     * Lista las manufacturas activas en el rango [ini, fin] usando fecha_entrega.
     */
    public List<ModistaRow> listarPorRango(LocalDate ini, LocalDate fin) throws SQLException {
        if (ini == null || fin == null) {
            throw new IllegalArgumentException("Fechas requeridas");
        }

        String sql =
            "SELECT " +
            "  m.id_manufactura, m.numero_nota, m.articulo, m.descripcion, " +
            "  m.precio, m.descuento, m.fecha_registro, m.fecha_entrega, " +
            "  m.observaciones, m.telefono, m.status, " +
            "  n.folio, " +
            "  CONCAT(COALESCE(c.nombre,''),' ',COALESCE(c.apellido_paterno,''),' ',COALESCE(c.apellido_materno,'')) AS nombre_cliente, " +
            "  c.fecha_evento " +
            "FROM manufacturas m " +
            "JOIN Notas n ON n.numero_nota = m.numero_nota " +
            "LEFT JOIN Clientes c ON c.telefono1 = n.telefono " +
            "WHERE m.status = 'A' " +
            "  AND m.fecha_entrega >= ? AND m.fecha_entrega <= ? " +
            "ORDER BY m.fecha_entrega, m.id_manufactura";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(ini));
            ps.setDate(2, Date.valueOf(fin));

            try (ResultSet rs = ps.executeQuery()) {
                List<ModistaRow> out = new ArrayList<>();
                while (rs.next()) {
                    ModistaRow r = new ModistaRow();
                    r.idManufactura = rs.getInt("id_manufactura");
                    r.numeroNota    = rs.getInt("numero_nota");
                    r.articulo      = rs.getString("articulo");
                    r.descripcion   = rs.getString("descripcion");

                    java.math.BigDecimal bdPrecio = rs.getBigDecimal("precio");
                    r.precio = (bdPrecio == null ? null : bdPrecio.doubleValue());

                    java.math.BigDecimal bdDesc = rs.getBigDecimal("descuento");
                    r.descuento = (bdDesc == null ? null : bdDesc.doubleValue());

                    Date fr = rs.getDate("fecha_registro");
                    r.fechaRegistro = (fr == null ? null : fr.toLocalDate());

                    Date fe = rs.getDate("fecha_entrega");
                    r.fechaEntrega = (fe == null ? null : fe.toLocalDate());

                    r.observaciones = rs.getString("observaciones");
                    r.telefono      = rs.getString("telefono");

                    r.folio         = rs.getString("folio");
                    r.cliente       = rs.getString("nombre_cliente");
                    Date fev = rs.getDate("fecha_evento");
                    r.fechaEvento = (fev == null ? null : fev.toLocalDate());

                    out.add(r);
                }
                return out;
            }
        }
    }
}
