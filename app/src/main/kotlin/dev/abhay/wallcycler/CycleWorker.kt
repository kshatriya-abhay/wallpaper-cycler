package dev.abhay.wallcycler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit

class CycleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = try {
            WallpaperChanger.changeNow(applicationContext)
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost access to the folder", e)
            Outcome.NoAccess
        } catch (e: IOException) {
            // A file that won't decode, or the wallpaper service failing. The next try picks again.
            Log.w(TAG, "Couldn't set the wallpaper", e)
            if (runAttemptCount < 2) return Result.retry()
            Outcome.Failed
        }
        Log.i(TAG, "Outcome: $outcome")
        val data = workDataOf(OUTCOME to outcome.name)
        return if (outcome == Outcome.Failed) Result.failure(data) else Result.success(data)
    }

    companion object {
        private const val TAG = "WallpaperCycler"
        const val OUTCOME = "outcome"
        const val PERIODIC = "cycle"
        const val NOW = "cycle-now"

        /**
         * Changes the wallpaper every [frequency]. The first change comes one period from now.
         * [replace] applies a new frequency; otherwise an existing schedule is kept.
         */
        fun schedule(context: Context, frequency: Frequency, replace: Boolean) {
            val request = PeriodicWorkRequestBuilder<CycleWorker>(frequency.hours, TimeUnit.HOURS)
                .setInitialDelay(frequency.hours, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelSchedule(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }

        fun changeNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<CycleWorker>().build())
        }
    }
}
