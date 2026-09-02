@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shoujilunhui.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoujilunhui.app.BuildConfig
import com.shoujilunhui.app.UpdateState
import com.shoujilunhui.app.ui.theme.Accent
import com.shoujilunhui.app.ui.theme.BgGray
import com.shoujilunhui.app.ui.theme.Danger
import com.shoujilunhui.app.ui.theme.TextPrimary
import com.shoujilunhui.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val testResult by vm.testResult.collectAsState()
    val saved by vm.saved.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var url by remember { mutableStateOf(vm.initialUrl) }
    var key by remember { mutableStateOf(vm.initialApiKey) }
    var arkKey by remember { mutableStateOf(vm.initialArkKey) }
    var arkModel by remember { mutableStateOf(vm.initialArkModel) }
    var provider by remember { mutableStateOf(vm.initialProvider) }
    var deepseekKey by remember { mutableStateOf(vm.initialDeepseekKey) }
    var deepseekModel by remember { mutableStateOf(vm.initialDeepseekModel) }
    var priceRatioText by remember { mutableStateOf(vm.initialPriceRatio.toString()) }
    var annotate by remember { mutableStateOf(vm.initialAnnotate) }
    var annotatePrice by remember { mutableStateOf(vm.initialAnnotatePrice) }
    var annotateModel by remember { mutableStateOf(vm.initialAnnotateModel) }

    LaunchedEffect(saved) {
        if (saved) {
            snackbar.showSnackbar("已保存")
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 17.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 服务器设置组
            item {
                SettingsGroup(title = "服务器") {
                    Field(
                        value = url,
                        onChange = { url = it },
                        label = "服务器地址",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(8.dp))
                    Field(
                        value = key,
                        onChange = { key = it },
                        label = "管理 API Key（增删改需要）",
                        password = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.test(url.trim()) },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("测试连接", fontSize = 13.sp) }
                    }
                    testResult?.let { (ok, text) ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text,
                            color = if (ok) Accent else Danger,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // 拍照识别设置组（支持豆包方舟 / DeepSeek 双服务商）
            item {
                SettingsGroup(title = "拍照识别") {
                    Text("识别服务商", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderButton(
                            label = "豆包方舟",
                            selected = provider == "ark",
                            onClick = { provider = "ark" },
                        )
                        ProviderButton(
                            label = "DeepSeek",
                            selected = provider == "deepseek",
                            onClick = { provider = "deepseek" },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (provider == "deepseek") {
                        Field(value = deepseekKey, onChange = { deepseekKey = it }, label = "DeepSeek API Key", password = true)
                        Spacer(Modifier.height(8.dp))
                        Field(value = deepseekModel, onChange = { deepseekModel = it }, label = "视觉模型 ID（默认 deepseek-v4-flash-vision-exp）")
                    } else {
                        Field(value = arkKey, onChange = { arkKey = it }, label = "豆包 API Key（火山方舟）", password = true)
                        Spacer(Modifier.height(8.dp))
                        Field(value = arkModel, onChange = { arkModel = it }, label = "视觉模型 ID（默认 doubao-seed-character-260628）")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("客户报价比例", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Field(
                        value = priceRatioText,
                        onChange = { priceRatioText = it.filter(Char::isDigit).take(3) },
                        label = "展示给客户的报价 = 渠道价 × 比例（默认 60，可填 50/60/70）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            // 识别结果标注设置组
            item {
                SettingsGroup(title = "识别结果标注") {
                    ToggleRow(
                        title = "在图片上标注位置",
                        desc = "在预览图上绘制识别框，一眼看清每台手机的位置",
                        checked = annotate,
                        onCheckedChange = { annotate = it },
                    )
                    ToggleRow(
                        title = "标注中显示价格",
                        desc = "识别框上直接显示该机型的回收价",
                        checked = annotatePrice,
                        onCheckedChange = { annotatePrice = it },
                    )
                    ToggleRow(
                        title = "标注中显示型号",
                        desc = "识别框上显示匹配到的机型名称",
                        checked = annotateModel,
                        onCheckedChange = { annotateModel = it },
                    )
                }
            }

            // 版本更新（主动更新，对齐 MeowMic 手机端：检查→下载→安装）
            item {
                SettingsGroup(title = "版本更新") {
                    Text(
                        "当前版本 v${vm.currentVersion}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    UpdateSection(vm)
                }
            }

            // 预留分区：后续设置持续扩展
            item {
                SettingsGroup(title = "更多设置（即将上线）") {
                    Text(
                        "界面显示 / 数据管理 等分区将在此按分组卡片持续扩展",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp,
                    )
                }
            }

            // 保存按钮
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { vm.save(url, key, arkKey, arkModel, provider, deepseekKey, deepseekModel, priceRatioText.toIntOrNull() ?: 60, annotate, annotatePrice, annotateModel) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("保存", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }

            // 版本号
            item {
                Text(
                    "版本 v${BuildConfig.VERSION_NAME}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ---------- 分组卡片 ----------

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            content()
        }
    }
}

// ---------- 开关行 ----------

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------- 输入框 ----------

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFFAFAFA),
            unfocusedContainerColor = Color(0xFFFAFAFA),
        ),
    )
}

// ---------- 服务商选择按钮 ----------

@Composable
private fun ProviderButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(10.dp),
        ) { Text(label, fontSize = 13.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(10.dp),
        ) { Text(label, fontSize = 13.sp, color = TextSecondary) }
    }
}

// ---------- 版本更新区（主动更新） ----------

@Composable
private fun UpdateSection(vm: SettingsViewModel) {
    val state by vm.updateState.collectAsState()
    // 进入设置页时若尚未检查过，自动检查一次展示最新状态；用户也可手动「检查更新」
    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle) vm.checkForUpdate()
    }
    when (val s = state) {
        is UpdateState.Idle -> {
            Button(
                onClick = { vm.checkForUpdate() },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("检查更新", fontSize = 13.5.sp) }
        }
        is UpdateState.Checking -> {
            Text("正在检查更新…", fontSize = 12.5.sp, color = TextSecondary)
        }
        is UpdateState.Available -> {
            Text(
                "发现新版本 v${s.version}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.downloadUpdate() },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("下载更新 v${s.version}", fontSize = 13.5.sp) }
        }
        is UpdateState.Downloading -> {
            Text("正在下载… ${s.progress}%", fontSize = 12.5.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { s.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is UpdateState.ReadyToInstall -> {
            Text("下载完成，可立即安装", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Accent)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.installUpdate() },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("立即安装", fontSize = 13.5.sp) }
        }
        is UpdateState.UpToDate -> {
            Text("已是最新版本", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.checkForUpdate() },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("重新检查", fontSize = 13.sp) }
        }
        is UpdateState.Error -> {
            Text("更新失败：${s.message}", fontSize = 12.sp, color = Danger)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.checkForUpdate() },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("重试", fontSize = 13.sp) }
        }
    }
}
