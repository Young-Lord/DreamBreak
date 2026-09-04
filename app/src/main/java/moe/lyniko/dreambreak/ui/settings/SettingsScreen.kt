package moe.lyniko.dreambreak.ui.settings

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.app.StatusBarManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakRuntime
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
import moe.lyniko.dreambreak.data.AppListMode
import moe.lyniko.dreambreak.data.AppThemeMode
import moe.lyniko.dreambreak.data.QsTileClickAction
import moe.lyniko.dreambreak.monitor.InstalledApp
import moe.lyniko.dreambreak.notification.BreakReminderService
import moe.lyniko.dreambreak.overlay.BreakOverlayController
import moe.lyniko.dreambreak.tile.DreamBreakTileService
import moe.lyniko.dreambreak.data.SettingsStore

private const val OVERLAY_PREVIEW_AUTO_DISMISS_MS = 20_000L
private const val SMALL_EVERY_MIN_SECONDS = 60
private const val SMALL_EVERY_MAX_SECONDS = 43_200
private const val SMALL_FOR_MIN_SECONDS = 1
private const val SMALL_FOR_MAX_SECONDS = 1_800

private const val BIG_AFTER_MIN_CYCLES = 1
private const val BIG_AFTER_MAX_CYCLES = 20
private const val BIG_FOR_MIN_SECONDS = 1
private const val BIG_FOR_MAX_SECONDS = 3_600

private const val BREAK_OVERLAY_FADE_DURATION_MIN_MS = 0
private const val BREAK_OVERLAY_FADE_DURATION_MAX_MS = 40_000
private const val BREAK_OVERLAY_FADE_DURATION_DEFAULT_MS = 300

