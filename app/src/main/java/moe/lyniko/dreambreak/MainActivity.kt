package moe.lyniko.dreambreak

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import moe.lyniko.dreambreak.core.BreakPhase
import moe.lyniko.dreambreak.core.BreakPreferences
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.BreakState
import moe.lyniko.dreambreak.core.SessionMode
import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.monitor.ForegroundAppMonitor
import moe.lyniko.dreambreak.monitor.InstalledApp
import moe.lyniko.dreambreak.monitor.InstalledAppsProvider
import moe.lyniko.dreambreak.notification.BreakReminderService
import moe.lyniko.dreambreak.overlay.BreakOverlayController
import moe.lyniko.dreambreak.ui.theme.DreamBreakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BreakRuntime.start()
        enableEdgeToEdge()
        setContent {
            DreamBreakTheme {
                DreamBreakApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyExcludeFromRecentsSetting(BreakRuntime.uiState.value.excludeFromRecents)
    }

    fun applyExcludeFromRecentsSetting(exclude: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { appTask ->
                appTask.setExcludeFromRecents(exclude)
            }
        }
    }

    companion object {
        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun hasNotificationPermission(context: Context): Boolean {
            val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun openNotificationSettings(context: Context) {
            val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(settingsIntent)
            }.onFailure {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallbackIntent) }
            }
        }
    }
}

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
        BreakRuntime.setOverlayBackgroundUri(uri.toString())
    }

    LaunchedEffect(settingsStore) {
        settingsStore.settingsFlow.collect { settings ->
            BreakRuntime.restoreSettings(
                preferences = settings.preferences,
                pauseInListedApps = settings.pauseInListedApps,
                monitoredApps = settings.monitoredApps,
                autoStartOnBoot = settings.autoStartOnBoot,
                appEnabled = settings.appEnabled,
                overlayTransparencyPercent = settings.overlayTransparencyPercent,
                overlayBackgroundUri = settings.overlayBackgroundUri,
                onboardingCompleted = settings.onboardingCompleted,
                excludeFromRecents = settings.excludeFromRecents,
                persistentNotificationEnabled = settings.persistentNotificationEnabled,
            )
            settingsLoaded = true
        }
    }

    LaunchedEffect(
        uiState.preferences,
        uiState.pauseInListedApps,
        uiState.monitoredApps,
        uiState.autoStartOnBoot,
        uiState.appEnabled,
        uiState.overlayTransparencyPercent,
        uiState.overlayBackgroundUri,
        uiState.onboardingCompleted,
        uiState.excludeFromRecents,
        uiState.persistentNotificationEnabled,
        settingsLoaded,
    ) {
        if (!settingsLoaded) {
            return@LaunchedEffect
        }

        settingsStore.save(
            AppSettings(
                preferences = uiState.preferences,
                pauseInListedApps = uiState.pauseInListedApps,
                monitoredApps = uiState.monitoredApps,
                autoStartOnBoot = uiState.autoStartOnBoot,
                appEnabled = uiState.appEnabled,
                overlayTransparencyPercent = uiState.overlayTransparencyPercent,
                overlayBackgroundUri = uiState.overlayBackgroundUri,
                onboardingCompleted = uiState.onboardingCompleted,
                excludeFromRecents = uiState.excludeFromRecents,
                persistentNotificationEnabled = uiState.persistentNotificationEnabled,
            )
        )
    }

    LaunchedEffect(settingsLoaded, uiState.persistentNotificationEnabled, uiState.appEnabled) {
        if (!settingsLoaded) {
            return@LaunchedEffect
        }

        if (uiState.persistentNotificationEnabled && uiState.appEnabled) {
            if (MainActivity.hasNotificationPermission(context)) {
                BreakReminderService.start(context)
            } else {
                BreakRuntime.setPersistentNotificationEnabled(false)
                BreakReminderService.stop(context)
            }
        } else {
            BreakReminderService.stop(context)
        }
    }

    LaunchedEffect(settingsLoaded, uiState.excludeFromRecents) {
        if (!settingsLoaded) {
            return@LaunchedEffect
        }
        (context as? MainActivity)?.applyExcludeFromRecentsSetting(uiState.excludeFromRecents)
    }

    LaunchedEffect(uiState.pauseInListedApps, uiState.monitoredApps) {
        while (true) {
            val shouldPause = if (uiState.pauseInListedApps && ForegroundAppMonitor.hasUsageAccess(context)) {
                val monitoredPackages = parsePackageList(uiState.monitoredApps)
                val topPackage = ForegroundAppMonitor.currentForegroundPackage(context)
                monitoredPackages.contains(topPackage)
            } else {
                false
            }
            BreakRuntime.setAppPauseActive(shouldPause)
            delay(2000)
        }
    }

    // Wait for settings to load before showing anything
    if (!settingsLoaded) {
        return
    }

    // Show onboarding if not completed
    if (!uiState.onboardingCompleted) {
        OnboardingScreen(
            onFinish = { BreakRuntime.setOnboardingCompleted(true) },
        )
        return
    }

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
                    .safeDrawingPadding()
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
                            onAppEnabledChange = { BreakRuntime.setAppEnabled(it) },
                            onBreakNow = { BreakRuntime.requestBreakNow() },
                            onBigBreakNow = { BreakRuntime.requestBreakNow(bigBreak = true) },
                            onPostpone = { BreakRuntime.postponeBreak() },
                        )

                        AppDestinations.SETTINGS -> SettingsPage(
                            preferences = uiState.preferences,
                            pauseInListedApps = uiState.pauseInListedApps,
                            monitoredApps = uiState.monitoredApps,
                            hasUsageAccess = hasUsageAccess,
                            installedApps = installedApps,
                            autoStartOnBoot = uiState.autoStartOnBoot,
                            overlayTransparencyPercent = uiState.overlayTransparencyPercent,
                            overlayBackgroundUri = uiState.overlayBackgroundUri,
                            excludeFromRecents = uiState.excludeFromRecents,
                            persistentNotificationEnabled = uiState.persistentNotificationEnabled,
                            onPreferencesChange = { BreakRuntime.updatePreferences(it) },
                            onPauseInListedAppsChange = { BreakRuntime.setPauseInListedApps(it) },
                            onMonitoredAppsChange = { BreakRuntime.setMonitoredApps(it) },
                            onAutoStartOnBootChange = { BreakRuntime.setAutoStartOnBoot(it) },
                            onOverlayTransparencyPercentChange = { BreakRuntime.setOverlayTransparencyPercent(it) },
                            onPickOverlayImage = { overlayPicker.launch(arrayOf("image/*")) },
                            onClearOverlayImage = { BreakRuntime.setOverlayBackgroundUri("") },
                            onExcludeFromRecentsChange = { BreakRuntime.setExcludeFromRecents(it) },
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

@Composable
private fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // Track permission states with periodic refresh
    var hasOverlay by remember { mutableStateOf(MainActivity.canDrawOverlays(context)) }
    var hasBattery by remember { mutableStateOf(MainActivity.isIgnoringBatteryOptimizations(context)) }

    // Refresh permission states periodically (user may return from settings)
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            hasOverlay = MainActivity.canDrawOverlays(context)
            hasBattery = MainActivity.isIgnoringBatteryOptimizations(context)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> OnboardingWelcomePage()
                        1 -> OnboardingPermissionPage(
                            title = stringResource(R.string.onboarding_overlay_title),
                            description = stringResource(R.string.onboarding_overlay_desc),
                            granted = hasOverlay,
                            grantedText = stringResource(R.string.onboarding_overlay_granted),
                            buttonText = stringResource(R.string.onboarding_overlay_btn),
                            onRequest = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            },
                        )
                        2 -> OnboardingPermissionPage(
                            title = stringResource(R.string.onboarding_battery_title),
                            description = stringResource(R.string.onboarding_battery_desc),
                            granted = hasBattery,
                            grantedText = stringResource(R.string.onboarding_battery_granted),
                            buttonText = stringResource(R.string.onboarding_battery_btn),
                            onRequest = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                }
                            },
                        )
                        3 -> OnboardingDonePage()
                    }
                }
            }

            // Page indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (pagerState.currentPage < pageCount - 1) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                } else {
                    Button(onClick = onFinish) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingPermissionPage(
    title: String,
    description: String,
    granted: Boolean,
    grantedText: String,
    buttonText: String,
    onRequest: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF2F6C4A),
                )
                Text(
                    text = grantedText,
                    color = Color(0xFF2F6C4A),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Button(onClick = onRequest) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun OnboardingDonePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF2F6C4A),
        )
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_done_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptBanner(show: Boolean) {
    val transition = rememberInfiniteTransition(label = "prompt")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bannerAlpha"
    )

    AnimatedVisibility(show) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.errorContainer),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.prompt_banner),
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun HomePage(
    state: BreakState,
    preferences: BreakPreferences,
    appEnabled: Boolean,
    onAppEnabledChange: (Boolean) -> Unit,
    onBreakNow: () -> Unit,
    onBigBreakNow: () -> Unit,
    onPostpone: () -> Unit,
) {
    val homeToggleBackgroundColor by animateColorAsState(
        targetValue = if (appEnabled) Color(0xFF0072B2) else Color(0xFFD55E00),
        label = "homeToggleBackgroundColor",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onAppEnabledChange(!appEnabled) },
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = homeToggleBackgroundColor,
                contentColor = Color.White,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (appEnabled) {
                        stringResource(R.string.home_app_status_on)
                    } else {
                        stringResource(R.string.home_app_status_off)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (appEnabled) {
                        stringResource(R.string.home_toggle_disable)
                    } else {
                        stringResource(R.string.home_toggle_enable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(stringResource(R.string.home_countdown, formatSeconds(state.secondsToNextBreak)), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.home_since_last, formatSeconds(state.secondsSinceLastBreak)))
        Text(stringResource(R.string.home_status, modeLabel(state.mode)))

        ActionButton(
            text = stringResource(R.string.action_small_break),
            icon = Icons.Default.Notifications,
            onClick = onBreakNow,
            enabled = appEnabled,
        )
        if (preferences.bigAfter > 0) {
            ActionButton(
                text = stringResource(R.string.action_big_break),
                icon = Icons.Default.Favorite,
                onClick = onBigBreakNow,
                enabled = appEnabled,
            )
        }
        ActionButton(
            text = stringResource(R.string.action_postpone_5m),
            icon = Icons.Default.Home,
            onClick = onPostpone,
            enabled = appEnabled,
        )
    }
}

@Composable
private fun SettingsPage(
    preferences: BreakPreferences,
    pauseInListedApps: Boolean,
    monitoredApps: String,
    hasUsageAccess: Boolean,
    installedApps: List<InstalledApp>,
    autoStartOnBoot: Boolean,
    overlayTransparencyPercent: Int,
    overlayBackgroundUri: String,
    excludeFromRecents: Boolean,
    persistentNotificationEnabled: Boolean,
    onPreferencesChange: (BreakPreferences) -> Unit,
    onPauseInListedAppsChange: (Boolean) -> Unit,
    onMonitoredAppsChange: (String) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onOverlayTransparencyPercentChange: (Int) -> Unit,
    onPickOverlayImage: () -> Unit,
    onClearOverlayImage: () -> Unit,
    onExcludeFromRecentsChange: (Boolean) -> Unit,
    onPersistentNotificationEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var appSearch by remember { mutableStateOf("") }
    var openSubPage by remember { mutableStateOf(false) }
    var overlayPreviewVisible by remember { mutableStateOf(false) }
    val previewController = remember(context.applicationContext) {
        BreakOverlayController(
            context = context.applicationContext,
            onInterruptBreak = { overlayPreviewVisible = false },
            onPostponeBreak = { _ -> overlayPreviewVisible = false },
        )
    }

    DisposableEffect(previewController) {
        onDispose {
            previewController.release()
        }
    }

    LaunchedEffect(openSubPage) {
        if (openSubPage) {
            overlayPreviewVisible = false
        }
    }

    LaunchedEffect(
        overlayPreviewVisible,
        overlayBackgroundUri,
        overlayTransparencyPercent,
        preferences.bigAfter,
        preferences.bigFor,
        preferences.smallFor,
    ) {
        if (!overlayPreviewVisible) {
            previewController.release()
            return@LaunchedEffect
        }

        val showBigBreakStyle = preferences.bigAfter > 0
        val previewDuration = if (showBigBreakStyle) preferences.bigFor else preferences.smallFor
        previewController.render(
            state = BreakState(
                mode = SessionMode.BREAK,
                phase = BreakPhase.FULL_SCREEN,
                secondsToNextBreak = 0,
                isBigBreak = showBigBreakStyle,
                breakSecondsRemaining = previewDuration,
            ),
            appEnabled = true,
            overlayBackgroundUri = overlayBackgroundUri,
            overlayTransparencyPercent = overlayTransparencyPercent,
        )
    }

    val selectedPackages = parsePackageList(monitoredApps)
    val filteredApps = installedApps.filter {
        appSearch.isBlank() ||
            it.label.contains(appSearch, ignoreCase = true) ||
            it.packageName.contains(appSearch, ignoreCase = true)
    }

    if (openSubPage) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_app_subpage_title), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { openSubPage = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }

            AppSelectionSection(
                appSearch = appSearch,
                onAppSearchChange = { appSearch = it },
                selectedPackages = selectedPackages,
                filteredApps = filteredApps,
                onMonitoredAppsChange = onMonitoredAppsChange,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_schedule), style = MaterialTheme.typography.titleLarge)

        NumberInputField(
            label = stringResource(R.string.settings_small_every),
            value = preferences.smallEvery / 60,
            minValue = 1,
            maxValue = 720,
            onValueChange = { onPreferencesChange(preferences.copy(smallEvery = it * 60)) },
        )
        NumberInputField(
            label = stringResource(R.string.settings_small_for),
            value = preferences.smallFor,
            minValue = 5,
            maxValue = 1800,
            onValueChange = { onPreferencesChange(preferences.copy(smallFor = it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_enable_big_break), modifier = Modifier.weight(1f))
            Switch(
                checked = preferences.bigAfter > 0,
                onCheckedChange = { enabled ->
                    val nextBigAfter = if (enabled) {
                        if (preferences.bigAfter > 0) preferences.bigAfter else BreakPreferences().bigAfter
                    } else {
                        0
                    }
                    onPreferencesChange(preferences.copy(bigAfter = nextBigAfter))
                }
            )
        }
        if (preferences.bigAfter > 0) {
            NumberInputField(
                label = stringResource(R.string.settings_big_after),
                value = preferences.bigAfter,
                minValue = 1,
                maxValue = 20,
                onValueChange = { onPreferencesChange(preferences.copy(bigAfter = it)) },
            )
            NumberInputField(
                label = stringResource(R.string.settings_big_for),
                value = preferences.bigFor,
                minValue = 10,
                maxValue = 3600,
                onValueChange = { onPreferencesChange(preferences.copy(bigFor = it)) },
            )
        }

        Text(stringResource(R.string.settings_reminder), style = MaterialTheme.typography.titleLarge)
        NumberInputField(
            label = stringResource(R.string.settings_flash_for),
            value = preferences.flashFor,
            minValue = 3,
            maxValue = 600,
            onValueChange = { onPreferencesChange(preferences.copy(flashFor = it)) },
        )
        NumberInputField(
            label = stringResource(R.string.settings_postpone_for),
            value = preferences.postponeFor,
            minValue = 10,
            maxValue = 7200,
            onValueChange = { onPreferencesChange(preferences.copy(postponeFor = it)) },
        )
        NumberInputField(
            label = stringResource(R.string.settings_reset_interval_after_pause),
            value = preferences.resetIntervalAfterPause,
            minValue = 60,
            maxValue = 43200,
            onValueChange = { onPreferencesChange(preferences.copy(resetIntervalAfterPause = it)) },
        )
        NumberInputField(
            label = stringResource(R.string.settings_reset_cycle_after_pause),
            value = preferences.resetCycleAfterPause,
            minValue = 60,
            maxValue = 86400,
            onValueChange = { onPreferencesChange(preferences.copy(resetCycleAfterPause = it)) },
        )

        Text(stringResource(R.string.settings_pause), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_pause_selected_apps), modifier = Modifier.weight(1f))
            Switch(checked = pauseInListedApps, onCheckedChange = onPauseInListedAppsChange)
        }
        if (!hasUsageAccess) {
            Text(
                text = stringResource(R.string.settings_no_usage_access),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(onClick = { openSubPage = true }) {
            Text(stringResource(R.string.settings_open_app_subpage))
        }
        Text(
            text = stringResource(R.string.settings_selected_apps_count, selectedPackages.size),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(stringResource(R.string.settings_overlay), style = MaterialTheme.typography.titleLarge)
        NumberInputField(
            label = stringResource(R.string.settings_overlay_transparency),
            value = overlayTransparencyPercent,
            minValue = 0,
            maxValue = 90,
            onValueChange = onOverlayTransparencyPercentChange,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPickOverlayImage) {
                Text(stringResource(R.string.settings_pick_overlay_image))
            }
            Button(onClick = onClearOverlayImage) {
                Text(stringResource(R.string.settings_clear_overlay_image))
            }
        }
        Text(
            text = if (overlayBackgroundUri.isBlank()) {
                stringResource(R.string.settings_overlay_image_none)
            } else {
                stringResource(R.string.settings_overlay_image_selected)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { overlayPreviewVisible = !overlayPreviewVisible },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (overlayPreviewVisible) {
                    stringResource(R.string.action_cancel)
                } else {
                    stringResource(R.string.settings_overlay_preview)
                }
            )
        }

        Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_persistent_notification), modifier = Modifier.weight(1f))
            Switch(
                checked = persistentNotificationEnabled,
                onCheckedChange = onPersistentNotificationEnabledChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_auto_start_on_boot), modifier = Modifier.weight(1f))
            Switch(checked = autoStartOnBoot, onCheckedChange = onAutoStartOnBootChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_exclude_from_recents), modifier = Modifier.weight(1f))
            Switch(checked = excludeFromRecents, onCheckedChange = onExcludeFromRecentsChange)
        }
        Text(stringResource(R.string.settings_lang_hint))
    }
}

