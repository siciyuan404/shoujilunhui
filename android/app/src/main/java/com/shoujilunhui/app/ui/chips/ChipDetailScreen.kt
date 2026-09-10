@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.shoujilunhui.app.ui.chips

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoujilunhui.app.data.ChipRefModel
import com.shoujilunhui.app.data.ModelRow
import com.shoujilunhui.app.ui.home.DetailSheet
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.BgGray
import com.shoujilunhui.app.ui.theme.PriceBg
import com.shoujilunhui.app.ui.theme.PriceRed
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary

@Composable
fun ChipDetailScreen(
    chipId: Long,
    onBack: () -> Unit,
    vm: ChipViewModel = viewModel(),
) {
    val state by vm.detail.collectAsState()
    var showModel by remember { mutableStateOf<ChipRefModel?>(null) }

    androidx.compose.runtime.LaunchedEffect(chipId) {
        vm.clearDetail()
        vm.loadDetail(chipId)
    }

    Scaffold(
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = {
                    Text("芯片详情", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    strokeWidth = 2.5.dp,
                )
                state.error != null -> Text(
                    state.error!!,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.detail != null -> {
                    val d = state.detail!!
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            ChipSpecCard(
                                pnp = d.vendorPnp,
                                chipClass = d.chipClass,
                                vendor = d.vendor,
                                packageName = d.packageName,
                                romType = d.romType,
                                romSize = d.romSize,
                                ramType = d.ramType,
                                ramSize = d.ramSize,
                                image = d.image,
                                baseUrl = vm.baseUrl,
                                refCount = d.models.size,
                            )
                        }
                        item {
                            Text(
                                "📱 引用该芯片的机型（${d.models.size}）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                            )
                        }
                        items(d.models, key = { it.id }) { m ->
                            ChipRefRow(m = m, onClick = { showModel = m })
                        }
                        item { Spacer(Modifier.height(20.dp).navigationBarsPadding()) }
                    }
                }
            }
        }
    }

    showModel?.let { m ->
        val row = ModelRow(
            id = m.id,
            brand = m.brand,
            category = m.category,
            model = m.model,
            price = m.price,
            note = null,
            modelCode = null,
            releaseDate = null,
            cpuBrand = null,
            cpuModel = null,
            ram = m.ram,
            rom = m.rom,
            backCamera = null,
            frontCamera = null,
            screenSize = null,
            screenType = null,
            refresh = null,
            battery = null,
            charge = null,
            network = null,
            os = null,
            images = null,
            variants = null,
            createdAt = null,
            updatedAt = null,
        )
        ModalBottomSheet(
            onDismissRequest = { showModel = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetailSheet(
                row = row,
                baseUrl = vm.baseUrl,
                onEdit = {},
                onDelete = {},
                onImageClick = { _, _ -> },
                showActions = false,
            )
        }
    }
}

@Composable
private fun ChipSpecCard(
    pnp: String?,
    chipClass: String?,
    vendor: String?,
    packageName: String?,
    romType: String?,
    romSize: String?,
    ramType: String?,
    ramSize: String?,
    image: String?,
    baseUrl: String,
    refCount: Int,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(pnp ?: "-", fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
            if (refCount > 0) {
                Spacer(Modifier.height(4.dp))
                Surface(color = Accent, shape = RoundedCornerShape(8.dp)) {
                    Text("被 $refCount 款机型引用", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            if (!image.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                val imgUrl = if (image.startsWith("http")) image else baseUrl.trimEnd('/') + "/" + image.trimStart('/')
                AsyncImage(
                    model = imgUrl,
                    contentDescription = pnp,
                    modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(10.dp))
            SpecRow("类别", chipClass)
            SpecRow("厂商", vendor)
            SpecRow("封装", packageName)
            SpecRow("ROM 类型", romType)
            SpecRow("ROM 容量", romSize)
            SpecRow("RAM 类型", ramType)
            SpecRow("RAM 容量", ramSize)
            Spacer(Modifier.height(6.dp))
            Text("注：同一机型不同批次可能采用不同厂商芯片，此对照为容量匹配的参考值。", fontSize = 10.5.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SpecRow(k: String, v: String?) {
    if (v.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.width(84.dp))
        Text(v, fontSize = 12.5.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChipRefRow(m: ChipRefModel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${m.brand} · ${m.model}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        listOfNotNull(m.rom, m.ram).joinToString(" + ").ifBlank { null },
                        m.category.takeIf { it.isNotBlank() && it != m.brand },
                    ).joinToString("  ·  "),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (m.price.isNotBlank()) {
                Surface(color = PriceBg, shape = RoundedCornerShape(6.dp)) {
                    Text("¥${m.price}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriceRed, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}
