package com.example.lottery.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.lottery.R
import com.example.lottery.util.Match
import com.example.lottery.util.dp

/**
 * 红蓝球展示组件（对应网页 <Balls>）。
 * 支持命中标记（绿色描边 + ✓ 角标）、大乐透前后区标签、堆叠模式（隐藏 + 号）。
 */
class BallsView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var sizePx = dp(32)
    private var gapPx = dp(7)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun setSize(dpSize: Int) {
        sizePx = dp(dpSize)
        gapPx = dp(7)
    }

    fun setBalls(
        type: String,
        red: String?,
        blue: String?,
        hitRed: List<String> = emptyList(),
        hitBlue: List<String> = emptyList(),
        stacked: Boolean = false,
        showLabels: Boolean = true
    ) {
        removeAllViews()
        val reds = Match.splitNums(red)
        val blues = Match.splitNums(blue)
        val hitR = hitRed.toSet()
        val hitB = hitBlue.toSet()
        val isDlt = type == "dlt"

        if (stacked) {
            // 堆叠模式：红球一行、蓝球一行（对应网页 Balls 的 stacked）
            orientation = VERTICAL
            gravity = Gravity.END

            val redRow = LinearLayout(context).apply {
                this.orientation = HORIZONTAL
                this.gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), 0)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
            if (isDlt && showLabels) redRow.addView(labelView("前区"))
            for (b in reds) redRow.addView(ballView(b, true, hitR.contains(b)))
            addView(redRow)

            if (blues.isNotEmpty()) {
                val blueRow = LinearLayout(context).apply {
                    this.orientation = HORIZONTAL
                    this.gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(2), 0, dp(2), 0)
                    val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    lp.topMargin = gapPx
                    layoutParams = lp
                }
                if (isDlt && showLabels) blueRow.addView(labelView("后区"))
                for (b in blues) blueRow.addView(ballView(b, false, hitB.contains(b)))
                addView(blueRow)
            }
        } else {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (isDlt && showLabels) addView(labelView("前区"))
            for (b in reds) addView(ballView(b, true, hitR.contains(b)))
            if (blues.isNotEmpty()) {
                addView(sepView())
                if (isDlt && showLabels) addView(labelView("后区"))
                for (b in blues) addView(ballView(b, false, hitB.contains(b)))
            }
        }
    }

    private fun labelView(text: String): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.textSize = 12f
        tv.setTextColor(ContextCompat.getColor(context, R.color.muted))
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(2)
        tv.layoutParams = lp
        return tv
    }

    private fun sepView(): TextView {
        val tv = TextView(context)
        tv.text = "+"
        tv.textSize = 14f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(ContextCompat.getColor(context, R.color.muted))
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.marginStart = gapPx / 2
        lp.marginEnd = gapPx / 2
        tv.layoutParams = lp
        return tv
    }

    private fun ballView(num: String, isRed: Boolean, hit: Boolean): FrameLayout {
        val frame = FrameLayout(context)

        val ball = TextView(context)
        ball.text = num
        ball.gravity = Gravity.CENTER
        ball.setTextColor(Color.WHITE)
        ball.setTypeface(null, Typeface.BOLD)
        ball.textSize = if (sizePx <= dp(24)) 11f else 13f

        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        gd.gradientType = GradientDrawable.RADIAL_GRADIENT
        gd.gradientRadius = sizePx * 0.72f
        gd.setGradientCenter(0.32f, 0.28f)
        val top = if (isRed) R.color.ball_red_top else R.color.ball_blue_top
        val bottom = if (isRed) R.color.ball_red_bottom else R.color.ball_blue_bottom
        gd.colors = intArrayOf(ContextCompat.getColor(context, top), ContextCompat.getColor(context, bottom))
        if (hit) gd.setStroke(dp(3), ContextCompat.getColor(context, R.color.green))
        ball.background = gd

        val blp = FrameLayout.LayoutParams(sizePx, sizePx)
        ball.layoutParams = blp
        frame.addView(ball)

        if (hit) {
            val bsize = dp(15)
            val badge = TextView(context)
            badge.text = "✓"
            badge.gravity = Gravity.CENTER
            badge.setTextColor(Color.WHITE)
            badge.textSize = 9f
            badge.setTypeface(null, Typeface.BOLD)
            badge.setBackgroundColor(ContextCompat.getColor(context, R.color.green))
            badge.layoutParams = FrameLayout.LayoutParams(bsize, bsize)
            badge.translationX = (sizePx - bsize * 0.7f)
            badge.translationY = (-bsize * 0.3f).toFloat()
            frame.addView(badge)
        }

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.marginEnd = gapPx
        frame.layoutParams = lp
        return frame
    }
}
