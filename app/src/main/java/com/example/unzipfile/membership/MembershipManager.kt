package com.example.unzipfile.membership

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 会员管理服务（单例）
 * 负责会员状态的存储、验证和权限检查
 */
class MembershipManager private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences
    private var currentProfile: UserProfile
    
    private val apiService = com.example.unzipfile.network.ApiService.create()

    init {
        // 创建主密钥
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        // 初始化加密的 SharedPreferences (增加异常捕获，防止部分设备因 KeyStore 问题闪退)
        prefs = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("MembershipManager", "EncryptedSharedPreferences creation failed", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        
        // 从SharedPreferences加载会员信息
        currentProfile = loadProfile()

        // 核心安全校验：检查硬件指纹一致性 (防设备迁移/克隆)
        checkDeviceConsistency()
    }

    private fun checkDeviceConsistency() {
        if (DebugConfig.IS_DEBUG_MODE) return
        
        val currentFingerprint = com.example.unzipfile.utils.DeviceUtils.getDeviceFingerprint(context)
        val boundDeviceId = prefs.getString(KEY_BIND_DEVICE_ID, "")
        
        if (boundDeviceId.isNullOrEmpty()) {
            // 第一次运行，绑定设备
            prefs.edit().putString(KEY_BIND_DEVICE_ID, currentFingerprint).apply()
        } else if (boundDeviceId != currentFingerprint) {
            // 检测到设备指纹不匹配 (可能被手动备份还原到另一台手机)
            android.util.Log.e("Security", "Device fingerprint mismatch! Potential cloning detected.")
            resetToFree()
            // 弹出强力警告或清除敏感数据
        }
    }


    /**
     * 从服务端同步资料
     */
    suspend fun syncProfileRemote(): Boolean {
        return try {
            val response = apiService.getProfile()
            if (response.code == 200 && response.data != null) {
                saveProfile(response.data)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("MembershipManager", "Failed to sync profile", e)
            false
        }
    }
    
    companion object {
        private const val PREFS_NAME = "membership_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_MEMBERSHIP_TIER = "membership_tier"
        private const val KEY_EXPIRE_TIME = "expire_time"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_INVITATION_CODE = "invitation_code"
        private const val KEY_INVITED_COUNT = "invited_count"
        private const val KEY_IS_INVITED = "is_invited"
        private const val KEY_AFFILIATE_SALES = "affiliate_sales"
        private const val KEY_ADS_WATCHED = "ads_watched"
        private const val KEY_BIND_DEVICE_ID = "bind_device_id"
        
        @Volatile
        private var instance: MembershipManager? = null
        
        fun getInstance(context: Context): MembershipManager {
            return instance ?: synchronized(this) {
                instance ?: MembershipManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 从SharedPreferences加载用户配置
     */
    private fun loadProfile(): UserProfile {
        val userId = prefs.getString(KEY_USER_ID, "guest") ?: "guest"
        val tierName = prefs.getString(KEY_MEMBERSHIP_TIER, MembershipTier.FREE.name) ?: MembershipTier.FREE.name
        val tier = try {
            MembershipTier.valueOf(tierName)
        } catch (e: IllegalArgumentException) {
            MembershipTier.FREE
        }
        val expireTime = prefs.getLong(KEY_EXPIRE_TIME, 0L)
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "游客") ?: "游客"
        val inviteCode = prefs.getString(KEY_INVITATION_CODE, "") ?: ""
        val invitedCount = prefs.getInt(KEY_INVITED_COUNT, 0)
        val isInvited = prefs.getBoolean(KEY_IS_INVITED, false)
        val affiliateSales = prefs.getInt(KEY_AFFILIATE_SALES, 0)
        val adsWatched = prefs.getInt(KEY_ADS_WATCHED, 0)

        return UserProfile(
            userId = userId,
            membershipTier = tier,
            membershipExpireTime = expireTime,
            displayName = displayName,
            invitationCode = inviteCode,
            invitedCount = invitedCount,
            isInvited = isInvited,
            affiliateSalesCount = affiliateSales,
            adsWatchedCount = adsWatched
        )
    }
    
    /**
     * 保存用户配置到SharedPreferences
     */
    private fun saveProfile(profile: UserProfile) {
        prefs.edit().apply {
            putString(KEY_USER_ID, profile.userId)
            putString(KEY_MEMBERSHIP_TIER, profile.membershipTier.name)
            putLong(KEY_EXPIRE_TIME, profile.membershipExpireTime)
            putString(KEY_DISPLAY_NAME, profile.displayName)
            putString(KEY_INVITATION_CODE, profile.invitationCode)
            putInt(KEY_INVITED_COUNT, profile.invitedCount)
            putBoolean(KEY_IS_INVITED, profile.isInvited)
            putInt(KEY_AFFILIATE_SALES, profile.affiliateSalesCount)
            putInt(KEY_ADS_WATCHED, profile.adsWatchedCount)
            commit()
        }
        currentProfile = profile
    }
    
    /**
     * 更新并保存用户配置
     */
    fun updateProfile(profile: UserProfile) {
        saveProfile(profile)
    }
    
    /**
     * 获取当前用户配置
     */
    fun getCurrentProfile(): UserProfile {
        return currentProfile
    }
    
    /**
     * 获取当前有效的会员等级
     */
    fun getCurrentTier(): MembershipTier {
        return currentProfile.getEffectiveTier()
    }

    /**
     * 检查会员是否处于活跃状态
     */
    fun isMembershipActive(): Boolean {
        return currentProfile.isMembershipValid()
    }

    /**
     * 获取剩余会员天数
     */
    fun getRemainingDays(): Long {
        if (currentProfile.membershipTier == MembershipTier.ADMIN) return 9999
        val diffMs = currentProfile.membershipExpireTime - System.currentTimeMillis()
        return if (diffMs > 0) diffMs / (24 * 60 * 60 * 1000L) else 0
    }

    /**
     * 获取格式化的剩余时间
     * < 1天显示小时，< 1小时显示分钟
     */
    fun getFormattedRemainingTime(): String {
        if (currentProfile.membershipTier == MembershipTier.ADMIN) return "永久有效"
        if (!isMembershipActive()) return "未开通"
        
        val diff = currentProfile.membershipExpireTime - System.currentTimeMillis()
        if (diff <= 0) return "已到期"

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}天"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "即将到期"
        }
    }
    
    /**
     * 检查文件大小是否符合当前会员权限
     * @param fileSizeBytes 文件大小（字节）
     * @return Pair<是否允许, 错误消息>
     */
    fun checkFileSizePermission(fileSizeBytes: Long): Pair<Boolean, String?> {
        if (DebugConfig.IS_DEBUG_MODE) return Pair(true, null) // 测试模式：无条件通过
        val tier = getCurrentTier()
        val allowed = tier.isFileSizeAllowed(fileSizeBytes)
        val message = if (!allowed) {
            val sizeMB = fileSizeBytes / (1024 * 1024)
            "文件大小 ${sizeMB}MB 超出${tier.displayName}限制（最大${tier.maxFileSizeMB}MB）"
        } else null
        return Pair(allowed, message)
    }
    
    /**
     * 检查批量操作是否符合当前会员权限
     * @param fileCount 文件数量
     * @return Pair<是否允许, 错误消息>
     */
    fun checkBatchPermission(fileCount: Int): Pair<Boolean, String?> {
        if (DebugConfig.IS_DEBUG_MODE) return Pair(true, null) // 测试模式：无条件通过
        val tier = getCurrentTier()
        val allowed = tier.isBatchCountAllowed(fileCount)
        val message = if (!allowed) {
            if (!tier.allowBatchOperation) {
                "批量操作仅限会员使用，当前为${tier.displayName}"
            } else {
                "批量文件数 $fileCount 超出限制（最大${tier.maxBatchCount}）"
            }
        } else null
        return Pair(allowed, message)
    }
    
    /**
     * 检查多个文件的总大小是否符合权限
     * @param fileSizes 文件大小列表（字节）
     * @return Pair<是否允许, 错误消息>
     */
    fun checkMultipleFilesPermission(fileSizes: List<Long>): Pair<Boolean, String?> {
        // 先检查批量操作权限
        val (batchAllowed, batchMessage) = checkBatchPermission(fileSizes.size)
        if (!batchAllowed) return Pair(false, batchMessage)
        
        // 检查每个文件的大小
        for ((index, size) in fileSizes.withIndex()) {
            val (sizeAllowed, sizeMessage) = checkFileSizePermission(size)
            if (!sizeAllowed) {
                return Pair(false, "文件 ${index + 1}: $sizeMessage")
            }
        }
        
        return Pair(true, null)
    }

    /**
     * 检查解压格式权限
     */
    fun checkUnzipFormatPermission(fileName: String): Pair<Boolean, String?> {
        if (DebugConfig.IS_DEBUG_MODE) return Pair(true, null) // 测试模式：无条件通过
        val tier = getCurrentTier()
        val allowed = tier.isFormatSupported(fileName)
        val message = if (!allowed) {
            "格式 ${fileName.substringAfterLast(".")} 仅限会员解压"
        } else null
        return Pair(allowed, message)
    }

    /**
     * 检查加密权限
     */
    fun checkEncryptionPermission(): Pair<Boolean, String?> {
        if (DebugConfig.IS_DEBUG_MODE) return Pair(true, null)
        val tier = getCurrentTier()
        return if (tier.allowEncryption) Pair(true, null) else Pair(false, "文件加密/解密功能仅限会员使用")
    }

    /**
     * 检查高级压缩权限
     */
    fun checkAdvancedCompressionPermission(): Pair<Boolean, String?> {
        if (DebugConfig.IS_DEBUG_MODE) return Pair(true, null)
        val tier = getCurrentTier()
        return if (tier.allowAdvancedCompression) Pair(true, null) else Pair(false, "高压缩率、分卷及密码保护功能仅限会员使用")
    }
    
    /**
     * 升级为会员（预留接口）
     * 未来可集成支付系统
     */
    fun upgradeToPremium(durationDays: Int = 365): Boolean {
        // 预留：未来可以调用支付API
        // 当前直接设置为会员
        val expireTime = System.currentTimeMillis() + (durationDays * 24 * 60 * 60 * 1000L)
        val newProfile = currentProfile.copy(
            membershipTier = MembershipTier.PREMIUM,
            membershipExpireTime = expireTime
        )
        saveProfile(newProfile)
        return true
    }
    
    /**
     * 设置会员等级（用于测试）
     */
    fun setMembershipTier(tier: MembershipTier) {
        val newProfile = currentProfile.copy(membershipTier = tier)
        saveProfile(newProfile)
    }
    
    /**
     * 重置为免费用户
     */
    fun resetToFree() {
        val newProfile = UserProfile()
        saveProfile(newProfile)
    }

    /**
     * 增加会员时长 (分钟)
     */
    fun addMembershipTime(minutes: Int) {
        val rewardMs = minutes * 60 * 1000L
        val newExpireTime = if (currentProfile.membershipExpireTime > System.currentTimeMillis()) {
            currentProfile.membershipExpireTime + rewardMs
        } else {
            System.currentTimeMillis() + rewardMs
        }

        val newProfile = currentProfile.copy(
            membershipTier = if (currentProfile.membershipTier == MembershipTier.FREE) MembershipTier.PREMIUM else currentProfile.membershipTier,
            membershipExpireTime = newExpireTime
        )
        saveProfile(newProfile)
    }

    /**
     * 激活管理员模式 (专属)
     * 只有输入正确的密钥才能开启最高权限
     */
    fun activateAdminMode(key: String): Boolean {
        // 设置一个只有您知道的密钥，例如 "Antigravity_Admin_999"
        if (key == "ikun") {
            val newProfile = currentProfile.copy(
                membershipTier = MembershipTier.ADMIN,
                membershipExpireTime = Long.MAX_VALUE, // 永久有效
                displayName = "首席管理员"
            )
            saveProfile(newProfile)
            return true
        }
        return false
    }

    /**
     * 模拟处理邀请奖励流程
     * 奖励会员时长而非现金
     */
    fun notifyReferrerReward(days: Int, friendName: String, friendDeviceId: String): Boolean {
        // 获取当前设备的高强度指纹
        val myDeviceId = com.example.unzipfile.utils.DeviceUtils.getDeviceFingerprint(context)

        // 核心安全校验：如果硬件指纹一致，判定为恶意互刷
        if (myDeviceId == friendDeviceId || friendDeviceId.contains(myDeviceId)) {
            android.util.Log.e("Security", "Detected self-referral attempt. Current: $myDeviceId, Friend: $friendDeviceId")
            return false
        }

        val rewardMs = days * 24 * 60 * 60 * 1000L
        val newExpireTime = if (currentProfile.membershipExpireTime > System.currentTimeMillis()) {
            currentProfile.membershipExpireTime + rewardMs
        } else {
            System.currentTimeMillis() + rewardMs
        }

        val newProfile = currentProfile.copy(
            membershipTier = MembershipTier.PREMIUM,
            membershipExpireTime = newExpireTime,
            invitedCount = currentProfile.invitedCount + 1
        )
        saveProfile(newProfile)
        return true
    }
}
