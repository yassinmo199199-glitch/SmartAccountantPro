package com.smartaccountant.pro.services

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VoucherAttachmentService(context: Context) {
    private val appContext = context.applicationContext

    suspend fun copyImage(companyId: String, uri: Uri): File = withContext(Dispatchers.IO) {
        val rawMimeType = appContext.contentResolver.getType(uri)
        val extension = if (!rawMimeType.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(rawMimeType) ?: "jpg"
        } else {
            "jpg"
        }

        val safeCompanyId = companyId.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(appContext.filesDir, "voucher_attachments/$safeCompanyId")
            .apply { if (!isDirectory) mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.$extension")

        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("تعذر قراءة الصورة المحددة")
            input.use { source ->
                file.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= MAX_IMAGE_SIZE_BYTES) { "حجم الصورة يتجاوز 15 ميغابايت" }
                        target.write(buffer, 0, count)
                    }
                }
            }
            check(file.length() > 0) { "الصورة المحددة فارغة" }
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun deleteLocalImage(path: String) {
        val attachmentsDirectory = File(appContext.filesDir, "voucher_attachments").canonicalFile
        val file = File(path).canonicalFile
        if (file.path.startsWith(attachmentsDirectory.path + File.separator)) {
            file.delete()
        }
    }

    private companion object {
        const val MAX_IMAGE_SIZE_BYTES = 15L * 1024 * 1024
    }
}
