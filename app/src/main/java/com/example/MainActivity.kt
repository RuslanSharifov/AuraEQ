package com.example

import android.content.Context
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
import androidx.compose.foundation.layout.Column
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
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: AuraViewModel by viewModels()

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingSessionIntent(intent)
    }

    private fun handleIncomingSessionIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        if (sessionId > 0) {
            (applicationContext as? AuraApplication)?.audioEffectManager?.attachSession(sessionId, packageName)
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
                Color(0xFF1E2235),
                AuraBgDark,
                Color(0xFF090B13)
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AuraBottomNav(
                currentTab = currentTab,
                hapticEnabled = state.hapticEnabled,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Persistent Glass Top Bar
            AuraTopBar(
                isEnabled = state.isEnabled,
                deviceType = state.currentDeviceType,
                deviceName = state.currentDeviceName,
                activePresetName = state.activePresetName,
                activeSessionCount = state.activeSessionCount,
                hapticEnabled = state.hapticEnabled,
                onTogglePower = { viewModel.toggleMasterPower() }
            )

            // Screen content
            Box(modifier = Modifier.fillMaxSize()) {
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
}
