package com.xingleme.wakeup

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class XinglemeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 产品只提供浅色模式，不跟随系统深色。
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        NotificationHelper.ensureChannels(this)
    }
}
