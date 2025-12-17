package Controlador;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import Conexion.Conecta;
import Modelo.Inventario;

/** Persistencia de artículos PEDIDOS asociados a una nota. */
public class PedidosDAO {

    // --- Row para el reporte de ENTREGAS ---
    public static class EntregaVestidoRow {
        public String folio;              // <-- NUEVO (visible en UI)
        public Integer numeroNota;        // clave técnica oculta
        public String telefono;
        public String articulo;
        public String marca;
        public String modelo;
        public String talla;
        public String color;
        public BigDecimal precio;
        public BigDecimal descuento; // %
        public java.util.Date fechaEntrega;   // FECHA QUE SE MUESTRA
        public java.util.Date fechaEvento;    // referencia
        public java.util.Date fechaRegistro;  // referencia
        public Boolean enTienda;
        public Integer detId;                 // id del renglón en Nota_Detalle (nullable si viene de Pedidos)
        public boolean fromPedidos;           // true si viene de tabla Pedidos
    }

    // --- listar entregas por rango usando COALESCE(fecha_entrega, fecha_evento, fecha_registro)
    //     e INCLUYENDO los vestidos que están “por pedirse” (tabla Pedidos)
    public List<EntregaVestidoRow> listarEntregasVestidos(LocalDate ini, LocalDate fin) throws SQLException {
        List<EntregaVestidoRow> out = new ArrayList<>();
        try (Connection cn = Conecta.getConnection()) {
            boolean tieneEnTienda = hasColumn(cn, "Pedidos", "en_tienda");
            String fc = folioCol(cn);
            String folioSelND = (fc == null) ? "CONCAT(d.numero_nota,'') AS folio" : ("n."+fc+" AS folio");
            String folioSelP  = (fc == null) ? "CONCAT(p.numero_nota,'') AS folio" : ("n."+fc+" AS folio");

            // 1) Vestidos capturados en Nota_Detalle (ventas normales)
            String sqlND =
                "SELECT d.id AS det_id, d.numero_nota, " + folioSelND + ", n.telefono, d.articulo, d.marca, d.modelo, d.talla, d.color," +
                "       d.precio, d.descuento," +
                "       COALESCE(d.fecha_entrega, d.fecha_evento, d.fecha_registro) AS fecha_entrega, " +
                "       d.fecha_evento, d.fecha_registro" +
                (tieneEnTienda ? ", COALESCE(p.en_tienda,'N') AS en_tienda " : ", d.status AS det_status ") +
                "FROM Nota_Detalle d " +
                "JOIN Notas n ON n.numero_nota = d.numero_nota " +
                (tieneEnTienda ? "LEFT JOIN Pedidos p ON p.numero_nota = d.numero_nota " : "") +
                "WHERE (UPPER(d.articulo) = 'VESTIDO' OR UPPER(d.articulo) LIKE 'VESTIDO%') " +
                "  AND d.status = 'A' " +
                "  AND COALESCE(d.fecha_entrega, d.fecha_evento, d.fecha_registro) >= ? " +
                "  AND COALESCE(d.fecha_entrega, d.fecha_evento, d.fecha_registro) <  ? " +
                "ORDER BY COALESCE(d.fecha_entrega, d.fecha_evento, d.fecha_registro), d.numero_nota, d.id";

            try (PreparedStatement ps = cn.prepareStatement(sqlND)) {
                ps.setDate(1, Date.valueOf(ini));
                ps.setDate(2, Date.valueOf(fin));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        EntregaVestidoRow r = new EntregaVestidoRow();
                        r.detId         = rs.getInt("det_id");
                        r.numeroNota    = rs.getInt("numero_nota");
                        r.folio         = rs.getString("folio");
                        r.telefono      = rs.getString("telefono");
                        r.articulo      = rs.getString("articulo");
                        r.marca         = rs.getString("marca");
                        r.modelo        = rs.getString("modelo");
                        r.talla         = rs.getString("talla");
                        r.color         = rs.getString("color");
                        r.precio        = rs.getBigDecimal("precio");
                        r.descuento     = rs.getBigDecimal("descuento");
                        r.fechaEntrega  = rs.getDate("fecha_entrega");
                        r.fechaEvento   = rs.getDate("fecha_evento");
                        r.fechaRegistro = rs.getDate("fecha_registro");
                        if (tieneEnTienda) {
                            String en = rs.getString("en_tienda");
                            r.enTienda = "S".equalsIgnoreCase(en) || "1".equals(en) || "true".equalsIgnoreCase(en);
                        } else {
                            String st = rs.getString("det_status"); // compatibilidad
                            r.enTienda = "C".equalsIgnoreCase(st);  // 'C' = recibido/en tienda
                        }
                        r.fromPedidos = false;
                        out.add(r);
                    }
                }
            }

