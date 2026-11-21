package com.smartaccountant.pro.data.remote

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.smartaccountant.pro.core.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID

class CloudMediaStorage(private val context: Context) {
    private val httpClient = OkHttpClient()

    suspend fun uploadAttachment(companyId: String, file: File, folder: String = "receipts"): String {
        check(file.isFile) { "ملف المرفق غير موجود" }
        return if (AppConfig.USE_CLOUDFLARE_R2) {
            uploadToCloudflareR2(companyId, file, folder)
        } else {
            uploadToFirebaseStorage(companyId, file, folder)
        }
    }

    private suspend fun uploadToCloudflareR2(companyId: String, file: File, folder: String): String =
        withContext(Dispatchers.IO) {
            val objectKey = "$companyId/$folder/${UUID.randomUUID()}_${file.name}"
            val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("${AppConfig.CLOUDFLARE_R2_UPLOAD_ENDPOINT}?key=$objectKey")
                .put(requestBody)
                .build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "رفع المرفق إلى التخزين السحابي فشل (HTTP ${response.code})" }
                "${AppConfig.CLOUDFLARE_R2_PUBLIC_BASE_URL}/$objectKey"
            }
        }

    private suspend fun uploadToFirebaseStorage(companyId: String, file: File, folder: String): String {
        check(FirebaseApp.getApps(context).isNotEmpty()) { "Firebase غير مهيأ لرفع المرفق" }
        val ref = FirebaseStorage.getInstance().reference
            .child("companies/$companyId/$folder/${UUID.randomUUID()}_${file.name}")
        ref.putFile(Uri.fromFile(file)).await()
        return ref.downloadUrl.await().toString()
    }
}
