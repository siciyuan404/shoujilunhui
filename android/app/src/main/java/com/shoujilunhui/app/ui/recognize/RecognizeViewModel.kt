package com.shoujilunhui.app.ui.recognize

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.recognize.PhoneRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecognizeResult(val model: String, val row: ModelRow?)

data class RecognizeUiState(
    val previewUri: Uri? = null,
    val busy: Boolean = false,
    val status: String = "",
    val results: List<RecognizeResult> = emptyList(),
)

class RecognizeViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

    private val _ui = MutableStateFlow(RecognizeUiState())
    val ui: StateFlow<RecognizeUiState> = _ui

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }
    fun showMessage(msg: String) { _message.value = msg }

    fun onImagePicked(uri: Uri) {
        _ui.update { it.copy(previewUri = uri, status = "") }
    }

    fun startRecognize() {
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) { showMessage("请先在设置中填写服务器地址"); return }
        val arkKey = config.arkApiKey
        if (arkKey.isBlank()) { showMessage("请先在设置中填写豆包 API Key"); return }
        val uri = _ui.value.previewUri
        if (uri == null) { showMessage("请先拍照或选择图片"); return }
        val arkModel = config.arkModel.ifBlank { PhoneRecognizer.DEFAULT_ARK_MODEL }

        _ui.update { it.copy(busy = true, status = "正在识别图片中的手机...", results = emptyList()) }
        viewModelScope.launch {
            try {
                val recognizer = PhoneRecognizer(baseUrl, arkKey, arkModel)
                val b64 = recognizer.bitmapToBase64(getApplication(), uri)
                _ui.update { it.copy(status = "识别中...（豆包视觉）") }
                val models = recognizer.recognizePhones(b64)
                if (models.isEmpty()) {
                    _ui.update { it.copy(status = "未识别到手机，换一张更清晰的图试试") }
                    return@launch
                }
                _ui.update { it.copy(status = "已识别 ${models.size} 台，正在匹配报价...") }
                val results = models.map { m -> RecognizeResult(m, recognizer.queryPrice(m)) }
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
