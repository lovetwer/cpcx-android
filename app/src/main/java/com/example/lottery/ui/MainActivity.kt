package com.example.lottery.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.lottery.R
import com.example.lottery.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTab = R.id.tabAdd
    private val colorMuted by lazy { 0xFF616061.toInt() }
    private val colorPrimary by lazy { 0xFFFF4D4D.toInt() }

    private val addFragment by lazy { AddFragment() }
    private val drawFragment by lazy { DrawFragment() }
    private val buyFragment by lazy { BuyFragment() }
    private val profileFragment by lazy { ProfileFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tabAdd.setOnClickListener { selectTab(R.id.tabAdd) }
        binding.tabDraw.setOnClickListener { selectTab(R.id.tabDraw) }
        binding.tabBuy.setOnClickListener { selectTab(R.id.tabBuy) }
        binding.tabProfile.setOnClickListener { selectTab(R.id.tabProfile) }

        selectTab(R.id.tabAdd)
    }

    private fun selectTab(id: Int) {
        currentTab = id
        val fragment: Fragment = when (id) {
            R.id.tabDraw -> drawFragment
            R.id.tabBuy -> buyFragment
            R.id.tabProfile -> profileFragment
            else -> addFragment
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHost, fragment)
            .commitAllowingStateLoss()

        // 高亮当前 Tab
        fun tint(view: LinearLayout, active: Boolean) {
            val color = if (active) colorPrimary else colorMuted
            val icon = view.getChildAt(0) as ImageView
            val text = view.getChildAt(1) as TextView
            icon.imageTintList = android.content.res.ColorStateList.valueOf(color)
            text.setTextColor(color)
        }
        tint(binding.tabAdd, id == R.id.tabAdd)
        tint(binding.tabDraw, id == R.id.tabDraw)
        tint(binding.tabBuy, id == R.id.tabBuy)
        tint(binding.tabProfile, id == R.id.tabProfile)
    }
}
