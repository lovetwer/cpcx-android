package com.example.lottery.model

import com.google.gson.annotations.SerializedName

// 用户
data class User(
    val id: Long = 0,
    val username: String = "",
    @SerializedName("device_id") val deviceId: String = "",
    val email: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class AuthResp(
    val ok: Boolean = false,
    val token: String = "",
    val user: User? = null,
    val msg: String? = null
)

// 彩票
data class Lottery(
    val id: Long = 0,
    val type: String = "",
    val issue: String = "",
    @SerializedName("red_balls") val redBalls: String = "",
    @SerializedName("blue_balls") val blueBalls: String = "",
    val status: String = "未开奖",
    @SerializedName("play_type") val playType: String = "single",
    val multiple: Int = 1,
    @SerializedName("banker_red") val bankerRed: String = "",
    @SerializedName("banker_blue") val bankerBlue: String = "",
    val bets: Int = 0,
    @SerializedName("prize_tier") val prizeTier: String = ""
)

data class ListResp(
    val ok: Boolean = false,
    val list: List<Lottery> = emptyList(),
    val msg: String? = null
)

// 请求体
data class DeviceLoginReq(
    @SerializedName("device_id") val deviceId: String,
    val email: String = ""
)

data class RegisterReq(
    val username: String,
    val password: String,
    val email: String = "",
    @SerializedName("device_id") val deviceId: String = ""
)

data class LoginReq(
    val username: String,
    val password: String
)

data class LotteryReq(
    val type: String,
    val issue: String,
    @SerializedName("red_balls") val redBalls: String,
    @SerializedName("blue_balls") val blueBalls: String,
    @SerializedName("play_type") val playType: String = "single",
    val multiple: Int = 1,
    @SerializedName("banker_red") val bankerRed: String = "",
    @SerializedName("banker_blue") val bankerBlue: String = ""
)

data class BatchItem(
    val type: String,
    val issue: String,
    @SerializedName("red_balls") val redBalls: String,
    @SerializedName("blue_balls") val blueBalls: String
)

data class BatchReq(val items: List<BatchItem>)

data class BatchResp(
    val ok: Boolean = false,
    val inserted: Int = 0,
    val failed: Int = 0,
    val errors: List<String> = emptyList(),
    val msg: String? = null
)

data class StatusReq(val status: String)

data class UpdateUserReq(
    val username: String? = null,
    val email: String? = null
)

data class MeResp(
    val ok: Boolean = false,
    val user: User? = null
)

// 开奖
data class Draw(
    val id: Long = 0,
    val type: String = "",
    val issue: String = "",
    @SerializedName("draw_date") val drawDate: String = "",
    @SerializedName("red_balls") val redBalls: String = "",
    @SerializedName("blue_balls") val blueBalls: String = ""
)

data class DrawListResp(
    val ok: Boolean = false,
    val list: List<Draw> = emptyList(),
    val msg: String? = null
)

// OCR
data class ParsedItem(
    val type: String = "",
    val issue: String = "",
    @SerializedName("red_balls") val redBalls: String = "",
    @SerializedName("blue_balls") val blueBalls: String = ""
)

data class OcrResp(
    val ok: Boolean = false,
    val parsed: List<ParsedItem> = emptyList(),
    val skipped: List<Map<String, String>> = emptyList(),
    val inserted: List<ParsedItem> = emptyList(),
    val msg: String? = null
)
