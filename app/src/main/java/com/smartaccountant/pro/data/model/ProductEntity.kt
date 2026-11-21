package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val warehouseId: String,
    val name: String,
    val categoryName: String = "عام",
    val barcode: String = "",
    val unit1Name: String = "حبة",
    val purchasePrice1: Double = 0.0,
    val salePrice1: Double = 0.0,
    val wholesalePrice1: Double = 0.0,
    val unit2Name: String = "",
    val unit2Factor: Double = 1.0,
    val salePrice2: Double = 0.0,
    val unit3Name: String = "",
    val unit3Factor: Double = 1.0,
    val salePrice3: Double = 0.0,
    val stockQuantity: Double = 0.0,
    val minStockAlert: Double = 5.0,
    val expiryDateMillis: Long? = null,
    val notes: String = "",
    val createdByUserId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val conflictJson: String? = null,
    val isDeleted: Boolean = false
)
