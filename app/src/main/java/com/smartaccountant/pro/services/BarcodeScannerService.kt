package com.smartaccountant.pro.services

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.UUID

object BarcodeScannerService {
    fun generateUniqueBarcode(): String = (100000000000L..999999999999L).random().toString()

    fun generateBarcodeBitmap(content: String, width: Int = 500, height: Int = 180, isQr: Boolean = false): Bitmap {
        val format = if (isQr) BarcodeFormat.QR_CODE else BarcodeFormat.CODE_128
        val matrix = MultiFormatWriter().encode(content.ifBlank { UUID.randomUUID().toString() }, format, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
