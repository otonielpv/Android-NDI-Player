package ndiplayer.oto.utils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas de rendimiento para frames NDI
 * Thread-safe y optimizado para bajo overhead
 */
public class FrameMetrics {
    private final AtomicInteger processedFrames = new AtomicInteger(0);
    private final AtomicInteger droppedFrames = new AtomicInteger(0);
    private final AtomicInteger skippedFrames = new AtomicInteger(0);
    
    private final AtomicLong totalFrameTime = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    private volatile long lastFpsUpdate = System.currentTimeMillis();
    private volatile int currentFPS = 0;
    private volatile int framesSinceLastUpdate = 0;
    
    // Ventana deslizante para promedios
    private static final int WINDOW_SIZE = 30;
    private final long[] frameTimeWindow = new long[WINDOW_SIZE];
    private final long[] processingTimeWindow = new long[WINDOW_SIZE];
    private volatile int windowIndex = 0;
    private final Object windowLock = new Object();
    
    public void incrementProcessedFrames() {
        int frames = processedFrames.incrementAndGet();
        framesSinceLastUpdate++;
        
        // Actualizar FPS cada segundo
        long now = System.currentTimeMillis();
        if (now - lastFpsUpdate >= 1000) {
            currentFPS = framesSinceLastUpdate;
            framesSinceLastUpdate = 0;
            lastFpsUpdate = now;
        }
    }
    
    public void incrementDroppedFrames() {
        droppedFrames.incrementAndGet();
    }
    
    public void incrementSkippedFrames() {
        skippedFrames.incrementAndGet();
    }
    
    public void addFrameTime(long timeMs) {
        totalFrameTime.addAndGet(timeMs);
        
        synchronized (windowLock) {
            frameTimeWindow[windowIndex] = timeMs;
            windowIndex = (windowIndex + 1) % WINDOW_SIZE;
        }
    }
    
    public void addProcessingTime(long timeMs) {
        totalProcessingTime.addAndGet(timeMs);
        
        synchronized (windowLock) {
            processingTimeWindow[windowIndex] = timeMs;
        }
    }
    
    public int getProcessedFrames() {
        return processedFrames.get();
    }
    
    public int getDroppedFrames() {
        return droppedFrames.get();
    }
    
    public int getSkippedFrames() {
        return skippedFrames.get();
    }
    
    public int getCurrentFPS() {
        return currentFPS;
    }
    
    public double getAverageFrameTime() {
        int frames = processedFrames.get();
        if (frames == 0) return 0.0;
        return (double) totalFrameTime.get() / frames;
    }
    
    public double getAverageProcessingTime() {
        int frames = processedFrames.get();
        if (frames == 0) return 0.0;
        return (double) totalProcessingTime.get() / frames;
    }
    
    public double getRecentAverageFrameTime() {
        synchronized (windowLock) {
            long sum = 0;
            int count = 0;
            for (long time : frameTimeWindow) {
                if (time > 0) {
                    sum += time;
                    count++;
                }
            }
            return count > 0 ? (double) sum / count : 0.0;
        }
    }
    
    public double getRecentAverageProcessingTime() {
        synchronized (windowLock) {
            long sum = 0;
            int count = 0;
            for (long time : processingTimeWindow) {
                if (time > 0) {
                    sum += time;
                    count++;
                }
            }
            return count > 0 ? (double) sum / count : 0.0;
        }
    }
    
    public double getDropRate() {
        int total = processedFrames.get() + droppedFrames.get();
        if (total == 0) return 0.0;
        return (double) droppedFrames.get() / total * 100.0;
    }
    
    public double getSkipRate() {
        int total = processedFrames.get() + skippedFrames.get();
        if (total == 0) return 0.0;
        return (double) skippedFrames.get() / total * 100.0;
    }
    
    public void reset() {
        processedFrames.set(0);
        droppedFrames.set(0);
        skippedFrames.set(0);
        totalFrameTime.set(0);
        totalProcessingTime.set(0);
        currentFPS = 0;
        framesSinceLastUpdate = 0;
        lastFpsUpdate = System.currentTimeMillis();
        
        synchronized (windowLock) {
            for (int i = 0; i < WINDOW_SIZE; i++) {
                frameTimeWindow[i] = 0;
                processingTimeWindow[i] = 0;
            }
            windowIndex = 0;
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "FrameMetrics{FPS=%d, Processed=%d, Dropped=%d (%.1f%%), Skipped=%d (%.1f%%), AvgFrame=%.1fms, AvgProc=%.1fms}",
            getCurrentFPS(),
            getProcessedFrames(),
            getDroppedFrames(), getDropRate(),
            getSkippedFrames(), getSkipRate(),
            getRecentAverageFrameTime(),
            getRecentAverageProcessingTime()
        );
    }
}
