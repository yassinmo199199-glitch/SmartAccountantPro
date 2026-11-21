package com.smartaccountant.pro.domain.repository

import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.PaymentMethod
import com.smartaccountant.pro.data.model.UserEntity
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.data.model.VoucherType
import java.util.Calendar

enum class OverdueBucket(val label: String) {
    DAYS_0_15("0-15 يوم"),
    DAYS_16_30("16-30 يوم"),
    DAYS_31_60("31-60 يوم"),
    OVER_60("أكثر من 60 يوم")
}

data class PartyLedgerEntry(
    val id: String,
    val dateMillis: Long,
    val description: String,
    val amount: Double,
    val balanceChange: Double,
    val runningBalance: Double,
    val invoice: InvoiceEntity? = null,
    val voucher: VoucherEntity? = null
)

data class PartyProfileData(
    val account: AccountEntity,
    val currentBalance: Double,
    val statement: List<PartyLedgerEntry>,
    val invoices: List<InvoiceEntity>,
    val invoiceOutstandingById: Map<String, Double>,
    val payments: List<PartyLedgerEntry>,
    val receivableOrPayable: Double
)

data class OverdueInvoice(
    val invoice: InvoiceEntity,
    val partyName: String,
    val daysLate: Int,
    val outstandingAmount: Double,
    val bucket: OverdueBucket
)

data class OverdueReport(
    val invoices: List<OverdueInvoice>,
    val count: Int,
    val totalOutstanding: Double,
    val totalsByBucket: Map<OverdueBucket, Double>
)

class PartyRepository(private val db: AppDatabase) {
    suspend fun getPartyProfile(user: UserEntity, companyId: String, accountId: String): PartyProfileData {
        requireCompanyUser(user, companyId)
        PermissionManager.requirePermission(
            user, { it.canViewPartyProfile }, "لا تملك صلاحية عرض ملف العميل أو المورد"
        )
        val account = db.accountDao().getPartyAccount(companyId, accountId)
            ?: throw NoSuchElementException("الحساب غير موجود في الشركة الحالية")
        requirePartyAccount(account)

        val vouchers = db.voucherDao().getPartyStatement(companyId, accountId)
        val invoices = db.invoiceDao().getPartyInvoices(companyId, accountId)
            .filter { it.isPosted && !it.isArchived && !it.isDeleted }
        val invoiceOutstanding = PartyLedgerCalculations.invoiceOutstandingById(account, invoices, vouchers)
        val statement = PartyLedgerCalculations.statement(account, vouchers, invoices)
        val paymentsById = db.voucherDao().getPartyPayments(companyId, accountId).associateBy { it.id }
        val payments = statement.filter { it.voucher?.id?.let(paymentsById::containsKey) == true }
        val currentBalance = statement.lastOrNull()?.runningBalance ?: (account.openingDebit - account.openingCredit)
        val owed = when (account.category) {
            AccountCategory.CUSTOMERS -> currentBalance.coerceAtLeast(0.0)
            AccountCategory.SUPPLIERS -> (-currentBalance).coerceAtLeast(0.0)
            else -> 0.0
        }
        return PartyProfileData(account, currentBalance, statement, invoices, invoiceOutstanding, payments, owed)
    }

    suspend fun getOverdueInvoices(
        user: UserEntity,
        companyId: String,
        todayStartMillis: Long
    ): OverdueReport {
        requireCompanyUser(user, companyId)
        PermissionManager.requirePermission(
            user, { it.canViewPartyProfile }, "لا تملك صلاحية عرض الفواتير المتأخرة"
        )
        val accounts = db.accountDao().getAllActiveAccounts(companyId).associateBy { it.id }
        val asOfMillis = System.currentTimeMillis()
        val overdueIds = db.invoiceDao().getOverdueInvoices(companyId, todayStartMillis)
            .mapTo(mutableSetOf()) { it.id }
        val invoices = db.invoiceDao().getPostedCreditInvoicesThroughDate(companyId, asOfMillis)
        val payments = db.voucherDao().getCollectionPayments(companyId, asOfMillis)
        val overdue = PartyLedgerCalculations.overdueInvoices(accounts, invoices, todayStartMillis, payments)
            .filter { it.invoice.id in overdueIds }
        return PartyLedgerCalculations.overdueReport(overdue)
    }

