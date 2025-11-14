package Controlador;

import Conexion.Conecta;
import Modelo.Nota;

import java.sql.*;
import java.sql.Date;   
import java.time.LocalDate;
import java.util.*;

public class NotasDAO {
    /** Notas (todas) por rango de fechas de registro [ini, fin), más recientes primero. */
    public List<NotaResumen> listarNotasPorRangoResumen(java.time.LocalDate ini, java.time.LocalDate fin) throws SQLException {
        String sql = "SELECT numero_nota, tipo, folio, DATE(fecha_registro) AS fecha, total, saldo, status " +
                     "FROM Notas " +
                     "WHERE fecha_registro >= ? AND fecha_registro < ? " +
                     "ORDER BY fecha_registro DESC, numero_nota DESC";
        try (java.sql.Connection cn = Conexion.Conecta.getConnection();
             java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(ini));
            ps.setDate(2, java.sql.Date.valueOf(fin));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                java.util.List<NotaResumen> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    NotaResumen r = new NotaResumen();
                    r.numero = rs.getInt("numero_nota");
                    r.tipo   = rs.getString("tipo");
                    try { r.folio = rs.getString("folio"); } catch (Throwable ignore) {}
                    java.sql.Date f = rs.getDate("fecha");
                    r.fecha = (f == null ? null : f.toLocalDate());
                    try { r.total = (rs.getBigDecimal("total")==null? null : rs.getBigDecimal("total").doubleValue()); }
                    catch(Throwable t){ r.total = rs.getDouble("total"); }
                    try { r.saldo = (rs.getBigDecimal("saldo")==null? null : rs.getBigDecimal("saldo").doubleValue()); }
                    catch(Throwable t){ r.saldo = rs.getDouble("saldo"); }
                    r.status = rs.getString("status");
                    out.add(r);
                }
                return out;
            }
        }
    }

    public List<Nota> listarCreditosConSaldo(String telefono) throws SQLException {
        String sql =
            "SELECT numero_nota, telefono, asesor, tipo, total, saldo, status, folio, fecha_registro " +
            "FROM Notas " +
            "WHERE tipo='CR' AND status='A' AND saldo > 0 AND telefono = ? " +
            "ORDER BY fecha_registro DESC, numero_nota DESC";

        List<Nota> out = new ArrayList<>();
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Nota n = new Nota();
                    n.setNumeroNota(rs.getInt("numero_nota"));
                    n.setTelefono(rs.getString("telefono"));

                    int a = rs.getInt("asesor");
                    if (rs.wasNull()) n.setAsesor(null); else n.setAsesor(a);

                    n.setTipo(rs.getString("tipo"));
                    n.setTotal(rs.getDouble("total"));
                    n.setSaldo(rs.getDouble("saldo"));
                    n.setStatus(rs.getString("status"));
                    n.setFolio(rs.getString("folio"));
                    out.add(n);
                }
            }
        }
        return out;
    }

    /** Devuelve la última nota (numero_nota) activa del cliente, o null si no tiene. */
    public Integer obtenerUltimaNotaPorTelefono(String telefono1) throws SQLException {
        String sql = "SELECT MAX(numero_nota) AS ult " +
                     "FROM Notas WHERE telefono = ? AND status = 'A'";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, telefono1);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int v = rs.getInt("ult");
                return rs.wasNull() ? null : v;
            }
        }
    }

    public List<Modelo.Nota> listarNotasClienteParaDevolucion(String telefono) throws SQLException {
        String sql = "SELECT numero_nota, telefono, asesor, tipo, total, saldo, status, folio " +
                     "FROM Notas WHERE telefono=? AND status='A' AND tipo IN ('CN','CR') " +
                     "ORDER BY numero_nota DESC";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                List<Modelo.Nota> out = new ArrayList<>();
                while (rs.next()) {
                    Modelo.Nota n = new Modelo.Nota();
                    n.setNumeroNota(rs.getInt("numero_nota"));
                    n.setTelefono(rs.getString("telefono"));
                    int a = rs.getInt("asesor"); n.setAsesor(rs.wasNull()? null : a);
                    n.setTipo(rs.getString("tipo"));
                    n.setTotal(rs.getBigDecimal("total")==null? null : rs.getBigDecimal("total").doubleValue());
                    n.setSaldo(rs.getBigDecimal("saldo")==null? null : rs.getBigDecimal("saldo").doubleValue());
                    n.setStatus(rs.getString("status"));
                    try { n.setFolio(rs.getString("folio")); } catch (Throwable ignore){}
                    out.add(n);
                }
                return out;
            }
        }
    }

    public static class PendienteDev {
        public int codigoArticulo;
        public String articulo;    // opcional: nombre visible
        public int vendidos;
        public int devueltos;
        public int pendientes;
    }

    /** Calcula pendientes de devolución sin usar 'nota_relacionada'. */
    public List<PendienteDev> pendientesDevolucion(int numeroNota) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {

            // 1) Vendidos por artículo en la nota original
            Map<Integer,Integer> vendidos = new HashMap<>();
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT codigo_articulo, COUNT(*) AS vendidos " +
                    "FROM Nota_Detalle WHERE numero_nota=? GROUP BY codigo_articulo")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        vendidos.put(rs.getInt("codigo_articulo"), rs.getInt("vendidos"));
                    }
                }
            }

            // 2) Devueltos: Devoluciones -> Notas(DV) -> Nota_Detalle
            Map<Integer,Integer> devueltos = new HashMap<>();
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT nd.codigo_articulo, COUNT(*) AS devueltos " +
                    "FROM Devoluciones dv " +
                    "JOIN Notas n       ON n.numero_nota = dv.numero_nota_dv AND n.tipo='DV' " +
                    "JOIN Nota_Detalle nd ON nd.numero_nota = n.numero_nota " +
                    "WHERE dv.nota_origen=? " +
                    "GROUP BY nd.codigo_articulo")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        devueltos.put(rs.getInt("codigo_articulo"), rs.getInt("devueltos"));
                    }
                }
            }

            // 3) Armar la lista con “pendientes”
            List<PendienteDev> out = new ArrayList<>();
            try (PreparedStatement psArt = cn.prepareStatement(
                    "SELECT articulo FROM Inventarios WHERE codigo_articulo=?")) {
                for (Map.Entry<Integer,Integer> e : vendidos.entrySet()) {
                    int cod = e.getKey();
                    int ven = e.getValue();
                    int dev = devueltos.getOrDefault(cod, 0);
                    int pen = ven - dev;
                    if (pen <= 0) continue;

                    PendienteDev p = new PendienteDev();
                    p.codigoArticulo = cod;
                    p.vendidos = ven;
                    p.devueltos = dev;
                    p.pendientes = pen;

                    psArt.setInt(1, cod);
                    try (ResultSet rs = psArt.executeQuery()) {
                        if (rs.next()) p.articulo = rs.getString(1);
                    }
                    out.add(p);
                }
            }

            out.sort(Comparator.comparingInt(a -> a.codigoArticulo));
            return out;
        }
    }

    /** Detalle de una nota (incluye 'id'). *Se eliminó el filtro por status para no ocultar renglones*. */
    /** Detalle de una nota incluyendo también los artículos PEDIDOS. */
