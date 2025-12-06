    package Controlador;

    import Conexion.Conecta;
    import Modelo.Nota;

    import java.sql.*;
    import java.sql.Date;   
    import java.time.LocalDate;
    import java.util.*;

    public class NotasDAO {
        /** Notas (todas) por rango de fechas de registro [ini, fin), más recientes primero. */
/** Notas (todas) por rango de fechas [ini, fin), con saldo ajustado para CR/AB. */
public List<NotaResumen> listarNotasPorRangoResumen(java.time.LocalDate ini,
                                                    java.time.LocalDate fin) throws SQLException {
    String sql =
            "SELECT numero_nota, tipo, folio, DATE(fecha_registro) AS fecha, " +
            "       total, saldo, nota_relacionada, status " +
            "FROM Notas " +
            "WHERE fecha_registro >= ? AND fecha_registro < ? " +
            // ASC para poder recorrer cronológicamente
            "ORDER BY fecha_registro ASC, numero_nota ASC";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setDate(1, java.sql.Date.valueOf(ini));
        ps.setDate(2, java.sql.Date.valueOf(fin));

        try (ResultSet rs = ps.executeQuery()) {

            List<NotaResumen> out = new ArrayList<>();

            // Mapa: numero_nota (CR) -> lista de abonos (NotaResumen)
            Map<Integer, List<NotaResumen>> abonosPorCredito = new HashMap<>();

            while (rs.next()) {
                NotaResumen r = new NotaResumen();
                r.numero = rs.getInt("numero_nota");
                r.tipo   = rs.getString("tipo");
                try { r.folio = rs.getString("folio"); } catch (Throwable ignore) {}

                java.sql.Date f = rs.getDate("fecha");
                r.fecha = (f == null ? null : f.toLocalDate());

                try {
                    java.math.BigDecimal bdTot = rs.getBigDecimal("total");
                    r.total = (bdTot == null ? null : bdTot.doubleValue());
                } catch (Throwable t) {
                    r.total = rs.getDouble("total");
                }

                try {
                    java.math.BigDecimal bdSal = rs.getBigDecimal("saldo");
                    r.saldo = (bdSal == null ? null : bdSal.doubleValue());
                } catch (Throwable t) {
                    r.saldo = rs.getDouble("saldo");
                }

                r.status = rs.getString("status");

                // Si es ABONO, guardamos la relación contra su crédito
                String tipoUpper = (r.tipo == null ? "" : r.tipo.trim().toUpperCase());
                if ("AB".equals(tipoUpper)) {
                    int numRel = rs.getInt("nota_relacionada");
                    if (!rs.wasNull()) {
                        abonosPorCredito
                                .computeIfAbsent(numRel, k -> new ArrayList<>())
                                .add(r);
                    }
                }

                out.add(r);
            }

            // ===================== AJUSTE DE SALDOS SECUENCIALES POR CRÉDITO =====================
            if (!abonosPorCredito.isEmpty()) {

                // Créditos presentes en el rango
                Map<Integer, NotaResumen> creditosPorNumero = new HashMap<>();
                for (NotaResumen r : out) {
                    String t = (r.tipo == null ? "" : r.tipo.trim().toUpperCase());
                    if ("CR".equals(t) && r.numero > 0) {
                        creditosPorNumero.put(r.numero, r);
                    }
                }

                Comparator<NotaResumen> cmp =
                        Comparator.comparing(
                                        (NotaResumen r) -> r.fecha,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .thenComparingInt(r -> r.numero);

                FormasPagoDAO fdao = new FormasPagoDAO();

                for (Map.Entry<Integer, List<NotaResumen>> e : abonosPorCredito.entrySet()) {
                    Integer numCredito = e.getKey();
                    NotaResumen credito = creditosPorNumero.get(numCredito);

                    // Si el crédito no está en el rango de fechas, no podemos recalcularlo aquí
                    if (credito == null) continue;

                    // Saldo inicial REAL del crédito = total - pagos iniciales del CR
                    Double saldoInicial = calcularSaldoInicialCredito(credito, fdao);
                    if (saldoInicial == null) continue;

                    // Mostrar este saldo inicial en la fila del CR
                    credito.saldo = saldoInicial;

                    List<NotaResumen> abonos = e.getValue();
                    abonos.sort(cmp);

                    double saldoTmp = saldoInicial;
                    for (NotaResumen ab : abonos) {
                        double montoAbono = (ab.total == null ? 0.0 : ab.total);
                        saldoTmp -= montoAbono;
                        if (Math.abs(saldoTmp) < 0.01) saldoTmp = 0.0;  // tolerancia centavos
                        // saldo DESPUÉS de este abono
                        ab.saldo = saldoTmp;
                    }
                }
            }

            // Volver a "más recientes primero" como cuando lo usabas antes
            out.sort(
                    Comparator.comparing(
                                    (NotaResumen r) -> r.fecha,
                                    Comparator.nullsLast(Comparator.naturalOrder())
                            )
                            .thenComparingInt(r -> r.numero)
                            .reversed()
            );

            return out;
        }
    }
}
            /** Busca una nota por folio. Devuelve null si no existe.
         *  Se usa para la Hoja de Entrega. */
        public Nota buscarNotaPorFolio(String folio) throws SQLException {
    String sql = "SELECT numero_nota, fecha_registro, telefono, asesor, " +
                 "tipo, total, saldo, status, folio " +
                 "FROM Notas WHERE folio = ? " +
                 "ORDER BY fecha_registro DESC, numero_nota DESC";
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setString(1, folio);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;

            Nota n = new Nota();
            n.setNumeroNota(rs.getInt("numero_nota"));

            Timestamp ts = rs.getTimestamp("fecha_registro");
            if (ts != null) {
                n.setFechaRegistro(ts.toLocalDateTime());
            }

            n.setTelefono(rs.getString("telefono"));

            int asesor = rs.getInt("asesor");
            if (!rs.wasNull()) {
                n.setAsesor(asesor);
            }

            // FALTABA ESTO:
            n.setTipo(rs.getString("tipo"));

            try {
                n.setTotal(
                        rs.getBigDecimal("total") == null
                                ? null
                                : rs.getBigDecimal("total").doubleValue()
                );
            } catch (Throwable t) {
                n.setTotal(rs.getDouble("total"));
            }

            try {
                n.setSaldo(
                        rs.getBigDecimal("saldo") == null
                                ? null
                                : rs.getBigDecimal("saldo").doubleValue()
                );
            } catch (Throwable t) {
                n.setSaldo(rs.getDouble("saldo"));
            }

            n.setStatus(rs.getString("status"));
            n.setFolio(rs.getString("folio"));
            return n;
        }
    }
}
            /** Notas liquidadas (saldo ~ 0) de un teléfono. */
        public List<Nota> listarNotasLiquidadasPorTelefono(String telefono) throws SQLException {
            String sql = "SELECT numero_nota, fecha_registro, telefono, asesor, " +
                        "tipo, total, saldo, status, folio " +
                        "FROM Notas " +
                        "WHERE telefono = ? " +
                        "AND (saldo IS NULL OR ABS(saldo) < 0.01) " +
                        "AND tipo IN ('CN','CR') " +  
                        "ORDER BY fecha_registro DESC, numero_nota DESC";

            List<Nota> lista = new java.util.ArrayList<>();

            try (java.sql.Connection cn = Conexion.Conecta.getConnection();
                java.sql.PreparedStatement ps = cn.prepareStatement(sql)) {

                ps.setString(1, telefono);

                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Nota n = new Nota();
                        n.setNumeroNota(rs.getInt("numero_nota"));

                        java.sql.Timestamp ts = rs.getTimestamp("fecha_registro");
                        if (ts != null) {
                            n.setFechaRegistro(ts.toLocalDateTime());
                        }

                        n.setTelefono(rs.getString("telefono"));

                        int asesor = rs.getInt("asesor");
                        if (!rs.wasNull()) {
                            n.setAsesor(asesor);
                        }

                        try {
                            java.math.BigDecimal bdTot = rs.getBigDecimal("total");
                            n.setTotal(bdTot == null ? null : bdTot.doubleValue());
                        } catch (Throwable t) {
                            n.setTotal(rs.getDouble("total"));
                        }

                        try {
                            java.math.BigDecimal bdSal = rs.getBigDecimal("saldo");
                            n.setSaldo(bdSal == null ? null : bdSal.doubleValue());
                        } catch (Throwable t) {
                            n.setSaldo(rs.getDouble("saldo"));
                        }

                        n.setStatus(rs.getString("status"));
                        n.setFolio(rs.getString("folio"));

                        lista.add(n);
                    }
                }
            }

            return lista;
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
/** Detalle de una nota incluyendo también PEDIDOS y MANUFACTURAS. */
public List<Modelo.NotaDetalle> listarDetalleDeNota(int numeroNota) throws SQLException {
    String sql =
        "SELECT id, codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, subtotal " +
        "FROM (" +
        // 1) Renglones normales de Nota_Detalle
        "   SELECT 1 AS ord, d.id AS id, d.codigo_articulo, d.articulo, d.marca, d.modelo, d.talla, d.color, " +
        "          d.precio, d.descuento, d.subtotal " +
        "   FROM Nota_Detalle d " +
        "   WHERE d.numero_nota = ? " +
        "   UNION ALL " +
        // 2) Pedidos ligados a la nota
        "   SELECT 2 AS ord, p.id AS id, NULL AS codigo_articulo, " +
        "          CONCAT('PEDIDO – ', p.articulo) AS articulo, p.marca, p.modelo, p.talla, p.color, " +
        "          p.precio, p.descuento, " +
        "          (p.precio - (p.precio * (p.descuento/100))) AS subtotal " +
        "   FROM Pedidos p " +
        "   WHERE p.numero_nota = ? " +
        "   UNION ALL " +
        // 3) MANUFACTURAS ligadas a la nota
        "   SELECT 3 AS ord, m.id_manufactura AS id, NULL AS codigo_articulo, " +
        "          CONCAT('MODISTA – ', m.articulo) AS articulo, " +   // <-- AQUÍ el cambio
        "          '' AS marca, m.descripcion AS modelo, " +           // <-- y AQUÍ usamos descripcion
        "          '' AS talla, '' AS color, " +
        "          m.precio, m.descuento, " +
        "          (m.precio - (m.precio * (COALESCE(m.descuento,0)/100))) AS subtotal " +
        "   FROM manufacturas m " +
        "   WHERE m.numero_nota = ? AND COALESCE(m.status,'A') = 'A' " +
        ") x " +
        "ORDER BY ord, id";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, numeroNota);  // Nota_Detalle
        ps.setInt(2, numeroNota);  // Pedidos
        ps.setInt(3, numeroNota);  // Manufacturas

        try (ResultSet rs = ps.executeQuery()) {
            List<Modelo.NotaDetalle> out = new ArrayList<>();
            while (rs.next()) {
                Modelo.NotaDetalle d = new Modelo.NotaDetalle();
                d.setId(rs.getInt("id"));
                d.setCodigoArticulo(rs.getString("codigo_articulo"));  // será null para pedidos y manufacturas
                d.setArticulo(rs.getString("articulo"));
                d.setMarca(rs.getString("marca"));
                d.setModelo(rs.getString("modelo"));
                d.setTalla(rs.getString("talla"));
                d.setColor(rs.getString("color"));
                d.setPrecio(
                    rs.getBigDecimal("precio") == null
                        ? null
                        : rs.getBigDecimal("precio").doubleValue()
                );
                d.setDescuento(
                    rs.getBigDecimal("descuento") == null
                        ? null
                        : rs.getBigDecimal("descuento").doubleValue()
                );
                d.setSubtotal(
                    rs.getBigDecimal("subtotal") == null
                        ? null
                        : rs.getBigDecimal("subtotal").doubleValue()
                );
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
            public String folioRef;          // NUEVO: folio del crédito al que se relaciona (AB/DV)
            public LocalDate fecha;
            public Double total;
            public Double saldo;
            public String status;

            public String uuidFactura;      // null si no hay
            public String estatusFactura;   // TIMBRADA/BORRADOR/CANCELADA o null
        }


/** Notas de un cliente por teléfono (todas, orden más reciente primero). */
public List<NotaResumen> listarNotasPorTelefonoResumen(String telefono) throws SQLException {
    String sql =
        "SELECT numero_nota, tipo, folio, DATE(fecha_registro) AS fecha, " +
        "       total, saldo, nota_relacionada, status " +
        "FROM Notas WHERE telefono=? " +
        // ASC para poder recorrer cronológicamente
        "ORDER BY fecha_registro ASC, numero_nota ASC";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setString(1, telefono);

        try (ResultSet rs = ps.executeQuery()) {
            List<NotaResumen> out = new ArrayList<>();

            // Mapa: numero_nota (CR) -> lista de abonos (NotaResumen)
            Map<Integer, List<NotaResumen>> abonosPorCredito = new HashMap<>();

            while (rs.next()) {
                NotaResumen r = new NotaResumen();
                r.numero = rs.getInt("numero_nota");
                r.tipo   = rs.getString("tipo");
                try { r.folio = rs.getString("folio"); } catch (Throwable ignore) {}

                Date f = rs.getDate("fecha");
                r.fecha = (f == null ? null : f.toLocalDate());

                try {
                    r.total = (rs.getBigDecimal("total") == null
                               ? null
                               : rs.getBigDecimal("total").doubleValue());
                } catch (Throwable t) {
                    r.total = rs.getDouble("total");
                }

                try {
                    r.saldo = (rs.getBigDecimal("saldo") == null
                               ? null
                               : rs.getBigDecimal("saldo").doubleValue());
                } catch (Throwable t) {
                    r.saldo = rs.getDouble("saldo");
                }

                r.status = rs.getString("status");

                // Si es ABONO, guardamos la relación numérica contra su crédito
                String tipoUpper = (r.tipo == null ? "" : r.tipo.trim().toUpperCase());
                if ("AB".equals(tipoUpper)) {
                    int numRel = rs.getInt("nota_relacionada");
                    if (!rs.wasNull()) {
                        abonosPorCredito
                                .computeIfAbsent(numRel, k -> new ArrayList<>())
                                .add(r);
                    }
                }

                out.add(r);
            }

            // ===================== AJUSTE DE SALDOS SECUENCIALES POR CRÉDITO =====================
            if (!abonosPorCredito.isEmpty()) {
                // créditos por número interno
                Map<Integer, NotaResumen> creditosPorNumero = new HashMap<>();
                for (NotaResumen r : out) {
                    String t = (r.tipo == null ? "" : r.tipo.trim().toUpperCase());
                    if ("CR".equals(t) && r.numero > 0) {
                        creditosPorNumero.put(r.numero, r);
                    }
                }

                Comparator<NotaResumen> cmp =
                    Comparator.comparing(
                            (NotaResumen r) -> r.fecha,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ).thenComparingInt(r -> r.numero);

                FormasPagoDAO fdao = new FormasPagoDAO();

                for (Map.Entry<Integer, List<NotaResumen>> e : abonosPorCredito.entrySet()) {
                    Integer numCredito = e.getKey();
                    NotaResumen credito = creditosPorNumero.get(numCredito);
                    if (credito == null) continue;

                    // Saldo inicial REAL del crédito = total - pagos iniciales del CR
                    Double saldoInicial = calcularSaldoInicialCredito(credito, fdao);
                    if (saldoInicial == null) continue;

                    // Mostrar este saldo inicial en la fila del CR
                    credito.saldo = saldoInicial;

                    List<NotaResumen> abonos = e.getValue();
                    abonos.sort(cmp);

                    double saldoTmp = saldoInicial;
                    for (NotaResumen ab : abonos) {
                        double montoAbono = (ab.total == null ? 0.0 : ab.total);
                        saldoTmp -= montoAbono;
                        if (Math.abs(saldoTmp) < 0.01) saldoTmp = 0.0;  // tolerancia
                        // saldo DESPUÉS de este abono
                        ab.saldo = saldoTmp;
                    }
                }
            }

            // De vuelta a "más recientes primero"
            out.sort(
                Comparator.comparing(
                            (NotaResumen r) -> r.fecha,
                            Comparator.nullsLast(Comparator.naturalOrder())
                       )
                       .thenComparingInt(r -> r.numero)
                       .reversed()
            );

            return out;
        }
    }
}
/** Suma pagos iniciales del CR y calcula saldo inicial = total - pagosIniciales. */
private Double calcularSaldoInicialCredito(NotaResumen credito, FormasPagoDAO fdao) {
    if (credito == null) return null;

    // Si no hay total, intentamos usar saldo de BD como último recurso
    if (credito.total == null) {
        return credito.saldo;
    }

    double total = credito.total;
    double pagosIniciales = 0.0;

    try {
        FormasPagoDAO.FormasPagoRow fp = fdao.obtenerPorNota(credito.numero);
        if (fp != null) {
            pagosIniciales += nz(fp.efectivo);
            pagosIniciales += nz(fp.tarjetaCredito);
            pagosIniciales += nz(fp.tarjetaDebito);
            pagosIniciales += nz(fp.americanExpress);
            pagosIniciales += nz(fp.transferencia);
            pagosIniciales += nz(fp.deposito);
            // OJO: no restamos aquí DV, eso ya se maneja por otro flujo
        }
    } catch (Exception ignore) {
        // Si algo truena, regresamos lo que tengamos
        return (credito.saldo != null ? credito.saldo : credito.total);
    }

    double saldo = total - pagosIniciales;
    if (saldo < 0 && Math.abs(saldo) < 0.01) {
        saldo = 0.0; // ajustes de centavos
    }
    return saldo;
}

