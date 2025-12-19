package Controlador;

import Conexion.Conecta;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servicio para cambiar el "origen" de un renglón de una nota:
 *
 *  - INVENTARIO -> PEDIDO:
 *      * Regresa 1 pieza al Inventario (existencia + 1)
 *      * Inserta un renglón en Pedidos con las mismas características
 *        (código_articulo = NULL)
 *      * Marca el renglón original de Nota_Detalle como status = 'C'
 *
 *  - PEDIDO -> INVENTARIO:
 *      * Busca un artículo compatible en Inventarios (status='A', existencia>0)
 *      * Descuenta 1 pieza de ese inventario
 *      * Inserta un renglón en Nota_Detalle con ese código_articulo
 *      * Marca el Pedido como status = 'C'
 *
 * Todo se hace dentro de UNA sola transacción.
 */
public class CambioCodigoService {

    private final InventarioDAO inventarioDAO = new InventarioDAO();

    // ========================= PUBLIC API =========================

    /**
     * Convierte un renglón de INVENTARIO (Nota_Detalle) a PEDIDO.
     *
     * @param numeroNota número de nota
     * @param idDetalle  id de Nota_Detalle (tabla Nota_Detalle.id)
     */
    public void convertirInventarioAPedido(int numeroNota, int idDetalle) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                convertirInventarioAPedidoTx(cn, numeroNota, idDetalle);
                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }

    /**
     * Convierte un renglón de PEDIDO (Pedidos.id) a INVENTARIO.
     *
     * @param numeroNota número de nota
     * @param idPedido   id de Pedidos.id
     */
    public void convertirPedidoAInventario(int numeroNota, int idPedido) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                convertirPedidoAInventarioTx(cn, numeroNota, idPedido);
                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }

    // ========================= LÓGICA INTERNA =========================

    private void convertirInventarioAPedidoTx(Connection cn, int numeroNota, int idDetalle) throws SQLException {

        // 1) Leer el renglón de Nota_Detalle + teléfono de la nota
        String sqlDet =
            "SELECT d.codigo_articulo, d.articulo, d.marca, d.modelo, d.talla, d.color, " +
            "       d.precio, d.descuento, d.subtotal, d.fecha_evento, n.telefono " +
            "FROM Nota_Detalle d " +
            "JOIN Notas n ON n.numero_nota = d.numero_nota " +
            "WHERE d.id = ? AND d.numero_nota = ? AND COALESCE(d.status,'A') = 'A'";

        String codArticulo;
        String articulo;
        String marca;
        String modelo;
        String talla;
        String color;
        Double precio;
        Double descuento;
        java.sql.Date fechaEvento;
        String telefono;

        try (PreparedStatement ps = cn.prepareStatement(sqlDet)) {
            ps.setInt(1, idDetalle);
            ps.setInt(2, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se encontró el renglón activo en Nota_Detalle (id=" + idDetalle + ").");
                }
                codArticulo  = rs.getString("codigo_articulo");
                articulo     = rs.getString("articulo");
                marca        = rs.getString("marca");
                modelo       = rs.getString("modelo");
                talla        = rs.getString("talla");
                color        = rs.getString("color");
                precio       = getDoubleOrNull(rs, "precio");
                descuento    = getDoubleOrNull(rs, "descuento");
                fechaEvento  = rs.getDate("fecha_evento");
                telefono     = rs.getString("telefono");
            }
        }

        if (codArticulo == null || codArticulo.isBlank()) {
            throw new SQLException("El renglón seleccionado no tiene codigo_articulo; no parece ser de inventario.");
        }

        if (precio == null) precio = 0.0;
        if (descuento == null) descuento = 0.0;

        // 2) Regresar existencia al inventario
        inventarioDAO.incrementarExistencia(cn, codArticulo, 1);

        // 3) Insertar en Pedidos (con codigo_articulo = NULL)
        String sqlInsPed =
            "INSERT INTO Pedidos (" +
            "  numero_nota, fecha_registro, articulo, marca, modelo, talla, color, " +
            "  precio, descuento, fecha_evento, telefono, codigo_articulo, status, en_tienda, en_tienda_ts" +
            ") VALUES ( ?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'A', 'N', NULL )";

        try (PreparedStatement ps = cn.prepareStatement(sqlInsPed)) {
            int k = 1;
            ps.setInt(k++, numeroNota);
            ps.setString(k++, articulo);
            ps.setString(k++, marca);
            ps.setString(k++, modelo);
            ps.setString(k++, talla);
            ps.setString(k++, color);
            ps.setBigDecimal(k++, new java.math.BigDecimal(precio));
            ps.setBigDecimal(k++, new java.math.BigDecimal(descuento));
            if (fechaEvento == null) ps.setNull(k++, java.sql.Types.DATE);
            else ps.setDate(k++, fechaEvento);
            ps.setString(k++, telefono);
            ps.executeUpdate();
        }

        // 4) Marcar el renglón original como cancelado
        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE Nota_Detalle SET status='C' WHERE id=?")) {
            ps.setInt(1, idDetalle);
            ps.executeUpdate();
        }
    }

    private void convertirPedidoAInventarioTx(Connection cn, int numeroNota, int idPedido) throws SQLException {

        // 1) Leer datos del pedido
        String sqlPed =
            "SELECT articulo, marca, modelo, talla, color, precio, descuento, " +
            "       fecha_evento, telefono " +
            "FROM Pedidos " +
            "WHERE id = ? AND numero_nota = ? AND COALESCE(status,'A') = 'A'";

        String articulo;
        String marca;
        String modelo;
        String talla;
        String color;
        Double precio;
        Double descuento;
        Date fechaEvento;
        String telefono;

        try (PreparedStatement ps = cn.prepareStatement(sqlPed)) {
            ps.setInt(1, idPedido);
            ps.setInt(2, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se encontró el pedido activo (id=" + idPedido + ").");
                }
                articulo    = rs.getString("articulo");
                marca       = rs.getString("marca");
                modelo      = rs.getString("modelo");
                talla       = rs.getString("talla");
                color       = rs.getString("color");
                precio      = getDoubleOrNull(rs, "precio");
                descuento   = getDoubleOrNull(rs, "descuento");
                fechaEvento = rs.getDate("fecha_evento");
                telefono    = rs.getString("telefono");
            }
        }

        if (precio == null) precio = 0.0;
        if (descuento == null) descuento = 0.0;

        // 2) Buscar un inventario compatible con existencia > 0
        String sqlFindInv =
            "SELECT codigo_articulo " +
            "FROM Inventarios " +
            "WHERE status='A' AND COALESCE(existencia,0) > 0 " +
            "  AND UPPER(TRIM(articulo)) = UPPER(TRIM(?)) " +
            "  AND UPPER(TRIM(modelo))  = UPPER(TRIM(?)) " +
            "  AND UPPER(TRIM(talla))   = UPPER(TRIM(?)) " +
            "  AND UPPER(TRIM(color))   = UPPER(TRIM(?)) " +
            "ORDER BY fecha_registro ASC, codigo_articulo ASC " +
            "LIMIT 1";


        String codigoInventario = null;
        try (PreparedStatement ps = cn.prepareStatement(sqlFindInv)) {
            ps.setString(1, articulo);
            ps.setString(2, modelo);
            ps.setString(3, talla);
            ps.setString(4, color);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    codigoInventario = rs.getString("codigo_articulo");
                }
            }
        }

        if (codigoInventario == null || codigoInventario.isBlank()) {
            throw new SQLException("No se encontró en Inventarios un artículo disponible que coincida con el pedido.");
        }

        // 3) Descontar existencia de ese inventario
        inventarioDAO.descontarExistencia(cn, codigoInventario, 1);

        // 4) Insertar renglón en Nota_Detalle (como INVENTARIO)
        double subtotal = precio - (precio * (descuento / 100.0));
        if (Math.abs(subtotal) < 0.005) subtotal = 0.0;

        String sqlInsDet =
            "INSERT INTO Nota_Detalle (" +
            "  numero_nota, fecha_registro, articulo, marca, modelo, talla, color, " +
            "  precio, descuento, subtotal, fecha_evento, telefono, codigo_articulo, status" +
            ") VALUES ( ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'A')";

        try (PreparedStatement ps = cn.prepareStatement(sqlInsDet)) {
            int k = 1;
            ps.setInt(k++, numeroNota);
            ps.setString(k++, articulo);
            ps.setString(k++, marca);
            ps.setString(k++, modelo);
            ps.setString(k++, talla);
            ps.setString(k++, color);
            ps.setBigDecimal(k++, new java.math.BigDecimal(precio));
            ps.setBigDecimal(k++, new java.math.BigDecimal(descuento));
            ps.setBigDecimal(k++, new java.math.BigDecimal(subtotal));
            if (fechaEvento == null) ps.setNull(k++, java.sql.Types.DATE);
            else ps.setDate(k++, fechaEvento);
            ps.setString(k++, telefono);
            ps.setString(k++, codigoInventario);
            ps.executeUpdate();
        }

        // 5) Marcar el pedido como cancelado
        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE Pedidos SET status='C' WHERE id=?")) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }
    }

    // ========================= HELPERS =========================

    private Double getDoubleOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}

