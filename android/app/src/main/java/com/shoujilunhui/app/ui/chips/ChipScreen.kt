@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shoujilunhui.app.ui.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoujilunhui.app.data.ChipRow
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.BgGray
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary

@Composable
fun ChipScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: ChipViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (vm.baseUrl.isBlank()) onBack() else vm.load()
    }

    Scaffold(
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("存储芯片", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (ui.loaded) "共 ${ui.total} 条" else "芯片库对照 (UMCP/eMCP/UFS/eMMC/LPDDR)",
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 搜索 + 筛选
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = ui.search,
                    onValueChange = vm::onSearchChange,
                    modifier = Modifier.weight(1f).height(42.dp),
                    textStyle = TextStyle(fontSize = 13.sp),
                    placeholder = { Text("搜料号 / 厂商 / 封装，如 KMJS9001", fontSize = 12.sp, color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (ui.search.isNotEmpty()) {
                            IconButton(onClick = { vm.onSearchChange("") }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(15.dp), tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgGray,
                        unfocusedContainerColor = BgGray,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipFilterDropdown(
                    label = "类别",
                    options = ui.meta?.classes ?: emptyList(),
                    selected = ui.chipClass,
                    onSelect = { vm.onFilter(chipClass = it) },
                    modifier = Modifier.weight(1f),
                )
                ChipFilterDropdown(
                    label = "厂商",
                    options = ui.meta?.vendors ?: emptyList(),
                    selected = ui.vendor,
                    onSelect = { vm.onFilter(vendor = it) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipFilterDropdown(
                    label = "ROM",
                    options = ui.meta?.romSizes ?: emptyList(),
                    selected = ui.romSize,
                    onSelect = { vm.onFilter(romSize = it) },
                    modifier = Modifier.weight(1f),
                )
                ChipFilterDropdown(
                    label = "RAM",
                    options = ui.meta?.ramSizes ?: emptyList(),
                    selected = ui.ramSize,
                    onSelect = { vm.onFilter(ramSize = it) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = false,
                    onClick = vm::resetFilters,
                    label = { Text("清空", fontSize = 12.sp) },
                    shape = RoundedCornerShape(18.dp),
                )
            }

            // 列表
            Box(Modifier.fillMaxSize()) {
                when {
                    ui.error != null -> Text(
                        ui.error!!,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    ui.loading && ui.chips.isEmpty() -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(30.dp),
                        strokeWidth = 2.5.dp,
                    )
                    ui.chips.isEmpty() -> Text(
                        "未找到匹配的芯片料号",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ui.chips, key = { it.id }) { chip ->
                            ChipRowCard(chip = chip, onClick = { onOpenDetail(chip.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRowCard(chip: ChipRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chip.vendorPnp ?: "-",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chip.refCount > 0) {
                    Surface(color = Accent, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${chip.refCount} 台引用",
                            fontSize = 10.5.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(chip.chipClass, chip.vendor).joinToString(" · "),
                fontSize = 11.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    chip.packageName?.let { "封装 $it" },
                    listOfNotNull(chip.romType, chip.romSize).joinToString(" ").ifBlank { null }?.let { "ROM $it" },
                    listOfNotNull(chip.ramType, chip.ramSize).joinToString(" ").ifBlank { null }?.let { "RAM $it" },
                ).joinToString("  "),
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChipFilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = selected != "全部",
            onClick = { expanded = true },
            label = { Text(if (selected == "全部") "$label 全部" else selected, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            shape = RoundedCornerShape(18.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf("全部") + options).distinct().forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}
