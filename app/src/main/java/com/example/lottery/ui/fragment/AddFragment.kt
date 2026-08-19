package com.example.lottery.ui.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lottery.LotteryApp
import com.example.lottery.MainActivity
import com.example.lottery.R
import com.example.lottery.data.Api
import com.example.lottery.data.model.DrawResult
import com.example.lottery.data.model.ParsedLot
import com.example.lottery.databinding.FragmentAddBinding
import com.example.lottery.databinding.DialogBatchBinding
import com.example.lottery.ui.widget.BallsView
import com.example.lottery.ui.widget.AIPredictLoadingView
import com.example.lottery.ui.widget.BallPickerView
import com.example.lottery.ui.widget.ToastUtil
import com.example.lottery.util.ImageUtil
import com.example.lottery.util.Match
import com.example.lottery.util.fmtDate
import com.example.lottery.util.weekName
import com.example.lottery.util.dp
import kotlinx.coroutines.launch
import java.util.Calendar

class AddFragment : Fragment() {

    private var _binding: FragmentAddBinding? = null
    private val binding get() = _binding!!

    private var type = "ssq"
    private var mode = "single" // single | compound | banker
    private var issue = ""
    private var multiple = 1
    private var draws = emptyList<DrawResult>()
    private var recognizing = false
    private var saving = false
    private var multiParsed = emptyList<ParsedLot>()

