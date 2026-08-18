package com.example.lottery.data

import android.content.Context
import android.provider.Settings
import com.example.lottery.data.model.User
import com.google.gson.Gson
import java.util.UUID

/**
 * 登录态与设备号持久化（对应网页 localStorage：lm_token / lm_user / lm_device）。
 * 设备号：优先用 Settings.Secure.ANDROID_ID，保证「设备登录」下次自动登录。
 */
class AuthStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lm_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var token: String
        get() = prefs.getString("lm_token", "") ?: ""
        set(v) = prefs.edit().putString("lm_token", v).apply()

    var userJson: String
        get() = prefs.getString("lm_user", "") ?: ""
        set(v) = prefs.edit().putString("lm_user", v).apply()

    fun save(token: String, user: User) {
        this.token = token
        userJson = gson.toJson(user)
    }

    /** 更新已存用户对象（保留 token） */
    fun updateUser(user: User) {
        userJson = gson.toJson(user)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun getUser(): User? {
        val j = userJson
        if (j.isEmpty()) return null
        return try {
            gson.fromJson(j, User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isAuthed(): Boolean = token.isNotEmpty()

    fun getDeviceId(): String {
        var d = prefs.getString("lm_device", "")
        if (d.isNullOrEmpty()) {
            d = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            if (d.isNullOrEmpty()) d = "and-" + UUID.randomUUID().toString().take(12)
            prefs.edit().putString("lm_device", d).apply()
        }
        return d
    }
}
