package com.smartaccountant.pro.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class BluetoothPrinterService(private val context: Context) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun adapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        return try {
            adapter()?.bondedDevices?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * يحول النص العربي المشكّل إلى صورة نقطية عالية الوضوح ويرسلها بأمر ESC/POS GS v 0
     * لضمان طباعة العربية متصلة وسليمة على جميع طابعات البلوتوث الحرارية 58mm و 80mm
     */
    @SuppressLint("MissingPermission")
    suspend fun printArabicReceiptAsRaster(
        deviceAddress: String,
        receiptContent: String,
        is80mm: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val device = adapter()?.getRemoteDevice(deviceAddress)
                ?: return@withContext Result.failure(IllegalStateException("لم يتم تحديد الطابعة مسبقاً"))
            val widthPx = if (is80mm) 576 else 384
            val bitmap = renderArabicTextToBitmap(receiptContent, widthPx)
            val rasterBytes = encodeBitmapToEscPosRaster(bitmap)

            val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()
            val out = socket.outputStream
            out.write(byteArrayOf(0x1B, 0x40)) // ESC @ Initialize
            out.write(rasterBytes)
            out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x00)) // Feed & Cut
            out.flush()
            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun renderArabicTextToBitmap(text: String, widthPx: Int): Bitmap {
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx - 16)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(4f, 1.15f)
            .build()

        val bmp = Bitmap.createBitmap(widthPx, layout.height + 24, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.translate(8f, 12f)
        layout.draw(canvas)
        return bmp
    }

    private fun encodeBitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8
        val baos = ByteArrayOutputStream()
        // GS v 0 m xL xH yL yH
        baos.write(
            byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (bytesPerRow and 0xFF).toByte(),
                ((bytesPerRow shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            )
        )
        for (y in 0 until height) {
            for (xByte in 0 until bytesPerRow) {
                var slice = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (luminance < 160) {
                            slice = slice or (1 shl (7 - bit))
                        }
                    }
                }
                baos.write(slice)
            }
        }
        return baos.toByteArray()
    }
}
