package ndiplayer.oto.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de descubrimiento NDI optimizado que ejecuta en background
 * Implementa descubrimiento asíncrono y gestión eficiente de recursos
 */
public class NDIDiscoveryService extends Service {
    private static final String TAG = "NDIDiscoveryService";
    
    // Binder para comunicación con el servicio
    public class NDIDiscoveryBinder extends Binder {
        public NDIDiscoveryService getService() {
            return NDIDiscoveryService.this;
        }
    }
    
    private final IBinder binder = new NDIDiscoveryBinder();
    private Thread discoveryThread;
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    private final AtomicReference<String[]> lastDiscoveredSources = new AtomicReference<>();
    private volatile DiscoveryCallback callback;
    
    // Native methods
    private native boolean nativeInitializeNDI();
    private native boolean nativeStartDiscovery();
    private native String[] nativeGetSources();
    private native void nativeDestroyNDI();
    
    public interface DiscoveryCallback {
        void onSourcesFound(String[] sources);
        void onDiscoveryError(String error);
        void onDiscoveryStatusUpdate(String status);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio NDI Discovery creado");
        
        // Inicializar NDI en thread separado
        new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                boolean success = nativeInitializeNDI();
                Log.d(TAG, "NDI SDK inicializado en servicio: " + success);
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando NDI SDK en servicio", e);
            }
        }).start();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Reiniciar si el sistema mata el servicio
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Destruyendo servicio NDI Discovery");
        stopDiscovery();
        
        // Limpiar recursos NDI
        try {
            nativeDestroyNDI();
        } catch (Exception e) {
            Log.e(TAG, "Error limpiando NDI SDK", e);
        }
        
        super.onDestroy();
    }
    
    public void startDiscovery(DiscoveryCallback callback) {
        if (isDiscovering.compareAndSet(false, true)) {
            this.callback = callback;
            
            discoveryThread = new Thread(this::performDiscovery, "NDI-Discovery");
            discoveryThread.setPriority(Thread.NORM_PRIORITY - 1); // Prioridad baja
            discoveryThread.start();
            
            Log.d(TAG, "Discovery iniciado en servicio");
        }
    }
    
    public void stopDiscovery() {
        if (isDiscovering.compareAndSet(true, false)) {
            if (discoveryThread != null && discoveryThread.isAlive()) {
                discoveryThread.interrupt();
                try {
                    discoveryThread.join(2000); // Esperar máximo 2 segundos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            discoveryThread = null;
            callback = null;
            Log.d(TAG, "Discovery detenido en servicio");
        }
    }
    
    private void performDiscovery() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        
        try {
            // Múltiples intentos de discovery optimizado
            for (int attempt = 1; attempt <= 6 && isDiscovering.get(); attempt++) {
                
                if (callback != null) {
                    callback.onDiscoveryStatusUpdate("Búsqueda " + attempt + "/6...");
                }
                
                Log.d(TAG, "Intento de discovery " + attempt + "/6");
                
                // Iniciar discovery nativo
                boolean discoveryStarted = nativeStartDiscovery();
                if (!discoveryStarted) {
                    Log.w(TAG, "Falló iniciar discovery en intento " + attempt);
                    Thread.sleep(1000);
                    continue;
                }
                
                // Tiempo de espera progresivo
                int waitTime = Math.min(3000 + (attempt * 1000), 8000);
                Thread.sleep(waitTime);
                
                if (!isDiscovering.get()) break;
                
                // Obtener fuentes
                String[] sources = nativeGetSources();
                
                if (sources != null && sources.length > 0) {
                    Log.d(TAG, "Encontradas " + sources.length + " fuentes en intento " + attempt);
                    lastDiscoveredSources.set(sources);
                    
                    if (callback != null) {
                        callback.onSourcesFound(sources);
                    }
                    return; // Éxito, salir
                }
                
                // Pausa entre intentos
                if (attempt < 6) {
                    Thread.sleep(2000);
                }
            }
            
            // No se encontraron fuentes
            Log.d(TAG, "No se encontraron fuentes tras 6 intentos");
            if (callback != null) {
                callback.onSourcesFound(new String[0]);
            }
            
        } catch (InterruptedException e) {
            Log.d(TAG, "Discovery interrumpido");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Error en discovery", e);
            if (callback != null) {
                callback.onDiscoveryError("Error en discovery: " + e.getMessage());
            }
        } finally {
            isDiscovering.set(false);
        }
    }
    
    public String[] getLastDiscoveredSources() {
        return lastDiscoveredSources.get();
    }
    
    public boolean isDiscovering() {
        return isDiscovering.get();
    }
    
    static {
        try {
            System.loadLibrary("ndiplayer");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error cargando librería nativa en servicio", e);
        }
    }
}
