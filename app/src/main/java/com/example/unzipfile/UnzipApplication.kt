package com.example.unzipfile

import android.app.Application
import com.example.unzipfile.utils.UmengHelper

class UnzipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 友盟预初始化
        UmengHelper.preInit(this)
    }
}
