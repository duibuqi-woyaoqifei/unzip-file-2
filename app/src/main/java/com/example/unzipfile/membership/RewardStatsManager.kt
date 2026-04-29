package com.example.unzipfile.membership

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*


/**
 * 收益图表数据项
 */
data class ChartEntry(
    val label: String,
    val value: Float
)

/**
 * 奖励统计管理器 (本地安全增强版)
 */
class RewardStatsManager private constructor(context: Context) {

    private val prefs: SharedPreferences
    private val gson = Gson()
    private val deviceFingerprint = com.example.unzipfile.utils.DeviceUtils.getDeviceFingerprint(context)

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "reward_stats_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_RECORDS = "reward_records_v2"
        private const val KEY_CHECKSUM = "records_integrity_hash"

        @Volatile
        private var instance: RewardStatsManager? = null

        fun getInstance(context: Context): RewardStatsManager {
            return instance ?: synchronized(this) {
                instance ?: RewardStatsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun recordReward(type: RewardType, days: Float, description: String) {
        synchronized(this) {
            val records = getAllRecords().toMutableList()
            val newRecord = RewardRecord(
                id = UUID.randomUUID().toString(),
                type = type,
                daysEarned = days,
                timestamp = System.currentTimeMillis(),
                description = description
            )
            records.add(newRecord)
            saveRecords(records)
        }
    }

    fun getAllRecords(): List<RewardRecord> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        val savedChecksum = prefs.getString(KEY_CHECKSUM, "")
        if (savedChecksum != calculateChecksum(json)) return emptyList()

        return try {
            val type = object : TypeToken<List<RewardRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getChartData(filterType: Int): List<ChartEntry> {
        val allRecords = getAllRecords()
        val calendar = Calendar.getInstance()
        return (0..6).reversed().map { offset ->
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -offset)
            val dateStr = "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
            val total = allRecords.filter { r -> isSameDay(r.timestamp, calendar.timeInMillis) }
                .sumOf { it.daysEarned.toDouble() }.toFloat()
            ChartEntry(dateStr, total)
        }
    }

    fun getStatsByType(): Map<RewardType, Float> {
        val allRecords = getAllRecords()
        val stats = mutableMapOf<RewardType, Float>()
        
        // 初始化所有类型为 0
        RewardType.values().forEach { stats[it] = 0f }
        
        allRecords.forEach { record ->
            val current = stats[record.type] ?: 0f
            stats[record.type] = current + record.daysEarned
        }
        return stats
    }

    fun getTotalRewards(): Float {
        return getAllRecords().sumOf { it.daysEarned.toDouble() }.toFloat()
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun saveRecords(records: List<RewardRecord>) {
        val json = gson.toJson(records)
        prefs.edit().apply {
            putString(KEY_RECORDS, json)
            putString(KEY_CHECKSUM, calculateChecksum(json))
            apply()
        }
    }

    private fun calculateChecksum(data: String): String {
        val input = data + deviceFingerprint
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
