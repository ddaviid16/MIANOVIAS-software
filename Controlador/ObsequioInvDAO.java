package Controlador;

import Conexion.Conecta;
import Modelo.ObsequioInv;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ObsequioInvDAO {

    private static final String SEL_ALL =
        "SELECT codigo_articulo, articulo, marca, modelo, talla, color, " +
        "precio, descuento, existencia, status, fecha_registro " +
        "FROM InventarioObsequios " +
        "ORDER BY fecha_registro DESC, codigo_articulo DESC";

    private static final String SEL_ONE =
        "SELECT codigo_articulo, articulo, marca, modelo, talla, color, " +
        "precio, descuento, existencia, status, fecha_registro " +
        "FROM InventarioObsequios WHERE codigo_articulo=?";

    private static final String SEL_SEARCH =
        "SELECT codigo_articulo, articulo, marca, modelo, talla, color, " +
        "       precio, descuento, existencia, status, fecha_registro " +
        "FROM InventarioObsequios " +
        "WHERE CAST(codigo_articulo AS CHAR) LIKE ? " +
        "   OR articulo LIKE ? " +
        "   OR marca LIKE ? " +
        "   OR modelo LIKE ? " +
        "   OR talla LIKE ? " +
        "   OR color LIKE ? " +
        "ORDER BY fecha_registro DESC, codigo_articulo DESC";

    // Ahora solo insertamos: código, artículo, status, fecha_registro
    private static final String INS =
        "INSERT INTO InventarioObsequios " +
        "(codigo_articulo, articulo, status, fecha_registro) " +
        "VALUES (?,?,?,CURDATE())";

    // Y solo actualizamos el nombre + status (que siempre será 'A')
    private static final String UPD =
        "UPDATE InventarioObsequios SET articulo=?, status=? WHERE codigo_articulo=?";

    // ================= LISTAR =================

    public List<ObsequioInv> listar() throws SQLException {
        return listar(null);
    }

    public List<ObsequioInv> listar(String filtro) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            PreparedStatement ps;

            if (filtro == null || filtro.isBlank()) {
                ps = cn.prepareStatement(SEL_ALL);
            } else {
                ps = cn.prepareStatement(SEL_SEARCH);
                String like = "%" + filtro.trim() + "%";
                for (int i = 1; i <= 6; i++) {
                    ps.setString(i, like);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<ObsequioInv> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        }
    }

    public List<ObsequioInv> listarActivosFiltrado(String q) throws SQLException {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        String sql =
            "SELECT codigo_articulo, articulo, marca, modelo, talla, color, " +
            "       precio, descuento, existencia, status, fecha_registro " +
            "FROM InventarioObsequios " +
            "WHERE status='A' AND (" +
            "  codigo_articulo LIKE ? OR articulo LIKE ? OR talla LIKE ? OR color LIKE ? OR marca LIKE ? OR modelo LIKE ? " +
            "  OR CAST(codigo_articulo AS CHAR) LIKE ?" +
            ") " +
            "ORDER BY articulo ASC, codigo_articulo DESC " +
            "LIMIT 200";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 1; i <= 7; i++) ps.setString(i, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<ObsequioInv> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        }
    }

    public ObsequioInv obtener(String codigo) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(SEL_ONE)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    // ================= INSERT / UPDATE =================

    public boolean insertar(ObsequioInv o) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(INS)) {
            int k = 1;
            ps.setString(k++, o.getCodigoArticulo());
            ps.setString(k++, o.getArticulo());
            ps.setString(k++, "A"); // siempre ACTIVO
            return ps.executeUpdate() == 1;
        }
    }

    public boolean actualizar(ObsequioInv o) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(UPD)) {
            int k = 1;
            ps.setString(k++, o.getArticulo());
            ps.setString(k++, "A"); // forzamos status A
            ps.setString(k++, o.getCodigoArticulo());
            return ps.executeUpdate() == 1;
        }
    }

    // ================= EXISTENCIA: YA NO SE TOCA =================

    /** Antes descontaba existencia; ahora no hace nada. */
    public void descontarExistencia(Connection cn, int codigo, int cantidad) throws SQLException {
        // Inventario de obsequios ya no maneja existencia.
        // Método vacío para no romper llamadas antiguas.
    }

    /** Antes descontaba 1 por código; ahora no hace nada. */
    public void descontarExistenciaBatch(Connection cn, List<Integer> codigos) throws SQLException {
        // Sin efecto.
    }

    /** Obtiene el nombre visible del obsequio (para imprimir en la nota). */
    public String obtenerNombrePorCodigo(Connection cn, int codigo) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT articulo FROM InventarioObsequios WHERE codigo_articulo=?")) {
            ps.setInt(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // --- helpers ---
    private static String empty(String s){ return (s==null||s.isBlank())?null:s.trim(); }

    private static ObsequioInv map(ResultSet rs) throws SQLException {
        ObsequioInv o = new ObsequioInv();
        o.setCodigoArticulo(rs.getString("codigo_articulo"));
        o.setArticulo(rs.getString("articulo"));
        o.setMarca(rs.getString("marca"));
        o.setModelo(rs.getString("modelo"));
        o.setTalla(rs.getString("talla"));
        o.setColor(rs.getString("color"));
        o.setPrecio(rs.getBigDecimal("precio")==null?null:rs.getBigDecimal("precio").doubleValue());
        o.setDescuento(rs.getBigDecimal("descuento")==null?null:rs.getBigDecimal("descuento").doubleValue());
        o.setExistencia((Integer) rs.getObject("existencia"));
        o.setStatus(rs.getString("status"));
        Date fr = rs.getDate("fecha_registro");
        o.setFechaRegistro(fr==null?null:fr.toLocalDate());
        return o;
    }
}
