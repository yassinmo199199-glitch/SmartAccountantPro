package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * سجل الحركات المخزنية الحتمي (Deterministic Ledger)
 * يضمن إعادة احتساب رصيد المخزون بدقة 100% عبر جميع الأجهزة المتزامنة دون فقد أو تكرار
 */
@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey val id: String, // مفتاح حتمي مثل "${invoiceId}_${itemId}" أو "${productId}_opening"
    val companyId: String,
    val productId: String,
    val warehouseId: String,
    val sourceDocId: String,
    val deltaQuantity: Double, // موجب للإضافة، سالب للصرف
    val isActive: Boolean = true, // false عند أرشفة أو حذف الفاتورة
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE
)
