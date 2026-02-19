package moe.lyniko.dreambreak.notification

import android.os.Bundle
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
        setContent {
            val context = LocalContext.current
            val settingsStore = remember(context.applicationContext) {
                SettingsStore(context.applicationContext)
            }
            val settings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            DreamBreakTheme(darkTheme = darkTheme) {
                PostponePickerScreen(
                    onSelect = { seconds ->
                        BreakRuntime.postponeBreakForSeconds(seconds)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun PostponePickerScreen(
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val options = listOf(
        60 to R.string.postpone_option_1m,
        5 * 60 to R.string.postpone_option_5m,
        15 * 60 to R.string.postpone_option_15m,
        30 * 60 to R.string.postpone_option_30m,
    )

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
                    options.forEach { (seconds, labelRes) ->
                        Button(
                            onClick = { onSelect(seconds) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Text(
                                text = stringResource(labelRes),
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
