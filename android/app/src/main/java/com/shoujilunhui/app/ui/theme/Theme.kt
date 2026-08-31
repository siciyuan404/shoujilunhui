package com.shoujilunhui.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/** 品牌色板（沿用原 XML 配色） */
val Accent = Color(0xFF1677FF)
val AccentDark = Color(0xFF0958D9)
val AccentSoft = Color(0xFFE6F0FF)
val BgGray = Color(0xFFF0F2F5)
val TextPrimary = Color(0xFF1A1A2E)
val TextSecondary = Color(0xFF8C8C8C)
val PriceRed = Color(0xFFCF1322)
val PriceBg = Color(0xFFFFF1F0)
val Danger = Color(0xFFD4380D)

/** 识别图上标注：已匹配报价（绿）/ 未收录（橙） */
val MatchGreen = Color(0xFF389E0D)
val MatchOrange = Color(0xFFFA8C16)

private val Scheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentDark,
    secondary = AccentDark,
    onSecondary = Color.White,
    background = BgGray,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF5F6F8),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFECECEC),
    error = Danger,
    onError = Color.White,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.statusBarColor = Accent.toArgb()
        }
    }
    MaterialTheme(colorScheme = Scheme, content = content)
}
