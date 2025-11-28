package Modelo;

import java.time.LocalDate;

public class ClienteResumen {
    private String telefono1;
    private String telefono2;
    private String parentescoTel2;
    private String nombreCompleto;
    private LocalDate fechaEvento;
    private LocalDate fechaPrueba1;
    private LocalDate fechaPrueba2;
    private LocalDate fechaEntrega;
    // Fechas
private LocalDate fechaCita1;
private LocalDate fechaCita2;

// Horas
private String horaCita1;
private String horaCita2;
private String horaPrueba1;
private String horaPrueba2;
private String horaEntrega;

// Personal
private String asesoraCita1;
private String asesoraCita2;
private String modistaPrueba1;
private String modistaPrueba2;
private String asesoraEntrega;


    public String getTelefono1() { return telefono1; }
    public void setTelefono1(String telefono1) { this.telefono1 = telefono1; }
    public String getTelefono2() { return telefono2; }
    public void setTelefono2(String telefono2) { this.telefono2 = telefono2; }
    public String getParentescoTel2() { return parentescoTel2; }
    public void setParentescoTel2(String parentescoTel2) { this.parentescoTel2 = parentescoTel2; }
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
    public LocalDate getFechaCita1() { return fechaCita1; }
public void setFechaCita1(LocalDate fechaCita1) { this.fechaCita1 = fechaCita1; }

public String getHoraCita1() { return horaCita1; }
public void setHoraCita1(String horaCita1) { this.horaCita1 = horaCita1; }

public String getAsesoraCita1() { return asesoraCita1; }
public void setAsesoraCita1(String asesoraCita1) { this.asesoraCita1 = asesoraCita1; }

public String getHoraCita2() { return horaCita2; }
public void setHoraCita2(String horaCita2) { this.horaCita2 = horaCita2;}

public LocalDate getFechaCita2() { return fechaCita2; }
public void setFechaCita2(LocalDate fechaCita2) { this.fechaCita2 = fechaCita2; }
public String getAsesoraCita2() { return asesoraCita2; }
public void setAsesoraCita2(String asesoraCita2) { this.asesoraCita2 = asesoraCita2; }
public String getHoraPrueba1() { return horaPrueba1; }
public void setHoraPrueba1(String horaPrueba1) { this.horaPrueba1 = horaPrueba1; }
public String getModistaPrueba1() { return modistaPrueba1; }
public void setModistaPrueba1(String modistaPrueba1) { this.modistaPrueba1 = modistaPrueba1; }
public String getHoraPrueba2() { return horaPrueba2; }
public void setHoraPrueba2(String horaPrueba2) { this.horaPrueba2 = horaPrueba2; }
public String getModistaPrueba2() { return modistaPrueba2; }
public void setModistaPrueba2(String modistaPrueba2) { this.modistaPrueba2 = modistaPrueba2; }
public String getHoraEntrega() { return horaEntrega; }
public void setHoraEntrega(String horaEntrega) { this.horaEntrega = horaEntrega; }
public String getAsesoraEntrega() { return asesoraEntrega; }
public void setAsesoraEntrega(String asesoraEntrega) { this.asesoraEntrega = asesoraEntrega; }

}
