package com.example.lottery

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

/**
 * 全局 Application：保存后端地址、登录 Token、设备号。
 * 部署时把 BASE_URL 改成你的后端地址（Render / 自有服务器）。
 */
class LotteryApp : Application() {

    companion object {
        // TODO: 改成你的后端地址，注意结尾带 '/'
        const val BASE_URL = "https://your-lottery-app.onrender.com/"

        lateinit var prefs: SharedPreferences
        lateinit var deviceId: String

        fun getToken(): String = prefs.getString("token", "") ?: ""
        fun setToken(t: String) { prefs.edit().putString("token", t).apply() }
        fun clearToken() { prefs.edit().remove("token").apply() }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("lottery_prefs", Context.MODE_PRIVATE)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
}
