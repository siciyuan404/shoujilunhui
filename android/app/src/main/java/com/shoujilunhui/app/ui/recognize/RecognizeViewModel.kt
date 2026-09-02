package com.shoujilunhui.app.ui.recognize

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.HistoryItem
import com.shoujilunhui.app.HistoryStore
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.recognize.PhoneBox
import com.shoujilunhui.app.recognize.PhoneRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RecognizeResult(
    val model: String,
    val box: PhoneBox?,
    val row: ModelRow?,
    /** 该结果来自第几张图片（与 previewUris 下标对应） */
    val imageIndex: Int = 0,
    /** 全局连续编号（跨图片连续，1、2、3…N） */
    val seq: Int = 0,
)

data class RecognizeUiState(
    /** 多张待识别图片 */
    val previewUris: List<Uri> = emptyList(),
    /** 每张图的原始宽高（与 previewUris 平行） */
    val imageSizes: List<Pair<Int, Int>?> = emptyList(),
    /** 当前大图预览显示第几张 */
    val selectedIndex: Int = 0,
    val busy: Boolean = false,
    val status: String = "",
    val results: List<RecognizeResult> = emptyList(),
    val annotate: Boolean = true,
    val annotatePrice: Boolean = true,
    val annotateModel: Boolean = true,
    /** true=显示渠道报价（内部用）；false=显示客户报价（渠道价×比例，隐藏渠道价，给客户看） */
    val showChannelPrice: Boolean = true,
)

class RecognizeViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

    private val historyStore by lazy { HistoryStore(getApplication()) }

    private val _ui = MutableStateFlow(
        RecognizeUiState(
            annotate = config.annotate,
            annotatePrice = config.annotatePrice,
            annotateModel = config.annotateModel,
        )
    )
    val ui: StateFlow<RecognizeUiState> = _ui

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }
    fun showMessage(msg: String) { _message.value = msg }

    /** 客户报价比例（%），来自设置 */
    fun priceRatio(): Int = config.priceRatio

    /** 新增一张或多张图片（拍照累加 / 相册多选） */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update {
            val sizes = it.imageSizes.toMutableList()
            uris.forEach { sizes += decodeBounds(it) }
            it.copy(
                previewUris = it.previewUris + uris,
                imageSizes = sizes,
                selectedIndex = it.previewUris.size + uris.size - 1,
                status = "",
            )
        }
    }

    /** 删除某张待识别图片，并同步调整结果归属下标 */
    fun removeImage(index: Int) {
        val cur = _ui.value
        if (index < 0 || index >= cur.previewUris.size) return
        _ui.update {
            val uris = it.previewUris.toMutableList().apply { removeAt(index) }
            val sizes = it.imageSizes.toMutableList().apply { removeAt(index) }
            val sel = when {
                uris.isEmpty() -> 0
                it.selectedIndex >= uris.size -> uris.lastIndex
                it.selectedIndex > index -> it.selectedIndex - 1
                else -> it.selectedIndex.coerceAtMost(uris.lastIndex)
            }
            it.copy(
                previewUris = uris,
                imageSizes = sizes,
                selectedIndex = sel,
                results = it.results
                    .filter { r -> r.imageIndex != index }
                    .map { r -> if (r.imageIndex > index) r.copy(imageIndex = r.imageIndex - 1) else r },
                status = "",
            )
        }
    }

    fun selectImage(index: Int) {
        val cur = _ui.value
        if (index in cur.previewUris.indices && index != cur.selectedIndex) {
            _ui.update { it.copy(selectedIndex = index) }
        }
    }

    /** 切换 渠道报价 / 客户报价（隐藏渠道价） */
    fun togglePriceMode() {
        _ui.update { it.copy(showChannelPrice = !it.showChannelPrice) }
    }

    fun startRecognize() {
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) { showMessage("请先在设置中填写服务器地址"); return }
        val provider = config.recProvider
        val (aiBaseUrl, apiKey, model, providerName) = when (provider) {
            "deepseek" -> listOf(
                PhoneRecognizer.DEEPSEEK_BASE,
                config.deepseekApiKey,
                config.deepseekModel.ifBlank { PhoneRecognizer.DEFAULT_DEEPSEEK_MODEL },
                "DeepSeek 视觉",
            )
            else -> listOf(
                PhoneRecognizer.ARK_BASE,
                config.arkApiKey,
                config.arkModel.ifBlank { PhoneRecognizer.DEFAULT_ARK_MODEL },
                "豆包视觉",
            )
        }
        if (apiKey.isBlank()) {
            showMessage(if (provider == "deepseek") "请先在设置中填写 DeepSeek API Key" else "请先在设置中填写豆包 API Key")
            return
        }
        val uris = _ui.value.previewUris
        if (uris.isEmpty()) { showMessage("请先拍照或选择图片"); return }

        _ui.update { it.copy(busy = true, status = "正在识别图片中的手机...", results = emptyList()) }
        viewModelScope.launch {
            try {
                val recognizer = PhoneRecognizer(baseUrl, aiBaseUrl, apiKey, model)
                val all = mutableListOf<RecognizeResult>()
                var failMsg: String? = null
                var seq = 1
                uris.forEachIndexed { idx, uri ->
                    _ui.update {
                        it.copy(status = if (uris.size == 1)
                            "识别中...（$providerName）"
                        else
                            "识别中...（$providerName · 第 ${idx + 1}/${uris.size} 张）")
                    }
                    try {
                        val b64 = recognizer.bitmapToBase64(getApplication(), uri)
                        val phones = recognizer.recognizePhones(b64)
                        phones.forEach { p ->
                            all += RecognizeResult(p.model, p.box, recognizer.queryPrice(p.model), idx, seq++)
                        }
                    } catch (e: Exception) {
                        failMsg = "第 ${idx + 1} 张识别失败：${e.message}"
                    }
                }
                if (all.isEmpty()) {
                    _ui.update { it.copy(status = failMsg ?: "未识别到手机，换更清晰的图重试") }
                    return@launch
                }
                _ui.update { it.copy(status = "已识别 ${all.size} 台，正在匹配报价...") }
                val matched = all.count { it.row != null }
                val total = channelTotal(all)
                val tail = if (failMsg != null) "（${failMsg}）" else ""
                // 自动保存历史（复制图片到私有目录 + 明细入库；失败不影响展示）
                val urisToSave = _ui.value.previewUris
                val chTotal = channelTotal(all)
                val cuTotal = customerTotal(all)
                val items = all.map { r ->
                    HistoryItem(
                        seq = r.seq,
                        imageIndex = r.imageIndex,
                        model = r.model,
                        recognized = r.model,
                        matched = r.row != null,
                        brand = r.row?.brand ?: "",
                        category = r.row?.category ?: "",
                        channelPrice = priceValue(r.row),
                        customerPrice = customerPrice(r.row),
                        box = r.box,
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        historyStore.save(getApplication(), urisToSave, items, all.size, matched, chTotal, cuTotal, config.priceRatio)
                    } catch (_: Exception) {}
                }
                _ui.update {
                    it.copy(
                        results = all,
                        status = "识别 ${all.size} 台，其中 $matched 台已匹配到报价" +
                            (if (total > 0) "，总价 ¥${fmt(total)}" else "") + tail,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(status = "识别失败：${e.message}") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    /** 只解码图片宽高（inJustDecodeBounds），不加载像素 */
    private fun decodeBounds(uri: Uri): Pair<Int, Int>? = try {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) (opts.outWidth to opts.outHeight) else null
        }
    } catch (e: Exception) { null }

    // ===== 报价计算：渠道价 / 客户价（渠道价 × 比例） =====

    /** 从渠道报价字符串解析数字（如 "20"、"100"），解析失败返回 null */
    fun priceValue(row: ModelRow?): Double? {
        val s = row?.price?.trim() ?: return null
        return Regex("\\d+(\\.\\d+)?").find(s)?.value?.toDoubleOrNull()
    }

    /** 客户报价 = 渠道价 × 比例（保留到元） */
    fun customerPrice(row: ModelRow?): Double? =
        priceValue(row)?.times(config.priceRatio / 100.0)?.let { (it * 10).roundToInt() / 10.0 }

    fun channelTotal(results: List<RecognizeResult>): Double =
        results.mapNotNull { priceValue(it.row) }.sum()

    fun customerTotal(results: List<RecognizeResult>): Double =
        results.mapNotNull { customerPrice(it.row) }.sum()

    fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
