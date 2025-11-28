package Modelo;

public final class SesionUsuario {

    private static Asesor usuarioActual;

    private SesionUsuario() { }

    public static void iniciar(Asesor a) {
        usuarioActual = a;
    }

    public static void cerrar() {
        usuarioActual = null;
    }

    public static Asesor getUsuarioActual() {
        return usuarioActual;
    }

    public static boolean haySesion() {
        return usuarioActual != null;
    }
    public static synchronized Integer getNumeroEmpleadoActual() {
        return (usuarioActual == null) ? null : usuarioActual.getNumeroEmpleado();
    }
    public static synchronized String getNombreEmpleadoActual() {
        return (usuarioActual == null) ? null : usuarioActual.getNombreCompleto();
    }
    public static synchronized boolean puedeCancelarNotas() {
        return usuarioActual != null && usuarioActual.isPermisoCancelaNota();
    }
}
