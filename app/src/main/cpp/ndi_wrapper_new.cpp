#include <jni.h>
#include <string>
#include <android/log.h>
#include "ndi/include/Processing.NDI.Lib.h"

// Logging macros
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NDI_WRAPPER", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NDI_WRAPPER", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "NDI_WRAPPER", __VA_ARGS__)

// Global NDI state - following official documentation patterns
static NDIlib_find_instance_t ndi_find = nullptr;
static NDIlib_recv_instance_t ndi_receiver = nullptr;
static const NDIlib_source_t* discovered_sources = nullptr;
static uint32_t num_discovered_sources = 0;

// =============================================================================
// NDI INITIALIZATION - Based on official documentation
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeInitializeNDI(JNIEnv *env, jobject thiz) {
    LOGI("=== INITIALIZING NDI (Official Documentation Pattern) ===");
    
    // Initialize the NDI SDK - this is the first step per documentation
    if (!NDIlib_initialize()) {
        LOGE("Failed to initialize NDI SDK");
        return JNI_FALSE;
    }
    
    LOGI("NDI SDK initialized successfully");
    
    // Create a finder to discover sources on the network
    // Using default settings as recommended in documentation
    NDIlib_find_create_t find_create = {};
    find_create.show_local_sources = true;  // Show local sources
    find_create.p_groups = nullptr;         // All groups
    find_create.p_extra_ips = nullptr;      // No extra IPs
    
    ndi_find = NDIlib_find_create_v2(&find_create);
    if (!ndi_find) {
        LOGE("Failed to create NDI finder");
        NDIlib_destroy();
        return JNI_FALSE;
    }
    
    LOGI("NDI finder created successfully");
    return JNI_TRUE;
}

// =============================================================================
// SOURCE DISCOVERY - Following official documentation
// =============================================================================

extern "C" JNIEXPORT jobjectArray JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetSources(JNIEnv *env, jobject thiz) {
    LOGI("=== DISCOVERING NDI SOURCES (Official Pattern) ===");
    
    if (!ndi_find) {
        LOGE("NDI finder not initialized");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }
    
    // Wait for sources to be discovered (as per documentation)
    LOGI("Waiting for source discovery...");
    
    // Give the finder time to discover sources (recommended in docs)
    for (int i = 0; i < 10; i++) {  // Wait up to 1 second
        discovered_sources = NDIlib_find_get_current_sources(ndi_find, &num_discovered_sources);
        if (num_discovered_sources > 0) {
            break;
        }
        usleep(100000); // 100ms
    }
    
    LOGI("Found %d NDI sources", num_discovered_sources);
    
    // Create Java string array for the sources
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(num_discovered_sources, stringClass, nullptr);
    
    for (uint32_t i = 0; i < num_discovered_sources; i++) {
        const char* source_name = discovered_sources[i].p_ndi_name;
        LOGI("Source %d: %s", i, source_name ? source_name : "NULL");
        
        jstring jname = env->NewStringUTF(source_name ? source_name : "Unknown Source");
        env->SetObjectArrayElement(result, i, jname);
        env->DeleteLocalRef(jname);
    }
    
    return result;
}

// =============================================================================
// RECEIVER CREATION - Following official NDI documentation exactly
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeConnectToSource(JNIEnv *env, jobject thiz, jstring source_name) {
    LOGI("=== CREATING NDI RECEIVER (Official Documentation) ===");
    
    if (!source_name) {
        LOGE("Source name is null");
        return JNI_FALSE;
    }
    
    const char* name_str = env->GetStringUTFChars(source_name, nullptr);
    LOGI("Connecting to source: %s", name_str);
    
    // Clean up existing receiver if any
    if (ndi_receiver) {
        LOGI("Destroying existing receiver");
        NDIlib_recv_destroy(ndi_receiver);
        ndi_receiver = nullptr;
    }
    
    // Find the source we want to connect to
    const NDIlib_source_t* target_source = nullptr;
    for (uint32_t i = 0; i < num_discovered_sources; i++) {
        if (discovered_sources[i].p_ndi_name && 
            strcmp(discovered_sources[i].p_ndi_name, name_str) == 0) {
            target_source = &discovered_sources[i];
            break;
        }
    }
    
    if (!target_source) {
        LOGE("Source not found: %s", name_str);
        env->ReleaseStringUTFChars(source_name, name_str);
        return JNI_FALSE;
    }
    
    // Create receiver settings following official documentation
    NDIlib_recv_create_v3_t recv_create = {};
    
    // Set the source to connect to (exact format from NDIlib_find_get_sources)
    recv_create.source_to_connect_to = *target_source;
    
    // Give receiver a meaningful name as recommended
    recv_create.p_ndi_recv_name = "NDI Android Receiver";
    
    // Use highest bandwidth for best quality (official recommendation)
    recv_create.bandwidth = NDIlib_recv_bandwidth_highest;
    
    // Allow progressive video (recommended for most applications)
    recv_create.allow_video_fields = false;
    
    // Use fastest color format for best performance (official recommendation)
    recv_create.color_format = NDIlib_recv_color_format_fastest;
    
    LOGI("Creating receiver with official settings:");
    LOGI("  Source: %s", target_source->p_ndi_name);
    LOGI("  Receiver name: %s", recv_create.p_ndi_recv_name);
    LOGI("  Bandwidth: highest");
    LOGI("  Allow fields: %s", recv_create.allow_video_fields ? "true" : "false");
    
    // Create the receiver using official API
    ndi_receiver = NDIlib_recv_create_v3(&recv_create);
    
    if (!ndi_receiver) {
        LOGE("Failed to create NDI receiver");
        env->ReleaseStringUTFChars(source_name, name_str);
        return JNI_FALSE;
    }
    
    LOGI("NDI receiver created successfully: %p", ndi_receiver);
    
    env->ReleaseStringUTFChars(source_name, name_str);
    return JNI_TRUE;
}

