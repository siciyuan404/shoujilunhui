package com.shoujilunhui.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.button.MaterialButton
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 拍照/选图识别手机：调豆包视觉大模型识别图中所有手机型号，再逐个匹配回收报价。
 */
class RecognizeActivity : AppCompatActivity() {

    companion object {
        // 火山方舟当前推荐的多模态视觉模型（未下线）。旧模型 doubao-1.5-vision-pro-32k-250115 已下线/停用会导致 404。
        // 若账号未开通，请到火山方舟控制台「开通管理」开通后再填准确模型 ID。
        const val DEFAULT_ARK_MODEL = "doubao-seed-2-1-turbo-260628"
        private const val ARK_BASE = "https://ark.cn-beijing.volces.com/api/v3"
        private const val RECOGNIZE_PROMPT =
            "请仔细查看这张图片，识别出图中出现的所有手机。请只返回 JSON，格式：" +
                "{\"phones\":[{\"model\":\"手机具体型号\"}]}。" +
                "注意：1) 一台一台列出，图中出现几台就列几台；" +
                "2) 型号尽量简洁，如 畅享9 Plus、P40 Pro、苹果15 Pro Max（不要带品牌名，不要带内存/颜色/新旧等多余描述）；" +
                "3) 如果图中有手机但看不清型号，根据外观给出最可能的型号；" +
                "4) 如果图中没有手机，返回 {\"phones\":[]}。不要输出任何其他内容。"
    }

    // ---------- 型号智能匹配：把识别结果与报价库命名对齐 ----------
    // 报价库命名规则：华为/小米/OPPO 等不带品牌（"P40pro"）、荣耀带品牌（"荣耀9"）、苹果用"苹果"（"苹果13pro"）。
    // 识别模型常返回带品牌/空格/大小写的全名（"华为P40 Pro"、"iPhone 15 Pro"），需归一化后多级匹配。
    private val BRAND_PREFIXES = listOf(
        "华为", "荣耀", "小米", "红米", "苹果", "iphone", "oppo", "vivo", "一加", "真我",
        "realme", "三星", "魅族", "努比亚", "金立", "联想", "360", "美图", "锤子", "乐视",
        "谷歌", "google", "智选", "鼎桥", "华硕", "huawei", "honor", "xiaomi", "samsung",
        "meizu", "oneplus", "nubia", "vivo", "iqoo"
    )

    private val STRIP_WORDS = listOf(
        "256g", "512g", "128g", "64g", "32g", "16g", "1tb",
        "黑色", "白色", "蓝色", "金色", "灰色", "银色", "紫色", "绿色", "红色", "粉色",
        "二手", "国行", "港版", "美版", "全新", "拆封", "未拆封", "标配", "高配", "低配"
    )

    private fun normalizeModel(s: String): String {
        var t = s.trim().lowercase()
            .replace(Regex("[\\s_\\-·.．/（）()【】\\[\\]{}:：,，;；\"'`~！!?？*+&]"), "")
        for (w in STRIP_WORDS) t = t.replace(w, "")
        return t
    }

    private fun stripBrandPrefix(normalized: String): String {
        for (b in BRAND_PREFIXES) {
            if (normalized.startsWith(b)) return normalized.removePrefix(b)
        }
        return normalized
    }

    /** iPhone/苹果 → 苹果（报价库苹果用"苹果"命名） */
    private fun mapBrand(normalized: String): String = when {
        normalized.startsWith("iphone") -> "苹果" + normalized.removePrefix("iphone")
        normalized.startsWith("苹果") -> normalized
        else -> normalized
    }

    /** 核心系列词："畅享9plus"->畅享9，"p40pro"->p40，"苹果15promax"->苹果15 */
    private fun coreKeys(normalized: String): List<String> {
        val keys = mutableListOf<String>()
        Regex("([\\u4e00-\\u9fa5]+\\d+)").findAll(normalized).forEach { keys.add(it.value) }
        Regex("([a-z]+\\d+)").findAll(normalized).forEach { keys.add(it.value) }
        return keys.distinct().filter { it.isNotBlank() }
    }