    suspend fun createPaymentReminder(
        user: UserEntity,
        companyId: String,
        accountId: String
    ): String {
        requireCompanyUser(user, companyId)
        PermissionManager.requirePermission(
            user, { it.canSendPaymentReminder }, "لا تملك صلاحية إرسال تذكير دفع"
        )
        val profile = getPartyProfile(user, companyId, accountId)
        require(profile.account.phone.isNotBlank()) { "لا يوجد رقم هاتف مسجل لهذا الطرف" }
        require(profile.receivableOrPayable > 0.0) { "لا يوجد مبلغ مستحق لإرسال تذكير به" }
        val currencySymbol = db.financeDao().getCurrencies(companyId)
            .firstOrNull { it.id == profile.account.defaultCurrencyId }?.symbol.orEmpty()
        val amount = com.smartaccountant.pro.core.utils.CurrencyFormatter.format(
            profile.receivableOrPayable, currencySymbol
        )
        return when (profile.account.category) {
            AccountCategory.CUSTOMERS ->
                "مرحباً ${profile.account.name}، نود تذكيركم بأن المبلغ المتبقي على حسابكم هو $amount. " +
                    "نرجو التواصل معنا لتسوية الرصيد. شكراً لكم."
            AccountCategory.SUPPLIERS ->
                "مرحباً ${profile.account.name}، نود تذكيركم بأن المبلغ المستحق لكم لدينا هو $amount. " +
                    "يرجى التواصل معنا لتنسيق السداد. شكراً لكم."
            else -> throw IllegalArgumentException("التذكير متاح للعملاء والموردين فقط")
        }
    }

    private fun requireCompanyUser(user: UserEntity, companyId: String) {
        require(companyId.isNotBlank() && user.companyId == companyId) {
            "لا يمكن الوصول إلى بيانات شركة أخرى"
        }
    }

    private fun requirePartyAccount(account: AccountEntity) {
        require(account.category == AccountCategory.CUSTOMERS || account.category == AccountCategory.SUPPLIERS) {
            "الملف متاح للعملاء والموردين فقط"
        }
    }
}

object PartyLedgerCalculations {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun statement(
        account: AccountEntity,
        vouchers: List<VoucherEntity>,
        invoices: List<InvoiceEntity>
    ): List<PartyLedgerEntry> {
        val transactions = mutableListOf<PartyLedgerEntry>()
        vouchers.filterNot { it.isDeleted || it.id.startsWith("invpay-") }.forEach { voucher ->
            val amount = voucher.amount * voucher.exchangeRate
            val delta = when (voucher.type) {
                VoucherType.RECEIPT, VoucherType.REMITTANCE_IN ->
                    if (voucher.accountId == account.id) -amount else 0.0
                VoucherType.PAYMENT, VoucherType.REMITTANCE_OUT, VoucherType.CURRENCY_EXCHANGE ->
                    if (voucher.accountId == account.id) amount else 0.0
                VoucherType.SIMPLE_JOURNAL, VoucherType.ADJUSTMENT, VoucherType.EXPRESS -> when {
                    voucher.accountId == account.id -> amount
                    voucher.counterAccountId == account.id -> -amount
                    else -> 0.0
                }
                VoucherType.OPENING_BALANCE -> if (voucher.accountId == account.id) amount else 0.0
                VoucherType.BOX_TRANSFER -> 0.0
            }
            if (delta != 0.0) {
                transactions += PartyLedgerEntry(
                    id = voucher.id,
                    dateMillis = voucher.dateMillis,
                    description = voucher.description.ifBlank { voucher.type.arabicTitle },
                    amount = amount,
                    balanceChange = delta,
                    runningBalance = 0.0,
                    voucher = voucher
                )
            }
        }
        invoices.filter { it.isPosted && !it.isArchived && !it.isDeleted }.forEach { invoice ->
            val total = invoice.netTotal * invoice.exchangeRate
            val paid = invoice.paidAmount * invoice.exchangeRate
            val delta = when (invoice.type) {
                InvoiceType.SALE, InvoiceType.FREE_SALE ->
                    if (account.category == AccountCategory.CUSTOMERS) {
                        if (invoice.paymentMethod == PaymentMethod.CASH) {
                            if (vouchers.any { it.id == AutomaticVoucherIds.cashSaleReceipt(invoice.id) }) total else 0.0
                        } else (total - paid).coerceAtLeast(0.0)
                    } else 0.0
                InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE ->
                    if (account.category == AccountCategory.SUPPLIERS) {
                        if (invoice.paymentMethod == PaymentMethod.CASH) 0.0 else -(total - paid).coerceAtLeast(0.0)
                    } else 0.0
                InvoiceType.SALE_RETURN ->
                    if (account.category == AccountCategory.CUSTOMERS) -total else 0.0
                InvoiceType.PURCHASE_RETURN ->
                    if (account.category == AccountCategory.SUPPLIERS) total else 0.0
                else -> 0.0
            }
            transactions += PartyLedgerEntry(
                id = invoice.id,
                dateMillis = invoice.dateMillis,
                description = "${invoice.type.arabicTitle} ${invoice.displayNumber.ifBlank { "#${invoice.invoiceNumber}" }}",
                amount = total,
                balanceChange = delta,
                runningBalance = 0.0,
                invoice = invoice
            )
        }
        var balance = account.openingDebit - account.openingCredit
        return transactions.sortedWith(compareBy<PartyLedgerEntry> { it.dateMillis }.thenBy { it.id })
            .map { entry ->
                balance += entry.balanceChange
                entry.copy(runningBalance = balance)
            }
    }

