package moe.lyniko.dreambreak

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.toAppSettings
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.monitor.AppPauseMonitor
import moe.lyniko.dreambreak.monitor.ForegroundAppMonitor
import moe.lyniko.dreambreak.monitor.InstalledApp
import moe.lyniko.dreambreak.monitor.InstalledAppsProvider
import moe.lyniko.dreambreak.monitor.ScreenLockMonitor
import moe.lyniko.dreambreak.monitor.shouldPauseForForegroundApp
import moe.lyniko.dreambreak.notification.BreakReminderService
import moe.lyniko.dreambreak.notification.PostponePickerActivity
import moe.lyniko.dreambreak.startup.RuntimeBootstrap
import moe.lyniko.dreambreak.ui.home.HomePage
import moe.lyniko.dreambreak.ui.onboarding.OnboardingScreen
import moe.lyniko.dreambreak.ui.settings.SettingsPage
import moe.lyniko.dreambreak.ui.settings.parsePackageList
import moe.lyniko.dreambreak.ui.theme.DreamBreakTheme

@Composable
fun DreamBreakApp() {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val uiState by BreakRuntime.uiState.collectAsState()
    var settingsLoaded by remember { mutableStateOf(false) }
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val hasUsageAccess = ForegroundAppMonitor.hasUsageAccess(context)

    val installedApps by produceState(initialValue = emptyList<InstalledApp>(), key1 = Unit) {
        value = InstalledAppsProvider.loadLaunchableApps(context.applicationContext)
    }

    var overlayPickerTarget by remember { mutableStateOf(OverlayImageTarget.PORTRAIT) }
    val overlayPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        when (overlayPickerTarget) {
            OverlayImageTarget.PORTRAIT -> BreakRuntime.setOverlayBackgroundPortraitUri(uri.toString())
            OverlayImageTarget.LANDSCAPE -> BreakRuntime.setOverlayBackgroundLandscapeUri(uri.toString())
        }
    }

    LaunchedEffect(settingsStore) {
        var isFirstLoad = true
        settingsStore.settingsFlow.collect { settings ->
            RuntimeBootstrap.applySettings(settings, isFirstLoad = isFirstLoad)
            isFirstLoad = false
            settingsLoaded = true
        }
    }

    LaunchedEffect(Unit) {
        AppPauseMonitor.start(context.applicationContext)
        ScreenLockMonitor.start(context.applicationContext)
    }

    val persistableSettings = remember(uiState) { uiState.toAppSettings() }

    LaunchedEffect(persistableSettings, settingsLoaded) {
        if (!settingsLoaded) {
            return@LaunchedEffect
        }
        settingsStore.save(persistableSettings)
    }

    LaunchedEffect(settingsLoaded, uiState.persistentNotificationEnabled, uiState.appEnabled) {
        if (!settingsLoaded) {
            return@LaunchedEffect
        }
        RuntimeBootstrap.syncReminderService(context)
    }

    LaunchedEffect(settingsLoaded, uiState.excludeFromRecents) {
        if (!settingsLoaded) {
            return@LaunchedEffect
        }
        (context as? MainActivity)?.applyExcludeFromRecentsSetting(uiState.excludeFromRecents)
    }

    if (!settingsLoaded) {
        return
    }

    if (!uiState.onboardingCompleted) {
        OnboardingScreen(
            onFinish = { BreakRuntime.setOnboardingCompleted(true) },
        )
        return
    }

    val breakCycleEnableBlocked =
        !uiState.hasVisitedSpecificAppsPage ||
            !uiState.hasEnabledPauseInListedAppsOnce ||
            !uiState.hasAddedExternalPauseAppOnce

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = stringResource(it.labelRes)) },
                    label = { Text(stringResource(it.labelRes)) },
                    selected = currentDestination == it,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = 80)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                    },
                    label = "destinationTransition",
                ) { destination ->
                    when (destination) {
                        AppDestinations.HOME -> HomePage(
                            state = uiState.state,
                            preferences = uiState.preferences,
                            appEnabled = uiState.appEnabled,
                            breakCycleEnableBlocked = breakCycleEnableBlocked,
                            onAppEnabledChange = { BreakRuntime.setAppEnabled(it) },
                            onBreakNow = { BreakRuntime.requestBreakNow() },
                            onBigBreakNow = { BreakRuntime.requestBreakNow(bigBreak = true) },
                            onPostpone = {
                                context.startActivity(Intent(context, PostponePickerActivity::class.java))
                            },
                        )

                        AppDestinations.SETTINGS -> SettingsPage(
                            preferences = uiState.preferences,
                            pauseInListedApps = uiState.pauseInListedApps,
                            monitoredApps = uiState.monitoredApps,
                            hasUsageAccess = hasUsageAccess,
                            installedApps = installedApps,
                            autoStartOnBoot = uiState.autoStartOnBoot,
                            restoreEnabledStateOnStart = uiState.restoreEnabledStateOnStart,
                            reenableOnScreenUnlock = uiState.reenableOnScreenUnlock,
                            overlayTransparencyPercent = uiState.overlayTransparencyPercent,
                            overlayBackgroundPortraitUri = uiState.overlayBackgroundPortraitUri,
                            overlayBackgroundLandscapeUri = uiState.overlayBackgroundLandscapeUri,
                            excludeFromRecents = uiState.excludeFromRecents,
                            persistentNotificationEnabled = uiState.persistentNotificationEnabled,
                            persistentNotificationUpdateFrequencySeconds =
                                uiState.persistentNotificationUpdateFrequencySeconds,
                            persistentNotificationTitleTemplate =
                                uiState.persistentNotificationTitleTemplate,
                            persistentNotificationContentTemplate =
                                uiState.persistentNotificationContentTemplate,
                            qsTileCountdownAsTitle = uiState.qsTileCountdownAsTitle,
                            breakShowPostponeButton = uiState.breakShowPostponeButton,
                            breakShowTitle = uiState.breakShowTitle,
                            breakShowCountdown = uiState.breakShowCountdown,
                            breakShowExitButton = uiState.breakShowExitButton,
                            breakExitPostponeSeconds = uiState.breakExitPostponeSeconds,
                            breakOverlayFadeInDurationMs = uiState.breakOverlayFadeInDurationMs,
                            breakOverlayFadeOutDurationMs = uiState.breakOverlayFadeOutDurationMs,
                            themeMode = uiState.themeMode,
                            onPreferencesChange = { BreakRuntime.updatePreferences(it) },
                            onPauseInListedAppsChange = { enabled ->
                                BreakRuntime.setPauseInListedApps(enabled)
                                if (enabled) {
                                    BreakRuntime.markPauseInListedAppsEnabledOnce()
                                }
                                BreakRuntime.setAppPauseActive(
                                    shouldPauseForForegroundApp(
                                        context = context,
                                        pauseInListedApps = enabled,
                                        monitoredApps = uiState.monitoredApps,
                                    )
                                )
                            },
                            onMonitoredAppsChange = { monitoredApps ->
                                BreakRuntime.setMonitoredApps(monitoredApps)
                                val hasExternalApp = parsePackageList(monitoredApps)
                                    .any { it != context.packageName }
                                if (hasExternalApp) {
                                    BreakRuntime.markExternalPauseAppAddedOnce()
                                }
                                BreakRuntime.setAppPauseActive(
                                    shouldPauseForForegroundApp(
                                        context = context,
                                        pauseInListedApps = uiState.pauseInListedApps,
                                        monitoredApps = monitoredApps,
                                    )
                                )
                            },
                            onAutoStartOnBootChange = { BreakRuntime.setAutoStartOnBoot(it) },
                            onRestoreEnabledStateOnStartChange = { BreakRuntime.setRestoreEnabledStateOnStart(it) },
                            onReenableOnScreenUnlockChange = { BreakRuntime.setReenableOnScreenUnlock(it) },
                            onOverlayTransparencyPercentChange = { BreakRuntime.setOverlayTransparencyPercent(it) },
                            onPickOverlayPortraitImage = {
                                overlayPickerTarget = OverlayImageTarget.PORTRAIT
                                overlayPicker.launch(arrayOf("image/*"))
                            },
                            onPickOverlayLandscapeImage = {
                                overlayPickerTarget = OverlayImageTarget.LANDSCAPE
                                overlayPicker.launch(arrayOf("image/*"))
                            },
                            onClearOverlayPortraitImage = { BreakRuntime.setOverlayBackgroundPortraitUri("") },
                            onClearOverlayLandscapeImage = { BreakRuntime.setOverlayBackgroundLandscapeUri("") },
                            onExcludeFromRecentsChange = { BreakRuntime.setExcludeFromRecents(it) },
                            onThemeModeChange = { BreakRuntime.setThemeMode(it) },
                            onPersistentNotificationEnabledChange = { enabled ->
                                if (!enabled) {
                                    BreakRuntime.setPersistentNotificationEnabled(false)
                                } else if (MainActivity.hasNotificationPermission(context)) {
                                    BreakRuntime.setPersistentNotificationEnabled(true)
                                } else {
                                    BreakRuntime.setPersistentNotificationEnabled(false)
                                    MainActivity.openNotificationSettings(context)
                                }
                            },
                            onPersistentNotificationUpdateFrequencySecondsChange = {
                                BreakRuntime.setPersistentNotificationUpdateFrequencySeconds(it)
                            },
                            onPersistentNotificationTitleTemplateChange = {
                                BreakRuntime.setPersistentNotificationTitleTemplate(it)
                            },
                            onPersistentNotificationContentTemplateChange = {
                                BreakRuntime.setPersistentNotificationContentTemplate(it)
                            },
                            onQsTileCountdownAsTitleChange = {
                                BreakRuntime.setQsTileCountdownAsTitle(it)
                            },
                            onBreakShowPostponeButtonChange = {
                                BreakRuntime.setBreakShowPostponeButton(it)
                            },
                            onBreakShowTitleChange = {
                                BreakRuntime.setBreakShowTitle(it)
                            },
                            onBreakShowCountdownChange = {
                                BreakRuntime.setBreakShowCountdown(it)
                            },
                            onBreakShowExitButtonChange = {
                                BreakRuntime.setBreakShowExitButton(it)
                            },
                            onBreakExitPostponeSecondsChange = {
                                BreakRuntime.setBreakExitPostponeSeconds(it)
                            },
                            onBreakOverlayFadeInDurationMsChange = {
                                BreakRuntime.setBreakOverlayFadeInDurationMs(it)
                            },
                            onBreakOverlayFadeOutDurationMsChange = {
                                BreakRuntime.setBreakOverlayFadeOutDurationMs(it)
                            },
                            onOpenPreBreakNotificationChannelSettings = {
                                MainActivity.openNotificationChannelSettings(
                                    context,
                                    BreakReminderService.PRE_BREAK_CHANNEL_ID,
                                )
                            },
                            onSpecificAppsPageOpened = { BreakRuntime.markSpecificAppsPageVisited() },
                        )
                    }
                }
            }
        }
    }
}

private enum class AppDestinations(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Default.Home),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings),
}

private enum class OverlayImageTarget {
    PORTRAIT,
    LANDSCAPE,
}

@Preview(showBackground = true)
@Composable
private fun DreamBreakPreview() {
    DreamBreakTheme {
        DreamBreakApp()
    }
}