@Composable
fun SettingsPage(
    preferences: BreakPreferences,
    pauseInListedApps: Boolean,
    appListMode: AppListMode,
    monitoredApps: String,
    monitoredAppsBlacklist: String,
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
    hasAddedQsTile: Boolean,
    qsTileCountdownAsTitle: Boolean,
    qsTileClickAction: QsTileClickAction,
    breakShowPostponeButton: Boolean,
    postponeReasonSubmitDelaySeconds: Int,
    breakShowTitle: Boolean,
    breakShowCountdown: Boolean,
    breakShowExitButton: Boolean,
    breakExitPostponeSeconds: Int,
    breakOverlayFadeInDurationMs: Int,
    breakOverlayFadeOutDurationMs: Int,
    breakOverlayFadeOutKeepOpaque: Boolean,
    themeMode: AppThemeMode,
    onPreferencesChange: (BreakPreferences) -> Unit,
    onPauseInListedAppsChange: (Boolean) -> Unit,
    onAppListModeChange: (AppListMode) -> Unit,
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
    onQsTileClickActionChange: (QsTileClickAction) -> Unit,
    onBreakShowPostponeButtonChange: (Boolean) -> Unit,
    onPostponeReasonSubmitDelaySecondsChange: (Int) -> Unit,
    onBreakShowTitleChange: (Boolean) -> Unit,
    onBreakShowCountdownChange: (Boolean) -> Unit,
    onBreakShowExitButtonChange: (Boolean) -> Unit,
    onBreakExitPostponeSecondsChange: (Int) -> Unit,
    onBreakOverlayFadeInDurationMsChange: (Int) -> Unit,
    onBreakOverlayFadeOutDurationMsChange: (Int) -> Unit,
    onBreakOverlayFadeOutKeepOpaqueChange: (Boolean) -> Unit,
    onOpenPreBreakNotificationChannelSettings: () -> Unit,
    onSpecificAppsPageOpened: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val defaultPreferences = remember { BreakPreferences() }
    val defaultPersistentNotificationTitleTemplate = remember {
        DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE
    }
    val appSearch = rememberTextFieldState()
    var showPauseAppListPage by remember { mutableStateOf(false) }
    var overlayPreviewVisible by remember { mutableStateOf(false) }
    val previewController = remember(context.applicationContext) {
        BreakOverlayController(
            context = context.applicationContext,
            onExitBreak = { _ -> overlayPreviewVisible = false },
            onOpenPostponePicker = { overlayPreviewVisible = false },
            onDismissRequest = { overlayPreviewVisible = false },
        )
    }

    DisposableEffect(previewController) {
        onDispose {
            previewController.release()
        }
    }

    LaunchedEffect(showPauseAppListPage) {
        if (showPauseAppListPage) {
            overlayPreviewVisible = false
        }
    }

    BackHandler(enabled = showPauseAppListPage) {
        showPauseAppListPage = false
    }

    BackHandler(enabled = overlayPreviewVisible && !showPauseAppListPage) {
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
        breakOverlayFadeOutKeepOpaque,
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
            showPostponeButton = breakShowPostponeButton,
            showTitle = breakShowTitle,
            showCountdown = breakShowCountdown,
            showExitButton = breakShowExitButton,
            exitPostponeSeconds = breakExitPostponeSeconds,
            overlayFadeInDurationMs = breakOverlayFadeInDurationMs,
            overlayFadeOutDurationMs = breakOverlayFadeOutDurationMs,
            overlayFadeOutKeepOpaque = breakOverlayFadeOutKeepOpaque,
            topFlashSmallText = preferences.topFlashSmallText,
            topFlashBigText = preferences.topFlashBigText,
        )
    }

    val activeMonitoredAppsCsv = when (appListMode) {
        AppListMode.WHITELIST -> monitoredApps
        AppListMode.BLACKLIST -> monitoredAppsBlacklist
    }
    val selectedPackages = remember(activeMonitoredAppsCsv) {
        parsePackageList(activeMonitoredAppsCsv)
    }

    if (showPauseAppListPage) {
        // 仅当列表页可见时才计算过滤结果，并用 remember 缓存，避免每次输入都重排整页列表。
        val searchText = appSearch.text.toString()
        val filteredApps = remember(searchText, installedApps) {
            installedApps.filter {
                searchText.isBlank() ||
                    it.label.contains(searchText, ignoreCase = true) ||
                    it.packageName.contains(searchText, ignoreCase = true)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(
                    onClick = { showPauseAppListPage = false },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    text = stringResource(
                        when (appListMode) {
                            AppListMode.WHITELIST -> R.string.settings_pause_app_list_screen_title_whitelist
                            AppListMode.BLACKLIST -> R.string.settings_pause_app_list_screen_title_blacklist
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(start = 56.dp, end = 16.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppSelectionSection(
                    appSearch = appSearch,
                    selectedPackages = selectedPackages,
                    filteredApps = filteredApps,
                    onMonitoredAppsChange = onMonitoredAppsChange,
                )
            }
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
            minValue = SMALL_EVERY_MIN_SECONDS,
            maxValue = SMALL_EVERY_MAX_SECONDS,
            defaultValue = defaultPreferences.smallEvery,
            onValueChange = { onPreferencesChange(preferences.copy(smallEvery = it)) },
        )
        NumberInputField(
            label = stringResource(R.string.settings_small_for),
            value = preferences.smallFor,
            minValue = SMALL_FOR_MIN_SECONDS,
            maxValue = SMALL_FOR_MAX_SECONDS,
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
                minValue = BIG_AFTER_MIN_CYCLES,
                maxValue = BIG_AFTER_MAX_CYCLES,
                defaultValue = defaultPreferences.bigAfter,
                onValueChange = { onPreferencesChange(preferences.copy(bigAfter = it)) },
            )
            NumberInputField(
                label = stringResource(R.string.settings_big_for),
                value = preferences.bigFor,
                minValue = BIG_FOR_MIN_SECONDS,
                maxValue = BIG_FOR_MAX_SECONDS,
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
        NumberInputField(
            label = stringResource(R.string.settings_postpone_reason_submit_delay_seconds),
            value = postponeReasonSubmitDelaySeconds,
            minValue = 0,
            maxValue = 30,
            defaultValue = 5,
            onValueChange = onPostponeReasonSubmitDelaySecondsChange,
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
            minValue = BREAK_OVERLAY_FADE_DURATION_MIN_MS,
            maxValue = BREAK_OVERLAY_FADE_DURATION_MAX_MS,
            defaultValue = BREAK_OVERLAY_FADE_DURATION_DEFAULT_MS,
            onValueChange = onBreakOverlayFadeInDurationMsChange,
        )
        NumberInputField(
            label = stringResource(R.string.settings_break_overlay_fade_out_duration_ms),
            value = breakOverlayFadeOutDurationMs,
            minValue = BREAK_OVERLAY_FADE_DURATION_MIN_MS,
            maxValue = BREAK_OVERLAY_FADE_DURATION_MAX_MS,
            defaultValue = BREAK_OVERLAY_FADE_DURATION_DEFAULT_MS,
            onValueChange = onBreakOverlayFadeOutDurationMsChange,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_break_overlay_fade_out_keep_opaque),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = breakOverlayFadeOutKeepOpaque,
                onCheckedChange = onBreakOverlayFadeOutKeepOpaqueChange,
            )
        }

        Text(stringResource(R.string.settings_pause), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_app_list_enabled), modifier = Modifier.weight(1f))
            Switch(checked = pauseInListedApps, onCheckedChange = onPauseInListedAppsChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_app_blacklist_mode), modifier = Modifier.weight(1f))
            Switch(
                checked = appListMode == AppListMode.BLACKLIST,
                onCheckedChange = { onAppListModeChange(if (it) AppListMode.BLACKLIST else AppListMode.WHITELIST) },
                enabled = pauseInListedApps,
            )
        }
        Text(
            text = stringResource(
                when (appListMode) {
                    AppListMode.WHITELIST -> R.string.settings_app_list_hint_whitelist
                    AppListMode.BLACKLIST -> R.string.settings_app_list_hint_blacklist
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasUsageAccess) {
            Text(
                text = stringResource(R.string.settings_no_usage_access),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                onSpecificAppsPageOpened()
                showPauseAppListPage = true
            },
            enabled = pauseInListedApps,
        ) {
            Text(stringResource(R.string.settings_manage_pause_app_list))
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

        Text(stringResource(R.string.settings_qs_tile), style = MaterialTheme.typography.titleLarge)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasAddedQsTile) {
            Text(
                text = stringResource(R.string.settings_qs_tile_not_added_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    requestAddQuickSettingsTile(context) { confirmedAdded ->
                        if (!confirmedAdded) return@requestAddQuickSettingsTile
                        BreakRuntime.setHasAddedQsTile(true)
                        scope.launch {
                            SettingsStore(appContext).setHasAddedQsTile(true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_qs_tile_request_add))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_qs_tile_countdown_as_title), modifier = Modifier.weight(1f))
            Switch(
                checked = qsTileCountdownAsTitle,
                onCheckedChange = onQsTileCountdownAsTitleChange,
            )
        }
        QsTileClickActionDropdownRow(
            selectedAction = qsTileClickAction,
            onQsTileClickActionChange = onQsTileClickActionChange,
        )

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

private fun requestAddQuickSettingsTile(
    context: Context,
    onConfirmedAdded: (Boolean) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = ContextCompat.getSystemService(context, StatusBarManager::class.java)
            ?: return
        val component = ComponentName(context, DreamBreakTileService::class.java)
        statusBarManager.requestAddTileService(
            component,
            context.getString(R.string.qs_tile_label),
            AndroidIcon.createWithResource(context, R.drawable.ic_qs_bed),
            context.mainExecutor,
        ) { result ->
            val confirmed = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            onConfirmedAdded(confirmed)
        }
        return
    }

    onConfirmedAdded(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QsTileClickActionDropdownRow(
    selectedAction: QsTileClickAction,
    onQsTileClickActionChange: (QsTileClickAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        QsTileClickAction.NONE to stringResource(R.string.settings_qs_tile_click_action_none),
        QsTileClickAction.OPEN_APP to stringResource(R.string.settings_qs_tile_click_action_open_app),
        QsTileClickAction.OPEN_POSTPONE_PICKER to stringResource(R.string.settings_qs_tile_click_action_open_postpone),
        QsTileClickAction.TOGGLE_ENABLED to stringResource(R.string.settings_qs_tile_click_action_toggle),
    )
    val selectedLabel = options.firstOrNull { it.first == selectedAction }?.second
        ?: stringResource(R.string.settings_qs_tile_click_action_toggle)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_qs_tile_click_action),
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(180.dp),
        ) {
            val textState = remember(selectedLabel) { TextFieldState(selectedLabel) }
            TextField(
                state = textState,
                readOnly = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (action, label) ->
                    DropdownMenuItem(
                        text = { Text(text = label) },
                        onClick = {
                            onQsTileClickActionChange(action)
                            expanded = false
                        },
                    )
                }
            }
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
            val textState = remember(selectedLabel) { TextFieldState(selectedLabel) }
            TextField(
                state = textState,
                readOnly = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                ),
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
    appSearch: TextFieldState,
    selectedPackages: Set<String>,
    filteredApps: List<InstalledApp>,
    onMonitoredAppsChange: (String) -> Unit,
) {
    TextField(
        state = appSearch,
        modifier = Modifier
            .fillMaxWidth()
            .doubleTapSelectAll {
                if (appSearch.text.isNotEmpty()) {
                    appSearch.edit { selectAll() }
                }
            },
        label = { Text(stringResource(R.string.settings_pause_app_list_filter)) },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
    Text(
        text = stringResource(R.string.settings_selected_apps_count, selectedPackages.size),
        style = MaterialTheme.typography.bodyMedium,
    )

    filteredApps.forEach { app ->
        key(app.packageName) {
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
}

/**
 * 在 TextField 上叠加“双击全选”手势。
 *
 * 新 TextField / BasicTextField 双击默认选词，不会全选。这里手动检测双击，并在确认后
 * 消费第二段按下/抬起事件，避免内部再把选区收成光标。滚动、长按等会被消费或取消的
 * 手势不会误触发全选。
 *
 * 用 [modifier.composed] + [rememberUpdatedState] 是因为 pointerInput 协程只在首次
 * 组合时启动一次，直接捕获 onDoubleTap 会拿到过期的闭包，必须保证每次双击都执行最新回调。
 */
private fun Modifier.doubleTapSelectAll(
    onDoubleTap: () -> Unit,
): Modifier = composed {
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    pointerInput(Unit) {
        val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            awaitUpOrNullIgnoringConsumed() ?: return@awaitEachGesture
            val secondDown = withTimeoutOrNull(doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false)
            } ?: return@awaitEachGesture
            secondDown.consume()
            val secondUp = awaitUpOrNullIgnoringConsumed() ?: return@awaitEachGesture
            secondUp.consume()
            currentOnDoubleTap()
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitUpOrNullIgnoringConsumed(): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.all { it.changedToUpIgnoreConsumed() }) {
            return event.changes[0]
        }
        if (event.changes.any { it.isOutOfBounds(size, extendedTouchPadding) }) {
            return null
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
    val textState = remember { TextFieldState(formatPostponeDurations(values)) }
    var wasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(values) {
        val formatted = formatPostponeDurations(values)
        if (!wasFocused && textState.text.toString() != formatted) {
            textState.edit { replace(0, length, formatted) }
        }
    }

    TextField(
        state = textState,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused) {
                    val normalizedInput = normalizePostponeDurationInput(textState.text.toString())
                    val normalizedValues = if (required && normalizedInput.isBlank()) {
                        parsePostponeDurations(rawInput = null, fallback = defaultValues)
                    } else {
                        parsePostponeDurations(normalizedInput, fallback = defaultValues)
                    }
                    val normalizedText = formatPostponeDurations(normalizedValues)
                    if (textState.text.toString() != normalizedText) {
                        textState.edit { replace(0, length, normalizedText) }
                    }
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
    val textState = remember { TextFieldState(value) }
    var wasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .collect { current -> onValueChange(current) }
    }

    LaunchedEffect(value) {
        if (!wasFocused && textState.text.toString() != value) {
            textState.edit { replace(0, length, value) }
        }
    }

    TextField(
        state = textState,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused && required) {
                    val normalized = textState.text.toString().trim()
                    if (normalized.isBlank()) {
                        textState.edit { replace(0, length, defaultValue) }
                        onValueChange(defaultValue)
                    } else if (normalized != textState.text.toString()) {
                        textState.edit { replace(0, length, normalized) }
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
    val textState = remember { TextFieldState(value.toString()) }
    var wasFocused by remember { mutableStateOf(false) }
    val currentText = textState.text.toString()
    val parsedValue = currentText.toIntOrNull()
    val showRangeWarning = currentText.isNotEmpty() && (parsedValue == null || parsedValue !in minValue..maxValue)

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .collect { current ->
                val digits = current.filter { it.isDigit() }
                if (digits != current) {
                    textState.edit { replace(0, length, digits) }
                    return@collect
                }
                val parsed = digits.toIntOrNull()
                if (parsed != null && parsed in minValue..maxValue) {
                    onValueChange(parsed)
                }
            }
    }

    LaunchedEffect(value) {
        if (!wasFocused && textState.text.toString() != value.toString()) {
            textState.edit { replace(0, length, value.toString()) }
        }
    }

    TextField(
        state = textState,
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
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused && required) {
                    val parsed = textState.text.toString().toIntOrNull()
                    val isValid = parsed != null && parsed in minValue..maxValue
                    if (!isValid) {
                        val safeDefaultValue = defaultValue.coerceIn(minValue, maxValue)
                        textState.edit { replace(0, length, safeDefaultValue.toString()) }
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
