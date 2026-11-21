package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val name: String,
    val symbol: String,
    val subUnitName: String = "",
    val buyRate: Double = 1.0,
    val sellRate: Double = 1.0,
    val isDefault: Boolean = false,
    val sortOrder: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isDeleted: Boolean = false
)