    private val issueDates = mutableListOf<String>()
    private val issueLabels = mutableListOf<String>()

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) doRecognize(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.segSsq.setOnClickListener { setType("ssq") }
        binding.segDlt.setOnClickListener { setType("dlt") }
        binding.segSingle.setOnClickListener { setMode("single") }
        binding.segCompound.setOnClickListener { setMode("compound") }
        binding.segBanker.setOnClickListener { setMode("banker") }

        binding.ballPicker.onChanged = { updatePreview(); updateSaveSummary() }
        binding.ballPicker.onToast = { ToastUtil.show(requireContext(), it, "warn") }
        binding.ballPicker.onAIPredict = { doAIPredict() }

        binding.issueSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (pos in issueDates.indices) issue = issueDates[pos]
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>) {}
        }
        binding.issueInput.addTextChangedListener(SimpleWatcher { issue = it })

        binding.ocrDrop.setOnClickListener { pickImage.launch("image/*") }
        binding.batchLink.setOnClickListener { showBatchDialog() }

        binding.stepMinus.setOnClickListener { setMultiple(multiple - 1) }
        binding.stepPlus.setOnClickListener { setMultiple(multiple + 1) }
        binding.stepInput.addTextChangedListener(SimpleWatcher {
            val n = it.toIntOrNull() ?: 1
            multiple = n.coerceIn(1, 99)
            updateSaveSummary()
            updatePreview()
        })

        binding.saveBtn.setOnClickListener { save() }

        // 初始 UI
        updateTypeSeg()
        updateModeSeg()
        binding.ballPicker.resetTo(type, mode)
        loadDraws(false)
    }

    /* ---------------- 彩种 / 玩法 ---------------- */

    private fun setType(t: String, refresh: Boolean = true) {
        if (t == type && refresh) return
        type = t
        mode = "single"
        updateTypeSeg()
        updateModeSeg()
        binding.ballPicker.resetTo(type, mode)
        if (refresh) loadDraws(false)
    }

    private fun setMode(m: String) {
        if (m == mode) return
        mode = m
        updateModeSeg()
        binding.ballPicker.resetTo(type, mode)
        updatePreview()
        updateSaveSummary()
    }

    private fun updateTypeSeg() {
        binding.segSsq.setBackgroundResource(if (type == "ssq") R.drawable.bg_segment_item else 0)
        binding.segSsq.setTextColor(ContextCompat.getColor(requireContext(), if (type == "ssq") R.color.primary else R.color.muted))
        binding.segDlt.setBackgroundResource(if (type == "dlt") R.drawable.bg_segment_item else 0)
        binding.segDlt.setTextColor(ContextCompat.getColor(requireContext(), if (type == "dlt") R.color.primary else R.color.muted))
    }

    private fun updateModeSeg() {
        val map = listOf(Pair(binding.segSingle, "single"), Pair(binding.segCompound, "compound"), Pair(binding.segBanker, "banker"))
        for ((v, m) in map) {
            v.setBackgroundResource(if (m == mode) R.drawable.bg_segment_item else 0)
            v.setTextColor(ContextCompat.getColor(requireContext(), if (m == mode) R.color.primary else R.color.muted))
        }
    }

    /* ---------------- 开奖日期 ---------------- */

    private fun loadDraws(keep: Boolean) {
        lifecycleScope.launch {
            draws = try { Api.listDraw(type) } catch (e: Exception) { emptyList() }
            refreshOptions(keep)
            updatePreview()
            updateSaveSummary()
        }
    }

    private fun refreshOptions(keep: Boolean) {
        val upcoming = computeUpcoming(type)
        val history = draws.sortedByDescending { it.issue }.mapNotNull { if (it.draw_date.isNotEmpty()) it.draw_date else null }

        issueDates.clear(); issueLabels.clear()
        for (d in upcoming) {
            issueDates.add(d)
            issueLabels.add("${fmtDate(d)} ${weekName(d)} · 未开奖")
        }
        for (d in history) {
            issueDates.add(d)
            issueLabels.add("${fmtDate(d)} ${weekName(d)} · 已开奖")
        }

        if (draws.isEmpty()) {
            binding.issueSpinner.visibility = View.GONE
            binding.issueInput.visibility = View.VISIBLE
            binding.issueHint.text = "暂无开奖数据，可手动填写期号"
            if (!keep || issue.isEmpty()) issue = binding.issueInput.text.toString().trim()
        } else {
            binding.issueSpinner.visibility = View.VISIBLE
            binding.issueInput.visibility = View.GONE
            binding.issueHint.text = "默认选“未开奖”期，买的是未来开奖的彩票"
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, issueLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.issueSpinner.adapter = adapter
            val target = if (keep && issue in issueDates) issue else (upcoming.firstOrNull() ?: "")
            issue = target
            val idx = issueDates.indexOf(target).coerceAtLeast(0)
            binding.issueSpinner.setSelection(idx)
        }
    }

    private fun computeUpcoming(type: String): List<String> {
        val cal = Calendar.getInstance()
        val sched = if (type == "dlt")
            setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.SATURDAY)
        else
            setOf(Calendar.SUNDAY, Calendar.TUESDAY, Calendar.THURSDAY)
        val out = mutableListOf<String>()
        var guard = 0
        while (out.size < 12 && guard < 400) {
            guard++
            if (sched.contains(cal.get(Calendar.DAY_OF_WEEK))) out.add(isoLocal(cal))
            cal.add(Calendar.DATE, 1)
        }
        return out
    }

    private fun isoLocal(cal: Calendar): String {
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    /* ---------------- 实时预览 ---------------- */

    private fun updatePreview() {
        val c = binding.previewCard
        c.removeAllViews()
        val head = TextView(requireContext()).apply {
            text = "本轮已选（实时）"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
        }
        c.addView(head)

        val cfg = Match.playConfig(type)
        addGroupPreview(cfg.redLabel, binding.ballPicker.picked.red, true, false)
        if (mode == "banker") addGroupPreview("红胆", binding.ballPicker.picked.bankerRed, true, true)
        addGroupPreview(cfg.blueLabel, binding.ballPicker.picked.blue, false, false)
        if (mode == "banker") addGroupPreview("蓝胆", binding.ballPicker.picked.bankerBlue, false, true)

        val bets = binding.ballPicker.getBets()
        val amount = bets * multiple * 2
        val sum = TextView(requireContext()).apply {
            text = "共 $bets 注 · $multiple 倍 · 合计 ¥$amount"
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        c.addView(sum)
    }

    private fun addGroupPreview(tag: String, nums: List<String>, isRed: Boolean, isBanker: Boolean) {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        row.layoutParams = lp

        val tagView = TextView(requireContext()).apply {
            text = tag
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = ContextCompat.getDrawable(requireContext(), if (isRed) R.drawable.bg_pp_red else R.drawable.bg_pp_blue)
            setTextColor(ContextCompat.getColor(requireContext(), if (isRed) R.color.ball_red_bottom else R.color.blue))
        }
        row.addView(tagView)

        if (nums.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = if (isBanker) "未设" else "未选"
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
                val elp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                elp.marginStart = dp(8)
                layoutParams = elp
            }
            row.addView(empty)
        } else {
            val bv = BallsView(requireContext()).apply { setSize(26); setBalls(type, nums.joinToString(","), "") }
            val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            blp.marginStart = dp(8)
            bv.layoutParams = blp
            row.addView(bv)
        }
        binding.previewCard.addView(row)
    }

    private fun updateSaveSummary() {
        val cfg = Match.playConfig(type)
        val modeLabel = when (mode) { "compound" -> "复式"; "banker" -> "胆拖"; else -> "单式" }
        binding.saveSummary1.text = "${cfg.name} · $modeLabel"
        val bets = binding.ballPicker.getBets()
        val amount = bets * multiple * 2
        binding.saveSummary2.text = "开奖日 ${if (issue.isEmpty()) "—" else fmtDate(issue)} · $bets 注 × $multiple 倍 = ¥$amount"
    }

    private fun setMultiple(v: Int) {
        multiple = v.coerceIn(1, 99)
        binding.stepInput.setText(multiple.toString())
        updateSaveSummary()
        updatePreview()
    }

    /* ---------------- 校验 / 保存 ---------------- */

    private fun valid(): Boolean {
        val c = Match.playConfig(type)
        val r = binding.ballPicker.picked.red.size
        val b = binding.ballPicker.picked.blue.size
        val br = binding.ballPicker.picked.bankerRed.size
        val bb = binding.ballPicker.picked.bankerBlue.size
        val dr = r - br
        val db = b - bb
        if (issue.trim().isEmpty()) return false
        return when (mode) {
            "single" -> r == c.minRed && b == c.minBlue
            "compound" -> r in c.minRed..c.maxRed && b in c.minBlue..c.maxBlue && (r > c.minRed || b > c.minBlue)
            else -> {
                if (br == 0 && bb == 0) false
                else if (br > c.minRed - 1 || bb > c.minBlue - 1) false
                else if (dr < c.minRed - br || db < c.minBlue - bb) false
                else if (r > c.maxRed || b > c.maxBlue) false
                else true
            }
        }
    }

    private fun save() {
        if (saving) return
        if (!valid()) {
            if (issue.trim().isEmpty()) { ToastUtil.show(requireContext(), "请选择开奖日期", "warn"); return }
            ToastUtil.show(requireContext(), "号码不符合当前玩法规则", "warn"); return
        }
        saving = true
        binding.saveSpinner.visibility = View.VISIBLE
        binding.saveText.visibility = View.GONE
        val p = binding.ballPicker.picked
        lifecycleScope.launch {
            try {
                val r = Api.createLottery(
                    mapOf(
                        "type" to type,
                        "issue" to issue.trim(),
                        "red_balls" to p.red.joinToString(","),
                        "blue_balls" to p.blue.joinToString(","),
                        "play_type" to mode,
                        "multiple" to multiple,
                        "banker_red" to p.bankerRed.joinToString(","),
                        "banker_blue" to p.bankerBlue.joinToString(",")
                    )
                )
                if (r.ok != false) {
                    ToastUtil.show(requireContext(), "录入成功", "success")
                    // 重置
                    issue = ""
                    setMultiple(1)
                    binding.ballPicker.resetTo(type, "single")
                    loadDraws(false)
                    (requireActivity() as MainActivity).setTab(2)
                } else {
                    ToastUtil.show(requireContext(), r.msg ?: "录入失败", "error")
                }
            } catch (e: Exception) {
                ToastUtil.show(requireContext(), e.message ?: "录入失败", "error")
            } finally {
                saving = false
                binding.saveSpinner.visibility = View.GONE
                binding.saveText.visibility = View.VISIBLE
            }
        }
    }

    /* ---------------- 图片识别 ---------------- */

    private fun doRecognize(uri: android.net.Uri) {
        if (recognizing) return
        recognizing = true
        binding.ocrSpinner.visibility = View.VISIBLE
        binding.ocrText.text = "识别中…"
        lifecycleScope.launch {
            try {
                val part = ImageUtil.toMultipart(requireContext(), uri)
                if (part == null) { ToastUtil.show(requireContext(), "读取图片失败", "error"); return@launch }
                val r = Api.recognize(part, true)
                when {
                    r.parsed != null && r.parsed.size > 1 -> {
                        multiParsed = r.parsed
                        showMulti()
                        ToastUtil.show(requireContext(), "识别到 ${r.parsed.size} 注，请核对后点击「保存全部」", "info")
                    }
                    r.parsed != null && r.parsed.size == 1 -> {
                        val p = r.parsed[0]
                        setType(p.type, refresh = false)
                        val fallback = draws.firstOrNull()?.draw_date ?: computeUpcoming(type).firstOrNull() ?: ""
                        issue = if (p.issue.isNotEmpty()) p.issue else fallback
                        binding.ballPicker.setPicked(Match.splitNums(p.red_balls), Match.splitNums(p.blue_balls))
                        loadDraws(keep = true)
                        ToastUtil.show(requireContext(), "已从图片识别，请核对后点击「保存彩票」", "info")
                        // 滚动到选球器可见
                        binding.ballPicker.post { binding.root.smoothScrollTo(0, binding.ballPicker.top) }
                    }
                    r.skipped != null && r.skipped.isNotEmpty() -> {
                        ToastUtil.show(requireContext(), r.skipped[0].reason, "error")
                    }
                    else -> ToastUtil.show(requireContext(), r.msg ?: "识别失败，请换一张号码区清晰的截图或手动录入", "error")
                }
            } catch (e: Exception) {
                ToastUtil.show(requireContext(), e.message ?: "识别失败", "error")
            } finally {
                recognizing = false
                binding.ocrSpinner.visibility = View.GONE
                binding.ocrText.text = "上传彩票截图，自动识别号码"
            }
        }
    }

    private fun showMulti() {
        val c = binding.multiContainer
        c.visibility = View.VISIBLE
        c.removeAllViews()
        val head = TextView(requireContext()).apply {
            text = "识别到 ${multiParsed.size} 注，请核对后一键保存"
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        }
        c.addView(head)
        multiParsed.forEachIndexed { i, b ->
            // 每注用垂直布局：第一行是序号+彩种+期号，第二行是号码球（可横向滚动）
            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(10)
                layoutParams = lp
            }
            // 第一行：序号 + 彩种 + 期号
            val infoRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val idx = TextView(requireContext()).apply {
                text = (i + 1).toString(); textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pp_red)
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }
            val typeLabel = TextView(requireContext()).apply {
                text = if (b.type == "dlt") " 大乐透 " else " 双色球 "; textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
            }
            val issueLabel = TextView(requireContext()).apply {
                text = if (b.issue.isNotEmpty()) " 第${b.issue}期 " else " 期号待识别 "; textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
            }
            infoRow.addView(idx); infoRow.addView(typeLabel); infoRow.addView(issueLabel)
            item.addView(infoRow)

            // 第二行：号码球，用 HorizontalScrollView 包裹防止超出屏幕宽度被截断
            val scroll = android.widget.HorizontalScrollView(requireContext()).apply {
                isHorizontalScrollBarEnabled = false
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6)
                layoutParams = lp
            }
            val bv = BallsView(requireContext()).apply { setSize(26); setBalls(b.type, b.red_balls, b.blue_balls) }
            scroll.addView(bv)
            item.addView(scroll)
            c.addView(item)
        }
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(10); layoutParams = lp }
        val saveAll = TextView(requireContext()).apply { text = "保存全部 ${multiParsed.size} 注"; setPadding(dp(16), dp(11), dp(16), dp(11)); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(ContextCompat.getColor(requireContext(), R.color.white)); background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_btn_primary) }
        val cancel = TextView(requireContext()).apply { text = "取消"; setPadding(dp(16), dp(11), dp(16), dp(11)); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(ContextCompat.getColor(requireContext(), R.color.text)); background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_btn_ghost); val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.marginStart = dp(10); layoutParams = lp }
        saveAll.setOnClickListener { saveMulti() }
        cancel.setOnClickListener { multiParsed = emptyList(); binding.multiContainer.visibility = View.GONE; binding.multiContainer.removeAllViews() }
        actions.addView(saveAll); actions.addView(cancel)
        c.addView(actions)

        // 滚动到多注区域可见
        binding.multiContainer.post { binding.root.smoothScrollTo(0, binding.multiContainer.top) }
    }

    private fun saveMulti() {
        if (multiParsed.isEmpty()) return
        val fallback = draws.firstOrNull()?.draw_date ?: computeUpcoming(type).firstOrNull() ?: ""
        val items = multiParsed.map {
            mapOf(
                "type" to it.type,
                "issue" to (if (it.issue.isNotEmpty()) it.issue else fallback),
                "red_balls" to it.red_balls,
                "blue_balls" to it.blue_balls
            )
        }
        lifecycleScope.launch {
            try {
                val r = Api.batchLottery(items)
                val inserted = r.inserted
                val failed = r.failed
                if (inserted > 0 && failed == 0) {
                    ToastUtil.show(requireContext(), "已保存 $inserted 注", "success")
                    multiParsed = emptyList()
                    binding.multiContainer.visibility = View.GONE
                    binding.multiContainer.removeAllViews()
                    (requireActivity() as MainActivity).setTab(2)
                } else if (inserted > 0) {
                    ToastUtil.show(requireContext(), "已保存 $inserted 注，$failed 注失败：${r.errors?.firstOrNull() ?: "号码不符合规则"}", "warn")
                } else {
                    ToastUtil.show(requireContext(), "保存失败：${r.errors?.firstOrNull() ?: "号码不符合规则（双色球需6红+1蓝，大乐透需5前+2后）"}", "error")
                }
            } catch (e: Exception) {
                ToastUtil.show(requireContext(), e.message ?: "保存失败", "error")
            }
        }
    }

    /* ---------------- 批量录入弹窗 ---------------- */

    private fun showBatchDialog() {
        val dlgBinding = DialogBatchBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(dlgBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dlgBinding.close.setOnClickListener { dialog.dismiss() }
        dlgBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dlgBinding.btnStart.setOnClickListener {
            val items = parseBatch(dlgBinding.batchText.text.toString())
            if (items.isEmpty()) { ToastUtil.show(requireContext(), "没有可解析的记录，格式见下方示例", "warn"); return@setOnClickListener }
            lifecycleScope.launch {
                try {
                    val r = Api.batchLottery(items)
                    dlgBinding.batchResult.visibility = View.VISIBLE
                    dlgBinding.batchResult.removeAllViews()
                    dlgBinding.batchResult.addView(TextView(requireContext()).apply { text = "成功 ${r.inserted} 条"; textSize = 13f; setTextColor(ContextCompat.getColor(requireContext(), R.color.green)) })
                    if (r.failed > 0) dlgBinding.batchResult.addView(TextView(requireContext()).apply { text = "失败 ${r.failed} 条"; textSize = 13f; setTextColor(ContextCompat.getColor(requireContext(), R.color.red)) })
                    r.errors?.forEach { e -> dlgBinding.batchResult.addView(TextView(requireContext()).apply { text = e; textSize = 12f; setTextColor(ContextCompat.getColor(requireContext(), R.color.muted)) }) }
                    ToastUtil.show(requireContext(), "成功 ${r.inserted} 条，失败 ${r.failed} 条", if (r.failed > 0) "warn" else "success")
                } catch (e: Exception) {
                    ToastUtil.show(requireContext(), e.message ?: "批量失败", "error")
                }
            }
        }
        dialog.show()
    }

    private fun parseBatch(text: String): List<Map<String, String>> {
        val items = mutableListOf<Map<String, String>>()
        for (line in text.split("\n")) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val p = t.split(",")
            if (p.size < 4) continue
            items.add(mapOf("type" to p[0].trim().lowercase(), "issue" to p[1].trim(), "red_balls" to p[2].trim(), "blue_balls" to p[3].trim()))
        }
        return items
    }

    /* ---------------- AI 预测 ---------------- */

    private fun doAIPredict() {
        // 创建与 Web 端一致的 AI 预测 loading 视图
        val loadingView = AIPredictLoadingView(requireContext())
        loadingView.start()

        // 使用 Dialog 全屏遮罩，半透明黑色背景（与 Web 端 rgba(15,15,20,0.75) 一致）
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(loadingView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // 遮罩背景半透明黑
        dialog.window?.setDimAmount(0.75f)
        dialog.show()

        lifecycleScope.launch {
            try {
                // 调用 AI 预测（历史记录由服务端按 type 拉取）
                val result = Api.aiPredict(type)

                // 3. 格式化号码
                val cfg = Match.playConfig(type)
                val reds = (result.red ?: emptyList())
                    .map { it.padStart(2, '0') }
                    .filter {
                        val v = it.toIntOrNull() ?: 0
                        v in 1..cfg.redCount
                    }
                    .take(cfg.maxRed)
                    .sorted()
                val blues = (result.blue ?: emptyList())
                    .map { it.padStart(2, '0') }
                    .filter {
                        val v = it.toIntOrNull() ?: 0
                        v in 1..cfg.blueCount
                    }
                    .take(cfg.maxBlue)
                    .sorted()

                // 4. 校验并填充
                if (reds.size < cfg.minRed || blues.size < cfg.minBlue) {
                    loadingView.stop()
                    dialog.dismiss()
                    ToastUtil.show(requireContext(), "AI 返回号码数量不足，已退回机选", "warn")
                    randomPickFallback()
                    return@launch
                }

                binding.ballPicker.setPicked(reds, blues)
                loadingView.stop()
                dialog.dismiss()
                ToastUtil.show(requireContext(), "AI 预测已填充", "success")

                // 显示分析理由
                val reason = result.reason
                if (!reason.isNullOrEmpty()) {
                    showReasonDialog(reason)
                }
            } catch (e: Exception) {
                loadingView.stop()
                dialog.dismiss()
                ToastUtil.show(requireContext(), e.message ?: "AI 预测失败，已退回机选", "warn")
                randomPickFallback()
            }
        }
    }

    private fun randomPickFallback() {
        val cfg = Match.playConfig(type)
        val reds = (1..cfg.redCount).map { String.format("%02d", it) }.shuffled().take(cfg.minRed).sorted()
        val blues = (1..cfg.blueCount).map { String.format("%02d", it) }.shuffled().take(cfg.minBlue).sorted()
        binding.ballPicker.setPicked(reds, blues)
    }

    private fun showReasonDialog(reason: String) {
        val scroll = android.widget.ScrollView(requireContext()).apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val text = TextView(requireContext()).apply {
            text = reason
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
            setLineSpacing(0f, 1.4f)
        }
        scroll.addView(text)

        AlertDialog.Builder(requireContext())
            .setTitle("AI 分析理由")
            .setView(scroll)
            .setPositiveButton("知道了", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 简单 TextWatcher */
    private class SimpleWatcher(val onText: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) { onText(s?.toString() ?: "") }
    }
}
