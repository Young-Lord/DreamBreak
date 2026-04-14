package moe.lyniko.dreambreak.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakPhase
import moe.lyniko.dreambreak.core.BreakPreferences
import moe.lyniko.dreambreak.core.BreakState
import moe.lyniko.dreambreak.core.DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE
import moe.lyniko.dreambreak.core.OVERLAY_TRANSPARENCY_MAX
import moe.lyniko.dreambreak.core.OVERLAY_TRANSPARENCY_MIN
import moe.lyniko.dreambreak.core.SessionMode
import moe.lyniko.dreambreak.core.formatPostponeDurations
import moe.lyniko.dreambreak.core.normalizePostponeDurationInput
import moe.lyniko.dreambreak.core.parsePostponeDurations
import moe.lyniko.dreambreak.data.AppThemeMode
import moe.lyniko.dreambreak.monitor.InstalledApp
import moe.lyniko.dreambreak.notification.BreakReminderService
import moe.lyniko.dreambreak.overlay.BreakOverlayController

private const val OVERLAY_PREVIEW_AUTO_DISMISS_MS = 20_000L

@Composable
fun SettingsPage(
    preferences: BreakPreferences,
    pauseInListedApps: Boolean,
    monitoredApps: String,
    hasUsageAccess: Boolean,
    installedApps: List<InstalledApp>,
    autoStartOnBoot: Boolean,
    restoreEnabledStateOnStart: Boolean,
    reenableOnScreenUnlock: Boolean,
    overlayTransparencyPercent: Int,
    overlayBackgroundPortraitUri: String,
    overlayBackgroundLandscapeUri: String,
    excludeFromRecents: Boolean,
    persistentNotificationEnabled: Boolean,
    persistentNotificationUpdateFrequencySeconds: Int,
    persistentNotificationTitleTemplate: String,
    persistentNotificationContentTemplate: String,
    qsTileCountdownAsTitle: Boolean,
    breakShowPostponeButton: Boolean,
    breakShowTitle: Boolean,
    breakShowCountdown: Boolean,
    breakShowExitButton: Boolean,
    breakExitPostponeSeconds: Int,
    breakOverlayFadeInDurationMs: Int,
    breakOverlayFadeOutDurationMs: Int,
    themeMode: AppThemeMode,
    onPreferencesChange: (BreakPreferences) -> Unit,
    onPauseInListedAppsChange: (Boolean) -> Unit,
    onMonitoredAppsChange: (String) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onRestoreEnabledStateOnStartChange: (Boolean) -> Unit,
    onReenableOnScreenUnlockChange: (Boolean) -> Unit,
    onOverlayTransparencyPercentChange: (Int) -> Unit,
    onPickOverlayPortraitImage: () -> Unit,
    onPickOverlayLandscapeImage: () -> Unit,
    onClearOverlayPortraitImage: () -> Unit,
    onClearOverlayLandscapeImage: () -> Unit,
    onExcludeFromRecentsChange: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onPersistentNotificationEnabledChange: (Boolean) -> Unit,
    onPersistentNotificationUpdateFrequencySecondsChange: (Int) -> Unit,
    onPersistentNotificationTitleTemplateChange: (String) -> Unit,
    onPersistentNotificationContentTemplateChange: (String) -> Unit,
    onQsTileCountdownAsTitleChange: (Boolean) -> Unit,
    onBreakShowPostponeButtonChange: (Boolean) -> Unit,
    onBreakShowTitleChange: (Boolean) -> Unit,
    onBreakShowCountdownChange: (Boolean) -> Unit,
    onBreakShowExitButtonChange: (Boolean) -> Unit,
    onBreakExitPostponeSecondsChange: (Int) -> Unit,
    onBreakOverlayFadeInDurationMsChange: (Int) -> Unit,
    onBreakOverlayFadeOutDurationMsChange: (Int) -> Unit,
    onOpenPreBreakNotificationChannelSettings: () -> Unit,
    onSpecificAppsPageOpened: () -> Unit,
) {
    val context = LocalContext.current
    val defaultPreferences = remember { BreakPreferences() }
    val defaultPersistentNotificationTitleTemplate = remember {
        DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE
    }
    var appSearch by remember { mutableStateOf("") }
    var openSubPage by remember { mutableStateOf(false) }
    var overlayPreviewVisible by remember { mutableStateOf(false) }
    val previewController = remember(context.applicationContext) {
        BreakOverlayController(
            context = context.applicationContext,
            onExitBreak = { _ -> overlayPreviewVisible = false },
            onPostponeBreak = { _ -> overlayPreviewVisible = false },
            onDismissRequest = { overlayPreviewVisible = false },
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

    BackHandler(enabled = openSubPage) {
        openSubPage = false
    }

    BackHandler(enabled = overlayPreviewVisible && !openSubPage) {
        overlayPreviewVisible = false
    }

    LaunchedEffect(overlayPreviewVisible) {
        if (!overlayPreviewVisible) {
            return@LaunchedEffect
        }
        delay(OVERLAY_PREVIEW_AUTO_DISMISS_MS)
        overlayPreviewVisible = false
    }

    DisposableEffect(context.applicationContext, overlayPreviewVisible) {
        if (!overlayPreviewVisible) {
            onDispose { }
        } else {
            val appContext = context.applicationContext
            val screenOffReceiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_SCREEN_OFF) {
                        overlayPreviewVisible = false
                    }
                }
            }
            val intentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            ContextCompat.registerReceiver(
                appContext,
                screenOffReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose {
                runCatching {
                    appContext.unregisterReceiver(screenOffReceiver)
                }
            }
        }
    }

    LaunchedEffect(
        overlayPreviewVisible,
        overlayBackgroundPortraitUri,
        overlayBackgroundLandscapeUri,
        overlayTransparencyPercent,
        preferences.bigAfter,
        preferences.bigFor,
        preferences.smallFor,
        breakShowPostponeButton,
        breakShowTitle,
        breakShowCountdown,
        breakShowExitButton,
        breakExitPostponeSeconds,
        breakOverlayFadeInDurationMs,
        breakOverlayFadeOutDurationMs,
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
            overlayBackgroundPortraitUri = overlayBackgroundPortraitUri,
            overlayBackgroundLandscapeUri = overlayBackgroundLandscapeUri,
            overlayTransparencyPercent = overlayTransparencyPercent,
            postponeOptions = preferences.postponeFor,
            showPostponeButton = breakShowPostponeButton,
            showTitle = breakShowTitle,
            showCountdown = breakShowCountdown,
            showExitButton = breakShowExitButton,
            exitPostponeSeconds = breakExitPostponeSeconds,
            overlayFadeInDurationMs = breakOverlayFadeInDurationMs,
            overlayFadeOutDurationMs = breakOverlayFadeOutDurationMs,
            topFlashSmallText = preferences.topFlashSmallText,
            topFlashBigText = preferences.topFlashBigText,
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
            value = preferences.smallEvery,
            minValue = 60,
            maxValue = 43200,
            defaultValue = defaultPreferences.smallEvery,
            onValueChange = { onPreferencesChange(preferences.copy(smallEvery = it)) },
        )
        NumberInputField(
            label = stringResource(R.string.settings_small_for),
            value = preferences.smallFor,
            minValue = 5,
            maxValue = 1800,
            defaultValue = defaultPreferences.smallFor,
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
                defaultValue = defaultPreferences.bigAfter,
                onValueChange = { onPreferencesChange(preferences.copy(bigAfter = it)) },
            )
            NumberInputField(
                label = stringResource(R.string.settings_big_for),
                value = preferences.bigFor,
                minValue = 10,
                maxValue = 3600,
                defaultValue = defaultPreferences.bigFor,
                onValueChange = { onPreferencesChange(preferences.copy(bigFor = it)) },
            )
        }

        Text(stringResource(R.string.settings_reminder), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_top_flash_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = preferences.topFlashEnabled,
                onCheckedChange = { enabled -> onPreferencesChange(preferences.copy(topFlashEnabled = enabled)) },
            )
        }
        if (preferences.topFlashEnabled) {
            NumberInputField(
                label = stringResource(R.string.settings_top_flash_lead_seconds),
                value = preferences.flashFor,
                minValue = 1,
                maxValue = 600,
                defaultValue = defaultPreferences.flashFor,
                onValueChange = { onPreferencesChange(preferences.copy(flashFor = it)) },
            )
            RequiredTextInputField(
                label = stringResource(R.string.settings_top_flash_small_message),
                value = preferences.topFlashSmallText,
                defaultValue = defaultPreferences.topFlashSmallText,
                required = false,
                onValueChange = { onPreferencesChange(preferences.copy(topFlashSmallText = it)) },
            )
            RequiredTextInputField(
                label = stringResource(R.string.settings_top_flash_big_message),
                value = preferences.topFlashBigText,
                defaultValue = defaultPreferences.topFlashBigText,
                required = false,
                onValueChange = { onPreferencesChange(preferences.copy(topFlashBigText = it)) },
            )
        }
        PostponeDurationsInputField(
            label = stringResource(R.string.settings_postpone_for),
            values = preferences.postponeFor,
            defaultValues = defaultPreferences.postponeFor,
            onValuesChange = { onPreferencesChange(preferences.copy(postponeFor = it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_break_show_postpone_button), modifier = Modifier.weight(1f))
            Switch(
                checked = breakShowPostponeButton,
                onCheckedChange = onBreakShowPostponeButtonChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_break_show_title), modifier = Modifier.weight(1f))
            Switch(
                checked = breakShowTitle,
                onCheckedChange = onBreakShowTitleChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_break_show_countdown), modifier = Modifier.weight(1f))
            Switch(
                checked = breakShowCountdown,
                onCheckedChange = onBreakShowCountdownChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_break_show_exit_button), modifier = Modifier.weight(1f))
            Switch(
                checked = breakShowExitButton,
                onCheckedChange = onBreakShowExitButtonChange,
            )
        }
        NumberInputField(
            label = stringResource(R.string.settings_break_exit_postpone_seconds),
            value = breakExitPostponeSeconds,
            minValue = 1,
            maxValue = 3600,
            defaultValue = 60,
            onValueChange = onBreakExitPostponeSecondsChange,
        )
        NumberInputField(
            label = stringResource(R.string.settings_break_overlay_fade_in_duration_ms),
            value = breakOverlayFadeInDurationMs,
            minValue = 0,
            maxValue = 5000,
            defaultValue = 300,
            onValueChange = onBreakOverlayFadeInDurationMsChange,
        )
        NumberInputField(
            label = stringResource(R.string.settings_break_overlay_fade_out_duration_ms),
            value = breakOverlayFadeOutDurationMs,
            minValue = 0,
            maxValue = 5000,
            defaultValue = 300,
            onValueChange = onBreakOverlayFadeOutDurationMsChange,
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

        Button(onClick = {
            onSpecificAppsPageOpened()
            openSubPage = true
        }) {
            Text(stringResource(R.string.settings_open_app_subpage))
        }
        Text(
            text = stringResource(R.string.settings_selected_apps_count, selectedPackages.size),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(stringResource(R.string.settings_overlay), style = MaterialTheme.typography.titleLarge)
        PercentageSliderField(
            label = stringResource(R.string.settings_overlay_transparency),
            value = overlayTransparencyPercent,
            onValueChange = onOverlayTransparencyPercentChange,
        )
        Button(
            onClick = onPickOverlayPortraitImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_pick_overlay_image_portrait))
        }
        Button(
            onClick = onClearOverlayPortraitImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_clear_overlay_image_portrait))
        }
        Text(
            text = if (overlayBackgroundPortraitUri.isBlank()) {
                stringResource(R.string.settings_overlay_image_portrait_none)
            } else {
                stringResource(R.string.settings_overlay_image_portrait_selected)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onPickOverlayLandscapeImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_pick_overlay_image_landscape))
        }
        Button(
            onClick = onClearOverlayLandscapeImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_clear_overlay_image_landscape))
        }
        Text(
            text = if (overlayBackgroundLandscapeUri.isBlank()) {
                stringResource(R.string.settings_overlay_image_landscape_none)
            } else {
                stringResource(R.string.settings_overlay_image_landscape_selected)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { overlayPreviewVisible = !overlayPreviewVisible },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_overlay_preview))
        }

        Text(stringResource(R.string.settings_notification), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_persistent_notification), modifier = Modifier.weight(1f))
            Switch(
                checked = persistentNotificationEnabled,
                onCheckedChange = onPersistentNotificationEnabledChange,
            )
        }
        NumberInputField(
            label = stringResource(R.string.settings_persistent_notification_update_frequency),
            value = persistentNotificationUpdateFrequencySeconds,
            minValue = 1,
            maxValue = 600,
            defaultValue = 10,
            onValueChange = onPersistentNotificationUpdateFrequencySecondsChange,
        )
        RequiredTextInputField(
            label = stringResource(R.string.settings_persistent_notification_title_template),
            value = persistentNotificationTitleTemplate,
            defaultValue = defaultPersistentNotificationTitleTemplate,
            required = false,
            onValueChange = onPersistentNotificationTitleTemplateChange,
        )
        RequiredTextInputField(
            label = stringResource(R.string.settings_persistent_notification_content_template),
            value = persistentNotificationContentTemplate,
            defaultValue = "",
            required = false,
            onValueChange = onPersistentNotificationContentTemplateChange,
        )
        Text(
            text = stringResource(R.string.settings_persistent_notification_placeholders),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_qs_tile_countdown_as_title), modifier = Modifier.weight(1f))
            Switch(
                checked = qsTileCountdownAsTitle,
                onCheckedChange = onQsTileCountdownAsTitleChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_pre_break_notification), modifier = Modifier.weight(1f))
            Switch(
                checked = preferences.preBreakNotificationEnabled,
                onCheckedChange = { enabled ->
                    onPreferencesChange(preferences.copy(preBreakNotificationEnabled = enabled))
                },
            )
        }
        if (preferences.preBreakNotificationEnabled) {
            NumberInputField(
                label = stringResource(R.string.settings_pre_break_notification_lead_seconds),
                value = preferences.preBreakNotificationLeadSeconds,
                minValue = 1,
                maxValue = 3600,
                defaultValue = defaultPreferences.preBreakNotificationLeadSeconds,
                onValueChange = { onPreferencesChange(preferences.copy(preBreakNotificationLeadSeconds = it)) },
            )
            RequiredTextInputField(
                label = stringResource(R.string.settings_pre_break_notification_small_title),
                value = preferences.preBreakNotificationSmallTitle,
                defaultValue = defaultPreferences.preBreakNotificationSmallTitle,
                required = false,
                onValueChange = { onPreferencesChange(preferences.copy(preBreakNotificationSmallTitle = it)) },
            )
            RequiredTextInputField(
                label = stringResource(R.string.settings_pre_break_notification_small_content),
                value = preferences.preBreakNotificationSmallContent,
                defaultValue = defaultPreferences.preBreakNotificationSmallContent,
                required = false,
                onValueChange = { onPreferencesChange(preferences.copy(preBreakNotificationSmallContent = it)) },
            )
            RequiredTextInputField(
                label = stringResource(R.string.settings_pre_break_notification_big_title),
                value = preferences.preBreakNotificationBigTitle,
                defaultValue = defaultPreferences.preBreakNotificationBigTitle,
                required = false,
                onValueChange = { onPreferencesChange(preferences.copy(preBreakNotificationBigTitle = it)) },
            )
            RequiredTextInputField(
                label = stringResource(R.string.settings_pre_break_notification_big_content),
                value = preferences.preBreakNotificationBigContent,
                defaultValue = defaultPreferences.preBreakNotificationBigContent,
                required = false,
                onValueChange = { onPreferencesChange(preferences.copy(preBreakNotificationBigContent = it)) },
            )
            Button(
                onClick = onOpenPreBreakNotificationChannelSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_open_pre_break_notification_channel))
            }
        }

        Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleLarge)
        ThemeModeDropdownRow(
            selectedMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_auto_start_on_boot), modifier = Modifier.weight(1f))
            Switch(checked = autoStartOnBoot, onCheckedChange = onAutoStartOnBootChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_restore_enabled_state_on_start), modifier = Modifier.weight(1f))
            Switch(checked = restoreEnabledStateOnStart, onCheckedChange = onRestoreEnabledStateOnStartChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_reenable_on_screen_unlock), modifier = Modifier.weight(1f))
            Switch(checked = reenableOnScreenUnlock, onCheckedChange = onReenableOnScreenUnlockChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_exclude_from_recents), modifier = Modifier.weight(1f))
            Switch(checked = excludeFromRecents, onCheckedChange = onExcludeFromRecentsChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeDropdownRow(
    selectedMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        AppThemeMode.FOLLOW_SYSTEM to stringResource(R.string.settings_theme_mode_follow_system),
        AppThemeMode.LIGHT to stringResource(R.string.settings_theme_mode_light),
        AppThemeMode.DARK to stringResource(R.string.settings_theme_mode_dark),
    )
    val selectedLabel = options.firstOrNull { it.first == selectedMode }?.second
        ?: stringResource(R.string.settings_theme_mode_follow_system)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_theme_mode),
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(180.dp),
        ) {
            TextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.menuAnchor(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(text = label) },
                        onClick = {
                            onThemeModeChange(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PercentageSliderField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val safeValue = value.coerceIn(OVERLAY_TRANSPARENCY_MIN, OVERLAY_TRANSPARENCY_MAX)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$safeValue%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Slider(
            value = safeValue.toFloat(),
            onValueChange = { sliderValue ->
                onValueChange(sliderValue.roundToInt().coerceIn(OVERLAY_TRANSPARENCY_MIN, OVERLAY_TRANSPARENCY_MAX))
            },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "0%", style = MaterialTheme.typography.labelSmall)
            Text(text = "100%", style = MaterialTheme.typography.labelSmall)
        }
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
private fun PostponeDurationsInputField(
    label: String,
    values: List<Int>,
    defaultValues: List<Int>,
    required: Boolean = true,
    onValuesChange: (List<Int>) -> Unit,
) {
    var text by remember(values) { mutableStateOf(formatPostponeDurations(values)) }
    var wasFocused by remember { mutableStateOf(false) }

    TextField(
        value = text,
        onValueChange = { input ->
            text = input
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused) {
                    val normalizedInput = normalizePostponeDurationInput(text)
                    val normalizedValues = if (required && normalizedInput.isBlank()) {
                        parsePostponeDurations(rawInput = null, fallback = defaultValues)
                    } else {
                        parsePostponeDurations(normalizedInput, fallback = defaultValues)
                    }
                    val normalizedText = formatPostponeDurations(normalizedValues)
                    text = normalizedText
                    onValuesChange(normalizedValues)
                }
                wasFocused = focusState.isFocused
            },
    )
}

@Composable
private fun RequiredTextInputField(
    label: String,
    value: String,
    defaultValue: String,
    required: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var wasFocused by remember { mutableStateOf(false) }

    TextField(
        value = text,
        onValueChange = { input ->
            text = input
            onValueChange(input)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused && required) {
                    val normalized = text.trim()
                    if (normalized.isBlank()) {
                        text = defaultValue
                        onValueChange(defaultValue)
                    } else if (normalized != text) {
                        text = normalized
                        onValueChange(normalized)
                    }
                }
                wasFocused = focusState.isFocused
            },
    )
}

@Composable
private fun NumberInputField(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    defaultValue: Int,
    required: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var wasFocused by remember { mutableStateOf(false) }
    val parsedValue = text.toIntOrNull()
    val showRangeWarning = text.isNotEmpty() && (parsedValue == null || parsedValue !in minValue..maxValue)

    TextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            text = digits
            val parsed = digits.toIntOrNull() ?: return@TextField
            if (parsed in minValue..maxValue) {
                onValueChange(parsed)
            }
        },
        label = { Text(label) },
        isError = showRangeWarning,
        supportingText = {
            if (showRangeWarning) {
                Text(
                    text = stringResource(R.string.settings_value_range, minValue, maxValue),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused && required) {
                    val parsed = text.toIntOrNull()
                    val isValid = parsed != null && parsed in minValue..maxValue
                    if (!isValid) {
                        val safeDefaultValue = defaultValue.coerceIn(minValue, maxValue)
                        text = safeDefaultValue.toString()
                        onValueChange(safeDefaultValue)
                    }
                }
                wasFocused = focusState.isFocused
            },
    )
}
fun parsePackageList(csv: String): Set<String> {
    return csv
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}
