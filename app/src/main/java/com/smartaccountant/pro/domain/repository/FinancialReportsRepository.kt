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

data class FinancialReportLine(
    val name: String,
    val category: AccountCategory,
    val amount: Double
)

data class ProfitLossReport(
    val fromMillis: Long,
    val toMillis: Long,
    val revenues: List<FinancialReportLine>,
    val expenses: List<FinancialReportLine>,
    val totalRevenue: Double,
    val totalExpense: Double,
    val netProfit: Double
)

data class BalanceSheetReport(
    val asOfMillis: Long,
    val assets: List<FinancialReportLine>,
    val liabilities: List<FinancialReportLine>,
    val equity: List<FinancialReportLine>,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val totalEquity: Double,
    val balanceDifference: Double
) {
    val totalLiabilitiesAndEquity: Double
        get() = totalLiabilities + totalEquity
}

enum class AgingBucket(val label: String) {
    DAYS_0_30("0-30 يوم"),
    DAYS_31_60("31-60 يوم"),
    DAYS_61_90("61-90 يوم"),
    OVER_90("أكثر من 90 يوم")
}

data class AgingAccountSummary(
    val accountId: String,
    val accountName: String,
    val category: AccountCategory,
    val current: Double,
    val days31To60: Double,
    val days61To90: Double,
    val over90: Double
) {
    val total: Double
        get() = current + days31To60 + days61To90 + over90

    fun amountIn(bucket: AgingBucket): Double = when (bucket) {
        AgingBucket.DAYS_0_30 -> current
        AgingBucket.DAYS_31_60 -> days31To60
        AgingBucket.DAYS_61_90 -> days61To90
        AgingBucket.OVER_90 -> over90
    }
}

data class AgingReport(
    val asOfMillis: Long,
    val category: AccountCategory,
    val accounts: List<AgingAccountSummary>,
    val totals: Map<AgingBucket, Double>
) {
    val total: Double
        get() = totals.values.sum()
}

class FinancialReportsRepository(private val db: AppDatabase) {
    suspend fun getProfitLoss(
        user: UserEntity,
        companyId: String,
        fromMillis: Long,
        toMillis: Long
    ): ProfitLossReport {
        requireFinancialReportAccess(user, companyId)
        require(fromMillis <= toMillis) { "تاريخ بداية الفترة يجب ألا يتجاوز تاريخ نهايتها" }
        val accounts = db.accountDao().getAllActiveAccounts(companyId)
        val dbBalances = db.reportsDao().getVoucherBalances(companyId, fromMillis, toMillis, excludeOpening = true)
        val invoices = db.invoiceDao().getPostedInvoicesThroughDate(companyId, toMillis)
        return FinancialReportCalculations.profitLoss(accounts, dbBalances, invoices, fromMillis, toMillis)
    }

    suspend fun getBalanceSheet(
        user: UserEntity,
        companyId: String,
        asOfMillis: Long
    ): BalanceSheetReport {
        requireFinancialReportAccess(user, companyId)
        val accounts = db.accountDao().getAllActiveAccounts(companyId)
        val dbBalances = db.reportsDao().getVoucherBalances(companyId, null, asOfMillis, excludeOpening = false)
        val invoices = db.invoiceDao().getPostedInvoicesThroughDate(companyId, asOfMillis)
        return FinancialReportCalculations.balanceSheet(accounts, dbBalances, invoices, asOfMillis)
    }

    suspend fun getAging(
        user: UserEntity,
        companyId: String,
        asOfMillis: Long,
        category: AccountCategory
    ): AgingReport {
        requireFinancialReportAccess(user, companyId)
        require(category == AccountCategory.CUSTOMERS || category == AccountCategory.SUPPLIERS) {
            "تقرير أعمار الديون متاح للعملاء والموردين فقط"
        }
        val accounts = db.accountDao().getAllActiveAccounts(companyId)
        val invoices = db.invoiceDao().getPostedInvoicesThroughDate(companyId, asOfMillis)
        val vouchers = db.voucherDao().getVouchersThroughDate(companyId, asOfMillis)
        return FinancialReportCalculations.aging(accounts, invoices, asOfMillis, category, vouchers)
    }

