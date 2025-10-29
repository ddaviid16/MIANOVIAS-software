package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class CambioFechaEventoService {

    /** Estructura para cambios específicos por nota (se usa desde tu panel). */
    public static class CambioNota {
    public int numeroNota;
    public LocalDate nuevaFechaEvento;   // puede ser null
    public LocalDate nuevaFechaEntrega;  // puede ser null
}

    /** Cambia la fecha del cliente y:
     *  (1) Actualiza automáticamente todas las notas del cliente cuya fecha en detalle era IGUAL a la fecha anterior del cliente.
     *  (2) Opcionalmente, actualiza las notas seleccionadas en 'cambios' con la fecha indicada para cada una. */
    public void cambiarClienteYNotas(String telefono,
                                 LocalDate nuevaFechaEventoCliente,
                                 LocalDate nuevaFechaEntregaCliente,
                                 List<CambioNota> cambios) throws SQLException {

    try (Connection cn = Conecta.getConnection()) {
        cn.setAutoCommit(false);
        try {
            // 1) Leer anteriores (y bloquear fila del cliente)
            LocalDate fechaEventoAnterior = null, fechaEntregaAnterior = null;
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT fecha_evento, fecha_entrega FROM Clientes WHERE telefono1=? FOR UPDATE")) {
                ps.setString(1, telefono);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Cliente no encontrado para teléfono: " + telefono);
                    Date fe = rs.getDate(1), fd = rs.getDate(2);
                    if (fe != null) fechaEventoAnterior  = fe.toLocalDate();
                    if (fd != null) fechaEntregaAnterior = fd.toLocalDate();
                }
            }

            // 2) Actualizar CLIENTE (solo columnas que hayas enviado)
            if (nuevaFechaEventoCliente != null || nuevaFechaEntregaCliente != null) {
                StringBuilder sb = new StringBuilder("UPDATE Clientes SET ");
                java.util.List<Object> params = new java.util.ArrayList<>();
                if (nuevaFechaEventoCliente != null) {
                    sb.append("fecha_evento=?");
                    params.add(Date.valueOf(nuevaFechaEventoCliente));
                }
                if (nuevaFechaEntregaCliente != null) {
                    if (!params.isEmpty()) sb.append(", ");
                    sb.append("fecha_entrega=?");
                    params.add(Date.valueOf(nuevaFechaEntregaCliente));
                }
                sb.append(" WHERE telefono1=?");
                try (PreparedStatement ps = cn.prepareStatement(sb.toString())) {
                    int i=1;
                    for (Object o : params) ps.setDate(i++, (Date)o);
                    ps.setString(i, telefono);
                    ps.executeUpdate();
                }
            }

            // 3) Auto-actualizar detalles que tenían la fecha anterior del cliente
            if (fechaEventoAnterior != null && nuevaFechaEventoCliente != null) {
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Nota_Detalle nd " +
                        "JOIN Notas n ON n.numero_nota = nd.numero_nota " +
                        "SET nd.fecha_evento=? " +
                        "WHERE n.telefono=? AND n.status='A' " +
                        "AND COALESCE(nd.status,'A')='A' AND nd.fecha_evento=?")) {
                    ps.setDate(1, Date.valueOf(nuevaFechaEventoCliente));
                    ps.setString(2, telefono);
                    ps.setDate(3, Date.valueOf(fechaEventoAnterior));
                    ps.executeUpdate();
                }
            }
            if (fechaEntregaAnterior != null && nuevaFechaEntregaCliente != null) {
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Nota_Detalle nd " +
                        "JOIN Notas n ON n.numero_nota = nd.numero_nota " +
                        "SET nd.fecha_entrega=? " +
                        "WHERE n.telefono=? AND n.status='A' " +
                        "AND COALESCE(nd.status,'A')='A' AND nd.fecha_entrega=?")) {
                    ps.setDate(1, Date.valueOf(nuevaFechaEntregaCliente));
                    ps.setString(2, telefono);
                    ps.setDate(3, Date.valueOf(fechaEntregaAnterior));
                    ps.executeUpdate();
                }
            }

            // 4) Cambios puntuales por nota
            if (cambios != null && !cambios.isEmpty()) {
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Nota_Detalle SET " +
                        "fecha_evento  = COALESCE(?, fecha_evento), " +
                        "fecha_entrega = COALESCE(?, fecha_entrega) " +
                        "WHERE numero_nota=?")) {
                    for (CambioNota cnm : cambios) {
                        if (cnm == null) continue;
                        if (cnm.nuevaFechaEvento == null)  ps.setNull(1, Types.DATE);
                        else                               ps.setDate(1, Date.valueOf(cnm.nuevaFechaEvento));
                        if (cnm.nuevaFechaEntrega == null) ps.setNull(2, Types.DATE);
                        else                               ps.setDate(2, Date.valueOf(cnm.nuevaFechaEntrega));
                        ps.setInt(3, cnm.numeroNota);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            cn.commit();
        } catch (SQLException ex) {
            cn.rollback();
            throw ex;
        }
    }
}

public void actualizarSoloNotas(List<CambioNota> cambios) throws SQLException {
    if (cambios == null || cambios.isEmpty()) return;
    try (Connection cn = Conecta.getConnection();
         PreparedStatement ps = cn.prepareStatement(
             "UPDATE Nota_Detalle SET " +
             "fecha_evento  = COALESCE(?, fecha_evento), " +
             "fecha_entrega = COALESCE(?, fecha_entrega) " +
             "WHERE numero_nota=?")) {

        for (CambioNota cnm : cambios) {
            if (cnm == null) continue;
            if (cnm.nuevaFechaEvento == null)  ps.setNull(1, Types.DATE);
            else                               ps.setDate(1, Date.valueOf(cnm.nuevaFechaEvento));
            if (cnm.nuevaFechaEntrega == null) ps.setNull(2, Types.DATE);
            else                               ps.setDate(2, Date.valueOf(cnm.nuevaFechaEntrega));
            ps.setInt(3, cnm.numeroNota);
            ps.addBatch();
        }
        ps.executeBatch();
    }
}
}
