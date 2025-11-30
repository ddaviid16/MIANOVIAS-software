package Controlador;

import Conexion.Conecta;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Modelo.PagoFormas;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class VentaContadoService {

    private static final String SQL_INS_NOTA =
        "INSERT INTO Notas (fecha_registro, telefono, asesor, tipo, total, saldo, status, folio) " +
        "VALUES (?, ?, ?, 'CN', ?, 0, 'A', ?)";

    private static final String SQL_INS_DET =
        "INSERT INTO Nota_Detalle (numero_nota, codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, subtotal, fecha_evento, fecha_entrega) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_INS_FP =
        "INSERT INTO Formas_Pago (" +
        "numero_nota, fecha_operacion, tarjeta_credito, tarjeta_debito, american_express, " +
        "transferencia_bancaria, deposito_bancario, efectivo, devolucion, referencia_dv, tipo_operacion, status" +
        ") VALUES (?, ?, ?,?,?,?,?,?,?,?, 'CN', 'A')";


    public int crearVentaContado(Nota nota,
                                 List<NotaDetalle> dets,
                                 PagoFormas pago,
                                 LocalDate fechaVenta,
                                 LocalDate fechaEventoVenta,
                                 LocalDate fechaEntregaDefault) throws SQLException {

        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                // Fecha de venta efectiva: la elegida o hoy
                LocalDate fechaVentaEfectiva =
                        (fechaVenta != null ? fechaVenta : LocalDate.now());

                // ===== 1) Insertar cabecera de nota =====
                FoliosDAO foliosDAO = new FoliosDAO();
                String folio = foliosDAO.siguiente(cn, "CN");

                int numeroNota;
                try (PreparedStatement ps = cn.prepareStatement(SQL_INS_NOTA, Statement.RETURN_GENERATED_KEYS)) {

                    // 1) fecha_registro
                    ps.setDate(1, java.sql.Date.valueOf(fechaVentaEfectiva));

                    // 2) telefono
                    ps.setString(2, nota.getTelefono());

                    // 3) asesor
                    if (nota.getAsesor() == null)
                        ps.setNull(3, Types.INTEGER);
                    else
                        ps.setInt(3, nota.getAsesor());

                    // 4) total
                    double total = (nota.getTotal() == null ? 0.0 : nota.getTotal());
                    ps.setDouble(4, total);

                    // 5) folio
                    ps.setString(5, folio);

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se pudo obtener numero_nota");
                        numeroNota = keys.getInt(1);
                    }
                }

                nota.setNumeroNota(numeroNota);
                nota.setFolio(folio);
                nota.setFechaRegistro(fechaVentaEfectiva.atStartOfDay());

                // ===== 2) Detalle + validación de inventario =====
                try (PreparedStatement psd = cn.prepareStatement(SQL_INS_DET)) {
                    InventarioDAO invDao = new InventarioDAO();

                    for (NotaDetalle d : dets) {

                        String codArt = d.getCodigoArticulo();
                        if (codArt != null) codArt = codArt.trim();
                        if (codArt == null) codArt = "";

                        boolean esLineaSinCodigo = codArt.isEmpty();

                        if (!esLineaSinCodigo) {
                            try (PreparedStatement chk = cn.prepareStatement(
                                     "SELECT TRIM(UPPER(status)) st, COALESCE(existencia,0) ex " +
                                     "FROM Inventarios WHERE codigo_articulo=? FOR UPDATE")) {

                                chk.setString(1, codArt);
                                try (ResultSet rs = chk.executeQuery()) {
                                    if (!rs.next())
                                        throw new SQLException("Artículo no encontrado: " + codArt);

                                    String st = rs.getString("st");
                                    int ex    = rs.getInt("ex");
                                    if (!"A".equals(st)) {
                                        throw new SQLException("Artículo inactivo (status=" + st + ")");
                                    }
                                    if (ex < 1) {
                                        throw new SQLException("Sin existencia para código " + codArt);
                                    }
                                }
                            }
                        }

                        psd.setInt(1, numeroNota);

                        if (esLineaSinCodigo) {
                            psd.setNull(2, Types.VARCHAR);
                        } else {
                            psd.setString(2, codArt);
                        }

                        psd.setString(3, nz(d.getArticulo()));
                        psd.setString(4, nz(d.getMarca()));
                        psd.setString(5, nz(d.getModelo()));
                        psd.setString(6, nz(d.getTalla()));
                        psd.setString(7, nz(d.getColor()));

                        if (d.getPrecio() == null)    psd.setNull(8,  Types.DECIMAL);
                        else                           psd.setDouble(8,  d.getPrecio());

                        if (d.getDescuento() == null) psd.setNull(9,  Types.DECIMAL);
                        else                           psd.setDouble(9,  d.getDescuento());

                        if (d.getSubtotal() == null)  psd.setNull(10, Types.DECIMAL);
                        else                           psd.setDouble(10, d.getSubtotal());

                        LocalDate f = (d.getFechaEvento() != null) ? d.getFechaEvento() : fechaEventoVenta;
                        if (f == null) psd.setNull(11, Types.DATE);
                        else           psd.setDate(11, Date.valueOf(f));

                        if (fechaEntregaDefault == null) psd.setNull(12, Types.DATE);
                        else                             psd.setDate(12, Date.valueOf(fechaEntregaDefault));

                        psd.addBatch();

                        if (!esLineaSinCodigo) {
                            invDao.descontarExistencia(cn, codArt, 1);
                        }
                    }
                    psd.executeBatch();
                }

                // ===== 3) FORMAS DE PAGO =====
                try (PreparedStatement psp = cn.prepareStatement(SQL_INS_FP)) {
                    psp.setInt(1, numeroNota);
                    psp.setDate(2, java.sql.Date.valueOf(fechaVentaEfectiva));

                    setNullable(psp, 3, pago.getTarjetaCredito());
                    setNullable(psp, 4, pago.getTarjetaDebito());
                    setNullable(psp, 5, pago.getAmericanExpress());
                    setNullable(psp, 6, pago.getTransferencia());
                    setNullable(psp, 7, pago.getDeposito());
                    setNullable(psp, 8, pago.getEfectivo());
                    setNullable(psp, 9, pago.getDevolucion());
                    psp.setString(10, pago.getReferenciaDV());

                    psp.executeUpdate();
                }

                cn.commit();
                return numeroNota;

            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, Double val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.DECIMAL);
        else             ps.setDouble(idx, val);
    }

    private static String nz(String s){
        return (s == null || s.isBlank()) ? null : s;
    }
}
