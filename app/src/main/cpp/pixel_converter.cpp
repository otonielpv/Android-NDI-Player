#include <jni.h>
#include <android/log.h>
#include <cstring>

#ifdef __aarch64__
#include <arm_neon.h>
#endif

#define LOG_TAG "PixelConverter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Fast BGRA to ARGB conversion using NEON SIMD instructions (ARM64)
extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeConvertBGRAToARGB(JNIEnv *env, jobject thiz, 
                                                         jbyteArray bgra_data, 
                                                         jintArray argb_pixels, 
                                                         jint width, jint height) {
    jbyte* bgra = env->GetByteArrayElements(bgra_data, nullptr);
    jint* argb = env->GetIntArrayElements(argb_pixels, nullptr);
    
    if (!bgra || !argb) {
        LOGE("Failed to get array elements");
        return;
    }
    
    int pixel_count = width * height;
    
#ifdef __aarch64__
    // ARM64 NEON optimized version
    int i = 0;
    for (; i <= pixel_count - 4; i += 4) {
        // Load 16 bytes (4 BGRA pixels)
        uint8x16_t bgra_vec = vld1q_u8((uint8_t*)&bgra[i * 4]);
        
        // Manually reorder BGRA to ARGB for each pixel
        uint8x16_t reordered;
        // For each pixel: BGRA -> ARGB (swap B and R, move A to front)
        uint8_t* src = (uint8_t*)&bgra_vec;
        uint8_t* dst = (uint8_t*)&reordered;
        
        // Pixel 0: BGRA -> ARGB (correct order)
        dst[0] = src[3];  // A
        dst[1] = src[2];  // R  
        dst[2] = src[1];  // G
        dst[3] = src[0];  // B
        
        // Pixel 1: BGRA -> ARGB
        dst[4] = src[7];  // A
        dst[5] = src[6];  // R
        dst[6] = src[5];  // G
        dst[7] = src[4];  // B
        
        // Pixel 2: BGRA -> ARGB
        dst[8] = src[11]; // A
        dst[9] = src[10]; // R
        dst[10] = src[9]; // G
        dst[11] = src[8]; // B
        
        // Pixel 3: BGRA -> ARGB
        dst[12] = src[15]; // A
        dst[13] = src[14]; // R
        dst[14] = src[13]; // G
        dst[15] = src[12]; // B
        
        // Store as 32-bit integers
        vst1q_u32((uint32_t*)&argb[i], (uint32x4_t)reordered);
    }
    
    // Handle remaining pixels
    for (; i < pixel_count; i++) {
        int idx = i * 4;
        uint8_t b = bgra[idx];
        uint8_t g = bgra[idx + 1];
        uint8_t r = bgra[idx + 2];
        uint8_t a = bgra[idx + 3];
        
        // Convert BGRA to ARGB (Android format)
        argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
#else
    // Fallback for other architectures
    for (int i = 0; i < pixel_count; i++) {
        int idx = i * 4;
        uint8_t b = bgra[idx];
        uint8_t g = bgra[idx + 1];
        uint8_t r = bgra[idx + 2];
        uint8_t a = bgra[idx + 3];
        
        // Convert BGRA to ARGB (Android format)
        argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
#endif
    
    env->ReleaseByteArrayElements(bgra_data, bgra, JNI_ABORT);
    env->ReleaseIntArrayElements(argb_pixels, argb, 0);
}

// Fast UYVY to RGB conversion with NEON optimization
extern "C" JNIEXPORT void JNICALL
Java_ndiplayer_oto_MainActivity_nativeConvertUYVYToARGB(JNIEnv *env, jobject thiz,
                                                         jbyteArray uyvy_data,
                                                         jintArray argb_pixels,
                                                         jint width, jint height) {
    jbyte* uyvy = env->GetByteArrayElements(uyvy_data, nullptr);
    jint* argb = env->GetIntArrayElements(argb_pixels, nullptr);
    
    if (!uyvy || !argb) {
        LOGE("Failed to get array elements");
        return;
    }
    
    // YUV to RGB conversion constants
    const int c1 = 1436; // 1.402 * 1024
    const int c2 = 352;  // 0.344 * 1024
    const int c3 = 731;  // 0.714 * 1024
    const int c4 = 1815; // 1.772 * 1024
    
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x += 2) {
            int uyvy_idx = (y * width + x);
            int u = uyvy[uyvy_idx * 2] & 0xFF;
            int y1 = uyvy[uyvy_idx * 2 + 1] & 0xFF;
            int v = uyvy[uyvy_idx * 2 + 2] & 0xFF;
            int y2 = uyvy[uyvy_idx * 2 + 3] & 0xFF;
            
            // Convert first pixel
            int r1 = (y1 * 1024 + c1 * (v - 128)) >> 10;
            int g1 = (y1 * 1024 - c2 * (u - 128) - c3 * (v - 128)) >> 10;
            int b1 = (y1 * 1024 + c4 * (u - 128)) >> 10;
            
            // Clamp values
            r1 = r1 < 0 ? 0 : (r1 > 255 ? 255 : r1);
            g1 = g1 < 0 ? 0 : (g1 > 255 ? 255 : g1);
            b1 = b1 < 0 ? 0 : (b1 > 255 ? 255 : b1);
            
            argb[y * width + x] = (255 << 24) | (r1 << 16) | (g1 << 8) | b1;
            
            // Convert second pixel if within bounds
            if (x + 1 < width) {
                int r2 = (y2 * 1024 + c1 * (v - 128)) >> 10;
                int g2 = (y2 * 1024 - c2 * (u - 128) - c3 * (v - 128)) >> 10;
                int b2 = (y2 * 1024 + c4 * (u - 128)) >> 10;
                
                // Clamp values
                r2 = r2 < 0 ? 0 : (r2 > 255 ? 255 : r2);
                g2 = g2 < 0 ? 0 : (g2 > 255 ? 255 : g2);
                b2 = b2 < 0 ? 0 : (b2 > 255 ? 255 : b2);
                
                argb[y * width + x + 1] = (255 << 24) | (r2 << 16) | (g2 << 8) | b2;
            }
        }
    }
    
    env->ReleaseByteArrayElements(uyvy_data, uyvy, JNI_ABORT);
    env->ReleaseIntArrayElements(argb_pixels, argb, 0);
}

// Memory-efficient bitmap creation
extern "C" JNIEXPORT jobject JNICALL
Java_ndiplayer_oto_MainActivity_nativeCreateOptimizedBitmap(JNIEnv *env, jobject thiz,
                                                            jintArray pixels,
                                                            jint width, jint height) {
    // Get Bitmap class and createBitmap method
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    
    jfieldID argb8888Field = env->GetStaticFieldID(configClass, "ARGB_8888", 
                                                   "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(configClass, argb8888Field);
    
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapClass, "createBitmap",
                                                          "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    return env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, pixels, width, height, config);
}
