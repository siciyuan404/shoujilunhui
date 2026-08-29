@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shoujilunhui.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 17.sp) },
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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle("服务器") }
            item {
                Field(
                    value = url,
                    onChange = { url = it },
                    label = "服务器地址",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            item {
                Field(
                    value = key,
                    onChange = { key = it },
                    label = "管理 API Key（增删改需要）",
                    password = true,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.test(url.trim()) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("测试连接", fontSize = 13.sp) }
                    Spacer(Modifier.weight(1f))
                }
            }
            testResult?.let { (ok, text) ->
                item {
                    Text(
                        text,
                        color = if (ok) Accent else Danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            item { SectionTitle("拍照识别（豆包视觉）") }
            item {
                Field(value = arkKey, onChange = { arkKey = it }, label = "豆包 API Key", password = true)
            }
            item {
                Field(value = arkModel, onChange = { arkModel = it }, label = "视觉模型 ID")
            }

            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { vm.save(url, key, arkKey, arkModel) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("保存", fontSize = 14.sp) }
            }
            item {
                Text(
                    "版本 v${BuildConfig.VERSION_NAME}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

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
            .height(56.dp),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
