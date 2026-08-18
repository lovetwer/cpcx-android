package com.example.lottery

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.lottery.data.Api
import com.example.lottery.data.model.LoginResp
import com.example.lottery.databinding.ActivityLoginBinding
import com.example.lottery.ui.widget.ToastUtil
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var mode = "login" // login | register | device
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 已登录直接进主页（设备登录：下次打开自动登录）
        if (LotteryApp.instance.authStore.isAuthed()) {
            goMain()
            return
        }

        binding.tabLogin.setOnClickListener { setMode("login") }
        binding.tabRegister.setOnClickListener { setMode("register") }
        binding.tabDevice.setOnClickListener { setMode("device") }
        binding.btnSubmit.setOnClickListener { submit() }

        setMode("device")
    }

    private fun setMode(m: String) {
        mode = m
        // 字段显隐
        binding.fieldUser.visibility = if (m != "device") View.VISIBLE else View.GONE
        binding.fieldPass.visibility = if (m == "login" || m == "register") View.VISIBLE else View.GONE
        binding.fieldEmail.visibility = if (m == "register" || m == "device") View.VISIBLE else View.GONE
        // 提交文案
        binding.btnText.text = when (m) {
            "register" -> "注册并登录"
            "device" -> "一键登录"
            else -> "登录"
        }
        updateTabs()
    }

    private fun updateTabs() {
        val tabs = listOf(
            Pair(binding.tabLogin, "login"),
            Pair(binding.tabRegister, "register"),
            Pair(binding.tabDevice, "device")
        )
        for ((view, m) in tabs) {
            val active = m == mode
            view.setBackgroundResource(if (active) R.drawable.bg_segment_item else 0)
            view.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (active) R.color.primary else R.color.muted
                )
            )
        }
    }

    private fun submit() {
        if (loading) return
        val username = binding.inputUser.text.toString().trim()
        val password = binding.inputPass.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        val deviceId = LotteryApp.instance.authStore.getDeviceId()

        when (mode) {
            "login" -> if (username.isEmpty() || password.isEmpty()) {
                ToastUtil.show(this, "请输入用户名和密码", "warn"); return
            }
            "register" -> if (username.isEmpty() || password.isEmpty()) {
                ToastUtil.show(this, "请输入用户名和密码", "warn"); return
            }
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val r: LoginResp = when (mode) {
                    "register" -> Api.register(username, password, email, deviceId)
                    "device" -> Api.deviceLogin(deviceId, email)
                    else -> Api.login(username, password)
                }
                if (r.token != null && r.user != null) {
                    LotteryApp.instance.authStore.save(r.token, r.user)
                    ToastUtil.show(this@LoginActivity, "登录成功", "success")
                    goMain()
                } else {
                    ToastUtil.show(this@LoginActivity, r.msg ?: "操作失败", "error")
                }
            } catch (e: Exception) {
                ToastUtil.show(this@LoginActivity, e.message ?: "操作失败", "error")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(v: Boolean) {
        loading = v
        binding.btnSpinner.visibility = if (v) View.VISIBLE else View.GONE
        binding.btnText.visibility = if (v) View.GONE else View.VISIBLE
        binding.btnSubmit.isClickable = !v
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