            // 2) Vestidos “por pedirse” (tabla Pedidos)
            String sqlP =
                "SELECT NULL AS det_id, p.numero_nota, " + folioSelP + ", p.telefono, p.articulo, p.marca, p.modelo, p.talla, p.color," +
                "       p.precio, p.descuento, " +
                "       COALESCE(p.fecha_evento, p.fecha_registro, CURRENT_DATE) AS fecha_entrega, " +
                "       p.fecha_evento, p.fecha_registro, " +
                (tieneEnTienda ? "COALESCE(p.en_tienda,'N') AS en_tienda " : "COALESCE(p.status,'A') AS det_status ") +
                "FROM Pedidos p " +
                "LEFT JOIN Notas n ON n.numero_nota = p.numero_nota " +
                "WHERE (UPPER(p.articulo) = 'VESTIDO' OR UPPER(p.articulo) LIKE 'VESTIDO%') " +
                "  AND COALESCE(p.status,'A') = 'A' " +   // <- SOLO activos
                "  AND COALESCE(p.fecha_evento, p.fecha_registro, CURRENT_DATE) >= ? " +
                "  AND COALESCE(p.fecha_evento, p.fecha_registro, CURRENT_DATE) <  ? ";


            try (PreparedStatement ps = cn.prepareStatement(sqlP)) {
                ps.setDate(1, Date.valueOf(ini));
                ps.setDate(2, Date.valueOf(fin));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        EntregaVestidoRow r = new EntregaVestidoRow();
                        r.detId         = null;
                        r.numeroNota    = rs.getInt("numero_nota");
                        r.folio         = rs.getString("folio");
                        r.telefono      = rs.getString("telefono");
                        r.articulo      = rs.getString("articulo");
                        r.marca         = rs.getString("marca");
                        r.modelo        = rs.getString("modelo");
                        r.talla         = rs.getString("talla");
                        r.color         = rs.getString("color");
                        r.precio        = rs.getBigDecimal("precio");
                        r.descuento     = rs.getBigDecimal("descuento");
                        r.fechaEntrega  = rs.getDate("fecha_entrega");
                        r.fechaEvento   = rs.getDate("fecha_evento");
                        r.fechaRegistro = rs.getDate("fecha_registro");
                        if (tieneEnTienda) {
                            String en = rs.getString("en_tienda");
                            r.enTienda = "S".equalsIgnoreCase(en) || "1".equals(en) || "true".equalsIgnoreCase(en);
                        } else {
                            String st = rs.getString("det_status");
                            r.enTienda = "C".equalsIgnoreCase(st);
                        }
                        r.fromPedidos = true;
                        out.add(r);
                    }
                }
            }
        }
        return out;
    }

    /** Borrador que se captura en el diálogo antes de guardar la venta. */
    public static class PedidoDraft {
        public String articulo;
        public String marca;
        public String modelo;
        public String talla;
        public String color;
        public BigDecimal precio;      // total del artículo
        public BigDecimal descuento;   // %
    }

    /** Estructura para mostrar pedidos en el panel de "Artículos a pedir". */
    public static class PedidoRow {
        public String folio;
        public int numeroNota;
        public Date fechaRegistro;
        public String articulo, marca, modelo, talla, color;
        public BigDecimal precio, descuento;
        public Date fechaEvento;
        public Date fechaEntrega;      // <-- se usa en la UI
        public String telefono;
        public String codigoArticulo; // puede ser null
        public String status;          // 'A' o 'C'
        public boolean enTienda;       // checkbox de la UI
    }

    /** Inserta todos los pedidos de una nota recién creada. codigo_articulo queda NULL por ahora. */
    public void insertarPedidos(int numeroNota, String telefono, LocalDate fechaEvento,
                                List<PedidoDraft> items) throws SQLException {
        if (items == null || items.isEmpty()) return;

        String sql = "INSERT INTO Pedidos " +
                "(numero_nota, fecha_registro, articulo, marca, modelo, talla, color, precio, descuento, " +
                " fecha_evento, telefono, codigo_articulo, status) " +
                "VALUES ( ?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'A')";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            for (PedidoDraft p : items) {
                ps.setInt(1, numeroNota);
                ps.setString(2, nz(p.articulo));
                ps.setString(3, nz(p.marca));
                ps.setString(4, nz(p.modelo));
                ps.setString(5, nz(p.talla));
                ps.setString(6, nz(p.color));
                ps.setBigDecimal(7, p.precio == null ? BigDecimal.ZERO : p.precio);
                ps.setBigDecimal(8, p.descuento == null ? BigDecimal.ZERO : p.descuento);
                ps.setDate(9, (fechaEvento == null) ? null : Date.valueOf(fechaEvento));
                ps.setString(10, telefono);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    /** 
 * Actualiza Pedidos.codigo_articulo.
 * Si el código NO existe en Inventarios, crea el artículo con los datos del pedido y existencia=1 (status 'A').
 */
public void actualizarCodigoArticulo(int numeroNota, String codigoNuevo) throws SQLException {
    try (Connection cn = Conecta.getConnection()) {
        boolean auto = cn.getAutoCommit();
        cn.setAutoCommit(false);
        try {
            // 1) Si hay código: crear en Inventarios si no existe
            if (codigoNuevo != null) {
                InventarioDAO invDao = new InventarioDAO();
                Inventario ya = invDao.buscarPorCodigo(codigoNuevo);
                if (ya == null) {
                    PedidoRow p = leerPedidoBasico(cn, numeroNota);
                    if (p == null) throw new SQLException("Pedido no encontrado para nota " + numeroNota);

                    Inventario inv = new Inventario();
                    inv.setCodigoArticulo(codigoNuevo);
                    inv.setArticulo(nz(p.articulo));
                    inv.setMarca(nz(p.marca));
                    inv.setModelo(nz(p.modelo));
                    inv.setTalla(nz(p.talla));
                    inv.setColor(nz(p.color));
                    inv.setPrecio(p.precio == null ? 0.0 : p.precio.doubleValue());
                    inv.setDescuento(p.descuento == null ? null : p.descuento.doubleValue());
                    inv.setExistencia(0);          // <- pieza que llegó a tienda
                    inv.setStatus("A");
                    invDao.insertar(inv);          // fecha_registro la pone el INSERT (CURDATE)
                }
            }

            // 2) Escribir el código (o NULL) en Pedidos
            try (PreparedStatement up = cn.prepareStatement(
                    "UPDATE Pedidos SET codigo_articulo=? WHERE numero_nota=?")) {
                if (codigoNuevo == null) up.setNull(1, java.sql.Types.INTEGER);
                else                      up.setString(1,  codigoNuevo);
                up.setInt(2, numeroNota);
                up.executeUpdate();
            }

            cn.commit();
        } catch (SQLException ex) {
            cn.rollback();
            throw ex;
        } finally {
            cn.setAutoCommit(auto);
        }
    }
}

    public void actualizarCodigoArticuloPorNota(int numeroNota, Integer codigoArticulo) throws SQLException {
    try (var cn = Conecta.getConnection()) {
        // Si viene un código, validar que exista para no violar la FK
        if (codigoArticulo != null) {
            try (var chk = cn.prepareStatement(
                    "SELECT 1 FROM Inventarios WHERE codigo_articulo=? LIMIT 1")) {
                chk.setInt(1, codigoArticulo);
                try (var rs = chk.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("El código de artículo " + codigoArticulo +
                                " no existe en Inventarios.");
                    }
                }
            }
        }
        try (var ps = cn.prepareStatement(
                "UPDATE Pedidos SET codigo_articulo=? WHERE numero_nota=?")) {
            if (codigoArticulo == null) ps.setNull(1, java.sql.Types.INTEGER);
            else ps.setInt(1, codigoArticulo);
            ps.setInt(2, numeroNota);
            ps.executeUpdate();
        }
    }
}

// ¿Existe ese código en Inventarios?
public boolean existeCodigoArticulo(int codigo) throws SQLException {
    try (var cn = Conecta.getConnection();
         var ps = cn.prepareStatement(
             "SELECT 1 FROM Inventarios WHERE codigo_articulo=? LIMIT 1")) {
        ps.setInt(1, codigo);
        try (var rs = ps.executeQuery()) { return rs.next(); }
    }
}


    // ===================== LECTURA / MARCADO =====================

    /** Lista TODOS los pedidos (con fechaEntrega calculada). */
    public List<PedidoRow> listarTodos() throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            boolean tieneEnTienda = hasColumn(cn, "Pedidos", "en_tienda");

            String select =
                "SELECT numero_nota, fecha_registro, articulo, marca, modelo, talla, color," +
                "       precio, descuento, fecha_evento, telefono, codigo_articulo, status" +
                (tieneEnTienda ? ", en_tienda" : "") +
                ", COALESCE(fecha_evento, fecha_registro) AS fecha_entrega " +
                "FROM Pedidos " +
                "ORDER BY COALESCE(fecha_registro, CURRENT_DATE) DESC, numero_nota DESC";

            try (PreparedStatement ps = cn.prepareStatement(select);
                 ResultSet rs = ps.executeQuery()) {
                List<PedidoRow> out = new ArrayList<>();
                while (rs.next()) {
                    PedidoRow r = new PedidoRow();
                    r.numeroNota     = rs.getInt("numero_nota");
                    r.fechaRegistro  = rs.getDate("fecha_registro");
                    r.articulo       = rs.getString("articulo");
                    r.marca          = rs.getString("marca");
                    r.modelo         = rs.getString("modelo");
                    r.talla          = rs.getString("talla");
                    r.color          = rs.getString("color");
                    r.precio         = rs.getBigDecimal("precio");
                    r.descuento      = rs.getBigDecimal("descuento");
                    r.fechaEvento    = rs.getDate("fecha_evento");
                    r.fechaEntrega   = rs.getDate("fecha_entrega");
                    r.telefono       = rs.getString("telefono");
                    String cod       = rs.getString("codigo_articulo");
                    r.codigoArticulo = cod;
                    r.status         = rs.getString("status");
                    if (tieneEnTienda) {
                        String en = rs.getString("en_tienda");
                        r.enTienda = "S".equalsIgnoreCase(en) || "1".equals(en) || "true".equalsIgnoreCase(en);
                    } else {
                        r.enTienda = "C".equalsIgnoreCase(nz(r.status)); // Fallback: 'C' = recibido/en tienda
                    }
                    out.add(r);
                }
                return out;
            }
        }
    }

    /** Lista SOLO los pendientes (con fechaEntrega calculada). */
