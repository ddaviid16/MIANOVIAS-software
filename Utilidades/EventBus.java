package Utilidades;

import java.util.ArrayList;
import java.util.List;

public class EventBus {

    public interface CorteCajaListener {
        void onOperacionFinalizada();
    }

    private static final List<CorteCajaListener> listeners = new ArrayList<>();

    public static void addCorteCajaListener(CorteCajaListener l) {
        listeners.add(l);
    }

    public static void removeCorteCajaListener(CorteCajaListener l) {
        listeners.remove(l);
    }

    public static void notificarOperacionFinalizada() {
        for (CorteCajaListener l : new ArrayList<>(listeners)) {
            try { l.onOperacionFinalizada(); } catch (Exception ignore) {}
        }
    }
}

