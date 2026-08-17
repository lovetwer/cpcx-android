package com.example.lottery.api

import com.example.lottery.LotteryApp
import com.example.lottery.model.*
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File

interface ApiService {
    // ---------- 用户 ----------
    @POST("/api/register")
    suspend fun register(@Body req: RegisterReq): AuthResp

    @POST("/api/login")
    suspend fun login(@Body req: LoginReq): AuthResp

    @POST("/api/login/device")
    suspend fun deviceLogin(@Body req: DeviceLoginReq): AuthResp

    @GET("/api/me")
    suspend fun me(): MeResp

    @PUT("/api/me")
    suspend fun updateMe(@Body req: UpdateUserReq): MeResp

    // ---------- 彩票 ----------
    @GET("/api/lottery")
    suspend fun listLottery(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("issue") issue: String? = null
    ): ListResp

    @POST("/api/lottery")
    suspend fun addLottery(@Body req: LotteryReq): Map<String, Any>

    @POST("/api/lottery/batch")
    suspend fun batchLottery(@Body req: BatchReq): BatchResp

    @DELETE("/api/lottery/{id}")
    suspend fun deleteLottery(@Path("id") id: Long): Map<String, Any>

    // ---------- 开奖 ----------
    @GET("/api/draw")
    suspend fun listDraw(@Query("type") type: String?): DrawListResp

    // ---------- 图片识别 ----------
    @Multipart
    @POST("/api/lottery/recognize")
    suspend fun recognize(
        @Part image: MultipartBody.Part,
        @Part("dry_run") dryRun: okhttp3.RequestBody
    ): OcrResp
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
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(LotteryApp.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // 便捷：把 File 封装为 MultipartBody.Part
    fun filePart(file: File): MultipartBody.Part {
        val body = file.asRequestBody("image/*".toMediaType())
        return MultipartBody.Part.createFormData("image", file.name, body)
    }
}
