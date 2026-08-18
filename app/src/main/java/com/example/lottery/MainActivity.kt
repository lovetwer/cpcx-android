package com.example.lottery

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lottery.data.Api
import com.example.lottery.databinding.ActivityMainBinding
import com.example.lottery.ui.fragment.AddFragment
import com.example.lottery.ui.fragment.BuyFragment
import com.example.lottery.ui.fragment.DrawFragment
import com.example.lottery.ui.fragment.ProfileFragment
import com.example.lottery.update.UpdateManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var addFrag: AddFragment
    private lateinit var drawFrag: DrawFragment
    private lateinit var buyFrag: BuyFragment
    private lateinit var profileFrag: ProfileFragment
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!LotteryApp.instance.authStore.isAuthed()) {
            goLogin()
            return
        }
        // 401 未授权：清登录态并跳回登录页
        Api.unauthorizedHandler = { runOnUiThread { goLogin() } }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addFrag = AddFragment()
        drawFrag = DrawFragment()
        buyFrag = BuyFragment()
        profileFrag = ProfileFragment()

        supportFragmentManager.beginTransaction()
            .add(R.id.container, addFrag, "add")
            .add(R.id.container, drawFrag, "draw").hide(drawFrag)
            .add(R.id.container, buyFrag, "buy").hide(buyFrag)
            .add(R.id.container, profileFrag, "profile").hide(profileFrag)
            .commitNow()

        binding.navAdd.setOnClickListener { setTab(0) }
        binding.navDraw.setOnClickListener { setTab(1) }
        binding.navBuy.setOnClickListener { setTab(2) }
        binding.navProfile.setOnClickListener { setTab(3) }

        setTab(0)

        // 启动后静默检查版本更新（有新版本才弹窗，无更新不提示）
        UpdateManager(this).checkForUpdate(manual = false)
    }

    fun setTab(index: Int) {
        current = index
        val frags = listOf(addFrag, drawFrag, buyFrag, profileFrag)
        supportFragmentManager.beginTransaction().apply {
            frags.forEachIndexed { i, f ->
                if (i == index) show(f) else hide(f)
            }
        }.commitNow()
        updateNav(index)
    }

    private fun updateNav(index: Int) {
        val items = listOf(
            Triple(binding.iconAdd, binding.labelAdd, binding.navAdd),
            Triple(binding.iconDraw, binding.labelDraw, binding.navDraw),
            Triple(binding.iconBuy, binding.labelBuy, binding.navBuy),
            Triple(binding.iconProfile, binding.labelProfile, binding.navProfile)
        )
        items.forEachIndexed { i, (icon, label, _) ->
            val active = i == index
            val color = ContextCompat.getColor(this, if (active) R.color.primary else R.color.muted)
            icon.setColorFilter(color)
            label.setTextColor(color)
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
