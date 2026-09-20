package com.shoujilunhui.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 离线数据包：全量机型 JSON + 机型图片，下载到 filesDir/offline/，首页和详情完全本地读。
 *
 * 结构：
 *   filesDir/offline/models.json          全量机型数据
 *   filesDir/offline/images/u_xxx.jpg     机型图片（按文件名）
 *
 * 只读快照：服务器后台改价/加机型/补图后，在设置页重新下载即可。
 */
object OfflineStore {

    private const val DIR = "offline"
    private const val FILE = "models.json"
    private const val IMG_DIR = "images"
    private const val PREFS = "offline_meta"
    private const val KEY_COUNT = "count"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_IMG_COUNT = "imgCount"
    private const val KEY_UPDATED = "updatedAt"

    data class Info(
        val count: Int,
        val sizeBytes: Long,
        val imageCount: Int,
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

    /** 下载进度回调：phase=0 下载数据，phase=1 下载图片 */
    interface Progress {
        fun onProgress(phase: Int, current: Int, total: Int)
    }

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }
    private fun dataFile(ctx: Context): File = File(dir(ctx), FILE)
    private fun imagesDir(ctx: Context): File = File(dir(ctx), IMG_DIR).apply { mkdirs() }

    fun info(ctx: Context): Info? {
        val f = dataFile(ctx)
        if (!f.exists() || f.length() == 0L) return null
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = p.getInt(KEY_COUNT, 0)
        if (count <= 0) return null
        return Info(
            count = count,
            sizeBytes = f.length(),
            imageCount = p.getInt(KEY_IMG_COUNT, 0),
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
     * 把 images 字段里的 URL 转成本地文件路径（离线模式用）。
     * 返回 null 表示本地没有这张图。
     */
    fun localImageFile(ctx: Context, rawUrl: String): File? {
        val name = rawUrl.trim().trimStart('/').substringAfterLast('/')
        if (name.isBlank()) return null
        val f = File(imagesDir(ctx), name)
        return if (f.exists() && f.length() > 0) f else null
    }

    /**
     * 从 baseUrl 下载全量离线包：models.json + 所有机型图片。
     * 已存在的图片跳过（增量更新）。
     */
    suspend fun download(
        ctx: Context,
        baseUrl: String,
        progress: Progress? = null,
    ): Result<Info> = withContext(Dispatchers.IO) {
        runCatching {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            // 1) 下载 models.json
            progress?.onProgress(0, 0, 1)
            val req = Request.Builder().url(base + "api/models?sort=release_desc").build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                resp.body?.bytes() ?: throw RuntimeException("响应为空")
            }
            val parsed = ApiClient.gson.fromJson(String(body), ModelsResponse::class.java)
                ?: throw RuntimeException("数据格式错误")
            if (parsed.items.isEmpty()) throw RuntimeException("服务器返回 0 款机型")

            // 原子写 models.json
            val target = dataFile(ctx)
            val tmp = File(dir(ctx), "$FILE.tmp")
            tmp.writeBytes(body)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) { target.writeBytes(body); tmp.delete() }

            // 2) 收集需要的图片文件名，下载缺失的
            val needed = LinkedHashSet<String>()
            for (m in parsed.items) {
                m.images?.forEach { u ->
                    val name = u.trim().trimStart('/').substringAfterLast('/')
                    if (name.isNotBlank()) needed.add(name)
                }
            }
            val imgDir = imagesDir(ctx)
            val toDownload = needed.filter { name ->
                val f = File(imgDir, name)
                !f.exists() || f.length() == 0L
            }
            progress?.onProgress(1, 0, toDownload.size)

            // 并发下载图片（最多 6 个并发）
            var done = 0
            val total = toDownload.size
            coroutineScope {
                toDownload.chunked(6).forEach { batch ->
                    batch.map { name ->
                        async(Dispatchers.IO) {
                            try {
                                val imgReq = Request.Builder().url(base + "uploads/" + name).build()
                                client.newCall(imgReq).execute().use { ir ->
                                    if (ir.isSuccessful) {
                                        val imgBytes = ir.body?.bytes()
                                        if (imgBytes != null && imgBytes.isNotEmpty()) {
                                            File(imgDir, name).writeBytes(imgBytes)
                                        }
                                    }
                                }
                            } catch (_: Exception) { /* 单张失败忽略 */ }
                        }
                    }.awaitAll()
                    done += batch.size
                    progress?.onProgress(1, done, total)
                }
            }

            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_COUNT, parsed.items.size)
                .putLong(KEY_SIZE, target.length())
                .putInt(KEY_IMG_COUNT, needed.size)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply()
            Info(parsed.items.size, target.length(), needed.size, System.currentTimeMillis())
        }
    }
}
