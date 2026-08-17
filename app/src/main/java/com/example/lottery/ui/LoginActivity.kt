package com.example.lottery.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lottery.LotteryApp
import com.example.lottery.R
import com.example.lottery.api.ApiClient
import com.example.lottery.databinding.ActivityLoginBinding
import com.example.lottery.model.DeviceLoginReq
import com.example.lottery.model.LoginReq
import com.example.lottery.model.RegisterReq
import com.example.lottery.utils.ToastHelper
import com.example.lottery.utils.ToastHelper.Type
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var mode = "login" // login | register | device

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (LotteryApp.getToken().isNotEmpty()) {
            goMain()
            return
        }

        // Tabs
        binding.tabLogin.setOnClickListener { switchMode("login") }
        binding.tabRegister.setOnClickListener { switchMode("register") }
        binding.tabDevice.setOnClickListener { switchMode("device") }
        switchMode("login")

        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun switchMode(m: String) {
        mode = m
        // 高亮当前 Tab
        val activeColor = 0xFFFFFFFF.toInt()
        val activeFg = 0xFF1D1C1D.toInt()
        val mutedFg = 0xFF616061.toInt()
        val bg = R.drawable.bg_seg_on

        fun TextView.setActive(active: Boolean) {
            setBackgroundResource(if (active) bg else 0)
            setTextColor(if (active) activeFg else mutedFg)
        }
        binding.tabLogin.setActive(m == "login")
        binding.tabRegister.setActive(m == "register")
        binding.tabDevice.setActive(m == "device")

        // 字段显隐
        // 用户名：login/register
        binding.labelUsername.visibility = if (m != "device") View.VISIBLE else View.GONE
        binding.etUsername.visibility = binding.labelUsername.visibility
        // 密码：login/register
        binding.labelPassword.visibility = if (m == "login" || m == "register") View.VISIBLE else View.GONE
        binding.etPassword.visibility = binding.labelPassword.visibility
        // 邮箱：register/device
        binding.labelEmail.visibility = if (m == "register" || m == "device") View.VISIBLE else View.GONE
        binding.etEmail.visibility = binding.labelEmail.visibility

        // 按钮文案
        binding.btnSubmit.text = when (m) {
            "register" -> getString(R.string.login_action_register)
            else -> getString(R.string.login_action_login)
        }
    }

    private fun submit() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if ((mode == "login" || mode == "register") && (username.isEmpty() || password.isEmpty())) {
            ToastHelper.show(this, "请输入用户名和密码", Type.WARN)
            return
        }

        binding.loading.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        lifecycleScope.launch {
            try {
                val resp = when (mode) {
                    "login" -> ApiClient.service.login(LoginReq(username, password))
                    "register" -> ApiClient.service.register(
                        RegisterReq(username, password, email, LotteryApp.deviceId)
                    )
                    else -> ApiClient.service.deviceLogin(DeviceLoginReq(LotteryApp.deviceId, email))
                }
                if (resp.ok && resp.user != null) {
                    LotteryApp.setToken(resp.token)
                    LotteryApp.setUser(resp.user)
                    ToastHelper.show(this@LoginActivity, "登录成功", Type.SUCCESS)
                    goMain()
                } else {
                    ToastHelper.show(this@LoginActivity, resp.msg ?: "操作失败", Type.ERROR)
                }
            } catch (e: Exception) {
                ToastHelper.show(this@LoginActivity, "错误：${e.message}", Type.ERROR)
            } finally {
                binding.loading.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