public List<PedidoRow> listarPendientes() throws SQLException {
    try (Connection cn = Conecta.getConnection()) {
        boolean tieneEnTienda = hasColumn(cn, "Pedidos", "en_tienda");
        String fc = folioCol(cn);
        String folioSel = (fc == null) ? "CONCAT(p.numero_nota,'') AS folio" : ("n."+fc+" AS folio");

        String select =
            "SELECT " + folioSel + ", p.numero_nota, p.fecha_registro, p.articulo, p.marca, p.modelo, p.talla, p.color," +
            "       p.precio, p.descuento, p.fecha_evento, p.telefono, p.codigo_articulo, p.status " +
            (tieneEnTienda ? ", p.en_tienda " : "") +
            ", COALESCE(p.fecha_evento, p.fecha_registro) AS fecha_entrega " +
            "FROM Pedidos p " +
            "LEFT JOIN Notas n ON n.numero_nota = p.numero_nota " +
            (tieneEnTienda
                ? "WHERE COALESCE(p.en_tienda,'N') <> 'S' "
                : "WHERE COALESCE(p.status,'A') <> 'C' ") +
            "ORDER BY COALESCE(p.fecha_registro, CURRENT_DATE) DESC, p.numero_nota DESC";

        try (PreparedStatement ps = cn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            List<PedidoRow> out = new ArrayList<>();
            while (rs.next()) {
                PedidoRow r = new PedidoRow();
                r.folio          = rs.getString("folio");
                r.numeroNota     = rs.getInt("numero_nota");
                r.fechaRegistro  = rs.getDate("fecha_registro");
                r.articulo       = rs.getString("articulo");
                r.marca          = rs.getString("marca");
                r.modelo         = rs.getString("modelo");
                r.talla          = rs.getString("talla");
                r.color          = rs.getString("color");
                r.precio         = rs.getBigDecimal("precio");
                r.descuento      = rs.getBigDecimal("descuento");
                r.fechaEvento    = rs.getDate("fecha_evento");
                r.fechaEntrega   = rs.getDate("fecha_entrega");
                r.telefono       = rs.getString("telefono");
                String cod       = rs.getString("codigo_articulo");
                r.codigoArticulo = cod;
                r.status         = rs.getString("status");
                if (tieneEnTienda) {
                    String en = rs.getString("en_tienda");
                    r.enTienda = "S".equalsIgnoreCase(en) || "1".equals(en) || "true".equalsIgnoreCase(en);
                } else {
                    r.enTienda = "C".equalsIgnoreCase(r.status == null ? "" : r.status);
                }
                out.add(r);
            }
            return out;
        }
    }
}

