package com.example.unzipfile.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 合规管理器
 * 负责隐私协议状态维护，确保合规执法检查通过
 */
object ComplianceManager {
    private const val PREFS_NAME = "compliance_prefs"
    private const val KEY_HAS_AGREED_PRIVACY = "has_agreed_privacy"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 是否已经同意过隐私协议
     */
    fun hasAgreedPrivacy(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_AGREED_PRIVACY, false)
    }

    /**
     * 设置已同意隐私协议
     */
    fun setAgreedPrivacy(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_HAS_AGREED_PRIVACY, true).apply()
    }
}
