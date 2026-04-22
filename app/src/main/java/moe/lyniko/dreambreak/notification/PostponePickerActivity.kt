package moe.lyniko.dreambreak.notification

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.AppThemeMode
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.ui.theme.DreamBreakTheme

class PostponePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BreakRuntime.start()

        // Best-effort: avoid briefly appearing in recents before settings are loaded.
        applyExcludeFromRecents(BreakRuntime.uiState.value.excludeFromRecents)

        setContent {
            val context = LocalContext.current
            val settingsStore = remember(context.applicationContext) {
                SettingsStore(context.applicationContext)
            }
            val settings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())
            androidx.compose.runtime.LaunchedEffect(settings.excludeFromRecents) {
                applyExcludeFromRecents(settings.excludeFromRecents)
            }
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            DreamBreakTheme(darkTheme = darkTheme) {
                PostponePickerScreen(
                    options = settings.preferences.postponeFor,
                    onSelect = { seconds ->
                        Log.d("DreamBreak", "PostponePicker selected seconds=$seconds")
                        BreakRuntime.postponeBreakForSeconds(seconds)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyExcludeFromRecents(BreakRuntime.uiState.value.excludeFromRecents)
    }

    private fun applyExcludeFromRecents(exclude: Boolean) {
        if (!exclude) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        am.appTasks.forEach { task ->
            task.setExcludeFromRecents(true)
        }
        // Also set on intent for some OEM taskers.
        intent?.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}

@Composable
private fun PostponePickerScreen(
    options: List<Int>,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.postpone_choose_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    options.forEach { seconds ->
                        Button(
                            onClick = { onSelect(seconds) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Text(
                                text = formatPostponeOption(seconds),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

private fun formatPostponeOption(seconds: Int): String {
    return when {
        seconds % 3600 == 0 -> "${seconds / 3600}h"
        seconds % 60 == 0 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}
