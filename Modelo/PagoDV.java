package Modelo;

public class PagoDV {
    public int numeroNotaDV;
    public String folio;
    public double monto;

    public PagoDV() {}

    public PagoDV(int numeroNotaDV, String folio, double monto) {
        this.numeroNotaDV = numeroNotaDV;
        this.folio = folio;
        this.monto = monto;
    }
}
