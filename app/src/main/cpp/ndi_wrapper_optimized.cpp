#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstring>
#include <memory>
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <chrono>

// NDI SDK Headers
#include "ndi/include/Processing.NDI.Lib.h"
#include "ndi/include/Processing.NDI.Recv.h"
#include "ndi/include/Processing.NDI.FrameSync.h"
#include "ndi/include/Processing.NDI.Find.h"
#include "ndi/include/Processing.NDI.structs.h"

#define LOG_TAG "NDI_Optimized"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Configuración de rendimiento
#define MAX_FRAME_CACHE 3
#define FRAME_TIMEOUT_MS 33  // ~30 FPS
#define CONNECTION_RETRY_COUNT 3

// Estado global optimizado con atomic operations
static std::atomic<NDIlib_find_instance_t> ndi_find{nullptr};
static std::atomic<NDIlib_recv_instance_t> ndi_recv{nullptr};
static std::atomic<const NDIlib_source_t*> current_sources{nullptr};
static uint32_t num_sources = 0;  // Sin atomic para compatibilidad con NDI API

// Variables adicionales para MainActivity compatibility
static NDIlib_video_frame_v2_t current_video_frame;
static std::atomic<bool> has_video_frame{false};

// Forward declaration
jobject createBitmapFromVideoFrame(JNIEnv *env, NDIlib_video_frame_v2_t* frame);

// Frame cache thread-safe
struct FrameData {
    std::unique_ptr<uint8_t[]> data;
    size_t size;
    int width;
    int height;
    NDIlib_FourCC_video_type_e fourCC;
    std::chrono::steady_clock::time_point timestamp;
    
    FrameData() : data(nullptr), size(0), width(0), height(0), 
                  fourCC(NDIlib_FourCC_video_type_UYVY), 
                  timestamp(std::chrono::steady_clock::now()) {}
    
    void reset() {
        data.reset();
        size = 0;
        width = height = 0;
        timestamp = std::chrono::steady_clock::now();
    }
};

// Thread-safe frame cache
class OptimizedFrameCache {
private:
    std::queue<std::unique_ptr<FrameData>> available_frames;
    std::queue<std::unique_ptr<FrameData>> ready_frames;
    std::mutex available_mutex;
    std::mutex ready_mutex;
    std::condition_variable frame_ready_cv;
    std::atomic<bool> shutdown{false};
    
public:
    OptimizedFrameCache() {
        // Pre-allocate frame objects
        for (int i = 0; i < MAX_FRAME_CACHE; i++) {
            available_frames.push(std::make_unique<FrameData>());
        }
    }
    
    ~OptimizedFrameCache() {
        shutdown = true;
        frame_ready_cv.notify_all();
    }
    
    std::unique_ptr<FrameData> getAvailableFrame() {
        std::lock_guard<std::mutex> lock(available_mutex);
        if (!available_frames.empty()) {
            auto frame = std::move(available_frames.front());
            available_frames.pop();
            return frame;
        }
        return std::make_unique<FrameData>();
    }
    
    void pushReadyFrame(std::unique_ptr<FrameData> frame) {
        std::lock_guard<std::mutex> lock(ready_mutex);
        
        // Mantener solo los frames más recientes
        while (ready_frames.size() >= MAX_FRAME_CACHE - 1) {
            auto old_frame = std::move(ready_frames.front());
            ready_frames.pop();
            
            // Reciclar frame
            old_frame->reset();
            std::lock_guard<std::mutex> av_lock(available_mutex);
            available_frames.push(std::move(old_frame));
        }
        
        ready_frames.push(std::move(frame));
        frame_ready_cv.notify_one();
    }
    
    std::unique_ptr<FrameData> popReadyFrame(int timeout_ms = 0) {
        std::unique_lock<std::mutex> lock(ready_mutex);
        
        if (timeout_ms > 0) {
            frame_ready_cv.wait_for(lock, std::chrono::milliseconds(timeout_ms), 
                [this] { return !ready_frames.empty() || shutdown; });
        }
        
        if (!ready_frames.empty()) {
            auto frame = std::move(ready_frames.front());
            ready_frames.pop();
            return frame;
        }
        
        return nullptr;
    }
    
    void recycleFrame(std::unique_ptr<FrameData> frame) {
        frame->reset();
        std::lock_guard<std::mutex> lock(available_mutex);
        available_frames.push(std::move(frame));
    }
    
    void clear() {
        std::lock_guard<std::mutex> ready_lock(ready_mutex);
        std::lock_guard<std::mutex> av_lock(available_mutex);
        
        // Mover todos los frames ready a available
        while (!ready_frames.empty()) {
            auto frame = std::move(ready_frames.front());
            ready_frames.pop();
            frame->reset();
            available_frames.push(std::move(frame));
        }
    }
};

static OptimizedFrameCache frame_cache;
static std::atomic<bool> capture_running{false};
static std::thread capture_thread;

