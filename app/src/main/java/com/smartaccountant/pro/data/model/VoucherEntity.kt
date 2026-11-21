package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vouchers")
data class VoucherEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val voucherNumber: Long,
    val displayNumber: String = "", // رقم فريد غير متصادم بين الأجهزة مثل VCH-A7K-0001
    val type: VoucherType,
    val dateMillis: Long = System.currentTimeMillis(),
    val accountId: String,
    val counterAccountId: String? = null,
    val cashBoxId: String? = null,
    val targetCashBoxId: String? = null,
    val currencyId: String,
    val exchangeRate: Double = 1.0,
    val amount: Double,
    val targetCurrencyId: String? = null,
    val targetAmount: Double = 0.0,
    val description: String = "",
    val transferCompany: String = "",
    val remittanceNumber: String = "",
    val senderName: String = "",
    val receiverName: String = "",
    val commissionAmount: Double = 0.0,
    val attachmentUrl: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val deviceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val conflictJson: String? = null,
    val isArchived: Boolean = false, // تمت إضافته ليُستخدم عند إغلاق السنة المالية
    val isDeleted: Boolean = false
)
