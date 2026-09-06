package com.shoujilunhui.app.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.HistoryEntry
import com.shoujilunhui.app.HistoryStore
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelPatchBody
import com.shoujilunhui.app.data.ModelRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HistoryStore(app)
    private val config = ConfigStore(app)

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    private val _detail = MutableStateFlow<HistoryEntry?>(null)
    val detail: StateFlow<HistoryEntry?> = _detail

    /** 从历史点击查出的报价库机型（用于详情编辑/补图） */
    private val _modelDetail = MutableStateFlow<ModelRow?>(null)
    val modelDetail: StateFlow<ModelRow?> = _modelDetail

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val baseUrl: String get() = config.baseUrl
    private val apiKey: String get() = config.apiKey

    fun clearMessage() { _message.value = null }
    fun showMessage(msg: String) { _message.value = msg }

    fun refresh() { _entries.value = store.all() }

    fun loadDetail(id: Long) { _detail.value = store.get(id) }

    fun delete(id: Long) {
        store.delete(id)
        _entries.value = store.all()
        if (_detail.value?.id == id) _detail.value = null
    }

    /** 按识别出的型号名在报价库查找，精确匹配优先，找不到取第一个 */
    fun searchModel(name: String) {
        if (baseUrl.isBlank()) { _message.value = "请先设置服务器地址"; return }
        viewModelScope.launch {
            try {
                val resp = ApiClient.api(baseUrl).getModels(search = name)
                val hit = resp.items.firstOrNull { it.model == name } ?: resp.items.firstOrNull()
                _modelDetail.value = hit
                if (hit == null) _message.value = "报价库中未找到「$name」，可到查价页添加该机型"
            } catch (e: Exception) {
                _message.value = "查询失败：${e.message}"
            }
        }
    }

    /** 修改报价库机型（价格/备注/型号代码） */
    fun updateModel(row: ModelRow, price: String, note: String, modelCode: String? = null) {
        viewModelScope.launch {
            try {
                val updated = ApiClient.api(baseUrl).putModel(
                    row.id, apiKey, ModelPatchBody(
                        price = price,
                        note = note,
                        modelCode = modelCode,
                    )
                )
                _modelDetail.value = updated
                _message.value = "已保存"
            } catch (e: Exception) {
                _message.value = "保存失败：${e.message}"
            }
        }
    }

    /** 删除报价库机型 */
    fun deleteModel(row: ModelRow) {
        viewModelScope.launch {
            try {
                ApiClient.api(baseUrl).deleteModel(row.id, apiKey)
                _modelDetail.value = null
                _message.value = "已删除"
            } catch (e: Exception) {
                _message.value = "删除失败：${e.message}"
            }
        }
    }

    /** 相册多选补图：逐张上传 → 合并写回机型 images（首图为封面） */
    fun addModelImages(row: ModelRow, uris: List<Uri>) {
        if (baseUrl.isBlank()) { _message.value = "请先设置服务器地址"; return }
        if (apiKey.isBlank()) { _message.value = "补图需要 API Key，请先在设置中填写"; return }
        viewModelScope.launch {
            try {
                val uploaded = uris.mapNotNull { uri -> uploadImage(uri) }
                if (uploaded.isEmpty()) { _message.value = "图片上传失败，请重试"; return@launch }
                val merged = (row.images?.filter { it.isNotBlank() }.orEmpty() + uploaded).distinct()
                val updated = ApiClient.api(baseUrl).putModel(
                    row.id, apiKey, ModelPatchBody(images = merged)
                )
                _modelDetail.value = updated
                _message.value = "已添加 ${uploaded.size} 张图片"
            } catch (e: Exception) {
                _message.value = "补图失败：${e.message}"
            }
        }
    }

    /** 删除机型的一张图片 */
    fun removeModelImage(row: ModelRow, url: String) {
        if (baseUrl.isBlank()) { _message.value = "请先设置服务器地址"; return }
        if (apiKey.isBlank()) { _message.value = "删图需要 API Key，请先在设置中填写"; return }
        viewModelScope.launch {
            try {
                val remaining = (row.images?.filter { it.isNotBlank() }.orEmpty())
                    .filterNot { it == url }
                val updated = ApiClient.api(baseUrl).putModel(
                    row.id, apiKey, ModelPatchBody(images = remaining)
                )
                _modelDetail.value = updated
                _message.value = "已删除图片"
            } catch (e: Exception) {
                _message.value = "删图失败：${e.message}"
            }
        }
    }

    /** 上传本地图片到服务器，返回相对 URL（/uploads/...），失败返回 null */
    private suspend fun uploadImage(uri: Uri): String? {
        val ctx = getApplication<Application>()
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        return try {
            ApiClient.api(baseUrl).uploadImage(apiKey, body).url
        } catch (e: Exception) {
            null
        }
    }
}
