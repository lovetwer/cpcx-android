package com.example.lottery.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lottery.LotteryApp
import com.example.lottery.R
import com.example.lottery.data.Api
import com.example.lottery.data.model.DrawResult
import com.example.lottery.data.model.Lottery
import com.example.lottery.databinding.FragmentBuyBinding
import com.example.lottery.databinding.TicketCardBinding
import com.example.lottery.ui.widget.BallsView
import com.example.lottery.ui.widget.ToastUtil
import com.example.lottery.util.Match
import com.example.lottery.util.dp
import com.example.lottery.util.fmtIssue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BuyFragment : Fragment() {

    private var _binding: FragmentBuyBinding? = null
    private val binding get() = _binding!!

    private var list: List<Lottery> = emptyList()
    private var loading = false
    private var filterType = ""
    private var filterStatus = ""

    private val drawMap = mutableMapOf<String, DrawResult>()      // type_draw_date
    private val drawByIssue = mutableMapOf<String, DrawResult>()   // type_issue
    private var latestDraws: List<DrawResult> = emptyList()
    private var curIdx = 0
    private var rotateJob: Job? = null

    private var selectMode = false
    private val selectedIds = mutableListOf<Long>()

    private var rcard: Float = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchMoved = false
    private val rowViews = mutableMapOf<Long, TicketCardBinding>()

    private data class Enriched(
        val lot: Lottery,
        val displayIssue: String,
        val tier: String,
        val tierNum: String,
        val hitRed: List<String>,
        val hitBlue: List<String>,
        val matchText: String,
        val bets: Int,
        val playLabel: String
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBuyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rcard = resources.getDimension(R.dimen.radius_card)

        val typeAdapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.filter_type, R.layout.spinner_item
        )
        typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.filterType.adapter = typeAdapter
        binding.filterType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                filterType = listOf("", "ssq", "dlt")[pos]
                load()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val statusAdapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.filter_status, R.layout.spinner_item
        )
        statusAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.filterStatus.adapter = statusAdapter
        binding.filterStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                filterStatus = listOf("", "未开奖", "未中奖", "已中奖")[pos]
                load()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        binding.btnCancel.setOnClickListener { exitSelect() }
        binding.btnShare.setOnClickListener { shareSelected() }
        binding.btnDelete.setOnClickListener { deleteSelected() }

        // 右上角最新开奖轮播：左右滑动切换（在 ScrollView 内也能滑动）
        binding.latestDraw.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = ev.x
                    touchStartY = ev.y
                    touchMoved = false
                    // 提前告知父容器（ScrollView）不要拦截本次手势，确保横向滑动不被抢走
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (latestDraws.size > 1 && !touchMoved) {
                        val dx = ev.x - touchStartX
                        val dy = ev.y - touchStartY
                        if (Math.abs(dx) > dp(20) && Math.abs(dx) > Math.abs(dy)) {
                            touchMoved = true
                            curIdx = if (dx < 0) (curIdx + 1) % latestDraws.size
                            else (curIdx - 1 + latestDraws.size) % latestDraws.size
                            updateLatest()
                            touchStartX = ev.x
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onPause() {
        super.onPause()
        stopRotate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRotate()
        _binding = null
    }

    /* ---------------- 数据加载 ---------------- */

    private fun load() {
        if (loading) return
        loading = true
        binding.stateBox.visibility = View.VISIBLE
        binding.spinner.visibility = View.VISIBLE
        binding.stateText.text = getString(R.string.loading)
        binding.list.visibility = View.GONE

        lifecycleScope.launch {
            try {
                loadDraws()
                val r = Api.listLottery(buildParams())
                list = r
                if (list.isEmpty()) {
                    binding.spinner.visibility = View.GONE
                    binding.stateText.text = getString(R.string.empty_lottery)
                    binding.list.visibility = View.GONE
                } else {
                    binding.stateBox.visibility = View.GONE
                    binding.list.visibility = View.VISIBLE
                }
                renderList()
                updateHeader()
                updateLatest()
                startRotate()
            } catch (e: Exception) {
                binding.spinner.visibility = View.GONE
                binding.stateText.text = e.message ?: "加载失败"
                ToastUtil.show(requireContext(), e.message ?: "加载失败", "error")
            } finally {
                loading = false
            }
        }
    }

    private fun buildParams(): Map<String, String> {
        val p = mutableMapOf<String, String>()
        if (filterType.isNotEmpty()) p["type"] = filterType
        if (filterStatus.isNotEmpty()) p["status"] = filterStatus
        return p
    }

    private suspend fun loadDraws() {
        drawMap.clear()
        drawByIssue.clear()
        val latest = mutableListOf<DrawResult>()
        for (t in listOf("ssq", "dlt")) {
            val arr = Api.listDraw(t).toMutableList()
            arr.sortByDescending { it.issue.toIntOrNull() ?: 0 }
            for (d in arr) {
                if (d.draw_date.isNotEmpty()) drawMap[t + "_" + d.draw_date] = d
                if (d.issue.isNotEmpty()) drawByIssue[t + "_" + d.issue] = d
            }
            if (arr.isNotEmpty()) latest.add(arr[0])
        }
        latestDraws = latest
        android.util.Log.d("BuyShare", "loadDraws latestDraws.size=${latestDraws.size}")
        if (curIdx >= latestDraws.size) curIdx = 0
    }

    /* ---------------- 富化：中奖等级 / 命中 ---------------- */

    private val PLAY_LABEL = mapOf("single" to "单式", "compound" to "复式", "banker" to "胆拖")

    private fun tierNum(t: String): String {
        if (t.isEmpty()) return ""
        val dm = t.firstOrNull { it.isDigit() }
        if (dm != null) return dm.toString()
        val cn = mapOf('一' to '1', '二' to '2', '两' to '2', '三' to '3', '四' to '4', '五' to '5', '六' to '6')
        for (ch in t) if (cn[ch] != null) return cn[ch].toString()
        return ""
    }

    private fun resolveIssueDate(it: Lottery): String {
        val s = it.issue
        if (s.isEmpty()) return s
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return s
        val d = drawByIssue[it.type + "_" + s]
        if (d != null && d.draw_date.isNotEmpty()) return d.draw_date
        return s
    }

    private fun computeEnriched(): List<Enriched> {
        val out = mutableListOf<Enriched>()
        for (it in list) {
            val displayIssue = resolveIssueDate(it)
            val d = drawMap[it.type + "_" + displayIssue] ?: drawMap[it.type + "_" + it.issue]
            var tier = it.prize_tier
            var tierNum = ""
            var hitRed = emptyList<String>()
            var hitBlue = emptyList<String>()
            var matchText = ""
            var bets = it.bets
            if (d != null) {
                val m = Match.matchTicket(it.type, it.red_balls, it.blue_balls, it.banker_red, it.banker_blue, d.red_balls, d.blue_balls)
                if (tier.isEmpty()) tier = m.tier
                tierNum = tierNum(tier)
                hitRed = m.hitRed
                hitBlue = m.hitBlue
                matchText = m.matchText
                bets = m.bets ?: it.bets
            }
            out.add(Enriched(it, displayIssue, tier, tierNum, hitRed, hitBlue, matchText, bets, PLAY_LABEL[it.play_type] ?: "单式"))
        }
        return out
    }

    /* ---------------- 渲染列表 ---------------- */

    private fun renderList() {
        binding.list.removeAllViews()
        rowViews.clear()
        if (list.isEmpty()) return
        val enriched = computeEnriched()
        for (e in enriched) binding.list.addView(bindRow(e))
        if (selectMode) applySelectionUI()
    }

    private fun chipBg(color: Int): GradientDrawable {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.cornerRadius = dp(11).toFloat()
        gd.setColor(color)
        return gd
    }

    private fun bindRow(e: Enriched): View {
        val b = TicketCardBinding.inflate(layoutInflater, binding.list, false)
        rowViews[e.lot.id] = b
        val isDlt = e.lot.type == "dlt"

        // 彩种标签（圆角胶囊）
        b.chipType.text = if (isDlt) "大乐透" else "双色球"
        b.chipType.background = chipBg(if (isDlt) 0x1Aff4d4d.toInt() else 0x1Ae23b4e.toInt())
        b.chipType.setTextColor(if (isDlt) 0xffff4d4d.toInt() else 0xffe23b4e.toInt())

        // 期号
        b.issue.text = fmtIssue(e.displayIssue)

        // 玩法标签
        b.chipPlay.text = e.playLabel
        b.chipPlay.background = chipBg(ContextCompat.getColor(requireContext(), R.color.surface_2))
        b.chipPlay.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))

        // 倍数标签
        if (e.lot.multiple > 1) {
            b.chipMult.visibility = View.VISIBLE
            b.chipMult.text = "${e.lot.multiple}倍"
            b.chipMult.background = chipBg(ContextCompat.getColor(requireContext(), R.color.primary_100))
            b.chipMult.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        } else {
            b.chipMult.visibility = View.GONE
        }

        // 球号 + 命中
        b.balls.setSize(24)
        b.balls.setBalls(e.lot.type, e.lot.red_balls, e.lot.blue_balls, e.hitRed, e.hitBlue)

        // 底部：命中 / 注数
        b.matchText.text = if (e.matchText.isNotEmpty()) e.matchText
        else if (e.lot.status == "未开奖") "该期尚未开奖" else "未命中"
        b.bets.text = "${e.bets} 注"

        // 状态徽章 / 等级徽章 / 选择框（选择态由 applySelectionUI 统一处理，避免整列表重建）
        b.badge.visibility = View.GONE
        b.tierBadge.visibility = View.GONE
        b.selectCheck.visibility = View.GONE

        if (e.tierNum.isNotEmpty()) {
            val style = Match.tierNumStyle(e.tierNum)
            b.tierBadge.visibility = View.VISIBLE
            b.tierBadge.text = e.tier
            if (style != null) {
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = 999f
                gd.setColor(style.bg)
                gd.setStroke(1, style.fg)
                b.tierBadge.background = gd
                b.tierBadge.setTextColor(style.fg)
            } else {
                b.tierBadge.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_100))
                b.tierBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            }
        } else {
            when (e.lot.status) {
                "未开奖" -> { b.badge.visibility = View.VISIBLE; b.badge.text = "待开奖"; b.badge.setBackgroundResource(R.drawable.bg_badge_pending); b.badge.setTextColor(0xff5b6172.toInt()) }
                "未中奖" -> { b.badge.visibility = View.VISIBLE; b.badge.text = "未中奖"; b.badge.setBackgroundResource(R.drawable.bg_badge_muted); b.badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted)) }
                "已中奖" -> { b.badge.visibility = View.VISIBLE; b.badge.text = "已中奖"; b.badge.setBackgroundResource(R.drawable.bg_badge_muted); b.badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted)) }
            }
        }

        // 卡片底色：中奖等级 / 待开奖 / 已中奖
        applyCardStyle(b, e)

        // 长按进入多选
        b.root.setOnLongClickListener {
            enterSelect(e.lot.id)
            true
        }
        b.root.setOnClickListener {
            if (selectMode) toggleSelect(e.lot.id)
        }

        return b.root
    }

    private fun applyCardStyle(b: TicketCardBinding, e: Enriched) {
        val style = if (e.tierNum.isNotEmpty()) Match.tierNumStyle(e.tierNum) else null
        when {
            style != null -> {
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = rcard
                gd.setColor(style.bg)
                gd.setStroke(1, style.fg)
                b.root.background = gd
                b.leftBar.visibility = View.VISIBLE
                b.leftBar.setBackgroundColor(style.fg)
                b.wonBar.visibility = View.VISIBLE
                b.wonBar.setBackgroundColor(style.fg)
            }
            e.lot.status == "未开奖" -> {
                b.root.setBackgroundResource(R.drawable.bg_ticket_pending)
                b.leftBar.visibility = View.VISIBLE
                b.leftBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.pending_line))
                b.wonBar.visibility = View.GONE
            }
            e.lot.status == "已中奖" -> {
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = rcard
                gd.setColor(0x14c8182e.toInt())
                gd.setStroke(1, 0xffc0152f.toInt())
                b.root.background = gd
                b.leftBar.visibility = View.VISIBLE
                b.leftBar.setBackgroundColor(0xffc0152f.toInt())
                b.wonBar.visibility = View.VISIBLE
                b.wonBar.setBackgroundColor(0xffc0152f.toInt())
            }
            else -> {
                b.root.setBackgroundResource(R.drawable.bg_card)
                b.leftBar.visibility = View.GONE
                b.wonBar.visibility = View.GONE
            }
        }
    }

    private fun updateHeader() {
        val total = list.size
        val pending = list.count { it.status == "未开奖" }
        val wins = list.count { it.status == "已中奖" }
        binding.pageSub.text = "共 $total 张 · 待开奖 $pending\n已中奖 $wins"
    }

    /* ---------------- 最新开奖轮播 ---------------- */

    private fun updateLatest() {
        if (latestDraws.isEmpty()) {
            binding.latestDraw.visibility = View.GONE
            return
        }
        binding.latestDraw.visibility = View.VISIBLE
        val cur = latestDraws[curIdx]
        binding.latestIssue.text = "第 ${cur.issue} 期"
        binding.latestDate.text = cur.draw_date.ifEmpty { "—" }
        binding.latestBalls.setSize(22)
        binding.latestBalls.setBalls(cur.type, cur.red_balls, cur.blue_balls, stacked = true, showLabels = false)

        binding.latestDots.removeAllViews()
        for (i in latestDraws.indices) {
            val dot = View(requireContext())
            val size = if (i == curIdx) 7 else 6
            val lp = ViewGroup.LayoutParams(size, size)
            dot.layoutParams = lp
            dot.setBackgroundResource(if (i == curIdx) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
            val mlp = ViewGroup.MarginLayoutParams(lp)
            mlp.marginEnd = 4
            dot.layoutParams = mlp
            binding.latestDots.addView(dot)
        }
    }

    private fun startRotate() {
        stopRotate()
        if (latestDraws.size < 2) return
        rotateJob = lifecycleScope.launch {
            while (this.isActive) {
                delay(7000)
                curIdx = (curIdx + 1) % latestDraws.size
                updateLatest()
            }
        }
    }

    private fun stopRotate() {
        rotateJob?.cancel()
        rotateJob = null
    }

    /* ---------------- 多选 ---------------- */

    /** 只刷新勾选框，不重建整列表，避免卡顿。 */
    private fun applySelectionUI() {
        for ((id, b) in rowViews) {
            val on = selectedIds.contains(id)
            b.selectCheck.visibility = if (selectMode) View.VISIBLE else View.GONE
            b.selectCheck.text = if (on) "✓" else ""
            b.selectCheck.setBackgroundResource(if (on) R.drawable.bg_check_on else R.drawable.bg_check_off)
        }
    }

    private fun enterSelect(id: Long) {
        selectMode = true
        if (!selectedIds.contains(id)) selectedIds.add(id)
        android.util.Log.d("BuyShare", "enterSelect id=$id selectedIds=$selectedIds")
        applySelectionUI()
        showSelectBar()
    }

    private fun toggleSelect(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        applySelectionUI()
        updateSelectCount()
    }

    private fun exitSelect() {
        selectMode = false
        selectedIds.clear()
        applySelectionUI()
        binding.selectBar.visibility = View.GONE
    }

    private fun showSelectBar() {
        binding.selectBar.visibility = View.VISIBLE
        updateSelectCount()
    }

    private fun updateSelectCount() {
        binding.selectCount.text = "已选 ${selectedIds.size} 张"
    }

    private fun shareSelected() {
        val ids = selectedIds.toList()
        android.util.Log.d("BuyShare", "shareSelected ids=$ids")
        if (ids.isEmpty()) {
            ToastUtil.show(requireContext(), "请先选择要分享的彩票", "error")
            return
        }
        lifecycleScope.launch {
            try {
                val r = Api.createShare(ids)
                android.util.Log.d("BuyShare", "createShare resp ok=${r.ok} code=${r.code} msg=${r.msg}")
                if (!r.code.isNullOrEmpty()) {
                    val url = LotteryApp.WEB_BASE_URL + "/#/share?code=" + r.code
                    copyToClipboard(url)
                    ToastUtil.show(requireContext(), "分享链接已复制：" + url, "success")
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    startActivity(Intent.createChooser(intent, "分享我的彩票"))
                } else {
                    ToastUtil.show(requireContext(), r.msg ?: "分享失败：未返回分享码", "error")
                }
            } catch (e: Exception) {
                android.util.Log.e("BuyShare", "share failed", e)
                ToastUtil.show(requireContext(), e.message ?: "分享失败", "error")
            } finally {
                exitSelect()
            }
        }
    }

    private fun deleteSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("删除确认")
            .setMessage("确定删除选中的 ${ids.size} 张彩票？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    var ok = 0
                    for (id in ids) {
                        try {
                            val r = Api.deleteLottery(id)
                            if (r.ok != false) ok++
                        } catch (_: Exception) {
                        }
                    }
                    ToastUtil.show(requireContext(), "已删除 $ok 张", "success")
                    exitSelect()
                    load()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("share", text))
    }
}