private static double nz(Double v) {
    return (v == null ? 0.0 : v);
}

            /** 
     * Crea o actualiza la nota de CR de "migración" para un cliente.
     * Usa folio 'MIG-<telefono>' para identificarla.
     * - Si saldo es null o <= 0: marca la nota existente como cancelada (status='C', saldo=0).
     * - Si saldo > 0: inserta o actualiza la nota con ese saldo.
     */
/** Quita todo lo que no sea dígito. */
private static String normalizarTelefono(String tel) {
    if (tel == null) return null;
    return tel.replaceAll("\\D+", "");  // fuera guiones, espacios, etc.
}

/**
 * Crea o actualiza la nota de CR de "migración" para un cliente.
 * Usa folio 'MIG-<telefono>' para identificarla.
 * - Si saldo es null o <= 0: marca la nota existente como cancelada (status='C', saldo=0).
 * - Si saldo > 0: inserta o actualiza la nota con ese saldo.
 */
// Genera un folio de migración que siempre cabe en varchar(12)
private String folioMigracion(String telefono) {
    if (telefono == null) return "MIG";
    // Solo dígitos
    String digits = telefono.replaceAll("\\D", "");
    if (digits.isEmpty()) return "MIG";

    // Usar solo los últimos 8 dígitos si se pasa
    if (digits.length() > 8) {
        digits = digits.substring(digits.length() - 8);
    }
    // "MIG-" (4) + 8 dígitos = 12 caracteres máximo
    return "MIG-" + digits;
}

