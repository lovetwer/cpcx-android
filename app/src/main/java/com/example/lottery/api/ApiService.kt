package com.example.lottery.api

import com.example.lottery.LotteryApp
import com.example.lottery.model.*
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {

    @POST("/api/login/device")
    suspend fun deviceLogin(@Body req: DeviceLoginReq): AuthResp

    @GET("/api/lottery")
    suspend fun listLottery(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("issue") issue: String? = null
    ): ListResp

    @POST("/api/lottery")
    suspend fun addLottery(@Body req: LotteryReq): Map<String, Any>

    @DELETE("/api/lottery/{id}")
    suspend fun deleteLottery(@Path("id") id: Long)

    @PUT("/api/lottery/{id}/status")
    suspend fun setStatus(@Path("id") id: Long, @Body req: StatusReq): Map<String, Any>

    // 图片识别（与网页端同一接口 /lottery/ai-generate）
    @Multipart
    @POST("/lottery/ai-generate")
    suspend fun ocr(@Part image: MultipartBody.Part): OcrResp
}

object ApiClient {
    private val authInterceptor = Interceptor { chain ->
        val token = LotteryApp.getToken()
        val req = if (token.isNotEmpty()) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(req)
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(LotteryApp.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
