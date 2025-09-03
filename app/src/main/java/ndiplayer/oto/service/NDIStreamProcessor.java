package ndiplayer.oto.service;

import android.graphics.Bitmap;
import android.os.Process;
import android.util.Log;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import ndiplayer.oto.utils.ObjectPool;
import ndiplayer.oto.utils.FrameMetrics;

/**
 * Procesador de streams NDI ultra-optimizado para dispositivos de bajo rendimiento
 * Implementa procesamiento asíncrono, pools de objetos y gestión inteligente de memoria
 */
public class NDIStreamProcessor {
    private static final String TAG = "NDIStreamProcessor";
    
    // Configuración de rendimiento
    private static final int MAX_WORKER_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int FRAME_QUEUE_SIZE = 3; // Búfer mínimo para evitar latencia
    private static final int TARGET_FPS = 30;
    private static final long TARGET_FRAME_TIME_MS = 1000 / TARGET_FPS;
    
    // Thread pools optimizados
    private ThreadPoolExecutor captureExecutor;
    private ThreadPoolExecutor processingExecutor;
    private ScheduledExecutorService statsExecutor;
    
    // Gestión de frames
    private final BlockingQueue<FrameData> frameQueue;
    private final ObjectPool<FrameData> framePool;
    private final ObjectPool<int[]> pixelPool;
    private final ObjectPool<Bitmap> bitmapPool;
    
    // Estado del procesador
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger activeCaptures = new AtomicInteger(0);
    private final AtomicLong lastFrameTime = new AtomicLong(0);
    
    // Métricas de rendimiento
    private final FrameMetrics frameMetrics;
    private volatile FrameCallback frameCallback;
    
    // Configuración adaptativa
    private volatile int currentQuality = 100; // 100% calidad inicial
    private volatile boolean adaptiveMode = true;
    
    public interface FrameCallback {
        void onFrameReady(Bitmap bitmap, FrameMetrics metrics);
        void onError(String error);
    }
    
    public NDIStreamProcessor() {
        // Inicializar colas con tamaño optimizado
        this.frameQueue = new ArrayBlockingQueue<>(FRAME_QUEUE_SIZE);
        
        // Pools de objetos para evitar GC
        this.framePool = new ObjectPool<>(
            () -> new FrameData(),
            FrameData::reset,
            FRAME_QUEUE_SIZE * 2
        );
        
        this.pixelPool = new ObjectPool<>(
            () -> new int[1920 * 1080], // Máximo Full HD
            null, // No necesita reset
            4
        );
        
        this.bitmapPool = new ObjectPool<>(
            () -> Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888),
            bitmap -> bitmap.eraseColor(0),
            3
        );
        
        this.frameMetrics = new FrameMetrics();
        
