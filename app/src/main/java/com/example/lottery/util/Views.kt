package com.example.lottery.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

/** dp 转换（基于全局 Application Context，避免到处传 Context 导致扩展函数解析失败） */
private lateinit var appCtx: Context
fun initDp(ctx: Context) { appCtx = ctx.applicationContext }

fun dp(v: Int): Int {
    val d = if (::appCtx.isInitialized) appCtx.resources.displayMetrics.density else 1f
    return (v * d).toInt()
}
fun dpF(v: Float): Float = v * (if (::appCtx.isInitialized) appCtx.resources.displayMetrics.density else 1f)

private val ISSUE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
private val WEEK = arrayOf("日", "一", "二", "三", "四", "五", "六")

/** yyyy-mm-dd → "2026年8月20日" */
fun fmtIssue(s: String?): String {
    if (s.isNullOrEmpty()) return "—"
    val p = s.split("-")
    if (p.size == 3) return "${p[0]}年${p[1].toInt()}月${p[2].toInt()}日"
    return s
}

/** yyyy-mm-dd → "8月20日" */
fun fmtDate(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    val p = s.split("-")
    if (p.size == 3) return "${p[1].toInt()}月${p[2].toInt()}日"
    return s
}

/** yyyy-mm-dd → 星期中文 */
fun weekName(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        val d = ISSUE_FMT.parse(s) ?: return ""
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.time = d
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=周日, 2=周一, ..., 7=周六
        "周" + WEEK[dayOfWeek - 1]
    } catch (e: Exception) {
        ""
    }
}
