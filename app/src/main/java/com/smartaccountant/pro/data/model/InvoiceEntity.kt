package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val invoiceNumber: Long,
    val displayNumber: String = "", // رقم فريد غير متصادم بين الأجهزة مثل INV-A7K-0001
    val type: InvoiceType,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val dateMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val dueDateMillis: Long = 0L,
    val accountId: String,
    val originalInvoiceId: String? = null,
    val warehouseId: String,
    val targetWarehouseId: String? = null,
    val cashBoxId: String? = null,
    val currencyId: String,
    val exchangeRate: Double = 1.0,
    val subTotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val isDiscountPercentage: Boolean = false,
    val taxAmount: Double = 0.0,
    val netTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val notes: String = "",
    val isArchived: Boolean = false,
    val isPosted: Boolean = false,
    val attachmentUrl: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val deviceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val conflictJson: String? = null,
    val isDeleted: Boolean = false
)
