package com.shoujilunhui.app.ui.chips

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoujilunhui.app.ConfigStore
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ChipDetail
import com.shoujilunhui.app.data.ChipMeta
import com.shoujilunhui.app.data.ChipRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChipUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val chips: List<ChipRow> = emptyList(),
    val total: Int = 0,
    val search: String = "",
    val chipClass: String = "全部",
    val vendor: String = "全部",
    val romSize: String = "全部",
    val ramSize: String = "全部",
    val sort: String = "ref_desc",
    val meta: ChipMeta? = null
)

data class ChipDetailState(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: ChipDetail? = null
)

class ChipViewModel(app: Application) : AndroidViewModel(app) {

    private val config = ConfigStore(app)

    private val _ui = MutableStateFlow(ChipUiState())
    val ui: StateFlow<ChipUiState> = _ui

    private val _detail = MutableStateFlow(ChipDetailState())
    val detail: StateFlow<ChipDetailState> = _detail

    val baseUrl: String get() = config.baseUrl

    private var searchJob: Job? = null
    private var listJob: Job? = null

    /** 首次加载：meta（筛选枚举）+ 列表 */
    fun load() {
        if (baseUrl.isBlank()) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val meta = ApiClient.api(baseUrl).getChipMeta()
                _ui.update { it.copy(meta = meta) }
                loadInternal()
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "加载失败：${e.message}") }
            }
        }
    }

    fun onSearchChange(s: String) {
        _ui.update { it.copy(search = s) }
        // 搜索防抖 300ms
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadInternal()
        }
    }

    fun onFilter(chipClass: String? = null, vendor: String? = null,
                 romSize: String? = null, ramSize: String? = null) {
        val s = _ui.value
        _ui.update {
            it.copy(
                chipClass = chipClass ?: s.chipClass,
                vendor = vendor ?: s.vendor,
                romSize = romSize ?: s.romSize,
                ramSize = ramSize ?: s.ramSize,
            )
        }
        loadInternal()
    }

    fun onSort(sort: String) {
        _ui.update { it.copy(sort = sort) }
        loadInternal()
    }

    fun resetFilters() {
        _ui.update { it.copy(chipClass = "全部", vendor = "全部", romSize = "全部", ramSize = "全部") }
        loadInternal()
    }

    private fun loadInternal() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            try {
                val s = _ui.value
                val resp = ApiClient.api(baseUrl).getChips(
                    search = s.search.ifBlank { null },
                    chipClass = if (s.chipClass == "全部") null else s.chipClass,
                    vendor = if (s.vendor == "全部") null else s.vendor,
                    romSize = if (s.romSize == "全部") null else s.romSize,
                    ramSize = if (s.ramSize == "全部") null else s.ramSize,
                    sort = s.sort,
                    limit = 0
                )
                _ui.update { it.copy(chips = resp.items, total = resp.total, loading = false, loaded = true, error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "加载失败：${e.message}") }
            }
        }
    }

    fun loadDetail(id: Long) {
        if (baseUrl.isBlank()) return
        _detail.value = ChipDetailState(loading = true)
        viewModelScope.launch {
            try {
                _detail.value = ChipDetailState(loading = false, detail = ApiClient.api(baseUrl).getChipDetail(id))
            } catch (e: Exception) {
                _detail.value = ChipDetailState(loading = false, error = "加载失败：${e.message}")
            }
        }
    }

    fun clearDetail() {
        _detail.value = ChipDetailState()
    }
}
