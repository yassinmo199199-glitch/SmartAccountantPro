package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "invoice_items")
data class InvoiceItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val invoiceId: String,
    val productId: String,
    val productName: String,
    val unitName: String = "حبة",
    val unitFactor: Double = 1.0,
    val meteringMode: MeteringMode = MeteringMode.NONE,
    val dimLength: Double = 0.0,
    val dimWidth: Double = 0.0,
    val dimHeight: Double = 0.0,
    val pieceCount: Int = 1,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val costPriceAtTime: Double = 0.0,
    val lineTotal: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isDeleted: Boolean = false
)
