package com.example.lottery.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lottery.R
import com.example.lottery.databinding.ItemLotteryBinding
import com.example.lottery.model.Lottery
import com.example.lottery.utils.BallViewFactory
import com.example.lottery.utils.BallViewFactory.BallSize
import com.example.lottery.utils.Match
import com.example.lottery.utils.TierStyle

data class EnrichedLottery(
    val lottery: Lottery,
    val tier: String,
    val tierNum: String,
    val hitRed: List<String>,
    val hitBlue: List<String>,
    val matchText: String,
    val playLabel: String,
    val displayIssue: String,
    val bets: Int
)

class LotteryAdapter(
    private val context: Context,
    private val selectMode: () -> Boolean,
    private val selectedIds: () -> List<Long>,
    private val onLongPress: (Lottery) -> Unit,
    private val onClick: (Lottery) -> Unit
) : RecyclerView.Adapter<LotteryAdapter.VH>() {

    private val items = mutableListOf<EnrichedLottery>()

    fun submit(list: List<EnrichedLottery>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemLotteryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemLotteryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val l = e.lottery
        val isDlt = l.type == "dlt"

        // chip 类型
        holder.b.chipType.text = if (isDlt) "大乐透" else "双色球"
        holder.b.chipType.setTextColor(if (isDlt) 0xFFFF4D4D.toInt() else 0xFFE23B4E.toInt())

        // 期号
        holder.b.tvIssue.text = e.displayIssue

        // 玩法
        holder.b.chipPlay.text = e.playLabel

        // 倍数
        if (l.multiple > 1) {
            holder.b.chipMult.visibility = View.VISIBLE
            holder.b.chipMult.text = "${l.multiple}倍"
        } else {
            holder.b.chipMult.visibility = View.GONE
        }

        // 多选态
        val inSelect = selectMode()
        if (inSelect) {
            holder.b.badgeStatus.visibility = View.GONE
            holder.b.selectCheck.visibility = View.VISIBLE
            val selected = selectedIds().contains(l.id)
            holder.b.selectCheck.setBackgroundResource(
                if (selected) R.drawable.bg_select_check_on else R.drawable.bg_select_check
            )
            holder.b.selectCheck.text = if (selected) "✓" else ""
        } else {
            holder.b.selectCheck.visibility = View.GONE
            holder.b.badgeStatus.visibility = View.VISIBLE
            // 状态徽章
            when (l.status) {
                "未开奖" -> {
                    holder.b.badgeStatus.text = "待开奖"
                    holder.b.badgeStatus.setBackgroundResource(R.drawable.bg_seg_on)
                    holder.b.badgeStatus.setTextColor(0xFF5B6172.toInt())
                }
                "未中奖" -> {
                    holder.b.badgeStatus.text = "未中奖"
                    holder.b.badgeStatus.setBackgroundResource(R.drawable.bg_seg_on)
                    holder.b.badgeStatus.setTextColor(0xFF616061.toInt())
                }
                else -> {
                    // 已中奖：按等级显示徽章
                    if (e.tier.isNotEmpty()) {
                        holder.b.badgeStatus.text = e.tier
                        holder.b.badgeStatus.setTextColor(Color.parseColor(TierStyle.STYLES[e.tier]?.fg ?: "#D8343F"))
                    } else {
                        holder.b.badgeStatus.text = "已中奖"
                        holder.b.badgeStatus.setTextColor(0xFFE23B4E.toInt())
                    }
                    holder.b.badgeStatus.setBackgroundResource(R.drawable.bg_seg_on)
                }
            }
        }

        // 球号
        val reds = Match.splitNums(l.redBalls)
        val blues = Match.splitNums(l.blueBalls)
        BallViewFactory.appendBalls(
            holder.b.ballsRow, context, reds, blues,
            isDlt = isDlt,
            size = BallSize.NORMAL,
            hitRed = e.hitRed,
            hitBlue = e.hitBlue,
            showLabels = true
        )

        // 底部：命中信息 + 注数
        if (e.matchText.isNotEmpty()) {
            holder.b.tvMatch.text = e.matchText
        } else {
            holder.b.tvMatch.text = if (l.status == "未开奖") "该期尚未开奖" else "未命中"
        }
        holder.b.tvBets.text = "${e.bets} 注"

        // 长按 / 点击
        holder.b.root.setOnLongClickListener {
            onLongPress(l)
            true
        }
        holder.b.root.setOnClickListener {
            onClick(l)
        }

        // 中奖卡片描边
        if (l.status == "已中奖" || e.tier.isNotEmpty()) {
            holder.b.root.setBackgroundResource(R.drawable.bg_ticket_won_default)
        } else if (l.status == "未开奖") {
            holder.b.root.setBackgroundResource(R.drawable.bg_ticket_pending)
        } else {
            holder.b.root.setBackgroundResource(R.drawable.bg_card)
        }
    }

    override fun getItemCount(): Int = items.size
}
