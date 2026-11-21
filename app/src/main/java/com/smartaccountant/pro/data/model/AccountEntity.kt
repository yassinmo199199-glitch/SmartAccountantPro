package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val name: String,
    val category: AccountCategory = AccountCategory.CUSTOMERS,
    val phone: String = "",
    @ColumnInfo(defaultValue = "''")
    val email: String = "",
    val parentId: String? = null, // تمت الإضافة لدعم شجرة الحسابات (Hierarchical Chart of Accounts)
    val address: String = "",
    val taxNumber: String = "",
    val notes: String = "",
    val defaultCurrencyId: String,
    val allowMultiCurrency: Boolean = true,
    val creditLimit: Double = 0.0,
    val openingDebit: Double = 0.0,
    val openingCredit: Double = 0.0,
    val createdByUserId: String = "",
    val deviceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val conflictJson: String? = null,
    val isDeleted: Boolean = false
)
