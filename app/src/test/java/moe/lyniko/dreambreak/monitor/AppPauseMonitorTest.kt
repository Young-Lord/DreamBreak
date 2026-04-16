package moe.lyniko.dreambreak.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.lyniko.dreambreak.data.AppListMode

class AppPauseMonitorTest {
    @Test
    fun whitelist_emptyList_neverPauses() {
        assertFalse(
            shouldPauseForForegroundPackage(
                topPackage = "com.example.app",
                appListMode = AppListMode.WHITELIST,
                monitoredAppsWhitelist = "",
                monitoredAppsBlacklist = "com.other",
            ),
        )
    }

    @Test
    fun whitelist_topInList_pauses() {
        assertTrue(
            shouldPauseForForegroundPackage(
                topPackage = "com.example.app",
                appListMode = AppListMode.WHITELIST,
                monitoredAppsWhitelist = "com.example.app",
                monitoredAppsBlacklist = "",
            ),
        )
    }

    @Test
    fun whitelist_topNull_noPause() {
        assertFalse(
            shouldPauseForForegroundPackage(
                topPackage = null,
                appListMode = AppListMode.WHITELIST,
                monitoredAppsWhitelist = "com.example.app",
                monitoredAppsBlacklist = "",
            ),
        )
    }

    @Test
    fun blacklist_emptyList_alwaysPauses() {
        assertTrue(
            shouldPauseForForegroundPackage(
                topPackage = "com.example.app",
                appListMode = AppListMode.BLACKLIST,
                monitoredAppsWhitelist = "",
                monitoredAppsBlacklist = "",
            ),
        )
    }

    @Test
    fun blacklist_topInList_noPause() {
        assertFalse(
            shouldPauseForForegroundPackage(
                topPackage = "com.example.app",
                appListMode = AppListMode.BLACKLIST,
                monitoredAppsWhitelist = "",
                monitoredAppsBlacklist = "com.example.app",
            ),
        )
    }

    @Test
    fun blacklist_topNotInList_pauses() {
        assertTrue(
            shouldPauseForForegroundPackage(
                topPackage = "com.other",
                appListMode = AppListMode.BLACKLIST,
                monitoredAppsWhitelist = "",
                monitoredAppsBlacklist = "com.example.app",
            ),
        )
    }

    @Test
    fun blacklist_topNull_pauses() {
        assertTrue(
            shouldPauseForForegroundPackage(
                topPackage = null,
                appListMode = AppListMode.BLACKLIST,
                monitoredAppsWhitelist = "",
                monitoredAppsBlacklist = "com.example.app",
            ),
        )
    }
}
