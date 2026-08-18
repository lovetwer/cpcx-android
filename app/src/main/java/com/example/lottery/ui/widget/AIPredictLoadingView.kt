package com.example.lottery.ui.widget

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.lottery.R
import com.example.lottery.util.dp

/**
 * AI 预测 Loading 视图 —— 复刻 Web 端 AIPredictOverlay 效果
 *
 * 包含：
 * 1. 多层轨道旋转球动画（替代 Loader.svg）
 * 2. 标题文字脉冲透明度动画
 * 3. 副标题
 * 4. 分析步骤滚动切换（6 步，每 1.2s 轮换，带滑入/滑出过渡）
 */
class AIPredictLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 步骤文案，与 Web 端完全一致 */
    private val steps = arrayOf(
        "正在获取开奖记录…",
        "冷热号分析中…",
        "遗漏值分析中…",
        "区间与分区分析中…",
        "和值与跨度分析中…",
        "重邻孤分析中…"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var stepIndex = 0

    private val stepTextView: TextView
    private val orbitView: OrbitLoadingView

    private val stepRunnable = object : Runnable {
        override fun run() {
            stepIndex = (stepIndex + 1) % steps.size
            updateStepText()
            handler.postDelayed(this, 1200)
        }
    }

    init {
        // 整体内容容器
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(40), dp(40), dp(40))
        }

        // 1. 轨道旋转动画
        orbitView = OrbitLoadingView(context).apply {
            val size = dp(200)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = dp(24)
            }
        }
        container.addView(orbitView)

        // 2. 标题
        val title = TextView(context).apply {
            text = "AI 正在分析近30期开奖数据…"
            textSize = 16f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        // 标题脉冲透明度动画（与 Web textPulse 一致）
        val titlePulse = ObjectAnimator.ofFloat(title, "alpha", 0.6f, 1f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        titlePulse.start()
        container.addView(title)

        // 3. 副标题
        val sub = TextView(context).apply {
            text = "冷热号 · 遗漏值 · 区间分区 · 重邻孤"
            textSize = 13f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
        container.addView(sub)

        // 4. 步骤滚动文字
        val stepContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(22)
            )
        }
        stepTextView = TextView(context).apply {
            text = steps[0]
            textSize = 13f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        stepContainer.addView(stepTextView)
        container.addView(stepContainer)

        // 整体居中
        val wrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            addView(container)
        }
        addView(wrapper)
    }

    /** 步骤文字切换，带滑入/滑出动画 */
    private fun updateStepText() {
        // 滑出
        val slideOut = ObjectAnimator.ofFloat(stepTextView, "translationY", 0f, -dp(10).toFloat()).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
        }
        val fadeOut = ObjectAnimator.ofFloat(stepTextView, "alpha", 1f, 0f).apply {
            duration = 200
        }

        val outSet = AnimatorSet()
        outSet.playTogether(slideOut, fadeOut)
        outSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                stepTextView.text = steps[stepIndex]
                // 滑入
                stepTextView.translationY = dp(10).toFloat()
                stepTextView.alpha = 0f

                val slideIn = ObjectAnimator.ofFloat(stepTextView, "translationY", 0f).apply {
                    duration = 250
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val fadeIn = ObjectAnimator.ofFloat(stepTextView, "alpha", 1f).apply {
                    duration = 200
                }
                val inSet = AnimatorSet()
                inSet.playTogether(slideIn, fadeIn)
                inSet.start()
            }
        })
        outSet.start()
    }

    fun start() {
        stepIndex = 0
        stepTextView.text = steps[0]
        stepTextView.translationY = 0f
        stepTextView.alpha = 1f
        handler.postDelayed(stepRunnable, 1200)
        orbitView.start()
    }

    fun stop() {
        handler.removeCallbacks(stepRunnable)
        orbitView.stop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    // 防止 stopView 误用
    fun release() {
        stop()
    }

    /**
     * 轨道旋转球动画 View —— 用 Canvas 绘制多层旋转球轨道
     * 替代 Web 端的 Loader.svg
     */
    private class OrbitLoadingView @JvmOverloads constructor(
        ctx: Context,
        attrs: AttributeSet? = null
    ) : View(ctx, attrs) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val primaryColor = ContextCompat.getColor(ctx, R.color.primary)

        private var rotationAngle = 0f

        private val rotator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { angle ->
                rotationAngle = angle.animatedValue as Float
                invalidate()
            }
        }

        // 多层轨道参数：[半径比例, 球半径dp, 初始角度, 旋转方向(1/-1), 颜色]
        private data class Orbit(
            val radiusRatio: Float,  // 相对view尺寸
            val ballRadiusDp: Int,
            val initialAngle: Float,
            val direction: Int,
            val color: Int,
            val speed: Float   // 旋转速度倍率
        )

        private val orbits = listOf(
            Orbit(0.42f, 7, 0f, 1, primaryColor, 1f),
            Orbit(0.33f, 6, 120f, -1, 0xFFE8A030.toInt(), 1.3f),
            Orbit(0.24f, 5, 240f, 1, 0xFF2F6FB0.toInt(), 1.6f),
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val baseSize = minOf(width, height) / 2f

            // 绘制轨道圆环
            for (orbit in orbits) {
                val r = baseSize * orbit.radiusRatio
                paint.color = (orbit.color and 0x00FFFFFF) or 0x1A000000.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1).toFloat()
                canvas.drawCircle(cx, cy, r, paint)
            }

            // 绘制旋转球
            for (orbit in orbits) {
                val r = baseSize * orbit.radiusRatio
                val angleRad = Math.toRadians(
                    (orbit.initialAngle + rotationAngle * orbit.direction * orbit.speed).toDouble()
                )
                val bx = (cx + r * Math.cos(angleRad)).toFloat()
                val by = (cy + r * Math.sin(angleRad)).toFloat()
                val ballR = dp(orbit.ballRadiusDp).toFloat()

                // 球的发光（模糊光晕）
                ballPaint.color = (orbit.color and 0x00FFFFFF) or 0x33000000.toInt()
                ballPaint.style = Paint.Style.FILL
                canvas.drawCircle(bx, by, ballR * 1.6f, ballPaint)

                // 球主体
                ballPaint.color = orbit.color
                ballPaint.style = Paint.Style.FILL
                canvas.drawCircle(bx, by, ballR, ballPaint)

                // 球高光
                ballPaint.color = 0xFFFFFFFF.toInt()
                ballPaint.alpha = 80
                canvas.drawCircle(
                    (bx - ballR * 0.3).toFloat(),
                    (by - ballR * 0.3).toFloat(),
                    ballR * 0.4f,
                    ballPaint
                )
            }

            // 中心微光
            ballPaint.color = (primaryColor and 0x00FFFFFF) or 0x15000000.toInt()
            ballPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, baseSize * 0.12f, ballPaint)
        }

        fun start() {
            if (!rotator.isStarted) rotator.start()
        }

        fun stop() {
            rotator.cancel()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stop()
        }
    }
}