// Optimized NDI initialization
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeInitializeNDI(JNIEnv *env, jobject thiz) {
    LOGI("Inicializando NDI SDK optimizado para bajo rendimiento");

    try {
        if (!NDIlib_initialize()) {
            LOGE("Error inicializando NDI SDK");
            return JNI_FALSE;
        }
        
        LOGI("NDI SDK inicializado exitosamente");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Excepción durante inicialización NDI: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Excepción desconocida durante inicialización NDI");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeDestroyNDI(JNIEnv *env, jobject thiz) {
    LOGI("Destruyendo recursos NDI optimizados");
    
    // Detener captura
    capture_running = false;
    if (capture_thread.joinable()) {
        capture_thread.join();
    }
    
    // Limpiar receiver
    NDIlib_recv_instance_t recv = ndi_recv.exchange(nullptr);
    if (recv) {
        NDIlib_recv_destroy(recv);
    }
    
    // Limpiar finder
    NDIlib_find_instance_t find = ndi_find.exchange(nullptr);
    if (find) {
        NDIlib_find_destroy(find);
    }
    
    // Limpiar cache
    frame_cache.clear();
    
    current_sources = nullptr;
    num_sources = 0;
    
    NDIlib_destroy();
    LOGI("Recursos NDI destruidos");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeStartDiscovery(JNIEnv *env, jobject thiz) {
    LOGI("Iniciando discovery NDI optimizado");
    
    try {
        // Limpiar finder anterior
        NDIlib_find_instance_t old_find = ndi_find.exchange(nullptr);
        if (old_find) {
            NDIlib_find_destroy(old_find);
        }
        
        // Crear finder con configuración optimizada
        NDIlib_find_create_t find_desc;
        memset(&find_desc, 0, sizeof(find_desc));
        find_desc.show_local_sources = true;
        find_desc.p_groups = nullptr;
        find_desc.p_extra_ips = nullptr;
        
        NDIlib_find_instance_t new_find = NDIlib_find_create_v2(&find_desc);
        if (!new_find) {
            LOGE("Error creando instancia NDI find");
            return JNI_FALSE;
        }
        
        ndi_find = new_find;
        LOGI("Discovery NDI iniciado exitosamente");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Excepción durante discovery: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Excepción desconocida durante discovery");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeGetSources(JNIEnv *env, jobject thiz) {
    LOGD("Obteniendo fuentes NDI optimizado");
    
    try {
        jclass stringClass = env->FindClass("java/lang/String");
        if (!stringClass) {
            LOGE("No se pudo encontrar clase String");
            return nullptr;
        }
        
        NDIlib_find_instance_t find = ndi_find.load();
        if (!find) {
            LOGD("No hay instancia find disponible");
            return env->NewObjectArray(0, stringClass, nullptr);
        }
        
        // Esperar fuentes con timeout progresivo
        bool sources_available = NDIlib_find_wait_for_sources(find, 2000);
        LOGD("Fuentes disponibles después de espera: %s", sources_available ? "sí" : "no");
        
        uint32_t source_count = 0;
        const NDIlib_source_t* sources = NDIlib_find_get_current_sources(find, &source_count);
        
        LOGD("Encontradas %d fuentes", source_count);
        
        if (source_count == 0 || !sources) {
            return env->NewObjectArray(0, stringClass, nullptr);
        }
        
        // Actualizar estado global
        current_sources = sources;
        num_sources = source_count;
        
        // Crear array Java
        jobjectArray result = env->NewObjectArray(source_count, stringClass, nullptr);
        if (!result) {
            LOGE("Error creando array Java");
            return nullptr;
        }
        
        // Llenar array con nombres de fuentes
        for (uint32_t i = 0; i < source_count; i++) {
            const char* source_name = sources[i].p_ndi_name ? sources[i].p_ndi_name : "Fuente Desconocida";
            LOGD("Agregando fuente[%d]: %s", i, source_name);
            
            jstring jstr = env->NewStringUTF(source_name);
            if (jstr) {
                env->SetObjectArrayElement(result, i, jstr);
                env->DeleteLocalRef(jstr);
            }
        }
        
        LOGI("Array de fuentes creado exitosamente con %d elementos", source_count);
        return result;
        
    } catch (const std::exception& e) {
        LOGE("Excepción en nativeGetSources: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Excepción desconocida en nativeGetSources");
        return nullptr;
    }
}

// Optimized connection with retry logic
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeConnectToSource(JNIEnv *env, jobject thiz, jstring sourceName, jstring sourceUrl) {
    const char* source_name_cstr = env->GetStringUTFChars(sourceName, 0);
    
    LOGI("=== CONEXIÓN NDI OPTIMIZADA ===");
    LOGI("Conectando a fuente: %s", source_name_cstr);
    
    try {
        // Limpiar receiver anterior
        NDIlib_recv_instance_t old_recv = ndi_recv.exchange(nullptr);
        if (old_recv) {
            LOGD("Limpiando receiver anterior");
            NDIlib_recv_destroy(old_recv);
        }
        
        // Detener captura anterior
        capture_running = false;
        if (capture_thread.joinable()) {
            capture_thread.join();
        }
        frame_cache.clear();
        
        // Buscar fuente específica con reintentos
        const NDIlib_source_t* target_source = nullptr;
        
        for (int retry = 0; retry < CONNECTION_RETRY_COUNT && !target_source; retry++) {
            LOGD("Intento de búsqueda %d/%d", retry + 1, CONNECTION_RETRY_COUNT);
            
            NDIlib_find_instance_t find = ndi_find.load();
            if (!find) {
                LOGE("No hay instancia find disponible");
                break;
            }
            
            NDIlib_find_wait_for_sources(find, 1000);
            
            uint32_t source_count = 0;
            const NDIlib_source_t* sources = NDIlib_find_get_current_sources(find, &source_count);
            
            if (sources && source_count > 0) {
                for (uint32_t i = 0; i < source_count; i++) {
                    const char* current_name = sources[i].p_ndi_name ? sources[i].p_ndi_name : "";
                    LOGD("Verificando fuente[%d]: %s", i, current_name);
                    
                    if (strcmp(current_name, source_name_cstr) == 0) {
                        target_source = &sources[i];
                        LOGI("Fuente objetivo encontrada en intento %d", retry + 1);
                        break;
                    }
                }
            }
            
            if (!target_source && retry < CONNECTION_RETRY_COUNT - 1) {
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
            }
        }
        
        if (!target_source) {
            LOGE("Fuente no encontrada después de %d intentos: %s", CONNECTION_RETRY_COUNT, source_name_cstr);
            env->ReleaseStringUTFChars(sourceName, source_name_cstr);
            return JNI_FALSE;
        }
        
        // Crear receiver con configuración optimizada para bajo rendimiento
        NDIlib_recv_create_v3_t recv_desc;
        memset(&recv_desc, 0, sizeof(recv_desc));
        recv_desc.source_to_connect_to = *target_source;
        recv_desc.p_ndi_recv_name = "NDI Android Player Optimized";
        recv_desc.bandwidth = NDIlib_recv_bandwidth_audio_only; // Iniciar con audio solo para conexión rápida
        recv_desc.allow_video_fields = false; // Desactivar fields para rendimiento
        recv_desc.color_format = NDIlib_recv_color_format_BGRX_BGRA; // Formato consistente
        
        NDIlib_recv_instance_t new_recv = NDIlib_recv_create_v3(&recv_desc);
        if (!new_recv) {
            LOGE("Error creando receiver NDI");
            env->ReleaseStringUTFChars(sourceName, source_name_cstr);
            return JNI_FALSE;
        }
        
        // Actualizar configuración para incluir video después de conexión
        NDIlib_recv_set_tally(new_recv, nullptr); // Sin tally para rendimiento
        
        ndi_recv = new_recv;
        
        LOGI("=== CONEXIÓN NDI COMPLETADA EXITOSAMENTE ===");
        
        env->ReleaseStringUTFChars(sourceName, source_name_cstr);
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Excepción durante conexión: %s", e.what());
        env->ReleaseStringUTFChars(sourceName, source_name_cstr);
        return JNI_FALSE;
    } catch (...) {
        LOGE("Excepción desconocida durante conexión");
        env->ReleaseStringUTFChars(sourceName, source_name_cstr);
        return JNI_FALSE;
    }
}

// High-performance frame capture function
void optimized_capture_loop() {
    LOGI("Iniciando loop de captura optimizado");
    
    NDIlib_recv_instance_t recv = ndi_recv.load();
    if (!recv) {
        LOGE("No hay receiver disponible para captura");
        return;
    }
    
    // Configurar prioridad de thread
    struct sched_param param;
    param.sched_priority = sched_get_priority_max(SCHED_FIFO) - 1;
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);
    
    int frame_count = 0;
    auto last_stats = std::chrono::steady_clock::now();
    
    while (capture_running.load()) {
        try {
            auto frame = frame_cache.getAvailableFrame();
            if (!frame) continue;
            
            // Estructuras para captura
            NDIlib_video_frame_v2_t video_frame;
            NDIlib_audio_frame_v2_t audio_frame;
            NDIlib_metadata_frame_t metadata_frame;
            
            memset(&video_frame, 0, sizeof(video_frame));
            memset(&audio_frame, 0, sizeof(audio_frame));
            memset(&metadata_frame, 0, sizeof(metadata_frame));
            
            // Capturar con timeout corto para responsividad
            NDIlib_frame_type_e frame_type = NDIlib_recv_capture_v2(
                recv, &video_frame, &audio_frame, &metadata_frame, FRAME_TIMEOUT_MS);
            
            switch (frame_type) {
                case NDIlib_frame_type_video: {
                    frame_count++;
                    
                    // Copiar datos de frame de manera eficiente
                    size_t frame_size = video_frame.yres * video_frame.line_stride_in_bytes;
                    
                    if (frame_size > 0 && video_frame.p_data) {
                        frame->width = video_frame.xres;
                        frame->height = video_frame.yres;
                        frame->fourCC = video_frame.FourCC;
                        frame->size = frame_size;
                        
                        // Allocar memoria solo si es necesario
                        if (!frame->data || frame_size > frame->size) {
                            frame->data = std::make_unique<uint8_t[]>(frame_size);
                        }
                        
                        // Copia optimizada
                        std::memcpy(frame->data.get(), video_frame.p_data, frame_size);
                        
                        // Liberar frame NDI inmediatamente
                        NDIlib_recv_free_video_v2(recv, &video_frame);
                        
                        // Agregar frame al cache listo
                        frame_cache.pushReadyFrame(std::move(frame));
                        
                        // Estadísticas cada 5 segundos
                        auto now = std::chrono::steady_clock::now();
                        if (std::chrono::duration_cast<std::chrono::seconds>(now - last_stats).count() >= 5) {
                            LOGD("Captura: %d frames en 5s (%dx%d)", frame_count, 
                                video_frame.xres, video_frame.yres);
                            frame_count = 0;
                            last_stats = now;
                        }
                    } else {
                        NDIlib_recv_free_video_v2(recv, &video_frame);
                        frame_cache.recycleFrame(std::move(frame));
                    }
                    break;
                }
                
                case NDIlib_frame_type_audio:
                    NDIlib_recv_free_audio_v2(recv, &audio_frame);
                    frame_cache.recycleFrame(std::move(frame));
                    break;
                    
                case NDIlib_frame_type_none:
                    frame_cache.recycleFrame(std::move(frame));
                    std::this_thread::sleep_for(std::chrono::milliseconds(1));
                    break;
                    
                default:
                    frame_cache.recycleFrame(std::move(frame));
                    std::this_thread::sleep_for(std::chrono::milliseconds(5));
                    break;
            }
            
        } catch (const std::exception& e) {
            LOGE("Excepción en loop de captura: %s", e.what());
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        } catch (...) {
            LOGE("Excepción desconocida en loop de captura");
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }
    
    LOGI("Loop de captura optimizado terminado");
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeCaptureFrame(JNIEnv *env, jobject thiz, jintArray widthHeight, jint timeoutMs) {
    NDIlib_recv_instance_t recv = ndi_recv.load();
    if (!recv) {
        return -1; // Error
    }
    
    // Iniciar thread de captura si no está corriendo
    if (!capture_running.load()) {
        capture_running = true;
        if (capture_thread.joinable()) {
            capture_thread.join();
        }
        capture_thread = std::thread(optimized_capture_loop);
    }
    
    // Obtener frame del cache
    auto frame = frame_cache.popReadyFrame(timeoutMs);
    if (!frame) {
        return 0; // No frame
    }
    
    // Establecer dimensiones
    if (widthHeight) {
        jint* dimensions = env->GetIntArrayElements(widthHeight, nullptr);
        if (dimensions) {
            dimensions[0] = frame->width;
            dimensions[1] = frame->height;
            env->ReleaseIntArrayElements(widthHeight, dimensions, 0);
        }
    }
    
    // Mantener frame para GetFrameData
    static thread_local std::unique_ptr<FrameData> current_frame;
    current_frame = std::move(frame);
    
    return 1; // Video frame
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeGetFrameData(JNIEnv *env, jobject thiz) {
    static thread_local std::unique_ptr<FrameData> current_frame;
    
    if (!current_frame || !current_frame->data) {
        return nullptr;
    }
    
    jbyteArray result = env->NewByteArray(current_frame->size);
    if (!result) {
        LOGE("Error creando array Java para frame data");
        return nullptr;
    }
    
    env->SetByteArrayRegion(result, 0, current_frame->size, 
                           reinterpret_cast<const jbyte*>(current_frame->data.get()));
    
    // Reciclar frame después de usar los datos
    frame_cache.recycleFrame(std::move(current_frame));
    
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeGetFrameFourCC(JNIEnv *env, jobject thiz) {
    static thread_local std::unique_ptr<FrameData> current_frame;
    
    if (!current_frame) {
        return 0;
    }
    
    return static_cast<jint>(current_frame->fourCC);
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeGetFrameWidth(JNIEnv *env, jobject thiz) {
    static thread_local std::unique_ptr<FrameData> current_frame;
    
    if (!current_frame) {
        return 0;
    }
    
    return current_frame->width;
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeGetFrameHeight(JNIEnv *env, jobject thiz) {
    static thread_local std::unique_ptr<FrameData> current_frame;
    
    if (!current_frame) {
        return 0;
    }
    
    return current_frame->height;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_service_NDIRenderService_nativeDisconnect(JNIEnv *env, jobject thiz) {
    LOGI("Desconectando de fuente NDI");
    
    try {
        // Detener captura
        capture_running = false;
        if (capture_thread.joinable()) {
            capture_thread.join();
        }
        
        // Limpiar receiver
        NDIlib_recv_instance_t recv = ndi_recv.exchange(nullptr);
        if (recv) {
            NDIlib_recv_destroy(recv);
        }
        
        // Limpiar cache
        frame_cache.clear();
        
        LOGI("Desconexión NDI completada");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Excepción durante desconexión: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Excepción desconocida durante desconexión");
        return JNI_FALSE;
    }
}

// Nuevas funciones nativas para optimización de conversión
extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeConvertBGRAToARGB(JNIEnv *env, jobject thiz, 
    jbyteArray bgraData, jintArray argbPixels, jint width, jint height) {
    
    jbyte* bgra = env->GetByteArrayElements(bgraData, nullptr);
    jint* argb = env->GetIntArrayElements(argbPixels, nullptr);
    
    if (bgra && argb) {
        int pixel_count = width * height;
        
        // Conversión optimizada con loop unrolling
        for (int i = 0; i < pixel_count; i += 4) {
            // Procesar 4 pixels a la vez
            for (int j = 0; j < 4 && (i + j) < pixel_count; j++) {
                int idx = (i + j) * 4;
                int b = bgra[idx] & 0xFF;
                int g = bgra[idx + 1] & 0xFF;
                int r = bgra[idx + 2] & 0xFF;
                int a = bgra[idx + 3] & 0xFF;
                
                argb[i + j] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }
    
    if (bgra) env->ReleaseByteArrayElements(bgraData, bgra, JNI_ABORT);
    if (argb) env->ReleaseIntArrayElements(argbPixels, argb, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeConvertUYVYToARGB(JNIEnv *env, jobject thiz,
    jbyteArray uyvyData, jintArray argbPixels, jint width, jint height) {
    
    jbyte* uyvy = env->GetByteArrayElements(uyvyData, nullptr);
    jint* argb = env->GetIntArrayElements(argbPixels, nullptr);
    
    if (uyvy && argb) {
        int pixel_count = width * height;
        
        // Lookup tables para conversión YUV optimizada
        static bool lookup_initialized = false;
        static int yuv_to_rgb_r[256];
        static int yuv_to_rgb_g_u[256];
        static int yuv_to_rgb_g_v[256];
        static int yuv_to_rgb_b[256];
        
        if (!lookup_initialized) {
            for (int i = 0; i < 256; i++) {
                int c = i - 128;
                yuv_to_rgb_r[i] = (int)(1.402f * c);
                yuv_to_rgb_g_u[i] = (int)(-0.344f * c);
                yuv_to_rgb_g_v[i] = (int)(-0.714f * c);
                yuv_to_rgb_b[i] = (int)(1.772f * c);
            }
            lookup_initialized = true;
        }
        
        // Conversión UYVY optimizada
        for (int i = 0; i < pixel_count; i += 2) {
            int uyvy_idx = i * 2; // 2 bytes por pixel en UYVY
            
            int u = uyvy[uyvy_idx] & 0xFF;
            int y1 = uyvy[uyvy_idx + 1] & 0xFF;
            int v = uyvy[uyvy_idx + 2] & 0xFF;
            int y2 = uyvy[uyvy_idx + 3] & 0xFF;
            
            // Primer pixel
            int r1 = y1 + yuv_to_rgb_r[v];
            int g1 = y1 + yuv_to_rgb_g_u[u] + yuv_to_rgb_g_v[v];
            int b1 = y1 + yuv_to_rgb_b[u];
            
            // Clamp con operaciones bit optimizadas
            r1 = (r1 & 0xFFFFFF00) == 0 ? r1 : r1 < 0 ? 0 : 255;
            g1 = (g1 & 0xFFFFFF00) == 0 ? g1 : g1 < 0 ? 0 : 255;
            b1 = (b1 & 0xFFFFFF00) == 0 ? b1 : b1 < 0 ? 0 : 255;
            
            argb[i] = (0xFF << 24) | (r1 << 16) | (g1 << 8) | b1;
            
            // Segundo pixel (si existe)
            if (i + 1 < pixel_count) {
                int r2 = y2 + yuv_to_rgb_r[v];
                int g2 = y2 + yuv_to_rgb_g_u[u] + yuv_to_rgb_g_v[v];
                int b2 = y2 + yuv_to_rgb_b[u];
                
                r2 = (r2 & 0xFFFFFF00) == 0 ? r2 : r2 < 0 ? 0 : 255;
                g2 = (g2 & 0xFFFFFF00) == 0 ? g2 : g2 < 0 ? 0 : 255;
                b2 = (b2 & 0xFFFFFF00) == 0 ? b2 : b2 < 0 ? 0 : 255;
                
                argb[i + 1] = (0xFF << 24) | (r2 << 16) | (g2 << 8) | b2;
            }
        }
    }
    
    if (uyvy) env->ReleaseByteArrayElements(uyvyData, uyvy, JNI_ABORT);
    if (argb) env->ReleaseIntArrayElements(argbPixels, argb, 0);
}

extern "C" JNIEXPORT jobject JNICALL
Java_ndiplayer_oto_MainActivity_nativeCreateOptimizedBitmap(JNIEnv *env, jobject thiz,
    jintArray pixels, jint width, jint height) {
    
    // Crear bitmap optimizado usando configuración RGB_565 para menor uso de memoria en dispositivos de bajos recursos
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    
    if (!bitmapClass || !configClass) {
        LOGE("No se pudieron encontrar clases Bitmap");
        return nullptr;
    }
    
    // Usar ARGB_8888 para mejor calidad, RGB_565 para menor memoria
    jfieldID configField = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(configClass, configField);
    
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapClass, "createBitmap",
        "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    if (!createBitmapMethod) {
        LOGE("No se pudo encontrar método createBitmap");
        return nullptr;
    }
    
    return env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, pixels, width, height, config);
}

// ======================================
// FUNCIONES ADICIONALES PARA MAINACTIVITY
// ======================================

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeInitializeNDI(JNIEnv *env, jobject thiz) {
    LOGI("Initializing NDI SDK for MainActivity - FULL MODE with low-performance optimizations");

    try {
        if (!NDIlib_initialize()) {
            LOGE("Failed to initialize NDI SDK");
            return JNI_FALSE;
        }
        
        LOGI("NDI SDK initialized successfully with performance optimizations");
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("Exception during NDI initialization");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetNDIVersion(JNIEnv *env, jobject /* this */) {
    LOGI("Getting NDI version information...");
    
    std::string version_info = "NDI SDK - Full Receiver Mode\n";
    version_info += "Discovery + Receiving Implementation\n";
    version_info += "Library: libndi.so\n";
    
    return env->NewStringUTF(version_info.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeStartDiscovery(JNIEnv *env, jobject thiz) {
    LOGI("Starting NDI discovery - FULL MODE");
    
    try {
        // Clean up any previous find instance
        if (ndi_find) {
            NDIlib_find_destroy(ndi_find);
            ndi_find = nullptr;
        }
        
        // Create basic find instance
        NDIlib_find_create_t find_desc;
        find_desc.show_local_sources = true;
        find_desc.p_groups = nullptr;
        find_desc.p_extra_ips = nullptr;
        
        ndi_find = NDIlib_find_create_v2(&find_desc);
        
        if (!ndi_find) {
            LOGE("Failed to create NDI find instance");
            return JNI_FALSE;
        }
        
        LOGI("NDI discovery started successfully");
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("Exception during NDI discovery start");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetSourceCount(JNIEnv *env, jobject thiz) {
    if (!ndi_find) {
        LOGE("NDI find instance not created");
        return 0;
    }
    
    current_sources = NDIlib_find_get_current_sources(ndi_find, &num_sources);
    LOGI("Found %u NDI sources", num_sources);
    return static_cast<jint>(num_sources);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetSourceName(JNIEnv *env, jobject thiz, jint index) {
    if (!ndi_find || !current_sources || index < 0 || index >= (jint)num_sources) {
        LOGE("Invalid source index or no sources available");
        return env->NewStringUTF("ERROR");
    }
    
    const char* source_name = current_sources[index].p_ndi_name;
    if (!source_name) {
        LOGE("Source name is null for index %d", index);
        return env->NewStringUTF("Unknown Source");
    }
    
    LOGI("Source %d: %s", index, source_name);
    return env->NewStringUTF(source_name);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeConnectToSource(JNIEnv *env, jobject thiz, jint index) {
    LOGI("Connecting to NDI source - FULL MODE");
    
    if (!ndi_find || !current_sources || index < 0 || index >= (jint)num_sources) {
        LOGE("Invalid source index or no sources available");
        return JNI_FALSE;
    }
    
    try {
        // Disconnect from any existing receiver
        if (ndi_recv) {
            if (has_video_frame) {
                NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
                has_video_frame = false;
            }
            NDIlib_recv_destroy(ndi_recv);
            ndi_recv = nullptr;
        }
        
        // Create new receiver with optimized settings for low-performance devices
        NDIlib_recv_create_v3_t recv_desc;
        recv_desc.source_to_connect_to = current_sources[index];
        recv_desc.color_format = NDIlib_recv_color_format_UYVY_RGBA;  // Optimized format
        recv_desc.bandwidth = NDIlib_recv_bandwidth_lowest;           // Lowest bandwidth for performance
        recv_desc.allow_video_fields = false;                        // Disable fields for simplicity
        recv_desc.p_ndi_recv_name = "NDI Android Receiver";
        
        ndi_recv = NDIlib_recv_create_v3(&recv_desc);
        
        if (!ndi_recv) {
            LOGE("Failed to create NDI receiver");
            return JNI_FALSE;
        }
        
        LOGI("Connected to NDI source: %s", current_sources[index].p_ndi_name);
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("Exception during NDI source connection");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeDisconnect(JNIEnv *env, jobject thiz) {
    LOGI("Disconnecting from NDI source");
    
    if (ndi_recv) {
        if (has_video_frame) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        NDIlib_recv_destroy(ndi_recv);
        ndi_recv = nullptr;
    }
    
    LOGI("Disconnected from NDI source");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeIsConnected(JNIEnv *env, jobject thiz) {
    return (ndi_recv != nullptr) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrame(JNIEnv *env, jobject thiz) {
    if (!ndi_recv) {
        LOGE("No NDI receiver connected");
        return nullptr;
    }
    
    try {
        // Free previous frame if exists
        if (has_video_frame) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        // Capture new frame with timeout
        switch (NDIlib_recv_capture_v2(ndi_recv, &current_video_frame, nullptr, nullptr, 100)) {
            case NDIlib_frame_type_video:
                has_video_frame = true;
                
                if (current_video_frame.p_data) {
                    // Log format only occasionally
                    static int frame_counter = 0;
                    if (frame_counter % 300 == 0) { // Log every 300 frames (~10 seconds at 30fps)
                        LOGI("Frame %d: %dx%d, FourCC=0x%08X", frame_counter, 
                             current_video_frame.xres, current_video_frame.yres, current_video_frame.FourCC);
                    }
                    frame_counter++;
                    
                    jobject bitmap = createBitmapFromVideoFrame(env, &current_video_frame);
                    return bitmap;
                } else {
                    LOGE("Video frame data is null");
                    return nullptr;
                }
                
            case NDIlib_frame_type_none:
                // No new frame available
                return nullptr;
                
            default:
                LOGE("Unexpected frame type received");
                return nullptr;
        }
        
    } catch (...) {
        LOGE("Exception during frame capture");
        return nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeHasFrame(JNIEnv *env, jobject thiz) {
    bool result = has_video_frame;
    if (result) {
        LOGI("nativeHasFrame: TRUE - frame available");
    }
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetConnectionInfo(JNIEnv *env, jobject thiz) {
    if (!ndi_recv) {
        return env->NewStringUTF("Not connected");
    }
    
    NDIlib_recv_performance_t perf;
    NDIlib_recv_get_performance(ndi_recv, &perf, nullptr);
    
    char info[512];
    snprintf(info, sizeof(info), 
        "Connected\nFrames: Video=%ld, Audio=%ld\nDropped: Video=%ld, Audio=%ld", 
        (long)perf.video_frames, (long)perf.audio_frames, 
        (long)(perf.video_frames - perf.video_frames), (long)(perf.audio_frames - perf.audio_frames));
    
    return env->NewStringUTF(info);
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameRate(JNIEnv *env, jobject thiz) {
    if (has_video_frame) {
        return static_cast<jint>(current_video_frame.frame_rate_N / current_video_frame.frame_rate_D);
    }
    return 0;
}

// Función auxiliar para crear bitmap desde frame de video
jobject createBitmapFromVideoFrame(JNIEnv *env, NDIlib_video_frame_v2_t* frame) {
    if (!frame || !frame->p_data) {
        LOGE("Invalid frame data");
        return nullptr;
    }
    
    try {
        // Find Bitmap class and createBitmap method
        jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
        if (!bitmapClass) {
            LOGE("Could not find Bitmap class");
            return nullptr;
        }
        
        jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapClass, "createBitmap", 
                                                             "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
        if (!createBitmapMethod) {
            LOGE("Could not find createBitmap method");
            return nullptr;
        }
        
        // Find Bitmap.Config.ARGB_8888
        jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
        if (!configClass) {
            LOGE("Could not find Bitmap.Config class");
            return nullptr;
        }
        
        jfieldID argb8888Field = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
        if (!argb8888Field) {
            LOGE("Could not find ARGB_8888 field");
            return nullptr;
        }
        
        jobject config = env->GetStaticObjectField(configClass, argb8888Field);
        if (!config) {
            LOGE("Could not get ARGB_8888 config");
            return nullptr;
        }
        
        // Convert frame data to ARGB format
        int width = frame->xres;
        int height = frame->yres;
        int stride = frame->line_stride_in_bytes;
        
        jintArray pixels = env->NewIntArray(width * height);
        if (!pixels) {
            LOGE("Could not create pixel array");
            return nullptr;
        }
        
        jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
        if (!pixelData) {
            LOGE("Could not get pixel data");
            return nullptr;
        }
        
        // Smart color conversion based on NDI format
        uint8_t* srcData = (uint8_t*)frame->p_data;
        
        // Log detailed format information
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "=== COLOR DEBUG === FourCC=0x%08X (%d), Size=%dx%d, Stride=%d", 
                           frame->FourCC, frame->FourCC, width, height, stride);
        
        // Detect format and convert appropriately
        switch (frame->FourCC) {
            case NDIlib_FourCC_video_type_BGRX:
            case NDIlib_FourCC_video_type_BGRA:
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "=== COLOR === Using BGRA/BGRX conversion");
                // BGRA to ARGB conversion
                for (int y = 0; y < height; y++) {
                    uint8_t* srcRow = srcData + (y * stride);
                    for (int x = 0; x < width; x++) {
                        uint8_t b = srcRow[x * 4 + 0];
                        uint8_t g = srcRow[x * 4 + 1];
                        uint8_t r = srcRow[x * 4 + 2];
                        uint8_t a = (frame->FourCC == NDIlib_FourCC_video_type_BGRA) ? srcRow[x * 4 + 3] : 255;
                        
                        pixelData[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                break;
                
            case NDIlib_FourCC_video_type_RGBA:
            case NDIlib_FourCC_video_type_RGBX:
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "=== COLOR === Using RGBA/RGBX conversion");
                // RGBA to ARGB conversion (swap R and B)
                for (int y = 0; y < height; y++) {
                    uint8_t* srcRow = srcData + (y * stride);
                    for (int x = 0; x < width; x++) {
                        uint8_t r = srcRow[x * 4 + 0];
                        uint8_t g = srcRow[x * 4 + 1];
                        uint8_t b = srcRow[x * 4 + 2];
                        uint8_t a = (frame->FourCC == NDIlib_FourCC_video_type_RGBA) ? srcRow[x * 4 + 3] : 255;
                        
                        pixelData[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                break;
                
            case NDIlib_FourCC_video_type_UYVY:
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "=== COLOR === Using UYVY conversion");
                // UYVY to RGB conversion
                for (int y = 0; y < height; y++) {
                    uint8_t* srcRow = srcData + (y * stride);
                    for (int x = 0; x < width; x += 2) {
                        // UYVY: U Y V Y (2 pixels per 4 bytes)
                        uint8_t u = srcRow[x * 2 + 0];
                        uint8_t y1 = srcRow[x * 2 + 1];
                        uint8_t v = srcRow[x * 2 + 2];
                        uint8_t y2 = (x + 1 < width) ? srcRow[x * 2 + 3] : y1;
                        
                        // Convert YUV to RGB (BT.601)
                        int c1 = y1 - 16;
                        int c2 = (x + 1 < width) ? (y2 - 16) : c1;
                        int d = u - 128;
                        int e = v - 128;
                        
                        // First pixel
                        int r1 = (298 * c1 + 409 * e + 128) >> 8;
                        int g1 = (298 * c1 - 100 * d - 208 * e + 128) >> 8;
                        int b1 = (298 * c1 + 516 * d + 128) >> 8;
                        
                        // Clamp values
                        r1 = r1 < 0 ? 0 : (r1 > 255 ? 255 : r1);
                        g1 = g1 < 0 ? 0 : (g1 > 255 ? 255 : g1);
                        b1 = b1 < 0 ? 0 : (b1 > 255 ? 255 : b1);
                        
                        pixelData[y * width + x] = (255 << 24) | (r1 << 16) | (g1 << 8) | b1;
                        
                        // Second pixel (if exists)
                        if (x + 1 < width) {
                            int r2 = (298 * c2 + 409 * e + 128) >> 8;
                            int g2 = (298 * c2 - 100 * d - 208 * e + 128) >> 8;
                            int b2 = (298 * c2 + 516 * d + 128) >> 8;
                            
                            r2 = r2 < 0 ? 0 : (r2 > 255 ? 255 : r2);
                            g2 = g2 < 0 ? 0 : (g2 > 255 ? 255 : g2);
                            b2 = b2 < 0 ? 0 : (b2 > 255 ? 255 : b2);
                            
                            pixelData[y * width + x + 1] = (255 << 24) | (r2 << 16) | (g2 << 8) | b2;
                        }
                    }
                }
                break;
                
            default:
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "=== COLOR === UNKNOWN FORMAT! FourCC=0x%08X (%d) - Using default BGRA", 
                                   frame->FourCC, frame->FourCC);
                // Fallback: assume BGRA
                for (int y = 0; y < height; y++) {
                    uint8_t* srcRow = srcData + (y * stride);
                    for (int x = 0; x < width; x++) {
                        uint8_t b = srcRow[x * 4 + 0];
                        uint8_t g = srcRow[x * 4 + 1];
                        uint8_t r = srcRow[x * 4 + 2];
                        uint8_t a = srcRow[x * 4 + 3];
                        
                        pixelData[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                break;
        }
        
        env->ReleaseIntArrayElements(pixels, pixelData, 0);
        
        return env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, pixels, width, height, config);
        
    } catch (...) {
        LOGE("Exception creating bitmap from frame");
        return nullptr;
    }
}

// Funciones adicionales del archivo original para compatibilidad
extern "C" JNIEXPORT jobject JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameInfo(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame) {
        return nullptr;
    }
    
    try {
        // Create frame info object
        jclass frameInfoClass = env->FindClass("ndiplayer/oto/FrameInfo");
        if (!frameInfoClass) {
            LOGE("Could not find FrameInfo class");
            return nullptr;
        }
        
        jmethodID constructor = env->GetMethodID(frameInfoClass, "<init>", "(IIJI)V");
        if (!constructor) {
            LOGE("Could not find FrameInfo constructor");
            return nullptr;
        }
        
        // Return frame information
        return env->NewObject(frameInfoClass, constructor, 
                            current_video_frame.xres, 
                            current_video_frame.yres,
                            (jlong)current_video_frame.p_data,
                            current_video_frame.line_stride_in_bytes);
                            
    } catch (const std::exception& e) {
        LOGE("Exception getting frame info: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception getting frame info");
        return nullptr;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameData(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame || !current_video_frame.p_data) {
        LOGI("nativeGetFrameData called - No frame data available");
        return nullptr;
    }
    
    try {
        // Calculate total data size
        size_t data_size = current_video_frame.yres * current_video_frame.line_stride_in_bytes;
        
        // Create Java byte array
        jbyteArray result = env->NewByteArray(data_size);
        if (!result) {
            LOGE("Failed to create byte array for frame data");
            return nullptr;
        }
        
        // Copy frame data to Java array
        env->SetByteArrayRegion(result, 0, data_size, (jbyte*)current_video_frame.p_data);
        
        LOGI("Frame data copied: %zu bytes", data_size);
        return result;
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeGetFrameData: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Exception in nativeGetFrameData");
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameWidth(JNIEnv *env, jobject thiz) {
    return has_video_frame ? current_video_frame.xres : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameHeight(JNIEnv *env, jobject thiz) {
    return has_video_frame ? current_video_frame.yres : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameStride(JNIEnv *env, jobject thiz) {
    return has_video_frame ? current_video_frame.line_stride_in_bytes : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameFormat(JNIEnv *env, jobject thiz) {
    if (!has_video_frame) {
        return env->NewStringUTF("No frame");
    }
    
    const char* format = "Unknown";
    switch (current_video_frame.FourCC) {
        case NDIlib_FourCC_video_type_UYVY: format = "UYVY"; break;
        case NDIlib_FourCC_video_type_BGRA: format = "BGRA"; break;
        case NDIlib_FourCC_video_type_BGRX: format = "BGRX"; break;
        case NDIlib_FourCC_video_type_RGBA: format = "RGBA"; break;
        case NDIlib_FourCC_video_type_RGBX: format = "RGBX"; break;
        default: format = "Unknown"; break;
    }
    
    return env->NewStringUTF(format);
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameFourCC(JNIEnv *env, jobject thiz) {
    return has_video_frame ? static_cast<jint>(current_video_frame.FourCC) : 0;
}

// Nueva función para shutdown completo
extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeShutdownNDI(JNIEnv *env, jobject thiz) {
    LOGI("Cerrando NDI SDK...");
    
    // Liberar receiver si existe
    NDIlib_recv_instance_t recv = ndi_recv.exchange(nullptr);
    if (recv) {
        NDIlib_recv_destroy(recv);
        LOGI("Receiver destruido");
    }
    
    // Liberar finder si existe
    NDIlib_find_instance_t find = ndi_find.exchange(nullptr);
    if (find) {
        NDIlib_find_destroy(find);
        LOGI("Finder destruido");
    }
    
    // Limpiar frame actual
    has_video_frame.store(false);
    
    // Destruir NDI SDK
    NDIlib_destroy();
    LOGI("NDI SDK cerrado completamente");
}

// Funciones para MainActivityOptimized - Compatible con MainActivity
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeInitializeNDI(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeInitializeNDI(env, thiz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeStartDiscovery(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeStartDiscovery(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeGetSourceCount(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeGetSourceCount(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeGetSourceName(JNIEnv *env, jobject thiz, jint index) {
    return Java_ndiplayer_oto_MainActivity_nativeGetSourceName(env, thiz, index);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeConnectToSource(JNIEnv *env, jobject thiz, jint index) {
    return Java_ndiplayer_oto_MainActivity_nativeConnectToSource(env, thiz, index);
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeDisconnect(JNIEnv *env, jobject thiz) {
    Java_ndiplayer_oto_MainActivity_nativeDisconnect(env, thiz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeIsConnected(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeIsConnected(env, thiz);
}

extern "C" JNIEXPORT jobject JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeGetFrame(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeGetFrame(env, thiz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeHasFrame(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeHasFrame(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeGetConnectionInfo(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeGetConnectionInfo(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivityOptimized_nativeGetFrameRate(JNIEnv *env, jobject thiz) {
    return Java_ndiplayer_oto_MainActivity_nativeGetFrameRate(env, thiz);
}
