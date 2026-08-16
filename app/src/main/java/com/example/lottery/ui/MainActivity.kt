package com.example.lottery.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lottery.LotteryApp
import com.example.lottery.api.ApiClient
import com.example.lottery.databinding.ActivityMainBinding
import com.example.lottery.model.Lottery
import com.example.lottery.model.StatusReq
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: LotteryAdapter

    private val addLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == Activity.RESULT_OK) loadList() }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { doOcr(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = LotteryAdapter(
            onDelete = { delete(it) },
            onRedeem = { redeem(it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            addLauncher.launch(Intent(this, AddLotteryActivity::class.java))
        }
        binding.btnOcr.setOnClickListener { pickImage.launch("image/*") }
        binding.btnRefresh.setOnClickListener { loadList() }
        binding.btnLogout.setOnClickListener {
            LotteryApp.clearToken()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        loadList()
    }

    private fun loadList() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.service.listLottery()
                adapter.submit(resp.list)
                binding.tvEmpty.text = if (resp.list.isEmpty()) "还没有彩票，点右下角 + 添加" else ""
            } catch (e: Exception) {
                binding.tvEmpty.text = "加载失败：${e.message}"
            }
        }
    }

    private fun delete(l: Lottery) {
        lifecycleScope.launch {
            try {
                ApiClient.service.deleteLottery(l.id)
                Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                loadList()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun redeem(l: Lottery) {
        lifecycleScope.launch {
            try {
                ApiClient.service.setStatus(l.id, StatusReq("已兑奖"))
                Toast.makeText(this@MainActivity, "已标记已兑奖", Toast.LENGTH_SHORT).show()
                loadList()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "操作失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doOcr(uri: Uri) {
        lifecycleScope.launch {
            try {
                val file = uriToFile(uri)
                val body = file.asRequestBody("image/*".toMediaType())
                val part = MultipartBody.Part.createFormData("image", file.name, body)
                val resp = ApiClient.service.ocr(part)
                Toast.makeText(this@MainActivity, "识别完成，成功 ${resp.inserted.size} 张", Toast.LENGTH_SHORT).show()
                loadList()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "识别失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val inp = contentResolver.openInputStream(uri)!!
        val f = File(cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { out -> inp.copyTo(out) }
        return f
    }
}
