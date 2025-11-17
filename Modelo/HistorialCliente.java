package Modelo;

import java.time.LocalDate;

public class HistorialCliente {

    private String telefono1;
    private Double saldoMigrado;      // saldo histórico (antes del sistema)
    private LocalDate fechaSaldo;     // fecha en que existía ese saldo
    private String obsequios;         // texto libre
    private String observacion;       // texto libre

    public String getTelefono1() {
        return telefono1;
    }

    public void setTelefono1(String telefono1) {
        this.telefono1 = telefono1;
    }


    public void setSaldoMigrado(Double saldoMigrado) {
        this.saldoMigrado = saldoMigrado;
    }
    
    public Double getSaldoMigrado() {
        return saldoMigrado;
    }

    public LocalDate getFechaSaldo() {
        return fechaSaldo;
    }

    public void setFechaSaldo(LocalDate fechaSaldo) {
        this.fechaSaldo = fechaSaldo;
    }

    public String getObsequios() {
        return obsequios;
    }

    public void setObsequios(String obsequios) {
        this.obsequios = obsequios;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
