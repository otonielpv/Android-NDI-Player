package ndiplayer.oto.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import ndiplayer.oto.utils.FrameMetrics;

/**
 * Servicio de renderizado NDI ultra-optimizado
 * Maneja la conexión, captura y procesamiento de frames en background
 */
public class NDIRenderService extends Service {
    private static final String TAG = "NDIRenderService";
    
    // Binder para comunicación
    public class NDIRenderBinder extends Binder {
        public NDIRenderService getService() {
            return NDIRenderService.this;
        }
    }
    
    private final IBinder binder = new NDIRenderBinder();
    private NDIStreamProcessor streamProcessor;
    private Thread captureThread;
    
    // Estado del servicio
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    private volatile RenderCallback renderCallback;
    private volatile String connectedSource = "";
    
    // Native methods
    private native boolean nativeConnectToSource(String sourceName, String sourceUrl);
    private native boolean nativeDisconnect();
    private native int nativeCaptureFrame(int[] widthHeight, int timeoutMs);
    private native byte[] nativeGetFrameData();
    private native int nativeGetFrameFourCC();
    private native int nativeGetFrameWidth();
    private native int nativeGetFrameHeight();
    
    public interface RenderCallback {
        void onFrameReady(Bitmap bitmap, FrameMetrics metrics);
        void onConnectionStatusChanged(boolean connected, String source);
        void onRenderError(String error);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio NDI Render creado");
        
        // Inicializar procesador de streams
        streamProcessor = new NDIStreamProcessor();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        activeConnections.incrementAndGet();
        Log.d(TAG, "Cliente conectado. Conexiones activas: " + activeConnections.get());
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        int connections = activeConnections.decrementAndGet();
        Log.d(TAG, "Cliente desconectado. Conexiones activas: " + connections);
        
        if (connections <= 0) {
            // No hay más clientes, detener captura
            stopCapture();
        }
        
        return false; // No permitir rebind automático
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY; // No reiniciar automáticamente
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Destruyendo servicio NDI Render");
        
        stopCapture();
        disconnect();
        
        if (streamProcessor != null) {
            streamProcessor.stopProcessing();
        }
        
        super.onDestroy();
    }
    