    fun overdueInvoices(
        accountsById: Map<String, AccountEntity>,
        invoices: List<InvoiceEntity>,
        todayStartMillis: Long,
        payments: List<VoucherEntity> = emptyList()
    ): List<OverdueInvoice> {
        val invoicesByAccount = invoices.groupBy { it.accountId }
        val paymentsByAccount = payments.groupBy { it.accountId }
        val outstandingByInvoice = accountsById.values.associate { account ->
            account.id to invoiceOutstandingById(
                account,
                invoicesByAccount[account.id].orEmpty(),
                paymentsByAccount[account.id].orEmpty()
            )
        }
        return invoices.asSequence()
            .filter {
                !it.isDeleted && !it.isArchived && it.isPosted && it.paymentMethod == PaymentMethod.CREDIT &&
                    it.dueDateMillis < todayStartMillis &&
                    it.type in setOf(InvoiceType.SALE, InvoiceType.FREE_SALE, InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE)
            }
            .mapNotNull { invoice ->
                val account = accountsById[invoice.accountId] ?: return@mapNotNull null
                if (account.isDeleted || account.category !in setOf(AccountCategory.CUSTOMERS, AccountCategory.SUPPLIERS)) {
                    return@mapNotNull null
                }
                val outstanding = outstandingByInvoice[account.id]?.get(invoice.id) ?: 0.0
                if (outstanding <= 0.0) return@mapNotNull null
                val daysLate = daysBetweenLocalDates(invoice.dueDateMillis, todayStartMillis)
                OverdueInvoice(invoice, account.name, daysLate, outstanding, bucketForDaysLate(daysLate))
            }
            .sortedWith(compareByDescending<OverdueInvoice> { it.daysLate }.thenBy { it.invoice.dueDateMillis })
            .toList()
    }

