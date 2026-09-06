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

/** 识别出的单台手机：型号 + 品牌（识别结果带 brand，空则未知）+ 位置框（模型未返回框时为 null） */
data class RecognizedPhone(val model: String, val box: PhoneBox?, val brand: String = "")

class PhoneRecognizer(
    private val serverBaseUrl: String,
    private val aiBaseUrl: String,
    private val apiKey: String,
    private val model: String,
    /** 用户在识别页填写的补充提示词，可引导模型更准确识别（空则不影响） */
    private val extraPrompt: String = "",
) {

    companion object {
        // 豆包/火山方舟：性价比更高的多模态视觉模型（默认）
        const val DEFAULT_ARK_MODEL = "doubao-seed-character-260628"
        // DeepSeek：视觉模型（可识别图片）
        const val DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash-vision-exp"
        const val ARK_BASE = "https://ark.cn-beijing.volces.com/api/v3"
        const val DEEPSEEK_BASE = "https://api.deepseek.com"
        private const val RECOGNIZE_PROMPT =
            "请识别这张图片中出现的所有手机。识别时必须严格分两步，先确定品牌，再确定型号。\n" +
                "第一步【先确定品牌】：逐一观察每台手机的品牌特征（背面或正面logo、摄像头模组布局与形状、屏幕形态、机身设计语言、系统界面），品牌只能从下面列表中选择：\n" +
                "苹果(Apple/iPhone)、华为、荣耀、小米、红米、OPPO、vivo、一加、真我、三星、魅族、努比亚、联想、摩托罗拉、中兴、谷歌(Google)、360、金立、酷派、乐视、美图、锤子、华硕、智选、鼎桥、诺基亚、索尼、LG、黑鲨、红魔、iQOO、其他\n" +
                "品牌判定规则：1) 必须有明确依据（logo、外观特征）才选该品牌，拿不准选\"其他\"；" +
                "2) 苹果特征：背面中央咬一口苹果logo、左上角方形摄像头模组（Pro系列为三角排列三摄）、灵动岛(15/16/17)或刘海屏(14及以前)、无后置指纹、边框圆润。苹果是市场最常见的手机，拍摄对象是苹果时务必认苹果，除非照片明显不是；" +
                "3) 严禁把苹果误认成360、魅族、金立、酷派等小众品牌，这些品牌只有照片上清楚出现其logo或标志性外观时才可选。\n" +
                "第二步【再确定型号】：品牌确定后，从该品牌真实存在过的型号中给出最可能的具体型号，宁缺勿错：看不清具体型号时只写系列名（如\"苹果15\"、\"P40\"），绝不编造不存在的型号。\n" +
                "输出严格 JSON 格式：{\"phones\":[{\"brand\":\"品牌\",\"model\":\"型号\",\"box\":{\"x1\":0,\"y1\":0,\"x2\":1000,\"y2\":1000}}]}。" +
                "model 规则：不带品牌名；苹果统一用 iPhone 写法（如 iPhone 15 Pro Max、iPhone 14、iPhone XR），其他品牌用官方型号名（如 P40 Pro、畅享9 Plus、N7）。\n" +
                "box 用 0~1000 归一化坐标表示该手机在图片中的边界框（左上角x1,y1，右下角x2,y2），框尽量紧贴手机主体；一台一台列，图中几台就列几台；图中没有手机返回 {\"phones\":[]}。只输出 JSON，不要任何其他内容。"
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

    /**
     * 单台补救：按归一化 box 裁剪图片中该手机所在区域，放大后单独重新识别。
     * 用于整体识别不准确时，对某台手机单独换更高级模型重试。
     */
    suspend fun recognizeCrop(context: Context, uri: Uri, box: PhoneBox): List<RecognizedPhone> =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: throw Exception("无法读取图片")
            val src = BitmapFactory.decodeStream(input)
            input.close()
            if (src == null) throw Exception("图片解码失败")
            val w = src.width
            val h = src.height
            val x1 = (box.x1 / 1000f * w).toInt().coerceIn(0, w - 1)
            val y1 = (box.y1 / 1000f * h).toInt().coerceIn(0, h - 1)
            val x2 = (box.x2 / 1000f * w).toInt().coerceIn(x1 + 1, w)
            val y2 = (box.y2 / 1000f * h).toInt().coerceIn(y1 + 1, h)
            val crop = Bitmap.createBitmap(src, x1, y1, x2 - x1, y2 - y1)
            val scaled = scaleBitmap(crop, 1024)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            recognizePhones(b64)
        }

    /** 组装最终识别指令：基础指令 + 用户补充提示词（选填） */
    private fun buildRecognizePrompt(): String {
        val t = extraPrompt.trim()
        if (t.isEmpty()) return RECOGNIZE_PROMPT
        return RECOGNIZE_PROMPT +
            "\n[用户补充提示词] $t\n" +
            "请把以上用户提示作为重要参考，结合提示更准确地识别图中手机及位置框。"
    }

    suspend fun recognizePhones(base64: String): List<RecognizedPhone> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            val userMsg = JSONObject().apply {
                put("role", "user")
                val content = JSONArray()
                content.put(JSONObject().put("type", "text").put("text", buildRecognizePrompt()))
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
            .url("$aiBaseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
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
                                (detail ?: "请在设置中选择正确的服务商并填写已开通的模型 ID（豆包方舟：$DEFAULT_ARK_MODEL；DeepSeek：$DEFAULT_DEEPSEEK_MODEL）")
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
                    val brand = obj.optString("brand").trim()
                    val b = obj.optJSONObject("box")
                    val box = if (b != null) {
                        val x1 = b.optDouble("x1", Double.NaN)
                        val y1 = b.optDouble("y1", Double.NaN)
                        val x2 = b.optDouble("x2", Double.NaN)
                        val y2 = b.optDouble("y2", Double.NaN)
                        if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) null
                        else PhoneBox(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
                    } else null
                    RecognizedPhone(model, box, brand)
                }
            }
    }

    /** 多级候选匹配报价库；brandHint 来自识别结果时只在该品牌内匹配（苹果/iPhone 统一视为苹果），避免跨品牌误配 */
    suspend fun queryPrice(model: String, brandHint: String = ""): ModelRow? {
        val hint = brandHint.trim()
        for (cand in ModelMatcher.buildCandidates(model)) {
            val items = try {
                ApiClient.api(serverBaseUrl).getModels(search = cand, sort = "brand").items
            } catch (e: Exception) {
                continue
            }
            if (items.isEmpty()) continue
            val pool = if (hint.isEmpty()) items else items.filter { ModelMatcher.sameBrand(it.brand, hint) }
            if (hint.isNotEmpty() && pool.isEmpty()) continue
            // 优先归一化后完全相等；否则选最短（最接近的精确型号）
            val cn = ModelMatcher.normalizeModel(cand)
            pool.firstOrNull { ModelMatcher.normalizeModel(it.model) == cn }?.let { return it }
            return pool.minByOrNull { ModelMatcher.normalizeModel(it.model).length }
        }
        return null
    }
}
