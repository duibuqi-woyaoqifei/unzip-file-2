package com.example.unzipfile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.unzipfile.databinding.ActivityPromotionBinding
import com.example.unzipfile.databinding.ItemAdTaskBinding
import com.example.unzipfile.databinding.ItemAffiliateProductBinding
import com.example.unzipfile.membership.*
import com.example.unzipfile.utils.*
import java.util.*
import kotlinx.coroutines.launch

class PromotionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromotionBinding
    private lateinit var membershipManager: MembershipManager
    private lateinit var affiliateManager: AffiliateManager
    private lateinit var rewardAdManager: RewardAdManager

    private var remoteShareRules: String = "温馨提示：\n1. 仅限新用户安装生效；\n2. 同一设备多次安装不计入奖励。"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromotionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        membershipManager = MembershipManager.getInstance(this)
        affiliateManager = AffiliateManager.getInstance(this)
        rewardAdManager = RewardAdManager(this)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 核心：规则点击监听器应该在初始化时就绑定
        binding.layoutRulesTrigger.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("奖励发放规则")
                    .setMessage(remoteShareRules)
                    .setPositiveButton("我知道了", null)
                    .show()
        }

        binding.rvAdTasks.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.layoutManager = LinearLayoutManager(this)

        binding.btnEarnMore.setOnClickListener { promoteApp() }

        binding.btnRedeemGift.setOnClickListener { showRedeemDialog() }

        binding.tvViewStats.setOnClickListener {
            startActivity(Intent(this, RewardStatsActivity::class.java))
        }

        refreshUserStats()
    }

    private fun loadData() {
        // 加载广告任务
        lifecycleScope.launch {
            val adTasks = affiliateManager.getDynamicAdRewardTasks()
            binding.rvAdTasks.adapter = AdTaskAdapter(adTasks) { task -> performAdTask(task) }
        }

        // 加载限时优惠商品
        binding.pbLoading.visibility = View.VISIBLE
        binding.rvProducts.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val products = affiliateManager.getFeaturedProducts()
                binding.pbLoading.visibility = View.GONE
                if (products.isEmpty()) {
                    showEmptyState("当前暂无优惠信息")
                } else {
                    binding.rvProducts.adapter =
                            ProductAdapter(products) { product -> buyProduct(product) }
                    binding.rvProducts.alpha = 0f
                    binding.rvProducts.visibility = View.VISIBLE
                    binding.rvProducts.animate().alpha(1f).setDuration(500).start()
                }
            } catch (e: Exception) {
                binding.pbLoading.visibility = View.GONE
                showEmptyState("加载失败，请检查网络")
            }
        }

        // 预加载配置（包含分享规则与特惠显示开关）
        lifecycleScope.launch {
            val config = affiliateManager.getAppConfig("unzip-pro")
            config?.optString("share_rules")?.let { remoteShareRules = it }
            
            // 动态控制限时特惠板块显示
            val showPromos = config?.optBoolean("show_promos", false) ?: false
            binding.layoutPromos.visibility = if (showPromos) View.VISIBLE else View.GONE
        }
    }

    private fun showEmptyState(message: String) {
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = message
        binding.layoutEmptyState.alpha = 0f
        binding.layoutEmptyState.animate().alpha(1f).setDuration(500).start()
        binding.rvProducts.visibility = View.GONE
    }

    private fun promoteApp() {
        lifecycleScope.launch {
            val config = affiliateManager.getAppConfig("unzip-pro")
            val shareTitle = config?.optString("share_title") ?: "🔥 极速解压缩app，推荐给您！"
            
            // 获取分享描述并附带云盘提取码
            val remoteDesc = config?.optString("share_desc") ?: "点击下方链接，立即下载体验吧！"
            val finalDesc = "$remoteDesc\n(提取码: 123)"
            
            val shareUrl = config?.optString("link") ?: "https://app.example.com/download"

            com.example.unzipfile.utils.UmengHelper.openShareBoard(
                    this@PromotionActivity,
                    shareTitle,
                    finalDesc,
                    shareUrl
            ) {
                Toast.makeText(this@PromotionActivity, "分享成功！待好友安装后您将获得奖励", Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    private fun showRedeemDialog() {
        val input = android.widget.EditText(this)
        input.setHint("请输入兑换口令")
        input.gravity = android.view.Gravity.CENTER

        val container = android.widget.FrameLayout(this)
        val params =
                android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
        params.setMargins(60, 20, 60, 0)
        input.layoutParams = params
        container.addView(input)

        androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("礼包兑换")
                .setMessage("请输入您获得的专属口令领取奖励")
                .setView(container)
                .setPositiveButton("立即兑换") { _, _ ->
                    val code = input.text.toString().trim()
                    if (code.isNotEmpty()) {
                        lifecycleScope.launch {
                            val (success, message) = affiliateManager.redeemGiftCode(code)
                            Toast.makeText(this@PromotionActivity, message, Toast.LENGTH_LONG)
                                    .show()
                            if (success) refreshUserStats()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
    }

    private fun refreshUserStats() {
        val isVip = membershipManager.isMembershipActive()
        val profile = membershipManager.getCurrentProfile()
        
        binding.tvVipStatus.text = when {
            profile.membershipTier == MembershipTier.ADMIN -> "🛡️ 首席管理员"
            isVip -> "✨ 尊享会员版 (${membershipManager.getFormattedRemainingTime()})"
            else -> "🔓 免费标准版"
        }

        val totalEarned = RewardStatsManager.getInstance(this).getTotalRewards()
        binding.tvTotalVipEarned.text = String.format("%.1f 天", totalEarned)

        // 动态切换尊贵皮肤
        if (isVip || profile.membershipTier == MembershipTier.ADMIN) {
            binding.rlPromoMemberCardContent.setBackgroundResource(R.drawable.bg_vip_noble)
            binding.btnEarnMore.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D4AF37"))
            binding.btnEarnMore.text = "推荐好友"
        } else {
            binding.rlPromoMemberCardContent.setBackgroundResource(R.drawable.gradient_premium)
            binding.btnEarnMore.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            binding.btnEarnMore.text = "领取免费会员"
        }
    }

    private fun performAdTask(task: AdRewardTask) {
        val userId = membershipManager.getCurrentProfile().userId
        Toast.makeText(this, "正在为您加载广告...", Toast.LENGTH_SHORT).show()

        rewardAdManager.loadAndShow(
                task,
                userId,
                object : RewardAdManager.RewardCallback {
                    override fun onAdLoaded() {}
                    override fun onAdFailed(message: String) {
                        // 免费领会员补偿逻辑：若无广告，则奖励1天会员 (每天限领一次)
                        lifecycleScope.launch {
                            val prefs = getSharedPreferences("reward_limits", Context.MODE_PRIVATE)
                            val lastClaimDate = prefs.getString("last_compensation_date", "")
                            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
                            
                            if (lastClaimDate == today) {
                                runOnUiThread {
                                    Toast.makeText(this@PromotionActivity, "今日已领取过补偿奖励，明天再来吧！", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            membershipManager.addMembershipTime(1440) // 1天 = 1440分钟
                            RewardStatsManager.getInstance(this@PromotionActivity)
                                .recordReward(RewardType.AD, 1.0f, "广告加载失败补偿")
                            
                            prefs.edit().putString("last_compensation_date", today).apply()

                            runOnUiThread {
                                Toast.makeText(
                                    this@PromotionActivity,
                                    "🎁 暂无广告，已为您补偿 1 天会员时长！",
                                    Toast.LENGTH_LONG
                                ).show()
                                refreshUserStats()
                            }
                        }
                    }
                    override fun onRewardVerified() {
                        val success = affiliateManager.processAdReward(task.id, task.rewardMinutes, task.title)
                        if (success) {
                            runOnUiThread {
                                Toast.makeText(
                                                this@PromotionActivity,
                                                "🎉 奖励已发放！获得${task.rewardMinutes}分钟会员",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                                refreshUserStats()
                            }
                        }
                    }
                    override fun onAdDismissed() {}
                }
        )
    }

    private fun buyProduct(product: AffiliateProduct) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("taobao", product.taobaoToken))
        
        // 记录抢购收益
        RewardStatsManager.getInstance(this).recordReward(
            RewardType.PURCHASE, 
            product.rewardDays.toFloat(), 
            "抢购商品: ${product.title}"
        )
        
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.taobao.taobao")
            if (intent != null) {
                startActivity(intent)
                Toast.makeText(this, "淘口令已复制，正在打开淘宝...", Toast.LENGTH_SHORT).show()
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(product.taobaoUrl))
                startActivity(browserIntent)
                Toast.makeText(this, "未检测到淘宝，已为您打开浏览器", Toast.LENGTH_SHORT).show()
            }
            refreshUserStats()
        } catch (e: Exception) {
            Toast.makeText(this, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // --- Adapters ---
    inner class AdTaskAdapter(
            private val tasks: List<AdRewardTask>,
            private val onTaskClick: (AdRewardTask) -> Unit
    ) : RecyclerView.Adapter<AdTaskAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemAdTaskBinding) :
                RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ViewHolder(
                        ItemAdTaskBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = tasks[position]
            holder.binding.tvTaskTitle.text = task.title
            holder.binding.tvRewardInfo.text = "${task.description} (+${task.rewardMinutes}分钟)"
            holder.binding.btnDoTask.setOnClickListener { onTaskClick(task) }
        }
        override fun getItemCount() = tasks.size
    }

    inner class ProductAdapter(
            private val products: List<AffiliateProduct>,
            private val onShareClick: (AffiliateProduct) -> Unit
    ) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemAffiliateProductBinding) :
                RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ViewHolder(
                        ItemAffiliateProductBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val product = products[position]
            holder.binding.tvTitle.text = product.title
            holder.binding.tvPrice.text = String.format("¥%.2f", product.price)
            holder.binding.tvCommission.text = "奖励会员: ${product.rewardDays}天"
            holder.binding.ivProduct.load(product.imageUrl)
            holder.binding.btnShare.text = if (product.actionType == "taobao") "复制口令抢购" else "前往购买"
            holder.binding.btnShare.setOnClickListener { onShareClick(product) }
        }
        override fun getItemCount() = products.size
    }
}
