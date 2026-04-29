package com.example.unzipfile

class NativeLib {
    companion object {
        init {
            System.loadLibrary("unzip-lib")
        }
    }

    var onProgress: ((Int, String) -> Unit)? = null

    external fun stringFromJNI(): String
    external fun extract(fd: Int, destPath: String): Int

    // Called by JNI
    fun onNativeProgress(progress: Int, fileName: String) {
        onProgress?.invoke(progress, fileName)
    }
}
