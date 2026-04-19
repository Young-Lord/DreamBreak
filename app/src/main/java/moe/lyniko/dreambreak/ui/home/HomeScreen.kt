package moe.lyniko.dreambreak.ui.home

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakPreferences
import moe.lyniko.dreambreak.core.BreakState
import moe.lyniko.dreambreak.core.SessionMode

private const val HOME_LOG_TAG = "DreamBreak"

@Composable
fun HomePage(
    state: BreakState,
    preferences: BreakPreferences,
    appEnabled: Boolean,
    breakCycleEnableBlocked: Boolean,
    onAppEnabledChange: (Boolean) -> Unit,
    onBreakNow: () -> Unit,
    onBigBreakNow: () -> Unit,
    onPostpone: () -> Unit,
) {
    val homeToggleBackgroundColor by animateColorAsState(
        targetValue = if (appEnabled) Color(0xFF0072B2) else Color(0xFFD55E00),
        label = "homeToggleBackgroundColor",
    )
    val nextBreaks = remember(state, preferences) { buildHomeNextBreaks(state, preferences) }

    LaunchedEffect(
        state.mode,
        state.modeBeforePause,
        state.phase,
        state.breakSecondsRemaining,
        state.secondsToNextBreak,
        state.breakCycleCount,
        preferences.smallEvery,
        preferences.bigAfter,
    ) {
        val built = buildHomeNextBreaks(state, preferences)
        Log.d(
            HOME_LOG_TAG,
            "HomeScreen nextBreaks=${built.map { "${it.isBigBreak}:${it.secondsUntil}" }} " +
                "mode=${state.mode} modeBeforePause=${state.modeBeforePause} phase=${state.phase} " +
                "breakSecondsRemaining=${state.breakSecondsRemaining} secondsToNextBreak=${state.secondsToNextBreak}",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (breakCycleEnableBlocked) {
            Text(
                text = stringResource(R.string.home_break_cycle_locked_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = { onAppEnabledChange(!appEnabled) },
            enabled = appEnabled || !breakCycleEnableBlocked,
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
                    } else if (breakCycleEnableBlocked) {
                        stringResource(R.string.home_toggle_locked)
                    } else {
                        stringResource(R.string.home_toggle_enable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        nextBreaks.forEachIndexed { index, item ->
            val labelRes = if (item.isBigBreak) {
                R.string.home_next_big_break
            } else {
                R.string.home_next_small_break
            }
            Text(
                text = stringResource(labelRes, formatMinutesSecondsNoLeadingZero(item.secondsUntil)),
                style = if (index == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            )
        }

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
            text = stringResource(R.string.action_postpone),
            icon = Icons.Default.Home,
            onClick = onPostpone,
            enabled = appEnabled,
        )
    }
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

private data class HomeNextBreak(
    val isBigBreak: Boolean,
    val secondsUntil: Int,
)

private fun buildHomeNextBreaks(state: BreakState, preferences: BreakPreferences): List<HomeNextBreak> {
    val nextSmall = secondsUntilBreakType(
        state = state,
        preferences = preferences,
        targetBigBreak = false,
    )
    val nextBig = secondsUntilBreakType(
        state = state,
        preferences = preferences,
        targetBigBreak = true,
    )

    return listOfNotNull(
        nextSmall?.let { HomeNextBreak(isBigBreak = false, secondsUntil = it) },
        nextBig?.let { HomeNextBreak(isBigBreak = true, secondsUntil = it) },
    ).sortedBy { it.secondsUntil }
}

private fun isEffectiveBreakSession(state: BreakState): Boolean {
    return state.mode == SessionMode.BREAK ||
        (state.mode == SessionMode.PAUSED && state.modeBeforePause == SessionMode.BREAK)
}

private fun secondsUntilBreakType(
    state: BreakState,
    preferences: BreakPreferences,
    targetBigBreak: Boolean,
): Int? {
    if (targetBigBreak && preferences.bigAfter <= 0) {
        return null
    }

    val intervalSeconds = preferences.smallEvery.coerceAtLeast(1)
    val inBreakSession = isEffectiveBreakSession(state)
    val baseCycle = if (inBreakSession) {
        state.breakCycleCount
    } else {
        state.breakCycleCount + 1
    }
    val baseSeconds = if (inBreakSession) {
        0
    } else {
        state.secondsToNextBreak.coerceAtLeast(0)
    }

    val searchLimit = baseCycle + preferences.bigAfter.coerceAtLeast(1) * 4 + 32
    for (cycle in baseCycle..searchLimit) {
        val isBigCycle = preferences.bigAfter > 0 && cycle % preferences.bigAfter == 0
        if (isBigCycle == targetBigBreak) {
            return baseSeconds + (cycle - baseCycle) * intervalSeconds
        }
    }

    return null
}

private fun formatMinutesSecondsNoLeadingZero(rawSeconds: Int): String {
    val seconds = rawSeconds.coerceAtLeast(0)
    val minutePart = seconds / 60
    val secondPart = seconds % 60
    return "$minutePart:${secondPart.toString().padStart(2, '0')}"
}
