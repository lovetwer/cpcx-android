package com.example.lottery.data.model

/** 图片识别：单条解析结果 */
data class ParsedLot(
    val type: String = "ssq",
    val issue: String = "",
    val red_balls: String = "",
    val blue_balls: String = ""
)

/** 图片识别：跳过项 */
data class SkippedLot(val reason: String = "")

/** 统一异常：把后端 msg 透传给 UI */
class ApiException(msg: String) : Exception(msg)

/* ---------------- 响应包装（字段名与后端 JSON 一致） ---------------- */

data class BaseResp(
    val ok: Boolean? = null,
    val msg: String? = null
)

data class LoginResp(
    val ok: Boolean? = null,
    val msg: String? = null,
    val token: String? = null,
    val user: User? = null
)

data class MeResp(
    val ok: Boolean? = null,
    val msg: String? = null,
    val user: User? = null
)

data class ListResp<T>(
    val ok: Boolean? = null,
    val msg: String? = null,
    val list: List<T>? = null
)

data class BatchResp(
    val ok: Boolean? = null,
    val msg: String? = null,
    val inserted: Int = 0,
    val failed: Int = 0,
    val errors: List<String>? = null
)

data class RecognizeResp(
    val ok: Boolean? = null,
    val msg: String? = null,
    val parsed: List<ParsedLot>? = null,
    val skipped: List<SkippedLot>? = null
)

data class ShareResp(
    val ok: Boolean? = null,
    val msg: String? = null,
    val code: String? = null
)

/* ---------------- AI 预测响应（后端统一格式） ---------------- */

data class AIPredictResp(
    val ok: Boolean? = null,
    val red: List<String>? = null,
    val blue: List<String>? = null,
    val reason: String? = null,
    val msg: String? = null
)
