package com.smartaccountant.pro.core.utils

import kotlin.math.abs
import kotlin.math.roundToLong

object ArabicTafqeet {
    private val ones = arrayOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
        "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
        "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"
    )
    private val tens = arrayOf(
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"
    )
    private val hundreds = arrayOf(
        "", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة",
        "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة"
    )

    fun convert(amount: Double, currencyName: String = "ريال", subUnitName: String = "فلس"): String {
        val absVal = abs(amount)
        val whole = absVal.toLong()
        val fraction = ((absVal - whole) * 100).roundToLong()

        if (whole == 0L && fraction == 0L) return "صفر $currencyName فقط لا غير"

        val parts = mutableListOf<String>()
        if (whole > 0L) parts.add("${convertNumber(whole)} $currencyName")
        if (fraction > 0L && subUnitName.isNotBlank()) parts.add("${convertNumber(fraction)} $subUnitName")
        return parts.joinToString(" و ") + " فقط لا غير"
    }

    private fun convertNumber(n: Long): String {
        if (n == 0L) return ""
        if (n < 20) return ones[n.toInt()]
        if (n < 100) {
            val t = tens[(n / 10).toInt()]
            val o = ones[(n % 10).toInt()]
            return if (o.isEmpty()) t else "$o و$t"
        }
        if (n < 1000) {
            val h = hundreds[(n / 100).toInt()]
            val rem = convertNumber(n % 100)
            return if (rem.isEmpty()) h else "$h و$rem"
        }
        if (n < 1_000_000) {
            val k = n / 1000
            val rem = convertNumber(n % 1000)
            val kText = when (k) {
                1L -> "ألف"
                2L -> "ألفان"
                in 3..10 -> "${ones[k.toInt()]} آلاف"
                else -> "${convertNumber(k)} ألف"
            }
            return if (rem.isEmpty()) kText else "$kText و$rem"
        }
        val m = n / 1_000_000
        val rem = convertNumber(n % 1_000_000)
        val mText = when (m) {
            1L -> "مليون"
            2L -> "مليونان"
            in 3..10 -> "${ones[m.toInt()]} ملايين"
            else -> "${convertNumber(m)} مليون"
        }
        return if (rem.isEmpty()) mText else "$mText و$rem"
    }
}
