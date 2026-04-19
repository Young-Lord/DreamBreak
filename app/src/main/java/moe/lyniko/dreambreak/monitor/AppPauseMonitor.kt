package moe.lyniko.dreambreak.monitor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.data.AppListMode

object AppPauseMonitor {
    private const val TAG = "DreamBreak"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startLock = Any()
    private var monitorJob: Job? = null
    private var lastShouldPause: Boolean? = null
    private var lastTopPackage: String? = null

    fun start(context: Context) {
        val appContext = context.applicationContext
        synchronized(startLock) {
            if (monitorJob?.isActive == true) {
                return
            }

            ForegroundAppMonitor.resetTracking()
            monitorJob = scope.launch {
                while (isActive) {
                    val uiState = BreakRuntime.uiState.value
                    if (!uiState.appEnabled) {
                        BreakRuntime.setAppPauseActive(false)
                        delay(CHECK_INTERVAL_MILLIS)
                        continue
                    }

                    val shouldPause = shouldPauseForForegroundApp(
                        context = appContext,
                        pauseInListedApps = uiState.pauseInListedApps,
                        appListMode = uiState.appListMode,
                        monitoredApps = uiState.monitoredApps,
                        monitoredAppsBlacklist = uiState.monitoredAppsBlacklist,
                    )
                    val topPackage = ForegroundAppMonitor.currentForegroundPackage(appContext)
                    if (shouldPause != lastShouldPause || topPackage != lastTopPackage) {
                        Log.d(
                            TAG,
                            "appPause transition shouldPause=$shouldPause (was $lastShouldPause) " +
                                "topPackage=$topPackage (was $lastTopPackage)",
                        )
                        lastShouldPause = shouldPause
                        lastTopPackage = topPackage
                    }
                    BreakRuntime.setAppPauseActive(shouldPause)
                    delay(CHECK_INTERVAL_MILLIS)
                }
            }
        }
    }

    private const val CHECK_INTERVAL_MILLIS = 2_000L
}

internal fun parseMonitoredPackageSet(csv: String): Set<String> {
    return csv
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

/**
 * Whether the break cycle should be paused based on the foreground app and list mode.
 *
 * Whitelist: pause while a selected app is in the foreground.
 * Blacklist: pause while the foreground app is not in the selected set (timer only runs in listed apps).
 */
fun shouldPauseForForegroundPackage(
    topPackage: String?,
    appListMode: AppListMode,
    monitoredAppsWhitelist: String,
    monitoredAppsBlacklist: String,
): Boolean {
    val whitelist = parseMonitoredPackageSet(monitoredAppsWhitelist)
    val blacklist = parseMonitoredPackageSet(monitoredAppsBlacklist)
    return when (appListMode) {
        AppListMode.WHITELIST -> {
            if (whitelist.isEmpty()) {
                return false
            }
            if (topPackage == null) {
                return false
            }
            whitelist.contains(topPackage)
        }
        AppListMode.BLACKLIST -> {
            if (blacklist.isEmpty()) {
                return true
            }
            if (topPackage == null) {
                return true
            }
            !blacklist.contains(topPackage)
        }
    }
}

fun shouldPauseForForegroundApp(
    context: Context,
    pauseInListedApps: Boolean,
    appListMode: AppListMode,
    monitoredApps: String,
    monitoredAppsBlacklist: String,
): Boolean {
    if (!pauseInListedApps) {
        return false
    }
    val topPackage = ForegroundAppMonitor.currentForegroundPackage(context)
    return shouldPauseForForegroundPackage(
        topPackage = topPackage,
        appListMode = appListMode,
        monitoredAppsWhitelist = monitoredApps,
        monitoredAppsBlacklist = monitoredAppsBlacklist,
    )
}
