package com.example.lottery.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lottery.databinding.ItemLotteryBinding
import com.example.lottery.model.Lottery

class LotteryAdapter(
    private val onDelete: (Lottery) -> Unit,
    private val onRedeem: (Lottery) -> Unit
) : RecyclerView.Adapter<LotteryAdapter.VH>() {

    private val items = mutableListOf<Lottery>()

    fun submit(list: List<Lottery>) {
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
        val l = items[position]
        holder.b.tvTitle.text = (if (l.type == "ssq") "双色球" else "大乐透") + "  期号 " + l.issue
        holder.b.tvBalls.text = "红: ${l.redBalls}\n蓝: ${l.blueBalls}"
        holder.b.tvStatus.text = l.status
        holder.b.btnRedeem.visibility = if (l.status == "已兑奖") View.GONE else View.VISIBLE
        holder.b.btnDelete.setOnClickListener { onDelete(l) }
        holder.b.btnRedeem.setOnClickListener { onRedeem(l) }
    }

    override fun getItemCount(): Int = items.size
}
