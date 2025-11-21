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

    private static final String INS =
        "INSERT INTO InventarioObsequios " +
        "(codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, existencia, status, fecha_registro) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,CURDATE())";

    private static final String UPD =
        "UPDATE InventarioObsequios SET articulo=?, marca=?, modelo=?, talla=?, color=?, " +
        "precio=?, descuento=?, existencia=?, status=? WHERE codigo_articulo=?";

    public List<ObsequioInv> listar() throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(SEL_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<ObsequioInv> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        }
    }

    public List<ObsequioInv> listarActivosFiltrado(String q) throws SQLException {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        String sql =
            "SELECT codigo_articulo, articulo, marca, modelo, talla, color, " +
            "       precio, descuento, existencia, status, fecha_registro " +
            "FROM InventarioObsequios " +
            "WHERE status='A' AND (" +
            "  articulo LIKE ? OR talla LIKE ? OR color LIKE ? OR marca LIKE ? OR modelo LIKE ? " +
            "  OR CAST(codigo_articulo AS CHAR) LIKE ?" +
            ") " +
            "ORDER BY articulo ASC, codigo_articulo DESC " +
            "LIMIT 200";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 1; i <= 6; i++) ps.setString(i, like);
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

    public boolean insertar(ObsequioInv o) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(INS)) {
            int k=1;
            ps.setString(k++, o.getCodigoArticulo());
            ps.setString(k++, o.getArticulo());
            ps.setString(k++, empty(o.getMarca()));
            ps.setString(k++, empty(o.getModelo()));
            ps.setString(k++, empty(o.getTalla()));
            ps.setString(k++, empty(o.getColor()));
            ps.setObject(k++, o.getPrecio(), Types.DECIMAL);
            ps.setObject(k++, o.getDescuento(), Types.DECIMAL);
            ps.setObject(k++, o.getExistencia(), Types.INTEGER);
            ps.setString(k++, (o.getStatus()==null||o.getStatus().isBlank())?"A":o.getStatus());
            return ps.executeUpdate()==1;
        }
    }

    public boolean actualizar(ObsequioInv o) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(UPD)) {
            int k=1;
            ps.setString(k++, o.getArticulo());
            ps.setString(k++, empty(o.getMarca()));
            ps.setString(k++, empty(o.getModelo()));
            ps.setString(k++, empty(o.getTalla()));
            ps.setString(k++, empty(o.getColor()));
            ps.setObject(k++, o.getPrecio(), Types.DECIMAL);
            ps.setObject(k++, o.getDescuento(), Types.DECIMAL);
            ps.setObject(k++, o.getExistencia(), Types.INTEGER);
            ps.setString(k++, (o.getStatus()==null||o.getStatus().isBlank())?"A":o.getStatus());
            ps.setString(k++, o.getCodigoArticulo());
            return ps.executeUpdate()==1;
        }
    }

    /** Descuenta existencia de un obsequio (no cambia status). */
    public void descontarExistencia(Connection cn, int codigo, int cantidad) throws SQLException {
        if (cantidad <= 0) return;

        int existenciaActual;
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT COALESCE(existencia,0) FROM InventarioObsequios WHERE codigo_articulo=? FOR UPDATE")) {
            ps.setInt(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Obsequio no encontrado: " + codigo);
                existenciaActual = rs.getInt(1);
            }
        }
        if (existenciaActual < cantidad)
            throw new SQLException("Sin existencia suficiente para obsequio " + codigo);

        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE InventarioObsequios SET existencia = COALESCE(existencia,0) - ? WHERE codigo_articulo=?")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, codigo);
            ps.executeUpdate();
        }
        // **NO** se cambia status automáticamente.
    }

    /** Descuenta 1 unidad por cada código (usa la misma transacción que recibe). */
    public void descontarExistenciaBatch(Connection cn, List<Integer> codigos) throws SQLException {
        for (Integer cod : codigos) {
            if (cod != null) descontarExistencia(cn, cod, 1);
        }
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
