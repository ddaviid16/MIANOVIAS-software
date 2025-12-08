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

    // ======================= SELECTS BASE =======================
private static final String SELECT_ALL =
    "SELECT codigo_articulo, articulo, " +
    "       descripcion1, descripcion2, " +
    "       marca, modelo, talla, color, " +
    "       precio, descuento, costo_iva, " +
    "       existencia, nombre_novia, inventario_conteo, " +
    "       fecha_registro, fecha_pago, " +
    "       remision, factura, " +
    "       status " +
    "FROM Inventarios " +
    "ORDER BY fecha_registro DESC, codigo_articulo DESC";

private static final String SELECT_SEARCH =
    "SELECT codigo_articulo, articulo, " +
    "       descripcion1, descripcion2, " +
    "       marca, modelo, talla, color, " +
    "       precio, descuento, costo_iva, " +
    "       existencia, nombre_novia, inventario_conteo, " +
    "       fecha_registro, fecha_pago, " +
    "       remision, factura, " +
    "       status " +
    "FROM Inventarios " +
    "WHERE codigo_articulo LIKE ? OR articulo LIKE ? OR talla LIKE ? OR color LIKE ? " +
    "ORDER BY fecha_registro DESC, codigo_articulo DESC";

private static final String SELECT_BY_ID =
    "SELECT codigo_articulo, articulo, " +
    "       descripcion1, descripcion2, " +
    "       marca, modelo, talla, color, " +
    "       precio, descuento, costo_iva, " +
    "       existencia, nombre_novia, inventario_conteo, " +
    "       fecha_registro, fecha_pago, " +
    "       remision, factura, " +
    "       status " +
    "FROM Inventarios WHERE codigo_articulo = ?";

    // ======================= INSERT / UPDATE =======================

    private static final String INSERT_SQL =
        "INSERT INTO Inventarios (" +
        "  codigo_articulo, articulo, descripcion1, descripcion2, " +
        "  marca, modelo, talla, color, " +
        "  precio, descuento, costo_iva, " +
        "  existencia, nombre_novia, inventario_conteo, " +
        "  fecha_registro, fecha_pago, " +
        "  remision, factura, status" +
        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, ?,CURDATE(),?,?,?,?)";
    //            1 2 3 4 5 6 7 8 9 10 11 12 13 14      15 16 17 18


    private static final String UPDATE_SQL =
        "UPDATE Inventarios SET " +
        "  articulo=?, descripcion1=?, descripcion2=?, " +
        "  marca=?, modelo=?, talla=?, color=?, " +
        "  precio=?, descuento=?, costo_iva=?, " +
        "  existencia=?, inventario_conteo=?, " +
        "  fecha_pago=?, remision=?, factura=?, " +
        "  status=? " +
        "WHERE codigo_articulo=?";

    // ======================= LISTADOS / BÚSQUEDA =======================

    public List<Inventario> listar(String filtro) throws SQLException {
    try (Connection cn = Conecta.getConnection()) {
        PreparedStatement ps;
        if (filtro == null || filtro.isBlank()) {
            ps = cn.prepareStatement(SELECT_ALL);
        } else {
            ps = cn.prepareStatement(SELECT_SEARCH);
            String like = "%" + filtro.trim() + "%";
            ps.setString(1, like); // codigo_articulo
            ps.setString(2, like); // articulo
            ps.setString(3, like); // talla
            ps.setString(4, like); // color
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
    public Inventario buscarPorCodigo(String codigoNuevo) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, codigoNuevo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // VENDER: sólo activos y con stock
    public Inventario buscarParaVenta(String cod) throws SQLException {
        String sql =
            "SELECT codigo_articulo, articulo, " +
            "       descripcion1, descripcion2, " +
            "       marca, modelo, talla, color, " +
            "       precio, descuento, costo_iva, " +
            "       existencia, nombre_novia, inventario_conteo, " +
            "       fecha_registro, fecha_pago, " +
            "       remision, factura, " +
            "       status " +
            "FROM Inventarios " +
            "WHERE codigo_articulo=? AND status='A' AND COALESCE(existencia,0) > 0";


        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, cod);
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
            "WHERE status='A' AND COALESCE(existencia,0) > 0 AND (" +
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
                    i.setCodigoArticulo(rs.getString("codigo_articulo"));
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
            ps.setString(k++, i.getCodigoArticulo());
            ps.setString(k++, i.getArticulo());
            ps.setString(k++, emptyToNull(i.getDescripcion1()));
            ps.setString(k++, emptyToNull(i.getDescripcion2()));
            ps.setString(k++, emptyToNull(i.getMarca()));
            ps.setString(k++, emptyToNull(i.getModelo()));
            ps.setString(k++, emptyToNull(i.getTalla()));
            ps.setString(k++, emptyToNull(i.getColor()));

            ps.setDouble(k++, i.getPrecio());

            if (i.getDescuento() == null) ps.setNull(k++, Types.DECIMAL);
            else ps.setDouble(k++, i.getDescuento());

            if (i.getCostoIva() == null) ps.setNull(k++, Types.DECIMAL);
            else ps.setDouble(k++, i.getCostoIva());

            // DESPUÉS: default existencia = 1 si viene null
            Integer existencia = i.getExistencia();
            if (existencia == null) {
                existencia = 1;  // valor por defecto
            }
            ps.setInt(k++, existencia);

            
            // NUEVO: nombre_novia
            ps.setString(k++, emptyToNull(i.getNombreNovia()));

            if (i.getInventarioConteo() == null) ps.setNull(k++, Types.INTEGER);
            else ps.setInt(k++, i.getInventarioConteo());

            // fecha_registro = CURDATE() en el SQL

            if (i.getFechaPago() == null) {
                ps.setNull(k++, Types.DATE);
            } else {
                ps.setDate(k++, Date.valueOf(i.getFechaPago()));
            }

            ps.setString(k++, emptyToNull(i.getRemision()));
            ps.setString(k++, emptyToNull(i.getFactura()));
            ps.setString(k++, (i.getStatus() == null || i.getStatus().isBlank()) ? "A" : i.getStatus());

            return ps.executeUpdate() == 1;
        }
    }

    public boolean actualizar(Inventario i) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(UPDATE_SQL)) {
            int k = 1;
            ps.setString(k++, i.getArticulo());
            ps.setString(k++, emptyToNull(i.getDescripcion1()));
            ps.setString(k++, emptyToNull(i.getDescripcion2()));
            ps.setString(k++, emptyToNull(i.getMarca()));
            ps.setString(k++, emptyToNull(i.getModelo()));
            ps.setString(k++, emptyToNull(i.getTalla()));
            ps.setString(k++, emptyToNull(i.getColor()));

            ps.setDouble(k++, i.getPrecio());

            if (i.getDescuento() == null) ps.setNull(k++, Types.DECIMAL);
            else ps.setDouble(k++, i.getDescuento());

            if (i.getCostoIva() == null) ps.setNull(k++, Types.DECIMAL);
            else ps.setDouble(k++, i.getCostoIva());

            if (i.getExistencia() == null) ps.setNull(k++, Types.INTEGER);
            else ps.setInt(k++, i.getExistencia());

                        
            // NUEVO: nombre_novia
            ps.setString(k++, emptyToNull(i.getNombreNovia()));


            if (i.getInventarioConteo() == null) ps.setNull(k++, Types.INTEGER);
            else ps.setInt(k++, i.getInventarioConteo());

            if (i.getFechaPago() == null) {
                ps.setNull(k++, Types.DATE);
            } else {
                ps.setDate(k++, Date.valueOf(i.getFechaPago()));
            }

            ps.setString(k++, emptyToNull(i.getRemision()));
            ps.setString(k++, emptyToNull(i.getFactura()));
            ps.setString(k++, (i.getStatus() == null || i.getStatus().isBlank()) ? "A" : i.getStatus());

            ps.setString(k++, i.getCodigoArticulo());
            return ps.executeUpdate() == 1;
        }
    }

    // ======================= STOCK / VENTAS =======================

    /** Descuenta existencia. NO cambia el status. */
    public void descontarExistencia(Connection cn, String codigo, int cantidad) throws SQLException {
        if (cantidad <= 0) return;

        // 1) Bloquea fila y verifica stock
        int existenciaActual;
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT COALESCE(existencia,0) FROM Inventarios WHERE codigo_articulo=? FOR UPDATE")) {
            ps.setString(1, codigo);
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
            ps.setString(2, codigo);
            ps.executeUpdate();
        }
    }

    // Reingresar existencia (devolución)
    public void incrementarExistencia(java.sql.Connection cn, String codigo, int cantidad) throws java.sql.SQLException {
        if (cantidad <= 0) return;
        try (java.sql.PreparedStatement ps = cn.prepareStatement(
                "UPDATE Inventarios SET existencia = COALESCE(existencia,0) + ? WHERE codigo_articulo=?")) {
            ps.setInt(1, cantidad);
            ps.setString(2, codigo);
            ps.executeUpdate();
        }
        // No tocar status automáticamente (queda como esté).
    }

    // ======================= HELPERS =======================

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Inventario mapRow(ResultSet rs) throws SQLException {
        Inventario i = new Inventario();
        i.setCodigoArticulo(rs.getString("codigo_articulo"));
        i.setArticulo(rs.getString("articulo"));

        i.setDescripcion1(rs.getString("descripcion1"));
        i.setDescripcion2(rs.getString("descripcion2"));

        i.setMarca(rs.getString("marca"));
        i.setModelo(rs.getString("modelo"));
        i.setTalla(rs.getString("talla"));
        i.setColor(rs.getString("color"));

        double precio = rs.getDouble("precio");
        i.setPrecio(rs.wasNull() ? null : precio);

        double desc = rs.getDouble("descuento");
        i.setDescuento(rs.wasNull() ? null : desc);

        double costo = rs.getDouble("costo_iva");
        i.setCostoIva(rs.wasNull() ? null : costo);

        int ex = rs.getInt("existencia");
        i.setExistencia(rs.wasNull() ? null : ex);

                
        // NUEVO
        i.setNombreNovia(rs.getString("nombre_novia"));

        int conteo = rs.getInt("inventario_conteo");
        i.setInventarioConteo(rs.wasNull() ? null : conteo);

        Date fr = rs.getDate("fecha_registro");
        i.setFechaRegistro(fr == null ? null : fr.toLocalDate());

        Date fp = rs.getDate("fecha_pago");
        i.setFechaPago(fp == null ? null : fp.toLocalDate());

        i.setRemision(rs.getString("remision"));
        i.setFactura(rs.getString("factura"));

        String st = rs.getString("status");
        i.setStatus(st == null ? null : st.trim().toUpperCase());

        return i;
    }
}
