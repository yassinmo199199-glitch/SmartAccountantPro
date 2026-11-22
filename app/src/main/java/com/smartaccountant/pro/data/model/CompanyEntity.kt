package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nameAr: String,
    val nameEn: String = "",
    val addressAr: String = "",
    val addressEn: String = "",
    val phone: String = "",
    val taxNumber: String = "",
    val logoUrl: String = "",
    val stampUrl: String = "",
    val signatureUrl: String = "",
    val ownerUserId: String = "",
    val syncEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isDeleted: Boolean = false
)

data class CompanySelectionInfo(
    val companyId: String,
    val companyName: String,
    val roleTitle: String = "عضو",
    val ownerName: String = "",
    val isOwner: Boolean = false
)
