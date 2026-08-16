package com.example.lottery.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lottery.LotteryApp
import com.example.lottery.api.ApiClient
import com.example.lottery.databinding.ActivityLoginBinding
import com.example.lottery.model.DeviceLoginReq
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (LotteryApp.getToken().isNotEmpty()) {
            goMain()
            return
        }

        binding.btnDeviceLogin.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.service.deviceLogin(DeviceLoginReq(LotteryApp.deviceId))
                    if (resp.ok && resp.user != null) {
                        LotteryApp.setToken(resp.token)
                        Toast.makeText(this@LoginActivity, "欢迎，${resp.user.username}", Toast.LENGTH_SHORT).show()
                        goMain()
                    } else {
                        Toast.makeText(this@LoginActivity, "登录失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "错误：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