public void registrarNotaMigracion(String telefono, java.time.LocalDate fechaSaldo, Double saldo)
        throws java.sql.SQLException {

    if (telefono == null || telefono.isBlank()) return;

    // Normalizar a solo dígitos para todo lo que vaya a Notas
    String telRaw = telefono.replaceAll("\\D", "");
    if (telRaw.isEmpty()) return;

    try (java.sql.Connection cn = Conexion.Conecta.getConnection()) {

        Integer numeroExistente = null;
        try (java.sql.PreparedStatement ps = cn.prepareStatement(
                "SELECT numero_nota " +
                "FROM Notas " +
                "WHERE telefono = ? AND tipo='CR' AND folio LIKE 'MIG-%' " +
                "ORDER BY numero_nota DESC LIMIT 1")) {
            ps.setString(1, telRaw);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    numeroExistente = rs.getInt("numero_nota");
                }
            }
        }

        // Si no hay saldo, cancelar la nota de migración (si existe) y salir
        if (saldo == null || saldo <= 0.0) {
            if (numeroExistente != null) {
                try (java.sql.PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Notas SET total=0, saldo=0, status='C' WHERE numero_nota=?")) {
                    ps.setInt(1, numeroExistente);
                    ps.executeUpdate();
                }
            }
            return;
        }

        java.time.LocalDateTime ldt =
                (fechaSaldo == null ? java.time.LocalDateTime.now() : fechaSaldo.atStartOfDay());
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(ldt);

        if (numeroExistente == null) {
            // INSERT
            String folio = folioMigracion(telRaw);
            try (java.sql.PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO Notas(fecha_registro, telefono, asesor, tipo, total, saldo, status, folio) " +
                    "VALUES (?, ?, NULL, 'CR', ?, ?, 'A', ?)")) {
                ps.setTimestamp(1, ts);
                ps.setString(2, telRaw);
                ps.setBigDecimal(3, new java.math.BigDecimal(saldo));
                ps.setBigDecimal(4, new java.math.BigDecimal(saldo));
                ps.setString(5, folio);
                ps.executeUpdate();
            }
        } else {
            // UPDATE (no tocamos folio)
            try (java.sql.PreparedStatement ps = cn.prepareStatement(
                    "UPDATE Notas " +
                    "SET fecha_registro=?, total=?, saldo=?, status='A' " +
                    "WHERE numero_nota=?")) {
                ps.setTimestamp(1, ts);
                ps.setBigDecimal(2, new java.math.BigDecimal(saldo));
                ps.setBigDecimal(3, new java.math.BigDecimal(saldo));
                ps.setInt(4, numeroExistente);
                ps.executeUpdate();
            }
        }
    }
}
// En NotasDAO

