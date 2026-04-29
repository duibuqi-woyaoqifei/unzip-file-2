package com.example.unzipfile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.unzipfile.membership.AffiliateManager
import com.umeng.union.UMUnionSdk
import com.umeng.union.UMSplashAD
import com.umeng.union.api.UMAdConfig
import com.umeng.union.api.UMUnionApi
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private var canJump = false
    private val TAG = "SplashAd"
    private val mHandler = Handler(Looper.getMainLooper())

    private val mReqTimeout = Runnable {
        Log.d(TAG, "Ad request timeout")
        goHome()
    }

    private val mLoadListener = object : UMUnionApi.AdLoadListener<UMSplashAD> {
        override fun onSuccess(type: UMUnionApi.AdType?, display: UMSplashAD?) {
            Log.d(TAG, "Ad load success")
            mHandler.removeCallbacks(mReqTimeout)
            if (isFinishing || display == null) return

            display.setAdEventListener(object : UMUnionApi.SplashAdListener {
                override fun onDismissed() { goHome() }
                override fun onExposed() {}
                override fun onClicked(view: android.view.View?) {}
                override fun onError(code: Int, message: String?) { goHome() }
            })
            
            display.show(container)
        }

        override fun onFailure(type: UMUnionApi.AdType?, message: String?) {
            Log.e(TAG, "Ad load failure: $message")
            mHandler.removeCallbacks(mReqTimeout)
            goHome()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        container = findViewById(R.id.splash_container)

        lifecycleScope.launch {
            val affiliateManager = AffiliateManager.getInstance(this@SplashActivity)
            val config = affiliateManager.getAppConfig("unzip-pro")
            val slotId = config?.optJSONObject("ad_config")?.optString("splash_id") ?: "100009029"
            
            val adConfigBuilder = UMAdConfig.Builder().setSlotId(slotId).build()
            UMUnionSdk.loadSplashAd(adConfigBuilder, mLoadListener, 3000)
        }
        
        mHandler.postDelayed(mReqTimeout, 3500)
    }

    override fun onResume() {
        super.onResume()
        if (canJump) {
            goHome()
        }
        canJump = true
    }

    override fun onPause() {
        super.onPause()
        canJump = false
    }

    private fun goHome() {
        if (canJump) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            canJump = true
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {}
}
