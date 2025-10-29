package Controlador;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import Conexion.Conecta;
import Modelo.Empresa;

public class EmpresaDAO {

    private static final String SELECT_BY_ID =
        "SELECT numero_empresa, razon_social, nombre_fiscal, rfc, calle_numero, colonia, codigo_postal, " +
        "ciudad, estado, whatsapp, telefono, instagram, facebook, tiktok, correo, pagina_web, " +
        "logo " +                                     // <<< NUEVO
        "FROM Empresa WHERE numero_empresa = ?";

    // UPSERT incluyendo LOGO
    private static final String UPSERT =
        "INSERT INTO Empresa(numero_empresa, razon_social, nombre_fiscal, rfc, calle_numero, colonia, " +
        "codigo_postal, ciudad, estado, whatsapp, telefono, instagram, facebook, tiktok, correo, pagina_web, " +
        "logo) " +                                     // <<< NUEVO
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
        "ON DUPLICATE KEY UPDATE " +
        "razon_social=VALUES(razon_social), nombre_fiscal=VALUES(nombre_fiscal), rfc=VALUES(rfc), " +
        "calle_numero=VALUES(calle_numero), colonia=VALUES(colonia), codigo_postal=VALUES(codigo_postal), " +
        "ciudad=VALUES(ciudad), estado=VALUES(estado), whatsapp=VALUES(whatsapp), telefono=VALUES(telefono), " +
        "instagram=VALUES(instagram), facebook=VALUES(facebook), tiktok=VALUES(tiktok), " +
        "correo=VALUES(correo), pagina_web=VALUES(pagina_web), " +
        "logo=VALUES(logo)";                           // <<< NUEVO

    public Empresa buscarPorNumero(int numeroEmpresa) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, numeroEmpresa);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Empresa e = new Empresa();
                e.setNumeroEmpresa(rs.getInt("numero_empresa"));
                e.setRazonSocial(rs.getString("razon_social"));
                e.setNombreFiscal(rs.getString("nombre_fiscal"));
                e.setRfc(rs.getString("rfc"));
                e.setCalleNumero(rs.getString("calle_numero"));
                e.setColonia(rs.getString("colonia"));
                e.setCodigoPostal(rs.getString("codigo_postal"));
                e.setCiudad(rs.getString("ciudad"));
                e.setEstado(rs.getString("estado"));
                e.setWhatsapp(rs.getString("whatsapp"));
                e.setTelefono(rs.getString("telefono"));
                e.setInstagram(rs.getString("instagram"));
                e.setFacebook(rs.getString("facebook"));
                e.setTiktok(rs.getString("tiktok"));
                e.setCorreo(rs.getString("correo"));
                e.setPaginaWeb(rs.getString("pagina_web"));

                e.setLogo(rs.getBytes("logo"));

                return e;
            }
        }
    }

    public boolean guardar(Empresa e) throws SQLException {
        try (Connection cn = Conecta.getConnection();
             PreparedStatement ps = cn.prepareStatement(UPSERT)) {
            int i = 1;
            ps.setInt(i++, e.getNumeroEmpresa());
            ps.setString(i++, e.getRazonSocial());
            ps.setString(i++, nullIfBlank(e.getNombreFiscal()));
            ps.setString(i++, nullIfBlank(e.getRfc()));
            ps.setString(i++, nullIfBlank(e.getCalleNumero()));
            ps.setString(i++, nullIfBlank(e.getColonia()));
            ps.setString(i++, nullIfBlank(e.getCodigoPostal()));
            ps.setString(i++, nullIfBlank(e.getCiudad()));
            ps.setString(i++, nullIfBlank(e.getEstado()));
            ps.setString(i++, nullIfBlank(e.getWhatsapp()));
            ps.setString(i++, nullIfBlank(e.getTelefono()));
            ps.setString(i++, nullIfBlank(e.getInstagram()));
            ps.setString(i++, nullIfBlank(e.getFacebook()));
            ps.setString(i++, nullIfBlank(e.getTiktok()));
            ps.setString(i++, nullIfBlank(e.getCorreo()));
            ps.setString(i++, nullIfBlank(e.getPaginaWeb()));

            if (e.getLogo() != null) ps.setBytes(i++, e.getLogo());
            else ps.setNull(i++, Types.BLOB);

            return ps.executeUpdate() >= 1;
        }
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