/** Devuelve la fecha_evento de la nota (la mínima de los renglones activos). */
public LocalDate obtenerFechaEventoDeNota(int numeroNota) throws SQLException {
    String sql =
        "SELECT MIN(fecha_evento) AS f " +
        "FROM Nota_Detalle " +
        "WHERE numero_nota = ? AND COALESCE(status,'A') = 'A'";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, numeroNota);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            Date f = rs.getDate("f");
            return (f == null ? null : f.toLocalDate());
        }
    }
}
// En NotasDAO

public String obtenerObsequiosDeNota(int numeroNota) throws SQLException {
    String sql =
        "SELECT obsequio1_cod, obsequio1, " +
        "       obsequio2_cod, obsequio2, " +
        "       obsequio3_cod, obsequio3, " +
        "       obsequio4_cod, obsequio4, " +
        "       obsequio5_cod, obsequio5 " +
        "FROM obsequios " +
        "WHERE numero_nota = ? AND status = 'A' " +
        "ORDER BY fecha_operacion DESC " +
        "LIMIT 1";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, numeroNota);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return "";

            StringBuilder sb = new StringBuilder();

            agregarLineaObsequio(sb, rs.getString("obsequio1_cod"), rs.getString("obsequio1"));
            agregarLineaObsequio(sb, rs.getString("obsequio2_cod"), rs.getString("obsequio2"));
            agregarLineaObsequio(sb, rs.getString("obsequio3_cod"), rs.getString("obsequio3"));
            agregarLineaObsequio(sb, rs.getString("obsequio4_cod"), rs.getString("obsequio4"));
            agregarLineaObsequio(sb, rs.getString("obsequio5_cod"), rs.getString("obsequio5"));

            return sb.toString().trim();
        }
    }
}

