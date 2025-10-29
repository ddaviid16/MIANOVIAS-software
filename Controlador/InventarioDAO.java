package Controlador;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import Conexion.Conecta;
import Modelo.Inventario;

public class InventarioDAO {

    private static final String SELECT_ALL =
        "SELECT codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, " +
        "existencia, fecha_registro, status " +
        "FROM Inventarios " +
        "ORDER BY fecha_registro DESC, codigo_articulo DESC";

    private static final String SELECT_SEARCH =
        "SELECT codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, " +
        "existencia, fecha_registro, status " +
        "FROM Inventarios " +
        "WHERE articulo LIKE ? OR talla LIKE ? OR color LIKE ? " +
        "ORDER BY fecha_registro DESC, codigo_articulo DESC";

    private static final String SELECT_BY_ID =
        "SELECT codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, " +
        "existencia, fecha_registro, status " +
        "FROM Inventarios WHERE codigo_articulo = ?";

    private static final String INSERT_SQL =
        "INSERT INTO Inventarios " +
        "(codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, existencia, fecha_registro, status) " +
        "VALUES (?,?,?,?,?,?,?,?,?,CURDATE(),?)";

    private static final String UPDATE_SQL =
        "UPDATE Inventarios SET articulo=?, marca=?, modelo=?, talla=?, color=?, precio=?, descuento=?, " +
        "existencia=?, status=? WHERE codigo_articulo=?";

    // ======================= LISTADOS / BÚSQUEDA =======================

    public List<Inventario> listar(String filtro) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            PreparedStatement ps;
            if (filtro == null || filtro.isBlank()) {
                ps = cn.prepareStatement(SELECT_ALL);
            } else {
                ps = cn.prepareStatement(SELECT_SEARCH);
                String like = "%" + filtro.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setString(3, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Inventario> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        }
    }

    // EDITAR: trae el artículo exista o no, sin importar status
public Inventario buscarPorCodigo(int codigo) throws SQLException {
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(
             "SELECT codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, " +
             "       existencia, fecha_registro, status " +
             "FROM Inventarios WHERE codigo_articulo = ?")) {
        ps.setInt(1, codigo);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? mapRow(rs) : null;
        }
    }
}

// VENDER: sólo activos y con stock
public Inventario buscarParaVenta(int codigo) throws SQLException {
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(
             "SELECT codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, " +
             "       existencia, fecha_registro, status " +
             "FROM Inventarios " +
             "WHERE codigo_articulo=? AND status='A' AND COALESCE(existencia,0) > 0")) {
        ps.setInt(1, codigo);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? mapRow(rs) : null;
        }
    }
}

    /** Para el diálogo de selección: solo artículos disponibles (A y existencia > 0). */
    public java.util.List<Modelo.Inventario> listarActivosFiltrado(String q) throws java.sql.SQLException {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        String sql =
    "SELECT codigo_articulo, articulo, marca, modelo, talla, color, " +
    "       precio, descuento, existencia, status, fecha_registro " +
    "FROM Inventarios " +
    "WHERE status='A' AND COALESCE(existencia,0) > 0 AND (" +     // <---
    "      articulo LIKE ? OR marca LIKE ? OR modelo LIKE ? OR " +
    "      talla LIKE ? OR color LIKE ? OR CAST(codigo_articulo AS CHAR) LIKE ?" +
    ") ORDER BY articulo ASC, codigo_articulo DESC LIMIT 200";

        try (java.sql.Connection cn = Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

            for (int i = 1; i <= 6; i++) ps.setString(i, like);

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                java.util.List<Modelo.Inventario> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    Modelo.Inventario i = new Modelo.Inventario();
                    i.setCodigoArticulo(rs.getInt("codigo_articulo"));
                    i.setArticulo(rs.getString("articulo"));
                    i.setMarca(rs.getString("marca"));
                    i.setModelo(rs.getString("modelo"));
                    i.setTalla(rs.getString("talla"));
                    i.setColor(rs.getString("color"));
                    i.setPrecio(rs.getBigDecimal("precio")==null? null : rs.getBigDecimal("precio").doubleValue());
                    i.setDescuento(rs.getBigDecimal("descuento")==null? null : rs.getBigDecimal("descuento").doubleValue());
                    i.setExistencia(rs.getObject("existencia")==null? null : rs.getInt("existencia"));
                    i.setStatus(rs.getString("status"));
                    try {
                        java.sql.Date fr = rs.getDate("fecha_registro");
                        if (fr != null) i.setFechaRegistro(fr.toLocalDate());
                    } catch (Exception ignore) {}
                    out.add(i);
                }
                return out;
            }
        }
    }

    // ======================= ALTAS / CAMBIOS =======================

    public boolean insertar(Inventario i) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(INSERT_SQL)) {
            int k = 1;
            ps.setInt(k++,    i.getCodigoArticulo());
            ps.setString(k++, i.getArticulo());
            ps.setString(k++, emptyToNull(i.getMarca()));
            ps.setString(k++, emptyToNull(i.getModelo()));
            ps.setString(k++, emptyToNull(i.getTalla()));
            ps.setString(k++, emptyToNull(i.getColor()));
            ps.setDouble(k++, i.getPrecio());
            if (i.getDescuento() == null) ps.setNull(k++, Types.DECIMAL); else ps.setDouble(k++, i.getDescuento());
            if (i.getExistencia() == null) ps.setNull(k++, Types.INTEGER); else ps.setInt(k++, i.getExistencia());
            ps.setString(k++, (i.getStatus() == null || i.getStatus().isBlank()) ? "A" : i.getStatus());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean actualizar(Inventario i) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(UPDATE_SQL)) {
            int k = 1;
            ps.setString(k++, i.getArticulo());
            ps.setString(k++, emptyToNull(i.getMarca()));
            ps.setString(k++, emptyToNull(i.getModelo()));
            ps.setString(k++, emptyToNull(i.getTalla()));
            ps.setString(k++, emptyToNull(i.getColor()));
            ps.setDouble(k++, i.getPrecio());
            if (i.getDescuento() == null) ps.setNull(k++, Types.DECIMAL); else ps.setDouble(k++, i.getDescuento());
            if (i.getExistencia() == null) ps.setNull(k++, Types.INTEGER); else ps.setInt(k++, i.getExistencia());
            ps.setString(k++, (i.getStatus() == null || i.getStatus().isBlank()) ? "A" : i.getStatus());
            ps.setInt(k++, i.getCodigoArticulo());
            return ps.executeUpdate() == 1;
        }
    }

    // ======================= STOCK / VENTAS =======================

    /** Descuenta existencia. Lanza SQLException si no hay stock suficiente. */
    /** Descuenta existencia. NO cambia el status. */
