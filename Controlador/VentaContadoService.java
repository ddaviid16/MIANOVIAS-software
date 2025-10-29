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
        "VALUES (NOW(), ?, ?, 'CN', ?, 0, 'A', ?)";

    private static final String SQL_INS_DET =
        "INSERT INTO Nota_Detalle (numero_nota, codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, subtotal, fecha_evento, fecha_entrega) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_INS_FP =
    "INSERT INTO Formas_Pago (" +
    "numero_nota, fecha_operacion, tarjeta_credito, tarjeta_debito, american_express, " +
    "transferencia_bancaria, deposito_bancario, efectivo, devolucion, referencia_dv, tipo_operacion, status" +
    ") VALUES (?, CURDATE(), ?,?,?,?,?,?,?,?, 'CN', 'A')";


    public int crearVentaContado(Nota nota, List<NotaDetalle> dets, PagoFormas pago, LocalDate fechaEventoVenta, LocalDate fechaEntregaDefault) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                FoliosDAO foliosDAO = new FoliosDAO();
                String folio = foliosDAO.siguiente(cn, "CN");

                int numeroNota;
                try (PreparedStatement ps = cn.prepareStatement(SQL_INS_NOTA, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nota.getTelefono());
                    if (nota.getAsesor() == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, nota.getAsesor());
                    ps.setDouble(3, nota.getTotal() == null ? 0.0 : nota.getTotal());
                    ps.setString(4, folio);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se pudo obtener numero_nota");
                        numeroNota = keys.getInt(1);
                    }
                }
                nota.setFolio(folio);

                try (PreparedStatement psd = cn.prepareStatement(SQL_INS_DET)) {
                    InventarioDAO invDao = new InventarioDAO();
                    for (NotaDetalle d : dets) {

                        try (PreparedStatement chk = cn.prepareStatement(
                                 "SELECT TRIM(UPPER(status)) st, COALESCE(existencia,0) ex " +
                                 "FROM Inventarios WHERE codigo_articulo=? FOR UPDATE")) {
                            chk.setInt(1, d.getCodigoArticulo());
                            try (ResultSet rs = chk.executeQuery()) {
                                if (!rs.next())
                                    throw new SQLException("Artículo no encontrado: " + d.getCodigoArticulo());
                                String st = rs.getString("st");
                                int ex    = rs.getInt("ex");
                                if (!"A".equals(st)) throw new SQLException("Artículo inactivo (status=" + st + ")");
                                if (ex < 1) throw new SQLException("Sin existencia para código " + d.getCodigoArticulo());
                            }
                        }

                        psd.setInt(1, numeroNota);
                        if (d.getCodigoArticulo() == null) psd.setNull(2, Types.INTEGER);
                        else psd.setInt(2, d.getCodigoArticulo());
                        psd.setString(3, nz(d.getArticulo()));
                        psd.setString(4, nz(d.getMarca()));
                        psd.setString(5, nz(d.getModelo()));
                        psd.setString(6, nz(d.getTalla()));
                        psd.setString(7, nz(d.getColor()));
                        if (d.getPrecio() == null)     psd.setNull(8,  Types.DECIMAL); else psd.setDouble(8,  d.getPrecio());
                        if (d.getDescuento() == null)  psd.setNull(9,  Types.DECIMAL); else psd.setDouble(9,  d.getDescuento());
                        if (d.getSubtotal() == null)   psd.setNull(10, Types.DECIMAL); else psd.setDouble(10, d.getSubtotal());

                        // NUEVO: fecha_evento por renglón, con fallback al parámetro (para compatibilidad)
                        LocalDate f = (d.getFechaEvento() != null) ? d.getFechaEvento() : fechaEventoVenta;
                        if (f == null) psd.setNull(11, Types.DATE); else psd.setDate(11, Date.valueOf(f));
                        // NUEVO: fecha_entrega de la venta (misma para todos los renglones)
                        if (fechaEntregaDefault == null) psd.setNull(12, Types.DATE);
                        else psd.setDate(12, Date.valueOf(fechaEntregaDefault));

                        psd.addBatch();

                        invDao.descontarExistencia(cn, d.getCodigoArticulo(), 1);
                    }
                    psd.executeBatch();
                }

                try (PreparedStatement psp = cn.prepareStatement(SQL_INS_FP)) {
                psp.setInt(1, numeroNota);
                setNullable(psp, 2, pago.getTarjetaCredito());
                setNullable(psp, 3, pago.getTarjetaDebito());
                setNullable(psp, 4, pago.getAmericanExpress());
                setNullable(psp, 5, pago.getTransferencia());
                setNullable(psp, 6, pago.getDeposito());
                setNullable(psp, 7, pago.getEfectivo());
                setNullable(psp, 8, pago.getDevolucion());
                psp.setString(9, pago.getReferenciaDV());
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
        if (val == null) ps.setNull(idx, Types.DECIMAL); else ps.setDouble(idx, val);
    }
    private static String nz(String s){ return (s==null || s.isBlank()) ? null : s; }
}