public List<Modelo.NotaDetalle> listarDetalleDeNota(int numeroNota) throws SQLException {
    String sql =
        "SELECT id, codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, subtotal " +
        "FROM (" +
        "   SELECT 1 AS ord, d.id AS id, d.codigo_articulo, d.articulo, d.marca, d.modelo, d.talla, d.color, " +
        "          d.precio, d.descuento, d.subtotal " +
        "   FROM Nota_Detalle d " +
        "   WHERE d.numero_nota = ? " +        // sin filtro por status para no ocultar renglones
        "   UNION ALL " +
        "   SELECT 2 AS ord, p.id AS id, NULL AS codigo_articulo, " +
        "          CONCAT('PEDIDO – ', p.articulo) AS articulo, p.marca, p.modelo, p.talla, p.color, " +
        "          p.precio, p.descuento, (p.precio - (p.precio * (p.descuento/100))) AS subtotal " +
        "   FROM Pedidos p " +
        "   WHERE p.numero_nota = ? " +
        ") x " +
        "ORDER BY ord, id";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setInt(1, numeroNota);
        ps.setInt(2, numeroNota);
        try (ResultSet rs = ps.executeQuery()) {
            List<Modelo.NotaDetalle> out = new ArrayList<>();
            while (rs.next()) {
                Modelo.NotaDetalle d = new Modelo.NotaDetalle();
                d.setId(rs.getInt("id"));
                d.setCodigoArticulo((Integer) rs.getObject("codigo_articulo")); // null para PEDIDOS
                d.setArticulo(rs.getString("articulo"));
                d.setMarca(rs.getString("marca"));
                d.setModelo(rs.getString("modelo"));
                d.setTalla(rs.getString("talla"));
                d.setColor(rs.getString("color"));
                d.setPrecio(rs.getBigDecimal("precio")==null? null : rs.getBigDecimal("precio").doubleValue());
                d.setDescuento(rs.getBigDecimal("descuento")==null? null : rs.getBigDecimal("descuento").doubleValue());
                d.setSubtotal(rs.getBigDecimal("subtotal")==null? null : rs.getBigDecimal("subtotal").doubleValue());
                out.add(d);
            }
            return out;
        }
    }
}
    // ================== NUEVO: listar notas con fecha distinta a la del cliente ==================
    public static class NotaFechaRow {
    public int numero;
    public String folio;
    public String tipo;
    public java.time.LocalDate fechaEvento;   // uniforme -> valor; mixta -> null
    public java.time.LocalDate fechaEntrega;  // uniforme -> valor; mixta -> null
}

    /** Regresa las notas del cliente donde la fecha_evento de detalle difiere de la fecha del cliente
     *  o hay múltiples fechas distintas en los renglones de la nota. */
    public List<NotaFechaRow> notasConFechaEventoDistinta(String telefono,
                                                      java.time.LocalDate fechaCliente) throws SQLException {
    String sql =
        "SELECT n.numero_nota, n.folio, n.tipo, " +
        "       COUNT(DISTINCT nd.fecha_evento)  AS ev_d, " +
        "       MIN(nd.fecha_evento)             AS ev_min, " +
        "       COUNT(DISTINCT nd.fecha_entrega) AS en_d, " +
        "       MIN(nd.fecha_entrega)            AS en_min " +
        "FROM Notas n " +
        "JOIN Nota_Detalle nd ON nd.numero_nota = n.numero_nota " +
        "WHERE n.telefono=? AND COALESCE(nd.status,'A')='A' " +
        "GROUP BY n.numero_nota, n.folio, n.tipo " +
        // Mostrar notas con EVENTO diferente o mixto (ignorando NULLs)
        "HAVING ev_d > 1 OR (ev_d = 1 AND ev_min IS NOT NULL AND ev_min <> ?) " +
        "ORDER BY n.numero_nota DESC";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setString(1, telefono);
        ps.setDate(2, java.sql.Date.valueOf(fechaCliente));
        try (ResultSet rs = ps.executeQuery()) {
            List<NotaFechaRow> out = new ArrayList<>();
            while (rs.next()) {
                NotaFechaRow r = new NotaFechaRow();
                r.numero = rs.getInt("numero_nota");
                r.folio  = rs.getString("folio");
                r.tipo   = rs.getString("tipo");

                int evd   = rs.getInt("ev_d");
                Date evMin = rs.getDate("ev_min");
                int end   = rs.getInt("en_d");
                Date enMin = rs.getDate("en_min");

                r.fechaEvento  = (evd == 1 && evMin != null) ? evMin.toLocalDate() : null;
                r.fechaEntrega = (end == 1 && enMin != null) ? enMin.toLocalDate() : null;

                out.add(r);
            }
            return out;
        }
    }
}
    public static class FechasRenglon {
    public java.time.LocalDate fechaEvento;
    public java.time.LocalDate fechaEntrega;
}

