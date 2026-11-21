package com.subnook.app.ui.theme

import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 暖纸墨绿 · 极简账本风（Light）：
 * 温润米白纸底 + 象牙白卡片细描边 + 深松石墨绿主色 + 暖琥珀赤陶点缀
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF165B4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBE5),
    onPrimaryContainer = Color(0xFF0D3A30),
    secondary = Color(0xFF7B6B5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7DC),
    onSecondaryContainer = Color(0xFF2B251F),
    tertiary = Color(0xFFC26D2D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E5D4),
    onTertiaryContainer = Color(0xFF4A2508),
    background = Color(0xFFF5F2EB),
    onBackground = Color(0xFF1B1F1D),
    surface = Color(0xFFF5F2EB),
    onSurface = Color(0xFF1B1F1D),
    surfaceVariant = Color(0xFFECE7DD),
    onSurfaceVariant = Color(0xFF636964),
    surfaceContainerLowest = Color(0xFFFFFEFC),
    surfaceContainerLow = Color(0xFFFAF7F2),
    surfaceContainer = Color(0xFFEEEAE1),
    surfaceContainerHigh = Color(0xFFE5E0D5),
    surfaceContainerHighest = Color(0xFFDBD5C7),
    outline = Color(0xFFB5B0A4),
    outlineVariant = Color(0xFFE4DFD4),
    error = Color(0xFFC8433A),
    onError = Color.White,
)

/**
 * 深松烟夜色（Dark）：
 * 沉静深墨绿灰底 + 温润羊皮纸文字 + 玉石青绿主色 + 暖琥珀点缀
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6BC4AD),
    onPrimary = Color(0xFF0A2922),
    primaryContainer = Color(0xFF1A463B),
    onPrimaryContainer = Color(0xFFCFEFE6),
    secondary = Color(0xFFB3A595),
    onSecondary = Color(0xFF241E18),
    secondaryContainer = Color(0xFF282D2A),
    onSecondaryContainer = Color(0xFFE6E1D8),
    tertiary = Color(0xFFE59B5D),
    onTertiary = Color(0xFF3A1D05),
    background = Color(0xFF101412),
    onBackground = Color(0xFFEBE7DF),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFEBE7DF),
    surfaceVariant = Color(0xFF1E2421),
    onSurfaceVariant = Color(0xFF9AA39C),
    surfaceContainerLowest = Color(0xFF161B18),
    surfaceContainerLow = Color(0xFF1B211E),
    surfaceContainer = Color(0xFF212824),
    surfaceContainerHigh = Color(0xFF29322D),
    surfaceContainerHighest = Color(0xFF323C37),
    outline = Color(0xFF47524C),
    outlineVariant = Color(0xFF28312C),
    error = Color(0xFFF07167),
    onError = Color(0xFF3A0E0A),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Immutable
data class ExtraColors(
    val heroBrush: Brush,
    val accentBrush: Brush,
    val cardShadow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dark: Boolean,
)

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(
        Brush.linearGradient(listOf(Color.Black, Color.Black)),
        Brush.linearGradient(listOf(Color.Black, Color.Black)),
        Color.Black, Color.Green, Color.Yellow, Color.Red, false,
    )
}

val OverdueRed = Color(0xFFC8433A)
val SoonOrange = Color(0xFFC97A1E)

/** 数字等宽，金额对齐更整齐 */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SubnookTheme(mode: com.subnook.app.data.ThemeMode = com.subnook.app.data.ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        com.subnook.app.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.subnook.app.data.ThemeMode.LIGHT -> false
        com.subnook.app.data.ThemeMode.DARK -> true
    }
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.LaunchedEffect(dark) {
        // 状态栏/导航栏图标颜色跟随应用主题，而不是系统主题
        val style = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(style, style)
    }
    val extra = if (dark) {
        ExtraColors(
            heroBrush = Brush.linearGradient(listOf(Color(0xFF112923), Color(0xFF16382F), Color(0xFF1D493E))),
            accentBrush = Brush.linearGradient(listOf(Color(0xFF1C6B5A), Color(0xFF26836F))),
            cardShadow = Color.Transparent,
            success = Color(0xFF4AC296),
            warning = Color(0xFFE6A94C),
            danger = Color(0xFFF07167),
            dark = true,
        )
    } else {
        ExtraColors(
            heroBrush = Brush.linearGradient(listOf(Color(0xFF133C34), Color(0xFF194E43), Color(0xFF216455))),
            accentBrush = Brush.linearGradient(listOf(Color(0xFF165B4C), Color(0xFF207360))),
            cardShadow = Color(0xFF2C281F).copy(alpha = 0.06f),
            success = Color(0xFF1D8A5E),
            warning = Color(0xFFC97A1E),
            danger = Color(0xFFC8433A),
            dark = false,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
