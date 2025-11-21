package Modelo;

import java.time.LocalDate;

public class ObsequioInv {
    private String codigoArticulo;
    private String articulo;
    private String marca;
    private String modelo;
    private String talla;
    private String color;
    private Double precio;        // puede ser null
    private Double descuento;     // %
    private Integer existencia;   // puede ser null
    private String status;        // 'A' | 'C'
    private LocalDate fechaRegistro;

    public String getCodigoArticulo() { return codigoArticulo; }
    public void setCodigoArticulo(String v) { this.codigoArticulo = v; }
    public String getArticulo() { return articulo; }
    public void setArticulo(String v) { this.articulo = v; }
    public String getMarca() { return marca; }
    public void setMarca(String v) { this.marca = v; }
    public String getModelo() { return modelo; }
    public void setModelo(String v) { this.modelo = v; }
    public String getTalla() { return talla; }
    public void setTalla(String v) { this.talla = v; }
    public String getColor() { return color; }
    public void setColor(String v) { this.color = v; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double v) { this.precio = v; }
    public Double getDescuento() { return descuento; }
    public void setDescuento(Double v) { this.descuento = v; }
    public Integer getExistencia() { return existencia; }
    public void setExistencia(Integer v) { this.existencia = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate v) { this.fechaRegistro = v; }

    @Override public String toString() { return codigoArticulo + " - " + articulo; }
}
