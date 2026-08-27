package com.shoujilunhui.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.shoujilunhui.app.data.ApiClient
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val etUrl = findViewById<TextInputEditText>(R.id.etUrl)
        val etKey = findViewById<TextInputEditText>(R.id.etKey)
        val etArkKey = findViewById<TextInputEditText>(R.id.etArkKey)
        val etArkModel = findViewById<TextInputEditText>(R.id.etArkModel)
        etUrl.setText(prefs.getString("baseUrl", ""))
        etKey.setText(prefs.getString("apiKey", ""))
        etArkKey.setText(prefs.getString("arkApiKey", ""))
        etArkModel.setText(prefs.getString("arkModel", RecognizeActivity.DEFAULT_ARK_MODEL))

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "请填写服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val h = ApiClient.api(url).health()
                    findViewById<TextView>(R.id.tvTestResult).text = "✓ 连接成功，收录 ${h.models} 款机型"
                } catch (e: Exception) {
                    findViewById<TextView>(R.id.tvTestResult).text = "✗ 连接失败：${e.message}"
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "服务器地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString("baseUrl", url)
                .putString("apiKey", etKey.text.toString().trim())
                .putString("arkApiKey", etArkKey.text.toString().trim())
                .putString("arkModel", etArkModel.text.toString().trim().ifBlank { RecognizeActivity.DEFAULT_ARK_MODEL })
                .apply()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
