package com.shoujilunhui.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.BuildConfig
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.UpdateState
import com.shoujilunhui.app.Updater
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
    val initialProvider = config.recProvider
    val initialDeepseekKey = config.deepseekApiKey
    val initialDeepseekModel = config.deepseekModel.ifBlank { PhoneRecognizer.DEFAULT_DEEPSEEK_MODEL }
    val initialAnnotate = config.annotate
    val initialAnnotatePrice = config.annotatePrice
    val initialAnnotateModel = config.annotateModel
    val initialPriceRatio = config.priceRatio

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
        provider: String,
        deepseekKey: String,
        deepseekModel: String,
        priceRatio: Int,
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
        config.arkModel = arkModel.trim()
        config.recProvider = provider
        config.deepseekApiKey = deepseekKey.trim()
        config.deepseekModel = deepseekModel.trim()
        config.priceRatio = priceRatio
        config.annotate = annotate
        config.annotatePrice = annotatePrice
        config.annotateModel = annotateModel
        _saved.value = true
    }

    // ===== 主动更新（对齐 MeowMic 手机端：检查→下载→安装） =====

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    /** 主动检查更新（用户点击触发） */
    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = Updater.checkForUpdate(config.baseUrl, currentVersion)
        }
    }

    /** 下载最新 APK（带进度） */
    fun downloadUpdate() {
        val st = _updateState.value
        val avail = st as? UpdateState.Available ?: return
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0)
            try {
                val path = Updater.downloadApk(getApplication(), avail.url, "update_${avail.version}.apk") { p ->
                    _updateState.value = UpdateState.Downloading(p)
                }
                _updateState.value = UpdateState.ReadyToInstall(path)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "下载失败")
            }
        }
    }

    /** 调起系统安装器安装 */
    fun installUpdate() {
        val ready = _updateState.value as? UpdateState.ReadyToInstall ?: return
        try {
            Updater.installApk(getApplication(), ready.apkPath)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "无法启动安装器")
        }
    }
}
