package com.smartaccountant.pro.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartaccountant.pro.SmartAccountantApp

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val companyId = inputData.getString("companyId") ?: return Result.success()
        val app = applicationContext as? SmartAccountantApp ?: return Result.retry()
        return try {
            app.container.syncEngine.syncPendingNow(companyId)
            app.container.syncEngine.pullRemoteOperationsAndNotify(companyId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
