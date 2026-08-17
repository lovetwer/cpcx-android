package com.example.lottery

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.google.gson.Gson

/**
 * 全局 Application：保存后端地址、登录 Token、设备号、用户信息。
 * 与网页 store/auth.js 对齐。
 */
class LotteryApp : Application() {

    companion object {
        // 后端 API 地址（与 web/src/api/http.js 中 BASE_URL 保持一致）
        const val BASE_URL = "https://cpcxapi.800820882.xyz/"

        lateinit var prefs: SharedPreferences
        lateinit var deviceId: String

        fun getToken(): String = prefs.getString("token", "") ?: ""
        fun setToken(t: String) { prefs.edit().putString("token", t).apply() }
        fun clearToken() {
            prefs.edit().remove("token").apply()
            prefs.edit().remove("user").apply()
        }

        fun getUser(): model.User? {
            val s = prefs.getString("user", null) ?: return null
            return try { Gson().fromJson(s, model.User::class.java) } catch (e: Exception) { null }
        }
        fun setUser(u: model.User) {
            prefs.edit().putString("user", Gson().toJson(u)).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("lottery_prefs", Context.MODE_PRIVATE)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ("android-" + System.currentTimeMillis())
    }
}
