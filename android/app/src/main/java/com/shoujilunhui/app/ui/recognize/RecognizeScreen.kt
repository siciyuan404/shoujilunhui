@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shoujilunhui.app.ui.recognize

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.toDp
import androidx.compose.ui.unit.toPx
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
    vm: RecognizeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.onImagePicked(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(vm::onImagePicked)
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
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
            // 预览 + 图上标注
            item { PreviewWithOverlay(ui) }

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
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("从相册选择", fontSize = 13.sp) }
                }
            }

            // 识别
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
                itemsIndexed(ui.results) { index, r ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 编号与图上标注一一对应
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(if (r.row != null) MatchGreen else MatchOrange, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
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
                            Text(
                                r.row?.let { "¥${it.price}" } ?: "—",
                                color = if (r.row != null) PriceRed else TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- 预览 + 图上标注 ----------

@Composable
private fun PreviewWithOverlay(ui: RecognizeUiState) {
    val uri = ui.previewUri
    if (uri == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Color(0xFFE9EDF2), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("拍照或从相册选择手机照片", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFE9EDF2), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { 400.dp.toPx() }
        val imgSize = ui.imageSize
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
            if (ui.results.isNotEmpty() && ui.annotate) {
                RecognitionOverlay(
                    results = ui.results,
                    showPrice = ui.annotatePrice,
                    showModel = ui.annotateModel,
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
            val label = buildAnnotationLabel(i, r, showPrice, showModel)
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

private fun buildAnnotationLabel(i: Int, r: RecognizeResult, showPrice: Boolean, showModel: Boolean): String {
    val sb = StringBuilder().append(i + 1)
    if (showModel) { sb.append(' ').append(r.model) }
    if (showPrice) {
        sb.append(if (showModel) " · " else " ")
        sb.append(if (r.row != null) "${r.row!!.price}元" else "未收录")
    }
    return sb.toString()
}
