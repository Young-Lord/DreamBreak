package moe.lyniko.dreambreak.data.history

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.core.BreakRuntime

object RuntimeHistoryRecorder {
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Any()
    private var recorderJob: Job? = null

    fun start(context: Context) {
        synchronized(startLock) {
            if (recorderJob?.isActive == true) {
                return
            }

            val repository = HistoryRepository.getInstance(context.applicationContext)
            recorderJob = recorderScope.launch {
                BreakRuntime.uiState
                    .distinctUntilChangedBy { uiState -> uiState.toRecordedRuntimeState() }
                    .collect { uiState ->
                        repository.recordRuntimeState(uiState)
                    }
            }
        }
    }
}
