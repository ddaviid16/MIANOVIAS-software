package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CambioFechaEventoNotaService {

    public static class PreviewNota {
        public String tipo;           // CN/CR/DV...
        public String folio;
        public int totalRenglones;
        public int conFecha;          // renglones con fecha_evento NOT NULL
        public int sinFecha;          // renglones con fecha_evento IS NULL
        public List<LocalDate> fechasDistintas = new ArrayList<>(); // fechas distintas encontradas
    }

    public static class ResultadoNota {
        public int numeroNota;
        public LocalDate fechaNueva;      // null = limpiar
        public LocalDate fechaEntregaNueva; // null = limpiar
        public int renglonesActualizados; // cuántos rows de Nota_Detalle se actualizaron
    }

    /** Muestra conteos/resumen antes de aplicar. Lanza excepción si la nota no existe. */
    public PreviewNota previsualizar(int numeroNota) throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            PreviewNota pv = new PreviewNota();

            // header de nota
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT tipo, folio FROM Notas WHERE numero_nota=?")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Nota no encontrada: " + numeroNota);
                    pv.tipo  = rs.getString("tipo");
                    pv.folio = rs.getString("folio");
                }
            }

            // conteos
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT COUNT(*) AS tot, " +
                    "SUM(CASE WHEN fecha_evento IS NOT NULL THEN 1 ELSE 0 END) AS conf " +
                    "FROM Nota_Detalle WHERE numero_nota=?")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pv.totalRenglones = rs.getInt("tot");
                        pv.conFecha       = rs.getInt("conf");
                        pv.sinFecha       = pv.totalRenglones - pv.conFecha;
                    }
                }
            }

            // distintas fechas (máx 10 por sanidad)
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT DISTINCT fecha_evento FROM Nota_Detalle " +
                    "WHERE numero_nota=? AND fecha_evento IS NOT NULL " +
                    "ORDER BY fecha_evento LIMIT 10")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Date d = rs.getDate(1);
                        if (d != null) pv.fechasDistintas.add(d.toLocalDate());
                    }
                }
            }

            return pv;
        }
    }

    /**
     * Aplica la nueva fecha a TODOS los renglones de la nota (override).
     * Si fechaNueva == null, se limpia (se pone NULL).
     */
// Overload para compatibilidad: solo cambia evento
public ResultadoNota aplicar(int numeroNota, LocalDate fechaEventoNueva) throws SQLException {
    return aplicar(numeroNota, fechaEventoNueva, null);
}

// Nuevo método: cambia evento y/o entrega (usa null para dejar sin cambio)
public ResultadoNota aplicar(int numeroNota,
                             LocalDate fechaEventoNueva,
                             LocalDate fechaEntregaNueva) throws SQLException {
    try (Connection cn = Conecta.getConnection()) {
        cn.setAutoCommit(false);
        try {
            String tipo=null, status=null;
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT tipo, status FROM Notas WHERE numero_nota=? FOR UPDATE")) {
                ps.setInt(1, numeroNota);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Nota no encontrada: " + numeroNota);
                    tipo   = rs.getString("tipo");
                    status = rs.getString("status");
                }
            }
            if (!"A".equalsIgnoreCase(status))
                throw new SQLException("La nota no está activa (status=" + status + ").");
            if (!"CN".equalsIgnoreCase(tipo) && !"CR".equalsIgnoreCase(tipo))
                throw new SQLException("Solo se permite cambiar fecha en CN/CR.");

            int upd;
            try (PreparedStatement ps = cn.prepareStatement(
                    "UPDATE Nota_Detalle SET " +
                    "  fecha_evento  = COALESCE(?, fecha_evento), " +
                    "  fecha_entrega = COALESCE(?, fecha_entrega) " +
                    "WHERE numero_nota=?")) {

                if (fechaEventoNueva == null)  ps.setNull(1, Types.DATE);
                else                           ps.setDate(1, Date.valueOf(fechaEventoNueva));

                if (fechaEntregaNueva == null) ps.setNull(2, Types.DATE);
                else                           ps.setDate(2, Date.valueOf(fechaEntregaNueva));

                ps.setInt(3, numeroNota);
                upd = ps.executeUpdate();
            }

            ResultadoNota out = new ResultadoNota();
            out.numeroNota = numeroNota;
            out.fechaNueva = fechaEventoNueva;   // si quieres, añade otro campo para entrega
            out.renglonesActualizados = upd;
            cn.commit();
            return out;
        } catch (SQLException ex) {
            cn.rollback();
            throw ex;
        }
    }
}
}

