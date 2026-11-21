package com.smartaccountant.pro.core.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentNumberGenerator(context: Context) {
    private val prefs = context.getSharedPreferences("smart_acc_device_prefs", Context.MODE_PRIVATE)

    fun getDeviceTag(): String {
        val existing = prefs.getString("device_short_tag", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
            .replace("-", "")
            .take(3)
            .uppercase(Locale.US)
        prefs.edit().putString("device_short_tag", generated).apply()
        return generated
    }

    fun formatVoucherDisplayNumber(seqNumber: Long, dateMillis: Long = System.currentTimeMillis()): String {
        val dayStr = SimpleDateFormat("yyMMdd", Locale.US).format(Date(dateMillis))
        val padded = seqNumber.toString().padStart(4, '0')
        return "VCH-${getDeviceTag()}-$dayStr-$padded"
    }

    fun formatInvoiceDisplayNumber(seqNumber: Long, dateMillis: Long = System.currentTimeMillis()): String {
        val dayStr = SimpleDateFormat("yyMMdd", Locale.US).format(Date(dateMillis))
        val padded = seqNumber.toString().padStart(4, '0')
        return "INV-${getDeviceTag()}-$dayStr-$padded"
    }
}
