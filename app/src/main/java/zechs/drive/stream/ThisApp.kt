package zechs.drive.stream

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import zechs.drive.stream.worker.EpisodeCheckWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class — wires Hilt's [HiltWorkerFactory] into WorkManager so
 * that [EpisodeCheckWorker] can receive injected dependencies.
 *
 * The worker is scheduled as a periodic task with a 12-hour minimum interval.
 * [ExistingPeriodicWorkPolicy.KEEP] ensures that re-launching the app after an
 * update doesn't reset the countdown of an already-scheduled check.
 */
@HiltAndroidApp
class ThisApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleEpisodeCheck()
    }

    private fun scheduleEpisodeCheck() {
        val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            EpisodeCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}