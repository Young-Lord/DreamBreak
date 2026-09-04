package moe.lyniko.dreambreak.data.history

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.core.BreakUiState
import moe.lyniko.dreambreak.core.PauseReason
import moe.lyniko.dreambreak.core.SessionMode

private const val RECENT_POSTPONE_DECISION_LIMIT = 20

enum class RecordedRuntimeState {
    STOPPED,
    COUNTDOWN,
    BREAK,
    PAUSED_SCREEN_LOCK,
    PAUSED_APP,
    PAUSED_OTHER,
}

class HistoryRepository private constructor(
    private val historyDao: HistoryDao,
) {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeRecentPostponeDecisions(): Flow<List<PostponeDecisionEntity>> {
        return historyDao.observeRecentPostponeDecisions(RECENT_POSTPONE_DECISION_LIMIT)
    }

    fun recordPostponeDecision(
        confirmedAtEpochMillis: Long,
        delayDurationSeconds: Int,
        reason: String,
    ) {
        val decision = PostponeDecisionEntity(
            confirmedAtEpochMillis = confirmedAtEpochMillis,
            delayDurationSeconds = delayDurationSeconds.coerceAtLeast(1),
            reason = reason.trim(),
        )

        writeScope.launch {
            historyDao.insertPostponeDecision(decision)
        }
    }

    suspend fun recordRuntimeState(
        uiState: BreakUiState,
        occurredAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        historyDao.insertRuntimeStateEvent(
            RuntimeStateEventEntity(
                occurredAtEpochMillis = occurredAtEpochMillis,
                runtimeState = uiState.toRecordedRuntimeState().name,
                sessionMode = uiState.state.mode.name,
                pauseReasons = uiState.state.pauseReasons
                    .map(PauseReason::name)
                    .sorted()
                    .joinToString(","),
                secondsToNextBreak = uiState.state.secondsToNextBreak.coerceAtLeast(0),
            )
        )
    }

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: HistoryRepository(
                    DreamBreakDatabase.getInstance(context).historyDao()
                ).also { repository ->
                    instance = repository
                }
            }
        }
    }
}

fun BreakUiState.toRecordedRuntimeState(): RecordedRuntimeState {
    return when {
        !appEnabled -> RecordedRuntimeState.STOPPED
        state.pauseReasons.contains(PauseReason.SLEEP) -> RecordedRuntimeState.PAUSED_SCREEN_LOCK
        state.pauseReasons.contains(PauseReason.APP_OPEN) -> RecordedRuntimeState.PAUSED_APP
        state.mode == SessionMode.PAUSED -> RecordedRuntimeState.PAUSED_OTHER
        state.mode == SessionMode.BREAK -> RecordedRuntimeState.BREAK
        else -> RecordedRuntimeState.COUNTDOWN
    }
}
