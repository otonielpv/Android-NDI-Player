#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstring>

// NDI SDK - Full headers from official SDK
#include "ndi/include/Processing.NDI.Lib.h"
#include "ndi/include/Processing.NDI.Recv.h"
#include "ndi/include/Processing.NDI.FrameSync.h"
#include "ndi/include/Processing.NDI.Find.h"
#include "ndi/include/Processing.NDI.structs.h"

#define LOG_TAG "NDI_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// FULL IMPLEMENTATION - DISCOVERY AND RECEIVING
static NDIlib_find_instance_t ndi_find = nullptr;
static NDIlib_recv_instance_t ndi_recv = nullptr;
static const NDIlib_source_t* current_sources = nullptr;
static uint32_t num_sources = 0;
static NDIlib_video_frame_v2_t current_video_frame;
static bool has_video_frame = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeInitializeNDI(JNIEnv *env, jobject thiz) {
    LOGI("Initializing NDI SDK - FULL MODE");

    try {
        if (!NDIlib_initialize()) {
            LOGE("Failed to initialize NDI SDK");
            return JNI_FALSE;
        }
        
        // Initialize video frame structure
        memset(&current_video_frame, 0, sizeof(current_video_frame));
        has_video_frame = false;
        
        LOGI("NDI SDK initialized successfully");
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

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeDestroyNDI(JNIEnv *env, jobject thiz) {
    LOGI("Destroying NDI SDK - FULL MODE");
    
    // Disconnect from any active receiver
    if (ndi_recv) {
        // Free any current video frame
        if (has_video_frame) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        NDIlib_recv_destroy(ndi_recv);
        ndi_recv = nullptr;
    }
    
    if (ndi_find) {
        NDIlib_find_destroy(ndi_find);
        ndi_find = nullptr;
    }
    
    current_sources = nullptr;
    num_sources = 0;
    
    NDIlib_destroy();
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

extern "C" JNIEXPORT jobjectArray JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetSources(JNIEnv *env, jobject thiz) {
    LOGI("Getting NDI sources - FULL MODE");
    
    try {
        // Get String class for array creation
        jclass stringClass = env->FindClass("java/lang/String");
        if (!stringClass) {
            LOGE("Failed to find String class");
            return nullptr;
        }
        
        if (!ndi_find) {
            LOGI("No find instance available - returning empty array");
            return env->NewObjectArray(0, stringClass, nullptr);
        }
        
        // Wait for sources with timeout
        LOGI("Waiting for sources...");
        bool wait_result = NDIlib_find_wait_for_sources(ndi_find, 3000);
        LOGI("Wait result: %s", wait_result ? "true" : "false");
        
        // Get current sources and store them globally
        current_sources = NDIlib_find_get_current_sources(ndi_find, &num_sources);
        
        LOGI("Found %d sources, pointer: %p", num_sources, current_sources);
        
        if (num_sources == 0 || !current_sources) {
            LOGI("No sources available - returning empty array");
            return env->NewObjectArray(0, stringClass, nullptr);
        }
        
        // Create Java array
        jobjectArray result = env->NewObjectArray(num_sources, stringClass, nullptr);
        if (!result) {
            LOGE("Failed to create Java array");
            return nullptr;
        }
        
        // Add sources to array
        for (uint32_t i = 0; i < num_sources; i++) {
            const char* source_name = current_sources[i].p_ndi_name ? current_sources[i].p_ndi_name : "Unknown Source";
            LOGI("Adding source[%d]: %s", i, source_name);
            
            jstring jstr = env->NewStringUTF(source_name);
            if (jstr) {
                env->SetObjectArrayElement(result, i, jstr);
                env->DeleteLocalRef(jstr);
            }
        }
        
        LOGI("Successfully created source array with %d elements", num_sources);
        return result;
        
    } catch (...) {
        LOGE("Exception in nativeGetSources");
        return nullptr;
    }
}

// Additional function to get the number of sources
extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetSourceCount(JNIEnv *env, jobject thiz) {
    LOGI("Getting NDI source count: %d", num_sources);
    return (jint)num_sources;
}

// Additional function to get source name by index for debugging
extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetSourceName(JNIEnv *env, jobject thiz, jint index) {
    LOGI("Getting source name for index: %d", (int)index);
    
    if (index < 0 || index >= (jint)num_sources || !current_sources) {
        LOGE("Invalid index for source name: %d (total: %d)", (int)index, num_sources);
        return env->NewStringUTF("INVALID_INDEX");
    }
    
    const char* source_name = current_sources[index].p_ndi_name ? 
                              current_sources[index].p_ndi_name : "Unknown Source";
    
    LOGI("Source[%d] name: %s", (int)index, source_name);
    return env->NewStringUTF(source_name);
}

// REAL CONNECTION FUNCTIONALITY - Using Official NDI Example Pattern
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeConnectToSource(JNIEnv *env, jobject thiz, jstring sourceName, jstring sourceUrl) {
    // Convert Java strings to C strings
    const char* source_name_cstr = env->GetStringUTFChars(sourceName, 0);
    const char* source_url_cstr = env->GetStringUTFChars(sourceUrl, 0);
    
    LOGI("=== STARTING NDI CONNECTION (Official Example Pattern) ===");
    LOGI("Connecting to source name: %s, URL: %s", source_name_cstr, source_url_cstr);
    
    try {
        // Clean up any existing receiver first
        if (ndi_recv) {
            LOGI("Cleaning up existing receiver...");
            if (has_video_frame) {
                NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
                has_video_frame = false;
            }
            NDIlib_recv_destroy(ndi_recv);
            ndi_recv = nullptr;
        }
        
        // Step 1: Create a finder (Following official example, corrected for simplified SDK)
        LOGI("Creating NDI finder...");
        
        // Create finder settings
        NDIlib_find_create_t find_desc;
        memset(&find_desc, 0, sizeof(find_desc));
        find_desc.show_local_sources = true;
        find_desc.p_groups = nullptr;
        find_desc.p_extra_ips = nullptr;
        
        NDIlib_find_instance_t pNDI_find = NDIlib_find_create_v2(&find_desc);
        if (!pNDI_find) {
            LOGE("Failed to create NDI finder");
            env->ReleaseStringUTFChars(sourceName, source_name_cstr);
            env->ReleaseStringUTFChars(sourceUrl, source_url_cstr);
            return JNI_FALSE;
        }
        
        // Step 2: Wait for sources (Following official example)
        LOGI("Waiting for sources to be available...");
        NDIlib_find_wait_for_sources(pNDI_find, 2000);
        
        uint32_t no_sources = 0;
        const NDIlib_source_t* p_sources = NDIlib_find_get_current_sources(pNDI_find, &no_sources);
        
        LOGI("Found %d sources after wait", no_sources);
        
        // Step 3: Find our specific source
        const NDIlib_source_t* target_source = nullptr;
        for (uint32_t i = 0; i < no_sources; i++) {
            const char* current_name = p_sources[i].p_ndi_name ? p_sources[i].p_ndi_name : "";
            LOGI("Checking source[%d]: %s", i, current_name);
            if (strcmp(current_name, source_name_cstr) == 0) {
                target_source = &p_sources[i];
                LOGI("Found target source at index %d", i);
                break;
            }
        }
        
        if (!target_source) {
            LOGE("Source not found: %s", source_name_cstr);
            NDIlib_find_destroy(pNDI_find);
            env->ReleaseStringUTFChars(sourceName, source_name_cstr);
            env->ReleaseStringUTFChars(sourceUrl, source_url_cstr);
            return JNI_FALSE;
        }
        
        // Step 4: Create receiver using OFFICIAL EXAMPLE PATTERN (with full SDK)
        LOGI("Creating NDI receiver (official pattern with full SDK)...");
        ndi_recv = NDIlib_recv_create_v3(nullptr);
        
        if (!ndi_recv) {
            LOGE("Failed to create NDI receiver");
            NDIlib_find_destroy(pNDI_find);
            env->ReleaseStringUTFChars(sourceName, source_name_cstr);
            env->ReleaseStringUTFChars(sourceUrl, source_url_cstr);
            return JNI_FALSE;
        }
        
        LOGI("NDI receiver created successfully: %p", ndi_recv);
        
        // Step 5: Connect to the source using NDIlib_recv_connect (NOW AVAILABLE!)
        LOGI("Connecting to source using NDIlib_recv_connect (full SDK)...");
        NDIlib_recv_connect(ndi_recv, target_source);
        LOGI("Connection to source completed successfully");
        
        // Step 6: Clean up finder (Following official example)
        NDIlib_find_destroy(pNDI_find);
        
        LOGI("=== NDI CONNECTION COMPLETED SUCCESSFULLY (Official Pattern) ===");
        
    } catch (const std::exception& e) {
        LOGE("Standard exception caught: %s", e.what());
        if (ndi_recv) {
            NDIlib_recv_destroy(ndi_recv);
            ndi_recv = nullptr;
        }
        env->ReleaseStringUTFChars(sourceName, source_name_cstr);
        env->ReleaseStringUTFChars(sourceUrl, source_url_cstr);
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown exception caught during connection");
        if (ndi_recv) {
            NDIlib_recv_destroy(ndi_recv);
            ndi_recv = nullptr;
        }
        env->ReleaseStringUTFChars(sourceName, source_name_cstr);
        env->ReleaseStringUTFChars(sourceUrl, source_url_cstr);
        return JNI_FALSE;
    }
    
    // Clean up strings
    env->ReleaseStringUTFChars(sourceName, source_name_cstr);
    env->ReleaseStringUTFChars(sourceUrl, source_url_cstr);
    
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeCaptureFrame(JNIEnv *env, jobject thiz, jintArray widthHeight, jint timeoutMs) {
    if (!ndi_recv) {
        return -1; // Error
    }
    
    try {
        // Free any previous video frame
        if (has_video_frame && current_video_frame.p_data) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        // Use static variables to reduce allocations
        static NDIlib_video_frame_v2_t video_frame;
        static NDIlib_audio_frame_v2_t audio_frame;
        static NDIlib_metadata_frame_t metadata_frame;
        
        // Clear structures
        memset(&video_frame, 0, sizeof(video_frame));
        memset(&audio_frame, 0, sizeof(audio_frame));
        memset(&metadata_frame, 0, sizeof(metadata_frame));
        
        // Capture with specified timeout
        NDIlib_frame_type_e frame_type = NDIlib_recv_capture_v2(ndi_recv, &video_frame, &audio_frame, &metadata_frame, timeoutMs);
        
        switch (frame_type) {
            case NDIlib_frame_type_none:
                return 0; // No frame
                
            case NDIlib_frame_type_video:
                // Reduced logging for better performance
                current_video_frame = video_frame;
                has_video_frame = true;
                
                // Fast dimension setting
                if (widthHeight) {
                    jint* dimensions = env->GetIntArrayElements(widthHeight, nullptr);
                    if (dimensions) {
                        dimensions[0] = video_frame.xres;
                        dimensions[1] = video_frame.yres;
                        env->ReleaseIntArrayElements(widthHeight, dimensions, 0);
                        LOGI("Dimensions set successfully in array");
                    } else {
                        LOGE("Failed to get array elements for dimensions");
                    }
                } else {
                    LOGE("widthHeight array is null");
                }
                
                return 1; // Video frame
                
            case NDIlib_frame_type_audio:
                LOGI("Audio frame received: %d samples", audio_frame.no_samples);
                NDIlib_recv_free_audio_v2(ndi_recv, &audio_frame);
                return 2; // Audio frame
                
            case NDIlib_frame_type_metadata:
                LOGI("Metadata frame received");
                return 3; // Metadata frame
                
            default:
                LOGI("Unknown frame type received: %d", frame_type);
                return -1; // Error
        }
        
    } catch (const std::exception& e) {
        LOGE("Exception during frame capture: %s", e.what());
        return -1; // Error
    } catch (...) {
        LOGE("Unknown exception during frame capture");
        return -1; // Error
    }
}

// Frame capture functionality using official NDI example pattern (original version)
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeCaptureFrameOriginal(JNIEnv *env, jobject thiz) {
    if (!ndi_recv) {
        LOGE("No NDI receiver available for frame capture");
        return JNI_FALSE;
    }
    
    try {
        // Free any previous video frame
        if (has_video_frame) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        // Capture frame using official example pattern
        NDIlib_video_frame_v2_t video_frame;
        NDIlib_audio_frame_v2_t audio_frame;
        NDIlib_metadata_frame_t metadata_frame;  // Correct type for SDK
        
        // Capture with 5 second timeout (following official example)
        NDIlib_frame_type_e frame_type = NDIlib_recv_capture_v2(ndi_recv, &video_frame, &audio_frame, &metadata_frame, 5000);
        
        switch (frame_type) {
            case NDIlib_frame_type_none:
                LOGI("No data received from NDI source");
                return JNI_FALSE;
                
            case NDIlib_frame_type_video:
                LOGI("Video frame received: %dx%d", video_frame.xres, video_frame.yres);
                current_video_frame = video_frame;
                has_video_frame = true;
                return JNI_TRUE;
                
            case NDIlib_frame_type_audio:
                LOGI("Audio frame received: %d samples", audio_frame.no_samples);
                NDIlib_recv_free_audio_v2(ndi_recv, &audio_frame);
                return JNI_FALSE; // We only care about video frames for now
                
            default:
                LOGI("Unknown frame type received: %d", frame_type);
                return JNI_FALSE;
        }
        
    } catch (const std::exception& e) {
        LOGE("Exception during frame capture: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown exception during frame capture");
        return JNI_FALSE;
    }
}

// Advanced frame capture using FrameSync (NOW AVAILABLE with full SDK)
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeCaptureFrameSync(JNIEnv *env, jobject thiz) {
    if (!ndi_recv) {
        LOGE("No NDI receiver available for FrameSync capture");
        return JNI_FALSE;
    }
    
    try {
        LOGI("Creating FrameSync instance for advanced capture...");
        
        // Create FrameSync instance (NOW AVAILABLE!)
        NDIlib_framesync_instance_t pNDI_framesync = NDIlib_framesync_create(ndi_recv);
        if (!pNDI_framesync) {
            LOGE("Failed to create FrameSync instance");
            return JNI_FALSE;
        }
        
        LOGI("FrameSync created successfully, capturing synchronized frame...");
        
        // Free any previous video frame
        if (has_video_frame) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        // Capture video using FrameSync (always gets a frame)
        NDIlib_framesync_capture_video(pNDI_framesync, &current_video_frame);
        
        if (current_video_frame.p_data) {
            LOGI("FrameSync video frame captured: %dx%d", current_video_frame.xres, current_video_frame.yres);
            has_video_frame = true;
            
            // Clean up FrameSync
            NDIlib_framesync_destroy(pNDI_framesync);
            return JNI_TRUE;
        } else {
            LOGI("FrameSync returned empty frame");
            NDIlib_framesync_destroy(pNDI_framesync);
            return JNI_FALSE;
        }
        
    } catch (const std::exception& e) {
        LOGE("Exception during FrameSync capture: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown exception during FrameSync capture");
        return JNI_FALSE;
    }
}

// Get frame info using official pattern
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

// Legacy function for integer index (keeping for compatibility)
extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeConnectToSourceByIndex(JNIEnv *env, jobject thiz, jint index) {
    LOGI("nativeConnectToSourceByIndex called - CONNECTING to source index: %d", (int)index);
    LOGI("Available sources: %d, current_sources pointer: %p", num_sources, current_sources);
    
    try {
        // Validate index bounds
        if (index < 0 || index >= (jint)num_sources || !current_sources) {
            LOGE("Invalid source index: %d (total sources: %d, sources pointer: %p)", 
                 (int)index, num_sources, current_sources);
            return JNI_FALSE;
        }
        
        // Log the source we're trying to connect to
        const char* source_name = current_sources[index].p_ndi_name ? 
                                  current_sources[index].p_ndi_name : "Unknown";
        LOGI("Attempting to connect to source[%d]: %s", (int)index, source_name);
        
        // Disconnect from any existing receiver
        if (ndi_recv) {
            LOGI("Disconnecting from previous receiver");
            if (has_video_frame) {
                NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
                has_video_frame = false;
            }
            NDIlib_recv_destroy(ndi_recv);
            ndi_recv = nullptr;
        }
        
        // Create receiver settings according to available NDI version
        NDIlib_recv_create_v3_t recv_desc;
        recv_desc.source_to_connect_to = current_sources[index];
        recv_desc.p_ndi_recv_name = "Android NDI Player";
        recv_desc.bandwidth = NDIlib_recv_bandwidth_highest; // Use highest quality
        recv_desc.allow_video_fields = true;
        
        LOGI("Creating NDI receiver with settings...");
        
        // Create the receiver
        ndi_recv = NDIlib_recv_create_v3(&recv_desc);
        if (!ndi_recv) {
            LOGE("Failed to create NDI receiver for source: %s", source_name);
            return JNI_FALSE;
        }
        
        LOGI("Successfully connected to NDI source: %s", source_name);
        
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("Exception in nativeConnectToSourceByIndex");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeDisconnect(JNIEnv *env, jobject thiz) {
    LOGI("nativeDisconnect called - Disconnecting from NDI source");
    
    try {
        if (ndi_recv) {
            // Free any current video frame
            if (has_video_frame) {
                NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
                has_video_frame = false;
            }
            
            // Destroy the receiver
            NDIlib_recv_destroy(ndi_recv);
            ndi_recv = nullptr;
            
            LOGI("Successfully disconnected from NDI source");
        } else {
            LOGI("No active connection to disconnect");
        }
    } catch (...) {
        LOGE("Exception in nativeDisconnect");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_MainActivity_nativeHasFrame(JNIEnv *env, jobject thiz) {
    if (!ndi_recv) {
        return JNI_FALSE;
    }
    
    try {
        // Free previous frame if we have one
        if (has_video_frame) {
            NDIlib_recv_free_video_v2(ndi_recv, &current_video_frame);
            has_video_frame = false;
        }
        
        // Try to capture a new frame (non-blocking)
        NDIlib_audio_frame_v2_t audio_frame;
        NDIlib_metadata_frame_t metadata_frame;
        
        switch (NDIlib_recv_capture_v2(ndi_recv, &current_video_frame, &audio_frame, &metadata_frame, 0)) {
            case NDIlib_frame_type_video:
                has_video_frame = true;
                LOGI("Received video frame: %dx%d, FourCC: 0x%08X, line_stride: %d", 
                     current_video_frame.xres, current_video_frame.yres, 
                     current_video_frame.FourCC, current_video_frame.line_stride_in_bytes);
                
                // Free any audio or metadata that might have been received
                if (audio_frame.p_data) {
                    NDIlib_recv_free_audio_v2(ndi_recv, &audio_frame);
                }
                
                return JNI_TRUE;
                
            case NDIlib_frame_type_audio:
                // We got audio but no video, free it
                if (audio_frame.p_data) {
                    NDIlib_recv_free_audio_v2(ndi_recv, &audio_frame);
                }
                return JNI_FALSE;
                
            case NDIlib_frame_type_metadata:
                // We got metadata but no video, ignore it
                return JNI_FALSE;
                
            case NDIlib_frame_type_none:
                // No frame available
                return JNI_FALSE;
                
            case NDIlib_frame_type_status_change:
                LOGI("NDI receiver status changed");
                return JNI_FALSE;
                
            default:
                return JNI_FALSE;
        }
        
    } catch (...) {
        LOGE("Exception in nativeHasFrame");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameData(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame || !current_video_frame.p_data) {
        LOGI("nativeGetFrameData called - No frame data available");
        return nullptr;
    }
    
    try {
        // Log frame format information
        LOGI("Frame format: FourCC=0x%08X, %dx%d, stride=%d bytes", 
             current_video_frame.FourCC, current_video_frame.xres, current_video_frame.yres,
             current_video_frame.line_stride_in_bytes);
        
        // Calculate frame data size
        int frame_size = current_video_frame.yres * current_video_frame.line_stride_in_bytes;
        
        LOGI("Creating frame data array: %dx%d, stride: %d, size: %d bytes", 
             current_video_frame.xres, current_video_frame.yres, 
             current_video_frame.line_stride_in_bytes, frame_size);
        
        // Create Java byte array
        jbyteArray result = env->NewByteArray(frame_size);
        if (!result) {
            LOGE("Failed to create Java byte array for frame data");
            return nullptr;
        }
        
        // Copy frame data to Java array
        env->SetByteArrayRegion(result, 0, frame_size, 
                               reinterpret_cast<const jbyte*>(current_video_frame.p_data));
        
        LOGI("Successfully copied %d bytes of frame data", frame_size);
        return result;
        
    } catch (...) {
        LOGE("Exception in nativeGetFrameData");
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameWidth(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame) {
        return 0;
    }
    return current_video_frame.xres;
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameHeight(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame) {
        return 0;
    }
    return current_video_frame.yres;
}

// Additional function to get frame stride information
extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameStride(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame) {
        return 0;
    }
    return current_video_frame.line_stride_in_bytes;
}

// Function to get frame format information
extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameFormat(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame) {
        return env->NewStringUTF("NONE");
    }
    
    std::string format_info = "FourCC_" + std::to_string(current_video_frame.FourCC);
    
    // Add frame rate information
    if (current_video_frame.frame_rate_N > 0 && current_video_frame.frame_rate_D > 0) {
        float fps = (float)current_video_frame.frame_rate_N / (float)current_video_frame.frame_rate_D;
        format_info += "_" + std::to_string((int)fps) + "fps";
    }
    
    // Add progressive/interlaced info
    if (current_video_frame.frame_format_type == NDIlib_frame_format_type_progressive) {
        format_info += "_Progressive";
    } else {
        format_info += "_Interlaced";
    }
    
    return env->NewStringUTF(format_info.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_MainActivity_nativeGetFrameFourCC(JNIEnv *env, jobject thiz) {
    if (!ndi_recv || !has_video_frame) {
        return 0;
    }
    return static_cast<jint>(current_video_frame.FourCC);
}
