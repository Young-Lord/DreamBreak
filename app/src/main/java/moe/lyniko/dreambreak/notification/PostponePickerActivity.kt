package moe.lyniko.dreambreak.notification

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.formatPostponeOption
import moe.lyniko.dreambreak.data.AppThemeMode
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.data.history.HistoryRepository
import moe.lyniko.dreambreak.startup.RuntimeBootstrap
import moe.lyniko.dreambreak.ui.theme.DreamBreakTheme

private const val MILLIS_PER_SECOND = 1_000L

class PostponePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BreakRuntime.start()
        applyExcludeFromRecents(BreakRuntime.uiState.value.excludeFromRecents)

        setContent {
            val context = LocalContext.current
            val settingsStore = remember(context.applicationContext) {
                SettingsStore(context.applicationContext)
            }
            val settings by settingsStore.settingsFlow.collectAsState(initial = null)
            LaunchedEffect(settings) {
                val loadedSettings = settings ?: return@LaunchedEffect
                RuntimeBootstrap.applySettings(loadedSettings)
                applyExcludeFromRecents(loadedSettings.excludeFromRecents)
            }
            val loadedSettings = settings ?: return@setContent
            val darkTheme = when (loadedSettings.themeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            DreamBreakTheme(darkTheme = darkTheme) {
                PostponeFlow(
                    options = loadedSettings.preferences.postponeFor,
                    reasonSubmitDelaySeconds = loadedSettings.postponeReasonSubmitDelaySeconds,
                    onConfirm = { seconds, reason ->
                        val accepted = BreakRuntime.postponeBreakForSeconds(seconds)
                        if (accepted) {
                            HistoryRepository.getInstance(applicationContext).recordPostponeDecision(
                                confirmedAtEpochMillis = System.currentTimeMillis(),
                                delayDurationSeconds = seconds,
                                reason = reason,
                            )
                        }
                        Log.d("DreamBreak", "PostponePicker selected seconds=$seconds accepted=$accepted")
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
        if (!exclude) {
            return
        }
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        activityManager.appTasks.forEach { task ->
            task.setExcludeFromRecents(true)
        }
        intent?.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}

private enum class PostponeStep {
    REASON,
    DURATION,
}

@Composable
private fun PostponeFlow(
    options: List<Int>,
    reasonSubmitDelaySeconds: Int,
    onConfirm: (seconds: Int, reason: String) -> Unit,
    onCancel: () -> Unit,
) {
    var currentStep by rememberSaveable { mutableStateOf(PostponeStep.REASON) }
    var reason by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = currentStep == PostponeStep.DURATION) {
        currentStep = PostponeStep.REASON
    }

    when (currentStep) {
        PostponeStep.REASON -> PostponeReasonScreen(
            reason = reason,
            reasonSubmitDelaySeconds = reasonSubmitDelaySeconds,
            onReasonChange = { reason = it },
            onBack = onCancel,
            onSubmit = { currentStep = PostponeStep.DURATION },
        )

        PostponeStep.DURATION -> PostponeDurationScreen(
            options = options,
            onSelect = { seconds -> onConfirm(seconds, reason.trim()) },
            onBack = { currentStep = PostponeStep.REASON },
        )
    }
}

@Composable
private fun PostponeReasonScreen(
    reason: String,
    reasonSubmitDelaySeconds: Int,
    onReasonChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val safeDelaySeconds = reasonSubmitDelaySeconds.coerceIn(0, 30)
    val enteredAtElapsedRealtime = rememberSaveable(safeDelaySeconds) { SystemClock.elapsedRealtime() }
    var remainingSeconds by rememberSaveable(safeDelaySeconds) {
        mutableLongStateOf(safeDelaySeconds.toLong())
    }

    LaunchedEffect(enteredAtElapsedRealtime, safeDelaySeconds) {
        val submitDelayMillis = safeDelaySeconds * MILLIS_PER_SECOND
        while (true) {
            val elapsedMillis = SystemClock.elapsedRealtime() - enteredAtElapsedRealtime
            val remainingMillis = (submitDelayMillis - elapsedMillis).coerceAtLeast(0L)
            remainingSeconds = (remainingMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND
            if (remainingMillis == 0L) {
                break
            }
            delay(200L)
        }
    }

    val canSubmit = reason.isNotBlank() && remainingSeconds == 0L
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.postpone_reason_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = reason,
                onValueChange = onReasonChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(stringResource(R.string.action_back))
                }
                Button(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    val submitText = if (remainingSeconds > 0L) {
                        stringResource(R.string.postpone_submit_countdown, remainingSeconds)
                    } else {
                        stringResource(R.string.action_submit)
                    }
                    Text(submitText)
                }
            }
        }
    }
}

@Composable
private fun PostponeDurationScreen(
    options: List<Int>,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
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
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}
