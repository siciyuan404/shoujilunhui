package com.shoujilunhui.app

import android.content.Context

/**
 * 统一封装 SharedPreferences("config") 读写。
 * key 与旧版完全一致，升级后原配置不丢失。
 */
class ConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("config", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", "") ?: ""
        set(v) = prefs.edit().putString("baseUrl", v).apply()

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) = prefs.edit().putString("apiKey", v).apply()

    var arkApiKey: String
        get() = prefs.getString("arkApiKey", "") ?: ""
        set(v) = prefs.edit().putString("arkApiKey", v).apply()

    var arkModel: String
        get() = prefs.getString("arkModel", "") ?: ""
        set(v) = prefs.edit().putString("arkModel", v).apply()

    /** 识别服务商：ark（豆包/火山方舟）或 deepseek */
    var recProvider: String
        get() = prefs.getString("recProvider", "ark") ?: "ark"
        set(v) = prefs.edit().putString("recProvider", v).apply()

    /** DeepSeek API Key（api.deepseek.com） */
    var deepseekApiKey: String
        get() = prefs.getString("deepseekApiKey", "") ?: ""
        set(v) = prefs.edit().putString("deepseekApiKey", v).apply()

    /** DeepSeek 视觉模型 ID，默认 deepseek-v4-flash-vision-exp */
    var deepseekModel: String
        get() = prefs.getString("deepseekModel", "") ?: ""
        set(v) = prefs.edit().putString("deepseekModel", v).apply()

    /** 识别结果图上标注开关（默认开启） */
    var annotate: Boolean
        get() = prefs.getBoolean("annotate", true)
        set(v) = prefs.edit().putBoolean("annotate", v).apply()

    /** 标注中显示价格 */
    var annotatePrice: Boolean
        get() = prefs.getBoolean("annotatePrice", true)
        set(v) = prefs.edit().putBoolean("annotatePrice", v).apply()

    /** 标注中显示型号 */
    var annotateModel: Boolean
        get() = prefs.getBoolean("annotateModel", true)
        set(v) = prefs.edit().putBoolean("annotateModel", v).apply()

    /** 用于检测从设置页返回后配置是否变化 */
    fun signature(): String = "$baseUrl|$apiKey|$arkApiKey|$arkModel|$annotate|$annotatePrice|$annotateModel"
}
