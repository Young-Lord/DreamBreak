package moe.lyniko.dreambreak.monitor

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.core.BreakRuntime

object AppPauseMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startLock = Any()
    private var monitorJob: Job? = null

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
                        monitoredApps = uiState.monitoredApps,
                    )
                    BreakRuntime.setAppPauseActive(shouldPause)
                    delay(CHECK_INTERVAL_MILLIS)
                }
            }
        }
    }

    private const val CHECK_INTERVAL_MILLIS = 2_000L
}

fun shouldPauseForForegroundApp(
    context: Context,
    pauseInListedApps: Boolean,
    monitoredApps: String,
): Boolean {
    if (!pauseInListedApps) {
        return false
    }

    val monitoredPackages = monitoredApps
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    if (monitoredPackages.isEmpty()) {
        return false
    }

    val topPackage = ForegroundAppMonitor.currentForegroundPackage(context) ?: return false
    return monitoredPackages.contains(topPackage)
}
