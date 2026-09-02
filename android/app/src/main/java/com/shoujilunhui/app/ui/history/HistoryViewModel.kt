package com.shoujilunhui.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.shoujilunhui.app.HistoryEntry
import com.shoujilunhui.app.HistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HistoryStore(app)

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    private val _detail = MutableStateFlow<HistoryEntry?>(null)
    val detail: StateFlow<HistoryEntry?> = _detail

    fun refresh() { _entries.value = store.all() }

    fun loadDetail(id: Long) { _detail.value = store.get(id) }

    fun delete(id: Long) {
        store.delete(id)
        _entries.value = store.all()
        if (_detail.value?.id == id) _detail.value = null
    }
}
