#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "Processing.NDI.Lib.h"

#define LOG_TAG "NDI_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static NDIlib_find_instance_t ndi_find = nullptr;
static std::vector<NDIlib_recv_instance_t> ndi_receivers;

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeInitializeNDI(JNIEnv *env, jobject thiz) {
    LOGI("Initializing NDI SDK - SAFE MODE");

    try {
        // For now, just return true without initializing NDI to prevent crashes
        // We'll add NDI functionality gradually
        LOGI("NDI SDK - returning success without initialization (safe mode)");
        return JNI_TRUE;
    } catch (...) {
        LOGE("Exception during NDI initialization");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeDestroyNDI(JNIEnv *env, jobject thiz) {
    LOGI("Destroying NDI SDK - SAFE MODE");
    // Safe cleanup - no actual NDI calls for now
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeStartDiscovery(JNIEnv *env, jobject thiz) {
    LOGI("Starting NDI discovery - SAFE MODE");
    
    try {
        // Return success without starting actual discovery
        LOGI("NDI discovery - returning success (safe mode)");
        return JNI_TRUE;
    } catch (...) {
        LOGE("Exception during NDI discovery start");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_ndiplayer_oto_service_NDIDiscoveryService_nativeGetSources(JNIEnv *env, jobject thiz) {
    LOGI("Getting NDI sources - SAFE MODE");
    
    // Always return empty array to prevent crashes
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) {
        LOGE("Failed to find String class");
        return nullptr;
    }
    
    try {
        LOGI("Returning empty source array (safe mode)");
        return env->NewObjectArray(0, stringClass, nullptr);
        
    } catch (...) {
        LOGE("Exception occurred while getting NDI sources");
        return env->NewObjectArray(0, stringClass, nullptr);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_ndiplayer_oto_service_NDIReceiver_nativeCreateReceiver(JNIEnv *env, jobject thiz,
                                                             jstring source_name, jstring source_url) {
    LOGI("Creating NDI receiver - SAFE MODE (returning null)");
    // Return 0 to indicate no receiver created
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_service_NDIReceiver_nativeDestroyReceiver(JNIEnv *env, jobject thiz, jlong receiver_ptr) {
    LOGI("Destroying NDI receiver - SAFE MODE");
    // Safe - no actual cleanup needed
}

extern "C" JNIEXPORT jint JNICALL
Java_ndiplayer_oto_service_NDIReceiver_nativeCaptureFrame(JNIEnv *env, jobject thiz, jlong receiver_ptr,
                                                          jbyteArray video_data, jint timeout_ms) {
    LOGI("Capturing frame - SAFE MODE (returning no frame)");
    return 0; // No frame available
}
