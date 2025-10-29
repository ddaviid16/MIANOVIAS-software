package Controlador;

import Modelo.PagoFormas;

import java.sql.*;
import java.time.LocalDate;

public class AbonoService {

    // Bloquea la nota de crédito hasta terminar la transacción
    private static final String SQL_SEL_NOTA =
        "SELECT telefono, asesor, tipo, status, saldo " +
        "FROM Notas WHERE numero_nota=? FOR UPDATE";

    // Nota de ABONO (si añadiste 'nota_relacionada', usa la variante comentada más abajo)
    private static final String SQL_INS_NOTA_AB =
        "INSERT INTO Notas (fecha_registro, telefono, asesor, tipo, total, saldo, status, folio, nota_relacionada) " +
        "VALUES (NOW(), ?, ?, 'AB', ?, 0, 'A', ?, ?)";


    // Formas de pago del ABONO: usaremos la fecha que trae el modelo
    private static final String SQL_INS_FP_AB =
    "INSERT INTO Formas_Pago (" +
    "numero_nota, fecha_operacion, tarjeta_credito, tarjeta_debito, american_express, " +
    "transferencia_bancaria, deposito_bancario, efectivo, devolucion, referencia_dv, tipo_operacion, status" +
    ") VALUES (?, ?, ?,?,?,?,?,?,?,?, 'AB', 'A')";

    private static final String SQL_UPD_SALDO =
        "UPDATE Notas SET saldo=? WHERE numero_nota=?";

    public static class ResultadoAbono {
        public final int numeroNotaAbono;
        public final String folio;      // A000x
        public final double saldoNuevo; // saldo que queda en la CR
        public ResultadoAbono(int numeroNotaAbono, String folio, double saldoNuevo) {
            this.numeroNotaAbono = numeroNotaAbono;
            this.folio = folio;
            this.saldoNuevo = saldoNuevo;
        }
    }

    public ResultadoAbono registrarAbono(Connection cn, int numeroNotaCredito, PagoFormas p) throws SQLException {
    // NO cerrar ni hacer commit: eso lo hace quien lo llame
    String telefono; Integer asesor; String tipo; String status; double saldo;

    try (PreparedStatement ps = cn.prepareStatement(SQL_SEL_NOTA)) {
        ps.setInt(1, numeroNotaCredito);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("No existe la nota " + numeroNotaCredito);
            telefono = rs.getString("telefono");
            int a = rs.getInt("asesor"); asesor = rs.wasNull() ? null : a;
            tipo = rs.getString("tipo");
            status = rs.getString("status");
            saldo = rs.getDouble("saldo");
        }
    }

    if (!"CR".equalsIgnoreCase(tipo))
        throw new SQLException("La nota " + numeroNotaCredito + " no es de crédito.");
    if (!"A".equalsIgnoreCase(status))
        throw new SQLException("La nota " + numeroNotaCredito + " no admite abonos (status=" + status + ").");

    double montoAbono = suma(p);
    if (montoAbono <= 0.0) throw new SQLException("El abono debe ser mayor a cero.");
    if (montoAbono > saldo + 0.005)
        throw new SQLException("El abono (" + montoAbono + ") no puede ser mayor al saldo (" + saldo + ").");

    double nuevoSaldo = redondear2(saldo - montoAbono);
    if (Math.abs(nuevoSaldo) < 0.004) nuevoSaldo = 0.0;

    FoliosDAO folios = new FoliosDAO();
    String folioAbono = folios.siguiente(cn, "AB");

    int numeroNotaAbono;
    try (PreparedStatement ps = cn.prepareStatement(SQL_INS_NOTA_AB, Statement.RETURN_GENERATED_KEYS)) {
    ps.setString(1, telefono);
    if (asesor == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, asesor);
    ps.setDouble(3, montoAbono);
    ps.setString(4, folioAbono);
    ps.setInt(5, numeroNotaCredito);  // ← Guarda la relación con la venta original
    ps.executeUpdate();

    try (ResultSet keys = ps.getGeneratedKeys()) {
        if (!keys.next()) throw new SQLException("No se generó número de nota AB");
        numeroNotaAbono = keys.getInt(1);
    }
}


    LocalDate f = (p.getFechaOperacion() == null) ? LocalDate.now() : p.getFechaOperacion();
    try (PreparedStatement psp = cn.prepareStatement(SQL_INS_FP_AB)) {
        psp.setInt(1, numeroNotaAbono);
        psp.setDate(2, Date.valueOf(f));
        setNullable(psp, 3, p.getTarjetaCredito());
        setNullable(psp, 4, p.getTarjetaDebito());
        setNullable(psp, 5, p.getAmericanExpress());
        setNullable(psp, 6, p.getTransferencia());
        setNullable(psp, 7, p.getDeposito());
        setNullable(psp, 8, p.getEfectivo());
        setNullable(psp, 9, p.getDevolucion());
        psp.setString(10, p.getReferenciaDV());
        psp.executeUpdate();
    }

    try (PreparedStatement ps = cn.prepareStatement(SQL_UPD_SALDO)) {
        ps.setDouble(1, nuevoSaldo);
        ps.setInt(2, numeroNotaCredito);
        ps.executeUpdate();
    }

    if (p.getDevolucion() != null && p.getDevolucion() > 0
            && p.getReferenciaDV() != null && !p.getReferenciaDV().isBlank()) {
        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE Devoluciones d " +
                        "JOIN Notas n ON n.numero_nota = d.numero_nota_dv " +
                        "SET d.monto_usado = COALESCE(d.monto_usado,0) + ? " +
                        "WHERE n.folio = ?")) {
            ps.setDouble(1, p.getDevolucion());
            ps.setString(2, p.getReferenciaDV());
            ps.executeUpdate();
        }
    }

    return new ResultadoAbono(numeroNotaAbono, folioAbono, nuevoSaldo);
}

    // ===== Helpers =====
    private static void setNullable(PreparedStatement ps, int idx, Double val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.DECIMAL); else ps.setDouble(idx, val);
    }
    private static double suma(PagoFormas p) {
    double s = 0;
    if (p.getTarjetaCredito() != null)  s += p.getTarjetaCredito();
    if (p.getTarjetaDebito() != null)   s += p.getTarjetaDebito();
    if (p.getAmericanExpress() != null) s += p.getAmericanExpress();
    if (p.getTransferencia() != null)   s += p.getTransferencia();
    if (p.getDeposito() != null)        s += p.getDeposito();
    if (p.getEfectivo() != null)        s += p.getEfectivo();
    if (p.getDevolucion() != null)      s += p.getDevolucion(); // <--- faltaba esto
    return s;
}

    private static double redondear2(double v) { return Math.round(v * 100.0) / 100.0; }
}