    private fun requireFinancialReportAccess(user: UserEntity, companyId: String) {
        require(user.companyId == companyId) { "لا يمكن عرض تقارير شركة أخرى" }
        PermissionManager.requirePermission(
            user,
            { it.canViewFinancialReports },
            "لا تملك صلاحية عرض التقارير المالية"
        )
    }
}

object FinancialReportCalculations {
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun profitLoss(
        accounts: List<AccountEntity>,
        dbBalances: List<com.smartaccountant.pro.data.local.dao.AccountAggregatedBalance>,
        invoices: List<InvoiceEntity>,
        fromMillis: Long,
        toMillis: Long
    ): ProfitLossReport {
        require(fromMillis <= toMillis) { "تاريخ بداية الفترة يجب ألا يتجاوز تاريخ نهايتها" }
        val balances = calculateNetBalances(
            accounts,
            dbBalances,
            invoices.filter {
                it.dateMillis in fromMillis..toMillis && it.isPosted && !it.isArchived && !it.isDeleted
            },
            includeOpeningBalances = false
        )
        val revenues = accounts
            .filter { it.category == AccountCategory.REVENUES }
            .mapNotNull { account -> balances[account.id]?.let { FinancialReportLine(account.name, account.category, -it) } }
        val expenses = accounts
            .filter { it.category == AccountCategory.EXPENSES }
            .mapNotNull { account -> balances[account.id]?.let { FinancialReportLine(account.name, account.category, it) } }
        val totalRevenue = revenues.sumOf { it.amount }
        val totalExpense = expenses.sumOf { it.amount }
        return ProfitLossReport(
            fromMillis, toMillis, revenues, expenses, totalRevenue, totalExpense, totalRevenue - totalExpense
        )
    }

    fun balanceSheet(
        accounts: List<AccountEntity>,
        dbBalances: List<com.smartaccountant.pro.data.local.dao.AccountAggregatedBalance>,
        invoices: List<InvoiceEntity>,
        asOfMillis: Long
    ): BalanceSheetReport {
        val balances = calculateNetBalances(
            accounts,
            dbBalances,
            invoices.filter { it.dateMillis <= asOfMillis && it.isPosted && !it.isArchived && !it.isDeleted },
            includeOpeningBalances = true
        )
        val assets = accounts.filter {
            it.category == AccountCategory.ASSETS || it.category == AccountCategory.CUSTOMERS ||
                it.category == AccountCategory.BANKS_EXCHANGERS || it.category == AccountCategory.EMPLOYEES ||
                it.category == AccountCategory.OTHER
        }.mapNotNull { account ->
            balances[account.id]?.let { FinancialReportLine(account.name, account.category, it) }
        }
        val liabilities = accounts.filter {
            it.category == AccountCategory.LIABILITIES || it.category == AccountCategory.SUPPLIERS
        }.mapNotNull { account ->
            balances[account.id]?.let { FinancialReportLine(account.name, account.category, -it) }
        }
        val equity = accounts.filter { it.category == AccountCategory.EQUITY }
            .mapNotNull { account ->
                balances[account.id]?.let { FinancialReportLine(account.name, account.category, -it) }
            }.toMutableList()
        val retainedEarnings = accounts.sumOf { account ->
            val balance = balances[account.id] ?: 0.0
            when (account.category) {
                AccountCategory.REVENUES -> -balance
                AccountCategory.EXPENSES -> -balance
                else -> 0.0
            }
        }
        if (retainedEarnings != 0.0) {
            equity.add(FinancialReportLine("الأرباح المحتجزة حتى التاريخ", AccountCategory.EQUITY, retainedEarnings))
        }
        val totalAssets = assets.sumOf { it.amount }
        val totalLiabilities = liabilities.sumOf { it.amount }
        val totalEquity = equity.sumOf { it.amount }
        return BalanceSheetReport(
            asOfMillis, assets, liabilities, equity, totalAssets, totalLiabilities, totalEquity,
            totalAssets - totalLiabilities - totalEquity
        )
    }

