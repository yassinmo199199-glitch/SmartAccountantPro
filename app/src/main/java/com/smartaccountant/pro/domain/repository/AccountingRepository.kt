package com.smartaccountant.pro.domain.repository

import androidx.room.withTransaction
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.sync.SyncEngine
import com.smartaccountant.pro.core.utils.DocumentNumberGenerator
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.*
import kotlinx.coroutines.flow.Flow

data class AccountBalanceSummary(
    val account: AccountEntity,
    val totalDebit: Double,
    val totalCredit: Double,
    val netBalance: Double,
    val transactionCount: Int
)

class AccountingRepository(
    private val db: AppDatabase,
    private val syncEngine: SyncEngine,
    private val docNumberGen: DocumentNumberGenerator,
    private val deviceName: String
) {
    fun observeAccounts(companyId: String): Flow<List<AccountEntity>> =
        db.accountDao().observeAccounts(companyId)

    fun observeVouchers(companyId: String): Flow<List<VoucherEntity>> =
        db.voucherDao().observeVouchers(companyId)

    suspend fun saveAccount(currentUser: UserEntity, account: AccountEntity, isEdit: Boolean) {
        if (isEdit) {
            PermissionManager.requirePermission(currentUser, { it.canEditAccount }, "لا تملك صلاحية تعديل بيانات الحساب")
        } else {
            PermissionManager.requirePermission(currentUser, { it.canAddAccount }, "لا تملك صلاحية إضافة حساب جديد")
        }
        if (account.creditLimit > 0.0) {
            PermissionManager.requirePermission(currentUser, { it.canSetCreditLimit }, "لا تملك صلاحية تحديد سقف المديونية")
        }

        val now = System.currentTimeMillis()
        val updated = account.copy(
            createdByUserId = if (isEdit) account.createdByUserId else currentUser.id,
            deviceId = deviceName,
            updatedAt = now,
            syncStatus = if (isEdit) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_CREATE
        )
        db.withTransaction {
            db.accountDao().upsertAccount(updated)
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = updated.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = if (isEdit) AuditAction.UPDATE else AuditAction.CREATE,
                    entityType = "حساب",
                    entityId = updated.id,
                    summary = "${if (isEdit) "تعديل" else "إضافة"} حساب: ${updated.name}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(updated.companyId)
    }

    suspend fun saveCompoundVoucher(
        currentUser: UserEntity, 
        voucher: VoucherEntity, 
        lines: List<com.smartaccountant.pro.data.model.VoucherLineEntity>, 
        isEdit: Boolean = false
    ) {
        if (isEdit) {
            PermissionManager.requirePermission(currentUser, { it.canEditVoucher }, "لا تملك صلاحية تعديل السندات")
        } else {
            PermissionManager.requirePermission(currentUser, { it.canAddVoucher }, "لا تملك صلاحية إضافة سندات")
        }

        // تحقق من التوازن
        val totalDebit = lines.sumOf { it.debit }
        val totalCredit = lines.sumOf { it.credit }
        require(kotlin.math.abs(totalDebit - totalCredit) < 0.001) { "لا يمكن حفظ قيد غير متزن" }
        require(lines.isNotEmpty()) { "لا يمكن حفظ قيد بدون تفاصيل" }

        val now = System.currentTimeMillis()
        val seqNum = if (voucher.voucherNumber > 0) voucher.voucherNumber
        else db.voucherDao().getNextVoucherNumber(voucher.companyId)
        val dispNum = voucher.displayNumber.ifBlank {
            docNumberGen.formatVoucherDisplayNumber(seqNum, voucher.dateMillis)
        }

        val finalVoucher = voucher.copy(
            voucherNumber = seqNum,
            displayNumber = dispNum,
            createdByUserId = if (isEdit && voucher.createdByUserId.isNotBlank()) voucher.createdByUserId else currentUser.id,
            createdByUserName = if (isEdit && voucher.createdByUserName.isNotBlank()) voucher.createdByUserName else currentUser.fullName,
            deviceId = deviceName,
            updatedAt = now,
            syncStatus = if (isEdit) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_CREATE
        )

        db.withTransaction {
            db.voucherDao().upsertVoucher(finalVoucher)
            if (isEdit) {
                db.voucherDao().deleteVoucherLines(finalVoucher.id)
            }
            db.voucherDao().upsertVoucherLines(lines)
            
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = finalVoucher.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = if (isEdit) AuditAction.UPDATE else AuditAction.CREATE,
                    entityType = "قيد يومية مركب",
                    entityId = finalVoucher.id,
                    summary = "قيد يومية مركب ($dispNum) بإجمالي ${finalVoucher.amount}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(finalVoucher.companyId)
    }

    suspend fun saveVoucher(currentUser: UserEntity, voucher: VoucherEntity, isEdit: Boolean = false) {
        if (isEdit) {
            PermissionManager.requirePermission(currentUser, { it.canEditVoucher }, "لا تملك صلاحية تعديل السندات")
        } else {
            PermissionManager.requirePermission(currentUser, { it.canAddVoucher }, "لا تملك صلاحية إضافة سندات")
        }
        if (voucher.type == VoucherType.REMITTANCE_IN || voucher.type == VoucherType.REMITTANCE_OUT) {
            PermissionManager.requirePermission(currentUser, { it.canManageRemittances }, "لا تملك صلاحية إدارة الحوالات")
        }
        if (voucher.type == VoucherType.CURRENCY_EXCHANGE) {
            PermissionManager.requirePermission(currentUser, { it.canExchangeCurrency }, "لا تملك صلاحية مصارفة العملات")
        }
        if (voucher.type == VoucherType.BOX_TRANSFER) {
            PermissionManager.requirePermission(currentUser, { it.canTransferBetweenBoxes }, "لا تملك صلاحية التحويل بين الصناديق")
        }
        voucher.cashBoxId?.let { boxId ->
            if (!PermissionManager.canAccessCashBox(currentUser, boxId)) {
                throw SecurityException("غير مصرح لك باستخدام هذا الصندوق النقدي")
            }
        }

        if (voucher.type == VoucherType.PAYMENT) {
            val summary = calculateAccountBalance(voucher.companyId, voucher.accountId)
            if (summary != null && summary.account.creditLimit > 0.0) {
                val projected = summary.netBalance + (voucher.amount * voucher.exchangeRate)
                if (projected > summary.account.creditLimit) {
                    throw IllegalStateException("لا يمكن تجاوز الحد الأقصى للمديونية (${summary.account.creditLimit})")
                }
            }
        }

        val now = System.currentTimeMillis()
        val seqNum = if (voucher.voucherNumber > 0) voucher.voucherNumber
        else db.voucherDao().getNextVoucherNumber(voucher.companyId)
        val dispNum = voucher.displayNumber.ifBlank {
            docNumberGen.formatVoucherDisplayNumber(seqNum, voucher.dateMillis)
        }

        val finalVoucher = voucher.copy(
            voucherNumber = seqNum,
            displayNumber = dispNum,
            createdByUserId = if (isEdit && voucher.createdByUserId.isNotBlank()) voucher.createdByUserId else currentUser.id,
            createdByUserName = if (isEdit && voucher.createdByUserName.isNotBlank()) voucher.createdByUserName else currentUser.fullName,
            deviceId = deviceName,
            updatedAt = now,
            syncStatus = if (isEdit) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_CREATE
        )

        val cashBoxAccountId = finalVoucher.cashBoxId?.let { boxId ->
            val box = db.financeDao().getCashBoxById(boxId)
            box?.id?.ifBlank { boxId } ?: boxId
        } ?: finalVoucher.counterAccountId.orEmpty()

        val autoLines = mutableListOf<VoucherLineEntity>()
        if (finalVoucher.accountId.isNotBlank() && cashBoxAccountId.isNotBlank()) {
            if (finalVoucher.type == VoucherType.RECEIPT) {
                autoLines.add(
                    VoucherLineEntity(
                        voucherId = finalVoucher.id,
                        accountId = finalVoucher.accountId,
                        debit = 0.0,
                        credit = finalVoucher.amount,
                        currencyId = finalVoucher.currencyId,
                        exchangeRate = finalVoucher.exchangeRate,
                        note = finalVoucher.description
                    )
                )
                autoLines.add(
                    VoucherLineEntity(
                        voucherId = finalVoucher.id,
                        accountId = cashBoxAccountId,
                        debit = finalVoucher.amount,
                        credit = 0.0,
                        currencyId = finalVoucher.currencyId,
                        exchangeRate = finalVoucher.exchangeRate,
                        note = finalVoucher.description
                    )
                )
            } else if (finalVoucher.type == VoucherType.PAYMENT) {
                autoLines.add(
                    VoucherLineEntity(
                        voucherId = finalVoucher.id,
                        accountId = finalVoucher.accountId,
                        debit = finalVoucher.amount,
                        credit = 0.0,
                        currencyId = finalVoucher.currencyId,
                        exchangeRate = finalVoucher.exchangeRate,
                        note = finalVoucher.description
                    )
                )
                autoLines.add(
                    VoucherLineEntity(
                        voucherId = finalVoucher.id,
                        accountId = cashBoxAccountId,
                        debit = 0.0,
                        credit = finalVoucher.amount,
                        currencyId = finalVoucher.currencyId,
                        exchangeRate = finalVoucher.exchangeRate,
                        note = finalVoucher.description
                    )
                )
            }
        }

        db.withTransaction {
            db.voucherDao().upsertVoucher(finalVoucher)
            if (isEdit) {
                db.voucherDao().deleteVoucherLines(finalVoucher.id)
            }
            if (autoLines.isNotEmpty()) {
                db.voucherDao().upsertVoucherLines(autoLines)
            }
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = finalVoucher.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = if (isEdit) AuditAction.UPDATE else AuditAction.CREATE,
                    entityType = finalVoucher.type.arabicTitle,
                    entityId = finalVoucher.id,
                    summary = "${finalVoucher.type.arabicTitle} ($dispNum) بمبلغ ${finalVoucher.amount}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(finalVoucher.companyId)
    }

    /**
     * حذف سند يدوياً (soft-delete): يُزامَن كـ PENDING_DELETE ويُستبعد من كل التقارير والكشوف.
     * السندات الآلية (invpay-/recurring-/auto-cash-*) والأنواع المُدارة من شاشات مخصصة لا تُحذف من هنا.
     */
    suspend fun deleteVoucher(currentUser: UserEntity, voucher: VoucherEntity) {
        require(voucher.companyId == currentUser.companyId) { "لا يمكن حذف سند يخص شركة أخرى" }
        PermissionManager.requirePermission(currentUser, { it.canDeleteVoucher }, "لا تملك صلاحية حذف السندات")
        require(isManuallyDeletable(voucher)) { "هذا السند يُدار تلقائياً ولا يمكن حذفه يدوياً" }
        val now = System.currentTimeMillis()
        val dispNum = voucher.displayNumber.ifBlank { "#${voucher.voucherNumber}" }
        db.withTransaction {
            db.voucherDao().upsertVoucher(
                voucher.copy(isDeleted = true, updatedAt = now, syncStatus = SyncStatus.PENDING_DELETE)
            )
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = voucher.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = AuditAction.DELETE,
                    entityType = voucher.type.arabicTitle,
                    entityId = voucher.id,
                    summary = "حذف ${voucher.type.arabicTitle} ($dispNum) بمبلغ ${voucher.amount}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(voucher.companyId)
    }

    suspend fun calculateAccountBalance(
        companyId: String,
        accountId: String,
        currencyIdFilter: String? = null,
        maxDateMillis: Long? = null
    ): AccountBalanceSummary? {
        val account = db.accountDao().getAccountById(accountId) ?: return null
        // 1. جلب رصيد القيود المركبة من قاعدة البيانات باستخدام SQL مجمعة للسرعة
        val dbBalance = db.reportsDao().getVoucherBalanceForAccount(companyId, accountId, maxDateMillis)
        
        var debit = account.openingDebit
        var credit = account.openingCredit
        var count = 0

        if (dbBalance != null) {
            debit += dbBalance.totalDebit
            credit += dbBalance.totalCredit
            // لا نحسب count حالياً من التقرير لتجنب تعقيد الاستعلام أكثر من اللازم، يمكن إضافته لاحقاً
        }

        // 2. جلب الفواتير وجمعها (يمكن لاحقاً تحويلها إلى قيود حقيقية لتسهيل الأمر أكثر)
        val invoices = db.invoiceDao().getAccountInvoices(companyId, accountId)
        val voucherIds = db.voucherDao().getAllVouchers(companyId).map { it.id }.toSet()

        for (inv in invoices) {
            if (maxDateMillis != null && inv.dateMillis >= maxDateMillis) continue
            if (currencyIdFilter != null && inv.currencyId != currencyIdFilter) continue
            val valInCur = if (currencyIdFilter != null) inv.netTotal else inv.netTotal * inv.exchangeRate
            val paidInCur = if (currencyIdFilter != null) inv.paidAmount else inv.paidAmount * inv.exchangeRate
            count++
            when (inv.type) {
                InvoiceType.SALE, InvoiceType.FREE_SALE -> {
                    debit += valInCur
                    if (inv.paymentMethod == PaymentMethod.CASH) {
                        if (AutomaticVoucherIds.cashSaleReceipt(inv.id) !in voucherIds) credit += valInCur
                    } else if (paidInCur > 0) credit += paidInCur
                }
                InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> {
                    credit += valInCur
                    if (inv.paymentMethod == PaymentMethod.CASH) debit += valInCur
                    else if (paidInCur > 0) debit += paidInCur
                }
                InvoiceType.SALE_RETURN -> credit += valInCur
                InvoiceType.PURCHASE_RETURN -> debit += valInCur
                else -> {}
            }
        }

        return AccountBalanceSummary(
            account = account,
            totalDebit = debit,
            totalCredit = credit,
            netBalance = debit - credit,
            transactionCount = count
        )
    }

    /**
     * حساب صافي رصيد كل حسابات الشركة دفعة واحدة (debit - credit).
     * نفس منطق calculateAccountBalance لكن لكل الحسابات في مسار واحد —
     * يشمل كل أنواع السندات (قبض/صرف/قيود/حوالات/تسويات) والفواتير.
     */
    suspend fun calculateAllAccountBalances(
        companyId: String,
        fromMillis: Long? = null,
        toMillis: Long? = null
    ): Map<String, Double> {
        val accounts = db.accountDao().getAllActiveAccounts(companyId)
        if (accounts.isEmpty()) return emptyMap()
        
        // 1. جلب أرصدة كل الحسابات من قاعدة البيانات باستعلام SQL مجمع واحد!
        val dbBalances = db.reportsDao().getVoucherBalances(
            companyId = companyId,
            fromMillis = fromMillis,
            toMillis = toMillis,
            excludeOpening = false
        )
        
        val invoices = db.invoiceDao().getAllInvoices(companyId)
        val voucherIds = db.voucherDao().getAllVouchers(companyId).map { it.id }.toSet()

        val debitMap = accounts.associate { it.id to it.openingDebit }.toMutableMap()
        val creditMap = accounts.associate { it.id to it.openingCredit }.toMutableMap()

        fun inRange(millis: Long) =
            (fromMillis == null || millis >= fromMillis) && (toMillis == null || millis <= toMillis)

        // إضافة الأرصدة القادمة من السندات (تم حسابها مسبقاً في SQL)
        for (bal in dbBalances) {
            if (bal.accountId in debitMap) {
                debitMap[bal.accountId] = debitMap[bal.accountId]!! + bal.totalDebit
                creditMap[bal.accountId] = creditMap[bal.accountId]!! + bal.totalCredit
            }
        }

        for (inv in invoices) {
            if (!inRange(inv.dateMillis)) continue
            val accId = inv.accountId
            if (accId !in debitMap) continue
            val valInCur = inv.netTotal * inv.exchangeRate
            val paidInCur = inv.paidAmount * inv.exchangeRate
            when (inv.type) {
                InvoiceType.SALE, InvoiceType.FREE_SALE -> {
                    debitMap[accId] = debitMap[accId]!! + valInCur
                    if (inv.paymentMethod == PaymentMethod.CASH) {
                        if (AutomaticVoucherIds.cashSaleReceipt(inv.id) !in voucherIds) {
                            creditMap[accId] = creditMap[accId]!! + valInCur
                        }
                    } else if (paidInCur > 0) {
                        creditMap[accId] = creditMap[accId]!! + paidInCur
                    }
                }
                InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> {
                    creditMap[accId] = creditMap[accId]!! + valInCur
                    if (inv.paymentMethod == PaymentMethod.CASH) {
                        debitMap[accId] = debitMap[accId]!! + valInCur
                    } else if (paidInCur > 0) {
                        debitMap[accId] = debitMap[accId]!! + paidInCur
                    }
                }
                InvoiceType.SALE_RETURN -> creditMap[accId] = creditMap[accId]!! + valInCur
                InvoiceType.PURCHASE_RETURN -> debitMap[accId] = debitMap[accId]!! + valInCur
                else -> {}
            }
        }

        return accounts.associate { it.id to ((debitMap[it.id] ?: 0.0) - (creditMap[it.id] ?: 0.0)) }
    }

    /**
     * إغلاق الفترة المحاسبية الحقيقي (المُحدَّث):
     * 1. يحسب صافي رصيد كل حساب لكل عملة حتى تاريخ القطع.
     * 2. يُنشئ قيد افتتاحي (OPENING_BALANCE) لكل حساب في جدول السطور (voucher_lines).
     * 3. يعلّم السندات والفواتير القديمة كـ "مغلقة" (isArchived = true) بدلاً من حذفها! (لكي تظل مرئية في التقارير التاريخية)
     * 4. يُصفّر الأرصدة الافتتاحية القديمة في بطاقة الحساب (لأنها رُحِّلَت للسند الافتتاحي الجديد).
     */
    suspend fun closeAccountingPeriod(currentUser: UserEntity, companyId: String, cutoffMillis: Long): Int {
        PermissionManager.requirePermission(currentUser, { it.canClosePeriod }, "لا تملك صلاحية إغلاق الحسابات")
        val accounts = db.accountDao().getAllActiveAccounts(companyId)
        val currencies = db.financeDao().getCurrencies(companyId)
        val now = System.currentTimeMillis()
        var carriedForwardCount = 0

        db.withTransaction {
            val nextNum = db.voucherDao().getNextVoucherNumber(companyId)
            val disp = docNumberGen.formatVoucherDisplayNumber(nextNum, cutoffMillis)

            // إنشاء رأس السند الافتتاحي المجمع للسنة الجديدة
            val openingVoucher = VoucherEntity(
                id = java.util.UUID.randomUUID().toString(),
                companyId = companyId,
                voucherNumber = nextNum,
                displayNumber = disp,
                type = VoucherType.OPENING_BALANCE,
                dateMillis = cutoffMillis + 1000, // بعد القطع بثانية
                accountId = "System", // حقل صوري لرأس السند الافتتاحي
                currencyId = currencies.firstOrNull { it.isDefault }?.id ?: "",
                exchangeRate = 1.0,
                amount = 0.0,
                description = "رصيد افتتاحي مرحّل من إغلاق الفترة السابقة",
                createdByUserId = currentUser.id,
                createdByUserName = currentUser.fullName,
                deviceId = deviceName,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            
            val linesToInsert = mutableListOf<com.smartaccountant.pro.data.model.VoucherLineEntity>()

            for (acc in accounts) {
                for (cur in currencies) {
                    val summary = calculateAccountBalance(companyId, acc.id, cur.id, cutoffMillis) ?: continue
                    val net = summary.netBalance
                    if (kotlin.math.abs(net) > 0.001) {
                        linesToInsert.add(
                            com.smartaccountant.pro.data.model.VoucherLineEntity(
                                voucherId = openingVoucher.id,
                                accountId = acc.id,
                                debit = if (net > 0) net else 0.0,
                                credit = if (net < 0) kotlin.math.abs(net) else 0.0,
                                currencyId = cur.id,
                                exchangeRate = cur.sellRate,
                                note = "ترصيد ختامي - ${acc.name}"
                            )
                        )
                        carriedForwardCount++
                    }
                }
                
                db.accountDao().upsertAccount(
                    acc.copy(openingDebit = 0.0, openingCredit = 0.0, updatedAt = now, syncStatus = SyncStatus.PENDING_UPDATE)
                )
            }

            if (linesToInsert.isNotEmpty()) {
                db.voucherDao().upsertVoucher(openingVoucher)
                db.voucherDao().upsertVoucherLines(linesToInsert)
            }

            // تجميد (أرشفة) الحركات القديمة بدلاً من حذفها لتكون مرئية لتقارير السنوات السابقة
            db.reportsDao().archiveVouchersBefore(companyId, cutoffMillis, now)
            db.reportsDao().archiveInvoicesBefore(companyId, cutoffMillis, now)

            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = AuditAction.CLOSE_PERIOD,
                    entityType = "إغلاق فترة محاسبية",
                    entityId = companyId,
                    summary = "تم إغلاق الحسابات وترحيل $carriedForwardCount رصيد افتتاحي، وأرشفة الحركات القديمة"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(companyId)
        return carriedForwardCount
    }

    companion object {
        private val AUTOMATIC_VOUCHER_ID_PREFIXES = listOf(
            "invpay-", "recurring-", "auto-cash-sale-", "auto-cash-return-"
        )
        private val MANUALLY_DELETABLE_TYPES = setOf(
            VoucherType.RECEIPT, VoucherType.PAYMENT,
            VoucherType.SIMPLE_JOURNAL, VoucherType.ADJUSTMENT, VoucherType.EXPRESS
        )
        private val ENTRY_EDITABLE_TYPES = setOf(VoucherType.RECEIPT, VoucherType.PAYMENT)

        fun isAutomaticVoucher(voucher: VoucherEntity): Boolean =
            AUTOMATIC_VOUCHER_ID_PREFIXES.any { voucher.id.startsWith(it) }

        fun isManuallyDeletable(voucher: VoucherEntity): Boolean =
            !isAutomaticVoucher(voucher) && voucher.type in MANUALLY_DELETABLE_TYPES

        fun isEntryEditable(voucher: VoucherEntity): Boolean =
            !isAutomaticVoucher(voucher) && voucher.type in ENTRY_EDITABLE_TYPES
    }
}