        initializeThreadPools();
    }
    
    private void initializeThreadPools() {
        // Executor para captura de frames - alta prioridad
        captureExecutor = new ThreadPoolExecutor(
            1, 1, // Un solo thread de captura
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1),
            r -> {
                Thread t = new Thread(r, "NDI-Capture");
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        
        // Executor para procesamiento - prioridad media
        processingExecutor = new ThreadPoolExecutor(
            MAX_WORKER_THREADS, MAX_WORKER_THREADS,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(FRAME_QUEUE_SIZE),
            r -> {
                Thread t = new Thread(r, "NDI-Process");
                t.setPriority(Thread.NORM_PRIORITY + 1);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        
        // Executor para estadísticas
        statsExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "NDI-Stats");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        );
    }
    
    public void startProcessing(FrameCallback callback) {
        if (isRunning.compareAndSet(false, true)) {
            this.frameCallback = callback;
            
            // Iniciar procesamiento de frames
            processingExecutor.execute(this::processFrames);
            
            // Iniciar estadísticas periódicas
            statsExecutor.scheduleAtFixedRate(
                this::updatePerformanceMetrics,
                1, 1, TimeUnit.SECONDS
            );
            
            Log.i(TAG, "Stream processor iniciado con " + MAX_WORKER_THREADS + " threads");
        }
    }
    
    public void stopProcessing() {
        if (isRunning.compareAndSet(true, false)) {
            // Detener executors
            captureExecutor.shutdown();
            processingExecutor.shutdown();
            statsExecutor.shutdown();
            
            try {
                if (!captureExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    captureExecutor.shutdownNow();
                }
                if (!processingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    processingExecutor.shutdownNow();
                }
                if (!statsExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    statsExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Limpiar recursos
            clearQueues();
            Log.i(TAG, "Stream processor detenido");
        }
    }
    
    public void submitFrame(byte[] frameData, int width, int height, int fourCC) {
        if (!isRunning.get() || activeCaptures.get() > FRAME_QUEUE_SIZE) {
            return; // Skip si hay demasiados frames pendientes
        }
        
        captureExecutor.execute(() -> {
            long startTime = System.nanoTime();
            activeCaptures.incrementAndGet();
            
            try {
                FrameData frame = framePool.acquire();
                if (frame == null) {
                    frame = new FrameData();
                }
                
                frame.setData(frameData, width, height, fourCC);
                frame.captureTime = startTime;
                
                // Control de framerate adaptativo
                long currentTime = System.currentTimeMillis();
                long timeSinceLastFrame = currentTime - lastFrameTime.get();
                
                if (adaptiveMode && timeSinceLastFrame < TARGET_FRAME_TIME_MS) {
                    // Skip frame para mantener framerate objetivo
                    frameMetrics.incrementSkippedFrames();
                    framePool.release(frame);
                    return;
                }
                
                lastFrameTime.set(currentTime);
                
                // Intentar agregar a la cola (non-blocking)
                if (!frameQueue.offer(frame)) {
                    // Cola llena, descartar frame más antiguo
                    FrameData oldFrame = frameQueue.poll();
                    if (oldFrame != null) {
                        framePool.release(oldFrame);
                        frameMetrics.incrementDroppedFrames();
                    }
                    frameQueue.offer(frame);
                }
                
            } finally {
                activeCaptures.decrementAndGet();
            }
        });
    }
    
    private void processFrames() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
        
        while (isRunning.get()) {
            try {
                FrameData frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                if (frame == null) continue;
                
                long processingStart = System.nanoTime();
                
                // Procesar frame de manera optimizada
                Bitmap bitmap = processFrameOptimized(frame);
                
                if (bitmap != null && frameCallback != null) {
                    // Actualizar métricas
                    long processingTime = System.nanoTime() - processingStart;
                    long totalTime = System.nanoTime() - frame.captureTime;
                    
                    frameMetrics.addFrameTime(totalTime / 1_000_000); // Convert to ms
                    frameMetrics.addProcessingTime(processingTime / 1_000_000);
                    frameMetrics.incrementProcessedFrames();
                    
                    // Callback en thread de UI
                    frameCallback.onFrameReady(bitmap, frameMetrics);
                }
                
                // Devolver frame al pool
                framePool.release(frame);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error procesando frame", e);
                if (frameCallback != null) {
                    frameCallback.onError("Error procesando frame: " + e.getMessage());
                }
            }
        }
    }
    
    private Bitmap processFrameOptimized(FrameData frame) {
        try {
            // Calcular dimensiones con calidad adaptativa
            int targetWidth = (int) (frame.width * currentQuality / 100.0f);
            int targetHeight = (int) (frame.height * currentQuality / 100.0f);
            
            // Asegurar dimensiones pares para optimización
            targetWidth = (targetWidth + 1) & ~1;
            targetHeight = (targetHeight + 1) & ~1;
            
            // Obtener arrays del pool
            int[] pixels = pixelPool.acquire();
            if (pixels == null || pixels.length < targetWidth * targetHeight) {
                pixels = new int[targetWidth * targetHeight];
            }
            
            Bitmap bitmap = null;
            
            // Conversión optimizada según formato
            switch (frame.fourCC) {
                case FrameData.FOURCC_BGRA:
                    bitmap = convertBGRAOptimized(frame.data, pixels, frame.width, frame.height, targetWidth, targetHeight);
                    break;
                case FrameData.FOURCC_UYVY:
                    bitmap = convertUYVYOptimized(frame.data, pixels, frame.width, frame.height, targetWidth, targetHeight);
                    break;
                case FrameData.FOURCC_RGBA:
                    bitmap = convertRGBAOptimized(frame.data, pixels, frame.width, frame.height, targetWidth, targetHeight);
                    break;
                default:
                    Log.w(TAG, "Formato no soportado: " + Integer.toHexString(frame.fourCC));
                    bitmap = convertBGRAOptimized(frame.data, pixels, frame.width, frame.height, targetWidth, targetHeight);
                    break;
            }
            
            // Devolver array al pool
            pixelPool.release(pixels);
            
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error en processFrameOptimized", e);
            return null;
        }
    }
    
    private Bitmap convertBGRAOptimized(byte[] data, int[] pixels, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        // Implementación optimizada con sampling inteligente
        float xRatio = (float) srcWidth / dstWidth;
        float yRatio = (float) srcHeight / dstHeight;
        
        for (int y = 0; y < dstHeight; y++) {
            int srcY = (int) (y * yRatio);
            int srcRowOffset = srcY * srcWidth * 4;
            int dstRowOffset = y * dstWidth;
            
            for (int x = 0; x < dstWidth; x++) {
                int srcX = (int) (x * xRatio);
                int srcPixelOffset = srcRowOffset + srcX * 4;
                
                if (srcPixelOffset + 3 < data.length) {
                    int b = data[srcPixelOffset] & 0xFF;
                    int g = data[srcPixelOffset + 1] & 0xFF;
                    int r = data[srcPixelOffset + 2] & 0xFF;
                    int a = data[srcPixelOffset + 3] & 0xFF;
                    
                    pixels[dstRowOffset + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }
        
        return Bitmap.createBitmap(pixels, dstWidth, dstHeight, Bitmap.Config.ARGB_8888);
    }
    
    private Bitmap convertUYVYOptimized(byte[] data, int[] pixels, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        // Conversión UYVY optimizada con lookup tables
        float xRatio = (float) srcWidth / dstWidth;
        float yRatio = (float) srcHeight / dstHeight;
        
        for (int y = 0; y < dstHeight; y++) {
            int srcY = (int) (y * yRatio);
            int srcRowOffset = srcY * srcWidth * 2; // UYVY es 2 bytes por pixel
            int dstRowOffset = y * dstWidth;
            
            for (int x = 0; x < dstWidth; x += 2) { // Procesar 2 pixels a la vez para UYVY
                int srcX = (int) (x * xRatio);
                int srcPixelOffset = srcRowOffset + (srcX & ~1) * 2; // Asegurar offset par
                
                if (srcPixelOffset + 3 < data.length) {
                    int u = data[srcPixelOffset] & 0xFF;
                    int y1 = data[srcPixelOffset + 1] & 0xFF;
                    int v = data[srcPixelOffset + 2] & 0xFF;
                    int y2 = data[srcPixelOffset + 3] & 0xFF;
                    
                    // Convertir YUV a RGB usando lookup optimizado
                    int[] rgb1 = convertYUVToRGBFast(y1, u, v);
                    int[] rgb2 = convertYUVToRGBFast(y2, u, v);
                    
                    pixels[dstRowOffset + x] = (0xFF << 24) | (rgb1[0] << 16) | (rgb1[1] << 8) | rgb1[2];
                    if (x + 1 < dstWidth) {
                        pixels[dstRowOffset + x + 1] = (0xFF << 24) | (rgb2[0] << 16) | (rgb2[1] << 8) | rgb2[2];
                    }
                }
            }
        }
        
        return Bitmap.createBitmap(pixels, dstWidth, dstHeight, Bitmap.Config.ARGB_8888);
    }
    
    private Bitmap convertRGBAOptimized(byte[] data, int[] pixels, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        float xRatio = (float) srcWidth / dstWidth;
        float yRatio = (float) srcHeight / dstHeight;
        
        for (int y = 0; y < dstHeight; y++) {
            int srcY = (int) (y * yRatio);
            int srcRowOffset = srcY * srcWidth * 4;
            int dstRowOffset = y * dstWidth;
            
            for (int x = 0; x < dstWidth; x++) {
                int srcX = (int) (x * xRatio);
                int srcPixelOffset = srcRowOffset + srcX * 4;
                
                if (srcPixelOffset + 3 < data.length) {
                    int r = data[srcPixelOffset] & 0xFF;
                    int g = data[srcPixelOffset + 1] & 0xFF;
                    int b = data[srcPixelOffset + 2] & 0xFF;
                    int a = data[srcPixelOffset + 3] & 0xFF;
                    
                    pixels[dstRowOffset + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }
        
        return Bitmap.createBitmap(pixels, dstWidth, dstHeight, Bitmap.Config.ARGB_8888);
    }
    
    // Conversión YUV a RGB optimizada con lookup tables
    private static final int[] YUV_TO_RGB_LOOKUP_R = new int[256];
    private static final int[] YUV_TO_RGB_LOOKUP_G_U = new int[256];
    private static final int[] YUV_TO_RGB_LOOKUP_G_V = new int[256];
    private static final int[] YUV_TO_RGB_LOOKUP_B = new int[256];
    
    static {
        // Precalcular lookup tables para conversión YUV
        for (int i = 0; i < 256; i++) {
            int c = i - 128;
            YUV_TO_RGB_LOOKUP_R[i] = (int) (1.402f * c);
            YUV_TO_RGB_LOOKUP_G_U[i] = (int) (-0.344f * c);
            YUV_TO_RGB_LOOKUP_G_V[i] = (int) (-0.714f * c);
            YUV_TO_RGB_LOOKUP_B[i] = (int) (1.772f * c);
        }
    }
    
    private int[] convertYUVToRGBFast(int y, int u, int v) {
        int r = y + YUV_TO_RGB_LOOKUP_R[v];
        int g = y + YUV_TO_RGB_LOOKUP_G_U[u] + YUV_TO_RGB_LOOKUP_G_V[v];
        int b = y + YUV_TO_RGB_LOOKUP_B[u];
        
        // Clamp con operaciones bit a bit (más rápido)
        r = (r & 0xFFFFFF00) == 0 ? r : r < 0 ? 0 : 255;
        g = (g & 0xFFFFFF00) == 0 ? g : g < 0 ? 0 : 255;
        b = (b & 0xFFFFFF00) == 0 ? b : b < 0 ? 0 : 255;
        
        return new int[]{r, g, b};
    }
    
    private void updatePerformanceMetrics() {
        try {
            double avgFrameTime = frameMetrics.getAverageFrameTime();
            double avgProcessingTime = frameMetrics.getAverageProcessingTime();
            int currentFPS = frameMetrics.getCurrentFPS();
            
            // Ajuste adaptativo de calidad
            if (adaptiveMode) {
                if (avgFrameTime > TARGET_FRAME_TIME_MS * 1.5) {
                    // Rendimiento bajo, reducir calidad
                    currentQuality = Math.max(50, currentQuality - 10);
                    Log.d(TAG, "Reduciendo calidad a " + currentQuality + "%");
                } else if (avgFrameTime < TARGET_FRAME_TIME_MS * 0.8 && currentQuality < 100) {
                    // Buen rendimiento, aumentar calidad
                    currentQuality = Math.min(100, currentQuality + 5);
                    Log.d(TAG, "Aumentando calidad a " + currentQuality + "%");
                }
            }
            
            Log.d(TAG, String.format("Rendimiento: %d FPS, Frame: %.1fms, Proc: %.1fms, Cal: %d%%",
                currentFPS, avgFrameTime, avgProcessingTime, currentQuality));
                
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando métricas", e);
        }
    }
    
    private void clearQueues() {
        // Limpiar cola de frames
        FrameData frame;
        while ((frame = frameQueue.poll()) != null) {
            framePool.release(frame);
        }
        
        // Limpiar pools
        framePool.clear();
        pixelPool.clear();
        bitmapPool.clear();
    }
    
    // Getters para métricas
    public FrameMetrics getMetrics() {
        return frameMetrics;
    }
    
    public void setAdaptiveMode(boolean enabled) {
        this.adaptiveMode = enabled;
    }
    
    public void setQuality(int quality) {
        this.currentQuality = Math.max(25, Math.min(100, quality));
    }
    
    // Clase interna para datos de frame
    private static class FrameData {
        static final int FOURCC_BGRA = ('B' << 0) | ('G' << 8) | ('R' << 16) | ('A' << 24);
        static final int FOURCC_UYVY = ('U' << 0) | ('Y' << 8) | ('V' << 16) | ('Y' << 24);
        static final int FOURCC_RGBA = ('R' << 0) | ('G' << 8) | ('B' << 16) | ('A' << 24);
        
        byte[] data;
        int width;
        int height;
        int fourCC;
        long captureTime;
        
        void setData(byte[] data, int width, int height, int fourCC) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.fourCC = fourCC;
        }
        
        void reset() {
            this.data = null;
            this.width = 0;
            this.height = 0;
            this.fourCC = 0;
            this.captureTime = 0;
        }
    }
}
