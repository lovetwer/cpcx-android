package com.example.lottery.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lottery.R
import com.example.lottery.data.Api
import com.example.lottery.data.model.DrawResult
import com.example.lottery.databinding.FragmentDrawBinding
import com.example.lottery.databinding.DrawRowBinding
import com.example.lottery.ui.widget.BallsView
import com.example.lottery.ui.widget.ToastUtil
import kotlinx.coroutines.launch

class DrawFragment : Fragment() {

    private var _binding: FragmentDrawBinding? = null
    private val binding get() = _binding!!
    private var type = "ssq"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDrawBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tabSsq.setOnClickListener { switchType("ssq") }
        binding.tabDlt.setOnClickListener { switchType("dlt") }
        updateTabs()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun switchType(t: String) {
        if (t == type) return
        type = t
        updateTabs()
        load()
    }

    private fun updateTabs() {
        binding.tabSsq.setBackgroundResource(if (type == "ssq") R.drawable.bg_segment_item else 0)
        binding.tabSsq.setTextColor(
            ContextCompat.getColor(requireContext(), if (type == "ssq") R.color.primary else R.color.muted)
        )
        binding.tabDlt.setBackgroundResource(if (type == "dlt") R.drawable.bg_segment_item else 0)
        binding.tabDlt.setTextColor(
            ContextCompat.getColor(requireContext(), if (type == "dlt") R.color.primary else R.color.muted)
        )
    }

    private fun load() {
        binding.stateBox.visibility = View.VISIBLE
        binding.spinner.visibility = View.VISIBLE
        binding.stateText.text = getString(R.string.loading)
        binding.list.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val list = Api.listDraw(type).toMutableList()
                list.sortByDescending { it.issue }
                if (list.isEmpty()) {
                    binding.spinner.visibility = View.GONE
                    binding.stateText.text = getString(R.string.empty_draw)
                    binding.list.visibility = View.GONE
                } else {
                    binding.list.removeAllViews()
                    for (d in list) binding.list.addView(bindRow(d))
                    binding.stateBox.visibility = View.GONE
                    binding.list.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.spinner.visibility = View.GONE
                binding.stateText.text = e.message ?: "加载失败"
                ToastUtil.show(requireContext(), e.message ?: "加载失败", "error")
            }
        }
    }

    private fun bindRow(d: DrawResult): View {
        val b = DrawRowBinding.inflate(layoutInflater, binding.list, false)
        val isDlt = d.type == "dlt"
        b.chip.text = if (isDlt) "大乐透" else "双色球"
        b.chip.setBackgroundColor(if (isDlt) 0x1Aff4d4d.toInt() else 0x1Ae23b4e.toInt())
        b.chip.setTextColor(if (isDlt) 0xffff4d4d.toInt() else 0xffe23b4e.toInt())
        b.issue.text = "第 ${d.issue} 期"
        b.date.text = d.draw_date.ifEmpty { "—" }
        b.balls.setSize(24)
        b.balls.setBalls(d.type, d.red_balls, d.blue_balls)
        return b.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
