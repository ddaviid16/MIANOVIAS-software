package Controlador;

import Conexion.Conecta;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reporte de obsequios por venta. */
public class ReporteObsequiosDAO {

    /** Cabecera de ventas para el panel superior. */
    public static class VentaRow {
        public int    numeroNota;
        public String folio;
        public String telefono;
        public Date   fecha;   // solo la fecha (DATE(fecha_registro))
    }

    /** Fila normalizada de obsequio: un obsequio por renglón. */
    public static class ObsItem {
        public String codigo;
        public String descripcion;
        public String tipoOperacion;
    }

    /** Lista ventas (CN/CR) en el rango indicado. */
    public List<VentaRow> listarVentas(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql =
            "SELECT numero_nota, folio, telefono, DATE(fecha_registro) " +
            "FROM Notas " +
            "WHERE status='A' AND tipo IN ('CN','CR') " +
            "  AND DATE(fecha_registro) BETWEEN ? AND ? " +
            "ORDER BY numero_nota DESC";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                List<VentaRow> out = new ArrayList<>();
                while (rs.next()) {
                    VentaRow v = new VentaRow();
                    v.numeroNota = rs.getInt(1);
                    v.folio      = rs.getString(2);
                    v.telefono   = rs.getString(3);
                    v.fecha      = rs.getDate(4);
                    out.add(v);
                }
                return out;
            }
        }
    }

    /**
     * Devuelve todos los obsequios de una nota, uno por renglón,
     * con su código, descripción y tipo de operación.
     */
    public List<ObsItem> listarObsequiosDeNota(int numeroNota) throws SQLException {
        String sql =
            "SELECT obsequio1_cod, obsequio1, " +
            "       obsequio2_cod, obsequio2, " +
            "       obsequio3_cod, obsequio3, " +
            "       obsequio4_cod, obsequio4, " +
            "       obsequio5_cod, obsequio5, " +
            "       tipo_operacion " +
            "FROM obsequios WHERE numero_nota=?";

        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, numeroNota);
            try (ResultSet rs = ps.executeQuery()) {
                List<ObsItem> out = new ArrayList<>();
                while (rs.next()) {
                    String tipo = nv(rs.getString(11));
                    add(out, rs.getString(1),  rs.getString(2),  tipo); // 1
                    add(out, rs.getString(3),  rs.getString(4),  tipo); // 2
                    add(out, rs.getString(5),  rs.getString(6),  tipo); // 3
                    add(out, rs.getString(7),  rs.getString(8),  tipo); // 4
                    add(out, rs.getString(9),  rs.getString(10), tipo); // 5
                }
                return out;
            }
        }
    }

    // ---------- helpers ----------
    private static void add(List<ObsItem> out, String cod, String desc, String tipo) {
        boolean vacio = ( (cod==null || cod.isBlank()) && (desc==null || desc.isBlank()) );
        if (vacio) return;
        ObsItem i = new ObsItem();
        i.codigo        = nv(cod);
        i.descripcion   = nv(desc);
        i.tipoOperacion = tipo;
        out.add(i);
    }
    private static String nv(String s){ return s==null ? "" : s.trim(); }
}
