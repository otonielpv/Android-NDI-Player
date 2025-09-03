package ndiplayer.oto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import ndiplayer.oto.utils.FrameMetrics;
import ndiplayer.oto.utils.PerformanceManager;

/**
 * MainActivity ultra-optimizada para dispositivos de bajo rendimiento
 * Implementa arquitectura de servicios, gestión inteligente de memoria
 * y procesamiento asíncrono para máximo rendimiento
 */
public class MainActivityOptimized extends Activity {
    private static final String TAG = "NDIPlayerOptimized";
    
    // UI Components
    private LinearLayout mainLayout;
    private View uiContainer;
    private ImageView videoView;
    private TextView statusText;
    private TextView deviceInfoText;  // Agregado para poder actualizar dinámicamente
    private ListView sourcesList;

    // Native methods - Direct implementation
    private native boolean nativeInitializeNDI();
    private native boolean nativeStartDiscovery();
    private native int nativeGetSourceCount();
    private native String nativeGetSourceName(int index);
    private native boolean nativeConnectToSource(int index);
    private native void nativeDisconnect();
    private native boolean nativeIsConnected();
    private native Bitmap nativeGetFrame();
    private native boolean nativeHasFrame();
    private native String nativeGetConnectionInfo();
    private native int nativeGetFrameRate();
    private native void nativeShutdownNDI();

    static {
        System.loadLibrary("ndiplayer");
    }
    private Button searchButton;
    private Button performanceButton;
    private ArrayAdapter<String> sourcesAdapter;
    private ArrayList<String> sources;
    
    // Performance Management
    private PerformanceManager performanceManager;
    private PerformanceManager.PerformanceConfig currentConfig;
    
    // State Management
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isFullscreen = new AtomicBoolean(false);
    private volatile String connectedSource = "";
    private Thread frameThread;
    private int frameCheckCounter = 0;
    
    // UI Thread Handler
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    
    // Performance Stats
    private long lastStatsUpdate = 0;
    private TextView performanceStats;
    
    // Función auxiliar para mostrar texto de clase de dispositivo
    private String getDeviceClassText(PerformanceManager.DeviceClass deviceClass) {
        switch (deviceClass) {
            case HIGH_END: return "🔥 ALTA GAMA";
            case MID_RANGE: return "⚖️ GAMA MEDIA";
            case LOW_END: return "🐌 GAMA BAJA";
            default: return "❓ DESCONOCIDO";
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "=== NDI Player Optimizado Iniciando ===");
        
        try {
            // Inicializar gestor de rendimiento
            performanceManager = new PerformanceManager(this);
            performanceManager.loadSavedConfig();
            currentConfig = performanceManager.getCurrentConfig();
            
            // Configurar UI optimizada
            setupOptimizedUI();
            
            // Inicializar NDI directamente (sin servicios)
            initializeNDI();
            
            // Mostrar información de red
            showNetworkInfo();
            
            Log.d(TAG, "MainActivity optimizada iniciada correctamente");
            
        } catch (Exception e) {
            Log.e(TAG, "Error crítico en onCreate", e);
            showErrorFallback(e);
        }
    }
    
    private void initializeNDI() {
        if (nativeInitializeNDI()) {
            statusText.setText("✅ NDI inicializado - Toca 'Buscar Fuentes'");
            searchButton.setEnabled(true);
            Log.d(TAG, "NDI inicializado exitosamente");
        } else {
            statusText.setText("❌ Error inicializando NDI");
            Log.e(TAG, "Error inicializando NDI");
        }
    }
    
    private void setupOptimizedUI() {
        // Layout principal optimizado
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        
        // UI Container para discovery
        uiContainer = createDiscoveryUI();
        mainLayout.addView(uiContainer);
        
        // Video view optimizado
        videoView = new ImageView(this);
        videoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        videoView.setBackgroundColor(Color.BLACK);
        videoView.setVisibility(View.GONE);
        
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.MATCH_PARENT);
        mainLayout.addView(videoView, videoParams);
        
