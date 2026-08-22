package com.jmcomic.pdfapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CTA 渐变配色（按钮 / 首页头图共用）。
 * 深色主题自动切换到更深沉的渐变，保证白字对比度。
 */
@Composable
fun ctaGradientColors(): List<Color> {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) listOf(Color(0xFF2563EB), Color(0xFF0891B2))
    else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
}

/**
 * 主题化渐变 CTA 按钮（下载、确认等主操作）。
 * 深色主题自动切换到更深沉的渐变，保证白字对比度；禁用时变灰。
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: Dp = 54.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    val gradientColors = if (enabled) {
        ctaGradientColors()
    } else {
        listOf(Color(0xFF94A3B8), Color(0xFF94A3B8))
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(0.dp),
        enabled = enabled,
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(gradientColors), shape),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
