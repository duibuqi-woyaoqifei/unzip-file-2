package com.example.unzipfile.membership

import android.content.Context
import com.example.unzipfile.OperationLogger

/**
 * 提成管理器
 * 处理邀请提成计算和发放逻辑
 */
class CommissionManager private constructor(private val context: Context) {

    private val membershipManager = MembershipManager.getInstance(context)
    private val operationLogger = OperationLogger.getInstance(context)

    companion object {
        @Volatile
        private var instance: CommissionManager? = null

        fun getInstance(context: Context): CommissionManager {
            return instance ?: synchronized(this) {
                instance ?: CommissionManager(context.applicationContext).also { instance = it }
            }
        }

        // 提成比例
        const val COMMISSION_RATE = 0.15 // 15%

        // 双方奖励时长（天）
        const val INVITER_REWARD_DAYS = 3
        const val INVITEE_REWARD_DAYS = 3
    }

    /**
     * 处理邀请成功事件
     * 为邀请者和被邀请者发放奖励
     */
    suspend fun processInvitationSuccess(inviterId: String, inviteeId: String, inviteeDeviceId: String): Boolean {
        val affiliateManager = AffiliateManager.getInstance(context)
        val config = affiliateManager.getAppConfig("unzip-pro")
        
        // 从 JSON 读取奖励时长（分钟），保底为 7 天
        val rewardMins = config?.optInt("invite_reward_mins", 10080) ?: 10080
        val rewardDays = rewardMins / 1440

        // 1. 为被邀请者发放会员奖励（目前设定为给被邀请者 3 天）
        val inviteeRewarded = rewardInvitee(inviteeId, inviteeDeviceId, 3) 
        if (!inviteeRewarded) return false

        // 2. 为邀请者发放会员时长奖励
        val inviterRewarded = rewardInviter(inviterId, rewardMins)
        if (!inviterRewarded) return false

        // 3. 记录操作日志与统计
        operationLogger.logOperation(
            userId = inviterId,
            operation = OperationType.INVITE,
            details = "邀请成功，获得 ${rewardMins / 1440f} 天会员奖励",
            deviceId = inviteeDeviceId
        )
        RewardStatsManager.getInstance(context).recordReward(
            RewardType.INSTALL, rewardMins / 1440f, "邀请好友奖励"
        )

        return true
    }

    /**
     * 为被邀请者发放会员奖励
     */
    private fun rewardInvitee(inviteeId: String, deviceId: String, days: Int): Boolean {
        // 检查设备唯一性（防刷单）
        val myDeviceId = com.example.unzipfile.utils.DeviceUtils.getDeviceFingerprint(context)

        if (myDeviceId == deviceId) {
            android.util.Log.w("CommissionManager", "Self-invite detected. Skipping reward.")
            return false
        }

        val currentProfile = membershipManager.getCurrentProfile()
        val rewardExpireTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)

        val updatedProfile = currentProfile.copy(
            membershipTier = MembershipTier.PREMIUM,
            membershipExpireTime = rewardExpireTime,
            isInvited = true
        )

        membershipManager.updateProfile(updatedProfile)
        return true
    }

    /**
     * 为邀请者发放奖励（按分钟计算）
     */
    private fun rewardInviter(inviterId: String, rewardMinutes: Int): Boolean {
        val currentProfile = membershipManager.getCurrentProfile()
        
        // 这里的逻辑通常应该由服务器处理，但在全本地模式下，
        // 我们根据识别到的邀请码给当前用户加时间
        
        val currentExpire = if (currentProfile.membershipExpireTime > System.currentTimeMillis()) {
            currentProfile.membershipExpireTime
        } else {
            System.currentTimeMillis()
        }
        
        val newExpire = currentExpire + (rewardMinutes * 60 * 1000L)

        val updatedProfile = currentProfile.copy(
            membershipTier = MembershipTier.PREMIUM,
            membershipExpireTime = newExpire,
            invitedCount = currentProfile.invitedCount + 1
        )

        membershipManager.updateProfile(updatedProfile)
        return true
    }

    /**
     * 获取用户的推广统计信息
     */
    fun getAffiliateStats(userId: String): AffiliateStats {
        val profile = membershipManager.getCurrentProfile()
        return AffiliateStats(
            totalSales = profile.affiliateSalesCount,
            invitedCount = profile.invitedCount,
            adsWatchedCount = profile.adsWatchedCount
        )
    }
}

/**
 * 推广统计数据类
 */
data class AffiliateStats(
    val totalSales: Int,    // 累计成交
    val invitedCount: Int,  // 邀请人数
    val adsWatchedCount: Int // 广告观看次数
)
