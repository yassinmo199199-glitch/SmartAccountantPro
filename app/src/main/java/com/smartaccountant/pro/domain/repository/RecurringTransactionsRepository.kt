package com.smartaccountant.pro.domain.repository

import androidx.room.withTransaction
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.sync.SyncEngine
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.*
import java.util.Calendar

class RecurringTransactionsRepository(
    private val db: AppDatabase,
    private val syncEngine: SyncEngine,
    private val deviceName: String
) {
    suspend fun save(user: UserEntity, transaction: RecurringTransactionEntity) {
        require(user.companyId == transaction.companyId) { "لا يمكن إدارة معاملة متكررة لشركة أخرى" }
        PermissionManager.requirePermission(
            user, { it.canManageRecurring }, "لا تملك صلاحية إدارة المعاملات المتكررة"
        )
        require(transaction.description.isNotBlank()) { "وصف المعاملة مطلوب" }
        require(transaction.amount > 0.0 && transaction.amount.isFinite()) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(transaction.occurrenceLimit == null || transaction.occurrenceLimit > 0) {
            "عدد التكرارات يجب أن يكون أكبر من صفر"
        }
        require(transaction.voucherType == VoucherType.RECEIPT || transaction.voucherType == VoucherType.PAYMENT) {
            "نوع المعاملة المتكررة يجب أن يكون سند قبض أو صرف"
        }
        require(db.accountDao().getAccountInCompany(transaction.companyId, transaction.accountId) != null) {
            "الحساب المحدد لا يتبع الشركة النشطة"
        }
        require(db.financeDao().getCashBoxInCompany(transaction.companyId, transaction.cashBoxId) != null) {
            "الصندوق المحدد لا يتبع الشركة النشطة"
        }
        require(db.financeDao().getCurrencyInCompany(transaction.companyId, transaction.currencyId) != null) {
            "العملة المحددة لا تتبع الشركة النشطة"
        }

        val now = System.currentTimeMillis()
        val existing = db.recurringTransactionDao()
            .getRecurringTransaction(transaction.companyId, transaction.id)
        db.recurringTransactionDao().upsertRecurringTransaction(
            transaction.copy(
                dueDayOfMonth = transaction.dueDayOfMonth.coerceIn(1, 31),
                createdByUserId = existing?.createdByUserId ?: user.id,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncStatus = if (existing == null) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE,
                conflictJson = null,
                isDeleted = false
            )
        )
        syncEngine.scheduleBackgroundSync(transaction.companyId)
    }

    suspend fun delete(user: UserEntity, transaction: RecurringTransactionEntity) {
        require(user.companyId == transaction.companyId) { "لا يمكن حذف معاملة متكررة لشركة أخرى" }
        PermissionManager.requirePermission(
            user, { it.canManageRecurring }, "لا تملك صلاحية إدارة المعاملات المتكررة"
        )
        db.recurringTransactionDao().upsertRecurringTransaction(
            transaction.copy(
                isActive = false,
                isDeleted = true,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_DELETE
            )
        )
        syncEngine.scheduleBackgroundSync(transaction.companyId)
    }

    suspend fun processDueTransactions(companyId: String, nowMillis: Long = System.currentTimeMillis()): Int {
        require(companyId.isNotBlank()) { "معرّف الشركة مطلوب لمعالجة المعاملات المتكررة" }
        var createdCount = 0
        val dueTransactions = db.recurringTransactionDao()
            .getDueRecurringTransactions(companyId, nowMillis)
        for (initial in dueTransactions) {
            var transaction = initial
            while (transaction.isActive &&
                transaction.nextDueDateMillis <= nowMillis &&
                (transaction.occurrenceLimit == null || transaction.executionCount < transaction.occurrenceLimit)
            ) {
                val dueAt = transaction.nextDueDateMillis
                val nextDate = RecurrenceSchedule.nextOccurrence(dueAt, transaction.frequency, transaction.dueDayOfMonth)
                val created = db.withTransaction {
                    val current = db.recurringTransactionDao()
                        .getRecurringTransaction(companyId, transaction.id) ?: return@withTransaction false
                    if (!current.isActive || current.nextDueDateMillis != dueAt) return@withTransaction false

                    val alreadyExecuted = current.lastExecutedDateMillis >= dueAt
                    if (!alreadyExecuted) {
                        val voucherId = "recurring-${current.id}-$dueAt"
                        val existingVoucher = db.voucherDao().getVoucherInCompany(companyId, voucherId)
                        if (existingVoucher == null) {
                            val account = db.accountDao().getAccountInCompany(companyId, current.accountId)
                                ?: error("الحساب المرتبط بالمعاملة المتكررة لم يعد متاحاً")
                            val cashBox = db.financeDao().getCashBoxInCompany(companyId, current.cashBoxId)
                                ?: error("الصندوق المرتبط بالمعاملة المتكررة لم يعد متاحاً")
                            val currency = db.financeDao().getCurrencyInCompany(companyId, current.currencyId)
                                ?: error("العملة المرتبطة بالمعاملة المتكررة لم تعد متاحة")
                            val number = db.voucherDao().getNextVoucherNumber(companyId)
                            db.voucherDao().upsertVoucher(
                                VoucherEntity(
                                    id = voucherId,
                                    companyId = companyId,
                                    voucherNumber = number,
                                    type = current.voucherType,
                                    dateMillis = dueAt,
                                    accountId = account.id,
                                    cashBoxId = cashBox.id,
                                    currencyId = currency.id,
                                    exchangeRate = currency.sellRate,
                                    amount = current.amount,
                                    description = current.description,
                                    createdByUserId = current.createdByUserId,
                                    createdByUserName = "معاملة متكررة",
                                    deviceId = deviceName,
                                    syncStatus = SyncStatus.PENDING_CREATE
                                )
                            )
                        }
                    }

                    val nextCount = current.executionCount + if (alreadyExecuted) 0 else 1
                    db.recurringTransactionDao().upsertRecurringTransaction(
                        current.copy(
                            lastExecutedDateMillis = if (alreadyExecuted) current.lastExecutedDateMillis else dueAt,
                            nextDueDateMillis = nextDate,
                            executionCount = nextCount,
                            isActive = current.occurrenceLimit?.let { nextCount < it } ?: true,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING_UPDATE,
                            conflictJson = null
                        )
                    )
                    !alreadyExecuted
                }
                if (!created) break
                createdCount++
                syncEngine.scheduleBackgroundSync(companyId)
                transaction = db.recurringTransactionDao()
                    .getRecurringTransaction(companyId, transaction.id) ?: break
            }
        }
        return createdCount
    }

}

object RecurrenceSchedule {
    fun nextOccurrence(currentMillis: Long, frequency: RecurrenceFrequency, dueDayOfMonth: Int): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = currentMillis }
        when (frequency) {
            RecurrenceFrequency.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            RecurrenceFrequency.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceFrequency.MONTHLY -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.MONTH, 1)
                val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, dueDayOfMonth.coerceIn(1, lastDay))
            }
            RecurrenceFrequency.YEARLY -> {
                val month = calendar.get(Calendar.MONTH)
                calendar.add(Calendar.YEAR, 1)
                calendar.set(Calendar.MONTH, month)
                calendar.set(
                    Calendar.DAY_OF_MONTH,
                    dueDayOfMonth.coerceIn(1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                )
            }
        }
        return calendar.timeInMillis
    }
}
