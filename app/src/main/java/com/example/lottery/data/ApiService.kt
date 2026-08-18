package com.example.lottery.data

import com.example.lottery.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    @POST("/api/register")
    suspend fun register(@Body body: Map<String, String>): LoginResp

    @POST("/api/login")
    suspend fun login(@Body body: Map<String, String>): LoginResp

    @POST("/api/login/device")
    suspend fun deviceLogin(@Body body: Map<String, String>): LoginResp

    @GET("/api/me")
    suspend fun me(): MeResp

    @PUT("/api/me")
    suspend fun updateMe(@Body body: Map<String, String>): MeResp

    @GET("/api/lottery")
    suspend fun listLottery(@QueryMap params: Map<String, String>): ListResp<Lottery>

    @POST("/api/lottery")
    suspend fun createLottery(@Body body: Map<String, Any>): BaseResp

    @POST("/api/lottery/batch")
    suspend fun batchLottery(@Body body: Map<String, Any>): BatchResp

    @DELETE("/api/lottery/{id}")
    suspend fun deleteLottery(@Path("id") id: Long): BaseResp

    @PUT("/api/lottery/{id}/status")
    suspend fun updateStatus(@Path("id") id: Long, @Body body: Map<String, String>): BaseResp

    @GET("/api/draw")
    suspend fun listDraw(@Query("type") type: String): ListResp<DrawResult>

    @Multipart
    @POST("/api/lottery/recognize")
    suspend fun recognize(
        @Part image: MultipartBody.Part,
        @Part dry_run: MultipartBody.Part
    ): RecognizeResp

    @POST("/api/share")
    suspend fun createShare(@Body body: Map<String, Any>): ShareResp

    @GET("/api/share")
    suspend fun getShare(@Query("code") code: String): ListResp<Lottery>

    /* ---------------- AI 预测（后端代理） ---------------- */

    @POST("/api/ai/predict")
    suspend fun aiPredict(@Body body: Map<String, String>): AIPredictResp
}
