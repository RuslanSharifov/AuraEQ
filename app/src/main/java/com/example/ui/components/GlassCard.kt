package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuraCardBorderDark
import com.example.ui.theme.AuraCardBorderLight

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    elevation: Dp = 6.dp,
    borderAlpha: Float = 0.5f,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val shape = RoundedCornerShape(cornerRadius)

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xB3445375),
                Color(0xA8273656)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF5FFFFFF),
                Color(0xD9F3F6FD)
            )
        )
    }

    val borderColor = if (isDark) {
        AuraCardBorderDark.copy(alpha = borderAlpha)
    } else {
        AuraCardBorderLight.copy(alpha = borderAlpha)
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = if (isDark) Color(0xFF151B35).copy(alpha = 0.25f) else Color(0x208274F5),
                spotColor = if (isDark) Color(0xFF2C356A).copy(alpha = 0.3f) else Color(0x33A6B4DF)
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        content()
    }
}
