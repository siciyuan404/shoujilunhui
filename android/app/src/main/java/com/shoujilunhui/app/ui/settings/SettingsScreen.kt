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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoujilunhui.app.BuildConfig
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

            // 拍照识别设置组
            item {
                SettingsGroup(title = "拍照识别（豆包视觉）") {
                    Field(value = arkKey, onChange = { arkKey = it }, label = "豆包 API Key", password = true)
                    Spacer(Modifier.height(8.dp))
                    Field(value = arkModel, onChange = { arkModel = it }, label = "视觉模型 ID")
                }
            }

            // 保存按钮
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { vm.save(url, key, arkKey, arkModel) },
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
