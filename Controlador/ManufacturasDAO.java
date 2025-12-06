package Controlador;

import Conexion.Conecta;
import Modelo.Manufactura;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ManufacturasDAO {

    /** Inserta una manufactura y devuelve el id generado. */
public int insertar(Manufactura m) throws SQLException {
    String sql = "INSERT INTO manufacturas " +
                 "(numero_nota, articulo, descripcion, precio, descuento, " +
                 " fecha_registro, fecha_entrega, observaciones, telefono, status) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    try (Connection con = Conecta.getConnection();
         PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        // 1) numero_nota
        ps.setInt(1, m.getNumeroNota());

        // 2) articulo
        ps.setString(2, m.getArticulo());

        // 3) descripcion
        ps.setString(3, m.getDescripcion());

        // 4) precio
        ps.setBigDecimal(4, java.math.BigDecimal.valueOf(m.getPrecio()));

        // 5) descuento (puede ser null)
        if (m.getDescuento() != null) {
            ps.setBigDecimal(5, java.math.BigDecimal.valueOf(m.getDescuento()));
        } else {
            ps.setNull(5, Types.DECIMAL);
        }

        // 6) fecha_registro (NOT NULL en la tabla, no la dejes en null)
        LocalDate fr = m.getFechaRegistro();
        if (fr != null) {
            ps.setDate(6, Date.valueOf(fr));
        } else {
            // o lanzas excepción, según como quieras forzar esto
            ps.setDate(6, new Date(System.currentTimeMillis()));
        }

        // 7) fecha_entrega (nullable)
        LocalDate fe = m.getFechaEntrega();
        if (fe != null) {
            ps.setDate(7, Date.valueOf(fe));
        } else {
            ps.setNull(7, Types.DATE);
        }

        // 8) observaciones
        if (m.getObservaciones() != null && !m.getObservaciones().isEmpty()) {
            ps.setString(8, m.getObservaciones());
        } else {
            ps.setNull(8, Types.VARCHAR);
        }

        // 9) telefono
        if (m.getTelefono() != null && !m.getTelefono().isEmpty()) {
            ps.setString(9, m.getTelefono());
        } else {
            ps.setNull(9, Types.VARCHAR);
        }

        // 10) status (ENUM('A','C'))
        String status = m.getStatus();
        if (status == null || status.isBlank()) {
            status = "A";  // default de la tabla
        }
        ps.setString(10, status);

        ps.executeUpdate();

        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
                int id = rs.getInt(1);
                m.setIdManufactura(id);
                return id;
            }
        }
    }

    return -1;
}

    /** Manufacturas asociadas a una nota. */
    public List<Manufactura> listarPorNota(int numeroNota) throws SQLException {
        String sql = "SELECT id_manufactura, numero_nota, articulo, descripcion, precio, descuento," +
                     "       fecha_registro, fecha_entrega, observaciones,telefono, status " +
                     "FROM manufacturas " +
                     "WHERE numero_nota = ? " +
                     "ORDER BY id_manufactura";

        List<Manufactura> lista = new ArrayList<>();

        try (Connection con = Conecta.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, numeroNota);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Manufactura m = new Manufactura();
                    m.setIdManufactura(rs.getInt("id_manufactura"));
                    m.setNumeroNota(rs.getInt("numero_nota"));
                    m.setArticulo(rs.getString("articulo"));
                    m.setDescripcion(rs.getString("descripcion"));
                    m.setPrecio(rs.getBigDecimal("precio").doubleValue());
                    java.math.BigDecimal desc = rs.getBigDecimal("descuento");
                    if (desc != null) {
                        m.setDescuento(desc.doubleValue());
                    } else {
                        m.setDescuento(0.0); // o null si usas Double en el modelo
                    }

                    

                    Date fr = rs.getDate("fecha_registro");
                    if (fr != null) {
                        m.setFechaRegistro(fr.toLocalDate());
                    }

                    Date fe = rs.getDate("fecha_entrega");
                    if (fe != null) {
                        m.setFechaEntrega(fe.toLocalDate());
                    }

                    m.setObservaciones(rs.getString("observaciones"));
                    m.setTelefono(rs.getString("telefono"));
                    m.setStatus(rs.getString("status"));

                    lista.add(m);
                }
            }
        }

        return lista;
    }

    /** Borra todas las manufacturas de una nota (por si cancelas la venta). */
    public void eliminarPorNota(int numeroNota) throws SQLException {
        String sql = "DELETE FROM manufacturas WHERE numero_nota = ?";

        try (Connection con = Conecta.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, numeroNota);
            ps.executeUpdate();
        }
    }

    /** Actualiza solo el estado de una manufactura. */
    public void actualizarEstado(int idManufactura, String nuevoEstado) throws SQLException {
        String sql = "UPDATE manufacturas SET status = ? WHERE id_manufactura = ?";

        try (Connection con = Conecta.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nuevoEstado);
            ps.setInt(2, idManufactura);
            ps.executeUpdate();
        }
    }
}
