package com.example.lottery.data.model

/** 官方开奖结果（字段名与后端 JSON 一致） */
data class DrawResult(
    val id: Long = 0,
    val type: String = "ssq",
    val issue: String = "",
    val red_balls: String = "",
    val blue_balls: String = "",
    val draw_date: String = "",
    val created_at: String = ""
)
