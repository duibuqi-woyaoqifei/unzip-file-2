package com.example.unzipfile

import android.content.Context
import android.content.SharedPreferences
import com.example.unzipfile.membership.OperationType
import com.example.unzipfile.membership.UserOperationLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 用户操作日志记录器
 * 记录用户的关键操作，用于后台数据分析
 */
class OperationLogger private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "operation_logs"
        private const val KEY_OPERATION_LOGS = "operation_logs"

        @Volatile
        private var instance: OperationLogger? = null

        fun getInstance(context: Context): OperationLogger {
            return instance ?: synchronized(this) {
                instance ?: OperationLogger(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 记录用户操作
     */
    fun logOperation(
        userId: String,
        operation: OperationType,
        details: String,
        deviceId: String = "",
        ipAddress: String = ""
    ) {
        val currentLogs = getAllLogs().toMutableList()

        val logEntry = UserOperationLog(
            id = generateLogId(),
            userId = userId,
            operation = operation,
            details = details,
            ipAddress = ipAddress.ifEmpty { getCurrentIpAddress() },
            deviceId = deviceId.ifEmpty { getCurrentDeviceId() },
            timestamp = System.currentTimeMillis()
        )

        currentLogs.add(logEntry)

        // 只保留最近1000条日志
        if (currentLogs.size > 1000) {
            currentLogs.removeAt(0)
        }

        saveLogs(currentLogs)
    }

    /**
     * 获取用户的操作日志
     */
    fun getUserLogs(userId: String, limit: Int = 50): List<UserOperationLog> {
        return getAllLogs()
            .filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * 获取所有操作日志（管理员功能）
     */
    fun getAllLogs(): List<UserOperationLog> {
        val logsJson = prefs.getString(KEY_OPERATION_LOGS, "[]")
        return try {
            val type = object : TypeToken<List<UserOperationLog>>() {}.type
            gson.fromJson(logsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清除指定用户的日志（隐私保护）
     */
    fun clearUserLogs(userId: String) {
        val updatedLogs = getAllLogs().filter { it.userId != userId }
        saveLogs(updatedLogs)
    }

    /**
     * 获取操作统计信息
     */
    fun getOperationStats(userId: String): OperationStats {
        val userLogs = getUserLogs(userId, 1000)

        return OperationStats(
            totalOperations = userLogs.size,
            operationCounts = userLogs.groupBy { it.operation }.mapValues { it.value.size },
            lastActivity = userLogs.maxByOrNull { it.timestamp }?.timestamp ?: 0L,
            firstActivity = userLogs.minByOrNull { it.timestamp }?.timestamp ?: 0L
        )
    }

    /**
     * 导出日志数据（JSON格式）
     */
    fun exportLogs(userId: String? = null): String {
        val logs = if (userId != null) {
            getUserLogs(userId, 1000)
        } else {
            getAllLogs()
        }
        return gson.toJson(logs)
    }

    /**
     * 获取当前设备ID
     */
    private fun getCurrentDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    /**
     * 获取当前IP地址（简化版，实际应用中需要网络请求）
     */
    private fun getCurrentIpAddress(): String {
        // 在实际应用中，这里应该通过API获取用户的真实IP
        // 现在返回一个占位符
        return "unknown_ip"
    }

    /**
     * 生成日志ID
     */
    private fun generateLogId(): String {
        return "log_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    /**
     * 保存日志到SharedPreferences
     */
    private fun saveLogs(logs: List<UserOperationLog>) {
        val logsJson = gson.toJson(logs)
        prefs.edit().putString(KEY_OPERATION_LOGS, logsJson).apply()
    }
}

/**
 * 操作统计数据类
 */
data class OperationStats(
    val totalOperations: Int,
    val operationCounts: Map<OperationType, Int>,
    val lastActivity: Long,
    val firstActivity: Long
)
