package com.smartaccountant.pro.core.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val df = DecimalFormat("#,##0.##", symbols)

    fun format(amount: Double, symbol: String = ""): String {
        val formatted = df.format(amount)
        return if (symbol.isBlank()) formatted else "$formatted $symbol"
    }

    fun formatAmount(amount: Double): String = format(amount)
}
