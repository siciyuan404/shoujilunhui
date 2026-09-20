package com.shoujilunhui.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.data.ModelPatchBody
import com.shoujilunhui.app.data.OfflineStore
import com.shoujilunhui.app.data.PostBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class HomeUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    /** 是否处于离线包模式（只读快照，增删改走在线提示） */
    val offlineMode: Boolean = false,
    /** 是否存在已下载的离线包（决定空态引导文案） */
    val hasOffline: Boolean = false,
    val brands: List<String> = listOf("全部"),
    val brand: String = "全部",
    val search: String = "",
    val cpuBrand: String = "全部",
    val year: String = "全部",
    val cameraMin: Int = 0,
    val models: List<ModelRow> = emptyList(),
    val total: Int = 0,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui

    /** 一次性提示消息（Snackbar），消费后由界面调 clearMessage() */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }
    fun showMessage(msg: String) { _message.value = msg }

    val baseUrl: String get() = config.baseUrl
    private val apiKey: String get() = config.apiKey

    private var configSignature = config.signature() + "|" + OfflineStore.info(getApplication())?.updatedAt
    private var searchJob: Job? = null

    /** 离线模式下内存中的全量机型；在线模式为空（分页接口数据即当前 models） */
    private var allModels: List<ModelRow> = emptyList()

    /** 界面每次回到前台调用：配置或离线包变化则全量重载 */
    fun onResume() {
        val sig = config.signature() + "|" + OfflineStore.info(getApplication())?.updatedAt
        if (sig != configSignature) {
            configSignature = sig
            loadAll()
        }
    }

    // ---------- 加载 ----------

    fun loadAll() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // 1) 优先离线包：有就本地加载，完全不走网络
            val local = OfflineStore.loadModels(getApplication())
            if (local != null) {
                allModels = local
                _ui.update { s ->
                    s.copy(
                        loading = false, loaded = true, error = null,
                        offlineMode = true, hasOffline = true,
                        brand = if (s.brand == "全部") "全部" else s.brand,
                    )
                }
                applyLocalFilter()
                return@launch
            }
            // 2) 无离线包：走在线
            allModels = emptyList()
            if (baseUrl.isBlank()) {
                _ui.update {
                    it.copy(
                        loading = false, loaded = true, error = null,
                        offlineMode = false, hasOffline = false,
                        brands = listOf("全部"), models = emptyList(), total = 0,
                    )
                }
                return@launch
            }
            try {
                val brands = ApiClient.api(baseUrl).getBrands().items.map { it.brand }
                _ui.update { it.copy(brands = listOf("全部") + brands, offlineMode = false, hasOffline = false) }
                loadModelsInternal()
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, loaded = true, error = "加载失败：${e.message}", offlineMode = false, hasOffline = false) }
            }
        }
    }

    fun loadModels() {
        if (_ui.value.offlineMode) { applyLocalFilter(); return }
        if (baseUrl.isBlank()) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { loadModelsInternal() }
    }

    private suspend fun loadModelsInternal() {
        try {
            val s = _ui.value
            val resp = ApiClient.api(baseUrl).getModels(
                brand = if (s.brand == "全部") null else s.brand,
                search = s.search.ifBlank { null },
                sort = "release_desc",
                year = if (s.year == "全部") null else s.year,
                cpuBrand = if (s.cpuBrand == "全部") null else s.cpuBrand,
                cameraMin = if (s.cameraMin <= 0) null else s.cameraMin,
            )
            _ui.update {
                it.copy(
                    loading = false, loaded = true, error = null,
                    models = resp.items, total = resp.total,
                )
            }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, loaded = true, error = "加载失败：${e.message}") }
        }
    }

    /** 离线模式：在内存全量上做与服务器一致的本地过滤 */
    private fun applyLocalFilter() {
        val s = _ui.value
        val q = s.search.trim()
        val filtered = allModels.filter { m ->
            // 品牌
            if (s.brand != "全部" && m.brand != s.brand) return@filter false
            // CPU 品牌
            if (s.cpuBrand != "全部" && (m.cpuBrand ?: "") != s.cpuBrand) return@filter false
            // 年份：releaseDate 前 4 位
            if (s.year != "全部") {
                val rd = m.releaseDate ?: ""
                if (rd.length < 4 || rd.substring(0, 4) != s.year) return@filter false
            }
            // 相机：back_camera 形如 "5000万像素"，取开头连续数字（与服务器 CAST 一致）
            if (s.cameraMin > 0) {
                val bc = m.backCamera ?: ""
                val head = bc.takeWhile { it.isDigit() || it == '.' }
                val num = head.toDoubleOrNull() ?: 0.0
                if (num < s.cameraMin) return@filter false
            }
            // 搜索：与服务器一致，匹配 model/note/brand/category/cpuModel/releaseDate/modelCode
            if (q.isNotEmpty()) {
                val hit = (m.model.contains(q, ignoreCase = true)
                    || (m.note ?: "").contains(q, ignoreCase = true)
                    || m.brand.contains(q, ignoreCase = true)
                    || m.category.contains(q, ignoreCase = true)
                    || (m.cpuModel ?: "").contains(q, ignoreCase = true)
                    || (m.releaseDate ?: "").contains(q, ignoreCase = true)
                    || (m.modelCode ?: "").contains(q, ignoreCase = true))
                if (!hit) return@filter false
            }
            true
        }
        // 品牌列表：从全量里按首次出现顺序去重
        val brandList = linkedSetOf<String>().apply { allModels.forEach { add(it.brand) } }.toList()
        _ui.update {
            it.copy(
                loading = false, loaded = true, error = null,
                brands = listOf("全部") + brandList,
                models = filtered, total = filtered.size,
            )
        }
    }

    // ---------- 筛选 / 搜索 ----------

    /** 搜索输入：300ms 防抖（与原逻辑一致） */
    fun onSearchChange(text: String) {
        _ui.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadModels()
        }
    }

    fun onBrandChange(brand: String) {
        if (brand == _ui.value.brand) return
        _ui.update { it.copy(brand = brand) }
        loadModels()
    }

    fun applyFilters(brand: String, cpuBrand: String, year: String, cameraMin: Int) {
        _ui.update { it.copy(brand = brand, cpuBrand = cpuBrand, year = year, cameraMin = cameraMin) }
        loadModels()
    }

    // ---------- 增 / 改 / 删（离线包为只读快照，给在线提示） ----------

    private fun offlineGuard(what: String): Boolean {
        if (_ui.value.offlineMode) {
            _message.value = "当前为离线包（只读快照），$what 需连接服务器后操作"
            return true
        }
        return false
    }

    fun addModel(brand: String, category: String, model: String, price: String, note: String, images: List<Uri> = emptyList()) {
        if (offlineGuard("添加机型")) return
        if (baseUrl.isBlank()) { _message.value = "请先设置服务器地址"; return }
        viewModelScope.launch {
            try {
                val urls = images.mapNotNull { uri -> uploadImage(uri) }
                ApiClient.api(baseUrl).postModel(
                    apiKey, PostBody(brand, category, model, price, note, images = urls.ifEmpty { null })
                )
                _message.value = "已添加"
                loadModels()
            } catch (e: Exception) {
                _message.value = "添加失败：${e.message}"
            }
        }
    }

    fun updateModel(row: ModelRow, price: String, note: String, modelCode: String? = null) {
        if (offlineGuard("修改机型")) return
        viewModelScope.launch {
            try {
                val updated = ApiClient.api(baseUrl).putModel(
                    row.id, apiKey, ModelPatchBody(
                        price = price,
                        note = note,
                        modelCode = modelCode,
                    )
                )
                _ui.update { s ->
                    s.copy(models = s.models.map { if (it.id == row.id) updated else it })
                }
                _message.value = "已保存"
            } catch (e: Exception) {
                _message.value = "保存失败：${e.message}"
            }
        }
    }

    fun deleteModel(row: ModelRow) {
        if (offlineGuard("删除机型")) return
        viewModelScope.launch {
            try {
                ApiClient.api(baseUrl).deleteModel(row.id, apiKey)
                _ui.update { s ->
                    s.copy(models = s.models.filterNot { it.id == row.id }, total = s.total - 1)
                }
                _message.value = "已删除"
            } catch (e: Exception) {
                _message.value = "删除失败：${e.message}"
            }
        }
    }

    // ---------- 机型图片管理 ----------

    /** 相册多选补图：逐张上传 → 合并写回机型 images（首图为封面） */
    fun addModelImages(row: ModelRow, uris: List<Uri>) {
        if (offlineGuard("补图")) return
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
                _ui.update { s ->
                    s.copy(models = s.models.map { if (it.id == row.id) updated else it })
                }
                _message.value = "已添加 ${uploaded.size} 张图片"
            } catch (e: Exception) {
                _message.value = "补图失败：${e.message}"
            }
        }
    }

    /** 删除机型的一张图片 */
    fun removeModelImage(row: ModelRow, url: String) {
        if (offlineGuard("删图")) return
        if (baseUrl.isBlank()) { _message.value = "请先设置服务器地址"; return }
        if (apiKey.isBlank()) { _message.value = "删图需要 API Key，请先在设置中填写"; return }
        viewModelScope.launch {
            try {
                val remaining = (row.images?.filter { it.isNotBlank() }.orEmpty())
                    .filterNot { it == url }
                val updated = ApiClient.api(baseUrl).putModel(
                    row.id, apiKey, ModelPatchBody(images = remaining)
                )
                _ui.update { s ->
                    s.copy(models = s.models.map { if (it.id == row.id) updated else it })
                }
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
            _message.value = "上传失败："
            null
        }
    }

    companion object {
        val CPU_OPTIONS = listOf("全部", "高通", "联发科", "苹果", "海思", "三星", "紫光展锐", "谷歌")
        val YEAR_OPTIONS = listOf("全部") + (2026 downTo 2015).map { it.toString() }
        val CAMERA_OPTIONS = listOf("全部", "≥5000万", "≥3000万", "≥2000万", "≥1000万", "≥800万", "≥500万")

        fun cameraValue(option: String): Int =
            if (option == "全部") 0 else option.removePrefix("≥").removeSuffix("万").toInt()

        fun cameraOption(value: Int): String =
            if (value <= 0) "全部" else "≥${value}万"
    }
}
