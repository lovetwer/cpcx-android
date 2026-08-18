package com.example.lottery.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.lottery.R
import com.example.lottery.util.Match
import com.example.lottery.util.dp
import kotlin.random.Random

/**
 * 选球器（对应网页 <BallPicker>）。
 * 支持单式 / 复式 / 胆拖，红球/蓝球 7 列网格，机选 / 清空。
 */
class BallPickerView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    data class Picked(
        var red: MutableList<String> = mutableListOf(),
        var blue: MutableList<String> = mutableListOf(),
        var bankerRed: MutableList<String> = mutableListOf(),
        var bankerBlue: MutableList<String> = mutableListOf()
    )

    private enum class Style { NORMAL, RED_ON, BLUE_ON, BANKER }

    var type = "ssq"
    var mode = "single" // single | compound | banker
    var picked = Picked()
    var disabled = false
    var onChanged: (() -> Unit)? = null
    var onToast: ((String) -> Unit)? = null
    var onAIPredict: (() -> Unit)? = null

    private val cfg get() = Match.playConfig(type)

    init {
        orientation = VERTICAL
    }

    fun resetTo(type: String, mode: String) {
        this.type = type
        this.mode = mode
        this.picked = Picked()
        rebuild()
    }

    fun setPicked(red: List<String>, blue: List<String>) {
        picked = Picked(red.toMutableList(), blue.toMutableList())
        rebuild()
        onChanged?.invoke()
    }

    fun getBets(): Int = Match.ticketBets(
        type, picked.red.joinToString(","), picked.blue.joinToString(","),
        picked.bankerRed.joinToString(","), picked.bankerBlue.joinToString(",")
    )

    private fun nums(n: Int): List<String> =
        (1..n).map { String.format("%02d", it) }

    private fun arr(g: String): MutableList<String> = if (g == "red") picked.red else picked.blue
    private fun banker(g: String): MutableList<String> = if (g == "red") picked.bankerRed else picked.bankerBlue

    private fun rebuild() {
        removeAllViews()
        if (mode != "banker") {
            addGroup("red")
            addGroup("blue")
        } else {
            addBankerGroup("red")
            addBankerGroup("blue")
        }
        addTools()
    }

    private fun header(label: String, countText: String, full: Boolean): LinearLayout {
        val hl = LinearLayout(context)
        hl.orientation = HORIZONTAL
        hl.gravity = Gravity.CENTER_VERTICAL
        hl.setPadding(0, 0, 0, dp(10))

        val lab = TextView(context)
        lab.text = label
        lab.textSize = 13f
        lab.setTypeface(null, Typeface.BOLD)
        lab.setTextColor(ContextCompat.getColor(context, R.color.text))

        val cnt = TextView(context)
        cnt.text = countText
        cnt.textSize = 12f
        cnt.setTextColor(
            ContextCompat.getColor(
                context,
                if (full) R.color.primary else R.color.muted
            )
        )

        hl.addView(lab)
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(8)
        cnt.layoutParams = lp
        hl.addView(cnt)
        return hl
    }

    private fun grid(): GridLayout {
        val g = GridLayout(context)
        g.columnCount = 7
        return g
    }

    private fun addGroup(g: String) {
        val m = if (g == "red") cfg.maxRed to cfg.redLabel else cfg.maxBlue to cfg.blueLabel
        val max = m.first
        val label = m.second
        val count = arr(g).size
        addView(header("$label", "已选 $count/$max", count >= max))

        val grid = grid()
        for (n in nums(if (g == "red") cfg.redCount else cfg.blueCount)) {
            val selected = arr(g).contains(n)
            val dis = disabled || (!selected && count >= max)
            grid.addView(
                pickButton(
                    n, g == "red",
                    if (selected) (if (g == "red") Style.RED_ON else Style.BLUE_ON) else Style.NORMAL,
                    dis
                ) { toggleNormal(g, n) }
            )
        }
        addView(grid)
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(18)
        grid.layoutParams = lp
    }

    private fun addBankerGroup(g: String) {
        val isRed = g == "red"
        val max = if (isRed) cfg.maxRed else cfg.maxBlue
        val bankerMax = if (isRed) cfg.minRed - 1 else cfg.minBlue - 1
        val label = if (isRed) cfg.redLabel else cfg.blueLabel
        val bCount = banker(g).size
        val rCount = arr(g).size
        val dragCount = rCount - bCount

        // 胆码
        addView(header("$label · 胆码", "胆 $bCount/$bankerMax", bCount >= bankerMax))
        val g1 = grid()
        for (n in nums(if (isRed) cfg.redCount else cfg.blueCount)) {
            val isB = banker(g).contains(n)
            g1.addView(pickButton(n, isRed, if (isB) Style.BANKER else Style.NORMAL, disabled) { toggleBanker(g, n) })
        }
        addView(g1)
        val lp1 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp1.bottomMargin = dp(12)
        g1.layoutParams = lp1

        // 拖码
        addView(header("$label · 拖码", "拖 $dragCount", false))
        val g2 = grid()
        for (n in nums(if (isRed) cfg.redCount else cfg.blueCount)) {
            val isB = banker(g).contains(n)
            val isDrag = arr(g).contains(n) && !isB
            val dis = disabled || isB || (!arr(g).contains(n) && rCount >= max)
            g2.addView(
                pickButton(
                    n, isRed,
                    if (isDrag) (if (isRed) Style.RED_ON else Style.BLUE_ON) else Style.NORMAL,
                    dis
                ) { toggleDrag(g, n) }
            )
        }
        addView(g2)
        val lp2 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp2.bottomMargin = dp(18)
        g2.layoutParams = lp2
    }

    private fun addTools() {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val aiBtn = toolButton("AI预测", true)
        val clearBtn = toolButton("清空", false)
        aiBtn.setOnClickListener { if (!disabled) onAIPredict?.invoke() }
        clearBtn.setOnClickListener { if (!disabled && (picked.red.isNotEmpty() || picked.blue.isNotEmpty())) clearAll() }

        aiBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        clearBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10)
        }
        row.addView(aiBtn)
        row.addView(clearBtn)
        addView(row)
    }

    private fun toolButton(text: String, isAI: Boolean = false): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, dp(10), 0, dp(10))
        tv.textSize = 13f
        tv.setTypeface(null, Typeface.BOLD)
        if (isAI) {
            tv.setTextColor(ContextCompat.getColor(context, R.color.primary))
            tv.background = aiButtonDrawable()
        } else {
            tv.setTextColor(ContextCompat.getColor(context, R.color.muted))
            tv.setBackgroundResource(R.drawable.bg_tool_btn)
        }
        return tv
    }

    private fun aiButtonDrawable(): GradientDrawable {
        val gd = GradientDrawable()
        gd.cornerRadius = dp(10).toFloat()
        gd.setColor(ContextCompat.getColor(context, R.color.surface))
        gd.setStroke(dp(1), ContextCompat.getColor(context, R.color.primary))
        return gd
    }

    private fun pickButton(num: String, isRed: Boolean, style: Style, dis: Boolean, onClick: () -> Unit): TextView {
        val tv = TextView(context)
        tv.text = num
        tv.gravity = Gravity.CENTER
        tv.setTypeface(null, Typeface.BOLD)
        tv.textSize = 14f
        tv.background = pickDrawable(isRed, style)
        tv.setTextColor(
            when (style) {
                Style.NORMAL -> ContextCompat.getColor(context, R.color.text)
                Style.BANKER -> ContextCompat.getColor(context, R.color.ball_banker_bottom)
                else -> Color.WHITE
            }
        )
        tv.isEnabled = !dis
        if (dis) tv.alpha = 0.38f
        tv.setOnClickListener { if (!dis) onClick() }

        val size = dp(40)
        val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED))
        lp.width = size
        lp.height = size
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        tv.layoutParams = lp
        return tv
    }

    private fun pickDrawable(isRed: Boolean, style: Style): GradientDrawable {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        when (style) {
            Style.NORMAL -> {
                gd.setColor(ContextCompat.getColor(context, R.color.surface))
                gd.setStroke(dp(1), ContextCompat.getColor(context, R.color.border))
            }
            Style.RED_ON -> radial(R.color.ball_red_top, R.color.ball_red_bottom, gd)
            Style.BLUE_ON -> radial(R.color.ball_pick_blue_top, R.color.ball_pick_blue_bottom, gd)
            Style.BANKER -> radial(R.color.ball_banker_top, R.color.ball_banker_bottom, gd)
        }
        return gd
    }

    private fun radial(topRes: Int, bottomRes: Int, gd: GradientDrawable) {
        gd.gradientType = GradientDrawable.RADIAL_GRADIENT
        gd.gradientRadius = dp(28).toFloat()
        gd.setGradientCenter(0.32f, 0.28f)
        gd.colors = intArrayOf(
            ContextCompat.getColor(context, topRes),
            ContextCompat.getColor(context, bottomRes)
        )
    }

    /* ---------------- 选号逻辑（对齐网页 BallPicker.vue） ---------------- */

    private fun toggleNormal(g: String, n: String) {
        val m = if (g == "red") cfg.maxRed else cfg.maxBlue
        val a = arr(g)
        if (a.contains(n)) a.remove(n)
        else {
            if (a.size >= m) { onToast?.invoke("最多选择 $m 个${if (g == "red") cfg.redLabel else cfg.blueLabel}"); return }
            a.add(n)
        }
        rebuild(); onChanged?.invoke()
    }

    private fun toggleBanker(g: String, n: String) {
        val bkey = banker(g)
        val normal = arr(g)
        val bankerMax = if (g == "red") cfg.minRed - 1 else cfg.minBlue - 1
        if (bkey.contains(n)) {
            bkey.remove(n)
        } else {
            if (bkey.size >= bankerMax) { onToast?.invoke("胆码最多 $bankerMax 个${if (g == "red") cfg.redLabel else cfg.blueLabel}"); return }
            bkey.add(n)
            if (!normal.contains(n)) normal.add(n)
        }
        rebuild(); onChanged?.invoke()
    }

    private fun toggleDrag(g: String, n: String) {
        if (banker(g).contains(n)) { onToast?.invoke("该号已设为胆码"); return }
        val normal = arr(g)
        val max = if (g == "red") cfg.maxRed else cfg.maxBlue
        if (normal.contains(n)) normal.remove(n)
        else {
            if (normal.size >= max) { onToast?.invoke("最多选择 $max 个${if (g == "red") cfg.redLabel else cfg.blueLabel}"); return }
            normal.add(n)
        }
        rebuild(); onChanged?.invoke()
    }

    private fun randomPick() {
        val reds = nums(cfg.redCount).shuffled(Random).take(cfg.minRed).sorted()
        val blues = nums(cfg.blueCount).shuffled(Random).take(cfg.minBlue).sorted()
        picked = Picked(reds.toMutableList(), blues.toMutableList())
        rebuild(); onChanged?.invoke()
    }

    private fun clearAll() {
        picked = Picked()
        rebuild(); onChanged?.invoke()
    }
}
