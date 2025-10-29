package Modelo;

public class Empresa {
    private Integer numeroEmpresa;   // PK
    private String razonSocial;
    private String nombreFiscal;
    private String rfc;
    private String calleNumero;
    private String colonia;
    private String codigoPostal;     // 5 dígitos
    private String ciudad;
    private String estado;
    private String whatsapp;         // numérico
    private String telefono;         // numérico
    private String instagram;
    private String facebook;
    private String tiktok;
    private String correo;
    private String paginaWeb;

    // >>> NUEVO:
    private byte[] logo;             // LONGBLOB en MySQL
    // private String logoNombre;    // opcional si agregas columna
    // private String logoTipo;      // opcional si agregas columna

    // Getters / Setters
    public Integer getNumeroEmpresa() { return numeroEmpresa; }
    public void setNumeroEmpresa(Integer numeroEmpresa) { this.numeroEmpresa = numeroEmpresa; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getNombreFiscal() { return nombreFiscal; }
    public void setNombreFiscal(String nombreFiscal) { this.nombreFiscal = nombreFiscal; }
    public String getRfc() { return rfc; }
    public void setRfc(String rfc) { this.rfc = rfc; }
    public String getCalleNumero() { return calleNumero; }
    public void setCalleNumero(String calleNumero) { this.calleNumero = calleNumero; }
    public String getColonia() { return colonia; }
    public void setColonia(String colonia) { this.colonia = colonia; }
    public String getCodigoPostal() { return codigoPostal; }
    public void setCodigoPostal(String codigoPostal) { this.codigoPostal = codigoPostal; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getInstagram() { return instagram; }
    public void setInstagram(String instagram) { this.instagram = instagram; }
    public String getFacebook() { return facebook; }
    public void setFacebook(String facebook) { this.facebook = facebook; }
    public String getTiktok() { return tiktok; }
    public void setTiktok(String tiktok) { this.tiktok = tiktok; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getPaginaWeb() { return paginaWeb; }
    public void setPaginaWeb(String paginaWeb) { this.paginaWeb = paginaWeb; }

    public byte[] getLogo() { return logo; }
    public void setLogo(byte[] logo) { this.logo = logo; }

}
