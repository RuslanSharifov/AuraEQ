package com.example

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContextWrapper
import android.content.res.Resources
import android.content.res.Configuration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AuraBottomNav
import com.example.ui.components.AuraTab
import com.example.ui.components.AuraTopBar
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.PresetsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AuraBgDark
import com.example.ui.theme.AuraBgLight
import com.example.ui.theme.AuraIceBlue
import com.example.ui.theme.AuraPastelLavender
import com.example.ui.theme.AuraSoftCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AuraViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: AuraViewModel by viewModels()
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val app = applicationContext as? AuraApplication
                app?.eqNotification?.update(viewModel.uiState.value.isEnabled)
            }
        }

    override fun onStart() {
        super.onStart()
        // One automatic runtime prompt on the first app launch on Android 13+.
        // A denial is respected; the user can retry via the existing in-app control.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = getSharedPreferences("aura_permissions", MODE_PRIVATE)
            if (!permissions.getBoolean("notification_requested", false)) {
                permissions.edit().putBoolean("notification_requested", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle external intent if opened with audio session
        handleIncomingSessionIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Keep the Activity in the ContextWrapper chain. A bare Context returned
            // by createConfigurationContext() does NOT expose ActivityResultRegistryOwner,
            // causing rememberLauncherForActivityResult() to crash at first launch.
            val localizedContext = remember(state.language) {
                val languageResources = updateLocale(this, state.language).resources
                object : ContextWrapper(this@MainActivity) {
                    override fun getResources(): Resources = languageResources
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                MyApplicationTheme(themeMode = state.themeMode) {
                    AuraMainScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (applicationContext as? AuraApplication)?.exitAndReset()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Reassert EQ parameters when returning from Spotify/Android audio settings.
        (applicationContext as? AuraApplication)?.audioEffectManager?.refreshActiveSessions()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSessionIntent(intent)
    }

    private fun handleIncomingSessionIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        if (sessionId > 0) {
            val app = applicationContext as? AuraApplication
            app?.audioEffectManager?.attachSession(sessionId, packageName)
            app?.onAudioSessionOpened(sessionId, packageName)
        }
    }

    private fun updateLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }
}

@Composable
fun AuraMainScreen(
    viewModel: AuraViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(AuraTab.EQUALIZER) }

    // BackHandler to navigate back to Equalizer tab when on secondary screens
    BackHandler(enabled = currentTab != AuraTab.EQUALIZER) {
        currentTab = AuraTab.EQUALIZER
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    // Ambient glass background gradient
    val backgroundBrush = if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF394A70),
                AuraBgDark,
                Color(0xFF1B2540)
            ),
            radius = 1600f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                AuraPastelLavender.copy(alpha = 0.5f),
                AuraIceBlue.copy(alpha = 0.4f),
                AuraBgLight
            ),
            radius = 1400f
        )
    }

    val hazeState = rememberHazeState()

    // Draw the gradient, ambient lights and screen content into a Haze source.
    // The bottom bar is then drawn as an overlay that refracts those real pixels.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(backgroundBrush)
        ) {
            // Layered light patches give the glass something to visibly bend.
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .offset(x = 85.dp, y = 105.dp)
                    .size(330.dp)
                    .background(
                        Brush.radialGradient(
                            colors = if (isDark) listOf(Color(0x776F65D7), Color.Transparent)
                            else listOf(Color(0x99D7C6FF), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .offset(x = (-100).dp, y = 92.dp)
                    .size(280.dp)
                    .background(
                        Brush.radialGradient(
                            colors = if (isDark) listOf(Color(0x5562D4DF), Color.Transparent)
                            else listOf(Color(0x99B4E5FF), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                AuraTopBar(
                    isEnabled = state.isEnabled,
                    deviceType = state.currentDeviceType,
                    deviceName = state.currentDeviceName,
                    activePresetName = state.activePresetName,
                    activeSessionCount = state.activeSessionCount,
                    hapticEnabled = state.hapticEnabled,
                    onTogglePower = { viewModel.toggleMasterPower() }
                )

                // Leave touch/scroll clearance for the floating nav.
                Box(modifier = Modifier.weight(1f).padding(bottom = 87.dp)) {
                    Crossfade(
                        targetState = currentTab,
                        label = "tab_crossfade"
                    ) { tab ->
                        when (tab) {
                            AuraTab.EQUALIZER -> EqualizerScreen(state = state, viewModel = viewModel)
                            AuraTab.APPS -> AppsScreen(state = state, viewModel = viewModel)
                            AuraTab.PRESETS -> PresetsScreen(state = state, viewModel = viewModel)
                            AuraTab.SETTINGS -> SettingsScreen(state = state, viewModel = viewModel)
                        }
                    }
                }
            }
        }

        AuraBottomNav(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            currentTab = currentTab,
            hapticEnabled = state.hapticEnabled,
            hazeState = hazeState,
            onTabSelected = { currentTab = it }
        )
    }

}
