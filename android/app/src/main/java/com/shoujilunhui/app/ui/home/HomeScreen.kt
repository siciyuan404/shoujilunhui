@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.shoujilunhui.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.BgGray
import com.shoujilunhui.app.ui.theme.PriceRed
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary

/** 相对路径图片拼全地址 */
fun fullImageUrl(baseUrl: String, raw: String): String =
    if (raw.startsWith("http")) raw else baseUrl.trimEnd('/') + "/" + raw.trimStart('/')

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenRecognize: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var detailRow by remember { mutableStateOf<ModelRow?>(null) }
    var editRow by remember { mutableStateOf<ModelRow?>(null) }
    var deleteRow by remember { mutableStateOf<ModelRow?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // 回到前台（如从设置页返回）时按配置变化重载
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    // 首次进入：未配置服务器则提示并引导去设置
    LaunchedEffect(Unit) {
        if (vm.baseUrl.isBlank()) {
            snackbar.showSnackbar("请先在设置中填写服务器地址")
            onOpenSettings()
        } else {
            vm.loadAll()
        }
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val ptr = rememberPullToRefreshState()
    if (ptr.isRefreshing) {
        LaunchedEffect(true) {
            if (vm.baseUrl.isBlank()) ptr.endRefresh() else vm.loadAll()
        }
    }
    LaunchedEffect(ui.loading) {
        if (!ui.loading && ptr.isRefreshing) ptr.endRefresh()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BgGray,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = Accent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加型号", tint = Color.White)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HomeHeader(
                count = if (ui.loaded) ui.total else null,
                search = ui.search,
                onSearchChange = vm::onSearchChange,
                onOpenSettings = onOpenSettings,
                onOpenRecognize = onOpenRecognize,
            )
            FilterEntryRow(
                brand = ui.brand,
                cpu = ui.cpuBrand,
                year = ui.year,
                cameraMin = ui.cameraMin,
                onOpen = { showFilter = true },
                onClear = { vm.applyFilters("全部", "全部", "全部", 0) },
            )
            Box(Modifier.fillMaxSize().nestedScroll(ptr.nestedScrollConnection)) {
                when {
                    ui.error != null -> ErrorView(ui.error!!, onRetry = vm::loadAll)
                    ui.loaded && ui.models.isEmpty() -> EmptyView()
                    else -> ModelList(
                        models = ui.models,
                        baseUrl = vm.baseUrl,
                        onClick = { detailRow = it },
                        onLongClick = { deleteRow = it },
                    )
                }
                if (ui.loading && ui.models.isEmpty() && ui.error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
                PullToRefreshContainer(ptr, Modifier.align(Alignment.TopCenter))
            }
        }
    }

    detailRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { detailRow = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetailSheet(
                row = row,
                baseUrl = vm.baseUrl,
                onEdit = { detailRow = null; editRow = row },
                onDelete = { detailRow = null; deleteRow = row },
                onImageClick = { imgs, idx -> viewer = imgs to idx },
            )
        }
    }

    editRow?.let { row ->
        EditModelDialog(
            row = row,
            onDismiss = { editRow = null },
            onSave = { price, note -> vm.updateModel(row, price, note); editRow = null },
        )
    }

    deleteRow?.let { row ->
        DeleteConfirmDialog(
            row = row,
            onDismiss = { deleteRow = null },
            onConfirm = { vm.deleteModel(row); deleteRow = null },
        )
    }

    if (showAdd) {
        AddModelDialog(
            defaultBrand = if (ui.brand == "全部") "" else ui.brand,
            onDismiss = { showAdd = false },
            onAdd = { b, c, m, p, n -> vm.addModel(b, c, m, p, n); showAdd = false },
        )
    }

    if (showFilter) {
        ModalBottomSheet(
            onDismissRequest = { showFilter = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FilterSheet(
                brands = ui.brands,
                brand = ui.brand,
                cpu = ui.cpuBrand,
                year = ui.year,
                cameraMin = ui.cameraMin,
                onApply = { b, c, y, cam -> vm.applyFilters(b, c, y, cam); showFilter = false },
                onReset = { vm.applyFilters("全部", "全部", "全部", 0); showFilter = false },
            )
        }
    }

    viewer?.let { (imgs, idx) ->
        ImageViewerDialog(
            urls = imgs,
            startIndex = idx,
            baseUrl = vm.baseUrl,
            onDismiss = { viewer = null },
        )
    }
}

// ---------- 头部（紧凑：搜索 + 拍照识别 + 设置） ----------

@Composable
private fun HomeHeader(
    count: Int?,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecognize: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f).height(44.dp),
            textStyle = TextStyle(fontSize = 13.sp),
            placeholder = {
                Text(
                    count?.let { "搜索型号 · 共 $it 款机型" } ?: "搜索型号",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search, contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = TextSecondary,
                )
            },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(16.dp), tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        // 拍照识别：仅图标圆形按钮
        Surface(
            onClick = onOpenRecognize,
            shape = CircleShape,
            color = Accent,
            modifier = Modifier.size(44.dp),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "拍照识别",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 设置入口
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "设置", tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

// ---------- 筛选入口（含品牌等已选条件） ----------

@Composable
private fun FilterEntryRow(
    brand: String,
    cpu: String,
    year: String,
    cameraMin: Int,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val active = brand != "全部" || cpu != "全部" || year != "全部" || cameraMin > 0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onOpen,
            label = { Text("筛选", fontSize = 12.sp) },
            modifier = Modifier.height(28.dp),
            shape = RoundedCornerShape(14.dp),
        )
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(
                buildList {
                    if (brand != "全部") add(brand)
                    if (cpu != "全部") add(cpu)
                    if (year != "全部") add("${year}年")
                    if (cameraMin > 0) add("≥${cameraMin}万")
                }.joinToString(" · "),
                fontSize = 11.sp,
                color = Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "清除筛选", tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ---------- 列表（卡片） ----------

@Composable
private fun ModelList(
    models: List<ModelRow>,
    baseUrl: String,
    onClick: (ModelRow) -> Unit,
    onLongClick: (ModelRow) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(models, key = { it.id }) { row ->
            ModelCard(
                row = row,
                baseUrl = baseUrl,
                onClick = { onClick(row) },
                onLongClick = { onLongClick(row) },
            )
        }
    }
}

@Composable
private fun ModelCard(row: ModelRow, baseUrl: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val imgs = row.images?.filter { it.isNotBlank() }.orEmpty()
            if (imgs.isNotEmpty()) {
                AsyncImage(
                    model = fullImageUrl(baseUrl, imgs.first()),
                    contentDescription = row.model,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFFEEEEEE), RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.model,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${row.brand} · ${row.category}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val specs = buildList {
                    if (!row.cpuModel.isNullOrBlank()) add(row.cpuModel)
                    if (!row.releaseDate.isNullOrBlank()) add(row.releaseDate.take(4) + "年")
                    if (!row.backCamera.isNullOrBlank()) add(row.backCamera)
                }
                if (specs.isNotEmpty()) {
                    Text(
                        specs.joinToString(" · "),
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (row.price.isNotBlank()) "¥${row.price}" else "面议",
                    color = PriceRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (!row.note.isNullOrBlank()) {
                    Text(
                        row.note,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

// ---------- 空 / 错误态 ----------

@Composable
private fun EmptyView() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔍", fontSize = 32.sp)
        Spacer(Modifier.height(6.dp))
        Text("没有找到匹配的型号", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ErrorView(msg: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚠️", fontSize = 32.sp)
        Spacer(Modifier.height(6.dp))
        Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}
