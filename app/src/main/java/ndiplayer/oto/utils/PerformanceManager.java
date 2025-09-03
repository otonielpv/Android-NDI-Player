package ndiplayer.oto.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Manager de rendimiento que adapta automáticamente la configuración
 * según las capacidades del dispositivo
 */
public class PerformanceManager {
    private static final String TAG = "PerformanceManager";
    private static final String PREFS_NAME = "ndi_performance";
    
    // Categorías de dispositivos
    public enum DeviceClass {
        LOW_END,    // Dispositivos de gama baja
        MID_RANGE,  // Dispositivos de gama media
        HIGH_END    // Dispositivos de gama alta
    }
    
    // Configuraciones optimizadas por tipo de dispositivo
    public static class PerformanceConfig {
        public final int maxResolutionWidth;
        public final int maxResolutionHeight;
        public final int targetFPS;
        public final int frameBufferSize;
        public final int processingThreads;
        public final boolean useAdaptiveQuality;
        public final int initialQuality;
        public final boolean useFrameSkipping;
        
        public PerformanceConfig(int maxResWidth, int maxResHeight, int fps, 
                               int bufferSize, int threads, boolean adaptive, 
                               int quality, boolean frameSkip) {
            this.maxResolutionWidth = maxResWidth;
            this.maxResolutionHeight = maxResHeight;
            this.targetFPS = fps;
            this.frameBufferSize = bufferSize;
            this.processingThreads = threads;
            this.useAdaptiveQuality = adaptive;
            this.initialQuality = quality;
            this.useFrameSkipping = frameSkip;
        }
    }
    
    private static final PerformanceConfig LOW_END_CONFIG = new PerformanceConfig(
        854, 480,    // Máximo 480p
        20,          // 20 FPS
        2,           // Buffer mínimo
        1,           // Un solo thread
        true,        // Calidad adaptativa activa
        60,          // Calidad inicial baja
        true         // Frame skipping activo
    );
    
    private static final PerformanceConfig MID_RANGE_CONFIG = new PerformanceConfig(
        1280, 720,   // Máximo 720p
        30,          // 30 FPS
        3,           // Buffer normal
        2,           // Dos threads
        true,        // Calidad adaptativa
        80,          // Calidad inicial media
        true         // Frame skipping moderado
    );
    
    private static final PerformanceConfig HIGH_END_CONFIG = new PerformanceConfig(
        1920, 1080,  // Máximo 1080p
        60,          // 60 FPS
        4,           // Buffer amplio
        3,           // Tres threads
        false,       // Calidad fija
        100,         // Calidad máxima
        false        // Sin frame skipping
    );
    
    private final Context context;
    private final SharedPreferences prefs;
    private DeviceClass deviceClass;
    private PerformanceConfig currentConfig;
    
    public PerformanceManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Detectar clase de dispositivo
        this.deviceClass = detectDeviceClass();
        this.currentConfig = getConfigForDeviceClass(deviceClass);
        