    public boolean connectToSource(String sourceName, RenderCallback callback) {
        if (isConnected.get()) {
            Log.w(TAG, "Ya hay una conexión activa");
            return false;
        }
        
        this.renderCallback = callback;
        
        return executeInBackground(() -> {
            try {
                boolean success = nativeConnectToSource(sourceName, "");
                
                if (success) {
                    isConnected.set(true);
                    connectedSource = sourceName;
                    
                    // Iniciar procesador de streams
                    streamProcessor.startProcessing(new NDIStreamProcessor.FrameCallback() {
                        @Override
                        public void onFrameReady(Bitmap bitmap, FrameMetrics metrics) {
                            if (renderCallback != null) {
                                renderCallback.onFrameReady(bitmap, metrics);
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            if (renderCallback != null) {
                                renderCallback.onRenderError(error);
                            }
                        }
                    });
                    
                    if (renderCallback != null) {
                        renderCallback.onConnectionStatusChanged(true, sourceName);
                    }
                    
                    Log.d(TAG, "Conectado exitosamente a: " + sourceName);
                    return true;
                } else {
                    Log.e(TAG, "Error conectando a: " + sourceName);
                    if (renderCallback != null) {
                        renderCallback.onRenderError("Error conectando a " + sourceName);
                    }
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Excepción conectando", e);
                if (renderCallback != null) {
                    renderCallback.onRenderError("Excepción: " + e.getMessage());
                }
                return false;
            }
        });
    }
    
    public void disconnect() {
        if (!isConnected.get()) {
            return;
        }
        
        executeInBackground(() -> {
            try {
                stopCapture();
                
                boolean success = nativeDisconnect();
                isConnected.set(false);
                String previousSource = connectedSource;
                connectedSource = "";
                
                if (renderCallback != null) {
                    renderCallback.onConnectionStatusChanged(false, previousSource);
                }
                
                Log.d(TAG, "Desconectado de: " + previousSource);
                return success;
                
            } catch (Exception e) {
                Log.e(TAG, "Error desconectando", e);
                return false;
            }
        });
    }
    
    public void startCapture() {
        if (!isConnected.get() || isCapturing.compareAndSet(false, true)) {
            if (!isConnected.get()) {
                Log.w(TAG, "No hay conexión activa para iniciar captura");
                return;
            }
            
            captureThread = new Thread(this::captureLoop, "NDI-Capture-Service");
            captureThread.setPriority(Thread.MAX_PRIORITY);
            captureThread.start();
            
            Log.d(TAG, "Captura iniciada");
        }
    }
    
    public void stopCapture() {
        if (isCapturing.compareAndSet(true, false)) {
            if (captureThread != null && captureThread.isAlive()) {
                captureThread.interrupt();
                try {
                    captureThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            captureThread = null;
            
            Log.d(TAG, "Captura detenida");
        }
    }
    
    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
        
        int[] dimensions = new int[2];
        int frameCount = 0;
        
        while (isCapturing.get() && isConnected.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Capturar frame con timeout corto
                int result = nativeCaptureFrame(dimensions, 33); // 33ms para ~30fps
                
                if (result == 1) { // Frame de video recibido
                    frameCount++;
                    
                    // Obtener datos del frame
                    byte[] frameData = nativeGetFrameData();
                    if (frameData != null && frameData.length > 0) {
                        int fourCC = nativeGetFrameFourCC();
                        int width = dimensions[0];
                        int height = dimensions[1];
                        
                        // Enviar frame al procesador
                        streamProcessor.submitFrame(frameData, width, height, fourCC);
                        
                        // Log estadísticas cada 5 segundos
                        if (frameCount % 150 == 0) {
                            FrameMetrics metrics = streamProcessor.getMetrics();
                            Log.d(TAG, String.format("Frame #%d: %dx%d, FPS: %d", 
                                frameCount, width, height, metrics.getCurrentFPS()));
                        }
                    }
                    
                } else if (result == 0) {
                    // No hay frame, pausa breve
                    Thread.sleep(1);
                } else {
                    // Error en captura
                    Log.w(TAG, "Error en captura de frame: " + result);
                    Thread.sleep(10);
                }
                
            } catch (InterruptedException e) {
                Log.d(TAG, "Thread de captura interrumpido");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error en loop de captura", e);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        
        Log.d(TAG, "Loop de captura terminado");
    }
    
    // Utilidad para ejecutar tareas en background
    private boolean executeInBackground(java.util.concurrent.Callable<Boolean> task) {
        try {
            return java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    return task.call();
                } catch (Exception e) {
                    Log.e(TAG, "Error en tarea background", e);
                    return false;
                }
            }).get();
        } catch (Exception e) {
            Log.e(TAG, "Error ejecutando tarea background", e);
            return false;
        }
    }
    
    // Getters de estado
    public boolean isConnected() {
        return isConnected.get();
    }
    
    public boolean isCapturing() {
        return isCapturing.get();
    }
    
    public String getConnectedSource() {
        return connectedSource;
    }
    
    public FrameMetrics getPerformanceMetrics() {
        return streamProcessor != null ? streamProcessor.getMetrics() : null;
    }
    
    public void setStreamQuality(int quality) {
        if (streamProcessor != null) {
            streamProcessor.setQuality(quality);
        }
    }
    
    public void setAdaptiveMode(boolean enabled) {
        if (streamProcessor != null) {
            streamProcessor.setAdaptiveMode(enabled);
        }
    }
    
    static {
        try {
            System.loadLibrary("ndiplayer");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error cargando librería nativa en render service", e);
        }
    }
}
