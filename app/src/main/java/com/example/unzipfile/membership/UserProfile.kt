package com.example.unzipfile.membership

/**
 * 用户配置数据模型
 * 存储用户会员信息和账号相关数据
 */
data class UserProfile(
    val userId: String = "guest",  // 用户ID
    val membershipTier: MembershipTier = MembershipTier.FREE,  // 会员等级
    val membershipExpireTime: Long = 0L,  // 会员到期时间
    val displayName: String = "游客",  // 显示名称
    val invitationCode: String = "", // 个人邀请码
    val invitedCount: Int = 0, // 已邀请人数
    val isInvited: Boolean = false, // 是否是被邀请进来的
    val affiliateSalesCount: Int = 0, // 推广成交笔数
    val adsWatchedCount: Int = 0 // 广告观看次数
) {
    /**
     * 检查会员是否有效
     */
    fun isMembershipValid(): Boolean {
        // ADMIN 永久有效
        if (membershipTier == MembershipTier.ADMIN) return true
        
        // PREMIUM 检查有效期
        val currentTime = System.currentTimeMillis()
        return membershipTier == MembershipTier.PREMIUM && membershipExpireTime > currentTime
    }
    
    /**
     * 获取有效的会员等级
     */
    fun getEffectiveTier(): MembershipTier {
        return if (isMembershipValid()) membershipTier else MembershipTier.FREE
    }
}