public FechasRenglon leerFechasDeRenglon(int detalleId) throws SQLException {
    String sql = "SELECT fecha_evento, fecha_entrega FROM Nota_Detalle WHERE id=?";
    try (java.sql.Connection cn = Conexion.Conecta.getConnection();
         java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {
        ps.setInt(1, detalleId);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            FechasRenglon f = new FechasRenglon();
            java.sql.Date ev = rs.getDate(1), en = rs.getDate(2);
            f.fechaEvento  = (ev==null? null : ev.toLocalDate());
            f.fechaEntrega = (en==null? null : en.toLocalDate());
            return f;
        }
    }
}
    // POJO simple para el historial en “Detalle de cliente”.
    public static class NotaHistRow {
        public int    numeroNota;
        public String tipo;
        public String folio;
        public java.sql.Timestamp fechaRegistro;
        public Double total;
        public Double saldo;
        public String status;
    }

    /** Todas las notas del cliente (cualquier tipo), más recientes primero. */
    public List<NotaHistRow> historialPorTelefono(String telefono) throws SQLException {
        String sql = "SELECT numero_nota, tipo, folio, fecha_registro, total, saldo, status " +
                     "FROM Notas WHERE telefono = ? " +
                     "ORDER BY fecha_registro DESC, numero_nota DESC";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                List<NotaHistRow> out = new ArrayList<>();
                while (rs.next()) {
                    NotaHistRow r = new NotaHistRow();
                    r.numeroNota    = rs.getInt("numero_nota");
                    r.tipo          = rs.getString("tipo");
                    r.folio         = rs.getString("folio");
                    r.fechaRegistro = rs.getTimestamp("fecha_registro");
                    r.total         = rs.getBigDecimal("total")==null ? null : rs.getBigDecimal("total").doubleValue();
                    r.saldo         = rs.getBigDecimal("saldo")==null ? null : rs.getBigDecimal("saldo").doubleValue();
                    r.status        = rs.getString("status");
                    out.add(r);
                }
                return out;
            }
        }
    }

    // --- Resumen de notas para listados (reimpresión, etc.)
    public static class NotaResumen {
        public int numero;
        public String tipo;
        public String folio;
        public LocalDate fecha;
        public Double total;
        public Double saldo;
        public String status;

        public String uuidFactura;      // null si no hay
        public String estatusFactura;   // TIMBRADA/BORRADOR/CANCELADA o null
    }

    /** Notas de un cliente por teléfono (todas, orden más reciente primero). */
    public List<NotaResumen> listarNotasPorTelefonoResumen(String telefono) throws SQLException {
        String sql = "SELECT numero_nota, tipo, folio, DATE(fecha_registro) AS fecha, " +
                     "       total, saldo, status " +
                     "FROM Notas WHERE telefono=? " +
                     "ORDER BY fecha_registro DESC, numero_nota DESC";
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                List<NotaResumen> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    NotaResumen r = new NotaResumen();
                    r.numero = rs.getInt("numero_nota");
                    r.tipo   = rs.getString("tipo");
                    try { r.folio = rs.getString("folio"); } catch (Throwable ignore) {}
                    Date f = rs.getDate("fecha");
                    r.fecha = (f == null ? null : f.toLocalDate());
                    try { r.total = (rs.getBigDecimal("total")==null? null : rs.getBigDecimal("total").doubleValue()); }
                    catch(Throwable t){ r.total = rs.getDouble("total"); }
                    try { r.saldo = (rs.getBigDecimal("saldo")==null? null : rs.getBigDecimal("saldo").doubleValue()); }
                    catch(Throwable t){ r.saldo = rs.getDouble("saldo"); }
                    r.status = rs.getString("status");
                    r.uuidFactura    = rs.getString("uuid_fact");
                    r.estatusFactura = rs.getString("est_fact");
                    out.add(r);
                }
                return out;
            }
        }
    }
}
