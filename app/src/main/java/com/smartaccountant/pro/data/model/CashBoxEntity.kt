package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "cash_boxes")
data class CashBoxEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val name: String,
    val isBank: Boolean = false,
    val accountNumber: String = "",
    val defaultCurrencyId: String,
    val isDefault: Boolean = false,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isDeleted: Boolean = false
)