@Composable
private fun AppSelectionSection(
    appSearch: String,
    onAppSearchChange: (String) -> Unit,
    selectedPackages: Set<String>,
    filteredApps: List<InstalledApp>,
    onMonitoredAppsChange: (String) -> Unit,
) {
    TextField(
        modifier = Modifier.fillMaxWidth(),
        value = appSearch,
        onValueChange = onAppSearchChange,
        label = { Text(stringResource(R.string.settings_app_search)) },
    )
    Text(
        text = stringResource(R.string.settings_selected_apps_count, selectedPackages.size),
        style = MaterialTheme.typography.bodyMedium,
    )

    filteredApps.forEach { app ->
        val checked = selectedPackages.contains(app.packageName)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.label)
                Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = { selected ->
                    val updated = selectedPackages.toMutableSet().apply {
                        if (selected) add(app.packageName) else remove(app.packageName)
                    }
                    onMonitoredAppsChange(updated.sorted().joinToString(","))
                }
            )
        }
    }
}

@Composable
private fun NumberInputField(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    TextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            text = digits
            val parsed = digits.toIntOrNull() ?: return@TextField
            val clamped = parsed.coerceIn(minValue, maxValue)
            onValueChange(clamped)
        },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.settings_value_range, minValue, maxValue)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = text)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun modeLabel(mode: SessionMode): String {
    return when (mode) {
        SessionMode.NORMAL -> stringResource(R.string.mode_normal)
        SessionMode.PAUSED -> stringResource(R.string.mode_paused)
        SessionMode.BREAK -> stringResource(R.string.mode_break)
    }
}

@Composable
private fun phaseLabel(phase: BreakPhase?): String {
    return when (phase) {
        BreakPhase.PROMPT -> stringResource(R.string.phase_prompt)
        BreakPhase.FULL_SCREEN -> stringResource(R.string.phase_full_screen)
        BreakPhase.POST -> stringResource(R.string.phase_post)
        null -> stringResource(R.string.break_not_started)
    }
}

private fun parsePackageList(csv: String): Set<String> {
    return csv
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

private fun formatSeconds(rawSeconds: Int): String {
    val seconds = rawSeconds.coerceAtLeast(0)
    val minutePart = seconds / 60
    val secondPart = seconds % 60
    return "%02d:%02d".format(minutePart, secondPart)
}

@Preview(showBackground = true)
@Composable
private fun DreamBreakPreview() {
    DreamBreakTheme {
        DreamBreakApp()
    }
}