// =============================================================================
// FRAME CAPTURE - Following official documentation example
// =============================================================================

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeCaptureFrame(JNIEnv *env, jobject thiz, jintArray width_height, jint timeout_ms) {
    if (!ndi_receiver) {
        return -1; // No receiver
    }
    
    // Create frame structures as per official documentation
    NDIlib_video_frame_v2_t video_frame;
    NDIlib_audio_frame_v2_t audio_frame;
    NDIlib_metadata_frame_t metadata_frame;
    
    // Capture frame using official API - exact pattern from documentation
    NDIlib_frame_type_e frame_type = NDIlib_recv_capture_v2(
        ndi_receiver,
        &video_frame,
        &audio_frame,
        &metadata_frame,
        timeout_ms
    );
    
    // Handle frame types as per official documentation example
    switch (frame_type) {
        case NDIlib_frame_type_video:
            LOGI("Received video frame: %dx%d", video_frame.xres, video_frame.yres);
            
            // Return frame dimensions to Java
            if (width_height) {
                jint* dimensions = env->GetIntArrayElements(width_height, nullptr);
                if (dimensions) {
                    dimensions[0] = video_frame.xres;
                    dimensions[1] = video_frame.yres;
                    env->ReleaseIntArrayElements(width_height, dimensions, 0);
                }
            }
            
            // Free the video frame as required by documentation
            NDIlib_recv_free_video_v2(ndi_receiver, &video_frame);
            return 1; // Video frame received
            
        case NDIlib_frame_type_audio:
            LOGI("Received audio frame");
            // Free the audio frame as required by documentation
            NDIlib_recv_free_audio_v2(ndi_receiver, &audio_frame);
            return 2; // Audio frame received
            
        case NDIlib_frame_type_metadata:
            LOGI("Received metadata frame");
            // Free the metadata as required by documentation
            NDIlib_recv_free_metadata(ndi_receiver, &metadata_frame);
            return 3; // Metadata received
            
        case NDIlib_frame_type_none:
            return 0; // No frame available
            
        case NDIlib_frame_type_status_change:
            LOGI("Status change detected");
            return 4; // Status change
            
        default:
            return 0; // Unknown frame type
    }
}

// =============================================================================
// DISCONNECT - Following official documentation
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeDisconnect(JNIEnv *env, jobject thiz) {
    LOGI("=== DISCONNECTING NDI RECEIVER ===");
    
    if (ndi_receiver) {
        NDIlib_recv_destroy(ndi_receiver);
        ndi_receiver = nullptr;
        LOGI("NDI receiver destroyed");
        return JNI_TRUE;
    }
    
    return JNI_FALSE;
}

// =============================================================================
// CONNECTION STATUS
// =============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetConnectionStatus(JNIEnv *env, jobject thiz) {
    if (ndi_receiver) {
        return env->NewStringUTF("Connected");
    } else {
        return env->NewStringUTF("Disconnected");
    }
}

// =============================================================================
// CLEANUP - Following official documentation
// =============================================================================

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeCleanup(JNIEnv *env, jobject thiz) {
    LOGI("=== NDI CLEANUP ===");
    
    // Cleanup receiver
    if (ndi_receiver) {
        NDIlib_recv_destroy(ndi_receiver);
        ndi_receiver = nullptr;
        LOGI("Receiver destroyed");
    }
    
    // Cleanup finder
    if (ndi_find) {
        NDIlib_find_destroy(ndi_find);
        ndi_find = nullptr;
        LOGI("Finder destroyed");
    }
    
    // Clear source references
    discovered_sources = nullptr;
    num_discovered_sources = 0;
    
    // Destroy NDI SDK
    NDIlib_destroy();
    LOGI("NDI SDK destroyed");
}
