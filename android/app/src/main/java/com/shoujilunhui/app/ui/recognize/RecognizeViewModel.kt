package com.shoujilunhui.app.ui.recognize

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.recognize.PhoneBox
import com.shoujilunhui.app.recognize.PhoneRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecognizeResult(
    val model: String,
    val box: PhoneBox?,
    val row: ModelRow?,
)

data class RecognizeUiState(
    val previewUri: Uri? = null,
    val busy: Boolean = false,
    val status: String = "",
    val results: List<RecognizeResult> = emptyList(),
    /** 原图宽高，用于让预览容器贴合图片比例，保证标注坐标对齐 */
    val imageSize: Pair<Int, Int>? = null,
    val annotate: Boolean = true,
    val annotatePrice: Boolean = true,
    val annotateModel: Boolean = true,
)

class RecognizeViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

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

    fun onImagePicked(uri: Uri) {
        _ui.update { it.copy(previewUri = uri, status = "", imageSize = decodeBounds(uri)) }
    }

    /** 只解码图片宽高（inJustDecodeBounds），不加载像素 */
    private fun decodeBounds(uri: Uri): Pair<Int, Int>? = try {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) (opts.outWidth to opts.outHeight) else null
        }
    } catch (e: Exception) { null }

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
        val uri = _ui.value.previewUri
        if (uri == null) { showMessage("请先拍照或选择图片"); return }

        _ui.update { it.copy(busy = true, status = "正在识别图片中的手机...", results = emptyList()) }
        viewModelScope.launch {
            try {
                val recognizer = PhoneRecognizer(baseUrl, aiBaseUrl, apiKey, model)
                val b64 = recognizer.bitmapToBase64(getApplication(), uri)
                _ui.update { it.copy(status = "识别中...（$providerName）") }
                val phones = recognizer.recognizePhones(b64)
                if (phones.isEmpty()) {
                    _ui.update { it.copy(status = "未识别到手机，换一张更清晰的图试试") }
                    return@launch
                }
                _ui.update { it.copy(status = "已识别 ${phones.size} 台，正在匹配报价...") }
                val results = phones.map { RecognizeResult(it.model, it.box, recognizer.queryPrice(it.model)) }
                val matched = results.count { it.row != null }
                _ui.update {
                    it.copy(
                        results = results,
                        status = "识别 ${results.size} 台，其中 $matched 台已匹配到报价",
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(status = "识别失败：${e.message}") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }
}
