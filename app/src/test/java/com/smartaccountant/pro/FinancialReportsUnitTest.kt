package com.smartaccountant.pro

import com.smartaccountant.pro.data.local.Converters
import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.PaymentMethod
import com.smartaccountant.pro.data.model.UserPermissions
import com.smartaccountant.pro.data.model.UserRole
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.domain.repository.AgingBucket
import com.smartaccountant.pro.domain.repository.FinancialReportCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialReportsUnitTest {
    @Test
    fun profitLossUsesOnlySelectedPeriodAndExcludesOpeningBalances() {
        val revenue = account("revenue", AccountCategory.REVENUES)
        val expense = account("expense", AccountCategory.EXPENSES)
        val cash = account("cash", AccountCategory.ASSETS)
        val vouchers = listOf(
            voucher("inside-revenue", VoucherType.SIMPLE_JOURNAL, cash.id, counterAccountId = revenue.id, amount = 70.0, date = 20),
            voucher("inside-expense", VoucherType.PAYMENT, expense.id, amount = 20.0, date = 25),
            voucher("outside", VoucherType.SIMPLE_JOURNAL, cash.id, counterAccountId = revenue.id, amount = 100.0, date = 9),
            voucher("opening", VoucherType.OPENING_BALANCE, revenue.id, amount = -500.0, date = 22)
        )

        val report = FinancialReportCalculations.profitLoss(
            listOf(revenue, expense, cash), vouchers, emptyList(), fromMillis = 10, toMillis = 30
        )

        assertEquals(70.0, report.totalRevenue, 0.001)
        assertEquals(20.0, report.totalExpense, 0.001)
        assertEquals(50.0, report.netProfit, 0.001)
    }

    @Test
    fun balanceSheetAddsAccumulatedIncomeToEquity() {
        val asset = account("cash", AccountCategory.ASSETS, openingDebit = 200.0)
        val liability = account("payable", AccountCategory.LIABILITIES, openingCredit = 50.0)
        val equity = account("capital", AccountCategory.EQUITY, openingCredit = 100.0)
        val revenue = account("income", AccountCategory.REVENUES, openingCredit = 50.0)

        val report = FinancialReportCalculations.balanceSheet(
            listOf(asset, liability, equity, revenue), emptyList(), emptyList(), asOfMillis = 100
        )

        assertEquals(200.0, report.totalAssets, 0.001)
        assertEquals(50.0, report.totalLiabilities, 0.001)
        assertEquals(150.0, report.totalEquity, 0.001)
        assertEquals(0.0, report.balanceDifference, 0.001)
    }

    @Test
    fun agingGroupsCreditOutstandingAmountsIntoCorrectBuckets() {
        val customer = account("customer", AccountCategory.CUSTOMERS)
        val supplier = account("supplier", AccountCategory.SUPPLIERS)
        val asOf = 100L * DAY
        val invoices = listOf(
            invoice("today", customer.id, InvoiceType.SALE, asOf, amount = 100.0),
            invoice("30-days", customer.id, InvoiceType.FREE_SALE, asOf - 30 * DAY, amount = 50.0, paid = 25.0),
            invoice("31-days", customer.id, InvoiceType.SALE, asOf - 31 * DAY, amount = 30.0),
            invoice("60-days", customer.id, InvoiceType.SALE, asOf - 60 * DAY, amount = 20.0),
            invoice("61-days", customer.id, InvoiceType.SALE, asOf - 61 * DAY, amount = 10.0),
            invoice("90-days", customer.id, InvoiceType.SALE, asOf - 90 * DAY, amount = 8.0),
            invoice("91-days", customer.id, InvoiceType.SALE, asOf - 91 * DAY, amount = 7.0),
            invoice("supplier-invoice", supplier.id, InvoiceType.PURCHASE, asOf - 91 * DAY, amount = 999.0),
            invoice("draft", customer.id, InvoiceType.SALE, asOf - 91 * DAY, amount = 999.0, posted = false),
            invoice("cash", customer.id, InvoiceType.SALE, asOf - 91 * DAY, amount = 999.0, paymentMethod = PaymentMethod.CASH)
        )

        val report = FinancialReportCalculations.aging(
            listOf(customer, supplier), invoices, asOf, AccountCategory.CUSTOMERS
        )
        val agedCustomer = report.accounts.single()

        assertEquals(125.0, agedCustomer.amountIn(AgingBucket.DAYS_0_30), 0.001)
        assertEquals(50.0, agedCustomer.amountIn(AgingBucket.DAYS_31_60), 0.001)
        assertEquals(18.0, agedCustomer.amountIn(AgingBucket.DAYS_61_90), 0.001)
        assertEquals(7.0, agedCustomer.amountIn(AgingBucket.OVER_90), 0.001)
    }

    @Test
    fun legacyFinancialReportPermissionIsPreserved() {
        val permissions = Converters().toUserPermissions("""{"canViewFinalReports":true}""")

        assertTrue(permissions.canViewFinancialReports)
        assertTrue(UserPermissions.forRole(UserRole.ACCOUNTANT).canViewFinancialReports)
    }

    private fun account(
        id: String,
        category: AccountCategory,
        openingDebit: Double = 0.0,
        openingCredit: Double = 0.0
    ) = AccountEntity(
        id = id,
        companyId = COMPANY,
        name = id,
        category = category,
        defaultCurrencyId = "currency",
        openingDebit = openingDebit,
        openingCredit = openingCredit
    )

    private fun voucher(
        id: String,
        type: VoucherType,
        accountId: String,
        counterAccountId: String? = null,
        amount: Double,
        date: Long
    ) = VoucherEntity(
        id = id,
        companyId = COMPANY,
        voucherNumber = 1,
        type = type,
        dateMillis = date,
        accountId = accountId,
        counterAccountId = counterAccountId,
        currencyId = "currency",
        amount = amount
    )

    private fun invoice(
        id: String,
        accountId: String,
        type: InvoiceType,
        date: Long,
        amount: Double,
        paid: Double = 0.0,
        posted: Boolean = true,
        paymentMethod: PaymentMethod = PaymentMethod.CREDIT
    ) = InvoiceEntity(
        id = id,
        companyId = COMPANY,
        invoiceNumber = 1,
        type = type,
        paymentMethod = paymentMethod,
        dateMillis = date,
        accountId = accountId,
        warehouseId = "warehouse",
        currencyId = "currency",
        netTotal = amount,
        paidAmount = paid,
        isPosted = posted
    )

    private companion object {
        const val COMPANY = "company"
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