    fun invoiceOutstandingById(
        account: AccountEntity,
        invoices: List<InvoiceEntity>,
        payments: List<VoucherEntity>
    ): Map<String, Double> {
        val eligibleInvoices = invoices.filter { invoice ->
            invoice.isPosted && !invoice.isArchived && !invoice.isDeleted &&
                invoice.paymentMethod == PaymentMethod.CREDIT &&
                when (account.category) {
                    AccountCategory.CUSTOMERS -> invoice.type == InvoiceType.SALE || invoice.type == InvoiceType.FREE_SALE
                    AccountCategory.SUPPLIERS -> invoice.type == InvoiceType.PURCHASE || invoice.type == InvoiceType.FREE_PURCHASE
                    else -> false
                }
        }.sortedWith(compareBy<InvoiceEntity> { it.dateMillis }.thenBy { it.invoiceNumber })
        val remaining = eligibleInvoices.associate { invoice ->
            invoice.id to (invoice.netTotal - invoice.paidAmount).coerceAtLeast(0.0) * invoice.exchangeRate
        }.toMutableMap()
        var availableCredit = when (account.category) {
            AccountCategory.CUSTOMERS -> (account.openingCredit - account.openingDebit).coerceAtLeast(0.0)
            AccountCategory.SUPPLIERS -> (account.openingDebit - account.openingCredit).coerceAtLeast(0.0)
            else -> 0.0
        }
        val returns = invoices.asSequence()
            .filter { it.isPosted && !it.isArchived && !it.isDeleted }
            .filter {
                (account.category == AccountCategory.CUSTOMERS && it.type == InvoiceType.SALE_RETURN) ||
                    (account.category == AccountCategory.SUPPLIERS && it.type == InvoiceType.PURCHASE_RETURN)
            }
            .sortedWith(compareBy<InvoiceEntity> { it.dateMillis }.thenBy { it.invoiceNumber })
            .toList()
        for (returnInvoice in returns) {
            val amount = returnInvoice.netTotal * returnInvoice.exchangeRate
            val originalId = returnInvoice.originalInvoiceId
            val original = invoices.firstOrNull { it.id == originalId }
            if (original?.paymentMethod == PaymentMethod.CASH) continue
            val originalBalance = originalId?.let { remaining[it] } ?: 0.0
            val applied = minOf(originalBalance, amount)
            if (originalId != null && remaining.containsKey(originalId)) {
                remaining[originalId] = originalBalance - applied
            }
            availableCredit += amount - applied
        }
        availableCredit += payments.asSequence()
            .filter { it.accountId == account.id && !it.isDeleted }
            .sumOf { voucher ->
                val isCredit = when (account.category) {
                    AccountCategory.CUSTOMERS ->
                        voucher.type == VoucherType.RECEIPT || voucher.type == VoucherType.REMITTANCE_IN
                    AccountCategory.SUPPLIERS ->
                        voucher.type == VoucherType.PAYMENT || voucher.type == VoucherType.REMITTANCE_OUT
                    else -> false
                }
                if (isCredit) voucher.amount * voucher.exchangeRate else 0.0
            }
        for (invoice in eligibleInvoices) {
            val invoiceBalance = remaining.getValue(invoice.id)
            val applied = minOf(invoiceBalance, availableCredit)
            remaining[invoice.id] = invoiceBalance - applied
            availableCredit -= applied
        }
        return remaining
    }

    fun bucketForDaysLate(daysLate: Int): OverdueBucket {
        require(daysLate >= 0) { "عدد أيام التأخير لا يمكن أن يكون سالباً" }
        return when (daysLate) {
            in 0..15 -> OverdueBucket.DAYS_0_15
            in 16..30 -> OverdueBucket.DAYS_16_30
            in 31..60 -> OverdueBucket.DAYS_31_60
            else -> OverdueBucket.OVER_60
        }
    }

    fun overdueReport(invoices: List<OverdueInvoice>): OverdueReport {
        val totals = OverdueBucket.values().associateWith { bucket ->
            invoices.filter { it.bucket == bucket }.sumOf { it.outstandingAmount }
        }
        return OverdueReport(invoices, invoices.size, invoices.sumOf { it.outstandingAmount }, totals)
    }

    private fun daysBetweenLocalDates(fromMillis: Long, toMillis: Long): Int {
        fun utcDay(localMillis: Long): Long {
            val local = Calendar.getInstance().apply { timeInMillis = localMillis }
            return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis / MILLIS_PER_DAY
        }
        return (utcDay(toMillis) - utcDay(fromMillis)).coerceAtLeast(0).toInt()
    }
}
