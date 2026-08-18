package com.example.lottery.data.model

/** 用户录入的彩票（字段名与后端 JSON 一致） */
data class Lottery(
    val id: Long = 0,
    val user_id: Long = 0,
    val type: String = "ssq",
    val issue: String = "",
    val red_balls: String = "",
    val blue_balls: String = "",
    val play_type: String = "single",
    val multiple: Int = 1,
    val banker_red: String = "",
    val banker_blue: String = "",
    val bets: Int = 0,
    val status: String = "",
    val prize_tier: String = "",
    val created_at: String = ""
)
