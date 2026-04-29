#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "UnzipNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include "ZipDecompressor.cpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_unzipfile_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ unzip-lib";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_unzipfile_NativeLib_extract(
        JNIEnv* env,
        jobject thiz,
        jint fd,
        jstring destPath) {
    
    // Find Java callback method
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID onProgressId = env->GetMethodID(clazz, "onNativeProgress", "(ILjava/lang/String;)V");

    const char* path = env->GetStringUTFChars(destPath, nullptr);
    std::string destPathStr(path);
    env->ReleaseStringUTFChars(destPath, path);

    ZipDecompressor decompressor;
    
    int result = decompressor.extract(fd, destPathStr, [&](int progress) {
        // Mocking some file names for UI wow factor
        std::string currentFile = "extracting_data_chunk_" + std::to_string(progress) + ".dat";
        jstring jFileName = env->NewStringUTF(currentFile.c_str());
        
        // Call Java callback
        env->CallVoidMethod(thiz, onProgressId, (jint)progress, jFileName);
        
        env->DeleteLocalRef(jFileName);
    });

    return result;
}
