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
        "VALUES (?, ?, ?, ?, 'CR', ?, ?, 'A')";

    private static final String SQL_INS_DET =
        "INSERT INTO Nota_Detalle (numero_nota, codigo_articulo, articulo, marca, modelo, talla, color, precio, descuento, subtotal, fecha_evento, fecha_entrega) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_INS_FP =
        "INSERT INTO Formas_Pago (" +
        "numero_nota, fecha_operacion, tarjeta_credito, tarjeta_debito, american_express, " +
        "transferencia_bancaria, deposito_bancario, efectivo, devolucion, referencia_dv, tipo_operacion, status" +
        ") VALUES (?, ?, ?,?,?,?,?,?,?,?, 'CR', 'A')";


    public int crearVentaCredito(Nota nota,
                             List<NotaDetalle> dets,
                             PagoFormas pago,
                             LocalDate fechaVenta,          // ← NUEVO
                             LocalDate fechaEventoVenta,
                             LocalDate fechaEntregaDefault) throws SQLException {


        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                LocalDate fechaVentaEfectiva = (fechaVenta != null ? fechaVenta : LocalDate.now());

                // ===== FOLIO =====
                String folio = new FoliosDAO().siguiente(cn, "CR");
                nota.setFolio(folio);

                // ===== SALDO (INCLUYENDO DEVOLUCIÓN) =====
                double total = nota.getTotal() == null ? 0.0 : nota.getTotal();
                double anticipo =
                        nz(pago.getTarjetaCredito()) +
                        nz(pago.getTarjetaDebito()) +
                        nz(pago.getAmericanExpress()) +
                        nz(pago.getTransferencia()) +
                        nz(pago.getDeposito()) +
                        nz(pago.getEfectivo()) +
                        nz(pago.getDevolucion());      // incluye DV

                double saldo = Math.max(0.0, total - anticipo);
                nota.setSaldo(saldo);

                // ===== INSERT EN NOTAS =====
                try (PreparedStatement psn = cn.prepareStatement(SQL_INS_NOTA, Statement.RETURN_GENERATED_KEYS)) {
                    psn.setString(1, folio);

                    // fecha_registro = fechaVentaEfectiva (sin hora, te basta la fecha)
                    psn.setDate(2, java.sql.Date.valueOf(fechaVentaEfectiva));

                    psn.setString(3, nota.getTelefono());

                    if (nota.getAsesor() == null)
                        psn.setNull(4, Types.INTEGER);
                    else
                        psn.setInt(4, nota.getAsesor());

                    psn.setDouble(5, total);
                    psn.setDouble(6, saldo);

                    psn.executeUpdate();

                    try (ResultSet keys = psn.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se pudo obtener numero_nota");
                        nota.setNumeroNota(keys.getInt(1));
                    }
                }


                // ===== DETALLE DE LA NOTA =====
                try (PreparedStatement psd = cn.prepareStatement(SQL_INS_DET)) {
                    InventarioDAO invDao = new InventarioDAO();

                    for (NotaDetalle d : dets) {

                        String cod = d.getCodigoArticulo();
                        boolean tieneInventario = esCodigoInventarioValido(cod);  // <<< clave

                        psd.setInt(1, nota.getNumeroNota());

                        if (!tieneInventario) {
                            // Renglones especiales (PEDIDO, manuales, etc.): guardar SIN código
                            psd.setNull(2, Types.INTEGER);
                        } else {
                            // Validar que el artículo exista, esté activo y con stock
                            try (PreparedStatement chk = cn.prepareStatement(
                                     "SELECT TRIM(UPPER(status)) st, COALESCE(existencia,0) ex " +
                                     "FROM Inventarios WHERE codigo_articulo=? FOR UPDATE")) {
                                chk.setString(1, cod);
                                try (ResultSet rs = chk.executeQuery()) {
                                    if (!rs.next())
                                        throw new SQLException("Artículo no encontrado: " + cod);
                                    String st = rs.getString("st");
                                    int ex    = rs.getInt("ex");
                                    if (!"A".equals(st))
                                        throw new SQLException("Artículo inactivo (status=" + st + ")");
                                    if (ex < 1)
                                        throw new SQLException("Sin existencia para código " + cod);
                                }
                            }
                            psd.setString(2, cod);
                        }

                        psd.setString(3,  nz(d.getArticulo()));
                        psd.setString(4,  nz(d.getMarca()));
                        psd.setString(5,  nz(d.getModelo()));
                        psd.setString(6,  nz(d.getTalla()));
                        psd.setString(7,  nz(d.getColor()));

                        if (d.getPrecio()    == null) psd.setNull(8,  Types.DECIMAL); else psd.setDouble(8,  d.getPrecio());
                        if (d.getDescuento() == null) psd.setNull(9,  Types.DECIMAL); else psd.setDouble(9,  d.getDescuento());
                        if (d.getSubtotal()  == null) psd.setNull(10, Types.DECIMAL); else psd.setDouble(10, d.getSubtotal());

                        LocalDate f = (d.getFechaEvento() != null) ? d.getFechaEvento() : fechaEventoVenta;
                        if (f == null) psd.setNull(11, Types.DATE); else psd.setDate(11, Date.valueOf(f));

                        if (fechaEntregaDefault == null)
                            psd.setNull(12, Types.DATE);
                        else
                            psd.setDate(12, Date.valueOf(fechaEntregaDefault));

                        psd.addBatch();

                        // Descontar del inventario SOLO si el código es válido
                        if (tieneInventario) {
                            invDao.descontarExistencia(cn, cod, 1);
                        }
                    }
                    psd.executeBatch();
                }

                // ===== FORMAS DE PAGO =====
                try (PreparedStatement psp = cn.prepareStatement(SQL_INS_FP)) {
                    psp.setInt(1, nota.getNumeroNota());

                    // fecha_operacion = misma fecha de venta
                    psp.setDate(2, java.sql.Date.valueOf(fechaVentaEfectiva));

                    // OJO: Todos los índices se recorren +1
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
                return nota.getNumeroNota();

            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }

    // ======================= HELPERS =======================

    private static boolean esCodigoInventarioValido(String cod) {
        return cod != null && !cod.isBlank() && !"0".equals(cod.trim());
    }

    private static void setNullable(PreparedStatement ps, int idx, Double val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.DECIMAL); else ps.setDouble(idx, val);
    }
    private static String nz(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static double nz(Double d) { return d == null ? 0.0 : d; }
}