    fun aging(
        accounts: List<AccountEntity>,
        invoices: List<InvoiceEntity>,
        asOfMillis: Long,
        category: AccountCategory,
        vouchers: List<VoucherEntity> = emptyList()
    ): AgingReport {
        require(category == AccountCategory.CUSTOMERS || category == AccountCategory.SUPPLIERS) {
            "تقرير أعمار الديون متاح للعملاء والموردين فقط"
        }
        val accountsById = accounts.associateBy { it.id }
        val eligible = invoices.asSequence()
            .filter {
                it.paymentMethod == PaymentMethod.CREDIT && it.isPosted && !it.isArchived && !it.isDeleted &&
                    it.dateMillis <= asOfMillis && (it.netTotal - it.paidAmount) > 0.0
            }
            .filter { invoice ->
                when (invoice.type) {
                    InvoiceType.SALE, InvoiceType.FREE_SALE -> AccountCategory.CUSTOMERS
                    InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> AccountCategory.SUPPLIERS
                    else -> null
                } == category && accountsById[invoice.accountId]?.let { !it.isDeleted && it.category == category } == true
            }
            .sortedWith(compareBy<InvoiceEntity> { it.dateMillis }.thenBy { it.invoiceNumber })
            .toList()
        val outstanding = eligible.associate { invoice ->
            invoice.id to (invoice.netTotal - invoice.paidAmount).coerceAtLeast(0.0) * invoice.exchangeRate
        }.toMutableMap()
        val creditByAccount = mutableMapOf<String, Double>()
        invoices.asSequence()
            .filter { it.isPosted && !it.isArchived && !it.isDeleted && it.dateMillis <= asOfMillis }
            .filter {
                (category == AccountCategory.CUSTOMERS && it.type == InvoiceType.SALE_RETURN) ||
                    (category == AccountCategory.SUPPLIERS && it.type == InvoiceType.PURCHASE_RETURN)
            }
            .sortedBy { it.dateMillis }
            .forEach { returnInvoice ->
                val original = invoices.firstOrNull { it.id == returnInvoice.originalInvoiceId }
                if (original?.paymentMethod == PaymentMethod.CASH) return@forEach
                val value = returnInvoice.netTotal * returnInvoice.exchangeRate
                val originalId = returnInvoice.originalInvoiceId
                val balance = originalId?.let { outstanding[it] } ?: 0.0
                val applied = minOf(balance, value)
                if (originalId != null && outstanding.containsKey(originalId)) {
                    outstanding[originalId] = balance - applied
                }
                creditByAccount[returnInvoice.accountId] =
                    (creditByAccount[returnInvoice.accountId] ?: 0.0) + value - applied
            }
        accounts.filter { !it.isDeleted && it.category == category }.forEach { account ->
            val openingCredit = if (category == AccountCategory.CUSTOMERS) {
                (account.openingCredit - account.openingDebit).coerceAtLeast(0.0)
            } else {
                (account.openingDebit - account.openingCredit).coerceAtLeast(0.0)
            }
            creditByAccount[account.id] = (creditByAccount[account.id] ?: 0.0) + openingCredit
        }
        vouchers.asSequence()
            .filter { !it.isDeleted && it.dateMillis <= asOfMillis }
            .filter {
                (category == AccountCategory.CUSTOMERS &&
                    (it.type == VoucherType.RECEIPT || it.type == VoucherType.REMITTANCE_IN)) ||
                    (category == AccountCategory.SUPPLIERS &&
                        (it.type == VoucherType.PAYMENT || it.type == VoucherType.REMITTANCE_OUT))
            }
            .forEach { voucher ->
                creditByAccount[voucher.accountId] =
                    (creditByAccount[voucher.accountId] ?: 0.0) + voucher.amount * voucher.exchangeRate
            }
        val orderedAccounts = eligible.groupBy { it.accountId }
        val amounts = mutableMapOf<String, DoubleArray>()
        orderedAccounts.forEach { (accountId, accountInvoices) ->
            var available = creditByAccount[accountId] ?: 0.0
            accountInvoices.forEach { invoice ->
                val balance = outstanding[invoice.id] ?: 0.0
                val applied = minOf(balance, available)
                outstanding[invoice.id] = balance - applied
                available -= applied
            }
        }
        eligible.forEach { invoice ->
                val account = accountsById[invoice.accountId] ?: return@forEach
                val openAmount = outstanding[invoice.id] ?: 0.0
                if (openAmount <= 0.0) return@forEach
                val dueDate = invoice.dueDateMillis.takeIf { it > 0 } ?: invoice.dateMillis
                val days = (asOfMillis - dueDate) / MILLIS_PER_DAY
                val bucket = when {
                    days <= 30 -> 0
                    days <= 60 -> 1
                    days <= 90 -> 2
                    else -> 3
                }
                val balance = amounts.getOrPut(account.id) { DoubleArray(4) }
                balance[bucket] += openAmount
        }
        val summaries = amounts.mapNotNull { (accountId, values) ->
            val account = accountsById[accountId] ?: return@mapNotNull null
            AgingAccountSummary(
                account.id, account.name, category, values[0], values[1], values[2], values[3]
            )
        }.sortedByDescending { it.total }
        val totals = AgingBucket.values().associateWith { bucket ->
            summaries.sumOf { it.amountIn(bucket) }
        }
        return AgingReport(asOfMillis, category, summaries, totals)
    }

