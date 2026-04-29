package com.example.unzipfile.utils

import android.app.Activity
import android.util.Log
import com.example.unzipfile.membership.AdRewardTask
import com.umeng.union.api.UMAdConfig
import com.umeng.union.api.UMUnionApi
import com.umeng.union.UMUnionSdk
import com.umeng.union.UMRewardAD

/**
 * 广告任务管理器 (支持激励视频和插屏/浏览广告)
 */
class RewardAdManager(private val activity: Activity) {

    private var mRewardAd: UMRewardAD? = null
    private val TAG = "RewardAdManager"

    interface RewardCallback {
        fun onAdLoaded()
        fun onAdFailed(message: String)
        fun onRewardVerified()
        fun onAdDismissed()
    }

    /**
     * 统一加载并展示广告
     */
    fun loadAndShow(task: AdRewardTask, userId: String, callback: RewardCallback) {
        if (task.adType == "INTERSTITIAL") {
            loadAndShowInterstitialAd(task.id, callback)
        } else {
            loadAndShowRewardVideo(task, userId, callback)
        }
    }

    /**
     * 加载并显示插屏广告 (用于“浏览推荐页面”任务)
     */
    private fun loadAndShowInterstitialAd(
        slotId: String,
        callback: RewardCallback
    ) {
        val config = UMAdConfig.Builder()
            .setSlotId(slotId)
            .build()

        Log.d(TAG, "Loading interstitial ad for slot: $slotId")

        UMUnionSdk.getApi().loadInterstitialAd(activity, config, object : UMUnionApi.AdLoadListener<UMUnionApi.AdDisplay> {
            override fun onSuccess(adType: UMUnionApi.AdType?, ad: UMUnionApi.AdDisplay?) {
                if (ad == null) {
                    callback.onAdFailed("插屏广告加载成功但对象为空")
                    return
                }

                // 根据文档：设置关闭回调
                ad.setAdCloseListener { type ->
                    Log.d(TAG, "Interstitial Closed - Task Verified")
                    // 插屏广告关闭时，视为完成浏览任务并发放奖励
                    callback.onRewardVerified()
                    callback.onAdDismissed()
                }

                // 根据文档：设置曝光、点击事件回调
                ad.setAdEventListener(object : UMUnionApi.AdEventListener {
                    override fun onExposed() {
                        Log.d(TAG, "Interstitial Exposed")
                    }
                    override fun onClicked(view: android.view.View?) {
                        Log.d(TAG, "Interstitial Clicked")
                    }
                    override fun onError(code: Int, message: String?) {
                        Log.e(TAG, "Interstitial Error: $code, $message")
                        callback.onAdFailed(message ?: "广告播放错误")
                    }
                })

                activity.runOnUiThread {
                    callback.onAdLoaded()
                    ad.show(activity)
                }
            }

            override fun onFailure(adType: UMUnionApi.AdType?, msg: String?) {
                Log.e(TAG, "Interstitial Ad Load Failure: $msg")
                val finalMsg = if (msg?.contains("1009") == true) "暂无广告" else (msg ?: "加载失败")
                activity.runOnUiThread {
                    callback.onAdFailed(finalMsg)
                }
            }
        })
    }

    /**
     * 加载并展示激励视频广告
     */
    private fun loadAndShowRewardVideo(task: AdRewardTask, userId: String, callback: RewardCallback) {
        // 如果已经加载了且有效，直接显示
        if (mRewardAd?.isValid == true) {
            showAd(callback)
            return
        }

        val config = UMAdConfig.Builder()
            .setSlotId(task.id)
            .setUserId(userId)
            .setCustomData("{\"task_id\": \"${task.id}\"}")
            .build()

        Log.d(TAG, "Loading reward video for slot: ${task.id}")
        
        UMUnionSdk.getApi().loadRewardAd(config, object : UMUnionApi.AdLoadListener<UMRewardAD> {
            override fun onSuccess(type: UMUnionApi.AdType?, display: UMRewardAD?) {
                if (display == null) {
                    callback.onAdFailed("激励视频加载成功但对象为空")
                    return
                }

                mRewardAd = display
                mRewardAd?.setAdEventListener(object : UMUnionApi.RewardAdListener {
                    override fun onExposed() {
                        Log.d(TAG, "Ad Exposed")
                    }

                    override fun onClicked(view: android.view.View?) {
                        Log.d(TAG, "Ad Clicked")
                    }

                    override fun onError(code: Int, message: String?) {
                        Log.e(TAG, "Ad Error: $code, $message")
                        callback.onAdFailed(message ?: "未知错误")
                    }

                    override fun onDismissed() {
                        Log.d(TAG, "Ad Dismissed")
                        callback.onAdDismissed()
                        mRewardAd = null 
                    }

                    override fun onReward(valid: Boolean, extra: MutableMap<String, Any>?) {
                        Log.d(TAG, "Reward Verified: $valid")
                        if (valid) {
                            callback.onRewardVerified()
                        }
                    }
                })

                activity.runOnUiThread {
                    callback.onAdLoaded()
                    showAd(callback)
                }
            }

            override fun onFailure(type: UMUnionApi.AdType?, message: String?) {
                Log.e(TAG, "Ad Load Failure: $message")
                val finalMsg = if (message?.contains("1009") == true) "暂无广告" else (message ?: "加载失败")
                activity.runOnUiThread {
                    callback.onAdFailed(finalMsg)
                }
            }
        })
    }

    private fun showAd(callback: RewardCallback) {
        mRewardAd?.let {
            if (it.isValid) {
                it.show(activity)
            } else {
                callback.onAdFailed("广告已失效，请重试")
                mRewardAd = null
            }
        }
    }
}
