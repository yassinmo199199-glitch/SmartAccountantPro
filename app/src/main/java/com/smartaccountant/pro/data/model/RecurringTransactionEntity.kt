package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RecurrenceFrequency(val arabicTitle: String) {
    DAILY("يومي"),
    WEEKLY("أسبوعي"),
    MONTHLY("شهري"),
    YEARLY("سنوي")
}

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val description: String,
    val accountId: String,
    val cashBoxId: String,
    val amount: Double,
    val currencyId: String,
    val voucherType: VoucherType,
    val frequency: RecurrenceFrequency,
    val nextDueDateMillis: Long,
    val dueDayOfMonth: Int,
    val lastExecutedDateMillis: Long = 0L,
    val occurrenceLimit: Int? = null,
    val executionCount: Int = 0,
    val isActive: Boolean = true,
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val conflictJson: String? = null,
    val isDeleted: Boolean = false
)
