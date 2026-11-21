package com.smartaccountant.pro.domain.repository

object AutomaticVoucherIds {
    fun cashSaleReceipt(invoiceId: String): String = "auto-cash-sale-$invoiceId"
    fun cashReturnPayment(invoiceId: String): String = "auto-cash-return-$invoiceId"
}
