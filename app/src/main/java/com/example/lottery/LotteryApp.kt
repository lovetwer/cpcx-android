package com.example.lottery

import android.app.Application
import com.example.lottery.data.AuthStore
import com.example.lottery.util.initDp
import com.example.lottery.data.ApiService
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 全局 Application：持有 Retrofit 实例、鉴权拦截器、AuthStore。
 * BASE_URL 改成你的后端地址（本地调试可用电脑局域网 IP，例如 http://192.168.1.10:8080）。
 */
class LotteryApp : Application() {
    lateinit var api: ApiService
        private set
    lateinit var authStore: AuthStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        initDp(this)

        authStore = AuthStore(this)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // AI 图片识别耗时较长，给 2 分钟
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                val token = authStore.token
                if (token.isNotEmpty()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        api = retrofit.create(ApiService::class.java)
    }

    companion object {
        // TODO: 改成你的后端地址（部署到服务器后填公网地址；本地调试填局域网 IP）
        const val BASE_URL = "https://cpcxapi.800820882.xyz"

        // 前端 Web 站点域名：分享链接统一指向这里（注意不要误用上面的后端 API 域名）
        const val WEB_BASE_URL = "https://cpcx.800820882.xyz"

        // 应用内更新：APK 托管在 GitHub Releases（仓库 owner/repo，latest 直链下载）
        const val GITHUB_OWNER = "lovetwer"
        const val GITHUB_REPO = "cpcx-android"

        lateinit var instance: LotteryApp
            private set
    }
}