        Log.i(TAG, "Dispositivo clasificado como: " + deviceClass);
        Log.i(TAG, "Configuración aplicada: " + configToString(currentConfig));
    }
    
    private DeviceClass detectDeviceClass() {
        try {
            // Factores para clasificación
            int totalRAM = getTotalRAM();
            int cpuCores = Runtime.getRuntime().availableProcessors();
            int sdkVersion = Build.VERSION.SDK_INT;
            String cpuAbi = Build.SUPPORTED_ABIS[0];
            
            Log.d(TAG, String.format("Specs: RAM=%dMB, Cores=%d, SDK=%d, ABI=%s", 
                totalRAM, cpuCores, sdkVersion, cpuAbi));
            
            // Puntuación basada en especificaciones
            int score = 0;
            
            // RAM (40% del peso)
            if (totalRAM >= 6000) score += 40;
            else if (totalRAM >= 4000) score += 30;
            else if (totalRAM >= 3000) score += 20;
            else if (totalRAM >= 2000) score += 10;
            
            // CPU Cores (30% del peso)
            if (cpuCores >= 8) score += 30;
            else if (cpuCores >= 6) score += 25;
            else if (cpuCores >= 4) score += 20;
            else if (cpuCores >= 2) score += 10;
            
            // Android Version (20% del peso)
            if (sdkVersion >= 30) score += 20; // Android 11+
            else if (sdkVersion >= 28) score += 15; // Android 9+
            else if (sdkVersion >= 26) score += 10; // Android 8+
            else if (sdkVersion >= 23) score += 5;  // Android 6+
            
            // Arquitectura (10% del peso)
            if (cpuAbi.contains("arm64")) score += 10;
            else if (cpuAbi.contains("armeabi-v7a")) score += 5;
            
            Log.d(TAG, "Puntuación del dispositivo: " + score + "/100");
            
            // Clasificación basada en puntuación
            if (score >= 70) return DeviceClass.HIGH_END;
            else if (score >= 40) return DeviceClass.MID_RANGE;
            else return DeviceClass.LOW_END;
            
        } catch (Exception e) {
            Log.w(TAG, "Error detectando clase de dispositivo, usando LOW_END", e);
            return DeviceClass.LOW_END;
        }
    }
    
    private int getTotalRAM() {
        try {
            // Leer de /proc/meminfo
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null && line.startsWith("MemTotal:")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[1]) / 1024; // KB to MB
                }
            }
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "Error leyendo RAM total", e);
        }
        
        return 1024; // Fallback conservador: 1GB
    }
    
    private PerformanceConfig getConfigForDeviceClass(DeviceClass deviceClass) {
        switch (deviceClass) {
            case HIGH_END:
                return HIGH_END_CONFIG;
            case MID_RANGE:
                return MID_RANGE_CONFIG;
            case LOW_END:
            default:
                return LOW_END_CONFIG;
        }
    }
    
    public PerformanceConfig getCurrentConfig() {
        return currentConfig;
    }
    
    public DeviceClass getDeviceClass() {
        return deviceClass;
    }
    
    /**
     * Aplica configuración manual (sobrescribe la automática)
     */
    public void applyCustomConfig(PerformanceConfig customConfig) {
        Log.d(TAG, "Aplicando configuración personalizada: " + 
              customConfig.maxResolutionWidth + "x" + customConfig.maxResolutionHeight + 
              " @ " + customConfig.targetFPS + " FPS");
        
        this.currentConfig = customConfig;
        
        // Guardar configuración personalizada
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("custom_config", true);
        editor.putInt("max_width", customConfig.maxResolutionWidth);
        editor.putInt("max_height", customConfig.maxResolutionHeight);
        editor.putInt("target_fps", customConfig.targetFPS);
        editor.putInt("buffer_size", customConfig.frameBufferSize);
        editor.putInt("threads", customConfig.processingThreads);
        editor.putBoolean("adaptive", customConfig.useAdaptiveQuality);
        editor.putInt("quality", customConfig.initialQuality);
        editor.putBoolean("frame_skip", customConfig.useFrameSkipping);
        boolean saved = editor.commit(); // Usar commit para asegurar que se guarde inmediatamente
        
        Log.i(TAG, "Configuración personalizada aplicada y guardada: " + saved);
    }
    
    /**
     * Restaura configuración automática
     */
    public void restoreAutoConfig() {
        Log.d(TAG, "Restaurando configuración automática para dispositivo: " + deviceClass);
        
        this.currentConfig = getConfigForDeviceClass(deviceClass);
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("custom_config", false);
        boolean saved = editor.commit();
        
        Log.i(TAG, "Configuración automática restaurada: " + 
              currentConfig.maxResolutionWidth + "x" + currentConfig.maxResolutionHeight + 
              " @ " + currentConfig.targetFPS + " FPS, guardado: " + saved);
    }
    
    /**
     * Carga configuración guardada
     */
    public void loadSavedConfig() {
        if (prefs.getBoolean("custom_config", false)) {
            // Cargar configuración personalizada
            PerformanceConfig savedConfig = new PerformanceConfig(
                prefs.getInt("max_width", currentConfig.maxResolutionWidth),
                prefs.getInt("max_height", currentConfig.maxResolutionHeight),
                prefs.getInt("target_fps", currentConfig.targetFPS),
                prefs.getInt("buffer_size", currentConfig.frameBufferSize),
                prefs.getInt("threads", currentConfig.processingThreads),
                prefs.getBoolean("adaptive", currentConfig.useAdaptiveQuality),
                prefs.getInt("quality", currentConfig.initialQuality),
                prefs.getBoolean("frame_skip", currentConfig.useFrameSkipping)
            );
            
            this.currentConfig = savedConfig;
            Log.i(TAG, "Configuración personalizada cargada");
        }
    }
    
    /**
     * Optimización adaptativa basada en métricas en tiempo real
     */
    public void adaptiveOptimization(FrameMetrics metrics) {
        if (!currentConfig.useAdaptiveQuality) return;
        
        double avgFrameTime = metrics.getRecentAverageFrameTime();
        double targetFrameTime = 1000.0 / currentConfig.targetFPS;
        double dropRate = metrics.getDropRate();
        
        // Si el rendimiento está por debajo del objetivo
        if (avgFrameTime > targetFrameTime * 1.3 || dropRate > 10.0) {
            // Reducir calidad o resolución
            Log.d(TAG, "Rendimiento bajo detectado, optimizando...");
            // Implementar lógica de optimización automática
        }
    }
    
    private String configToString(PerformanceConfig config) {
        return String.format("Res:%dx%d, FPS:%d, Threads:%d, Quality:%d%%, Adaptive:%s",
            config.maxResolutionWidth, config.maxResolutionHeight,
            config.targetFPS, config.processingThreads,
            config.initialQuality, config.useAdaptiveQuality);
    }
    
    // Campos para almacenar la información del dispositivo calculada
    private int calculatedDeviceScore = 0;
    private String deviceInfoString = "";
    
    /**
     * Obtiene la puntuación calculada del dispositivo
     */
    public int getDeviceScore() {
        if (calculatedDeviceScore == 0) {
            calculateDeviceInfo();
        }
        return calculatedDeviceScore;
    }
    
    /**
     * Obtiene información detallada del dispositivo
     */
    public String getDeviceInfoString() {
        if (deviceInfoString.isEmpty()) {
            calculateDeviceInfo();
        }
        return deviceInfoString;
    }
    
    /**
     * Calcula y almacena la información del dispositivo
     */
    private void calculateDeviceInfo() {
        try {
            Log.d(TAG, "Calculando información del dispositivo...");
            
            int totalRAM = getTotalRAM();
            int cpuCores = Runtime.getRuntime().availableProcessors();
            int sdkVersion = Build.VERSION.SDK_INT;
            
            String cpuAbi = "unknown";
            String deviceModel = "Unknown";
            String manufacturer = "Unknown";
            
            try {
                cpuAbi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
                deviceModel = Build.MODEL != null ? Build.MODEL : "Unknown";
                manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "Unknown";
            } catch (Exception e) {
                Log.w(TAG, "Error obteniendo información básica del dispositivo", e);
            }
            
            // Calcular puntuación
            int score = 0;
            
            // RAM (40% del peso)
            if (totalRAM >= 6000) score += 40;
            else if (totalRAM >= 4000) score += 30;
            else if (totalRAM >= 3000) score += 20;
            else if (totalRAM >= 2000) score += 10;
            
            // CPU Cores (30% del peso)
            if (cpuCores >= 8) score += 30;
            else if (cpuCores >= 6) score += 25;
            else if (cpuCores >= 4) score += 20;
            else if (cpuCores >= 2) score += 10;
            
            // Android Version (20% del peso)
            if (sdkVersion >= 30) score += 20; // Android 11+
            else if (sdkVersion >= 28) score += 15; // Android 9+
            else if (sdkVersion >= 26) score += 10; // Android 8+
            else if (sdkVersion >= 23) score += 5;  // Android 6+
            
            // Arquitectura (10% del peso)
            if (cpuAbi.contains("arm64")) score += 10;
            else if (cpuAbi.contains("armeabi-v7a")) score += 5;
            
            this.calculatedDeviceScore = score;
            
            // Crear string informativo
            StringBuilder info = new StringBuilder();
            info.append("📱 ").append(manufacturer).append(" ").append(deviceModel).append("\n");
            info.append("💾 RAM: ").append(totalRAM).append(" MB\n");
            info.append("🔧 CPU: ").append(cpuCores).append(" cores (").append(cpuAbi).append(")\n");
            info.append("🤖 Android ").append(getAndroidVersionName(sdkVersion)).append(" (API ").append(sdkVersion).append(")");
            
            this.deviceInfoString = info.toString();
            
            Log.d(TAG, "Información del dispositivo calculada: Score=" + score + ", RAM=" + totalRAM + "MB, Cores=" + cpuCores);
            
        } catch (Exception e) {
            Log.e(TAG, "Error crítico calculando información del dispositivo", e);
            this.calculatedDeviceScore = 30; // Puntuación por defecto
            this.deviceInfoString = "📱 Información del dispositivo no disponible\n💾 RAM: N/A\n🔧 CPU: N/A\n🤖 Android: N/A";
        }
    }
    
    /**
     * Convierte el nivel de API a nombre de Android
     */
    private String getAndroidVersionName(int apiLevel) {
        if (apiLevel >= 33) return "13+";
        if (apiLevel >= 32) return "12L";
        if (apiLevel >= 31) return "12";
        if (apiLevel >= 30) return "11";
        if (apiLevel >= 29) return "10";
        if (apiLevel >= 28) return "9";
        if (apiLevel >= 27) return "8.1";
        if (apiLevel >= 26) return "8.0";
        if (apiLevel >= 25) return "7.1";
        if (apiLevel >= 24) return "7.0";
        if (apiLevel >= 23) return "6.0";
        return "Anterior";
    }
}
