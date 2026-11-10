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

    @DELETE("/api/me")
    suspend fun deleteMe(): BaseResp

    @GET("/api/lottery")
    suspend fun listLottery(@QueryMap params: Map<String, String>): ListResp<Lottery>

    @POST("/api/lottery")
    suspend fun createLottery(@Body body: CreateLotteryReq): BaseResp

    data class CreateLotteryReq(
        val type: String,
        val issue: String,
        val red_balls: String,
        val blue_balls: String,
        val play_type: String,
        val multiple: Int,
        val banker_red: String,
        val banker_blue: String
    )

    @POST("/api/lottery/batch")
    suspend fun batchLottery(@Body body: BatchLotteryReq): BatchResp

    data class BatchLotteryReq(
        val items: List<Map<String, String>>
    )

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
    suspend fun createShare(@Body body: ShareReq): ShareResp

    data class ShareReq(
        val ids: List<Long>
    )

    @GET("/api/share")
    suspend fun getShare(@Query("code") code: String): ListResp<Lottery>

    /* ---------------- AI 预测（后端代理） ---------------- */

    @POST("/api/ai/predict")
    suspend fun aiPredict(@Body body: Map<String, String>): AIPredictResp
}
