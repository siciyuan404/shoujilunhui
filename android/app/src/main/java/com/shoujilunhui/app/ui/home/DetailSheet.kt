@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.shoujilunhui.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.Danger
import com.shoujilunhui.app.ui.theme.PriceBg
import com.shoujilunhui.app.ui.theme.PriceRed
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary

// ---------- 详情底部弹层（紧凑） ----------

@Composable
fun DetailSheet(
    row: ModelRow,
    baseUrl: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // 标题 + 价格
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(row.model, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("${row.brand} · ${row.category}", fontSize = 12.sp, color = TextSecondary)
            }
            Surface(color = PriceBg, shape = RoundedCornerShape(6.dp)) {
                Text(
                    if (row.price.isNotBlank()) "¥${row.price}" else "面议",
                    color = PriceRed, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        // 图片轮播
        val imgs = row.images?.filter { it.isNotBlank() }.orEmpty()
        if (imgs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val pagerState = rememberPagerState(pageCount = { imgs.size })
            Box {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEEEEEE)),
                ) { page ->
                    AsyncImage(
                        model = fullImageUrl(baseUrl, imgs[page]),
                        contentDescription = "${row.model} 图${page + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onImageClick(imgs, page) },
                    )
                }
                if (imgs.size > 1) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(imgs.size) { i ->
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(
                                        if (i == pagerState.currentPage) Accent else Color.White.copy(alpha = 0.75f),
                                        CircleShape,
                                    )
                            )
                        }
                    }
                }
            }
        }

        // 参数
        Spacer(Modifier.height(10.dp))
        SectionTitle("详细参数")
        val specs = listOf(
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
            "系统" to row.os,
        ).filter { !it.second.isNullOrBlank() }
        if (specs.isEmpty()) {
            Text("待补充", fontSize = 12.sp, color = TextSecondary)
        } else {
            specs.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
                    Text(k, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(68.dp))
                    Text(v ?: "", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        // 内存版本报价
        val variants = row.variants ?: emptyList()
        if (variants.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionTitle("内存版本报价")
            variants.forEach { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
                    Text(v.spec, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("¥${v.price}", fontSize = 13.sp, color = PriceRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 备注
        Spacer(Modifier.height(10.dp))
        SectionTitle("备注")
        Text(
            if (row.note.isNullOrBlank()) "无" else row.note,
            fontSize = 12.sp, color = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text("更新于 ${row.updatedAt ?: "-"}", fontSize = 10.5.sp, color = TextSecondary)

        // 操作
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                border = androidx.compose.foundation.BorderStroke(1.dp, Danger),
            ) { Text("删除", fontSize = 13.sp) }
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("编辑价格/备注", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

// ---------- 全屏看图 ----------

@Composable
fun ImageViewerDialog(
    urls: List<String>,
    startIndex: Int,
    baseUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize().clickable { onDismiss() }) {
                val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { urls.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = fullImageUrl(baseUrl, urls[page]),
                        contentDescription = "图片 ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Text(
                        "${pagerState.currentPage + 1}/${urls.size}",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

// ---------- 筛选面板（紧凑） ----------

@Composable
fun FilterSheet(
    cpu: String, year: String, cameraMin: Int,
    onApply: (String, String, Int) -> Unit,
    onReset: () -> Unit,
) {
    var selCpu by remember { mutableStateOf(cpu) }
    var selYear by remember { mutableStateOf(year) }
    var selCam by remember { mutableStateOf(HomeViewModel.cameraOption(cameraMin)) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Text("规格筛选", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FilterGroup("CPU 品牌", HomeViewModel.CPU_OPTIONS, selCpu) { selCpu = it }
        FilterGroup("上市年份", HomeViewModel.YEAR_OPTIONS, selYear) { selYear = it }
        FilterGroup("后置主摄", HomeViewModel.CAMERA_OPTIONS, selCam) { selCam = it }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("重置", fontSize = 13.sp) }
            Button(
                onClick = { onApply(selCpu, selYear, HomeViewModel.cameraValue(selCam)) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("完成", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun FilterGroup(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Text(title, fontSize = 12.sp, color = TextSecondary)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt, fontSize = 12.sp) },
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(15.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ---------- 添加 / 编辑 / 删除对话框（紧凑） ----------

@Composable
fun AddModelDialog(
    defaultBrand: String,
    onDismiss: () -> Unit,
    onAdd: (brand: String, category: String, model: String, price: String, note: String) -> Unit,
) {
    var brand by remember { mutableStateOf(defaultBrand) }
    var category by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加型号", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogField(brand, { brand = it }, "品牌 *")
                DialogField(category, { category = it }, "分类 *")
                DialogField(model, { model = it }, "型号 *")
                DialogField(price, { price = it }, "回收价（元）")
                DialogField(note, { note = it }, "备注")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (brand.isBlank() || category.isBlank() || model.isBlank()) {
                    error = "品牌/分类/型号必填"
                } else {
                    onAdd(brand.trim(), category.trim(), model.trim(), price.trim(), note.trim())
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun EditModelDialog(
    row: ModelRow,
    onDismiss: () -> Unit,
    onSave: (price: String, note: String) -> Unit,
) {
    var price by remember { mutableStateOf(row.price) }
    var note by remember { mutableStateOf(row.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑「${row.model}」", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogField(price, { price = it }, "回收价（元）")
                DialogField(note, { note = it }, "备注")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(price.trim(), note.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun DeleteConfirmDialog(
    row: ModelRow,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除型号", fontSize = 16.sp) },
        text = { Text("确定删除「${row.model}」？", fontSize = 13.sp) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Danger),
            ) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
