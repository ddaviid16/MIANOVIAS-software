package Controlador;

import Conexion.Conecta;
import Modelo.Nota;
import Modelo.NotaDetalle;
import Modelo.PagoFormas;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class VentaCreditoService {

    private static final String SQL_INS_NOTA =
        "INSERT INTO Notas (folio, fecha_registro, telefono, asesor, tipo, total, saldo, status) " +
        "VALUES (?, NOW(), ?, ?, 'CR', ?, ?, 'A')";

    private static final String SQL_INS_DET =
        "INSERT INTO Nota_Detalle (numero_nota, codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, subtotal, fecha_evento, fecha_entrega) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_INS_FP =
    "INSERT INTO Formas_Pago (" +
    "numero_nota, fecha_operacion, tarjeta_credito, tarjeta_debito, american_express, " +
    "transferencia_bancaria, deposito_bancario, efectivo, devolucion, referencia_dv, tipo_operacion, status" +
    ") VALUES (?, CURDATE(), ?,?,?,?,?,?,?,?, 'CR', 'A')";

    public int crearVentaCredito(Nota nota, List<NotaDetalle> dets, PagoFormas pago, LocalDate fechaEventoVenta, LocalDate fechaEntregaDefault) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String folio = new FoliosDAO().siguiente(cn, "CR");
                nota.setFolio(folio);

                double total = nota.getTotal() == null ? 0.0 : nota.getTotal();
                double anticipo =
                        nz(pago.getTarjetaCredito()) + nz(pago.getTarjetaDebito()) + nz(pago.getAmericanExpress()) +
                        nz(pago.getTransferencia()) + nz(pago.getDeposito()) + nz(pago.getEfectivo());
                double saldo = Math.max(0.0, total - anticipo);

                try (PreparedStatement psn = cn.prepareStatement(SQL_INS_NOTA, Statement.RETURN_GENERATED_KEYS)) {
                    psn.setString(1, folio);
                    psn.setString(2, nota.getTelefono());
                    if (nota.getAsesor() == null) psn.setNull(3, Types.INTEGER); else psn.setInt(3, nota.getAsesor());
                    psn.setDouble(4, total);
                    psn.setDouble(5, saldo);
                    psn.executeUpdate();

                    try (ResultSet keys = psn.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se pudo obtener numero_nota");
                        nota.setNumeroNota(keys.getInt(1));
                    }
                }

                try (PreparedStatement psd = cn.prepareStatement(SQL_INS_DET)) {
                    InventarioDAO invDao = new InventarioDAO();
                    for (NotaDetalle d : dets) {
                        psd.setInt(1, nota.getNumeroNota());
                        if (d.getCodigoArticulo()==null) psd.setNull(2, Types.INTEGER); else psd.setInt(2, d.getCodigoArticulo());
                        psd.setString(3, nz(d.getArticulo()));
                        psd.setString(4, nz(d.getMarca()));
                        psd.setString(5, nz(d.getModelo()));
                        psd.setString(6, nz(d.getTalla()));
                        psd.setString(7, nz(d.getColor()));
                        if (d.getPrecio()==null)    psd.setNull(8,  Types.DECIMAL); else psd.setDouble(8,  d.getPrecio());
                        if (d.getDescuento()==null) psd.setNull(9,  Types.DECIMAL); else psd.setDouble(9,  d.getDescuento());
                        if (d.getSubtotal()==null)  psd.setNull(10, Types.DECIMAL); else psd.setDouble(10, d.getSubtotal());

                        // NUEVO: tomar la fecha por renglón; si viene null, usa la de la venta
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
                psp.setInt(1, nota.getNumeroNota());
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
                return nota.getNumeroNota();
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
    private static String nz(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static double nz(Double d) { return d == null ? 0.0 : d; }
}
