package com.example.unzipfile.membership

/**
 * 推广来源类型
 */
enum class AffiliateSource {
    TAOBAO, // 淘宝联盟
    JD,     // 京东联盟
    GOOGLE_ADS, // 谷歌广告
    INTERNAL // 内部邀请
}

/**
 * 推广商品模型
 */
data class AffiliateProduct(
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val originalPrice: Double,
    val rewardDays: Int, // 奖励会员天数
    val imageUrl: String,
    val affiliateUrl: String,
    val source: AffiliateSource,
    val couponAmount: Double = 0.0, // 优惠券金额
    val salesCount: Int = 0, // 销量
    val actionType: String = "url",
    val taobaoToken: String = "",
    val taobaoUrl: String = "",
    val tag: String = ""
)

/**
 * 广告奖励模型
 */
data class AdRewardTask(
    val id: String,
    val title: String,
    val rewardMinutes: Int, // 奖励会员分钟数
    val adType: String, // e.g. "REWARDED_VIDEO"
    val description: String
)

/**
 * 用户操作类型枚举
 */
enum class OperationType {
    UNZIP,          // 解压
    COMPRESS,       // 压缩
    INVITE,         // 邀请
    BE_INVITED,     // 被邀请
    COMMISSION,     // 提成/奖励
    WITHDRAW,       // 提现
    UPGRADE,        // 升级会员
    LOGIN,          // 登录
    WATCH_AD        // 观看广告
}

/**
 * 用户操作日志模型
 */
data class UserOperationLog(
    val id: String,
    val userId: String,
    val operation: OperationType,
    val details: String,
    val timestamp: Long,
    val deviceId: String = "",
    val ipAddress: String = ""
)

/**
 * 奖励类型
 */
enum class RewardType {
    INSTALL,    // 邀请安装
    PURCHASE,   // 引导购买
    AD,         // 观看广告
    GIFT        // 礼包兑换
}

/**
 * 奖励记录模型
 */
data class RewardRecord(
    val id: String,
    val type: RewardType,
    val daysEarned: Float, // 使用Float支持分钟换算出的天数
    val timestamp: Long,
    val description: String
)
