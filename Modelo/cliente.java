package Modelo;

public class cliente {
    private String telefono1;          // PK
    private String telefono2;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private Integer edad;              // puede ser null
    private String comoSeEntero;       // ENUM: UBICACION, RECOMENDACION, GOOGLE MAPS, TIKTOK
    private String fechaEvento;        // YYYY-MM-DD (o null)
    private String lugarEvento;        // ENUM: HACIENDA, JARDIN, SALON, PLAYA
    private String fechaPrueba1;       // YYYY-MM-DD
    private String fechaPrueba2;       // YYYY-MM-DD
    private String fechaEntrega;       // YYYY-MM-DD
    private Double busto;              // null permitido
    private Double cintura;
    private Double cadera;
    private String status;             // ENUM: A, C
    private String situacionEvento;    // ENUM: CANCELA DEFINITIVO, POSPONE BODA INDEFINIDO, NORMAL

    // Getters/Setters
    public String getTelefono1() { return telefono1; }
    public void setTelefono1(String telefono1) { this.telefono1 = telefono1; }
    public String getTelefono2() { return telefono2; }
    public void setTelefono2(String telefono2) { this.telefono2 = telefono2; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public void setApellidoPaterno(String apellidoPaterno) { this.apellidoPaterno = apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public void setApellidoMaterno(String apellidoMaterno) { this.apellidoMaterno = apellidoMaterno; }
    public Integer getEdad() { return edad; }
    public void setEdad(Integer edad) { this.edad = edad; }
    public String getComoSeEntero() { return comoSeEntero; }
    public void setComoSeEntero(String comoSeEntero) { this.comoSeEntero = comoSeEntero; }
    public String getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(String fechaEvento) { this.fechaEvento = fechaEvento; }
    public String getLugarEvento() { return lugarEvento; }
    public void setLugarEvento(String lugarEvento) { this.lugarEvento = lugarEvento; }
    public String getFechaPrueba1() { return fechaPrueba1; }
    public void setFechaPrueba1(String fechaPrueba1) { this.fechaPrueba1 = fechaPrueba1; }
    public String getFechaPrueba2() { return fechaPrueba2; }
    public void setFechaPrueba2(String fechaPrueba2) { this.fechaPrueba2 = fechaPrueba2; }
    public String getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(String fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public Double getBusto() { return busto; }
    public void setBusto(Double busto) { this.busto = busto; }
    public Double getCintura() { return cintura; }
    public void setCintura(Double cintura) { this.cintura = cintura; }
    public Double getCadera() { return cadera; }
    public void setCadera(Double cadera) { this.cadera = cadera; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSituacionEvento() { return situacionEvento; }
    public void setSituacionEvento(String situacionEvento) { this.situacionEvento = situacionEvento; }
}
