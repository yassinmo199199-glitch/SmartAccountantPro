package com.smartaccountant.pro

import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceItemEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.PaymentMethod
import com.smartaccountant.pro.data.model.RecurrenceFrequency
import com.smartaccountant.pro.domain.repository.ProfitabilityCalculations
import com.smartaccountant.pro.domain.repository.RecurrenceSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RecurringAndProfitabilityUnitTest {
    @Test
    fun monthlyScheduleClampsToLastDayAndKeepsConfiguredDay() {
        val january31 = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 31, 12, 0)
        }.timeInMillis
        val february = Calendar.getInstance().apply {
            timeInMillis = RecurrenceSchedule.nextOccurrence(
                january31, RecurrenceFrequency.MONTHLY, 31
            )
        }
        assertEquals(Calendar.FEBRUARY, february.get(Calendar.MONTH))
        assertEquals(28, february.get(Calendar.DAY_OF_MONTH))

        val february28 = february.timeInMillis
        val march = Calendar.getInstance().apply {
            timeInMillis = RecurrenceSchedule.nextOccurrence(
                february28, RecurrenceFrequency.MONTHLY, 31
            )
        }
        assertEquals(Calendar.MARCH, march.get(Calendar.MONTH))
        assertEquals(31, march.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun dailyAndWeeklyScheduleAdvanceByCalendarUnits() {
        val date = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JUNE, 10, 9, 0)
        }.timeInMillis
        val nextDay = Calendar.getInstance().apply {
            timeInMillis = RecurrenceSchedule.nextOccurrence(date, RecurrenceFrequency.DAILY, 10)
        }
        val nextWeek = Calendar.getInstance().apply {
            timeInMillis = RecurrenceSchedule.nextOccurrence(date, RecurrenceFrequency.WEEKLY, 10)
        }
        assertEquals(11, nextDay.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, nextWeek.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun profitabilityIncludesReturnsAsRevenueAndCogsReversals() {
        val sale = invoice(InvoiceType.SALE, true, 1.0)
        val returnInvoice = invoice(InvoiceType.SALE_RETURN, true, 1.0)
        val items = listOf(
            sale to item("p1", quantity = 3.0, price = 20.0, cost = 12.0),
            returnInvoice to item("p1", quantity = 1.0, price = 20.0, cost = 12.0),
            invoice(InvoiceType.SALE, false, 1.0) to item("p2", 1.0, 100.0, 30.0)
        )

        val report = ProfitabilityCalculations.calculate(items)

        assertEquals(24.0, report.totalCostOfGoodsSold, 0.0001)
        assertEquals(2.0, report.products.single().quantity, 0.0001)
        assertEquals(40.0, report.products.single().revenue, 0.0001)
        assertEquals(16.0, report.products.single().profit, 0.0001)
    }

    private fun invoice(type: InvoiceType, posted: Boolean, exchangeRate: Double) = InvoiceEntity(
        companyId = "company",
        invoiceNumber = 1,
        type = type,
        paymentMethod = PaymentMethod.CASH,
        accountId = "account",
        warehouseId = "warehouse",
        currencyId = "currency",
        exchangeRate = exchangeRate,
        isPosted = posted
    )

    private fun item(productId: String, quantity: Double, price: Double, cost: Double) = InvoiceItemEntity(
        companyId = "company",
        invoiceId = "invoice",
        productId = productId,
        productName = productId,
        quantity = quantity,
        unitPrice = price,
        costPriceAtTime = cost,
        lineTotal = quantity * price
    )
}
