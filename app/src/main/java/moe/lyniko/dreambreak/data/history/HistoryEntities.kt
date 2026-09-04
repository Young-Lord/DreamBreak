package moe.lyniko.dreambreak.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "runtime_state_events",
    indices = [Index(value = ["occurredAtEpochMillis"])],
)
data class RuntimeStateEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val occurredAtEpochMillis: Long,
    val runtimeState: String,
    val sessionMode: String,
    val pauseReasons: String,
    val secondsToNextBreak: Int,
)

@Entity(
    tableName = "postpone_decisions",
    indices = [Index(value = ["confirmedAtEpochMillis"])],
)
data class PostponeDecisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val confirmedAtEpochMillis: Long,
    val delayDurationSeconds: Int,
    val reason: String,
)
