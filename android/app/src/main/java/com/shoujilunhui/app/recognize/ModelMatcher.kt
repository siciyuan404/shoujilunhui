package com.shoujilunhui.app.recognize

/**
 * 型号智能匹配：把识别结果与报价库命名对齐（逻辑与原 RecognizeActivity 完全一致）。
 *
 * 报价库命名规则：华为/小米/OPPO 等不带品牌（"P40pro"）、荣耀带品牌（"荣耀9"）、苹果用"苹果"（"苹果13pro"）。
 * 识别模型常返回带品牌/空格/大小写的全名（"华为P40 Pro"、"iPhone 15 Pro"），需归一化后多级匹配。
 */
object ModelMatcher {

    private val BRAND_PREFIXES = listOf(
        "华为", "荣耀", "小米", "红米", "苹果", "iphone", "oppo", "vivo", "一加", "真我",
        "realme", "三星", "魅族", "努比亚", "金立", "联想", "360", "美图", "锤子", "乐视",
        "谷歌", "google", "智选", "鼎桥", "华硕", "huawei", "honor", "xiaomi", "samsung",
        "meizu", "oneplus", "nubia", "vivo", "iqoo"
    )

    private val STRIP_WORDS = listOf(
        "256g", "512g", "128g", "64g", "32g", "16g", "1tb",
        "黑色", "白色", "蓝色", "金色", "灰色", "银色", "紫色", "绿色", "红色", "粉色",
        "二手", "国行", "港版", "美版", "全新", "拆封", "未拆封", "标配", "高配", "低配"
    )

    fun normalizeModel(s: String): String {
        var t = s.trim().lowercase()
            .replace(Regex("[\\s_\\-·.．/（）()【】\\[\\]{}:：,，;；\"'`~！!?？*+&]"), "")
        for (w in STRIP_WORDS) t = t.replace(w, "")
        return t
    }

    private fun stripBrandPrefix(normalized: String): String {
        for (b in BRAND_PREFIXES) {
            if (normalized.startsWith(b)) return normalized.removePrefix(b)
        }
        return normalized
    }

    /** iPhone/苹果 → 苹果（报价库苹果用"苹果"命名） */
    private fun mapBrand(normalized: String): String = when {
        normalized.startsWith("iphone") -> "苹果" + normalized.removePrefix("iphone")
        normalized.startsWith("苹果") -> normalized
        else -> normalized
    }

    /** 核心系列词："畅享9plus"->畅享9，"p40pro"->p40，"苹果15promax"->苹果15 */
    private fun coreKeys(normalized: String): List<String> {
        val keys = mutableListOf<String>()
        Regex("([\\u4e00-\\u9fa5]+\\d+)").findAll(normalized).forEach { keys.add(it.value) }
        Regex("([a-z]+\\d+)").findAll(normalized).forEach { keys.add(it.value) }
        return keys.distinct().filter { it.isNotBlank() }
    }

    /** 生成有序匹配候选（越靠前越精确） */
    fun buildCandidates(raw: String): List<String> {
        val norm = normalizeModel(raw)
        val list = linkedSetOf<String>()
        list.add(norm)                                 // 1. 原词归一化
        val mapped = mapBrand(norm)                    // 2. iPhone→苹果
        if (mapped != norm) list.add(mapped)
        val stripped = stripBrandPrefix(norm)          // 3. 去品牌前缀
        if (stripped.isNotBlank() && stripped !in list) list.add(stripped)
        val strippedMapped = mapBrand(stripped)        // 4. 去品牌后再映射
        if (strippedMapped.isNotBlank() && strippedMapped !in list) list.add(strippedMapped)
        coreKeys(norm).forEach { if (it !in list) list.add(it) }   // 5. 核心系列词兜底
        return list.toList()
    }
}
