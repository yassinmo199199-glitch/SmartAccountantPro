package com.smartaccountant.pro.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.smartaccountant.pro.SmartAccountantApp

class OtaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString("apkUrl") ?: return Result.failure()
        val versionName = inputData.getString("versionName") ?: ""
        val versionCode = inputData.getInt("versionCode", 0)

        val info = AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl
        )

        val app = applicationContext as? SmartAccountantApp ?: return Result.failure()

        val (file, _) = app.container.appUpdateManager.downloadFileDirectly(info) { progress ->
            updateNotificationProgress(versionName, progress)
        }

        return if (file != null && file.exists() && file.length() > 0) {
            AppUpdateManager.notifyUpdateReady(applicationContext)
            Result.success()
        } else {
            Result.failure()
        }
    }

    private fun updateNotificationProgress(versionName: String, progress: Int) {
        val channelId = "app_updates"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تحديثات التطبيق", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("جاري تنزيل تحديث $versionName ($progress%)")
            .setContentText("يمكنك الخروج والتنقل بحرية، سيكتمل التنزيل في الخلفية")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(1001, notification)
    }

    companion object {
        private const val WORK_NAME = "OtaDownloadForegroundWork"

        fun enqueueDownload(context: Context, info: AppUpdateInfo) {
            val data = workDataOf(
                "apkUrl" to info.apkUrl,
                "versionName" to info.versionName,
                "versionCode" to info.versionCode
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OtaDownloadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
