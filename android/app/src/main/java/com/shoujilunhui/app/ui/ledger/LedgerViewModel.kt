package com.shoujilunhui.app.ui.ledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.HistoryEntry
import com.shoujilunhui.app.HistoryItem
import com.shoujilunhui.app.HistoryStore
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.data.RecordPostBody
import com.shoujilunhui.app.data.RecordBatchBody
import com.shoujilunhui.app.data.RecordPatchBody
import com.shoujilunhui.app.data.RecordRow
import com.shoujilunhui.app.data.StatsResponse
import com.shoujilunhui.app.recognize.PhoneRecognizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 今日日期 YYYY-MM-DD */
fun todayStr(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

/** 待入账的一台（识别或手动填写后批量保存） */
data class PendingRecord(
    val photo: String = "",
    val model: String = "",
    val brand: String = "",
    val category: String = "",
    val modelId: Long? = null,
    val recPrice: String = "",
    val channel: String = "",
    val salePrice: String = "",
    val status: String = "在库",
    val day: String = todayStr(),
)

data class LedgerUiState(
    val period: String = "today",
    val customStart: String = "",
    val customEnd: String = "",
    val status: String = "",
    val stats: StatsResponse? = null,
    val records: List<RecordRow> = emptyList(),
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val pending: List<PendingRecord> = emptyList(),
    val pendingBusy: Boolean = false,
    val pendingStatus: String = "",
    val suggestions: List<ModelRow> = emptyList(),
    val suggestionLoading: Boolean = false,
    val historyEntries: List<HistoryEntry> = emptyList(),
    val channels: List<String> = emptyList(),
)

class LedgerViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

    /** 常用渠道（联想数据源：内置常用 + 历史记录里的渠道） */
    private val defaultChannels = listOf("路边收", "线上", "熟客", "闲鱼", "转转", "展会", "同行", "快递", "电商平台", "朋友介绍")

    private val _ui = MutableStateFlow(LedgerUiState())
    val ui: StateFlow<LedgerUiState> = _ui

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }
    private fun showMessage(msg: String) { _message.value = msg }

    fun serverBaseUrl(): String = config.baseUrl
    fun hasWriteKey(): Boolean = config.apiKey.isNotBlank()

    /** 当前报表期：today / week / month / lastweek / lastmonth / custom */
    fun setPeriod(p: String) {
        _ui.update { it.copy(period = p, customStart = "", customEnd = "") }
        load()
    }

    fun setCustom(start: String, end: String) {
        if (start.isBlank() || end.isBlank()) { showMessage("请选择起止日期"); return }
        if (start > end) { showMessage("开始日期不能晚于结束日期"); return }
        _ui.update { it.copy(period = "custom", customStart = start, customEnd = end) }
        load()
    }

    /** 明细状态筛选：""=全部 / 在库 / 已出 / __nosale=未填出货价（本地过滤，不影响报表） */
    fun setStatusFilter(s: String) {
        _ui.update { it.copy(status = s) }
    }

    /** 按状态筛选后的明细（供列表渲染） */
    fun filteredRecords(): List<RecordRow> {
        val cur = _ui.value
        return when (cur.status) {
            "__nosale" -> cur.records.filter { it.salePrice.isBlank() }
            "" -> cur.records
            else -> cur.records.filter { it.status == cur.status }
        }
    }

    /** 加载报表统计 + 明细列表 */
    fun load() {
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) { showMessage("请先填写服务器地址"); return }
        val cur = _ui.value
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val api = ApiClient.api(baseUrl)
                val isCustom = cur.period == "custom" &&
                    cur.customStart.isNotBlank() && cur.customEnd.isNotBlank()
                val stats = if (isCustom)
                    api.getRecordStats(start = cur.customStart, end = cur.customEnd)
                else
                    api.getRecordStats(period = cur.period)
                val records = if (isCustom)
                    api.getRecords(start = cur.customStart, end = cur.customEnd, limit = 500)
                else
                    api.getRecords(period = cur.period, limit = 500)
                _ui.update {
                    it.copy(
                        stats = stats,
                        records = records.items,
                        busy = false,
                        loaded = true,
                        channels = ((stats.byChannel?.mapNotNull { c -> c.channel.ifBlank { null } } ?: emptyList()) + defaultChannels)
                            .distinct().take(12),
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = e.message ?: "加载失败") }
            }
        }
    }

    // ===== 手动录入 =====

    /** 按输入搜索报价库，供型号输入联想 */
    private var searchJob: Job? = null
    fun searchModels(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _ui.update { it.copy(suggestions = emptyList(), suggestionLoading = false) }
            return
        }
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) return
        _ui.update { it.copy(suggestionLoading = true) }
        searchJob = viewModelScope.launch {
            try {
                val items = ApiClient.api(baseUrl).getModels(search = q, sort = "brand").items
                _ui.update { it.copy(suggestions = items.take(20), suggestionLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(suggestions = emptyList(), suggestionLoading = false) }
            }
        }
    }

    fun clearSuggestions() {
        _ui.update { it.copy(suggestions = emptyList(), suggestionLoading = false) }
    }

    /** 精确匹配报价库型号（供添加/编辑时归一品牌分类） */
    private suspend fun matchModel(q: String): ModelRow? {
        val trimmed = q.trim()
        if (trimmed.isBlank()) return null
        return try {
            val items = ApiClient.api(config.baseUrl).getModels(search = trimmed, sort = "brand").items
            items.firstOrNull { it.model == trimmed } ?: items.firstOrNull()
        } catch (e: Exception) { null }
    }

    fun addRecord(
        model: String,
        recPrice: String,
        channel: String,
        salePrice: String,
        status: String,
        day: String,
        onDone: () -> Unit,
    ) {
        if (model.isBlank()) { showMessage("请填写型号"); return }
        if (!hasWriteKey()) { showMessage("请先在设置中填写服务器 API Key"); return }
        viewModelScope.launch {
            try {
                val row = matchModel(model)
                val body = RecordPostBody(
                    photo = null,
                    brand = row?.brand ?: "",
                    category = row?.category ?: "",
                    model = row?.model ?: model.trim(),
                    modelId = row?.id,
                    recPrice = recPrice.ifBlank { null },
                    channel = channel,
                    salePrice = salePrice.ifBlank { null },
                    status = status,
                    day = day.ifBlank { todayStr() },
                )
                ApiClient.api(config.baseUrl).postRecord(config.apiKey, body)
                showMessage("已入账：${body.model}")
                onDone()
                load()
            } catch (e: Exception) {
                showMessage("入账失败：${e.message}")
            }
        }
    }

    fun updateRecord(
        id: Long,
        model: String,
        recPrice: String,
        channel: String,
        salePrice: String,
        status: String,
        day: String,
        onDone: () -> Unit,
    ) {
        if (model.isBlank()) { showMessage("请填写型号"); return }
        if (!hasWriteKey()) { showMessage("请先在设置中填写服务器 API Key"); return }
        viewModelScope.launch {
            try {
                val row = matchModel(model)
                val patch = RecordPatchBody(
                    model = row?.model ?: model.trim(),
                    brand = row?.brand,
                    category = row?.category,
                    modelId = row?.id,
                    recPrice = recPrice,
                    channel = channel,
                    salePrice = salePrice,
                    status = status,
                    day = day.ifBlank { todayStr() },
                )
                ApiClient.api(config.baseUrl).putRecord(id, config.apiKey, patch)
                showMessage("已保存")
                onDone()
                load()
            } catch (e: Exception) {
                showMessage("保存失败：${e.message}")
            }
        }
    }

    fun deleteRecord(id: Long, model: String) {
        if (!hasWriteKey()) { showMessage("请先在设置中填写服务器 API Key"); return }
        viewModelScope.launch {
            try {
                ApiClient.api(config.baseUrl).deleteRecord(id, config.apiKey)
                showMessage("已删除：$model")
                load()
            } catch (e: Exception) {
                showMessage("删除失败：${e.message}")
            }
        }
    }

    // ===== 识别历史 → 待入账（补记） =====

    private val historyStore by lazy { HistoryStore(getApplication()) }

    /** 加载识别历史列表（倒序） */
    fun loadHistoryEntries() {
        _ui.update { it.copy(historyEntries = historyStore.all()) }
    }

    /** 某条历史记录的明细（seq 升序） */
    fun historyItems(historyId: Long): List<HistoryItem> =
        historyStore.get(historyId)?.items.orEmpty()

    /** 从识别历史选中的机器加入待入账；记入识别当天（后续可在待入账卡上改日期） */
    fun addFromHistory(entry: HistoryEntry, items: List<HistoryItem>) {
        if (items.isEmpty()) return
        val day = Instant.ofEpochMilli(entry.createdAt)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val added = items.map { it ->
            PendingRecord(
                model = it.model,
                brand = it.brand,
                category = it.category,
                recPrice = it.channelPrice?.let { v ->
                    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
                } ?: "",
                status = "在库",
                day = day,
            )
        }
        _ui.update { st -> st.copy(pending = st.pending + added) }
        showMessage("已加入 ${added.size} 台待入账（记入 $day）")
    }

    /** 修改待入账行的记入日期（补记到某天） */
    fun updatePendingDay(index: Int, day: String) {
        val p = _ui.value.pending.getOrNull(index) ?: return
        val updated = _ui.value.pending.toMutableList()
        updated[index] = p.copy(day = day)
        _ui.update { it.copy(pending = updated) }
    }

    // ===== 拍照识别 → 待入账 =====

    /** 选图后逐张识别为待入账记录（复用现有识别引擎 + 报价库匹配） */
    fun recognizeToPending(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val baseUrl = config.baseUrl
        if (baseUrl.isBlank()) { showMessage("请先填写服务器地址"); return }
        val isDeep = config.recProvider == "deepseek"
        val aiBaseUrl = if (isDeep) PhoneRecognizer.DEEPSEEK_BASE else PhoneRecognizer.ARK_BASE
        val apiKey = if (isDeep) config.deepseekApiKey else config.arkApiKey
        if (apiKey.isBlank()) {
            showMessage(if (isDeep) "请先在设置中填写 DeepSeek API Key" else "请先在设置中填写豆包 API Key")
            return
        }
        val model = if (isDeep) PhoneRecognizer.DEFAULT_DEEPSEEK_MODEL else PhoneRecognizer.DEFAULT_ARK_MODEL
        _ui.update { it.copy(pendingBusy = true, pendingStatus = "识别中...") }
        viewModelScope.launch {
            try {
                val recognizer = PhoneRecognizer(baseUrl, aiBaseUrl, apiKey, model, "")
                val pending = mutableListOf<PendingRecord>()
                var fail: String? = null
                val day = todayStr()
                uris.forEachIndexed { idx, uri ->
                    _ui.update { it.copy(pendingStatus = "正在识别第 ${idx + 1}/${uris.size} 张...") }
                    try {
                        val b64 = recognizer.bitmapToBase64(getApplication(), uri)
                        val phones = recognizer.recognizePhones(b64)
                        var photoUrl = ""
                        if (config.apiKey.isNotBlank()) {
                            try { photoUrl = uploadPhoto(uri) } catch (_: Exception) {}
                        }
                        phones.forEach { p ->
                            val row = recognizer.queryPrice(p.model)
                            pending += PendingRecord(
                                photo = photoUrl,
                                model = row?.model ?: p.model,
                                brand = row?.brand ?: "",
                                category = row?.category ?: "",
                                modelId = row?.id,
                                recPrice = row?.price ?: "",
                                status = "在库",
                                day = day,
                            )
                        }
                    } catch (e: Exception) {
                        fail = "第 ${idx + 1} 张失败：${e.message}"
                    }
                }
                _ui.update {
                    it.copy(
                        pending = pending,
                        pendingBusy = false,
                        pendingStatus = "已识别 ${pending.size} 台待入账" +
                            (fail?.let { f -> "（$f）" } ?: ""),
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(pendingBusy = false, pendingStatus = "识别失败：${e.message}") }
            }
        }
    }

    /** 上传原图到服务器（写操作需 Key），失败返回空串不影响入账 */
    private suspend fun uploadPhoto(uri: Uri): String {
        val ctx = getApplication<Application>()
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val res = ApiClient.api(config.baseUrl).uploadImage(config.apiKey, body)
        return res.url ?: ""
    }

    /** 修改待入账行的收价/渠道 */
    fun updatePending(index: Int, recPrice: String, channel: String) {
        val p = _ui.value.pending.getOrNull(index) ?: return
        val updated = _ui.value.pending.toMutableList()
        updated[index] = p.copy(recPrice = recPrice, channel = channel)
        _ui.update { it.copy(pending = updated) }
    }

    fun clearPending() {
        _ui.update { it.copy(pending = emptyList(), pendingStatus = "") }
    }

    /** 批量保存待入账记录 */
    fun savePending(onDone: () -> Unit = {}) {
        val cur = _ui.value
        if (cur.pending.isEmpty()) { showMessage("没有待入账的记录"); return }
        if (!hasWriteKey()) { showMessage("请先在设置中填写服务器 API Key"); return }
        viewModelScope.launch {
            try {
                val items = cur.pending.map { p ->
                    RecordPostBody(
                        photo = p.photo.ifBlank { null },
                        brand = p.brand,
                        category = p.category,
                        model = p.model,
                        modelId = p.modelId,
                        recPrice = p.recPrice.ifBlank { null },
                        channel = p.channel.ifBlank { null },
                        salePrice = p.salePrice.ifBlank { null },
                        status = p.status,
                        day = p.day.ifBlank { todayStr() },
                    )
                }
                val res = ApiClient.api(config.baseUrl)
                    .postRecordsBatch(config.apiKey, RecordBatchBody(items))
                val n = res.inserted
                showMessage("已入账 $n 台")
                _ui.update { it.copy(pending = emptyList(), pendingStatus = "") }
                onDone()
                load()
            } catch (e: Exception) {
                showMessage("入账失败：${e.message}")
            }
        }
    }
}
