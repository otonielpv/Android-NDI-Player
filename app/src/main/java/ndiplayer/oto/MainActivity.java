package ndiplayer.oto;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsetsController;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final String TAG = "NDIPlayer";
    
    // NDI FourCC constants
    private static final int NDI_FOURCC_UYVY = ('U' << 0) | ('Y' << 8) | ('V' << 16) | ('Y' << 24);
    private static final int NDI_FOURCC_BGRA = ('B' << 0) | ('G' << 8) | ('R' << 16) | ('A' << 24);
    private static final int NDI_FOURCC_RGBA = ('R' << 0) | ('G' << 8) | ('B' << 16) | ('A' << 24);
    private static final int NDI_FOURCC_BGRX = ('B' << 0) | ('G' << 8) | ('R' << 16) | ('X' << 24);
    
    private ListView ndiSourcesList;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> ndiSources;
    private TextView statusText;
    
    // Connection state variables
    private boolean isConnected = false;
    private String connectedSourceName = "";
    private Thread captureThread;
    private ImageView videoView;
    
    // UI state variables
    private boolean isFullscreen = false;
    private LinearLayout mainLayout;
    private View uiContainer;

    // Native library loading
    static {
        try {
            System.loadLibrary("ndiplayer");
            Log.d("NDIPlayer", "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e("NDIPlayer", "Failed to load native library", e);
        }
    }

    // Native methods
    private native boolean nativeInitializeNDI();
    private native String[] nativeGetSources();
    private native boolean nativeStartDiscovery();
    private native String nativeGetNDIVersion();
    
    // New native methods for receiver functionality
    private native boolean nativeConnectToSource(String sourceName, String sourceUrl);
    private native boolean nativeDisconnect();
    private native int nativeCaptureFrame(int[] widthHeight, int timeoutMs);
    private native String nativeGetConnectionStatus();
    private native void nativeCleanup();
    private native byte[] nativeGetFrameData();
    private native int nativeGetFrameFourCC();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "MainActivity onCreate started - NDI FULLSCREEN VERSION");
        
        try {
            // Layout principal
            mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(30, 30, 30, 30);
            
            // Contenedor para UI de discovery
            uiContainer = createDiscoveryUI();
            mainLayout.addView(uiContainer);
            
            // Video view (inicialmente oculto)
            videoView = new ImageView(this);
            videoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            videoView.setBackgroundColor(Color.BLACK);
            videoView.setVisibility(View.GONE); // Oculto inicialmente
            LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            mainLayout.addView(videoView, videoParams);
            
            setContentView(mainLayout);
            
            // Inicializar NDI SDK automáticamente
            initializeNDI();
            
            // Mostrar información de red
            showNetworkInfo();
            
            Log.d(TAG, "MainActivity onCreate completed - NDI FULLSCREEN VERSION");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in NDI discovery onCreate", e);
            // Fallback a ultra minimal
            TextView errorText = new TextView(this);
            errorText.setText("Error: " + e.getMessage());
            setContentView(errorText);
        }
    }
    
    private void initializeNDI() {
        Log.d(TAG, "Initializing NDI SDK...");
        
        new Thread(() -> {
            try {
                boolean success = nativeInitializeNDI();
                
                runOnUiThread(() -> {
                    if (success) {
                        statusText.setText("✅ NDI SDK inicializado - Buscando fuentes...");
                        Log.d(TAG, "NDI SDK initialized successfully - Starting automatic discovery");
                        
                        // Automáticamente iniciar búsqueda de fuentes NDI
                        performNDIDiscovery();
                        
                    } else {
                        statusText.setText("⚠️ NDI SDK en modo seguro");
                        Log.w(TAG, "NDI SDK in safe mode");
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error initializing NDI SDK", e);
                runOnUiThread(() -> {
                    statusText.setText("❌ Error inicializando NDI SDK");
                });
            }
        }).start();
    }
    
    private void performNDIDiscovery() {
        Log.d(TAG, "Starting NDI discovery...");
        statusText.setText("🔍 Buscando fuentes NDI...");
        
        new Thread(() -> {
            try {
                // Limpiar fuentes anteriores
                runOnUiThread(() -> {
                    ndiSources.clear();
                    adapter.notifyDataSetChanged();
                });
                
                // Múltiples intentos de discovery (NDI a veces necesita varios intentos)
                // Basado en el programa de prueba exitoso: primer intento puede no encontrar nada
                for (int attempt = 1; attempt <= 8; attempt++) { // Aumentamos a 8 intentos
                    final int currentAttempt = attempt; // Variable final para lambda
                    Log.d(TAG, "NDI Discovery attempt " + currentAttempt + "/8");
                    
                    runOnUiThread(() -> {
                        statusText.setText("🔍 Intento " + currentAttempt + "/8 - Buscando fuentes NDI...");
                    });
                    
                    // Iniciar discovery nativo
                    boolean discoveryStarted = nativeStartDiscovery();
                    Log.d(TAG, "Native discovery started (attempt " + currentAttempt + "): " + discoveryStarted);
                    
                    if (!discoveryStarted) {
                        Log.w(TAG, "Failed to start discovery on attempt " + currentAttempt);
                        continue;
                    }
                    
                    // Esperar más tiempo para el discovery (NDI necesita tiempo)
                    Log.d(TAG, "Waiting for NDI sources... (6 seconds)");
                    Thread.sleep(6000);  // 6 segundos por intento (más tiempo)
                    
                    // Obtener fuentes encontradas
                    Log.d(TAG, "About to call nativeGetSources()...");
                    String[] sources = null;
                    try {
                        sources = nativeGetSources();
                        Log.d(TAG, "nativeGetSources() completed successfully");
                        Log.d(TAG, "nativeGetSources() returned: " + (sources != null ? "array with " + sources.length + " elements" : "null"));
                        
                        if (sources != null && sources.length > 0) {
                            Log.d(TAG, "Sources found:");
                            for (int i = 0; i < sources.length; i++) {
                                Log.d(TAG, "  [" + i + "] = " + sources[i]);
                            }
                        }
                        
                        Log.d(TAG, "Native sources found (attempt " + currentAttempt + "): " + (sources != null ? sources.length : 0));
                    } catch (Exception e) {
                        Log.e(TAG, "Exception calling nativeGetSources(): " + e.getMessage(), e);
                        sources = null; // Ensure sources is null on exception
                    }
                    
                    // Check if we should continue to next attempt
                    if (sources == null) {
                        if (currentAttempt < 8) {
                            Log.d(TAG, "Exception occurred on attempt " + currentAttempt + ", trying again...");
                            Thread.sleep(2000);
                        }
                        continue; // Skip to next attempt
                    }
                    
                    if (sources != null && sources.length > 0) {
                        // ¡Encontramos fuentes!
                        final int foundCount = sources.length;
                        final String[] finalSources = sources; // Variable final para lambda
                        runOnUiThread(() -> {
                            ndiSources.clear();
                            for (String source : finalSources) {
                                ndiSources.add("📡 " + source);
                            }
                            statusText.setText("✅ Encontradas " + foundCount + " fuentes NDI");
                            adapter.notifyDataSetChanged();
                            Log.d(TAG, "Found " + foundCount + " NDI sources on attempt " + currentAttempt);
                        });
                        return; // Salir si encontramos fuentes
                    }
                    
                    // No encontramos fuentes en este intento
                    if (currentAttempt < 8) {
                        Log.d(TAG, "No sources found on attempt " + currentAttempt + ", trying again...");
                        Thread.sleep(2000); // Pausa más larga entre intentos
                    }
                }
                
                // No se encontraron fuentes después de todos los intentos
                runOnUiThread(() -> {
                    ndiSources.clear();
                    ndiSources.add("⚠️ Sin fuentes NDI encontradas tras 8 intentos");
                    ndiSources.add("📝 PC-OTO (FreeShow NDI - PROYECCION) debería aparecer");
                    ndiSources.add("🔧 Posibles problemas:");
                    ndiSources.add("   • Dispositivos en redes diferentes");
                    ndiSources.add("   • Firewall bloqueando multicast");
                    ndiSources.add("   • FreeShow NDI no visible en red");
                    statusText.setText("⚠️ Sin fuentes NDI tras 8 intentos");
                    adapter.notifyDataSetChanged();
                    Log.d(TAG, "No NDI sources found after 8 attempts");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during NDI discovery", e);
                runOnUiThread(() -> {
                    ndiSources.clear();
                    ndiSources.add("❌ Error en discovery: " + e.getMessage());
                    statusText.setText("❌ Error en búsqueda NDI");
                    adapter.notifyDataSetChanged();
                });
            }
        }).start();
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
                    
                Log.d(TAG, "Network Info - SSID: " + ssid + ", IP: " + ipString);
                Log.d(TAG, "This will help diagnose NDI discovery issues");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting network info", e);
        }
    }

    // Crear UI de discovery y conexión
    private View createDiscoveryUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        TextView titleText = new TextView(this);
        titleText.setText("NDI Player - Rota el dispositivo manualmente para horizontal");
        titleText.setTextSize(20);
        titleText.setPadding(0, 0, 0, 20);
        
        statusText = new TextView(this);
        statusText.setText("Inicializando NDI SDK...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 15);
        
        // Inicializar lista vacía
        ndiSources = new ArrayList<>();
        
        // Crear ListView
        ndiSourcesList = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ndiSources);
        ndiSourcesList.setAdapter(adapter);
        
        // Configurar click en elementos de la lista para conectar automáticamente
        ndiSourcesList.setOnItemClickListener((parent, view, position, id) -> {
            String selectedSource = ndiSources.get(position);
            Log.d(TAG, "Selected NDI source: " + selectedSource);
            Toast.makeText(this, "Conectando a: " + selectedSource, Toast.LENGTH_SHORT).show();
            
            // Auto-connect to selected source
            connectToSelectedSource(selectedSource);
        });
        
        // Añadir vistas al layout
        titleText.setText("🎥 NDI Player");
        layout.addView(titleText);
        layout.addView(statusText);
        layout.addView(ndiSourcesList, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)); // weight=1 para que ocupe el espacio
        
        return layout;
    }

    // Entrar en modo pantalla completa
    private void enterFullscreen() {
        Log.d(TAG, "Entering fullscreen mode");
        
        try {
            // Primero ocultar UI de discovery y mostrar video
            if (uiContainer != null) {
                uiContainer.setVisibility(View.GONE);
            }
            if (videoView != null) {
                videoView.setVisibility(View.VISIBLE);
                
                // Configurar video sin padding ni margin para quitar bordes
                LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.MATCH_PARENT
                );
                videoParams.setMargins(0, 0, 0, 0); // Sin márgenes
                videoView.setLayoutParams(videoParams);
                videoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                videoView.setPadding(0, 0, 0, 0); // Sin padding
            }
            
            // Quitar padding del layout principal para eliminar bordes
            if (mainLayout != null) {
                mainLayout.setPadding(0, 0, 0, 0);
            }
            
            // Usar método legacy más compatible para ocultar UI del sistema
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            
            // NO cambiar orientación automáticamente para evitar crashes
            // El usuario puede cambiar manualmente la orientación del dispositivo
            
            isFullscreen = true;
            
            Toast.makeText(this, "Pantalla completa activada - Rota el dispositivo manualmente", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error entering fullscreen mode", e);
            Toast.makeText(this, "Error al entrar en pantalla completa", Toast.LENGTH_SHORT).show();
        }
    }

    // Salir del modo pantalla completa
    private void exitFullscreen() {
        Log.d(TAG, "Exiting fullscreen mode");
        
        try {
            // NO cambiar orientación automáticamente para evitar crashes
            // setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            
            // Mostrar barra de estado y navegación usando método legacy
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            
            // Mostrar UI de discovery y ocultar video
            if (uiContainer != null) {
                uiContainer.setVisibility(View.VISIBLE);
            }
            if (videoView != null) {
                videoView.setVisibility(View.GONE); // Ocultar video en modo normal
            }
            
            // Restaurar padding del layout principal
            if (mainLayout != null) {
                mainLayout.setPadding(30, 30, 30, 30);
            }
            
            isFullscreen = false;
            
            Toast.makeText(this, "Volviendo al menú principal", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error exiting fullscreen mode", e);
            Toast.makeText(this, "Error al salir de pantalla completa", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart() called");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume() called");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause() called");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() called");
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            // Si estamos en pantalla completa, salir al modo normal y desconectar
            exitFullscreen();
            if (isConnected) {
                Log.d(TAG, "Exiting fullscreen - disconnecting from NDI source");
                disconnectFromSource();
            }
        } else if (isConnected) {
            // Si estamos conectados pero no en pantalla completa, desconectar
            Log.d(TAG, "Back pressed - disconnecting from NDI source");
            disconnectFromSource();
            super.onBackPressed(); // Cerrar la app después de desconectar
        } else {
            // Si no estamos conectados, comportamiento normal (cerrar app)
            super.onBackPressed();
        }
    }
    
    // =============================================
    // NDI CONNECTION AND PLAYBACK METHODS
    // =============================================
    
    private String selectedSourceName = "";
    private String selectedSourceUrl = "";
    
    private void connectToSelectedSource(String selectedSource) {
        Log.d(TAG, "Attempting to connect to: " + selectedSource);
        
        if (selectedSource == null || selectedSource.isEmpty()) {
            Toast.makeText(this, "No se ha seleccionado ninguna fuente", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isConnected) {
            Toast.makeText(this, "Ya hay una conexión activa. Desconecta primero.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Remove emoji prefix if present
        String cleanSource = selectedSource;
        if (cleanSource.startsWith("📡 ")) {
            cleanSource = cleanSource.substring(2).trim(); // Add trim to remove extra spaces
        }
        
        // For NDI sources, the full name IS the source name, no parsing needed
        selectedSourceName = cleanSource;
        selectedSourceUrl = ""; // NDI URL will be resolved automatically by the SDK
        
        Log.d(TAG, "Cleaned source name: '" + selectedSourceName + "'");
        
        new Thread(() -> {
            try {
                // Use the full source name as NDI name, empty URL lets SDK resolve it
                boolean success = nativeConnectToSource(selectedSourceName, selectedSourceUrl);
                
                runOnUiThread(() -> {
                    if (success) {
                        isConnected = true;
                        connectedSourceName = selectedSourceName;
                        
                        Toast.makeText(this, "✅ Conectado - Iniciando pantalla completa...", Toast.LENGTH_SHORT).show();
                        
                        // Start frame capture
                        startFrameCapture();
                        
                        // Auto-enter fullscreen after successful connection
                        new android.os.Handler().postDelayed(() -> {
                            if (isConnected && !isFullscreen) {
                                try {
                                    enterFullscreen();
                                } catch (Exception e) {
                                    Log.e(TAG, "Error entering fullscreen from handler", e);
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "Error al entrar en pantalla completa", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }
                        }, 1000); // Wait 1 second for video to start
                        
                    } else {
                        Toast.makeText(this, "❌ Error al conectar", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during connection", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Excepción al conectar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void disconnectFromSource() {
        Log.d(TAG, "Disconnecting from NDI source");
        
        new Thread(() -> {
            try {
                // Stop frame capture thread
                stopFrameCapture();
                
                boolean success = nativeDisconnect();
                
                runOnUiThread(() -> {
                    // Exit fullscreen if we're in it
                    if (isFullscreen) {
                        exitFullscreen();
                    }
                    
                    isConnected = false;
                    connectedSourceName = "";
                    
                    if (success) {
                        Toast.makeText(this, "✅ Desconectado exitosamente", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "⚠️ Desconectado (con advertencias)", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during disconnection", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Error al desconectar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void startFrameCapture() {
        Log.d(TAG, "Starting frame capture thread");
        
        captureThread = new Thread(() -> {
            int frameCount = 0;
            int[] dimensions = new int[2]; // [width, height]
            
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    int result = nativeCaptureFrame(dimensions, 100); // 100ms timeout
                    
                    if (result == 1) { // Video frame received
                        frameCount++;
                        final int width = dimensions[0];
                        final int height = dimensions[1];
                        final int currentFrameCount = frameCount;
                        
                        // Get frame data from native code
                        byte[] frameData = nativeGetFrameData();
                        Log.d(TAG, String.format("Frame data: %s, length: %d", 
                                (frameData != null ? "received" : "null"), 
                                (frameData != null ? frameData.length : 0)));
                        
                        if (frameData != null && frameData.length > 0) {
                            // Create bitmap from frame data
                            Bitmap bitmap = createBitmapFromFrameData(frameData, width, height);
                            if (bitmap != null) {
                                runOnUiThread(() -> {
                                    // Update video display
                                    videoView.setImageBitmap(bitmap);
                                });
                                
                                if (currentFrameCount % 30 == 0) { // Log every 30 frames
                                    Log.d(TAG, String.format("Video frame #%d - %dx%d", currentFrameCount, width, height));
                                }
                            }
                        } else {
                            // Create test pattern when no frame data
                            Bitmap testBitmap = createTestPattern(width, height);
                            if (testBitmap != null) {
                                runOnUiThread(() -> {
                                    videoView.setImageBitmap(testBitmap);
                                });
                            }
                        }
                    } else if (result == 2) { // Audio frame received
                        // Handle audio frame if needed
                    } else if (result == 0) { // No frame
                        // Normal timeout, continue
                    } else { // Error
                        Log.w(TAG, "Frame capture error: " + result);
                        Thread.sleep(100); // Brief pause on error
                    }
                    
                } catch (InterruptedException e) {
                    Log.d(TAG, "Frame capture thread interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in frame capture", e);
                    try {
                        Thread.sleep(500); // Pause on error
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            
            Log.d(TAG, "Frame capture thread ended");
        });
        
        captureThread.start();
    }
    
    private void stopFrameCapture() {
        if (captureThread != null && captureThread.isAlive()) {
            Log.d(TAG, "Stopping frame capture thread");
            captureThread.interrupt();
            try {
                captureThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for capture thread to stop");
            }
            captureThread = null;
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() called - cleaning up NDI connections");
        
        // Stop frame capture and disconnect
        if (isConnected) {
            stopFrameCapture();
            try {
                nativeDisconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting in onDestroy", e);
            }
        }
        
        // Clean up all NDI resources
        try {
            nativeCleanup();
            Log.d(TAG, "NDI cleanup completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during NDI cleanup", e);
        }
        
        super.onDestroy();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration changed: " + newConfig.orientation);
        
        // No need to recreate the activity, just handle the orientation change gracefully
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "Changed to landscape orientation");
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d(TAG, "Changed to portrait orientation");
        }
    }
    
    private Bitmap createBitmapFromFrameData(byte[] frameData, int width, int height) {
        try {
            // Get the FourCC format of the current frame
            int fourCC = nativeGetFrameFourCC();
            Log.d(TAG, String.format("Converting frame: %dx%d, FourCC=0x%08X, data size=%d", 
                                    width, height, fourCC, frameData.length));
            
            // Handle different formats
            switch (fourCC) {
                case NDI_FOURCC_BGRA:
                    return createBitmapFromBGRA(frameData, width, height);
                case NDI_FOURCC_RGBA:
                    return createBitmapFromRGBA(frameData, width, height);
                case NDI_FOURCC_BGRX:
                    return createBitmapFromBGRX(frameData, width, height);
                case NDI_FOURCC_UYVY:
                    return createBitmapFromUYVY(frameData, width, height);
                default:
                    Log.w(TAG, String.format("Unsupported FourCC format: 0x%08X, trying BGRA fallback", fourCC));
                    return createBitmapFromBGRA(frameData, width, height);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating bitmap from frame data", e);
            return null;
        }
    }
    
    private Bitmap createBitmapFromBGRA(byte[] frameData, int width, int height) {
        try {
            int expectedSize = width * height * 4;
            if (frameData.length < expectedSize) {
                Log.w(TAG, "BGRA frame data too small: " + frameData.length + " expected: " + expectedSize);
                return null;
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            
            for (int i = 0; i < pixels.length; i++) {
                int byteIndex = i * 4;
                if (byteIndex + 3 < frameData.length) {
                    // BGRA format from NDI
                    int b = frameData[byteIndex] & 0xFF;
                    int g = frameData[byteIndex + 1] & 0xFF;
                    int r = frameData[byteIndex + 2] & 0xFF;
                    int a = frameData[byteIndex + 3] & 0xFF;
                    
                    // Convert to ARGB for Android
                    pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating BGRA bitmap", e);
            return null;
        }
    }
    
    private Bitmap createBitmapFromRGBA(byte[] frameData, int width, int height) {
        try {
            int expectedSize = width * height * 4;
            if (frameData.length < expectedSize) {
                Log.w(TAG, "RGBA frame data too small: " + frameData.length + " expected: " + expectedSize);
                return null;
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            
            for (int i = 0; i < pixels.length; i++) {
                int byteIndex = i * 4;
                if (byteIndex + 3 < frameData.length) {
                    // RGBA format from NDI
                    int r = frameData[byteIndex] & 0xFF;
                    int g = frameData[byteIndex + 1] & 0xFF;
                    int b = frameData[byteIndex + 2] & 0xFF;
                    int a = frameData[byteIndex + 3] & 0xFF;
                    
                    // Convert to ARGB for Android
                    pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating RGBA bitmap", e);
            return null;
        }
    }
    
    private Bitmap createBitmapFromBGRX(byte[] frameData, int width, int height) {
        try {
            int expectedSize = width * height * 4;
            if (frameData.length < expectedSize) {
                Log.w(TAG, "BGRX frame data too small: " + frameData.length + " expected: " + expectedSize);
                return null;
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            
            for (int i = 0; i < pixels.length; i++) {
                int byteIndex = i * 4;
                if (byteIndex + 3 < frameData.length) {
                    // BGRX format from NDI (X = ignored)
                    int b = frameData[byteIndex] & 0xFF;
                    int g = frameData[byteIndex + 1] & 0xFF;
                    int r = frameData[byteIndex + 2] & 0xFF;
                    // X is ignored, set alpha to 255
                    
                    // Convert to ARGB for Android
                    pixels[i] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating BGRX bitmap", e);
            return null;
        }
    }
    
    private Bitmap createBitmapFromUYVY(byte[] frameData, int width, int height) {
        try {
            // UYVY is YUV 4:2:2 format - 2 bytes per pixel
            int expectedSize = width * height * 2;
            if (frameData.length < expectedSize) {
                Log.w(TAG, "UYVY frame data too small: " + frameData.length + " expected: " + expectedSize);
                return null;
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            
            // Convert UYVY to RGB
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x += 2) {
                    int index = (y * width + x) * 2;
                    if (index + 3 < frameData.length) {
                        // UYVY format: U Y V Y (for 2 pixels)
                        int u = frameData[index] & 0xFF;
                        int y1 = frameData[index + 1] & 0xFF;
                        int v = frameData[index + 2] & 0xFF;
                        int y2 = frameData[index + 3] & 0xFF;
                        
                        // Convert YUV to RGB for first pixel
                        int[] rgb1 = convertYUVtoRGB(y1, u, v);
                        pixels[y * width + x] = (255 << 24) | (rgb1[0] << 16) | (rgb1[1] << 8) | rgb1[2];
                        
                        // Convert YUV to RGB for second pixel
                        if (x + 1 < width) {
                            int[] rgb2 = convertYUVtoRGB(y2, u, v);
                            pixels[y * width + x + 1] = (255 << 24) | (rgb2[0] << 16) | (rgb2[1] << 8) | rgb2[2];
                        }
                    }
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating UYVY bitmap", e);
            return null;
        }
    }
    
    private int[] convertYUVtoRGB(int y, int u, int v) {
        // Convert YUV to RGB using standard formulas
        int r = (int)(y + 1.402 * (v - 128));
        int g = (int)(y - 0.344 * (u - 128) - 0.714 * (v - 128));
        int b = (int)(y + 1.772 * (u - 128));
        
        // Clamp values to 0-255
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        
        return new int[]{r, g, b};
    }
    
    private Bitmap createTestPattern(int width, int height) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            
            // Create a simple color pattern
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    
                    // Create colored stripes
                    if (x < width / 3) {
                        pixels[index] = Color.RED;
                    } else if (x < 2 * width / 3) {
                        pixels[index] = Color.GREEN;
                    } else {
                        pixels[index] = Color.BLUE;
                    }
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            Log.d(TAG, "Created test pattern: " + width + "x" + height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating test pattern", e);
            return null;
        }
    }
}
