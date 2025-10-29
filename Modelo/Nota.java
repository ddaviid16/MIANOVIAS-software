package Modelo;

import java.time.LocalDateTime;

public class Nota {
    private Integer numeroNota;         // generado por BD
    private LocalDateTime fechaRegistro;
    private String telefono;            // cliente (opcional)
    private Integer asesor;             // numero_empleado
    private String tipo;                // 'CN'
    private Double total;
    private Double saldo;               // 0.0
    private String status;              // 'A'
    private String memo;

    private String folio;

    public void setMemo(String memo) { this.memo = memo; }
    public String getMemo() { return memo; }
    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }
    public Integer getNumeroNota() { return numeroNota; }
    public void setNumeroNota(Integer numeroNota) { this.numeroNota = numeroNota; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Integer getAsesor() { return asesor; }
    public void setAsesor(Integer asesor) { this.asesor = asesor; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public Double getSaldo() { return saldo; }
    public void setSaldo(Double saldo) { this.saldo = saldo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
