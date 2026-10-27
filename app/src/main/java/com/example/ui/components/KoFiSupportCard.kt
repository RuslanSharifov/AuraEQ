package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraAccent
import kotlin.math.sin

// Replace with the real owner URL before publishing, e.g. https://ko-fi.com/yourname.
// Never insert payment credentials here.
private const val KOFI_PROFILE_URL = ""

@Composable
fun KoFiSupportCard(animationsEnabled: Boolean, isAzerbaijani: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profile = KOFI_PROFILE_URL.takeIf { it.startsWith("https://ko-fi.com/") && it.length > "https://ko-fi.com/".length }
    val transition = rememberInfiniteTransition(label = "ko-fi-slime")
    val wave by transition.animateFloat(
        initialValue = 0f, targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(3400), RepeatMode.Restart),
        label = "slime-wave"
    )
    val phase = if (animationsEnabled) wave else 0f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = profile != null) {
                profile?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(Color(0xFF31265F))
            drawSlime(phase)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAzerbaijani) "AuraEQ-ni dəstəklə" else "Support AuraEQ",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (profile == null) {
                        if (isAzerbaijani) "Ko-fi hesab linki əlavə edilməyib" else "Ko-fi profile link not set"
                    } else {
                        if (isAzerbaijani) "Ko-fi ilə könüllü dəstək ol" else "Support on Ko-fi"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFFE7DBFF)
                )
            }
            Text("♥", fontSize = 30.sp, color = Color(0xFFFFC5E4))
        }
    }
}

private fun DrawScope.drawSlime(phase: Float) {
    val x = size.width * (0.79f + 0.035f * sin(phase))
    val y = size.height * (0.52f + 0.12f * sin(phase * 0.7f))
    drawCircle(Color(0x907BDCF9), radius = size.height * 0.65f, center = Offset(x, y))
    drawCircle(Color(0x998A66E8), radius = size.height * 0.41f, center = Offset(x - size.width * 0.09f, y - size.height * 0.22f))
    drawCircle(Color(0x777BFFE1), radius = size.height * 0.25f, center = Offset(x + size.width * 0.08f, y + size.height * 0.28f))
}
