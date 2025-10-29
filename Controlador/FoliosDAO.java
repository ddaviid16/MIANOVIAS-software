package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FoliosDAO {

    public static class FolioRec {
        public String tipo;    // CN, CR, AB, DV  (fijo, clave lógica)
        public String prefijo; // editable por el usuario (visible en el folio)
        public int ultimo;     // último emitido (el siguiente será ultimo+1)
    }

    private void ensureBaseRows(Connection cn) throws SQLException {
        final String[][] base = { {"CN","CN"}, {"CR","CR"}, {"AB","AB"}, {"DV","DV"} };
        try (PreparedStatement sel = cn.prepareStatement(
                 "SELECT COUNT(*) FROM Folios WHERE tipo=?");
             PreparedStatement ins = cn.prepareStatement(
                 "INSERT IGNORE INTO Folios(tipo, prefijo, ultimo) VALUES(?,?,0)")) {
            for (String[] it : base) {
                sel.setString(1, it[0]);
                try (ResultSet rs = sel.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        ins.setString(1, it[0]);
                        ins.setString(2, it[1]);
                        ins.addBatch();
                    }
                }
            }
            ins.executeBatch();
        }
    }

    public List<FolioRec> listar() throws SQLException {
        try (Connection cn = Conecta.getConnection()) {
            ensureBaseRows(cn);
            List<FolioRec> out = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT tipo, prefijo, ultimo FROM Folios ORDER BY tipo")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        FolioRec r = new FolioRec();
                        r.tipo    = rs.getString("tipo");
                        r.prefijo = rs.getString("prefijo");
                        r.ultimo  = rs.getInt("ultimo");
                        out.add(r);
                    }
                }
            }
            return out;
        }
    }

    /** (Compatibilidad) Sólo cambia 'ultimo'. */
    public void actualizarVarios(Map<String,Integer> nuevosUltimos) throws SQLException {
        if (nuevosUltimos == null || nuevosUltimos.isEmpty()) return;
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                ensureBaseRows(cn);
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Folios SET ultimo=? WHERE tipo=?")) {
                    for (Map.Entry<String,Integer> e : nuevosUltimos.entrySet()) {
                        ps.setInt(1, e.getValue() == null ? 0 : e.getValue());
                        ps.setString(2, e.getKey());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }

    /** NUEVO: cambia 'prefijo' y 'ultimo' juntos. */
    public void actualizarVarios(List<FolioRec> nuevos) throws SQLException {
        if (nuevos == null || nuevos.isEmpty()) return;
        try (Connection cn = Conecta.getConnection()) {
            cn.setAutoCommit(false);
            try {
                ensureBaseRows(cn);
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE Folios SET prefijo=?, ultimo=? WHERE tipo=?")) {
                    for (FolioRec r : nuevos) {
                        ps.setString(1, r.prefijo == null ? "" : r.prefijo.trim().toUpperCase());
                        ps.setInt(2, r.ultimo);
                        ps.setString(3, r.tipo);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                cn.commit();
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            } finally {
                cn.setAutoCommit(true);
            }
        }
    }

    public String siguiente(Connection cn, String tipo) throws SQLException {
        ensureBaseRows(cn);
        String prefijo; int siguiente;
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT prefijo, ultimo FROM Folios WHERE tipo=? FOR UPDATE")) {
            ps.setString(1, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Tipo de folio no configurado: " + tipo);
                prefijo   = rs.getString("prefijo");
                int ultimo = rs.getInt("ultimo");
                siguiente = ultimo + 1;
            }
        }
        try (PreparedStatement up = cn.prepareStatement(
                "UPDATE Folios SET ultimo=? WHERE tipo=?")) {
            up.setInt(1, siguiente);
            up.setString(2, tipo);
            up.executeUpdate();
        }
        return formatear(prefijo, siguiente);
    }

    private static String formatear(String prefijo, int numero) {
        return String.format("%s%04d", prefijo == null ? "" : prefijo.trim(), numero);
    }
}
