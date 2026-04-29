package com.example.unzipfile.membership

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** 推广与激励任务管理器 */
class AffiliateManager private constructor(private val context: Context) {

    private val membershipManager = MembershipManager.getInstance(context)
    private val TAG = "AffiliateManager"

    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    companion object {
        @Volatile private var instance: AffiliateManager? = null

        fun getInstance(context: Context): AffiliateManager {
            return instance ?: synchronized(this) {
                instance ?: AffiliateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /** 获取应用配置信息 增加多镜像重试机制 */
    suspend fun getAppConfig(appId: String): JSONObject? {
        val jsonStr = fetchWithMirrorRetry("apps.json") ?: return null
        
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            var targetConfig: JSONObject? = null
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                // 修复：apps.json 中的字段名是 "id" 而不是 "app_id"
                if (item.optString("id").equals(appId, ignoreCase = true)) {
                    targetConfig = item
                    break
                }
            }
            targetConfig
        } catch (e: Exception) {
            Log.e(TAG, "Parse apps.json error", e)
            null
        }
    }

    private suspend fun fetchWithMirrorRetry(fileName: String): String? {
        val mirrors = listOf(
            "https://fastly.jsdelivr.net/gh/duibuqi-woyaoqifei/jinlun-blog@main/public/api/v1/", // Fastly 刷新较快
            "https://raw.githubusercontent.com/duibuqi-woyaoqifei/jinlun-blog/main/public/api/v1/", // GitHub 原生，无缓存延迟
            "https://cdn.jsdelivr.net/gh/duibuqi-woyaoqifei/jinlun-blog@main/public/api/v1/",
            "https://gcore.jsdelivr.net/gh/duibuqi-woyaoqifei/jinlun-blog@main/public/api/v1/"
        )

        return withContext(Dispatchers.IO) {
            for (baseUrl in mirrors) {
                try {
                    val url = "$baseUrl$fileName?t=${System.currentTimeMillis()}"
                    Log.d(TAG, "Attempting request to: $url")

                    val request = Request.Builder()
                        .url(url)
                        // 移除 FORCE_NETWORK，避免在无缓存或网络波动时直接引发 504 崩溃
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim()
                        if (!body.isNullOrBlank()) {
                            // 移除 startsWith 强校验，因为 GitHub 文件如果带有 UTF-8 BOM，startsWith("[") 会失败
                            // JSONObject 解析器会自动跳过 BOM
                            Log.i(TAG, "Successfully fetched $fileName from mirror: $baseUrl")
                            return@withContext body
                        }
                    } else {
                        Log.w(TAG, "Mirror $baseUrl returned HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mirror failed: $baseUrl, error: ${e.message}")
                }
            }
            null
        }
    }

    /** 获取广告激励任务列表（带镜像重试逻辑） */
    suspend fun getDynamicAdRewardTasks(): List<AdRewardTask> {
        // 直接复用 getAppConfig，内部已处理好 JSONArray 的解析
        val config = getAppConfig("unzip-pro")
        
        val tasks = mutableListOf<AdRewardTask>()
        try {
            // apps.json 里的字段是 reward_tasks
            val adTasks = config?.optJSONArray("reward_tasks")
            if (adTasks != null) {
                for (i in 0 until adTasks.length()) {
                    val obj = adTasks.getJSONObject(i)
                    tasks.add(
                            AdRewardTask(
                                    id = obj.optString("slot_id"), // apps.json 对应的是 slot_id
                                    title = obj.optString("title"),
                                    rewardMinutes = obj.optInt("reward_mins"), // 对应 reward_mins
                                    adType = obj.optString("type"),
                                    description = obj.optString("desc") // 对应 desc
                            )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse ad tasks error", e)
        }

        if (tasks.isEmpty()) {
            Log.w(TAG, "Tasks list is empty, falling back to local defaults")
            tasks.add(
                    AdRewardTask("100009031", "观看视频广告", 60, "REWARDED_VIDEO", "观看一段完整视频，获得1小时会员时长")
            )
        }
        return tasks
    }

    /** 获取限时优惠商品列表（带镜像重试逻辑） */
    suspend fun getFeaturedProducts(): List<AffiliateProduct> {
        val jsonStr = fetchWithMirrorRetry("promos.json") ?: return emptyList()

        val products = mutableListOf<AffiliateProduct>()
        try {
            val root = JSONObject(jsonStr)
            val affiliates = root.optJSONArray("affiliates") ?: return emptyList()

            for (i in 0 until affiliates.length()) {
                val obj = affiliates.getJSONObject(i)
                products.add(
                        AffiliateProduct(
                                id = obj.optString("id"),
                                title = obj.optString("name"),
                                description = obj.optString("description"),
                                price = obj.optDouble("price", 0.0),
                                originalPrice = obj.optDouble("original_price", 0.0),
                                rewardDays = obj.optInt("reward_days", 3),
                                imageUrl = obj.optString("image"),
                                affiliateUrl = obj.optString("action_url"),
                                source = if (obj.optString("source") == "taobao") AffiliateSource.TAOBAO else AffiliateSource.JD,
                                actionType = obj.optString("action_type", "url"),
                                taobaoToken = obj.optString("taobao_token"),
                                taobaoUrl = obj.optString("taobao_url"),
                                tag = obj.optString("tag", "限时优惠")
                        )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse promos error", e)
        }
        return products
    }

    /** 处理口令兑换逻辑 (实时检查) */
    suspend fun redeemGiftCode(inputCode: String): Pair<Boolean, String> {
        val config = getAppConfig("unzip-pro") ?: return false to "无法连接配置服务器"

        val serverCode = config.optString("gift_code")
        val rewardMins = config.optInt("gift_reward_mins", 0)

        if (serverCode.isEmpty() || rewardMins <= 0) return false to "当前暂无活动口令"
        if (inputCode != serverCode) return false to "口令错误"

        val prefs = context.getSharedPreferences("gift_codes_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("used_$serverCode", false)) return false to "该口令已领过"

        // 发放奖励
        membershipManager.addMembershipTime(rewardMins)

        // 计入收益统计
        RewardStatsManager.getInstance(context)
                .recordReward(RewardType.GIFT, (rewardMins / 1440f), "口令兑换: $serverCode")

        // 记录已使用
        prefs.edit().putBoolean("used_$serverCode", true).apply()

        return true to "🎉 兑换成功！"
    }

    fun processAdReward(taskId: String, rewardMinutes: Int, taskTitle: String? = null): Boolean {
        membershipManager.addMembershipTime(rewardMinutes)

        // 计入收益统计
        val description = if (taskTitle.isNullOrEmpty()) "广告激励任务" else taskTitle
        RewardStatsManager.getInstance(context)
                .recordReward(RewardType.AD, (rewardMinutes / 1440f), description)
        return true
    }
}
