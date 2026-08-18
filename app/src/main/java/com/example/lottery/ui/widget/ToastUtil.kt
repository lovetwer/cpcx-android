package com.example.lottery.ui.widget

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.lottery.R
import com.example.lottery.util.dp

/** 底部居中的轻提示（对应网页 Toast）。type: info/success/error/warn */
object ToastUtil {
    fun show(context: Context, msg: String, type: String = "info") {
        if (msg.isEmpty()) return
        val v = LayoutInflater.from(context).inflate(R.layout.toast_layout, null)
        v.findViewById<TextView>(R.id.msg).text = msg
        val dot = v.findViewById<View>(R.id.dot)
        val color = when (type) {
            "success" -> 0xff4ade80.toInt()
            "error" -> 0xfff87171.toInt()
            "warn" -> 0xfffbbf24.toInt()
            else -> 0xff9aa0ad.toInt()
        }
        dot.setBackgroundColor(color)

        val toast = Toast(context)
        toast.view = v
        toast.duration = Toast.LENGTH_SHORT
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(28))
        toast.show()
    }
}
