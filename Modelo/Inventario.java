package Modelo;

import java.time.LocalDate;

public class Inventario {
    private String codigoArticulo;     // PK
    private String articulo;
    private String descripcion1;       // NUEVO
    private String descripcion2;       // NUEVO
    private String marca;
    private String modelo;
    private String talla;
    private String color;
    private Double precio;
    private Double descuento;
    private Double costoIva;           // NUEVO
    private Integer existencia;
    private String nombreNovia;       // NUEVO
    private Integer inventarioConteo;  // NUEVO
    private LocalDate fechaRegistro;
    private LocalDate fechaPago;       // NUEVO
    private String remision;           // NUEVO
    private String factura;            // NUEVO
    private String status;

    public String getCodigoArticulo() { return codigoArticulo; }
    public void setCodigoArticulo(String codigoArticulo) { this.codigoArticulo = codigoArticulo; }

    public String getArticulo() { return articulo; }
    public void setArticulo(String articulo) { this.articulo = articulo; }

    public String getDescripcion1() { return descripcion1; }
    public void setDescripcion1(String descripcion1) { this.descripcion1 = descripcion1; }

    public String getDescripcion2() { return descripcion2; }
    public void setDescripcion2(String descripcion2) { this.descripcion2 = descripcion2; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }

    public Double getDescuento() { return descuento; }
    public void setDescuento(Double descuento) { this.descuento = descuento; }

    public Double getCostoIva() { return costoIva; }
    public void setCostoIva(Double costoIva) { this.costoIva = costoIva; }

    public Integer getExistencia() { return existencia; }
    public void setExistencia(Integer existencia) { this.existencia = existencia; }
        public String getNombreNovia() {
        return nombreNovia;
    }

    public void setNombreNovia(String nombreNovia) {
        this.nombreNovia = nombreNovia;
    }

    public Integer getInventarioConteo() { return inventarioConteo; }
    public void setInventarioConteo(Integer inventarioConteo) { this.inventarioConteo = inventarioConteo; }

    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public LocalDate getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDate fechaPago) { this.fechaPago = fechaPago; }

    public String getRemision() { return remision; }
    public void setRemision(String remision) { this.remision = remision; }

    public String getFactura() { return factura; }
    public void setFactura(String factura) { this.factura = factura; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
