package com.example.lottery.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lottery.api.ApiClient
import com.example.lottery.databinding.ActivityAddBinding
import com.example.lottery.model.LotteryReq
import kotlinx.coroutines.launch

class AddLotteryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ArrayAdapter.createFromResource(
            this, R.array.lottery_types, android.R.layout.simple_spinner_item
        ).also { a ->
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerType.adapter = a
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val type = if (binding.spinnerType.selectedItemPosition == 0) "ssq" else "dlt"
        val issue = binding.etIssue.text.toString().trim()
        val red = binding.etRed.text.toString().trim()
        val blue = binding.etBlue.text.toString().trim()
        if (issue.isEmpty() || red.isEmpty() || blue.isEmpty()) {
            Toast.makeText(this, "请填完整", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                ApiClient.service.addLottery(LotteryReq(type, issue, red, blue))
                Toast.makeText(this@AddLotteryActivity, "已保存", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddLotteryActivity, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
