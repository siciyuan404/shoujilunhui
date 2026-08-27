package com.shoujilunhui.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 基于 GitHub tag 的版本更新检查。
 * 通过 GitHub Releases API 读取最新 tag，与本地版本号比较，有新版本时弹窗提示。
 */
object Updater {

    private const val TAG = "Updater"
    private const val REPO = "siciyuan404/shoujilunhui"
    private const val API_BASE = "https://api.github.com/"
    private const val RELEASE_URL = "https://github.com/$REPO/releases"

    /** 解析 v1.2.0 -> [1,2,0] */
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

    /** 网络层：请求 GitHub Releases API（独立 OkHttp 请求，不依赖业务服务） */
    private suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE" + "repos/$REPO/releases/latest"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "phone-recycle-android")
                .header("Accept", "application/vnd.github+json")
                .build()
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    val gson = com.google.gson.Gson()
                    gson.fromJson(body, GitHubRelease::class.java)
                }
        } catch (e: Exception) {
            Log.w(TAG, "fetch release failed: ${e.message}")
            null
        }
    }

    /**
     * 检查更新。若本地已有"忽略此版本"则不打扰。
     * 有新版本时弹 Material 对话框，可一键跳转 GitHub Release 下载。
     */
    fun check(activity: AppCompatActivity, currentVersionName: String) {
        val prefs = activity.getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        val ignored = prefs.getString("ignoreVersion", "") ?: ""
        activity.lifecycleScope.launch {
            val rel = fetchLatestRelease() ?: return@launch
            val latestTag = rel.tagName ?: return@launch
            if (latestTag == ignored) return@launch
            if (!isNewer(latestTag, currentVersionName)) return@launch

            val apkAsset = rel.assets?.firstOrNull {
                it.name?.endsWith(".apk", ignoreCase = true) == true
            }
            val url = apkAsset?.browserDownloadUrl ?: rel.htmlUrl ?: RELEASE_URL

            MaterialAlertDialogBuilder(activity)
                .setTitle("发现新版本 v$latestTag")
                .setMessage("当前版本 v$currentVersionName\n是否前往下载更新？")
                .setPositiveButton("立即更新") { _, _ -> openBrowser(activity, url) }
                .setNegativeButton("忽略此版本") { _, _ ->
                    prefs.edit().putString("ignoreVersion", latestTag).apply()
                }
                .setNeutralButton("暂不", null)
                .show()
        }
    }

    private fun openBrowser(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no browser: ${e.message}")
        }
    }
}
