package com.shoujilunhui.app.recognize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 豆包视觉识别 + 报价匹配（网络与图像逻辑与原 RecognizeActivity 完全一致）。
 */

/** 识别结果中的手机位置框（0~1000 归一化坐标，左上角 x1,y1，右下角 x2,y2） */
data class PhoneBox(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** 识别出的单台手机：型号 + 位置框（模型未返回框时为 null） */
data class RecognizedPhone(val model: String, val box: PhoneBox?)

class PhoneRecognizer(
    private val baseUrl: String,
    private val arkKey: String,
    private val arkModel: String = DEFAULT_ARK_MODEL,
) {

    companion object {
        // 火山方舟当前推荐的多模态视觉模型（未下线）。旧模型 doubao-1.5-vision-pro-32k-250115 已下线/停用会导致 404。
        // 若账号未开通，请到火山方舟控制台「开通管理」开通后再填准确模型 ID。
        const val DEFAULT_ARK_MODEL = "doubao-seed-2-1-turbo-260628"
        private const val ARK_BASE = "https://ark.cn-beijing.volces.com/api/v3"
        private const val RECOGNIZE_PROMPT =
            "请仔细查看这张图片，识别出图中出现的所有手机。请只返回 JSON，格式：" +
                "{\"phones\":[{\"model\":\"手机具体型号\",\"box\":{\"x1\":0,\"y1\":0,\"x2\":1000,\"y2\":1000}}]}。" +
                "注意：1) 一台一台列出，图中出现几台就列几台；" +
                "2) box 用 0~1000 归一化坐标表示该手机在图片中的边界框（左上角x1,y1，右下角x2,y2），框要尽量紧贴手机主体；" +
                "3) 型号尽量简洁，如 畅享9 Plus、P40 Pro、苹果15 Pro Max（不要带品牌名，不要带内存/颜色/新旧等多余描述）；" +
                "4) 如果图中有手机但看不清型号，根据外观给出最可能的型号；" +
                "5) 如果图中没有手机，返回 {\"phones\":[]}。不要输出任何其他内容。"
    }

    suspend fun bitmapToBase64(context: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 85): String =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: throw Exception("无法读取图片")
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

    suspend fun recognizePhones(base64: String): List<RecognizedPhone> = withContext(Dispatchers.IO) {
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
                    val obj = phones.getJSONObject(i)
                    val model = obj.optString("model").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val b = obj.optJSONObject("box")
                    val box = if (b != null) {
                        val x1 = b.optDouble("x1", Double.NaN)
                        val y1 = b.optDouble("y1", Double.NaN)
                        val x2 = b.optDouble("x2", Double.NaN)
                        val y2 = b.optDouble("y2", Double.NaN)
                        if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) null
                        else PhoneBox(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
                    } else null
                    RecognizedPhone(model, box)
                }
            }
    }

    /** 多级候选匹配报价库 */
    suspend fun queryPrice(model: String): ModelRow? {
        for (cand in ModelMatcher.buildCandidates(model)) {
            val items = try {
                ApiClient.api(baseUrl).getModels(search = cand, sort = "brand").items
            } catch (e: Exception) {
                continue
            }
            if (items.isEmpty()) continue
            // 优先归一化后完全相等；否则选最短（最接近的精确型号）
            val cn = ModelMatcher.normalizeModel(cand)
            items.firstOrNull { ModelMatcher.normalizeModel(it.model) == cn }?.let { return it }
            return items.minByOrNull { ModelMatcher.normalizeModel(it.model).length }
        }
        return null
    }
}
