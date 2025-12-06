// Controlador/DevolucionService.java
package Controlador;

import Conexion.Conecta;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DevolucionService {

    public static class ResultadoDev {
        public final int numeroNotaDV;
        public final String folio;
        public final Double nuevoSaldo;     // saldo de la nota origen tras la devolución (si aplica)
        public final int piezasDevueltas;
        public final double totalDV;        // subtotal de artículos movidos a DV
        public final double saldoAFavor;    // lo que realmente queda como saldo a favor

        public ResultadoDev(int numeroNotaDV, String folio, Double nuevoSaldo, int piezasDevueltas,
                            double totalDV, double saldoAFavor) {
            this.numeroNotaDV = numeroNotaDV;
            this.folio = folio;
            this.nuevoSaldo = nuevoSaldo;
            this.piezasDevueltas = piezasDevueltas;
            this.totalDV = totalDV;
            this.saldoAFavor = saldoAFavor;
        }
    }

    /**
     * Nueva versión:
     *  - idsDetalle = IDs de Nota_Detalle (solo artículos con código).
     *  - montoExtra = suma de subtotales de renglones sin código (MODISTA / PEDIDO).
     */
    public ResultadoDev registrarDevolucionMoviendoDetalles(int numeroNotaOrigen,
                                                            List<Integer> idsDetalle,
                                                            double montoExtra,
                                                            String motivo) throws SQLException {

        if ((idsDetalle == null || idsDetalle.isEmpty()) && montoExtra <= 0.0) {
            throw new SQLException("No se recibieron renglones a devolver.");
        }

        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                // 1) Bloquea la nota origen y toma datos
                String tel; Integer asesor; String tipo;
                double totalOrigen; double saldoOrigen;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT telefono, asesor, tipo, total, saldo " +
                        "FROM Notas WHERE numero_nota=? FOR UPDATE")) {
                    ps.setInt(1, numeroNotaOrigen);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next())
                            throw new SQLException("Nota origen no existe: " + numeroNotaOrigen);
                        tel   = rs.getString(1);
                        int a = rs.getInt(2); asesor = rs.wasNull() ? null : a;
                        tipo  = rs.getString(3);
                        totalOrigen = rs.getDouble(4);
                        saldoOrigen = rs.getDouble(5);
                    }
                }

                // 2) Valida renglones de Nota_Detalle y acumula total de devolución
                double montoDV = montoExtra;   // ya trae lo de MODISTA/PEDIDOS
                java.util.List<String> codigos = new java.util.ArrayList<>();

                if (idsDetalle != null && !idsDetalle.isEmpty()) {
                    try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT id, numero_nota, codigo_articulo, subtotal, status " +
                        "FROM Nota_Detalle WHERE id=? FOR UPDATE")) {

                        for (Integer id : idsDetalle) {
                            if (id == null) continue;
                            ps.setInt(1, id);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (!rs.next())
                                    throw new SQLException("Renglón ya no existe o no pertenece a la nota (id=" + id + ")");
                                int nota = rs.getInt("numero_nota");
                                if (nota != numeroNotaOrigen)
                                    throw new SQLException("Renglón no pertenece a la nota origen (id=" + id + ")");
                                String st = rs.getString("status");
                                if (st == null || !"A".equalsIgnoreCase(st))
                                    throw new SQLException("Renglón inactivo (id=" + id + ")");
                                String codArt = rs.getString("codigo_articulo");
                                if (codArt != null && !codArt.isBlank()) {
                                    codigos.add(codArt);
                                }
                                montoDV += rs.getDouble("subtotal");
                            }
                        }
                    }
                }

                montoDV = Math.round(montoDV * 100.0) / 100.0;

                // 2.1) Calcula saldo a favor REAL y nuevo saldo de la nota origen
                double saldoAFavor;
                double nuevoSaldo;

                if ("CR".equalsIgnoreCase(tipo)) {
                    // Crédito:
                    // Si lo devuelto es mayor a la deuda, el sobrante es saldo a favor.
                    saldoAFavor = Math.max(0.0, montoDV - saldoOrigen);
                    nuevoSaldo  = Math.max(0.0, saldoOrigen - montoDV);
                } else {
                    // Contado:
                    // Todo lo devuelto es saldo a favor.
                    saldoAFavor = montoDV;
                    nuevoSaldo  = 0.0;
                }

                // 3) Genera folio y crea header DV
                FoliosDAO fdao = new FoliosDAO();
                String folioDV = fdao.siguiente(cn, "DV");

                int numeroNotaDV;
                try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO Notas (fecha_registro, telefono, asesor, tipo, total, saldo, status, folio) " +
                    "VALUES (NOW(), ?, ?, 'DV', ?, ?, 'A', ?)", Statement.RETURN_GENERATED_KEYS)) {

                    if (tel == null || tel.isBlank()) ps.setNull(1, Types.VARCHAR); else ps.setString(1, tel);
                    if (asesor == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, asesor);
                    ps.setDouble(3, montoDV);
                    ps.setDouble(4, saldoAFavor);
                    ps.setString(5, folioDV);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) {
                        if (!k.next()) throw new SQLException("No se generó número de nota DV");
                        numeroNotaDV = k.getInt(1);
                    }
                }

                // 4) Copia renglones de Nota_Detalle a la DV y elimina del origen
                int piezasMovidas = 0;
                if (idsDetalle != null && !idsDetalle.isEmpty()) {
                    try (PreparedStatement ins = cn.prepareStatement(
                            "INSERT INTO Nota_Detalle (numero_nota, articulo, marca, modelo, talla, color, " +
                            " precio, descuento, subtotal, codigo_articulo, status) " +
                            "SELECT ?, articulo, marca, modelo, talla, color, precio, descuento, subtotal, codigo_articulo, 'A' " +
                            "FROM Nota_Detalle WHERE id=?");
                         PreparedStatement del = cn.prepareStatement(
                            "DELETE FROM Nota_Detalle WHERE id=?")) {

                        for (Integer id : idsDetalle) {
                            if (id == null) continue;
                            ins.setInt(1, numeroNotaDV);
                            ins.setInt(2, id);
                            piezasMovidas += ins.executeUpdate();

                            del.setInt(1, id);
                            del.executeUpdate();
                        }
                    }
                }

                // 5) Regresa existencia al inventario (solo artículos con código)
                if (!codigos.isEmpty()) {
                    try (PreparedStatement up = cn.prepareStatement(
                            "UPDATE Inventarios " +
                            "SET existencia = COALESCE(existencia,0) + 1 " +
                            "WHERE codigo_articulo=?")) {
                        for (String cod : codigos) {
                            up.setString(1, cod);
                            up.executeUpdate();   // si no existe, simplemente actualiza 0 filas
                        }
                    }
                }

                // 6) Ajusta total/saldo de la nota origen usando el nuevoSaldo calculado
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Notas " +
                        "SET total = GREATEST(0, total - ?), " +
                        "    saldo = ? " +
                        "WHERE numero_nota=?")) {
                    ps.setDouble(1, montoDV);
                    ps.setDouble(2, nuevoSaldo);
                    ps.setInt   (3, numeroNotaOrigen);
                    ps.executeUpdate();
                }

                // 7) Guarda registro de devolución
                try (PreparedStatement ps = cn.prepareStatement(
                        "INSERT INTO Devoluciones (numero_nota_dv, nota_origen, motivo) VALUES (?,?,?)")) {
                    ps.setInt(1, numeroNotaDV);
                    ps.setInt(2, numeroNotaOrigen);
                    ps.setString(3, (motivo == null || motivo.isBlank()) ? null : motivo);
                    ps.executeUpdate();
                }

                cn.commit();
                return new ResultadoDev(
                        numeroNotaDV, folioDV, nuevoSaldo,
                        piezasMovidas,        // piezas devueltas (solo artículos con código)
                        montoDV,              // totalDV
                        saldoAFavor
                );

            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }
}