        setContentView(mainLayout);
    }
    
    private View createDiscoveryUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        // Título con información de rendimiento
        TextView titleText = new TextView(this);
        titleText.setText("🎥 NDI Player Ultra-Optimizado");
        titleText.setTextSize(18);
        titleText.setPadding(0, 0, 0, 15);
        
        // Información del dispositivo
        deviceInfoText = new TextView(this);
        PerformanceManager.DeviceClass deviceClass = performanceManager.getDeviceClass();
        int deviceScore = performanceManager.getDeviceScore();
        deviceInfoText.setText(String.format("📱 %s (%d/100) | 📺 %dx%d @ %d FPS | 🔧 %d threads", 
                          getDeviceClassText(deviceClass), deviceScore,
                          currentConfig.maxResolutionWidth, currentConfig.maxResolutionHeight,
                          currentConfig.targetFPS, currentConfig.processingThreads));
        deviceInfoText.setTextSize(12);
        deviceInfoText.setPadding(0, 0, 0, 10);
        
        // Status text
        statusText = new TextView(this);
        statusText.setText("Inicializando servicios optimizados...");
        statusText.setTextSize(14);
        statusText.setPadding(0, 0, 0, 15);
        
        // Lista de fuentes
        sources = new ArrayList<>();
        sourcesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, sources);
        sourcesList = new ListView(this);
        sourcesList.setAdapter(sourcesAdapter);
        sourcesList.setOnItemClickListener(this::onSourceSelected);
        
        // Botones de control
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        searchButton = new Button(this);
        searchButton.setText("🔍 Buscar Fuentes");
        searchButton.setOnClickListener(v -> startDiscovery());
        
        performanceButton = new Button(this);
        performanceButton.setText("⚙️ Rendimiento");
        performanceButton.setOnClickListener(v -> showPerformanceSettings());
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttonParams.setMargins(5, 0, 5, 0);
        
        buttonLayout.addView(searchButton, buttonParams);
        buttonLayout.addView(performanceButton, buttonParams);
        
        // Stats de rendimiento en tiempo real
        performanceStats = new TextView(this);
        performanceStats.setTextSize(10);
        performanceStats.setVisibility(View.GONE);
        
        // Inicializar estadísticas de rendimiento
        updatePerformanceStats();
        
        // Ensamblar layout
        layout.addView(titleText);
        layout.addView(deviceInfoText);
        layout.addView(statusText);
        layout.addView(sourcesList, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        layout.addView(buttonLayout);
        layout.addView(performanceStats);
        
        return layout;
    }
    
    private void startDiscovery() {
        searchButton.setEnabled(false);
        statusText.setText("🔍 Buscando fuentes NDI...");
        sources.clear();
        sourcesAdapter.notifyDataSetChanged();
        
        // Usar función nativa directamente
        new Thread(() -> {
            if (nativeStartDiscovery()) {
                // Esperar un poco para que la búsqueda se complete
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                
                int sourceCount = nativeGetSourceCount();
                
                uiHandler.post(() -> {
                    sources.clear();
                    
                    if (sourceCount > 0) {
                        for (int i = 0; i < sourceCount; i++) {
                            String sourceName = nativeGetSourceName(i);
                            if (sourceName != null && !sourceName.equals("ERROR")) {
                                sources.add("📡 " + sourceName);
                            }
                        }
                        statusText.setText("✅ Encontradas " + sourceCount + " fuentes");
                    } else {
                        sources.add("⚠️ No se encontraron fuentes NDI");
                        sources.add("• Verifica que haya fuentes activas en la red");
                        sources.add("• Comprueba la conectividad WiFi");
                        statusText.setText("⚠️ Sin fuentes encontradas");
                    }
                    
                    sourcesAdapter.notifyDataSetChanged();
                    searchButton.setEnabled(true);
                });
            } else {
                uiHandler.post(() -> {
                    statusText.setText("❌ Error iniciando búsqueda NDI");
                    searchButton.setEnabled(true);
                });
            }
        }).start();
    }
    
    private void onSourceSelected(AdapterView<?> parent, View view, int position, long id) {
        String selectedItem = sources.get(position);
        
        if (!selectedItem.startsWith("📡 ")) {
            return; // No es una fuente válida
        }
        
        String sourceName = selectedItem.substring(2).trim();
        connectToSource(sourceName);
    }
    
    private void connectToSource(String sourceName) {
        if (isConnected.get()) {
            Toast.makeText(this, "Ya hay una conexión activa", Toast.LENGTH_SHORT).show();
            return;
        }
        
        statusText.setText("🔗 Conectando a: " + sourceName);
        
        // Buscar el índice de la fuente
        new Thread(() -> {
            int sourceIndex = -1;
            int sourceCount = nativeGetSourceCount();
            
            for (int i = 0; i < sourceCount; i++) {
                String name = nativeGetSourceName(i);
                if (name != null && name.equals(sourceName)) {
                    sourceIndex = i;
                    break;
                }
            }
            
            if (sourceIndex >= 0) {
                if (nativeConnectToSource(sourceIndex)) {
                    uiHandler.post(() -> {
                        isConnected.set(true);
                        connectedSource = sourceName;
                        statusText.setText("✅ Conectado a: " + sourceName);
                        
                        // Entrar en modo fullscreen
                        uiHandler.postDelayed(() -> {
                            if (isConnected.get()) {
                                enterFullscreen();
                                startFrameCapture();
                            }
                        }, 1000);
                    });
                } else {
                    uiHandler.post(() -> {
                        statusText.setText("❌ Error conectando a: " + sourceName);
                    });
                }
            } else {
                uiHandler.post(() -> {
                    statusText.setText("❌ Fuente no encontrada: " + sourceName);
                });
            }
        }).start();
    }
    
    private void startFrameCapture() {
        Log.d(TAG, "Iniciando hilo de captura de frames");
        frameThread = new Thread(() -> {
            Log.d(TAG, "Hilo de captura iniciado, isConnected=" + isConnected.get());
            while (isConnected.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Bitmap frame = nativeGetFrame();
                    if (frame != null) {
                        // Log solo cada 300 frames para ver el formato sin spam
                        frameCheckCounter++;
                        if (frameCheckCounter % 300 == 0) {
                            Log.d(TAG, "Frame #" + frameCheckCounter + " obtenido: " + frame.getWidth() + "x" + frame.getHeight());
                        }
                        uiHandler.post(() -> {
                            if (videoView != null && isConnected.get()) {
                                videoView.setImageBitmap(frame);
                            }
                        });
                    }
                    Thread.sleep(16); // ~60fps max
                } catch (InterruptedException e) {
                    Log.d(TAG, "Hilo de captura interrumpido");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error en captura de frames", e);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            Log.d(TAG, "Hilo de captura terminado");
        }, "FrameCaptureThread");
        frameThread.start();
    }
    
    private void updatePerformanceStats(FrameMetrics metrics) {
        long now = System.currentTimeMillis();
        if (now - lastStatsUpdate > 1000) { // Actualizar cada segundo
            lastStatsUpdate = now;
            
            if (performanceStats.getVisibility() == View.VISIBLE) {
                String stats = String.format("FPS: %d | Proc: %.1fms | Drop: %.1f%% | Calidad: %s",
                    metrics.getCurrentFPS(),
                    metrics.getRecentAverageProcessingTime(),
                    metrics.getDropRate(),
                    currentConfig.useAdaptiveQuality ? "AUTO" : currentConfig.initialQuality + "%"
                );
                performanceStats.setText(stats);
            }
        }
    }
    
    private void enterFullscreen() {
        if (isFullscreen.compareAndSet(false, true)) {
            try {
                // Ocultar UI y mostrar video
                uiContainer.setVisibility(View.GONE);
                videoView.setVisibility(View.VISIBLE);
                
                // Remover padding
                mainLayout.setPadding(0, 0, 0, 0);
                
                // Fullscreen flags
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
                
                // Mostrar stats si está en modo debug
                if (performanceManager.getDeviceClass() == PerformanceManager.DeviceClass.LOW_END) {
                    performanceStats.setVisibility(View.VISIBLE);
                }
                
                Toast.makeText(this, "Pantalla completa - Toca atrás para salir", Toast.LENGTH_LONG).show();
                
            } catch (Exception e) {
                Log.e(TAG, "Error entrando en fullscreen", e);
                isFullscreen.set(false);
            }
        }
    }
    
    private void exitFullscreen() {
        if (isFullscreen.compareAndSet(true, false)) {
            try {
                // Mostrar UI del sistema
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                
                // Mostrar UI de discovery
                uiContainer.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                performanceStats.setVisibility(View.GONE);
                
                // Restaurar padding
                mainLayout.setPadding(20, 20, 20, 20);
                
            } catch (Exception e) {
                Log.e(TAG, "Error saliendo de fullscreen", e);
            }
        }
    }
    
    private void showPerformanceSettings() {
        Log.d(TAG, "Mostrando configuración de rendimiento...");
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📱 Configuración de Rendimiento");
            
            // Crear mensaje simple primero
            StringBuilder message = new StringBuilder();
            message.append("⚙️ Configuración Actual:\n");
            message.append("📺 Resolución: ").append(currentConfig.maxResolutionWidth).append("x").append(currentConfig.maxResolutionHeight).append("\n");
            message.append("🎬 FPS Objetivo: ").append(currentConfig.targetFPS).append("\n\n");
            
            // Intentar obtener información adicional
            try {
                PerformanceManager.DeviceClass deviceClass = performanceManager.getDeviceClass();
                int deviceScore = performanceManager.getDeviceScore();
                String deviceInfo = performanceManager.getDeviceInfoString();
                
                message.insert(0, "🔧 INFORMACIÓN DEL DISPOSITIVO:\n" + deviceInfo + "\n" +
                              "📊 Puntuación: " + deviceScore + "/100\n" +
                              "🏷️ Categoría: " + getDeviceClassText(deviceClass) + "\n\n");
                
                Log.d(TAG, "Información del dispositivo obtenida correctamente");
            } catch (Exception e) {
                Log.w(TAG, "Error obteniendo información del dispositivo, usando información básica", e);
                message.insert(0, "� INFORMACIÓN DEL DISPOSITIVO:\n" +
                              "📱 Información del dispositivo no disponible\n\n");
            }
            
            
            // SIN setMessage() para evitar conflicto con setItems()
            // builder.setMessage(message.toString());
            
            String[] options = {
                "🐌 Calidad Baja (640x360 @ 15 FPS)",
                "⚖️ Calidad Media (854x480 @ 25 FPS)", 
                "🔥 Calidad Alta (1280x720 @ 30 FPS)",
                "🤖 Configuración Automática",
                "📊 Mostrar/Ocultar estadísticas"
            };
            
            builder.setItems(options, (dialog, which) -> {
                Log.d(TAG, "Opción seleccionada: " + which + " (" + options[which] + ")");
                try {
                    switch (which) {
                        case 0: // Baja calidad
                            applyLowQualitySettings();
                            break;
                        case 1: // Media calidad
                            applyMediumQualitySettings();
                            break;
                        case 2: // Alta calidad
                            applyHighQualitySettings();
                            break;
                        case 3: // Automático
                            Log.d(TAG, "Restaurando configuración automática...");
                            performanceManager.restoreAutoConfig();
                            currentConfig = performanceManager.getCurrentConfig();
                            updatePerformanceStats();
                            Toast.makeText(this, "🤖 Configuración automática restaurada", Toast.LENGTH_SHORT).show();
                            break;
                        case 4: // Toggle stats
                            togglePerformanceStats();
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error aplicando configuración seleccionada: " + which, e);
                    Toast.makeText(this, "Error aplicando configuración: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            
            builder.setNegativeButton("Cerrar", (dialog, which) -> {
                Log.d(TAG, "Diálogo de configuración cerrado");
            });
            
            AlertDialog dialog = builder.create();
            dialog.show();
            
            Log.d(TAG, "Diálogo de configuración mostrado correctamente");
            
        } catch (Exception e) {
            Log.e(TAG, "Error crítico mostrando configuración de rendimiento", e);
            Toast.makeText(this, "Error mostrando configuración: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Fallback: mostrar diálogo simple
            showSimplePerformanceSettings();
        }
    }
    
    private void showSimplePerformanceSettings() {
        Log.d(TAG, "Mostrando configuración simple de emergencia...");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Configuración de Rendimiento");
        builder.setMessage("Configuración Actual: " + currentConfig.maxResolutionWidth + "x" + currentConfig.maxResolutionHeight + " @ " + currentConfig.targetFPS + " FPS");
        
        String[] options = {
            "Baja (640x360 @ 15 FPS)",
            "Media (854x480 @ 25 FPS)", 
            "Alta (1280x720 @ 30 FPS)"
        };
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: applyLowQualitySettings(); break;
                case 1: applyMediumQualitySettings(); break;
                case 2: applyHighQualitySettings(); break;
            }
        });
        
        builder.setNegativeButton("Cerrar", null);
        builder.show();
    }
    
    private void applyLowQualitySettings() {
        Log.d(TAG, "Aplicando configuración de baja calidad...");
        PerformanceManager.PerformanceConfig lowConfig = 
            new PerformanceManager.PerformanceConfig(640, 360, 15, 2, 1, true, 50, true);
        performanceManager.applyCustomConfig(lowConfig);
        currentConfig = lowConfig;
        updatePerformanceStats();
        Log.d(TAG, "Configuración de baja calidad aplicada: " + lowConfig.maxResolutionWidth + "x" + lowConfig.maxResolutionHeight);
        Toast.makeText(this, "🐌 Configuración de baja calidad aplicada", Toast.LENGTH_SHORT).show();
    }
    
    private void applyMediumQualitySettings() {
        Log.d(TAG, "Aplicando configuración de media calidad...");
        PerformanceManager.PerformanceConfig medConfig = 
            new PerformanceManager.PerformanceConfig(854, 480, 25, 3, 2, true, 75, true);
        performanceManager.applyCustomConfig(medConfig);
        currentConfig = medConfig;
        updatePerformanceStats();
        Log.d(TAG, "Configuración de media calidad aplicada: " + medConfig.maxResolutionWidth + "x" + medConfig.maxResolutionHeight);
        Toast.makeText(this, "⚖️ Configuración de media calidad aplicada", Toast.LENGTH_SHORT).show();
    }
    
    private void applyHighQualitySettings() {
        Log.d(TAG, "Aplicando configuración de alta calidad...");
        PerformanceManager.PerformanceConfig highConfig = 
            new PerformanceManager.PerformanceConfig(1280, 720, 30, 4, 3, false, 100, false);
        performanceManager.applyCustomConfig(highConfig);
        currentConfig = highConfig;
        updatePerformanceStats();
        Log.d(TAG, "Configuración de alta calidad aplicada: " + highConfig.maxResolutionWidth + "x" + highConfig.maxResolutionHeight);
        Toast.makeText(this, "🔥 Configuración de alta calidad aplicada", Toast.LENGTH_SHORT).show();
    }
    
    private void updatePerformanceStats() {
        Log.d(TAG, "Actualizando estadísticas de rendimiento...");
        
        try {
            PerformanceManager.DeviceClass deviceClass = performanceManager.getDeviceClass();
            int deviceScore = performanceManager.getDeviceScore();
            
            // Actualizar información principal del dispositivo
            if (deviceInfoText != null) {
                deviceInfoText.setText(String.format("📱 %s (%d/100) | 📺 %dx%d @ %d FPS | 🔧 %d threads", 
                              getDeviceClassText(deviceClass), deviceScore,
                              currentConfig.maxResolutionWidth, currentConfig.maxResolutionHeight,
                              currentConfig.targetFPS, currentConfig.processingThreads));
                Log.d(TAG, "Información de dispositivo actualizada");
            }
            
            // Actualizar estadísticas detalladas
            if (performanceStats != null) {
                String stats = String.format(
                    "📱 %s (%d/100)\n" +
                    "📺 %dx%d @ %d FPS\n" +
                    "💾 Buffer: %d | 🔄 Threads: %d\n" +
                    "🎯 Calidad: %d%% | 🚀 Adaptativa: %s",
                    getDeviceClassText(deviceClass), deviceScore,
                    currentConfig.maxResolutionWidth, currentConfig.maxResolutionHeight, currentConfig.targetFPS,
                    currentConfig.frameBufferSize, currentConfig.processingThreads,
                    currentConfig.initialQuality, currentConfig.useAdaptiveQuality ? "SÍ" : "NO"
                );
                
                performanceStats.setText(stats);
                Log.d(TAG, "Estadísticas detalladas actualizadas: " + currentConfig.maxResolutionWidth + "x" + currentConfig.maxResolutionHeight);
            } else {
                Log.w(TAG, "performanceStats es null, no se pueden actualizar");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando estadísticas de rendimiento", e);
        }
    }
    
    private void togglePerformanceStats() {
        if (performanceStats.getVisibility() == View.VISIBLE) {
            performanceStats.setVisibility(View.GONE);
        } else {
            performanceStats.setVisibility(View.VISIBLE);
        }
    }
    
    private void showNetworkInfo() {
        try {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();
                int ipAddress = wifiInfo.getIpAddress();
                String ipString = String.format("%d.%d.%d.%d", 
                    (ipAddress & 0xff), (ipAddress >> 8 & 0xff), 
                    (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
                    
                Log.d(TAG, "Red: " + ssid + " | IP: " + ipString);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo info de red", e);
        }
    }
    
    private void showErrorFallback(Exception e) {
        LinearLayout errorLayout = new LinearLayout(this);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setPadding(30, 30, 30, 30);
        
        TextView errorTitle = new TextView(this);
        errorTitle.setText("❌ Error Crítico");
        errorTitle.setTextSize(20);
        
        TextView errorMessage = new TextView(this);
        errorMessage.setText("Error: " + e.getMessage());
        errorMessage.setTextSize(14);
        
        errorLayout.addView(errorTitle);
        errorLayout.addView(errorMessage);
        
        setContentView(errorLayout);
    }
    
    @Override
    public void onBackPressed() {
        if (isFullscreen.get()) {
            exitFullscreen();
            if (isConnected.get()) {
                disconnectSource();
            }
        } else if (isConnected.get()) {
            disconnectSource();
            super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }
    
    private void disconnectSource() {
        if (isConnected.get()) {
            isConnected.set(false);
            
            // Detener thread de captura
            if (frameThread != null) {
                frameThread.interrupt();
                try {
                    frameThread.join(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Timeout esperando thread de captura");
                }
                frameThread = null;
            }
            
            // Desconectar nativo
            nativeDisconnect();
            
            uiHandler.post(() -> {
                if (videoView != null) {
                    videoView.setImageBitmap(null);
                }
                statusText.setText("🔌 Desconectado");
                connectedSource = null;
            });
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Orientación cambiada: " + 
            (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "Horizontal" : "Vertical"));
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "Destruyendo MainActivity optimizada");
        
        // Desconectar fuente si está conectada
        disconnectSource();
        
        // Cleanup nativo
        nativeShutdownNDI();
        
        super.onDestroy();
    }
    
    static {
        try {
            System.loadLibrary("ndiplayer");
            Log.d("NDIPlayerOptimized", "Librería nativa cargada");
        } catch (UnsatisfiedLinkError e) {
            Log.e("NDIPlayerOptimized", "Error cargando librería nativa", e);
        }
    }
}
