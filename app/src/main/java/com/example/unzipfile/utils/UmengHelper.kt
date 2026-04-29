package com.example.unzipfile.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import com.umeng.umlink.MobclickLink
import com.umeng.umlink.UMLinkListener
import java.util.HashMap

/** 友盟 SDK 助手 严格遵守合规要求，实现延迟初始化 */
object UmengHelper {

    const val UMENG_APP_KEY = "69eb55076f259537c79efbc6"

    /** 预初始化：可以在 Application.onCreate 中调用 但注意：预初始化不采集敏感信息，仅用于准备环境 */
    fun preInit(context: Context) {
        UMConfigure.setLogEnabled(true)
        UMConfigure.preInit(context, UMENG_APP_KEY, "Official")
    }

    /** 正式初始化：必须在用户同意隐私协议后调用！ */
    fun init(context: Context) {
        UMConfigure.init(context, UMENG_APP_KEY, "Official", UMConfigure.DEVICE_TYPE_PHONE, "")
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO)
    }

    /** 处理 U-Link 深度链接 (自动识别邀请码) */
    fun handleDeepLink(context: Context, intent: Intent, onInviteCodeFound: (String) -> Unit) {
        val listener =
                object : UMLinkListener {
                    override fun onLink(path: String?, params: HashMap<String, String>?) {
                        android.util.Log.d("UmengHelper", "U-Link onLink: path=$path, params=$params")
                        params?.get("invite_code")?.let {
                            if (it.isNotEmpty()) onInviteCodeFound(it)
                        }
                    }

                    override fun onInstall(
                            params: HashMap<String, String>?,
                            uri: android.net.Uri?
                    ) {
                        android.util.Log.d("UmengHelper", "U-Link onInstall: params=$params, uri=$uri")
                        params?.get("invite_code")?.let {
                            if (it.isNotEmpty()) onInviteCodeFound(it)
                        }
                    }

                    override fun onError(error: String?) {
                        android.util.Log.e("UmengHelper", "U-Link Error: $error")
                    }
                }
        MobclickLink.getInstallParams(context, listener)
        intent.data?.let { MobclickLink.handleUMLinkURI(context, it, listener) }
    }

    /** 自定义原生分享形式 使用 Android 系统原生的分享面板，无需集成复杂的第三方 SDK */
    fun openShareBoard(
            activity: Activity,
            title: String,
            content: String,
            url: String,
            onSuccess: () -> Unit
    ) {
        val shareText = "$title\n$content\n立即下载体验: $url"

        val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, title)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }

        // 弹出系统分享面板
        val chooser = Intent.createChooser(intent, "分享给好友")

        // 注意：系统原生分享无法精确回调“分享成功”，
        // 但我们可以在用户点击分享后即视为“发起成功”或给予鼓励。
        try {
            activity.startActivity(chooser)
            // 原生分享通常在启动选择器后即可回调，或者由开发者根据业务决定
            onSuccess()
        } catch (e: Exception) {
            android.util.Log.e("UmengHelper", "Failed to start share chooser: ${e.message}")
        }
    }
}
