package Modelo;

import java.time.LocalDate;

public class Asesor {
    private Integer numeroEmpleado;
    private String nombreCompleto;
    private LocalDate fechaAlta;
    private LocalDate fechaBaja;
    private String tipoEmpleado;   // NUEVO: A, M, MA
    private String status;

    public Integer getNumeroEmpleado() { return numeroEmpleado; }
    public void setNumeroEmpleado(Integer numeroEmpleado) { this.numeroEmpleado = numeroEmpleado; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public LocalDate getFechaAlta() { return fechaAlta; }
    public void setFechaAlta(LocalDate fechaAlta) { this.fechaAlta = fechaAlta; }
    public LocalDate getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDate fechaBaja) { this.fechaBaja = fechaBaja; }
    public String getTipoEmpleado() { return tipoEmpleado; }
    public void setTipoEmpleado(String tipoEmpleado) { this.tipoEmpleado = tipoEmpleado; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
