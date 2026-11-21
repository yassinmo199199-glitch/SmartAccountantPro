package com.smartaccountant.pro

import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.PaymentMethod
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.domain.repository.OverdueBucket
import com.smartaccountant.pro.domain.repository.PartyLedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyLedgerUnitTest {
    @Test
    fun overdueCalculationUsesDueDateOutstandingAndPostedCreditInvoices() {
        val today = 100L * DAY
        val customer = account("customer", AccountCategory.CUSTOMERS)
        val invoices = listOf(
            invoice("15", customer.id, today - 15 * DAY, 100.0),
            invoice("16", customer.id, today - 16 * DAY, 100.0),
            invoice("30", customer.id, today - 30 * DAY, 100.0),
            invoice("31", customer.id, today - 31 * DAY, 100.0),
            invoice("60", customer.id, today - 60 * DAY, 100.0),
            invoice("61", customer.id, today - 61 * DAY, 100.0),
            invoice("paid", customer.id, today - 20 * DAY, 100.0, paid = 100.0),
            invoice("draft", customer.id, today - 20 * DAY, 100.0, posted = false),
            invoice("future", customer.id, today + DAY, 100.0)
        )

        val overdue = PartyLedgerCalculations.overdueInvoices(
            mapOf(customer.id to customer), invoices, today
        )

        assertEquals(6, overdue.size)
        assertEquals(OverdueBucket.DAYS_0_15, overdue.first { it.invoice.id == "15" }.bucket)
        assertEquals(OverdueBucket.DAYS_16_30, overdue.first { it.invoice.id == "16" }.bucket)
        assertEquals(OverdueBucket.DAYS_16_30, overdue.first { it.invoice.id == "30" }.bucket)
        assertEquals(OverdueBucket.DAYS_31_60, overdue.first { it.invoice.id == "31" }.bucket)
        assertEquals(OverdueBucket.DAYS_31_60, overdue.first { it.invoice.id == "60" }.bucket)
        assertEquals(OverdueBucket.OVER_60, overdue.first { it.invoice.id == "61" }.bucket)
        assertEquals(600.0, PartyLedgerCalculations.overdueReport(overdue).totalOutstanding, 0.001)
    }

    @Test
    fun statementCarriesForwardOpeningBalanceAndRunningVoucherAndInvoiceBalances() {
        val customer = account("customer", AccountCategory.CUSTOMERS, openingDebit = 10.0)
        val vouchers = listOf(
            voucher("payment", VoucherType.PAYMENT, customer.id, 20.0, 2),
            voucher("receipt", VoucherType.RECEIPT, customer.id, 5.0, 3)
        )
        val invoices = listOf(invoice("sale", customer.id, 4L, 100.0, paid = 10.0))

        val statement = PartyLedgerCalculations.statement(customer, vouchers, invoices)

        assertEquals(3, statement.size)
        assertEquals(30.0, statement[0].runningBalance, 0.001)
        assertEquals(25.0, statement[1].runningBalance, 0.001)
        assertEquals(115.0, statement[2].runningBalance, 0.001)
        assertEquals(90.0, statement[2].balanceChange, 0.001)
        assertTrue(statement.last().invoice != null)
    }

    @Test
    fun receivedPaymentsAreAllocatedToOldestOpenInvoiceForOverdueTotals() {
        val today = 100L * DAY
        val customer = account("customer", AccountCategory.CUSTOMERS)
        val invoices = listOf(
            invoice("oldest", customer.id, today - 40 * DAY, 100.0),
            invoice("next", customer.id, today - 20 * DAY, 100.0)
        )
        val payments = listOf(voucher("receipt", VoucherType.RECEIPT, customer.id, 120.0, today - 2 * DAY))

        val overdue = PartyLedgerCalculations.overdueInvoices(
            mapOf(customer.id to customer), invoices, today, payments
        )

        assertEquals(1, overdue.size)
        assertEquals("next", overdue.single().invoice.id)
        assertEquals(80.0, overdue.single().outstandingAmount, 0.001)
    }

    private fun account(
        id: String,
        category: AccountCategory,
        openingDebit: Double = 0.0
    ) = AccountEntity(
        id = id,
        companyId = COMPANY,
        name = id,
        category = category,
        defaultCurrencyId = "currency",
        openingDebit = openingDebit
    )

    private fun invoice(
        id: String,
        accountId: String,
        dueDate: Long,
        total: Double,
        paid: Double = 0.0,
        posted: Boolean = true
    ) = InvoiceEntity(
        id = id,
        companyId = COMPANY,
        invoiceNumber = id.hashCode().toLong(),
        type = InvoiceType.SALE,
        paymentMethod = PaymentMethod.CREDIT,
        dateMillis = dueDate,
        dueDateMillis = dueDate,
        accountId = accountId,
        warehouseId = "warehouse",
        currencyId = "currency",
        netTotal = total,
        paidAmount = paid,
        isPosted = posted
    )

    private fun voucher(
        id: String,
        type: VoucherType,
        accountId: String,
        amount: Double,
        date: Long
    ) = VoucherEntity(
        id = id,
        companyId = COMPANY,
        voucherNumber = date,
        type = type,
        dateMillis = date,
        accountId = accountId,
        currencyId = "currency",
        amount = amount
    )

    private companion object {
        const val COMPANY = "company"
        const val DAY = 86_400_000L
    }
}
