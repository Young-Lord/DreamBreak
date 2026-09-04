package moe.lyniko.dreambreak

import android.app.Application
import moe.lyniko.dreambreak.data.history.HistoryRepository
import moe.lyniko.dreambreak.data.history.RuntimeHistoryRecorder

class DreamBreakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HistoryRepository.getInstance(this)
        RuntimeHistoryRecorder.start(this)
    }
}
