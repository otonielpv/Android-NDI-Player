#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "NDI_NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_ndiplayer_oto_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    std::string hello = "NDI Player Native Library Loaded";
    LOGI("Native library initialized");
    return env->NewStringUTF(hello.c_str());
}
