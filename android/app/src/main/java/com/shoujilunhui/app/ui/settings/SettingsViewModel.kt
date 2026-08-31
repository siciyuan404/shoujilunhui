package com.shoujilunhui.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.recognize.PhoneRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

    val initialUrl = config.baseUrl
    val initialApiKey = config.apiKey
    val initialArkKey = config.arkApiKey
    val initialArkModel = config.arkModel.ifBlank { PhoneRecognizer.DEFAULT_ARK_MODEL }
    val initialAnnotate = config.annotate
    val initialAnnotatePrice = config.annotatePrice
    val initialAnnotateModel = config.annotateModel

    /** first=是否成功, second=提示文案 */
    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    fun test(url: String) {
        if (url.isBlank()) {
            _testResult.value = false to "✗ 请填写服务器地址"
            return
        }
        _testResult.value = null
        viewModelScope.launch {
            try {
                val h = ApiClient.api(url).health()
                _testResult.value = true to "✓ 连接成功，收录 ${h.models} 款机型"
            } catch (e: Exception) {
                _testResult.value = false to "✗ 连接失败：${e.message}"
            }
        }
    }

    fun save(
        url: String,
        key: String,
        arkKey: String,
        arkModel: String,
        annotate: Boolean,
        annotatePrice: Boolean,
        annotateModel: Boolean,
    ) {
        val u = url.trim()
        if (u.isBlank()) {
            _testResult.value = false to "✗ 服务器地址不能为空"
            return
        }
        config.baseUrl = u
        config.apiKey = key.trim()
        config.arkApiKey = arkKey.trim()
        config.arkModel = arkModel.trim().ifBlank { PhoneRecognizer.DEFAULT_ARK_MODEL }
        config.annotate = annotate
        config.annotatePrice = annotatePrice
        config.annotateModel = annotateModel
        _saved.value = true
    }
}
