package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AuraAccent
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

enum class AuraTab(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    EQUALIZER("equalizer", R.string.nav_equalizer, Icons.Filled.Equalizer, Icons.Outlined.Equalizer),
    APPS("apps", R.string.nav_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
    PRESETS("presets", R.string.nav_presets, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AuraBottomNav(
    currentTab: AuraTab,
    hapticEnabled: Boolean,
    hazeState: HazeState,
    onTabSelected: (AuraTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val shape = RoundedCornerShape(30.dp)
    val glassStyle = remember(isDark) {
        GlassStyle.clear.then {
            shape(RoundedCornerShape(30.dp))
            // Transparent tint: the real image behind the bar is blurred and
            // refracted instead of being replaced with an opaque color.
            tint(if (isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.12f))
            specularIntensity(0.88f)
            chromaticAberrationStrength(0.08f)
            lightPosition(Alignment.TopStart)
        }
    }

    // No opaque navigation background. The underlying screen remains visible.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = Color(0x268274F5),
                    spotColor = Color(0x408274F5)
                )
                .hazeGlass(
                    input = HazeInput.Backdrop(hazeState),
                    style = glassStyle
                )
                .clip(shape)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.55f else 0.92f),
                            Color.White.copy(alpha = 0.22f),
                            Color(0xFFB3A8F9).copy(alpha = 0.52f)
                        )
                    ),
                    shape = shape
                )
        ) {
            // Prismatic top-edge reflection, while the center stays optically clear.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.78f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.93f), Color.Transparent)
                        )
                    )
            )
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 9.dp)
            ) {
                AuraTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(),
                        label = "tab_tint"
                    )
                    val pillBrush = if (isSelected) {
                        Brush.linearGradient(
                            listOf(
                                AuraAccent.copy(alpha = 0.20f),
                                Color.White.copy(alpha = 0.10f),
                                AuraAccent.copy(alpha = 0.11f)
                            )
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .testTag("nav_tab_${tab.route}")
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBrush)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (currentTab != tab) {
                                    onTabSelected(tab)
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.titleRes),
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(tab.titleRes),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}
