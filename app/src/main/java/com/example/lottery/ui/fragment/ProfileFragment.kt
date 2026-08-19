package com.example.lottery.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lottery.LotteryApp
import com.example.lottery.R
import com.example.lottery.data.Api
import com.example.lottery.data.model.User
import com.example.lottery.databinding.FragmentProfileBinding
import com.example.lottery.ui.widget.ToastUtil
import com.example.lottery.update.UpdateManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var editing = false
    private var saving = false
    private var savingPwd = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnEdit.setOnClickListener { startEdit() }
        binding.btnCancelEdit.setOnClickListener { exitEdit() }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnLogout.setOnClickListener { logout() }
        binding.btnCheckUpdate.setOnClickListener { checkUpdate() }
        binding.btnChangePwd.setOnClickListener { openPwdPanel() }
        binding.btnCancelPwd.setOnClickListener { closePwdPanel() }
        binding.btnSavePwd.setOnClickListener { savePassword() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun currentUser(): User? = LotteryApp.instance.authStore.getUser()

    private fun refresh() {
        val u = currentUser() ?: return
        val initial = (u.username.ifEmpty { "?" }).take(1).uppercase()
        binding.avatar.text = initial
        binding.profileName.text = u.username.ifEmpty { "—" }
        val dev = if (u.device_id.length >= 10) u.device_id.take(10) + "…" else u.device_id.ifEmpty { "—" }
        binding.profileSub.text = "设备号 $dev"
        binding.fieldUsername.text = u.username.ifEmpty { "—" }
        binding.fieldEmail.text = u.email.ifEmpty { "未填写" }
        binding.fieldReg.text = if (u.created_at.length >= 10) u.created_at.take(10) else u.created_at.ifEmpty { "—" }
    }

    private fun startEdit() {
        val u = currentUser() ?: return
        editing = true
        binding.inputUsername.setText(u.username)
        binding.inputEmail.setText(u.email)
        binding.fieldUsername.visibility = View.GONE
        binding.fieldEmail.visibility = View.GONE
        binding.inputUsername.visibility = View.VISIBLE
        binding.inputEmail.visibility = View.VISIBLE
        binding.btnEdit.visibility = View.GONE
        binding.btnChangePwd.visibility = View.GONE
        binding.btnCancelEdit.visibility = View.VISIBLE
        binding.btnSave.visibility = View.VISIBLE
    }

    private fun exitEdit() {
        editing = false
        binding.fieldUsername.visibility = View.VISIBLE
        binding.fieldEmail.visibility = View.VISIBLE
        binding.inputUsername.visibility = View.GONE
        binding.inputEmail.visibility = View.GONE
        binding.btnEdit.visibility = View.VISIBLE
        binding.btnChangePwd.visibility = View.VISIBLE
        binding.btnCancelEdit.visibility = View.GONE
        binding.btnSave.visibility = View.GONE
    }

    private fun saveProfile() {
        if (saving) return
        val username = binding.inputUsername.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        if (username.isEmpty() && email.isEmpty()) {
            ToastUtil.show(requireContext(), "没有修改", "warn")
            return
        }
        saving = true
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                val payload = mutableMapOf<String, String>()
                if (email.isNotEmpty()) payload["email"] = email
                if (username.isNotEmpty()) payload["username"] = username
                val r = Api.updateMe(payload)
                if (r.ok != false && r.user != null) {
                    LotteryApp.instance.authStore.updateUser(r.user)
                    ToastUtil.show(requireContext(), "资料已更新", "success")
                    exitEdit()
                    refresh()
                } else {
                    ToastUtil.show(requireContext(), r.msg ?: "更新失败", "error")
                }
            } catch (e: Exception) {
                ToastUtil.show(requireContext(), e.message ?: "更新失败", "error")
            } finally {
                saving = false
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun openPwdPanel() {
        binding.pwdPanel.visibility = View.VISIBLE
        binding.btnChangePwd.visibility = View.GONE
        binding.inputOldPwd.text?.clear()
        binding.inputNewPwd.text?.clear()
        binding.inputConfirmPwd.text?.clear()
    }

    private fun closePwdPanel() {
        binding.pwdPanel.visibility = View.GONE
        binding.btnChangePwd.visibility = View.VISIBLE
        binding.inputOldPwd.text?.clear()
        binding.inputNewPwd.text?.clear()
        binding.inputConfirmPwd.text?.clear()
    }

    private fun savePassword() {
        if (savingPwd) return
        val old = binding.inputOldPwd.text.toString().trim()
        val newP = binding.inputNewPwd.text.toString().trim()
        val confirm = binding.inputConfirmPwd.text.toString().trim()
        if (newP.isEmpty()) {
            ToastUtil.show(requireContext(), "请输入新密码", "warn")
            return
        }
        if (newP.length < 6) {
            ToastUtil.show(requireContext(), "密码至少6位", "warn")
            return
        }
        if (newP != confirm) {
            ToastUtil.show(requireContext(), "两次输入的新密码不一致", "warn")
            return
        }
        savingPwd = true
        binding.btnSavePwd.isEnabled = false
        lifecycleScope.launch {
            try {
                val payload = mutableMapOf<String, String>("password" to newP)
                if (old.isNotEmpty()) payload["old_password"] = old
                val r = Api.updateMe(payload)
                if (r.ok != false && r.user != null) {
                    LotteryApp.instance.authStore.updateUser(r.user)
                    ToastUtil.show(requireContext(), "密码已修改，已双端同步", "success")
                    closePwdPanel()
                } else {
                    ToastUtil.show(requireContext(), r.msg ?: "修改失败", "error")
                }
            } catch (e: Exception) {
                ToastUtil.show(requireContext(), e.message ?: "修改失败", "error")
            } finally {
                savingPwd = false
                binding.btnSavePwd.isEnabled = true
            }
        }
    }

    private fun loadStats() {
        binding.statTotal.text = "—"
        binding.statWins.text = "—"
        binding.statPending.text = "—"
        lifecycleScope.launch {
            try {
                val l = Api.listLottery(emptyMap<String, String>())
                binding.statTotal.text = l.size.toString()
                binding.statWins.text = l.count { it.status == "已中奖" }.toString()
                binding.statPending.text = l.count { it.status == "未开奖" }.toString()
            } catch (e: Exception) {
                // 静默失败，保留占位
            }
        }
    }

    private fun checkUpdate() {
        val act = requireActivity()
        if (act is AppCompatActivity) {
            UpdateManager(act).checkForUpdate(manual = true)
        }
    }

    private fun logout() {
        LotteryApp.instance.authStore.clear()
        ToastUtil.show(requireContext(), "已退出登录", "info")
        val intent = Intent(requireContext(), com.example.lottery.LoginActivity::class.java)
        startActivity(intent)
        requireActivity().finish()
    }
}
