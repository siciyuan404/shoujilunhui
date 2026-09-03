@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.shoujilunhui.app.ui.recognize

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.ui.home.DetailSheet
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.MatchGreen
import com.shoujilunhui.app.ui.theme.MatchOrange
import com.shoujilunhui.app.ui.theme.PriceRed
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun RecognizeScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
    vm: RecognizeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val candIdx by vm.candidatesIndex.collectAsState()
    val candidates by vm.candidates.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var detailRow by remember { mutableStateOf<ModelRow?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.addImages(listOf(uri))
    }
    // 相册多选
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    // 单台报价展示文本：渠道价 or 客户价（隐藏渠道价）
    val priceFor: (RecognizeResult) -> String = { r ->
        if (r.row == null) "未收录"
        else if (ui.showChannelPrice) "${r.row.price}元"
        else "${vm.fmt(vm.customerPrice(r.row) ?: 0.0)}元"
    }

    // 模型选择对话框
    if (showModelPicker) {
        ModelPickerDialog(
            presets = vm.modelPresets(),
            current = vm.activeModel(),
            onDismiss = { showModelPicker = false },
            onSelect = { m -> vm.selectModel(m); showModelPicker = false },
        )
    }

    // 相似机型候选弹层
    if (candIdx >= 0) {
        ModalBottomSheet(
            onDismissRequest = { vm.closeCandidates() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CandidateSheet(
                index = candIdx,
                candidates = candidates,
                onPick = { m -> vm.applyCandidate(candIdx, m) },
                onDismiss = { vm.closeCandidates() },
            )
        }
    }

    // 机型详情弹层
    detailRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { detailRow = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetailSheet(
                row = row,
                baseUrl = vm.serverBaseUrl(),
                onEdit = {},
                onDelete = {},
                onImageClick = { _, _ -> },
                showActions = false,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("拍照识别报价", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenHistory) {
                        Text("历史", color = Color.White, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Accent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 预览（多图大图 + 图上标注）
            item { PreviewWithOverlay(ui, priceFor) }

            // 缩略图列表（可删除 / 点击切换大图）
            if (ui.previewUris.isNotEmpty()) {
                item { ThumbnailStrip(ui, vm) }
            }

            // 取图
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
                                file.createNewFile()
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                cameraUri = uri
                                takePicture.launch(uri)
                            } catch (e: Exception) {
                                vm.showMessage("无法打开相机：${e.message}")
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("拍照", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { pickImages.launch("image/*") },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("从相册选择", fontSize = 13.sp) }
                }
            }

            // 识别
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("识别模型", fontSize = 12.5.sp, color = TextSecondary)
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = { showModelPicker = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(vm.activeModel(), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                Button(
                    onClick = vm::startRecognize,
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (ui.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("识别中...", fontSize = 13.sp)
                    } else {
                        Text("开始识别", fontSize = 14.sp)
                    }
                }
            }

            if (ui.status.isNotBlank()) {
                item { Text(ui.status, fontSize = 12.sp, color = TextSecondary) }
            }

            if (ui.results.isNotEmpty()) {
                item { Text("识别结果", fontSize = 14.sp, fontWeight = FontWeight.Bold) }

                // 总价卡片 + 报价隐藏/显示切换
                item { TotalCard(ui, vm) }

                itemsIndexed(ui.results) { index, r ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                            .then(if (r.row != null)
                                Modifier.clickable { detailRow = r.row }
                            else Modifier),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(if (r.row != null) MatchGreen else MatchOrange, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${r.seq}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.model,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    r.row?.let { "${it.brand} · ${it.category}" } ?: "未收录该型号，暂无报价",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (ui.previewUris.size > 1) {
                                Text(
                                    "图${r.imageIndex + 1}",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier
                                        .background(Color(0xFFF0F2F5), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (r.row == null) "—"
                                else if (ui.showChannelPrice) "¥${r.row.price}"
                                else "¥${vm.fmt(vm.customerPrice(r.row) ?: 0.0)}",
                                color = if (r.row != null) PriceRed else TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { vm.reRecognize(index) },
                                enabled = !ui.busy,
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                            ) { Text("重识别", fontSize = 12.sp) }
                            if (r.row == null) {
                                TextButton(
                                    onClick = { vm.loadCandidates(index) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                ) { Text("选相似机型", fontSize = 12.sp, color = Accent) }
                            } else {
                                TextButton(
                                    onClick = { detailRow = r.row },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                ) { Text("详情", fontSize = 12.sp) }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "识别有偏差可点「重识别」换模型重试",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 总价卡片 + 报价显示模式切换（隐藏渠道价） ----------

@Composable
private fun TotalCard(ui: RecognizeUiState, vm: RecognizeViewModel) {
    val ratio = vm.priceRatio()
    val showChannel = ui.showChannelPrice
    val total = if (showChannel) vm.channelTotal(ui.results) else vm.customerTotal(ui.results)
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF3E3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showChannel) "渠道报价总计（内部）" else "客户报价总计（比例 ${ratio}%）",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Text(
                    "¥${vm.fmt(total)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PriceRed,
                )
                if (showChannel) {
                    Text(
                        "客户价 = 渠道价 × ${ratio}%，点击右侧按钮切换后隐藏渠道价",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                    )
                }
            }
            TextButton(onClick = vm::togglePriceMode) {
                Text(if (showChannel) "隐藏报价" else "显示渠道价", fontSize = 12.5.sp)
            }
        }
    }
}

// ---------- 缩略图横向列表 ----------

@Composable
private fun ThumbnailStrip(ui: RecognizeUiState, vm: RecognizeViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(ui.previewUris) { index, uri ->
                val selected = index == ui.selectedIndex
                val imgItems = ui.results.filter { it.imageIndex == index }
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, if (selected) Accent else Color(0xFFDDE1E6), RoundedCornerShape(8.dp))
                        .clickable { vm.selectImage(index) },
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "第${index + 1}张",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 每张图都叠加识别框标注
                    if (imgItems.isNotEmpty()) {
                        Canvas(Modifier.fillMaxSize()) {
                            imgItems.forEach { r ->
                                val b = r.box ?: return@forEach
                                val l = b.x1 / 1000f * size.width
                                val t = b.y1 / 1000f * size.height
                                val w = (b.x2 - b.x1) / 1000f * size.width
                                val h = (b.y2 - b.y1) / 1000f * size.height
                                if (w <= 0f || h <= 0f) return@forEach
                                drawRect(
                                    if (r.row != null) MatchGreen else MatchOrange,
                                    topLeft = Offset(l, t), size = Size(w, h), style = Stroke(width = 2f),
                                )
                            }
                        }
                        val seqs = imgItems.map { it.seq }
                        Text(
                            if (seqs.size == 1) "${seqs[0]}" else "${seqs.first()}-${seqs.last()}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color(0xAA000000), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color(0x99000000), CircleShape)
                            .clickable { vm.removeImage(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }
        TextButton(onClick = { while (ui.previewUris.isNotEmpty()) vm.removeImage(0) }) {
            Text("清空", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// ---------- 预览 + 图上标注（多图：只画当前选中图的结果） ----------

@Composable
private fun PreviewWithOverlay(
    ui: RecognizeUiState,
    priceFor: (RecognizeResult) -> String,
) {
    val uri = ui.previewUris.getOrNull(ui.selectedIndex)
    if (uri == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Color(0xFFE9EDF2), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("拍照或从相册选择手机照片（可多张一起识别）", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }
    val imgSize = ui.imageSizes.getOrNull(ui.selectedIndex)
    val currentResults = ui.results.filter { it.imageIndex == ui.selectedIndex }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFE9EDF2), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { 400.dp.toPx() }
        val hasSize = imgSize != null && imgSize.first > 0 && imgSize.second > 0
        val (dispWpx, dispHpx) = if (hasSize) {
            val s = imgSize!!
            val scale = min(maxWpx / s.first, maxHpx / s.second)
            (s.first * scale) to (s.second * scale)
        } else {
            maxWpx to with(density) { 200.dp.toPx() }
        }
        Box(
            Modifier
                .width(with(density) { dispWpx.toDp() })
                .height(with(density) { dispHpx.toDp() })
                .align(Alignment.Center),
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "待识别图片",
                contentScale = if (hasSize) ContentScale.FillBounds else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (currentResults.isNotEmpty() && ui.annotate) {
                RecognitionOverlay(
                    results = currentResults,
                    showPrice = ui.annotatePrice,
                    showModel = ui.annotateModel,
                    showChannelPrice = ui.showChannelPrice,
                    priceFor = priceFor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 在预览图上绘制彩色标注框：绿=已匹配报价，橙=未收录；顶部标签条显示 编号+型号+价格 */
@Composable
private fun RecognitionOverlay(
    results: List<RecognizeResult>,
    showPrice: Boolean,
    showModel: Boolean,
    showChannelPrice: Boolean,
    priceFor: (RecognizeResult) -> String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        results.forEachIndexed { i, r ->
            val box = r.box ?: return@forEachIndexed
            val l = box.x1 / 1000f * size.width
            val t = box.y1 / 1000f * size.height
            val w = (box.x2 - box.x1) / 1000f * size.width
            val h = (box.y2 - box.y1) / 1000f * size.height
            if (w <= 0f || h <= 0f) return@forEachIndexed
            val matched = r.row != null
            val color = if (matched) MatchGreen else MatchOrange
            // 外框
            drawRect(color, topLeft = Offset(l, t), size = Size(w, h), style = Stroke(width = 4f))
            // 顶部标签条：编号 + 型号 + 价格
            val label = buildAnnotationLabel(i, r, showPrice, showModel, showChannelPrice, priceFor)
            val style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val layout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = style,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (w - 16f).roundToInt().coerceAtLeast(1)),
            )
            val headerH = layout.size.height.toFloat() + 16f
            drawRect(color, topLeft = Offset(l, t), size = Size(w, headerH))
            drawText(layout, topLeft = Offset(l + 8f, t + 8f))
        }
    }
}

private fun buildAnnotationLabel(
    i: Int,
    r: RecognizeResult,
    showPrice: Boolean,
    showModel: Boolean,
    showChannelPrice: Boolean,
    priceFor: (RecognizeResult) -> String,
): String {
    val sb = StringBuilder().append(r.seq)
    if (showModel) { sb.append(' ').append(r.model) }
    if (showPrice) {
        sb.append(if (showModel) " · " else " ")
        if (r.row != null && !showChannelPrice) sb.append("客")
        sb.append(priceFor(r))
    }
    return sb.toString()
}

// ---------- 识别模型选择对话框 ----------

@Composable
private fun ModelPickerDialog(
    presets: List<String>,
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择识别模型", fontSize = 16.sp) },
        text = {
            Column {
                Text("预设模型（点选即切换）", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                presets.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (m == current) Accent.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(m) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (m == current) "● " else "○ ",
                            color = if (m == current) Accent else TextSecondary,
                            fontSize = 12.sp,
                        )
                        Text(m, fontSize = 13.sp, color = TextPrimary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("自定义模型 ID", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    placeholder = { Text("粘贴其他模型 ID", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (custom.isNotBlank()) onSelect(custom) else onDismiss()
            }) { Text("确定", fontSize = 14.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 14.sp) }
        },
    )
}

// ---------- 相似机型候选弹层 ----------

@Composable
private fun CandidateSheet(
    index: Int,
    candidates: List<ModelRow>,
    onPick: (ModelRow) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("选择相似机型（第 ${index + 1} 台）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
        }
        if (candidates.isEmpty()) {
            Text("加载中...", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(candidates) { _, m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(m) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.model, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                "${m.brand} · ${m.category}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("¥${m.price}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriceRed)
                    }
                }
            }
        }
    }
}
