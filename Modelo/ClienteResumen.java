package Modelo;

import java.time.LocalDate;

public class ClienteResumen {
    private String telefono1;
    private String telefono2;
    private String nombreCompleto;
    private LocalDate fechaEvento;
    private LocalDate fechaPrueba1;
    private LocalDate fechaPrueba2;
    private LocalDate fechaEntrega;

    public String getTelefono1() { return telefono1; }
    public void setTelefono1(String telefono1) { this.telefono1 = telefono1; }
    public String getTelefono2() { return telefono2; }
    public void setTelefono2(String telefono2) { this.telefono2 = telefono2; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public LocalDate getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDate fechaEvento) { this.fechaEvento = fechaEvento; }
    public LocalDate getFechaPrueba1() { return fechaPrueba1; }
    public void setFechaPrueba1(LocalDate fechaPrueba1) { this.fechaPrueba1 = fechaPrueba1; }
    public LocalDate getFechaPrueba2() { return fechaPrueba2; }
    public void setFechaPrueba2(LocalDate fechaPrueba2) { this.fechaPrueba2 = fechaPrueba2; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }
}
