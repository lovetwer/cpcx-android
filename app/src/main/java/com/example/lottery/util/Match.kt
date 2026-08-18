package com.example.lottery.util

import kotlin.math.min

/**
 * 中奖匹配与等级计算（从网页 utils/match.js 原样移植，规则与官方一致）。
 * 号码均为逗号分隔的字符串，例如 "03,08,15,20,21,24"。
 */
object Match {

    fun splitNums(s: String?): List<String> =
        (s ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun intersectCount(a: List<String>, b: List<String>): Int {
        val set = b.toSet()
        return a.count { set.contains(it) }
    }

    data class HitBalls(val hitRed: List<String>, val hitBlue: List<String>)

    fun hitBalls(userRed: String?, userBlue: String?, drawRed: String?, drawBlue: String?): HitBalls {
        val dr = splitNums(drawRed).toSet()
        val db = splitNums(drawBlue).toSet()
        return HitBalls(
            splitNums(userRed).filter { dr.contains(it) },
            splitNums(userBlue).filter { db.contains(it) }
        )
    }

    data class TierResult(val mr: Int, val mb: Int, val tier: String, val won: Boolean)

    fun matchTier(type: String, userRed: String?, userBlue: String?, drawRed: String?, drawBlue: String?): TierResult {
        val mr = intersectCount(splitNums(userRed), splitNums(drawRed))
        val mb = intersectCount(splitNums(userBlue), splitNums(drawBlue))
        var tier = ""
        if (type == "ssq") {
            tier = when {
                mr == 6 && mb == 1 -> "一等奖"
                mr == 6 && mb == 0 -> "二等奖"
                mr == 5 && mb == 1 -> "三等奖"
                (mr == 5 && mb == 0) || (mr == 4 && mb == 1) -> "四等奖"
                (mr == 4 && mb == 0) || (mr == 3 && mb == 1) -> "五等奖"
                mb == 1 -> "六等奖"
                mr == 3 && mb == 0 -> "福运奖"
                else -> ""
            }
        } else {
            tier = when {
                mr == 5 && mb == 2 -> "一等奖"
                mr == 5 && mb == 1 -> "二等奖"
                (mr == 5 && mb == 0) || (mr == 4 && mb == 2) -> "三等奖"
                mr == 4 && mb == 1 -> "四等奖"
                (mr == 4 && mb == 0) || (mr == 3 && mb == 2) -> "五等奖"
                (mr == 3 && mb == 1) || (mr == 2 && mb == 2) -> "六等奖"
                (mr == 3 && mb == 0) || (mr == 2 && mb == 1) || (mr == 1 && mb == 2) || (mr == 0 && mb == 2) -> "七等奖"
                else -> ""
            }
        }
        return TierResult(mr, mb, tier, tier.isNotEmpty())
    }

    data class PlayConfig(
        val minRed: Int, val maxRed: Int, val minBlue: Int, val maxBlue: Int,
        val redCount: Int, val blueCount: Int, val name: String,
        val redLabel: String, val blueLabel: String
    )

    fun playConfig(type: String): PlayConfig = if (type == "dlt") {
        PlayConfig(5, 12, 2, 12, 35, 12, "大乐透", "前区", "后区")
    } else {
        PlayConfig(6, 20, 1, 16, 33, 16, "双色球", "红球", "蓝球")
    }

    private fun combinations(arr: List<String>, k: Int): List<List<String>> {
        val res = mutableListOf<List<String>>()
        val n = arr.size
        if (k < 0 || k > n) return res
        val idx = IntArray(k)
        fun rec(start: Int, i: Int) {
            if (i == k) {
                res.add(idx.map { arr[it] })
                return
            }
            var j = start
            while (j < n) {
                idx[i] = j
                rec(j + 1, i + 1)
                j++
            }
        }
        rec(0, 0)
        return res
    }

    private fun diff(a: List<String>, b: List<String>): List<String> {
        val set = b.toSet()
        return a.filter { !set.contains(it) }
    }

    fun enumerateCombos(type: String, red: String?, blue: String?, bankerRed: String?, bankerBlue: String?): List<Pair<List<String>, List<String>>> {
        val cfg = playConfig(type)
        val reds = splitNums(red)
        val blues = splitNums(blue)
        val bRed = splitNums(bankerRed)
        val bBlue = splitNums(bankerBlue)
        val redCombos = if (bRed.isNotEmpty()) {
            combinations(diff(reds, bRed), cfg.minRed - bRed.size).map { bRed + it }
        } else {
            combinations(reds, cfg.minRed)
        }
        val blueCombos = if (bBlue.isNotEmpty()) {
            combinations(diff(blues, bBlue), cfg.minBlue - bBlue.size).map { bBlue + it }
        } else {
            combinations(blues, cfg.minBlue)
        }
        val out = mutableListOf<Pair<List<String>, List<String>>>()
        for (rc in redCombos) for (bc in blueCombos) out.add(Pair(rc, bc))
        return out
    }

    fun ticketBets(type: String, red: String?, blue: String?, bankerRed: String?, bankerBlue: String?): Int {
        val cfg = playConfig(type)
        val reds = splitNums(red)
        val blues = splitNums(blue)
        val bRed = splitNums(bankerRed)
        val bBlue = splitNums(bankerBlue)
        val rc = if (bRed.isNotEmpty()) combinations(diff(reds, bRed), cfg.minRed - bRed.size).size
        else combinations(reds, cfg.minRed).size
        val bc = if (bBlue.isNotEmpty()) combinations(diff(blues, bBlue), cfg.minBlue - bBlue.size).size
        else combinations(blues, cfg.minBlue).size
        return rc * bc
    }

    private val TIER_RANK = mapOf(
        "一等奖" to 1, "二等奖" to 2, "三等奖" to 3, "四等奖" to 4,
        "五等奖" to 5, "六等奖" to 6, "七等奖" to 7, "福运奖" to 8
    )

    data class TicketMatch(
        val won: Boolean, val tier: String, val bestMr: Int, val bestMb: Int,
        val bets: Int, val hitRed: List<String>, val hitBlue: List<String>, val matchText: String
    )

    fun matchTicket(type: String, red: String?, blue: String?, bankerRed: String?, bankerBlue: String?, drawRed: String?, drawBlue: String?): TicketMatch {
        val bets = ticketBets(type, red, blue, bankerRed, bankerBlue)
        val combos = enumerateCombos(type, red, blue, bankerRed, bankerBlue)
        var best = ""
        var bestMr = 0
        var bestMb = 0
        for ((cRed, cBlue) in combos) {
            val r = matchTier(type, cRed.joinToString(","), cBlue.joinToString(","), drawRed, drawBlue)
            if (r.mr > bestMr || (r.mr == bestMr && r.mb > bestMb)) {
                bestMr = r.mr
                bestMb = r.mb
            }
            if (r.tier.isNotEmpty() && (best.isEmpty() || (TIER_RANK[r.tier] ?: 99) < (TIER_RANK[best] ?: 99))) {
                best = r.tier
            }
        }
        val hb = hitBalls(red, blue, drawRed, drawBlue)
        return TicketMatch(
            won = best.isNotEmpty(), tier = best, bestMr = bestMr, bestMb = bestMb,
            bets = bets, hitRed = hb.hitRed, hitBlue = hb.hitBlue,
            matchText = "命中 ${bestMr}+${bestMb}"
        )
    }

    /** 等级徽标配色（与网页 TIER_STYLE 对应）。返回 null 表示无对应样式。 */
    data class TierStyle(val bg: Int, val fg: Int)

    fun tierStyle(tier: String): TierStyle? = when (tier) {
        "一等奖" -> TierStyle(0x2Ec8182e.toInt(), 0xffc0152f.toInt())
        "二等奖" -> TierStyle(0x26d8343f.toInt(), 0xffd8343f.toInt())
        "三等奖" -> TierStyle(0x24e85a5f.toInt(), 0xffd8343f.toInt())
        "四等奖" -> TierStyle(0x21f07a7e.toInt(), 0xffd8343f.toInt())
        "五等奖" -> TierStyle(0x1ff59a9d.toInt(), 0xffd8343f.toInt())
        "六等奖" -> TierStyle(0x1cfab8ba.toInt(), 0xffc0152f.toInt())
        "七等奖" -> TierStyle(0x1afac8c8.toInt(), 0xffc0152f.toInt())
        "福运奖" -> TierStyle(0x26ffc107.toInt(), 0xffb8860b.toInt())
        else -> null
    }

    /** 老数据 prize_tier 可能存数字（"5" / "5等奖"），这里按数字映射到等级样式。 */
    fun tierNumStyle(num: String): TierStyle? {
        val n = num.toIntOrNull() ?: return null
        return when (n) {
            1 -> tierStyle("一等奖")
            2 -> tierStyle("二等奖")
            3 -> tierStyle("三等奖")
            4 -> tierStyle("四等奖")
            5 -> tierStyle("五等奖")
            6 -> tierStyle("六等奖")
            7 -> tierStyle("七等奖")
            8 -> tierStyle("福运奖")
            else -> null
        }
    }
}
