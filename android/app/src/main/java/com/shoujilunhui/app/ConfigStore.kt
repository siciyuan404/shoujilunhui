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
