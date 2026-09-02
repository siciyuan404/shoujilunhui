package com.shoujilunhui.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.shoujilunhui.app.recognize.PhoneBox
import java.io.File

/** 历史识别记录中的单台机器 */
data class HistoryItem(
    val seq: Int,
    val imageIndex: Int,
    val model: String,
    val recognized: String,
    val matched: Boolean,
    val brand: String,
    val category: String,
    val channelPrice: Double?,
    val customerPrice: Double?,
    val box: PhoneBox?,
)

/** 一次历史识别记录（header + 明细） */
data class HistoryEntry(
    val id: Long,
    val createdAt: Long,
    val imageCount: Int,
    val phoneCount: Int,
    val matchedCount: Int,
    val channelTotal: Double,
    val customerTotal: Double,
    val ratio: Int,
    val dir: String,
    val items: List<HistoryItem> = emptyList(),
)

/**
 * 识别历史存储：SQLite 存元数据与明细，图片复制到 filesDir/history/<id>/img_<n>.jpg 持久保存，
 * 后续可随时回看图片与识别结果（不依赖原始相册/相机 URI）。
 */
class HistoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE recognize_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "created_at INTEGER NOT NULL," +
                "image_count INTEGER NOT NULL," +
                "phone_count INTEGER NOT NULL," +
                "matched_count INTEGER NOT NULL," +
                "channel_total REAL NOT NULL," +
                "customer_total REAL NOT NULL," +
                "ratio INTEGER NOT NULL," +
                "dir TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE recognize_history_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "history_id INTEGER NOT NULL," +
                "seq INTEGER NOT NULL," +
                "image_index INTEGER NOT NULL," +
                "model TEXT NOT NULL," +
                "recognized TEXT NOT NULL," +
                "matched INTEGER NOT NULL," +
                "brand TEXT," +
                "category TEXT," +
                "channel_price REAL," +
                "customer_price REAL," +
                "box_x1 REAL," +
                "box_y1 REAL," +
                "box_x2 REAL," +
                "box_y2 REAL)"
        )
        db.execSQL("CREATE INDEX idx_hist_items ON recognize_history_items(history_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /**
     * 保存一次识别记录。
     * @param uris 待识别图片（index = imageIndex，用于复制到私有目录）
     */
    fun save(
        context: Context,
        uris: List<Uri>,
        items: List<HistoryItem>,
        phoneCount: Int,
        matchedCount: Int,
        channelTotal: Double,
        customerTotal: Double,
        ratio: Int,
    ): Long {
        val db = writableDatabase
        val id = db.insert("recognize_history", null, ContentValues().apply {
            put("created_at", System.currentTimeMillis())
            put("image_count", uris.size)
            put("phone_count", phoneCount)
            put("matched_count", matchedCount)
            put("channel_total", channelTotal)
            put("customer_total", customerTotal)
            put("ratio", ratio)
            put("dir", "")
        })
        val dir = File(context.filesDir, "history/$id").apply { mkdirs() }
        uris.forEachIndexed { idx, uri ->
            copyUri(context, uri, File(dir, "img_$idx.jpg"))
        }
        db.update("recognize_history", ContentValues().apply { put("dir", dir.absolutePath) }, "id=?", arrayOf(id.toString()))
        items.forEach { it ->
            db.insert("recognize_history_items", null, ContentValues().apply {
                put("history_id", id)
                put("seq", it.seq)
                put("image_index", it.imageIndex)
                put("model", it.model)
                put("recognized", it.recognized)
                put("matched", if (it.matched) 1 else 0)
                put("brand", it.brand)
                put("category", it.category)
                it.channelPrice?.let { v -> put("channel_price", v) }
                it.customerPrice?.let { v -> put("customer_price", v) }
                it.box?.let { b ->
                    put("box_x1", b.x1.toDouble()); put("box_y1", b.y1.toDouble())
                    put("box_x2", b.x2.toDouble()); put("box_y2", b.y2.toDouble())
                }
            })
        }
        return id
    }

    /** 历史列表（不含明细），按时间倒序 */
    fun all(): List<HistoryEntry> {
        val list = mutableListOf<HistoryEntry>()
        readableDatabase.query("recognize_history", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                list += HistoryEntry(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    imageCount = c.getInt(c.getColumnIndexOrThrow("image_count")),
                    phoneCount = c.getInt(c.getColumnIndexOrThrow("phone_count")),
                    matchedCount = c.getInt(c.getColumnIndexOrThrow("matched_count")),
                    channelTotal = c.getDouble(c.getColumnIndexOrThrow("channel_total")),
                    customerTotal = c.getDouble(c.getColumnIndexOrThrow("customer_total")),
                    ratio = c.getInt(c.getColumnIndexOrThrow("ratio")),
                    dir = c.getString(c.getColumnIndexOrThrow("dir")) ?: "",
                )
            }
        }
        return list
    }

    /** 某条记录的明细 */
    fun items(historyId: Long): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        readableDatabase.query(
            "recognize_history_items", null, "history_id=?", arrayOf(historyId.toString()),
            null, null, "seq ASC"
        ).use { c ->
            while (c.moveToNext()) {
                val x1 = c.getDoubleOrNull("box_x1")
                val y1 = c.getDoubleOrNull("box_y1")
                val x2 = c.getDoubleOrNull("box_x2")
                val y2 = c.getDoubleOrNull("box_y2")
                val box = if (x1 != null && y1 != null && x2 != null && y2 != null)
                    PhoneBox(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
                else null
                list += HistoryItem(
                    seq = c.getInt(c.getColumnIndexOrThrow("seq")),
                    imageIndex = c.getInt(c.getColumnIndexOrThrow("image_index")),
                    model = c.getString(c.getColumnIndexOrThrow("model")),
                    recognized = c.getString(c.getColumnIndexOrThrow("recognized")),
                    matched = c.getInt(c.getColumnIndexOrThrow("matched")) == 1,
                    brand = c.getStringOrNull("brand") ?: "",
                    category = c.getStringOrNull("category") ?: "",
                    channelPrice = c.getDoubleOrNull("channel_price"),
                    customerPrice = c.getDoubleOrNull("customer_price"),
                    box = box,
                )
            }
        }
        return list
    }

    /** 完整记录（含明细） */
    fun get(id: Long): HistoryEntry? {
        readableDatabase.query("recognize_history", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) {
                return HistoryEntry(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    imageCount = c.getInt(c.getColumnIndexOrThrow("image_count")),
                    phoneCount = c.getInt(c.getColumnIndexOrThrow("phone_count")),
                    matchedCount = c.getInt(c.getColumnIndexOrThrow("matched_count")),
                    channelTotal = c.getDouble(c.getColumnIndexOrThrow("channel_total")),
                    customerTotal = c.getDouble(c.getColumnIndexOrThrow("customer_total")),
                    ratio = c.getInt(c.getColumnIndexOrThrow("ratio")),
                    dir = c.getString(c.getColumnIndexOrThrow("dir")) ?: "",
                    items = items(id),
                )
            }
        }
        return null
    }

    /** 删除记录（含图片目录） */
    fun delete(id: Long) {
        val dir = get(id)?.dir
        writableDatabase.delete("recognize_history_items", "history_id=?", arrayOf(id.toString()))
        writableDatabase.delete("recognize_history", "id=?", arrayOf(id.toString()))
        if (!dir.isNullOrBlank()) File(dir).deleteRecursively()
    }

    private fun copyUri(context: Context, uri: Uri, dest: File) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        } catch (_: Exception) {}
    }

    private fun android.database.Cursor.getDoubleOrNull(col: String): Double? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getDouble(idx) else null
    }

    private fun android.database.Cursor.getStringOrNull(col: String): String? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }
}
