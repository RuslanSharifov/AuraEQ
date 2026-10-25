package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AudioDeviceType
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraGreenSuccess

@Composable
fun AuraTopBar(
    isEnabled: Boolean,
    deviceType: AudioDeviceType,
    deviceName: String,
    activePresetName: String,
    activeSessionCount: Int,
    hapticEnabled: Boolean,
    onTogglePower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val powerColor by animateColorAsState(
        targetValue = if (isEnabled) AuraAccent else (if (isDark) Color(0xFF3E4358) else Color(0xFFC7CEDF)),
        animationSpec = spring(),
        label = "power_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Title & Active Preset pill
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AURA",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EQ",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp,
                        color = AuraAccent
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Preset pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isDark) Color(0x338274F5) else Color(0x228274F5)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activePresetName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuraAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Output Device Route with Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val deviceIcon = when (deviceType) {
                        AudioDeviceType.BLUETOOTH -> Icons.Default.Bluetooth
                        AudioDeviceType.WIRED -> Icons.Default.Headphones
                        AudioDeviceType.USB -> Icons.Default.Usb
                        else -> Icons.Default.Speaker
                    }

                    Icon(
                        imageVector = deviceIcon,
                        contentDescription = "Device Route",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = deviceName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (activeSessionCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AuraGreenSuccess)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$activeSessionCount active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AuraGreenSuccess
                        )
                    }
                }
            }

            // Power Toggle Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("power_button")
                    .shadow(
                        elevation = if (isEnabled) 6.dp else 1.dp,
                        shape = CircleShape,
                        spotColor = if (isEnabled) AuraAccent else Color.Transparent
                    )
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B7CF8), Color(0xFF6C57F6))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    if (isDark) Color(0xFF23283C) else Color(0xFFE5E9F3),
                                    if (isDark) Color(0xFF1B1E2E) else Color(0xFFD6DBE8)
                                )
                            )
                        }
                    )
                    .clickable {
                        onTogglePower()
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isEnabled) stringResource(R.string.status_power_on) else stringResource(R.string.status_power_off),
                    tint = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