    private fun calculateNetBalances(
        accounts: List<AccountEntity>,
        dbBalances: List<com.smartaccountant.pro.data.local.dao.AccountAggregatedBalance>,
        invoices: List<InvoiceEntity>,
        includeOpeningBalances: Boolean
    ): Map<String, Double> {
        val balances = accounts.associate { account ->
            account.id to if (includeOpeningBalances) account.openingDebit - account.openingCredit else 0.0
        }.toMutableMap()
        
        dbBalances.forEach { bal ->
            balances.addIfPresent(bal.accountId, bal.totalDebit - bal.totalCredit)
        }
        
        val voucherIds = emptySet<String>() // يمكن التعديل لاحقاً إذا لزم
        
        invoices.forEach { invoice ->
            val total = invoice.netTotal * invoice.exchangeRate
            val paid = invoice.paidAmount * invoice.exchangeRate
            when (invoice.type) {
                InvoiceType.SALE, InvoiceType.FREE_SALE -> {
                    balances.addIfPresent(invoice.accountId, total)
                    if (invoice.paymentMethod == PaymentMethod.CASH) {
                        if (AutomaticVoucherIds.cashSaleReceipt(invoice.id) !in voucherIds) {
                            balances.addIfPresent(invoice.accountId, -total)
                        }
                    } else if (paid > 0.0) balances.addIfPresent(invoice.accountId, -paid)
                }
                InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> {
                    balances.addIfPresent(invoice.accountId, -total)
                    if (invoice.paymentMethod == PaymentMethod.CASH) balances.addIfPresent(invoice.accountId, total)
                    else if (paid > 0.0) balances.addIfPresent(invoice.accountId, paid)
                }
                InvoiceType.SALE_RETURN -> balances.addIfPresent(invoice.accountId, -total)
                InvoiceType.PURCHASE_RETURN -> balances.addIfPresent(invoice.accountId, total)
                else -> Unit
            }
        }
        return balances
    }

    private fun MutableMap<String, Double>.addIfPresent(accountId: String?, amount: Double) {
        if (accountId == null) return
        if (containsKey(accountId)) this[accountId] = getValue(accountId) + amount
    }
}
