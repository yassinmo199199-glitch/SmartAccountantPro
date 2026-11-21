package com.smartaccountant.pro.data.remote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppUpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return
        val appContext = context.applicationContext
        when (AppUpdateManager.downloadStatus(appContext, downloadId)) {
            DownloadManager.STATUS_SUCCESSFUL -> AppUpdateManager.notifyUpdateReady(appContext)
            DownloadManager.STATUS_FAILED -> AppUpdateManager.clearPendingDownload(appContext)
        }
    }
}
