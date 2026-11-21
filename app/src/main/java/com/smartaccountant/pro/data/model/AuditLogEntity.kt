package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val userId: String,
    val userName: String,
    val deviceName: String,
    val action: AuditAction,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE
)
