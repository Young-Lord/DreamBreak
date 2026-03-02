package moe.lyniko.dreambreak.monitor

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.Context
import android.os.Process
import android.util.Log

object ForegroundAppMonitor {
    fun resetTracking() {
        synchronized(stateLock) {
            lastEventQueryEndTimeMillis = 0L
            lastForegroundPackage = null
            lastForegroundEventTimestampMillis = 0L
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val debugEnabled = isDebugBuild(context)
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        val allowed = mode == AppOpsManager.MODE_ALLOWED
        logUsageAccess(
            mode = mode,
            allowed = allowed,
            packageName = context.packageName,
            debugEnabled = debugEnabled,
        )
        return allowed
    }

    fun currentForegroundPackage(context: Context): String? {
        val debugEnabled = isDebugBuild(context)
        if (!hasUsageAccess(context)) {
            resetTracking()
            logForegroundResult(
                source = "no_access",
                resultPackage = null,
                totalEvents = 0,
                matchedEvents = 0,
                lastMatchedEventType = -1,
                queryWindowStartMillis = -1L,
                queryWindowEndMillis = -1L,
                latestForegroundEventMillis = -1L,
                debugEnabled = debugEnabled,
            )
            return null
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val queryWindowStart = synchronized(stateLock) {
            val previousQueryEnd = lastEventQueryEndTimeMillis
            if (previousQueryEnd <= 0L || now - previousQueryEnd > INCREMENTAL_GAP_RESET_MILLIS) {
                now - INITIAL_LOOKBACK_MILLIS
            } else {
                (previousQueryEnd - QUERY_OVERLAP_MILLIS).coerceAtMost(now)
            }
        }
        val events = usageStatsManager.queryEvents(queryWindowStart, now)
        val event = UsageEvents.Event()
        var topPackage: String? = null
        var totalEvents = 0
        var matchedEvents = 0
        var lastMatchedEventType = -1
        var latestForegroundEventMillis = -1L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            totalEvents += 1
            if (isForegroundEventType(event.eventType)) {
                topPackage = event.packageName
                matchedEvents += 1
                lastMatchedEventType = event.eventType
                latestForegroundEventMillis = event.timeStamp
            }
        }

        val (resultPackage, source, latestKnownForegroundEventMillis) = synchronized(stateLock) {
            lastEventQueryEndTimeMillis = now
            if (!topPackage.isNullOrBlank()) {
                lastForegroundPackage = topPackage
                lastForegroundEventTimestampMillis = latestForegroundEventMillis
                Triple(topPackage, "usage_events_incremental", latestForegroundEventMillis)
            } else if (!lastForegroundPackage.isNullOrBlank()) {
                Triple(lastForegroundPackage, "usage_events_cached", lastForegroundEventTimestampMillis)
            } else {
                Triple(null, "usage_events_empty", -1L)
            }
        }

        logForegroundResult(
            source = source,
            resultPackage = resultPackage,
            totalEvents = totalEvents,
            matchedEvents = matchedEvents,
            lastMatchedEventType = lastMatchedEventType,
            queryWindowStartMillis = queryWindowStart,
            queryWindowEndMillis = now,
            latestForegroundEventMillis = latestKnownForegroundEventMillis,
            debugEnabled = debugEnabled,
        )
        return resultPackage
    }

    @Suppress("DEPRECATION")
    private fun isForegroundEventType(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    private fun logUsageAccess(mode: Int, allowed: Boolean, packageName: String, debugEnabled: Boolean) {
        if (!debugEnabled) {
            return
        }
        if (allowed == lastLoggedUsageAccessAllowed && mode == lastLoggedUsageAccessMode) {
            return
        }

        lastLoggedUsageAccessAllowed = allowed
        lastLoggedUsageAccessMode = mode
        Log.d(
            TAG,
            "hasUsageAccess allowed=$allowed mode=${appOpsModeName(mode)} package=$packageName",
        )
    }

    private fun appOpsModeName(mode: Int): String {
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> "MODE_ALLOWED"
            AppOpsManager.MODE_DEFAULT -> "MODE_DEFAULT"
            AppOpsManager.MODE_ERRORED -> "MODE_ERRORED"
            AppOpsManager.MODE_IGNORED -> "MODE_IGNORED"
            AppOpsManager.MODE_FOREGROUND -> "MODE_FOREGROUND"
            else -> "MODE_$mode"
        }
    }

    private fun logForegroundResult(
        source: String,
        resultPackage: String?,
        totalEvents: Int,
        matchedEvents: Int,
        lastMatchedEventType: Int,
        queryWindowStartMillis: Long,
        queryWindowEndMillis: Long,
        latestForegroundEventMillis: Long,
        debugEnabled: Boolean,
    ) {
        if (!debugEnabled) {
            return
        }

        val now = System.currentTimeMillis()
        val signature = "$source|${resultPackage ?: "none"}"
        val logRecentlyPrinted = now - lastLoggedForegroundAtMillis < RESULT_LOG_MIN_INTERVAL_MILLIS
        if (signature == lastLoggedForegroundSignature && logRecentlyPrinted) {
            return
        }

        lastLoggedForegroundSignature = signature
        lastLoggedForegroundAtMillis = now
        Log.d(
            TAG,
            "currentForegroundPackage source=$source result=${resultPackage ?: "none"} " +
                "queryWindow=$queryWindowStartMillis..$queryWindowEndMillis " +
                "eventsTotal=$totalEvents eventsMatched=$matchedEvents " +
                "lastEvent=${usageEventTypeName(lastMatchedEventType)} " +
                "latestForegroundTs=$latestForegroundEventMillis",
        )
    }

    @Suppress("DEPRECATION")
    private fun usageEventTypeName(eventType: Int): String {
        return when (eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> "ACTIVITY_RESUMED"
            UsageEvents.Event.ACTIVITY_PAUSED -> "ACTIVITY_PAUSED"
            UsageEvents.Event.ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
            UsageEvents.Event.MOVE_TO_FOREGROUND -> "MOVE_TO_FOREGROUND"
            UsageEvents.Event.MOVE_TO_BACKGROUND -> "MOVE_TO_BACKGROUND"
            -1 -> "NONE"
            else -> "EVENT_$eventType"
        }
    }

    private fun isDebugBuild(context: Context): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private var lastLoggedUsageAccessAllowed: Boolean? = null
    private var lastLoggedUsageAccessMode: Int? = null
    private var lastLoggedForegroundSignature: String? = null
    private var lastLoggedForegroundAtMillis: Long = 0L

    private val stateLock = Any()
    private var lastEventQueryEndTimeMillis: Long = 0L
    private var lastForegroundPackage: String? = null
    private var lastForegroundEventTimestampMillis: Long = 0L

    private const val TAG = "ForegroundAppMonitor"
    private const val RESULT_LOG_MIN_INTERVAL_MILLIS = 10_000L
    private const val INITIAL_LOOKBACK_MILLIS = 60_000L
    private const val QUERY_OVERLAP_MILLIS = 1_000L
    private const val INCREMENTAL_GAP_RESET_MILLIS = 2 * 60 * 1000L
}