    /** 生成有序匹配候选（越靠前越精确） */
    private fun buildCandidates(raw: String): List<String> {
        val norm = normalizeModel(raw)
        val list = linkedSetOf<String>()
        list.add(norm)                                 // 1. 原词归一化
        val mapped = mapBrand(norm)                    // 2. iPhone→苹果
        if (mapped != norm) list.add(mapped)
        val stripped = stripBrandPrefix(norm)          // 3. 去品牌前缀
        if (stripped.isNotBlank() && stripped !in list) list.add(stripped)
        val strippedMapped = mapBrand(stripped)        // 4. 去品牌后再映射
        if (strippedMapped.isNotBlank() && strippedMapped !in list) list.add(strippedMapped)
        coreKeys(norm).forEach { if (it !in list) list.add(it) }   // 5. 核心系列词兜底
        return list.toList()
    }

    data class RecognizeResult(val model: String, val row: ModelRow?)

    private var baseUrl = ""
    private var arkKey = ""
    private var arkModel = DEFAULT_ARK_MODEL
    private var currentUri: Uri? = null
    private var cameraUri: Uri? = null

    private val results = mutableListOf<RecognizeResult>()
    private lateinit var resultAdapter: ResultAdapter
    private lateinit var btnRecognize: MaterialButton

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = cameraUri
            if (ok && uri != null) {
                currentUri = uri
                findViewById<ImageView>(R.id.ivPreview).load(uri)
                findViewById<TextView>(R.id.tvStatus).text = ""
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                currentUri = uri
                findViewById<ImageView>(R.id.ivPreview).load(uri)
                findViewById<TextView>(R.id.tvStatus).text = ""
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognize)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        baseUrl = prefs.getString("baseUrl", "") ?: ""
        arkKey = prefs.getString("arkApiKey", "") ?: ""
        arkModel = prefs.getString("arkModel", DEFAULT_ARK_MODEL)
            ?.ifBlank { DEFAULT_ARK_MODEL } ?: DEFAULT_ARK_MODEL

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnTakePhoto).setOnClickListener { launchCamera() }
        findViewById<View>(R.id.btnPickImage).setOnClickListener { pickImage.launch("image/*") }

        btnRecognize = findViewById(R.id.btnRecognize)
        btnRecognize.setOnClickListener { startRecognize() }

        resultAdapter = ResultAdapter(results)
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvResults).apply {
            layoutManager = LinearLayoutManager(this@RecognizeActivity)
            adapter = resultAdapter
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            file.createNewFile()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            cameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开相机：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecognize() {
        if (baseUrl.isBlank()) {
            Toast.makeText(this, "请先在设置中填写服务器地址", Toast.LENGTH_LONG).show()
            return
        }
        if (arkKey.isBlank()) {
            Toast.makeText(this, "请先在设置中填写豆包 API Key", Toast.LENGTH_LONG).show()
            return
        }
        val uri = currentUri
        if (uri == null) {
            Toast.makeText(this, "请先拍照或选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        btnRecognize.isEnabled = false
        btnRecognize.text = "识别中..."
        val status = findViewById<TextView>(R.id.tvStatus)
        status.text = "正在识别图片中的手机..."
        results.clear()
        resultAdapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.tvResultTitle).visibility = View.GONE

        lifecycleScope.launch {
            try {
                val b64 = bitmapToBase64(uri)
                status.text = "识别中...（豆包视觉）"
                val models = recognizePhones(b64)
                if (models.isEmpty()) {
                    status.text = "未识别到手机，换一张更清晰的图试试"
                    return@launch
                }
                status.text = "已识别 ${models.size} 台，正在匹配报价..."
                models.forEach { m ->
                    results.add(RecognizeResult(m, queryPrice(m)))
                }
                resultAdapter.notifyDataSetChanged()
                findViewById<TextView>(R.id.tvResultTitle).visibility = View.VISIBLE
                val matched = results.count { it.row != null }
                status.text = "识别 ${results.size} 台，其中 $matched 台已匹配到报价"
            } catch (e: Exception) {
                status.text = "识别失败：${e.message}"
            } finally {
                btnRecognize.isEnabled = true
                btnRecognize.text = "开始识别"
            }
        }
    }

    private suspend fun bitmapToBase64(uri: Uri, maxDim: Int = 1280, quality: Int = 85): String =
        withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(uri) ?: throw Exception("无法读取图片")
            val src = BitmapFactory.decodeStream(input)
            input.close()
            val bmp = src?.let { scaleBitmap(it, maxDim) } ?: throw Exception("图片解码失败")
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val max = maxOf(w, h)
        if (max <= maxDim) return src
        val ratio = maxDim.toFloat() / max
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    private suspend fun recognizePhones(base64: String): List<String> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", arkModel)
            val userMsg = JSONObject().apply {
                put("role", "user")
                val content = JSONArray()
                content.put(JSONObject().put("type", "text").put("text", RECOGNIZE_PROMPT))
                content.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
                )
                put("content", content)
            }
            put("messages", JSONArray().put(userMsg))
            put("temperature", 0.1)
            put("response_format", JSONObject().put("type", "json_object"))
        }
        val request = okhttp3.Request.Builder()
            .url("$ARK_BASE/chat/completions")
            .addHeader("Authorization", "Bearer $arkKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
            .newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val detail = try {
                        JSONObject(resp.body?.string())
                            .optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    if (resp.code == 404) {
                        throw Exception(
                            "识别服务错误（404）模型不存在或未开通：" +
                                (detail ?: "请到火山方舟控制台「开通管理」开通视觉模型，并在设置中填写准确模型 ID（如 $DEFAULT_ARK_MODEL）")
                        )
                    }
                    throw Exception("识别服务错误（${resp.code}）：${detail ?: "请检查 API Key 与模型名"}")
                }
                val json = JSONObject(resp.body?.string() ?: throw Exception("识别无响应"))
                val content = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                val result = JSONObject(content)
                val phones = result.optJSONArray("phones") ?: JSONArray()
                (0 until phones.length()).mapNotNull { i ->
                    phones.getJSONObject(i).optString("model").trim().takeIf { it.isNotBlank() }
                }
            }
    }

    private suspend fun queryPrice(model: String): ModelRow? {
        for (cand in buildCandidates(model)) {
            val items = try {
                ApiClient.api(baseUrl).getModels(search = cand, sort = "brand").items
            } catch (e: Exception) {
                continue
            }
            if (items.isEmpty()) continue
            // 优先归一化后完全相等；否则选最短（最接近的精确型号）
            val cn = normalizeModel(cand)
            items.firstOrNull { normalizeModel(it.model) == cn }?.let { return it }
            return items.minByOrNull { normalizeModel(it.model).length }
        }
        return null
    }

    private class ResultAdapter(private val items: List<RecognizeResult>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<ResultAdapter.VH>() {
        class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recognize_result, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = items[position]
            holder.itemView.findViewById<TextView>(R.id.tvResultModel).text = r.model
            val row = r.row
            if (row != null) {
                holder.itemView.findViewById<TextView>(R.id.tvResultBrand).text =
                    "${row.brand} · ${row.category}"
                holder.itemView.findViewById<TextView>(R.id.tvResultPrice).text = "${row.price} 元"
            } else {
                holder.itemView.findViewById<TextView>(R.id.tvResultBrand).text = "未收录该型号，暂无报价"
                holder.itemView.findViewById<TextView>(R.id.tvResultPrice).text = "—"
            }
        }
    }
}