public List<PedidoRow> listarEnTienda() throws SQLException {
    try (Connection cn = Conecta.getConnection()) {
        boolean tieneEnTienda = hasColumn(cn, "Pedidos", "en_tienda");
        String fc = folioCol(cn);
        String folioSel = (fc == null) ? "CONCAT(p.numero_nota,'') AS folio" : ("n."+fc+" AS folio");

        String select =
            "SELECT " + folioSel + ", p.numero_nota, p.fecha_registro, p.articulo, p.marca, p.modelo, p.talla, p.color," +
            "       p.precio, p.descuento, p.fecha_evento, p.telefono, p.codigo_articulo, p.status " +
            (tieneEnTienda ? ", p.en_tienda " : "") +
            ", COALESCE(p.fecha_evento, p.fecha_registro) AS fecha_entrega " +
            "FROM Pedidos p " +
            "LEFT JOIN Notas n ON n.numero_nota = p.numero_nota " +
            (tieneEnTienda
                ? "WHERE COALESCE(p.en_tienda,'N') = 'S' "
                : "WHERE COALESCE(p.status,'A') = 'C' ") +
            "ORDER BY COALESCE(p.fecha_registro, CURRENT_DATE) DESC, p.numero_nota DESC";

        try (PreparedStatement ps = cn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            List<PedidoRow> out = new ArrayList<>();
            while (rs.next()) {
                PedidoRow r = new PedidoRow();
                r.folio          = rs.getString("folio");
                r.numeroNota     = rs.getInt("numero_nota");
                r.fechaRegistro  = rs.getDate("fecha_registro");
                r.articulo       = rs.getString("articulo");
                r.marca          = rs.getString("marca");
                r.modelo         = rs.getString("modelo");
                r.talla          = rs.getString("talla");
                r.color          = rs.getString("color");
                r.precio         = rs.getBigDecimal("precio");
                r.descuento      = rs.getBigDecimal("descuento");
                r.fechaEvento    = rs.getDate("fecha_evento");
                r.fechaEntrega   = rs.getDate("fecha_entrega");
                r.telefono       = rs.getString("telefono");
                String cod       = rs.getString("codigo_articulo");
                r.codigoArticulo = cod;
                r.status         = rs.getString("status");
                if (tieneEnTienda) {
                    String en = rs.getString("en_tienda");
                    r.enTienda = "S".equalsIgnoreCase(en) || "1".equals(en) || "true".equalsIgnoreCase(en);
                } else {
                    r.enTienda = "C".equalsIgnoreCase(r.status == null ? "" : r.status);
                }
                out.add(r);
            }
            return out;
        }
    }
}

    /** Marca/desmarca “en tienda” para renglones de NOTA_DETALLE y también actualiza Pedidos/entregas_vestidos. */
    public void setEntregaEnTienda(int detId, int numeroNota, boolean enTienda) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            boolean auto = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try {
                // 1) Cambia status del detalle
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Nota_Detalle SET status=? WHERE id=?")) {
                    ps.setString(1, enTienda ? "C" : "A");
                    ps.setInt(2, detId);
                    ps.executeUpdate();
                }

                // 2) También refleja en Pedidos (si existe)
                if (hasColumn(cn, "Pedidos", "en_tienda")) {
                    try (PreparedStatement up = cn.prepareStatement(
                            "UPDATE Pedidos SET en_tienda=?, en_tienda_ts=NOW() WHERE numero_nota=?")) {
                        up.setString(1, enTienda ? "S" : "N");
                        up.setInt(2, numeroNota);
                        up.executeUpdate();
                    }
                } else {
                    try (PreparedStatement up = cn.prepareStatement(
                            "UPDATE Pedidos SET status=? WHERE numero_nota=?")) {
                        up.setString(1, enTienda ? "C" : "A");
                        up.setInt(2, numeroNota);
                        up.executeUpdate();
                    }
                }

                // 3) Registrar/actualizar en entregas_vestidos (si la tabla existe)
                upsertEntregaDesdeDetalle(cn, detId, enTienda);

                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(auto);
            }
        }
    }

    // --- listar ENTREGADOS desde la tabla entregas_vestidos ---
    public List<EntregaVestidoRow> listarEntregadosVestidos(LocalDate ini, LocalDate fin) throws SQLException {
        List<EntregaVestidoRow> out = new ArrayList<>();
        try (Connection cn = Conecta.getConnection()) {
            String fc = folioCol(cn);
            String folioSelEV = (fc == null) ? "CONCAT(ev.numero_nota,'') AS folio" : ("n."+fc+" AS folio");

            String sql =
                "SELECT ev.numero_nota, " + folioSelEV + ", ev.cliente AS telefono, " +
                "       COALESCE(nd.articulo, p.articulo)  AS articulo, " +
                "       COALESCE(nd.marca,    p.marca)     AS marca, " +
                "       COALESCE(nd.modelo,   p.modelo)    AS modelo, " +
                "       COALESCE(nd.talla,    p.talla)     AS talla, " +
                "       COALESCE(nd.color,    p.color)     AS color, " +
                "       COALESCE(nd.precio,   p.precio)    AS precio, " +
                "       COALESCE(nd.descuento,p.descuento) AS descuento, " +
                "       ev.fecha_entrega, nd.id AS det_id " +
                "FROM entregas_vestidos ev " +
                "LEFT JOIN Notas n ON n.numero_nota = ev.numero_nota " +
                "LEFT JOIN Nota_Detalle nd ON nd.numero_nota = ev.numero_nota " +
                "  AND (UPPER(nd.articulo)='VESTIDO' OR UPPER(nd.articulo) LIKE 'VESTIDO%') " +
                "  AND (nd.articulo = ev.articulo) " +
                "LEFT JOIN Pedidos p ON p.numero_nota = ev.numero_nota " +
                "  AND (UPPER(p.articulo)='VESTIDO' OR UPPER(p.articulo) LIKE 'VESTIDO%') " +
                "  AND (p.articulo = ev.articulo) " +
                "WHERE ev.fecha_entrega >= ? AND ev.fecha_entrega < ? " +
                "  AND COALESCE(ev.entregado,'N')='S' " +
                "ORDER BY ev.fecha_entrega, ev.numero_nota";

            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(ini));
                ps.setDate(2, Date.valueOf(fin));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        EntregaVestidoRow r = new EntregaVestidoRow();
                        r.numeroNota    = rs.getInt("numero_nota");
                        r.folio         = rs.getString("folio");
                        r.telefono      = rs.getString("telefono");
                        r.articulo      = rs.getString("articulo");
                        r.marca         = rs.getString("marca");
                        r.modelo        = rs.getString("modelo");
                        r.talla         = rs.getString("talla");
                        r.color         = rs.getString("color");
                        r.precio        = rs.getBigDecimal("precio");
                        r.descuento     = rs.getBigDecimal("descuento");
                        r.fechaEntrega  = rs.getDate("fecha_entrega");
                        r.fechaEvento   = null;
                        r.fechaRegistro = null;
                        r.enTienda      = true; // ya entregado
                        int det = rs.getInt("det_id");
                        r.detId         = rs.wasNull() ? null : det;
                        r.fromPedidos   = (r.detId == null);
                        out.add(r);
                    }
                }
            }
        }
        return out;
    }

    /** Detecta el nombre de columna de folio en Notas (o null si no existe). */
    private static String folioCol(Connection cn){
        if (hasColumn(cn, "Notas", "folio_venta"))  return "folio_venta";
        if (hasColumn(cn, "Notas", "folio"))        return "folio";
        if (hasColumn(cn, "Notas", "folio_ticket")) return "folio_ticket";
        return null;
    }

    /** Marca/desmarca “en tienda” para filas que provienen de la tabla PEDIDOS. */
    public void setPedidoEnTienda(int numeroNota, boolean enTienda) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            boolean auto = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try {
                if (hasColumn(cn, "Pedidos", "en_tienda")) {
                    try (PreparedStatement up = cn.prepareStatement(
                            "UPDATE Pedidos SET en_tienda=?, en_tienda_ts=NOW() WHERE numero_nota=?")) {
                        up.setString(1, enTienda ? "S" : "N");
                        up.setInt(2, numeroNota);
                        up.executeUpdate();
                    }
                } else {
                    try (PreparedStatement up = cn.prepareStatement(
                            "UPDATE Pedidos SET status=? WHERE numero_nota=?")) {
                        up.setString(1, enTienda ? "C" : "A");
                        up.setInt(2, numeroNota);
                        up.executeUpdate();
                    }
                }

                // Registrar/actualizar en entregas_vestidos
                upsertEntregaDesdePedido(cn, numeroNota, enTienda);

                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(auto);
            }
        }
    }


    // ===================== HELPERS =====================
    /** Lee los campos del pedido necesarios para crear inventario. */
