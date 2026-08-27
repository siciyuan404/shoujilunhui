package com.shoujilunhui.app

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
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
    private lateinit var spCpuBrand: Spinner
    private lateinit var spYear: Spinner
    private lateinit var spCamera: Spinner
    private var filterCpuBrand = "全部"
    private var filterYear = "全部"
    private var filterCamera = 0
    private var spinnerReady = false
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

        adapter = ModelAdapter(baseUrl, onClick = { showDetail(it) }, onLongClick = { confirmDelete(it) })
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        refresh = findViewById(R.id.refresh)
        refresh.setColorSchemeResources(R.color.accent)
        refresh.setOnRefreshListener { loadAll() }

        chipGroup = findViewById(R.id.chipGroup)
        emptyView = findViewById(R.id.emptyView)
        spCpuBrand = findViewById(R.id.spCpuBrand)
        spYear = findViewById(R.id.spYear)
        spCamera = findViewById(R.id.spCamera)
        setupSpecSpinners()
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

        // 基于 GitHub tag 的版本更新检查（延迟 3 秒，避免打断首屏）
        Handler(Looper.getMainLooper()).postDelayed({
            Updater.check(this, BuildConfig.VERSION_NAME)
        }, 3000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            baseUrl = prefs.getString("baseUrl", "") ?: ""
            apiKey = prefs.getString("apiKey", "") ?: ""
            adapter.updateBaseUrl(baseUrl)
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

    private fun setupSpecSpinners() {
        val cpuOpts = listOf("全部") + listOf("高通", "联发科", "苹果", "海思", "三星", "紫光展锐", "谷歌")
        val yearOpts = listOf("全部") + (2026 downTo 2015).map { it.toString() }
        val camOpts = listOf("全部", "≥5000万", "≥3000万", "≥2000万", "≥1000万", "≥800万", "≥500万")
        spCpuBrand.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cpuOpts)
        spYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearOpts)
        spCamera.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, camOpts)
        spCpuBrand.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                filterCpuBrand = if (pos == 0) "全部" else cpuOpts[pos]
                if (spinnerReady) loadModels()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        spYear.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                filterYear = if (pos == 0) "全部" else yearOpts[pos]
                if (spinnerReady) loadModels()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        spCamera.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                filterCamera = if (pos == 0) 0 else camOpts[pos].removePrefix("≥").removeSuffix("万").toInt()
                if (spinnerReady) loadModels()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        spinnerReady = true
    }

    private fun loadModels() {
        if (baseUrl.isBlank()) return
        refresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val brand = if (currentBrand == "全部") null else currentBrand
                val s = search.ifBlank { null }
                val resp = ApiClient.api(baseUrl).getModels(
                    brand = brand, search = s, sort = "brand",
                    year = if (filterYear == "全部") null else filterYear,
                    cpuBrand = if (filterCpuBrand == "全部") null else filterCpuBrand,
                    cameraMin = if (filterCamera <= 0) null else filterCamera
                )
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

    private fun fillSpecs(v: View, row: ModelRow) {
        val specContainer = v.findViewById<android.widget.LinearLayout>(R.id.specContainer)
        val items = listOf(
            "上市时间" to row.releaseDate,
            "CPU品牌" to row.cpuBrand,
            "CPU型号" to row.cpuModel,
            "运行内存" to row.ram,
            "存储" to row.rom,
            "后置主摄" to row.backCamera,
            "前置" to row.frontCamera,
            "屏幕" to row.screenSize,
            "屏幕材质" to row.screenType,
            "刷新率" to row.refresh,
            "电池" to row.battery,
            "快充" to row.charge,
            "网络" to row.network,
            "系统" to row.os
        ).filter { !it.second.isNullOrBlank() }
        if (items.isEmpty()) {
            val tv = TextView(this)
            tv.text = "待补充"
            tv.setTextColor(resources.getColor(R.color.textSecondary, null))
            tv.textSize = 13f
            specContainer.addView(tv)
        } else {
            items.forEach { (k, vv) ->
                val tv = TextView(this)
                tv.text = "$k：${vv}"
                tv.setTextColor(resources.getColor(R.color.text, null))
                tv.textSize = 13f
                tv.setPadding(0, 4, 0, 4)
                specContainer.addView(tv)
            }
        }
        val variantContainer = v.findViewById<android.widget.LinearLayout>(R.id.variantContainer)
        val vars = row.variants ?: emptyList()
        if (vars.isNotEmpty()) {
            v.findViewById<TextView>(R.id.dVariantTitle).visibility = View.VISIBLE
            vars.forEach { vv ->
                val tv = TextView(this)
                tv.text = "${vv.spec}   ${vv.price} 元"
                tv.setTextColor(resources.getColor(R.color.priceColor, null))
                tv.textSize = 14f
                tv.setPadding(0, 4, 0, 4)
                variantContainer.addView(tv)
            }
        }
    }

    private fun showDetail(row: ModelRow) {
        val dialog = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_detail, null)
        v.findViewById<TextView>(R.id.dModel).text = row.model
        v.findViewById<TextView>(R.id.dBrand).text = row.brand
        v.findViewById<TextView>(R.id.dCategory).text = row.category
        v.findViewById<TextView>(R.id.dPrice).text = "${row.price} 元"
        // 详情图片：多图左右滑动 + 完整展示
        val imageSection = v.findViewById<View>(R.id.imageSection)
        val pager = v.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.dImagePager)
        val dotContainer = v.findViewById<LinearLayout>(R.id.dotIndicator)
        val imgs = row.images?.filter { it.isNotBlank() }.orEmpty()
        if (imgs.isNotEmpty()) {
            imageSection.visibility = View.VISIBLE
            pager.adapter = DetailImageAdapter(imgs)
            if (imgs.size > 1) {
                buildDots(dotContainer, imgs.size)
                pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateDots(dotContainer, position)
                    }
                })
            }
        } else {
            imageSection.visibility = View.GONE
        }
        fillSpecs(v, row)
        v.findViewById<TextView>(R.id.dNote).text = if (row.note.isNullOrBlank()) "无" else row.note
        v.findViewById<TextView>(R.id.dUpdated).text = "更新于 ${row.updatedAt ?: "-"}"
        v.findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener {
            dialog.dismiss()
            showEditDialog(row)
        }
        dialog.setContentView(v)
        dialog.show()
    }

    private fun buildDots(container: LinearLayout, count: Int) {
        container.removeAllViews()
        repeat(count) {
            val dot = View(this)
            val size = dp(6)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(dp(3), 0, dp(3), 0)
            dot.layoutParams = lp
            container.addView(dot)
        }
        updateDots(container, 0)
    }

    private fun updateDots(container: LinearLayout, active: Int) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).background = resources.getDrawable(
                if (i == active) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive, null
            )
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private inner class DetailImageAdapter(private val urls: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<DetailImageAdapter.VH>() {
        class VH(val imageView: ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_detail_image, parent, false) as ImageView
            return VH(iv)
        }

        override fun getItemCount(): Int = urls.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val raw = urls[position]
            val full = if (raw.startsWith("http")) raw else baseUrl.trimEnd('/') + "/" + raw.trimStart('/')
            holder.imageView.load(full) {
                crossfade(true)
                placeholder(ColorDrawable(0xFFEEEEEE.toInt()))
                error(ColorDrawable(0xFFDDDDDD.toInt()))
            }
        }
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
