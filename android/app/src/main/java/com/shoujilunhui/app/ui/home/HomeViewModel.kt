package com.shoujilunhui.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.data.PostBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
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

    val baseUrl: String get() = config.baseUrl
    private val apiKey: String get() = config.apiKey

    private var configSignature = config.signature()
    private var searchJob: Job? = null

    /** 界面每次回到前台调用：配置变化（如刚保存设置）则全量重载 */
    fun onResume() {
        val sig = config.signature()
        if (sig != configSignature) {
            configSignature = sig
            if (config.baseUrl.isNotBlank()) loadAll()
        }
    }

    // ---------- 加载 ----------

    fun loadAll() {
        if (baseUrl.isBlank()) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val brands = ApiClient.api(baseUrl).getBrands().items.map { it.brand }
                _ui.update { it.copy(brands = listOf("全部") + brands) }
                loadModelsInternal()
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, loaded = true, error = "加载失败：${e.message}") }
            }
        }
    }

    fun loadModels() {
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
                sort = "brand",
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

    // ---------- 增 / 改 / 删 ----------

    fun addModel(brand: String, category: String, model: String, price: String, note: String) {
        if (baseUrl.isBlank()) { _message.value = "请先设置服务器地址"; return }
        viewModelScope.launch {
            try {
                ApiClient.api(baseUrl).postModel(apiKey, PostBody(brand, category, model, price, note))
                _message.value = "已添加"
                loadModels()
            } catch (e: Exception) {
                _message.value = "添加失败：${e.message}"
            }
        }
    }

    fun updateModel(row: ModelRow, price: String, note: String) {
        viewModelScope.launch {
            try {
                val updated = ApiClient.api(baseUrl).putModel(
                    row.id, apiKey, mapOf("price" to price, "note" to note)
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
