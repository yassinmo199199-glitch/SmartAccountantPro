package com.smartaccountant.pro.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import java.io.File

object MessagingShareService {
    fun openPdf(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "عرض الفاتورة").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf", targetPackage: String? = null) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة المستند عبر").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun sharePdfToWhatsApp(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        val whatsappPackage = whatsappPackages.firstOrNull { packageName ->
            context.packageManager.resolveActivity(Intent(intent).setPackage(packageName), 0) != null
        }
        if (whatsappPackage != null) {
            context.startActivity(Intent(intent).setPackage(whatsappPackage))
        } else {
            shareFile(context, file)
        }
    }

    fun sendWhatsAppMessage(context: Context, phone: String, message: String, isBusiness: Boolean = false) {
        val cleanPhone = phone.replace("+", "").replace(" ", "")
        val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isBusiness) setPackage("com.whatsapp.w4b")
        }
        context.startActivity(intent)
    }

    fun sendDirectSms(phone: String, message: String): Result<Unit> {
        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
