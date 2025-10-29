package Modelo;

import java.time.LocalDate;

public class PagoFormas {
    private Integer numeroNota; // FK
    private LocalDate fechaOperacion;
    private Double tarjetaCredito;
    private Double tarjetaDebito;
    private Double americanExpress;
    private Double transferencia;
    private Double deposito;
    private Double efectivo;
    private String tipoOperacion;       // 'CN'
    private String status;              // 'A'
    private Double devolucion;
    private String referenciaDV;


    public Integer getNumeroNota() { return numeroNota; }
    public void setNumeroNota(Integer numeroNota) { this.numeroNota = numeroNota; }
    public LocalDate getFechaOperacion() { return fechaOperacion; }
    public void setFechaOperacion(LocalDate fechaOperacion) { this.fechaOperacion = fechaOperacion; }
    public Double getTarjetaCredito() { return tarjetaCredito; }
    public void setTarjetaCredito(Double tarjetaCredito) { this.tarjetaCredito = tarjetaCredito; }
    public Double getTarjetaDebito() { return tarjetaDebito; }
    public void setTarjetaDebito(Double tarjetaDebito) { this.tarjetaDebito = tarjetaDebito; }
    public Double getAmericanExpress() { return americanExpress; }
    public void setAmericanExpress(Double americanExpress) { this.americanExpress = americanExpress; }
    public Double getTransferencia() { return transferencia; }
    public void setTransferencia(Double transferencia) { this.transferencia = transferencia; }
    public Double getDeposito() { return deposito; }
    public void setDeposito(Double deposito) { this.deposito = deposito; }
    public Double getEfectivo() { return efectivo; }
    public void setEfectivo(Double efectivo) { this.efectivo = efectivo; }
    public Double getDevolucion() { return devolucion; }
    public void setDevolucion(Double devolucion) { this.devolucion = devolucion; }
    public String getReferenciaDV() { return referenciaDV; }
    public void setReferenciaDV(String referenciaDV) { this.referenciaDV = referenciaDV; }
    public String getTipoOperacion() { return tipoOperacion; }
    public void setTipoOperacion(String tipoOperacion) { this.tipoOperacion = tipoOperacion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
