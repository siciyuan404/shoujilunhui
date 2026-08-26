package com.shoujilunhui.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.shoujilunhui.app.data.ApiClient
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.data.PostBody
import com.shoujilunhui.app.ui.ModelAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ModelAdapter
    private lateinit var recycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var refresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var chipGroup: com.google.android.material.chip.ChipGroup
    private lateinit var emptyView: View
    private lateinit var errorView: View
    private lateinit var tvCount: TextView

    private var baseUrl = ""
    private var apiKey = ""
    private var currentBrand = "全部"
    private var search = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        baseUrl = prefs.getString("baseUrl", "") ?: ""
        apiKey = prefs.getString("apiKey", "") ?: ""

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), 1)
        }

        adapter = ModelAdapter(onClick = { showDetail(it) }, onLongClick = { confirmDelete(it) })
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        refresh = findViewById(R.id.refresh)
        refresh.setColorSchemeResources(R.color.accent)
        refresh.setOnRefreshListener { loadAll() }

        chipGroup = findViewById(R.id.chipGroup)
        emptyView = findViewById(R.id.emptyView)
        errorView = findViewById(R.id.errorView)
        tvCount = findViewById(R.id.tvCount)
        findViewById<TextView>(R.id.tvCount)

        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    search = s?.toString()?.trim() ?: ""
                    loadModels()
                }
                searchHandler.postDelayed(searchRunnable!!, 300)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<View>(R.id.fabAdd).setOnClickListener { showAddDialog() }

        if (baseUrl.isBlank()) {
            Toast.makeText(this, "请先在设置中填写服务器地址", Toast.LENGTH_LONG).show()
            startActivityForResult(Intent(this, SettingsActivity::class.java), 1)
        } else {
            loadAll()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            baseUrl = prefs.getString("baseUrl", "") ?: ""
            apiKey = prefs.getString("apiKey", "") ?: ""
            if (baseUrl.isNotBlank()) loadAll()
        }
    }

    private fun loadAll() {
        if (baseUrl.isBlank()) return
        refresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api = ApiClient.api(baseUrl)
                val brands = api.getBrands().items.map { it.brand }
                buildChips(listOf("全部") + brands)
                loadModels()
            } catch (e: Exception) {
                refresh.isRefreshing = false
                showError("加载失败：${e.message}")
            }
        }
    }

    private fun buildChips(brands: List<String>) {
        chipGroup.removeAllViews()
        brands.forEach { b ->
            val chip = LayoutInflater.from(this).inflate(R.layout.item_chip, chipGroup, false) as Chip
            chip.text = b
            chip.isChecked = b == currentBrand
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) { currentBrand = b; loadModels() }
            }
            chipGroup.addView(chip)
        }
    }

    private fun loadModels() {
        if (baseUrl.isBlank()) return
        refresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val brand = if (currentBrand == "全部") null else currentBrand
                val s = search.ifBlank { null }
                val resp = ApiClient.api(baseUrl).getModels(brand = brand, search = s, sort = "brand")
                adapter.submit(resp.items)
                tvCount.text = "共 ${resp.total} 款机型"
                refresh.isRefreshing = false
                emptyView.visibility = if (resp.items.isEmpty()) View.VISIBLE else View.GONE
                errorView.visibility = View.GONE
                recycler.visibility = if (resp.items.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                refresh.isRefreshing = false
                showError("加载失败：${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        errorView.visibility = View.VISIBLE
        recycler.visibility = View.GONE
        emptyView.visibility = View.GONE
        errorView.findViewById<TextView>(R.id.tvError).text = msg
    }

    private fun showDetail(row: ModelRow) {
        val dialog = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_detail, null)
        v.findViewById<TextView>(R.id.dModel).text = row.model
        v.findViewById<TextView>(R.id.dBrand).text = row.brand
        v.findViewById<TextView>(R.id.dCategory).text = row.category
        v.findViewById<TextView>(R.id.dPrice).text = "${row.price} 元"
        v.findViewById<TextView>(R.id.dNote).text = if (row.note.isNullOrBlank()) "无" else row.note
        v.findViewById<TextView>(R.id.dUpdated).text = "更新于 ${row.updatedAt ?: "-"}"
        v.findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener {
            dialog.dismiss()
            showEditDialog(row)
        }
        dialog.setContentView(v)
        dialog.show()
    }

    private fun showEditDialog(row: ModelRow) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null)
        val etPrice = v.findViewById<TextInputEditText>(R.id.etPrice)
        val etNote = v.findViewById<TextInputEditText>(R.id.etNote)
        etPrice.setText(row.price)
        etNote.setText(row.note ?: "")
        MaterialAlertDialogBuilder(this)
            .setTitle("编辑「${row.model}」")
            .setView(v)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val updated = ApiClient.api(baseUrl).putModel(
                            row.id, apiKey,
                            mapOf("price" to etPrice.text.toString().trim(), "note" to etNote.text.toString().trim())
                        )
                        adapter.update(updated)
                        Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(row: ModelRow) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除型号")
            .setMessage("确定删除「${row.model}」？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        ApiClient.api(baseUrl).deleteModel(row.id, apiKey)
                        adapter.remove(row.id)
                        Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "删除失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddDialog() {
        if (baseUrl.isBlank()) {
            Toast.makeText(this, "请先设置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val etBrand = v.findViewById<TextInputEditText>(R.id.etBrand)
        val etCategory = v.findViewById<TextInputEditText>(R.id.etCategory)
        val etModel = v.findViewById<TextInputEditText>(R.id.etModel)
        val etPrice = v.findViewById<TextInputEditText>(R.id.etPrice)
        val etNote = v.findViewById<TextInputEditText>(R.id.etNote)
        if (currentBrand != "全部") etBrand.setText(currentBrand)
        MaterialAlertDialogBuilder(this)
            .setTitle("添加型号")
            .setView(v)
            .setPositiveButton("添加") { _, _ ->
                val brand = etBrand.text.toString().trim()
                val category = etCategory.text.toString().trim()
                val model = etModel.text.toString().trim()
                if (brand.isBlank() || category.isBlank() || model.isBlank()) {
                    Toast.makeText(this, "品牌/分类/型号必填", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        ApiClient.api(baseUrl).postModel(
                            apiKey,
                            PostBody(brand, category, model, etPrice.text.toString().trim(), etNote.text.toString().trim())
                        )
                        Toast.makeText(this@MainActivity, "已添加", Toast.LENGTH_SHORT).show()
                        loadModels()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "添加失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
