@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.shoujilunhui.app.ui.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shoujilunhui.app.HistoryEntry
import com.shoujilunhui.app.HistoryItem
import com.shoujilunhui.app.data.DayStat
import com.shoujilunhui.app.data.RecordRow
import com.shoujilunhui.app.data.StatsResponse
import com.shoujilunhui.app.ui.home.fullImageUrl
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.BgGray
import com.shoujilunhui.app.ui.theme.PriceRed
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val PERIODS = listOf(
    "today" to "今天",
    "week" to "本周",
    "month" to "本月",
    "lastweek" to "上周",
    "lastmonth" to "上月",
    "custom" to "自定义",
)

/** 数字去尾零显示（1800.0 → 1800） */
private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
private fun statRow(stats: StatsResponse?): String {
    val s = stats?.summary ?: return ""
    return if (s.count == 0) "本期暂无记录" else
        "共 ${s.count} 台 · 总收 ¥${fmt(s.recTotal)} · 总出 ¥${fmt(s.saleTotal)} · 毛利 ¥${fmt(s.profit)}"
}

@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    vm: LedgerViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showEntrySheet by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editRow by remember { mutableStateOf<RecordRow?>(null) }
    var deleteRow by remember { mutableStateOf<RecordRow?>(null) }
    var customStage by remember { mutableStateOf(0) } // 0=无 1=选开始 2=选结束
    var customStart by remember { mutableStateOf("") }
    var customEnd by remember { mutableStateOf("") }
    var showHistoryPicker by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf<Long?>(null) }
    var historySelected by remember { mutableStateOf<Set<Pair<Long, Int>>>(emptySet()) }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var promptPick by remember { mutableStateOf("") }
    var promptTake by remember { mutableStateOf("") }
    var showPromptPick by remember { mutableStateOf(false) }
    var showPromptTake by remember { mutableStateOf(false) }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.recognizeToPending(uris, promptPick)
        }
    }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) cameraUri?.let { vm.recognizeToPending(listOf(it), promptTake) }
    }

    LaunchedEffect(Unit) {
        if (vm.serverBaseUrl().isBlank()) {
            snackbar.showSnackbar("请先在设置中填写服务器地址")
            onBack()
        } else {
            vm.load()
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = { Text("📒 收机记账", fontSize = 17.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.load() }) { Text("刷新", fontSize = 13.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextPrimary,
                ),
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showEntrySheet = true },
                containerColor = Accent,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "记账", tint = Color.White)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 周期切换
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PERIODS.forEach { (key, label) ->
                    FilterChip(
                        selected = ui.period == key,
                        onClick = { if (key == "custom") customStage = 1 else vm.setPeriod(key) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
            if (ui.period == "custom" && ui.customStart.isNotBlank() && ui.customEnd.isNotBlank()) {
                Text(
                    "📅 ${ui.customStart} ~ ${ui.customEnd}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    ui.busy && !ui.loaded -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(30.dp), strokeWidth = 2.dp
                    )
                    ui.error != null -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("⚠️", fontSize = 30.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(ui.error ?: "加载失败", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { vm.load() }) { Text("重试") }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 统计
                        item { StatsSection(ui.stats) }
                        // 待入账（识别结果）
                        if (ui.pending.isNotEmpty()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "📥 待入账 ${ui.pending.size} 台",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { vm.clearPending() }) { Text("清空", fontSize = 12.sp) }
                                }
                            }
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    var batchSeller by remember { mutableStateOf("") }
                                    LgField(
                                        value = batchSeller,
                                        onValueChange = { v -> batchSeller = v; vm.applyBatchSeller(v) },
                                        label = "来源人（整批，谁卖给你的）",
                                        placeholder = "如 张三 / 同行阿强，可空",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            itemsIndexed(ui.pending) { index, p ->
                                PendingCard(
                                    index = index,
                                    pending = p,
                                    channels = ui.channels,
                                    sellers = ui.sellers,
                                    onPrice = { v -> vm.updatePending(index, v, p.channel, p.salePrice, p.saleChannel) },
                                    onChannel = { v -> vm.updatePending(index, p.recPrice, v, p.salePrice, p.saleChannel) },
                                    onSalePrice = { v -> vm.updatePending(index, p.recPrice, p.channel, v, p.saleChannel) },
                                    onSaleChannel = { v -> vm.updatePending(index, p.recPrice, p.channel, p.salePrice, v) },
                                    onSeller = { v -> vm.updatePendingSeller(index, v) },
                                    onDay = { v -> vm.updatePendingDay(index, v) },
                                    onReRecognize = { vm.reRecognizePending(index) },
                                    onChecked = { checked -> vm.togglePending(index, checked) },
                                    onDelete = { vm.removePending(index) },
                                )
                            }
                            item {
                                Button(
                                    onClick = { vm.savePending() },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    enabled = !ui.pendingBusy,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(if (ui.pendingBusy) "保存中..." else "✔ 保存全部入账", fontSize = 14.sp)
                                }
                            }
                            if (ui.pendingStatus.isNotBlank()) {
                                item {
                                    Text(ui.pendingStatus, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                        if (ui.pendingBusy) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(ui.pendingStatus.ifBlank { "识别中..." }, fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                        // 明细
                        item {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(
                                    "" to "全部",
                                    "在库" to "🟢 在库",
                                    "已出" to "✅ 已出",
                                    "__nosale" to "🔸 未出货",
                                ).forEach { (k, label) ->
                                    FilterChip(
                                        selected = ui.status == k,
                                        onClick = { vm.setStatusFilter(k) },
                                        label = { Text(label, fontSize = 12.sp) },
                                    )
                                }
                            }
                        }
                        val shown = vm.filteredRecords()
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    statRow(ui.stats).ifBlank { "本期暂无记录，点右下角 ＋ 记账" },
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        items(shown, key = { it.id }) { row ->
                            RecordCard(
                                row = row,
                                baseUrl = vm.serverBaseUrl(),
                                onEdit = { editRow = row },
                                onDelete = { deleteRow = row },
                            )
                        }
                    }
                }
            }
        }
    }

    // 记账入口（拍照识别 / 手动录入）
    if (showEntrySheet) {
        ModalBottomSheet(
            onDismissRequest = { showEntrySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("选择记账方式", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        showEntrySheet = false
                        promptTake = ""
                        showPromptTake = true
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("📷 拍照识别", fontSize = 14.sp) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        showEntrySheet = false
                        promptPick = ""
                        showPromptPick = true
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("🖼 选图识别", fontSize = 14.sp) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showEntrySheet = false; showAdd = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("✍️ 手动录入一台", fontSize = 14.sp) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        showEntrySheet = false
                        vm.loadHistoryEntries()
                        historyExpanded = null
                        historySelected = emptySet()
                        showHistoryPicker = true
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("🕘 从识别历史选择", fontSize = 14.sp) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // 识别提示词对话框（拍照 / 选图前可补充提示词影响识别）
    if (showPromptTake || showPromptPick) {
        val context = LocalContext.current
        val isTake = showPromptTake
        AlertDialog(
            onDismissRequest = { showPromptTake = false; showPromptPick = false },
            title = { Text("识别提示词（可留空）", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = if (isTake) promptTake else promptPick,
                    onValueChange = { if (isTake) promptTake = it else promptPick = it },
                    placeholder = { Text("如：重点关注标签上印刷的型号文字", fontSize = 12.sp) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isTake) {
                        showPromptTake = false
                        try {
                            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
                            file.createNewFile()
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            cameraUri = uri
                            takePhoto.launch(uri)
                        } catch (e: Exception) {
                            vm.showMessage("无法打开相机：${e.message}")
                        }
                    } else {
                        showPromptPick = false
                        pickImages.launch("*/*")
                    }
                }) { Text("开始识别") }
            },
            dismissButton = {
                TextButton(onClick = { showPromptTake = false; showPromptPick = false }) { Text("取消") }
            },
        )
    }

    // 从识别历史选择
    if (showHistoryPicker) {
        HistoryPickerSheet(
            entries = ui.historyEntries,
            expandedId = historyExpanded,
            onToggleExpand = { id ->
                historyExpanded = if (historyExpanded == id) null else id
            },
            itemsOf = { id -> vm.historyItems(id) },
            selected = historySelected,
            onToggleSelect = { key ->
                historySelected = if (key in historySelected) historySelected - key else historySelected + key
            },
            onConfirm = {
                historySelected.groupBy { it.first }.forEach { (hid, keys) ->
                    val entry = ui.historyEntries.firstOrNull { it.id == hid } ?: return@forEach
                    val seqs = keys.map { it.second }.toSet()
                    vm.addFromHistory(entry, vm.historyItems(hid).filter { it.seq in seqs })
                }
                historySelected = emptySet()
                historyExpanded = null
                showHistoryPicker = false
            },
            onDismiss = { showHistoryPicker = false },
        )
    }

    // 添加
    if (showAdd) {
        RecordFormDialog(
            title = "手动录入",
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { model, rec, ch, saleCh, sale, status, day, seller, photo ->
                vm.addRecord(model, rec, ch, saleCh, seller, photo, sale, status, day) { showAdd = false }
            },
            vm = vm,
        )
    }

    // 编辑
    editRow?.let { row ->
        RecordFormDialog(
            title = "编辑修正",
            initial = row,
            onDismiss = { editRow = null },
            onSave = { model, rec, ch, saleCh, sale, status, day, seller, photo ->
                vm.updateRecord(row.id, model, rec, ch, saleCh, seller, photo, sale, status, day) { editRow = null }
            },
            vm = vm,
        )
    }

    // 删除确认
    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = { Text("删除这条记录？") },
            text = { Text("${row.model} · ${row.day} · 收 ¥${row.recPrice}", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deleteRecord(row.id, row.model); deleteRow = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteRow = null }) { Text("取消") } },
        )
    }

    // 自定义日期段（两步选日期）
    if (customStage == 1) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { customStage = 0 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        customStart = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        customStage = 2
                    }
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { customStage = 0 }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
    if (customStage == 2) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { customStage = 0 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        customEnd = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        customStage = 0
                        vm.setCustom(customStart, customEnd)
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { customStage = 0 }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

// ---------- 统计区 ----------

@Composable
private fun StatsSection(stats: StatsResponse?) {
    val s = stats?.summary
    val cards = listOf(
        "本期台数" to (s?.count?.toString() ?: "-"),
        "总收购价" to (s?.let { "¥${fmt(it.recTotal)}" } ?: "-"),
        "总出货价" to (s?.let { "¥${fmt(it.saleTotal)}" } ?: "-"),
        "毛利" to (s?.let { "¥${fmt(it.profit)}" } ?: "-"),
    )
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.take(2).forEach { (label, value) ->
                StatCard(label, value, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.drop(2).forEach { (label, value) ->
                StatCard(label, value, Modifier.weight(1f))
            }
        }
        // 每日趋势
        val byDay = stats?.byDay.orEmpty()
        if (byDay.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("每日收购额", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DayTrendChart(byDay)
            }
        }
        // 渠道排行
        val channels = stats?.byChannel.orEmpty().take(6)
        if (channels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("渠道排行", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    channels.forEach { BreakdownBar(it.channel.ifBlank { "（未填渠道）" }, it.recTotal, channels.maxOf { c -> c.recTotal }) }
                }
            }
        }
        // 来源人排行
        val sellers = stats?.bySeller.orEmpty().take(8)
        if (sellers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("来源人排行", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    sellers.forEach {
                        BreakdownBar(
                            "👤 ${it.seller}（${it.count} 台）",
                            it.recTotal,
                            sellers.maxOf { s -> s.recTotal },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (label == "毛利") PriceRed else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DayTrendChart(byDay: List<DayStat>) {
    val max = byDay.maxOf { d -> d.recTotal }.coerceAtLeast(1.0)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp).height(86.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        byDay.forEach { d ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val h = (d.recTotal / max * 56).toFloat().coerceAtLeast(2f)
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(h.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Accent)
                )
                Spacer(Modifier.height(3.dp))
                Text(d.day.takeLast(2) + "日", fontSize = 9.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BreakdownBar(name: String, value: Double, max: Double) {
    val fraction = if (max > 0) (value / max).toFloat().coerceIn(0.05f, 1f) else 0.05f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEEEEEE))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Accent)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("¥${fmt(value)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

// ---------- 待入账 ----------

@Composable
private fun PendingCard(
    index: Int,
    pending: PendingRecord,
    channels: List<String>,
    sellers: List<String>,
    onPrice: (String) -> Unit,
    onChannel: (String) -> Unit,
    onSalePrice: (String) -> Unit,
    onSaleChannel: (String) -> Unit,
    onSeller: (String) -> Unit,
    onDay: (String) -> Unit,
    onReRecognize: () -> Unit,
    onChecked: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = pending.checked,
                    onCheckedChange = onChecked,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "${index + 1}. ${pending.model}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (pending.brand.isNotBlank()) {
                    Text(pending.brand, fontSize = 10.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LgField(
                    value = pending.recPrice,
                    onValueChange = onPrice,
                    label = "收价（元）",
                    placeholder = "如 800",
                    number = true,
                    modifier = Modifier.weight(1f),
                )
                LgField(
                    value = pending.channel,
                    onValueChange = onChannel,
                    label = "渠道",
                    placeholder = "如 路边收",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            ChannelSuggestRow(channels = channels, current = pending.channel, onPick = onChannel)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LgField(
                    value = pending.salePrice,
                    onValueChange = onSalePrice,
                    label = "出货价（元）",
                    placeholder = "可后填",
                    number = true,
                    modifier = Modifier.weight(1f),
                )
                LgField(
                    value = pending.saleChannel,
                    onValueChange = onSaleChannel,
                    label = "出货渠道",
                    placeholder = "如 闲鱼",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            ChannelSuggestRow(channels = channels, current = pending.saleChannel, onPick = onSaleChannel)
            Spacer(Modifier.height(6.dp))
            LgField(
                value = pending.seller,
                onValueChange = onSeller,
                label = "来源人",
                placeholder = "谁卖给你的，可空",
                modifier = Modifier.fillMaxWidth(),
            )
            if (sellers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SellerSuggestRow(sellers = sellers, current = pending.seller, onPick = onSeller)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDate = true },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("📅 记入 ${pending.day}", fontSize = 13.sp, color = TextPrimary)
                }
                if (pending.sourceUri.isNotBlank()) {
                    OutlinedButton(
                        onClick = onReRecognize,
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("🔄 重识别", fontSize = 12.sp) }
                }
                Text("🟢 在库", fontSize = 11.sp, color = TextSecondary)
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("✕ 移除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (showDate) {
        val initial = try {
            LocalDate.parse(pending.day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDay(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

// ---------- 记录卡片 ----------

@Composable
private fun RecordCard(
    row: RecordRow,
    baseUrl: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.photo.isNotBlank()) {
                AsyncImage(
                    model = fullImageUrl(baseUrl, row.photo),
                    contentDescription = row.model,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFEEEEEE), RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF2F6FF),
                    modifier = Modifier.size(48.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("📷", fontSize = 16.sp)
                        Text("补图", fontSize = 9.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.model,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.brand.isNotBlank()) {
                        Text(row.brand, fontSize = 10.sp, color = TextSecondary)
                    }
                }
                Text(
                    "${if (row.seller.isNotBlank()) "👤 " + row.seller + " · " else ""}${row.day} · 收:${row.channel.ifBlank { "无" }}${if (row.saleChannel.isNotBlank()) " · 出:${row.saleChannel}" else ""} · ${row.status}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("收 ¥${row.recPrice}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                val sale = row.salePrice
                if (sale.isNotBlank()) {
                    Text(
                        "出 ¥$sale",
                        fontSize = 11.sp,
                        color = PriceRed,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                    val profit = sale.toDoubleOrNull()?.minus(row.recPrice.toDoubleOrNull() ?: 0.0)
                    if (profit != null) {
                        Text(
                            (if (profit >= 0) "毛利 +${fmt(profit)}" else "毛利 ${fmt(profit)}"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (profit >= 0) Color(0xFF1E9E5A) else Color(0xFFE03E3E),
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                } else {
                    Text("未出货", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("修正", fontSize = 12.sp) }
            TextButton(onClick = onDelete) { Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ---------- 录入 / 编辑表单 ----------

@Composable
private fun RecordFormDialog(
    title: String,
    initial: RecordRow?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String, String) -> Unit,
    vm: LedgerViewModel,
) {
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var recPrice by remember { mutableStateOf(initial?.recPrice ?: "") }
    var channel by remember { mutableStateOf(initial?.channel ?: "路边收") }
    var saleChannel by remember { mutableStateOf(initial?.saleChannel ?: "") }
    var seller by remember { mutableStateOf(initial?.seller ?: "") }
    var photo by remember { mutableStateOf(initial?.photo ?: "") }
    var photoUploading by remember { mutableStateOf(false) }
    var cameraUri2 by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    fun upPhoto(u: Uri) {
        if (photoUploading) return
        photoUploading = true
        vm.uploadRecordPhoto(u,
            onOk = { photo = it; photoUploading = false },
            onFail = { photoUploading = false },
        )
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { upPhoto(it) }
    }
    val takeOne = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri2?.let { upPhoto(it) }
    }
    var salePrice by remember { mutableStateOf(initial?.salePrice ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: "在库") }
    var day by remember { mutableStateOf(initial?.day ?: todayStr()) }
    var showDate by remember { mutableStateOf(false) }
    val ui by vm.ui.collectAsState()
    val suggestions = ui.suggestions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LgField(
                    value = model,
                    onValueChange = { model = it; vm.searchModels(it) },
                    label = "型号",
                    placeholder = "搜索或输入型号",
                )
                if (suggestions.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                            .background(Color(0xFFF7F7F7), RoundedCornerShape(10.dp))
                            .padding(vertical = 2.dp)
                    ) {
                        suggestions.forEach { s ->
                            Text(
                                "${s.model}（${s.brand} · ¥${s.price}）",
                                fontSize = 12.5.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        model = s.model
                                        recPrice = s.price
                                        vm.clearSuggestions()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LgField(
                        value = recPrice,
                        onValueChange = { recPrice = it },
                        label = "收购价（元）",
                        placeholder = "如 800",
                        number = true,
                        modifier = Modifier.weight(1f),
                    )
                    LgField(
                        value = channel,
                        onValueChange = { channel = it },
                        label = "渠道",
                        placeholder = "如 路边收",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                ChannelSuggestRow(channels = ui.channels, current = channel, onPick = { channel = it })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LgField(
                        value = salePrice,
                        onValueChange = { salePrice = it },
                        label = "出货价（元）",
                        placeholder = "可后填",
                        number = true,
                        modifier = Modifier.weight(1f),
                    )
                    LgField(
                        value = saleChannel,
                        onValueChange = { saleChannel = it },
                        label = "出货渠道",
                        placeholder = "如 闲鱼",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                ChannelSuggestRow(channels = ui.channels, current = saleChannel, onPick = { saleChannel = it })
                Spacer(Modifier.height(6.dp))
                LgField(
                    value = seller,
                    onValueChange = { seller = it },
                    label = "来源人",
                    placeholder = "谁卖给你的，可空",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui.sellers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    SellerSuggestRow(sellers = ui.sellers, current = seller, onPick = { seller = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { showDate = true },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("📅 $day", fontSize = 13.sp, color = TextPrimary) }
                }
                Spacer(Modifier.height(10.dp))
                Text("状态", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("在库", "已出").forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s, fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("照片", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                if (photoUploading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬆ 正在上传照片…", fontSize = 12.sp, color = TextSecondary)
                    }
                } else if (photo.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(
                            model = fullImageUrl(vm.serverBaseUrl(), photo),
                            contentDescription = "记录照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).background(Color(0xFFEEEEEE), RoundedCornerShape(10.dp)),
                        )
                        OutlinedButton(onClick = {
                            try {
                                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
                                file.createNewFile()
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                cameraUri2 = uri
                                takeOne.launch(uri)
                            } catch (e: Exception) {
                                vm.showMessage("无法打开相机：${e.message}")
                            }
                        }) { Text("📷 重拍", fontSize = 12.sp) }
                        OutlinedButton(onClick = { pickOne.launch("image/*") }) { Text("🖼 换图", fontSize = 12.sp) }
                        OutlinedButton(onClick = { photo = "" }) { Text("✕ 移除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                    }
                    if (photo.isNotBlank() && photo != (initial?.photo ?: "")) {
                        Text("新照片已上传，点「保存」生效", fontSize = 11.sp, color = Color(0xFF1E9E5A))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            try {
                                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
                                file.createNewFile()
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                cameraUri2 = uri
                                takeOne.launch(uri)
                            } catch (e: Exception) {
                                vm.showMessage("无法打开相机：${e.message}")
                            }
                        }) { Text("📷 拍照", fontSize = 12.sp) }
                        OutlinedButton(onClick = { pickOne.launch("image/*") }) { Text("🖼 相册选图", fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !photoUploading,
                onClick = { onSave(model, recPrice, channel, saleChannel, salePrice, status, day, seller, photo) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDate) {
        val initial = try {
            LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

// ---------- 统一输入框 / 渠道联想 ----------

/** 记账统一输入框：56dp 高度、12dp 圆角、14sp 文字、浮动 label 不遮字 */
@Composable
private fun LgField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, fontSize = 12.sp, color = TextSecondary) }
        } else null,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        singleLine = true,
        keyboardOptions = if (number) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
    )
}

/** 渠道联想 chips：内置常用 + 历史渠道，点击即填入 */
@Composable
private fun ChannelSuggestRow(
    channels: List<String>,
    current: String,
    onPick: (String) -> Unit,
    max: Int = 6,
) {
    val show = channels.filter { it != current }.take(max)
    if (show.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        show.forEach { c ->
            Surface(
                color = if (c == current) Accent.copy(alpha = 0.15f) else Color(0xFFF2F2F2),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(c) },
            ) {
                Text(
                    c,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 来源人联想 chips：历史来源人，点击即填入 */
@Composable
private fun SellerSuggestRow(
    sellers: List<String>,
    current: String,
    onPick: (String) -> Unit,
    max: Int = 6,
) {
    val show = sellers.filter { it != current }.take(max)
    if (show.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        show.forEach { c ->
            Surface(
                color = Color(0xFFE8F1FF),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(c) },
            ) {
                Text(
                    "👤 " + c,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ---------- 识别历史选择 ----------

private fun historyDateOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun historyTimeOf(ms: Long): String {
    val dt: LocalDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "%02d-%02d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
}

@Composable
private fun HistoryPickerSheet(
    entries: List<HistoryEntry>,
    expandedId: Long?,
    onToggleExpand: (Long) -> Unit,
    itemsOf: (Long) -> List<HistoryItem>,
    selected: Set<Pair<Long, Int>>,
    onToggleSelect: (Pair<Long, Int>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("🕘 从识别历史选择", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text("勾选识别过的机器加入待入账（记入识别当天，可在待入账卡改日期）", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "还没有识别历史，先去「识别」拍照",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(entries, key = { it.id }) { e ->
                    val expanded = expandedId == e.id
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onToggleExpand(e.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(historyTimeOf(e.createdAt), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text(
                                        "${historyDateOf(e.createdAt)} · ${e.phoneCount} 台 · 匹配 ${e.matchedCount}",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                    )
                                }
                                Text(if (expanded) "▾" else "▸", fontSize = 12.sp, color = TextSecondary)
                            }
                            if (expanded) {
                                Spacer(Modifier.height(6.dp))
                                val items = itemsOf(e.id)
                                if (items.isEmpty()) {
                                    Text("该条记录没有明细", fontSize = 12.sp, color = TextSecondary)
                                }
                                items.forEach { item ->
                                    val key = e.id to item.seq
                                    val checked = key in selected
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (checked) Accent.copy(alpha = 0.08f) else Color.Transparent)
                                            .clickable { onToggleSelect(key) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            if (checked) "☑" else "☐",
                                            fontSize = 14.sp,
                                            color = if (checked) Accent else TextSecondary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                item.model,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (item.brand.isNotBlank()) {
                                                Text(item.brand, fontSize = 10.sp, color = TextSecondary)
                                            }
                                        }
                                        if (item.channelPrice != null) {
                                            Text("收 ¥${fmt(item.channelPrice)}", fontSize = 12.sp, color = TextSecondary)
                                        } else {
                                            Text("未匹配报价", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("✔ 加入待入账（已选 ${selected.size} 台）", fontSize = 14.sp) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
