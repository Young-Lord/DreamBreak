package moe.lyniko.dreambreak.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertRuntimeStateEvent(event: RuntimeStateEventEntity)

    @Insert
    suspend fun insertPostponeDecision(decision: PostponeDecisionEntity)

    @Query("SELECT * FROM postpone_decisions ORDER BY confirmedAtEpochMillis DESC LIMIT :limit")
    fun observeRecentPostponeDecisions(limit: Int): Flow<List<PostponeDecisionEntity>>
}
