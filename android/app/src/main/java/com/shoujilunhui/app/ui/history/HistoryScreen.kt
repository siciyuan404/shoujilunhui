@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shoujilunhui.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shoujilunhui.app.HistoryEntry
import com.shoujilunhui.app.HistoryItem
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.MatchGreen
import com.shoujilunhui.app.ui.theme.MatchOrange
import com.shoujilunhui.app.ui.theme.PriceRed
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- 历史列表 ----------

@Composable
fun HistoryListScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val entries by vm.entries.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("识别历史", fontSize = 17.sp) },
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
        if (entries.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无历史识别记录", color = TextSecondary, fontSize = 13.sp)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries) { e ->
                HistoryRow(e, onClick = { onOpenDetail(e.id) })
            }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(e.createdAt)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    "${e.imageCount} 张图 · 识别 ${e.phoneCount} 台（匹配 ${e.matchedCount}）",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    "客户总价 ¥${fmt(e.customerTotal)}（比例 ${e.ratio}%） · 渠道 ¥${fmt(e.channelTotal)}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "¥${fmt(e.customerTotal)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PriceRed,
            )
        }
    }
}

// ---------- 历史详情 ----------

@Composable
fun HistoryDetailScreen(
    entryId: Long,
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val detail by vm.detail.collectAsState()
    LaunchedEffect(entryId) { vm.loadDetail(entryId) }
    val e = detail
    if (e == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("记录不存在或已删除", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("识别详情", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.delete(e.id); onBack() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.White)
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF3E3)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(e.createdAt)),
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("客户总价 ¥${fmt(e.customerTotal)}（比例 ${e.ratio}%）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PriceRed)
                        Text("渠道总价 ¥${fmt(e.channelTotal)} · ${e.imageCount} 张图 · 识别 ${e.phoneCount} 台（匹配 ${e.matchedCount}）", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // 每张图片 + 该图标注 + 该图结果
            (0 until e.imageCount).forEach { imgIdx ->
                item {
                    Text("第 ${imgIdx + 1} 张", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                val imgItems = e.items.filter { it.imageIndex == imgIdx }
                item {
                    HistoryImageCard(e, imgIdx, imgItems)
                }
                item {
                    imgItems.forEach { it ->
                        HistoryItemRow(it)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryImageCard(e: HistoryEntry, imgIdx: Int, items: List<HistoryItem>) {
    val file = File(e.dir, "img_$imgIdx.jpg")
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFE9EDF2), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "第${imgIdx + 1}张",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("图片缺失", color = TextSecondary, fontSize = 12.sp)
            }
            if (items.isNotEmpty()) {
                HistoryOverlay(items, Modifier.fillMaxWidth().matchParentSize())
            }
        }
    }
}

/** 在历史图片上绘制识别框 + 顶部标签条（编号 + 型号） */
@Composable
private fun HistoryOverlay(items: List<HistoryItem>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        items.forEach { it ->
            val b = it.box ?: return@forEach
            val l = b.x1 / 1000f * size.width
            val t = b.y1 / 1000f * size.height
            val w = (b.x2 - b.x1) / 1000f * size.width
            val h = (b.y2 - b.y1) / 1000f * size.height
            if (w <= 0f || h <= 0f) return@forEach
            val color = if (it.matched) MatchGreen else MatchOrange
            drawRect(color, topLeft = Offset(l, t), size = Size(w, h), style = Stroke(width = 3f))
            val label = "${it.seq} ${it.model}"
            val style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val layout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = style,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (w - 10f).roundToInt().coerceAtLeast(1)),
            )
            val headerH = layout.size.height.toFloat() + 10f
            drawRect(color, topLeft = Offset(l, t), size = Size(w, headerH))
            drawText(layout, topLeft = Offset(l + 5f, t + 5f))
        }
    }
}

@Composable
private fun HistoryItemRow(it: HistoryItem) {
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
            Box(
                Modifier
                    .size(26.dp)
                    .background(if (it.matched) MatchGreen else MatchOrange, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${it.seq}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(it.model, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
                Text(
                    if (it.matched) "${it.brand} · ${it.category}" else "未收录该型号",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    it.customerPrice?.let { "¥${fmt(it)}" } ?: "—",
                    color = if (it.matched) PriceRed else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (it.channelPrice != null) {
                    Text("渠道 ¥${fmt(it.channelPrice)}", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
