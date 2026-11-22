package com.smartaccountant.pro.data.remote

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.smartaccountant.pro.BuildConfig
import com.smartaccountant.pro.MainActivity
import java.io.File

data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val mandatory: Boolean = false
)

enum class AppUpdateStatus { IDLE, AVAILABLE, DOWNLOADING, READY, ERROR }

data class AppUpdateState(
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val info: AppUpdateInfo? = null,
    val message: String = ""
)

class AppUpdateManager(
    private val context: Context,
    private val firestore: FirestoreSyncService
) {
    suspend fun fetchLatest(): AppUpdateInfo? {
        // 1. Try GitHub Releases API / Raw JSON
        try {
            val githubInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchFromGitHub()
            }
            if (githubInfo != null) return githubInfo
        } catch (_: Exception) {
            // Ignore error and fallback to Firestore
        }

        // 2. Fallback to Firestore
        val data = firestore.getRootDocumentMap("app_config", "latest") ?: return null
        val versionCode = (data["versionCode"] as? Number)?.toInt() ?: 0
        val apkUrl = data["apkUrl"] as? String ?: return null
        if (versionCode <= BuildConfig.VERSION_CODE || apkUrl.isBlank()) return null
        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = data["versionName"] as? String ?: "",
            apkUrl = apkUrl,
            releaseNotes = data["releaseNotes"] as? String ?: "",
            mandatory = data["mandatory"] as? Boolean ?: false
        )
    }

    private fun fetchFromGitHub(): AppUpdateInfo? {
        val repo = "yassinmo199199-glitch/SmartAccountantPro"

        // 1. Check GitHub Releases API (/releases/latest)
        try {
            val apiUrl = java.net.URL("https://api.github.com/repos/$repo/releases/latest")
            val conn = apiUrl.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; SmartAccountantPro)")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(text)
                val tagName = json.optString("tag_name", "").trim()
                val cleanTag = tagName.removePrefix("v").trim()
                val body = json.optString("body", "")
                val assets = json.optJSONArray("assets")

                var downloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (downloadUrl.isNotBlank()) {
                    val currentVersionName = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                    val tagParts = cleanTag.split(".").mapNotNull { it.toIntOrNull() }
                    val currentParts = currentVersionName.split(".").mapNotNull { it.toIntOrNull() }

                    var isNewer = false
                    if (tagParts.isNotEmpty() && currentParts.isNotEmpty()) {
                        for (i in 0 until maxOf(tagParts.size, currentParts.size)) {
                            val tagP = tagParts.getOrElse(i) { 0 }
                            val curP = currentParts.getOrElse(i) { 0 }
                            if (tagP > curP) {
                                isNewer = true
                                break
                            } else if (tagP < curP) {
                                isNewer = false
                                break
                            }
                        }
                    } else {
                        isNewer = cleanTag != currentVersionName
                    }

                    if (isNewer) {
                        return AppUpdateInfo(
                            versionCode = BuildConfig.VERSION_CODE + 1,
                            versionName = cleanTag,
                            apkUrl = downloadUrl,
                            releaseNotes = body.ifBlank { "تحديث جديد متوفر للتحميل والتثبيت المباشر" },
                            mandatory = false
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    suspend fun downloadFileDirectly(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Pair<File?, String> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "smart-accountant-${info.versionCode}.apk"
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val destinationFile = File(dir, fileName)

                if (destinationFile.exists() && destinationFile.length() > 1000000L) {
                    Companion.savePendingDownload(context, 99999L, fileName, info.versionName)
                    return@withContext Pair(destinationFile, "تم التنزيل بنجاح")
                }

                val tempFile = File(dir, "$fileName.tmp")
                if (tempFile.exists()) tempFile.delete()

                var currentUrl = info.apkUrl
                var redirects = 0
                var conn: java.net.HttpURLConnection? = null

                while (redirects < 8) {
                    val url = java.net.URL(currentUrl)
                    conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        setRequestProperty("Accept", "*/*")
                    }

                    val responseCode = conn.responseCode
                    if (responseCode in 300..399) {
                        val location = conn.getHeaderField("Location")
                            ?: conn.getHeaderField("location")
                            ?: conn.headerFields.entries.find { it.key?.equals("Location", ignoreCase = true) == true }?.value?.firstOrNull()

                        if (!location.isNullOrBlank()) {
                            currentUrl = if (location.startsWith("http")) location else java.net.URL(url, location).toString()
                            redirects++
                            conn.disconnect()
                            continue
                        }
                    }
                    break
                }

                val finalConn = conn ?: return@withContext Pair(null, "تعذر الاتصال بسيرفر التحميل")
                if (finalConn.responseCode != 200) {
                    return@withContext Pair(null, "رمز الخطأ من السيرفر: ${finalConn.responseCode}")
                }

                val totalLength = finalConn.contentLengthLong.takeIf { it > 0 } ?: finalConn.contentLength.toLong()
                val inputStream = finalConn.inputStream
                val outputStream = java.io.FileOutputStream(tempFile)

                val buffer = ByteArray(32768)
                var bytesRead: Int
                var totalRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalLength > 0) {
                        val progress = ((totalRead * 100) / totalLength).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                finalConn.disconnect()

                if (tempFile.exists() && tempFile.length() > 500000L) {
                    if (destinationFile.exists()) destinationFile.delete()
                    tempFile.renameTo(destinationFile)
                    Companion.savePendingDownload(context, 99999L, fileName, info.versionName)
                    return@withContext Pair(destinationFile, "تم التنزيل بنجاح")
                }
                Pair(null, "الملف المنزل غير مكتمل أو فارغ")
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(null, "فشل التنزيل: ${e.localizedMessage ?: e.message ?: "خطأ في الاتصال"}")
            }
        }
    }

    companion object {
        private const val PREFS = "app_update_prefs"
        private const val KEY_DOWNLOAD_ID = "pending_download_id"
        private const val KEY_FILE_NAME = "pending_file_name"
        private const val KEY_VERSION_NAME = "pending_version_name"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 1001

        fun startDownload(context: Context, info: AppUpdateInfo): Long? {
            val fileName = "smart-accountant-${info.versionCode}.apk"
            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("تحديث ${info.versionName.ifBlank { info.versionCode.toString() }}")
                .setDescription("جاري تنزيل تحديث التطبيق")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
            val id = dm.enqueue(request)
            savePendingDownload(context, id, fileName, info.versionName)
            return id
        }

        fun downloadStatus(context: Context, downloadId: Long): Int {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return -1
            val query = DownloadManager.Query().setFilterById(downloadId)
            dm.query(query).use { cursor ->
                if (!cursor.moveToFirst()) return -1
                val idx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                return if (idx >= 0) cursor.getInt(idx) else -1
            }
        }

        fun downloadedFile(context: Context, downloadId: Long): File? {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                        if (status == DownloadManager.STATUS_SUCCESSFUL && !localUri.isNullOrBlank()) {
                            if (localUri.startsWith("file://")) {
                                val file = File(Uri.parse(localUri).path ?: return@use null)
                                if (file.exists()) return file
                            }
                        }
                    }
                }
            }
            val name = prefs(context).getString(KEY_FILE_NAME, null)
            if (!name.isNullOrBlank()) {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
                if (file.exists()) return file
            }
            return null
        }

        fun pendingDownloadId(context: Context): Long = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)

        fun pendingVersionName(context: Context): String = prefs(context).getString(KEY_VERSION_NAME, "") ?: ""

        fun clearPendingDownload(context: Context) {
            prefs(context).edit().clear().apply()
        }

        private fun savePendingDownload(context: Context, downloadId: Long, fileName: String, versionName: String) {
            prefs(context).edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_VERSION_NAME, versionName)
                .apply()
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun notifyNewUpdateAvailable(context: Context, info: AppUpdateInfo) {
            ensureChannel(context)
            val launchIntent = Intent(context, MainActivity::class.java)
                .putExtra("check_update", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("تحديث جديد متوفر: ${info.versionName}")
                .setContentText("يتوفر إصدار جديد للتطبيق بخصائص ومميزات جديدة. اضغط للتحديث المباشر.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
            }
        }

        fun notifyUpdateReady(context: Context) {
            ensureChannel(context)
            val launchIntent = Intent(context, MainActivity::class.java)
                .putExtra("install_update", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("التحديث جاهز")
                .setContentText("اضغط للتثبيت")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "تحديثات التطبيق", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        fun canInstallPackages(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        }

        fun openInstallPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }

        fun installApk(context: Context, file: File?): Boolean {
            if (file == null || !file.exists() || file.length() <= 0) return false
            if (!canInstallPackages(context)) {
                openInstallPermissionSettings(context)
                return false
            }
            return try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
