package Modelo;

import java.time.LocalDate;

public class NotaDetalle {
    private Integer id;                 // autoincremento
    private Integer numeroNota;         // FK
    private String codigoArticulo;     // FK
    private String articulo;
    private String marca;
    private String modelo;   
    private String talla;
    private String color;
    private Double precio;
    private Double descuento;           // %
    private Double  descuentoMonto;
    private Integer cantidad;
    private Double subtotal;
    private LocalDate fechaEvento;
    private String status;


    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getNumeroNota() { return numeroNota; }
    public void setNumeroNota(Integer numeroNota) { this.numeroNota = numeroNota; }
    public String getCodigoArticulo() { return codigoArticulo; }
    public void setCodigoArticulo(String codigoArticulo) { this.codigoArticulo = codigoArticulo; }
    public String getArticulo() { return articulo; }
    public void setArticulo(String articulo) { this.articulo = articulo; }
    public String getMarca() { return marca; }        // <--- NUEVO
    public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; }      // <--- NUEVO
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public Double getDescuento() { return descuento; }
    public void setDescuento(Double descuento) { this.descuento = descuento; }
    public Double getDescuentoMonto() { return descuentoMonto; }
    public void setDescuentoMonto(Double descuentoMonto) { this.descuentoMonto = descuentoMonto; }
    public Integer getCantidad() { return 1; }
    public void setCantidad(Integer cantidad) { this.cantidad = 1; }
    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }
    public LocalDate getFechaEvento() {
    return fechaEvento;
    
}
public void setFechaEvento(LocalDate fechaEvento) {
    this.fechaEvento = fechaEvento;
}
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
