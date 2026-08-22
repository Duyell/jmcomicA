package com.jmcomic.pdfapp.ui.theme

import androidx.compose.ui.graphics.Color

// ── 品牌色（浅色主题基准）──────────────────────────────────────
val BrandBlue = Color(0xFF3B82F6)
val BrandBlueDim = Color(0xFF2563EB)
val BrandCyan = Color(0xFF06B6D4)
val BrandViolet = Color(0xFF8B5CF6)

// ── 浅色主题（浅蓝白）─────────────────────────────────────────
val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = BrandCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF155E75),
    tertiary = BrandViolet,
    onTertiary = Color.White,
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE8EEF6),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color(0xFFF3F6FB),
    surfaceContainerHigh = Color(0xFFEDF1F7),
    surfaceContainerHighest = Color(0xFFE6EBF2),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFB91C1C),
)

// ── 深色主题（深蓝灰）─────────────────────────────────────────
val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0A1A33),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1065),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF182237),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF243149),
    surfaceContainerHighest = Color(0xFF2A3954),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
)

// ── 扩展色（Material 3 没有的语义色，如 success）───────────────
// 由 Theme.kt 通过 LocalExtendedColors 按明暗主题提供。

val SuccessGreenLight = Color(0xFF22C55E)
val SuccessGreenDark = Color(0xFF4ADE80)

// ── 旧常量别名（兼容未迁移代码）───────────────────────────────
val SurfaceDark = Color(0xFFF3F6FB)      // 旧背景色 → 新 background
val SurfaceContainer = Color.White        // 旧卡片色 → 新 surface
val AccentBlue = BrandBlue
val AccentBlueDim = BrandBlueDim
val AccentCyan = BrandCyan
val TextPrimary = Color(0xFF1E293B)
val TextSecondary = Color(0xFF64748B)
val ErrorRed = Color(0xFFEF4444)
val SuccessGreen = SuccessGreenLight
val AccentPink = AccentBlue               // 历史遗留别名
val AccentPinkDim = AccentBlueDim
val AccentCrimson = Color(0xFFE04860)
val AccentCrimsonDim = Color(0xFFB83048)
