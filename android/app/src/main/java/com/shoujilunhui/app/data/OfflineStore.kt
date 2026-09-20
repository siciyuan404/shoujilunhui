package com.shoujilunhui.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 离线数据包：把服务器全量机型列表（/api/models 不传 limit，一次性返回）
 * 下载到本地 filesDir/offline/models.json，首页直接本地读，不依赖网络。
 *
 * 这是只读快照：服务器后台改价/加机型后，需要在设置页重新下载一次才会更新。
 */
object OfflineStore {

    private const val DIR = "offline"
    private const val FILE = "models.json"
    private const val PREFS = "offline_meta"
    private const val KEY_COUNT = "count"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_UPDATED = "updatedAt"

    data class Info(
        val count: Int,
        val sizeBytes: Long,
        val updatedAt: Long,
    ) {
        val sizeText: String
            get() = when {
                sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / 1024.0 / 1024.0)
                sizeBytes >= 1024 -> "${sizeBytes / 1024} KB"
                else -> "$sizeBytes B"
            }

        val updatedText: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(updatedAt))
    }

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }
    private fun dataFile(ctx: Context): File = File(dir(ctx), FILE)

    fun info(ctx: Context): Info? {
        val f = dataFile(ctx)
        if (!f.exists() || f.length() == 0L) return null
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = p.getInt(KEY_COUNT, 0)
        if (count <= 0) return null
        return Info(
            count = count,
            sizeBytes = f.length(),
            updatedAt = p.getLong(KEY_UPDATED, f.lastModified()),
        )
    }

    fun hasOffline(ctx: Context): Boolean = info(ctx) != null

    /** 读取本地离线包中的全部机型；文件缺失/损坏返回 null */
    fun loadModels(ctx: Context): List<ModelRow>? {
        val f = dataFile(ctx)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val json = f.readText()
            val resp = ApiClient.gson.fromJson(json, ModelsResponse::class.java)
            resp?.items?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从 baseUrl 下载全量离线包。
     * 下载到临时文件 → 校验能解析 → 原子替换；失败不影响旧包。
     */
    suspend fun download(ctx: Context, baseUrl: String): Result<Info> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val url = base + "api/models?sort=release_desc"
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                    val body = resp.body?.bytes() ?: throw RuntimeException("响应为空")
                    // 先解析校验，避免把坏 JSON 写进去
                    val parsed = ApiClient.gson.fromJson(String(body), ModelsResponse::class.java)
                        ?: throw RuntimeException("数据格式错误")
                    if (parsed.items.isEmpty()) throw RuntimeException("服务器返回 0 款机型")

                    val target = dataFile(ctx)
                    val tmp = File(dir(ctx), "$FILE.tmp")
                    tmp.writeBytes(body)
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        target.writeBytes(body)
                        tmp.delete()
                    }
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putInt(KEY_COUNT, parsed.items.size)
                        .putLong(KEY_SIZE, target.length())
                        .putLong(KEY_UPDATED, System.currentTimeMillis())
                        .apply()
                    Info(parsed.items.size, target.length(), System.currentTimeMillis())
                }
            }
        }
}
