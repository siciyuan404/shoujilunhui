package com.shoujilunhui.app.ui.recognize

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.HistoryItem
import com.shoujilunhui.app.HistoryStore
import com.shoujilunhui.app.data.ApiClient
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
    /** 识别页当前选中的模型 ID（空=按服务商默认），可在识别页直接切换 */
    val model: String = "",
    /** 用户填写的识别提示词（选填，引导模型提高识别率） */
    val prompt: String = "",
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

    fun serverBaseUrl(): String = config.baseUrl

    /** 切换 渠道报价 / 客户报价（隐藏渠道价） */
    fun togglePriceMode() {
        _ui.update { it.copy(showChannelPrice = !it.showChannelPrice) }
    }

    // ===== 模型切换：识别页可直接选择不同能力的视觉模型 =====

    /** 当前服务商的预设模型列表（识别页快捷切换；也可自定义输入） */
    fun modelPresets(): List<String> = if (config.recProvider == "deepseek")
        listOf("deepseek-v4-flash-vision-exp")
    else
        listOf("doubao-seed-character-260628", "doubao-seed-2-1-turbo-260628")

    fun defaultModel(): String = if (config.recProvider == "deepseek")
        PhoneRecognizer.DEFAULT_DEEPSEEK_MODEL else PhoneRecognizer.DEFAULT_ARK_MODEL

    /** 当前生效的模型（选中为空时用服务商默认） */
    fun activeModel(): String = _ui.value.model.ifBlank { defaultModel() }

    fun selectModel(m: String) {
        _ui.update { it.copy(model = m.trim()) }
    }

    /** 更新识别提示词 */
    fun setPrompt(p: String) {
        _ui.update { it.copy(prompt = p) }
    }

    // ===== 单台补救：单独重识别 / 选相似机型 =====

    /** 对某台按位置框裁剪原图区域，单独重新识别（可用更高级模型） */
    fun reRecognize(index: Int) {
        val cur = _ui.value
        if (cur.busy) return
        val target = cur.results.getOrNull(index) ?: return
        val box = target.box ?: run { showMessage("该台未返回位置框，无法单独裁剪重识别，可尝试「选相似机型」"); return }
        val uri = cur.previewUris.getOrNull(target.imageIndex) ?: run { showMessage("原图已不存在"); return }
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) { showMessage("请先填写服务器地址"); return }
        val isDeep = config.recProvider == "deepseek"
        val aiBaseUrl = if (isDeep) PhoneRecognizer.DEEPSEEK_BASE else PhoneRecognizer.ARK_BASE
        val apiKey = if (isDeep) config.deepseekApiKey else config.arkApiKey
        if (apiKey.isBlank()) { showMessage("请先填写 API Key"); return }
        val model = activeModel()
        _ui.update { it.copy(busy = true, status = "正在对第 ${target.seq} 台单独重识别（$model）...") }
        viewModelScope.launch {
            try {
                val recognizer = PhoneRecognizer(baseUrl, aiBaseUrl, apiKey, model, _ui.value.prompt)
                val phones = recognizer.recognizeCrop(getApplication(), uri, box)
                if (phones.isEmpty()) {
                    _ui.update { it.copy(busy = false, status = "该区域未识别到手机，可更换模型重试或选择相似机型") }
                    return@launch
                }
                val best = phones.first()
                val newRow = recognizer.queryPrice(best.model)
                val updated = cur.results.toMutableList()
                updated[index] = RecognizeResult(best.model, best.box, newRow, target.imageIndex, target.seq)
                _ui.update {
                    it.copy(
                        results = updated,
                        busy = false,
                        status = "已用「$model」重识别第 ${target.seq} 台为「${best.model}」",
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, status = "重识别失败：${e.message}") }
            }
        }
    }

    private val _candidatesIndex = MutableStateFlow(-1)
    val candidatesIndex: StateFlow<Int> = _candidatesIndex
    private val _candidates = MutableStateFlow<List<ModelRow>>(emptyList())
    val candidates: StateFlow<List<ModelRow>> = _candidates

    /** 加载该台的相似机型候选（基于识别型号搜索报价库） */
    fun loadCandidates(index: Int) {
        val target = _ui.value.results.getOrNull(index) ?: return
        if (config.baseUrl.isBlank()) { showMessage("请先填写服务器地址"); return }
        _candidatesIndex.value = index
        _candidates.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = ApiClient.api(config.baseUrl).getModels(search = target.model, sort = "brand").items
                _candidates.value = items.take(30)
            } catch (e: Exception) {
                showMessage("加载相似机型失败：${e.message}")
                _candidatesIndex.value = -1
            }
        }
    }

    fun closeCandidates() {
        _candidatesIndex.value = -1
        _candidates.value = emptyList()
    }

    /** 手动选择相似机型，替换该行并重新匹配报价 */
    fun applyCandidate(index: Int, row: ModelRow) {
        val cur = _ui.value
        val t = cur.results.getOrNull(index) ?: return
        val updated = cur.results.toMutableList()
        updated[index] = RecognizeResult(t.model, t.box, row, t.imageIndex, t.seq)
        _ui.update { it.copy(results = updated, status = "已将第 ${t.seq} 台手动设为「${row.model}」，重新匹配报价") }
        closeCandidates()
    }

    fun startRecognize() {
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) { showMessage("请先在设置中填写服务器地址"); return }
        val provider = config.recProvider
        val isDeep = provider == "deepseek"
        val aiBaseUrl = if (isDeep) PhoneRecognizer.DEEPSEEK_BASE else PhoneRecognizer.ARK_BASE
        val apiKey = if (isDeep) config.deepseekApiKey else config.arkApiKey
        val defaultModel = if (isDeep) PhoneRecognizer.DEFAULT_DEEPSEEK_MODEL else PhoneRecognizer.DEFAULT_ARK_MODEL
        val providerName = if (isDeep) "DeepSeek 视觉" else "豆包视觉"
        val model = _ui.value.model.ifBlank { defaultModel }
        if (apiKey.isBlank()) {
            showMessage(if (isDeep) "请先在设置中填写 DeepSeek API Key" else "请先在设置中填写豆包 API Key")
            return
        }
        val uris = _ui.value.previewUris
        if (uris.isEmpty()) { showMessage("请先拍照或选择图片"); return }

        _ui.update { it.copy(busy = true, status = "正在识别图片中的手机...", results = emptyList()) }
        viewModelScope.launch {
            try {
                val recognizer = PhoneRecognizer(baseUrl, aiBaseUrl, apiKey, model, _ui.value.prompt)
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