private void agregarLineaObsequio(StringBuilder sb, String cod, String desc) {
    boolean codOk  = cod  != null && !cod.isBlank();
    boolean descOk = desc != null && !desc.isBlank();

    if (!codOk && !descOk) return;  // nada que agregar

    if (sb.length() > 0) sb.append("\n"); // siguiente línea

    if (codOk) {
        sb.append(cod.trim());
        if (descOk) {
            sb.append(" - ").append(desc.trim());
        }
    } else {
        sb.append(desc.trim());
    }
}

// En NotasDAO

/** Regresa las observaciones de la nota (tabla notas_observaciones). */
public String obtenerObservacionesDeNota(int numeroNota) throws SQLException {
    String sql = "SELECT observaciones FROM notas_observaciones WHERE numero_nota = ?";

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, numeroNota);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return "";
            String obs = rs.getString("observaciones");
            return (obs == null ? "" : obs);
        }
    }
}
public String obtenerNombreAsesor(int idAsesor) throws SQLException {
    String sql = "SELECT nombre_completo " +      // AJUSTA ESTE NOMBRE
                 "FROM asesor " +               // AJUSTA EL NOMBRE DE LA TABLA
                 "WHERE numero_empleado = ?";           // AJUSTA EL NOMBRE DEL CAMPO

    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(sql)) {

        ps.setInt(1, idAsesor);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return "";
            return rs.getString(1);
        }
    }
}

    }
