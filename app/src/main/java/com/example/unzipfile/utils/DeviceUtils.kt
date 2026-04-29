package com.example.unzipfile.utils

import android.content.Context
import android.provider.Settings
import android.os.Build
import java.security.MessageDigest

/**
 * 设备指纹工具类
 * 生成唯一、难以篡改的硬件标识，用于防刷、防破解
 */
object DeviceUtils {

    /**
     * 获取高强度硬件指纹 (SHA-256)
     */
    fun getDeviceFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val hardwareInfo = StringBuilder()
            .append(Build.BOARD)
            .append(Build.BRAND)
            .append(Build.DEVICE)
            .append(Build.DISPLAY)
            .append(Build.FINGERPRINT)
            .append(Build.HARDWARE)
            .append(Build.ID)
            .append(Build.MANUFACTURER)
            .append(Build.MODEL)
            .append(Build.PRODUCT)
            .toString()

        val rawId = androidId + hardwareInfo
        return hashString(rawId)
    }

    private fun hashString(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}