public void descontarExistencia(Connection cn, int codigo, int cantidad) throws SQLException {
    if (cantidad <= 0) return;

    // 1) Bloquea fila y verifica stock
    int existenciaActual;
    try (PreparedStatement ps = cn.prepareStatement(
            "SELECT COALESCE(existencia,0) FROM Inventarios WHERE codigo_articulo=? FOR UPDATE")) {
        ps.setInt(1, codigo);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("Artículo no encontrado: " + codigo);
            existenciaActual = rs.getInt(1);
        }
    }
    if (existenciaActual < cantidad) {
        throw new SQLException("Sin existencia suficiente para el artículo " + codigo);
    }

    // 2) Descontar (sin tocar status)
    try (PreparedStatement ps = cn.prepareStatement(
            "UPDATE Inventarios SET existencia = COALESCE(existencia,0) - ? WHERE codigo_articulo=?")) {
        ps.setInt(1, cantidad);
        ps.setInt(2, codigo);
        ps.executeUpdate();
    }
}
// Reingresar existencia (devolución)
public void incrementarExistencia(java.sql.Connection cn, int codigo, int cantidad) throws java.sql.SQLException {
    if (cantidad <= 0) return;
    try (java.sql.PreparedStatement ps = cn.prepareStatement(
            "UPDATE Inventarios SET existencia = COALESCE(existencia,0) + ? WHERE codigo_articulo=?")) {
        ps.setInt(1, cantidad);
        ps.setInt(2, codigo);
        ps.executeUpdate();
    }
    // No tocar status automáticamente (queda como esté).
}


    // ======================= HELPERS =======================

    private String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private Inventario mapRow(ResultSet rs) throws SQLException {
    Inventario i = new Inventario();
    i.setCodigoArticulo(rs.getInt("codigo_articulo"));
    i.setArticulo(rs.getString("articulo"));
    i.setMarca(rs.getString("marca"));
    i.setModelo(rs.getString("modelo"));
    i.setTalla(rs.getString("talla"));
    i.setColor(rs.getString("color"));
    double precio = rs.getDouble("precio");
    i.setPrecio(rs.wasNull() ? null : precio);
    double desc = rs.getDouble("descuento");
    i.setDescuento(rs.wasNull() ? null : desc);
    int ex = rs.getInt("existencia");
    i.setExistencia(rs.wasNull() ? null : ex);
    Date fr = rs.getDate("fecha_registro");
    i.setFechaRegistro(fr == null ? null : fr.toLocalDate());

    String st = rs.getString("status");
    i.setStatus(st == null ? null : st.trim().toUpperCase()); // <<< clave

    return i;
}
}