private PedidoRow leerPedidoBasico(Connection cn, int numeroNota) throws SQLException {
    String q = "SELECT articulo, marca, modelo, talla, color, precio, descuento " +
               "FROM Pedidos WHERE numero_nota=? LIMIT 1";
    try (PreparedStatement ps = cn.prepareStatement(q)) {
        ps.setInt(1, numeroNota);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            PedidoRow r = new PedidoRow();
            r.articulo  = rs.getString("articulo");
            r.marca     = rs.getString("marca");
            r.modelo    = rs.getString("modelo");
            r.talla     = rs.getString("talla");
            r.color     = rs.getString("color");
            r.precio    = rs.getBigDecimal("precio");
            r.descuento = rs.getBigDecimal("descuento");
            return r;
        }
    }
}

    private static String nz(String s){ return (s == null) ? "" : s.trim(); }

    /** Verifica si una columna existe en la tabla (robusto con mayúsculas/minúsculas). */
    private static boolean hasColumn(Connection cn, String table, String column) {
        String t1 = table;
        String t2 = table.toUpperCase();
        String t3 = table.toLowerCase();
        String c1 = column;
        String c2 = column.toUpperCase();
        String c3 = column.toLowerCase();
        return probeColumn(cn, t1, c1) || probeColumn(cn, t1, c2) || probeColumn(cn, t1, c3) ||
               probeColumn(cn, t2, c1) || probeColumn(cn, t2, c2) || probeColumn(cn, t2, c3) ||
               probeColumn(cn, t3, c1) || probeColumn(cn, t3, c2) || probeColumn(cn, t3, c3);
    }

    private static boolean probeColumn(Connection cn, String table, String column) {
        String sql = "SELECT " + column + " FROM " + table + " WHERE 1=0";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.executeQuery();
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    // ====== Entregas_Vestidos ======

    /** ¿Existe la tabla entregas_vestidos? (probamos columna 'id') */
    private static boolean hasEntregasVestidos(Connection cn) {
        return hasColumn(cn, "entregas_vestidos", "id");
    }

    /** Upsert usando datos tomados de Nota_Detalle + Notas (incluye FECHA ENTREGA calculada). */
    private static void upsertEntregaDesdeDetalle(Connection cn, int detId, boolean enTienda) throws SQLException {
        if (!hasEntregasVestidos(cn)) return;
        Integer num = null; String tel = null; String art = null; Date fEnt = null;

        try (PreparedStatement q = cn.prepareStatement(
                "SELECT d.numero_nota, n.telefono, d.articulo, " +
                "       COALESCE(d.fecha_entrega, d.fecha_evento, d.fecha_registro, CURRENT_DATE) AS f_ent " +
                "FROM Nota_Detalle d JOIN Notas n ON n.numero_nota=d.numero_nota WHERE d.id=?")) {
            q.setInt(1, detId);
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) {
                    num  = rs.getInt("numero_nota");
                    tel  = rs.getString("telefono");
                    art  = rs.getString("articulo");
                    fEnt = rs.getDate("f_ent");
                }
            }
        }
        if (num != null) upsertEntrega(cn, num, tel, art, fEnt, enTienda);
    }

    /** Upsert usando datos de Pedidos (incluye FECHA ENTREGA calculada). */
    private static void upsertEntregaDesdePedido(Connection cn, int numeroNota, boolean enTienda) throws SQLException {
        if (!hasEntregasVestidos(cn)) return;
        String tel = null; String art = null; Date fEnt = null;

        try (PreparedStatement q = cn.prepareStatement(
                "SELECT telefono, articulo, COALESCE(fecha_evento, fecha_registro, CURRENT_DATE) AS f_ent " +
                "FROM Pedidos WHERE numero_nota=? LIMIT 1")) {
            q.setInt(1, numeroNota);
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) {
                    tel  = rs.getString("telefono");
                    art  = rs.getString("articulo");
                    fEnt = rs.getDate("f_ent");
                }
            }
        }
        if (fEnt == null) fEnt = Date.valueOf(LocalDate.now());
        upsertEntrega(cn, numeroNota, tel, art, fEnt, enTienda);
    }

    /** Inserta o actualiza un registro en entregas_vestidos (fecha_entrega NUNCA NULL). */
    private static void upsertEntrega(Connection cn, int numeroNota, String telefono, String articulo,
                                      Date fechaEntrega, boolean enTienda) throws SQLException {
        if (!hasEntregasVestidos(cn)) return;

        Integer id = null;
        try (PreparedStatement q = cn.prepareStatement(
                "SELECT id FROM entregas_vestidos WHERE numero_nota=? AND articulo=? LIMIT 1")) {
            q.setInt(1, numeroNota);
            q.setString(2, articulo == null ? "" : articulo);
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) id = rs.getInt(1);
            }
        }

        if (id == null) {
            try (PreparedStatement ins = cn.prepareStatement(
                    "INSERT INTO entregas_vestidos (numero_nota, cliente, articulo, fecha_entrega, entregado, entregado_ts) " +
                    "VALUES (?,?,?,?,?,NOW())")) {
                ins.setInt(1, numeroNota);
                ins.setString(2, telefono);
                ins.setString(3, articulo);
                ins.setDate(4, fechaEntrega);                 // <- NO NULL
                ins.setString(5, enTienda ? "S" : "N");
                ins.executeUpdate();
            }
        } else {
            try (PreparedStatement up = cn.prepareStatement(
                    "UPDATE entregas_vestidos SET cliente=?, fecha_entrega=?, entregado=?, entregado_ts=NOW() WHERE id=?")) {
                up.setString(1, telefono);
                up.setDate(2, fechaEntrega);                  // <- NO NULL
                up.setString(3, enTienda ? "S" : "N");
                up.setInt(4, id);
                up.executeUpdate();
            }
        }
    }
}
