package com.shoujilunhui.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.shoujilunhui.app.data.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 无感更新 + 滚动更新（灰度）。
 *
 * 流程（全程不打断使用）：
 *   1. 启动后后台静默检查更新（优先 server /api/update，失败回退 GitHub Release）
 *   2. 按设备 ID 哈希做灰度分流（服务端 gray 控制放量比例）
 *   3. 命中灰度的设备后台静默下载 APK 到缓存
 *   4. 下载完成发系统通知，点击一键安装
 *
 * 说明：普通应用受系统限制无法完全静默安装 APK，因此"无感"体现在检查与下载
 * 全程后台静默，安装仅需点一下通知。
 */
object Updater {

    private const val TAG = "Updater"
    private const val REPO = "siciyuan404/shoujilunhui"
    private const val GITHUB_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHANNEL_ID = "update"
    private const val NOTIFY_ID = 1001

    /** server /api/update 返回的更新策略 */
    data class UpdateInfo(
        val enabled: Boolean = false,
        val latest: String = "",
        val url: String = "",
        val gray: Int = 100,
        val forceBelow: String = ""
    )

    /** 解析 v1.5.0 / 1.5 -> [1,5,0] */
    private fun parseVersion(v: String?): List<Int> =
        (v ?: "").trim().removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }

    private fun isNewer(latest: String?, current: String?): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 设备唯一 ID（持久化随机 UUID），用于灰度分流 */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        var id = prefs.getString("deviceId", "")
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", id).apply()
        }
        return id
    }

    /** 灰度判断：hash(deviceId) % 100 < gray 则命中放量 */
    private fun inGray(deviceId: String, gray: Int): Boolean {
        if (gray >= 100) return true
        if (gray <= 0) return false
        val h = deviceId.hashCode() and 0x7fffffff
        return h % 100 < gray
    }

    /** 主入口：后台静默检查更新并自动下载（不弹窗打断使用） */
    fun checkSilently(activity: ComponentActivity, currentVersion: String, baseUrl: String) {
        activity.lifecycleScope.launch {
            try {
                val info = fetchUpdateInfo(baseUrl) ?: return@launch
                if (!info.enabled || info.latest.isBlank() || info.url.isBlank()) return@launch
                if (!isNewer(info.latest, currentVersion)) return@launch
                // 强制更新：当前版本低于 forceBelow 则无视灰度直接更新
                val force = info.forceBelow.isNotBlank() && !isNewer(currentVersion, info.forceBelow)
                if (!force && !inGray(deviceId(activity), info.gray)) {
                    Log.d(TAG, "新版本 v${info.latest} 存在，但本设备未命中 ${info.gray}% 灰度，暂不更新")
                    return@launch
                }
                Log.d(TAG, "开始后台下载 v${info.latest}（force=$force, gray=${info.gray}%）")
                downloadAndNotify(activity, info)
            } catch (e: Exception) {
                Log.w(TAG, "静默检查失败: ${e.message}")
            }
        }
    }

    /** 优先 server /api/update（可控灰度）；server 不可用/未配置时回退 GitHub Release */
    private suspend fun fetchUpdateInfo(baseUrl: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val gson = Gson()
        // 1) 服务端更新策略接口
        if (baseUrl.isNotBlank()) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/api/update")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val info = gson.fromJson(resp.body?.string(), UpdateInfo::class.java)
                        if (info.enabled) return@withContext info
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "server /api/update 不可用: ${e.message}")
            }
        }
        // 2) 回退 GitHub Release（全量）
        try {
            val req = okhttp3.Request.Builder()
                .url(GITHUB_API)
                .header("User-Agent", "phone-recycle-android")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val rel = gson.fromJson(resp.body?.string(), GitHubRelease::class.java)
                    val tag = rel?.tagName ?: return@withContext null
                    val apk = rel?.assets?.firstOrNull { it.name?.endsWith(".apk", true) == true }
                    val url = apk?.browserDownloadUrl ?: return@withContext null
                    return@withContext UpdateInfo(enabled = true, latest = tag, url = url, gray = 100)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub 回退检查失败: ${e.message}")
        }
        null
    }

    /** 后台下载 APK 到缓存，完成后发通知栏"点击安装" */
    private suspend fun downloadAndNotify(activity: AppCompatActivity, info: UpdateInfo) {
        val context = activity.applicationContext
        val file = File(context.cacheDir, "update_${info.latest}.apk")
        val ok = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build()
                val req = okhttp3.Request.Builder().url(info.url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    file.outputStream().use { os -> body.byteStream().copyTo(os) }
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "后台下载失败: ${e.message}")
                false
            }
        }
        if (!ok) return
        ensureChannel(context)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("新版本 v${info.latest} 已就绪")
            .setContentText("已在后台下载完成，点击安装")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFY_ID, notif)
        } catch (e: Exception) {
            Log.w(TAG, "发送通知失败: ${e.message}")
        }
    }

    private fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "版本更新", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
