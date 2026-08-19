package com.example.lottery.update

import android.app.AlertDialog
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.lottery.LotteryApp
import com.example.lottery.R
import com.example.lottery.ui.widget.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 应用内版本更新：
 * 1. 拉取 GitHub Releases 最新发布（api.github.com/repos/owner/repo/releases/latest）
 * 2. 解析 tag_name（版本号）
 * 3. 与当前版本比较，有新版本则弹窗
 * 4. 确认后通过 jsDelivr CDN 下载 APK（国内加速），用 FileProvider 调起系统安装
 *
 * 注意：GitHub API 必须带 User-Agent，否则返回 403。
 */
class UpdateManager(private val activity: AppCompatActivity) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var cancelDownload = false
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    /** 当前安装版本号（从 PackageManager 读取，避免依赖 BuildConfig 生成类）。 */
    private fun currentVersionName(): String {
        return try {
            val pm = activity.packageManager
            val info = pm.getPackageInfo(activity.packageName, 0)
            info.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /** 静默检查（启动后）或手动检查（我的页按钮）。manual=true 时无论有无更新都给提示。 */
    fun checkForUpdate(manual: Boolean) {
        activity.lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
                if (release == null) {
                    if (manual) ToastUtil.show(activity, "检查更新失败，请稍后重试", "error")
                    return@launch
                }
                val current = currentVersionName()
                if (!isNewer(release.tag, current)) {
                    if (manual) ToastUtil.show(activity, "已是最新版本 v$current", "success")
                    return@launch
                }
                showUpdateDialog(release)
            } catch (e: Exception) {
                if (manual) {
                    val msg = if (e is SocketTimeoutException) "检查更新超时" else "检查更新失败"
                    ToastUtil.show(activity, msg, "error")
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val url =
            "https://api.github.com/repos/${LotteryApp.GITHUB_OWNER}/${LotteryApp.GITHUB_REPO}/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "cpcx-android")
            .header("Accept", "application/vnd.github+json")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val bodyStr = resp.body?.string() ?: return null
        val json = JSONObject(bodyStr)
        val tag = json.optString("tag_name", "")
        val notes = json.optString("body", "")
        val htmlUrl = json.optString("html_url", "")
        if (tag.isBlank()) return null
        // 下载 APK 走 jsDelivr CDN 加速（APK 放在 release 分支根目录）
        // jsDelivr 国内有 CDN 节点，比直连 GitHub 快且稳定
        val apkUrl = "https://cdn.jsdelivr.net/gh/${LotteryApp.GITHUB_OWNER}/${LotteryApp.GITHUB_REPO}@release/cpcx.apk"
        return ReleaseInfo(tag, notes, apkUrl, htmlUrl)
    }

    private fun showUpdateDialog(r: ReleaseInfo) {
        val notes = if (r.notes.isBlank()) "修复若干已知问题，提升使用体验。" else r.notes
        val displayTag = if (r.tag.startsWith("v", ignoreCase = true)) r.tag else "v${r.tag}"
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 $displayTag")
            .setMessage(notes)
            .setPositiveButton("立即更新") { _, _ -> downloadAndInstall(r.apkUrl) }
            .setNegativeButton("稍后再说") { _, _ -> }
            .setCancelable(true)
            .show()
    }

    private fun downloadAndInstall(apkUrl: String) {
        cancelDownload = false
        val dialog = buildProgressDialog()
        dialog.show()
        activity.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    downloadApk(apkUrl) { pct ->
                        activity.runOnUiThread {
                            progressBar?.progress = pct
                            progressText?.text = "$pct%"
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    if (cancelDownload) {
                        ToastUtil.show(activity, "已取消下载", "info")
                    } else {
                        val msg = if (e is SocketTimeoutException) "下载超时" else "下载失败"
                        ToastUtil.show(activity, msg, "error")
                    }
                }
            }
        }
    }

    private fun downloadApk(url: String, onProgress: (Int) -> Unit): File {
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("无法访问下载目录")
        dir.mkdirs()
        val file = File(dir, "update.apk")
        if (file.exists()) file.delete()
        val req = Request.Builder().url(url).header("User-Agent", "cpcx-android").build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        val body = resp.body ?: throw IOException("空响应")
        val total = body.contentLength()
        val stream = body.byteStream()
        FileOutputStream(file).use { out ->
            val buf = ByteArray(8192)
            var read: Int
            var written = 0L
            while (stream.read(buf).also { read = it } != -1) {
                if (cancelDownload) throw IOException("已取消")
                out.write(buf, 0, read)
                written += read
                if (total > 0) onProgress((written * 100 / total).toInt())
            }
            out.flush()
        }
        if (file.length() == 0L) throw IOException("下载文件为空")
        return file
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            activity.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        // 部分系统在未授予"安装未知应用"权限时会跳转设置页，这里做兜底提示
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(activity, "无法调起安装，请到系统设置开启安装权限", "error")
            // 兜底：打开应用安装设置页
            try {
                val settingIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                activity.startActivity(settingIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildProgressDialog(): AlertDialog {
        val ctx = activity
        progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        progressText = TextView(ctx).apply {
            text = "0%"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.muted))
            setPadding(0, 12, 0, 0)
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 12)
            addView(progressBar)
            addView(progressText)
        }
        return AlertDialog.Builder(ctx)
            .setTitle("正在下载更新")
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ -> cancelDownload = true }
            .create()
    }

    /** 解析 "1.2.3" / "v1.2.3" / "1.2" 为数字列表。 */
    private fun parseVersion(v: String): List<Int> {
        return v.trim()
            .removePrefix("v").removePrefix("V")
            .split('.', '-')
            .mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
    }

    /** latest 是否比 current 新。 */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private data class ReleaseInfo(
        val tag: String,
        val notes: String,
        val apkUrl: String,
        val htmlUrl: String
    )
}
