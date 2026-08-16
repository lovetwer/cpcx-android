package com.example.lottery.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Long = 0,
    val username: String = "",
    @SerializedName("device_id") val deviceId: String = "",
    val email: String = ""
)

data class AuthResp(
    val ok: Boolean = false,
    val token: String = "",
    val user: User? = null
)

data class Lottery(
    val id: Long = 0,
    val type: String = "",          // ssq / dlt
    val issue: String = "",
    @SerializedName("red_balls") val redBalls: String = "",
    @SerializedName("blue_balls") val blueBalls: String = "",
    val status: String = "未开奖"
)

data class ListResp(
    val ok: Boolean = false,
    val list: List<Lottery> = emptyList()
)

data class DeviceLoginReq(@SerializedName("device_id") val deviceId: String)

data class LotteryReq(
    val type: String,
    val issue: String,
    @SerializedName("red_balls") val redBalls: String,
    @SerializedName("blue_balls") val blueBalls: String
)

data class StatusReq(val status: String)

data class OcrItem(
    val id: Long = 0,
    val type: String = "",
    val issue: String = "",
    @SerializedName("red_balls") val redBalls: String = "",
    @SerializedName("blue_balls") val blueBalls: String = "",
    val status: String = ""
)

data class OcrResp(
    val ok: Boolean = false,
    val message: String = "",
    val inserted: List<OcrItem> = emptyList(),
    val skipped: List<Map<String, String>> = emptyList()
)
