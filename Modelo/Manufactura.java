package Modelo;

import java.time.LocalDate;

public class Manufactura {
    private Integer idManufactura;
    private Integer numeroNota;
    private String articulo;
    private String descripcion;
    private Double precio;
    private Double descuento;
    private LocalDate fechaRegistro;
    private LocalDate fechaEntrega;
    private String observaciones;
    private String telefono;
    private String status;

    public Manufactura() {}

    public Manufactura(Integer numeroNota,
                       String articulo,
                       String descripcion,
                       Double precio,
                       Double descuento,
                       LocalDate fechaRegistro,
                       LocalDate fechaEntrega,
                       String observaciones,
                       String telefono,
                       String status) {
        this.numeroNota = numeroNota;
        this.articulo = articulo;
        this.descripcion = descripcion;
        this.precio = precio;
        this.descuento = descuento;
        this.fechaRegistro = fechaRegistro;
        this.fechaEntrega = fechaEntrega;
        this.observaciones = observaciones;
        this.telefono = telefono;
        this.status = status;
    }

    public Integer getIdManufactura() {
        return idManufactura;
    }

    public void setIdManufactura(Integer idManufactura) {
        this.idManufactura = idManufactura;
    }

    public Integer getNumeroNota() {
        return numeroNota;
    }

    public void setNumeroNota(Integer numeroNota) {
        this.numeroNota = numeroNota;
    }

    public String getArticulo() {
        return articulo;
    }
    public void setArticulo(String articulo) {
        this.articulo = articulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }
    public Double getDescuento() {
        return descuento;
    }
    public void setDescuento(Double descuento) {
        this.descuento = descuento;
    }

    public LocalDate getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDate fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDate fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}
