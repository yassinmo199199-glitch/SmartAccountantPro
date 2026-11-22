package com.smartaccountant.pro.data.remote

import android.content.Context
import androidx.work.*
import com.smartaccountant.pro.SmartAccountantApp
import java.util.concurrent.TimeUnit

class OtaCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? SmartAccountantApp ?: return Result.success()
            val info = app.container.appUpdateManager.fetchLatest()
            if (info != null) {
                AppUpdateManager.notifyNewUpdateAvailable(applicationContext, info)
            }
            val activeCompanyId = app.container.sessionStore.currentCompanyId
            if (!activeCompanyId.isNullOrBlank()) {
                app.container.syncEngine.pullRemoteOperationsAndNotify(activeCompanyId)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "OtaBackgroundCheckWork"

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<OtaCheckWorker>(
                2, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}
