package com.smartaccountant.pro.domain.repository

import androidx.room.withTransaction
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.security.PinHasher
import com.smartaccountant.pro.core.sync.SyncEngine
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.*
import kotlinx.coroutines.flow.Flow

class AdminRepository(
    private val db: AppDatabase,
    private val syncEngine: SyncEngine,
    private val deviceName: String
) {
    fun observeUsers(companyId: String): Flow<List<UserEntity>> =
        db.userSyncDao().observeUsers(companyId)

    fun observeAuditLogs(companyId: String): Flow<List<AuditLogEntity>> =
        db.userSyncDao().observeAuditLogs(companyId)

    suspend fun saveUserAndPermissions(
        adminUser: UserEntity,
        targetUser: UserEntity,
        rawNewPin: String?,
        isEdit: Boolean
    ) {
        PermissionManager.requirePermission(adminUser, { it.canManageUsers }, "هذه العملية مخصصة لمدير النظام فقط")
        if (!syncEngine.isOnline) {
            throw IllegalStateException("يلزم وجود اتصال فعال بالإنترنت لإضافة أو تعديل صلاحيات الموظفين ومزامنتها سحابياً")
        }
        if (targetUser.role == UserRole.OWNER && adminUser.id != targetUser.id) {
            throw SecurityException("لا يمكن تعيين مستخدم آخر كمدير نظام رئيسي")
        }

        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val phoneRegex = Regex("^[0-9+]{8,15}$")

        val cleanEmail = targetUser.email.trim()
        val cleanPhone = targetUser.phone.trim()

        val isValidEmail = cleanEmail.isNotBlank() && emailRegex.matches(cleanEmail)
        val isValidPhone = cleanPhone.isNotBlank() && phoneRegex.matches(cleanPhone)

        if (!isValidEmail && !isValidPhone) {
            throw IllegalArgumentException("يرجى إدخال بريد إلكتروني صحيح (مثل: name@company.com) أو رقم جوال صحيح")
        }

        val existingUsers = db.userSyncDao().getAllActiveUsers()
        val duplicateUser = existingUsers.find { u ->
            u.id != targetUser.id &&
            !u.isDeleted &&
            ((isValidEmail && u.email.equals(cleanEmail, ignoreCase = true)) ||
             (isValidPhone && cleanPhone.isNotBlank() && u.phone == cleanPhone))
        }

        if (duplicateUser != null) {
            throw IllegalArgumentException("عذراً، البريد الإلكتروني أو رقم الجوال مسجل بالفعل لموظف آخر (${duplicateUser.fullName})")
        }

        val hashedPin = if (!rawNewPin.isNullOrBlank()) PinHasher.hashPin(rawNewPin) else targetUser.pinHash
        val now = System.currentTimeMillis()
        val updated = targetUser.copy(
            pinHash = hashedPin,
            updatedAt = now,
            syncStatus = if (isEdit) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_CREATE
        )

        db.withTransaction {
            db.userSyncDao().upsertUser(updated)
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = updated.companyId,
                    userId = adminUser.id,
                    userName = adminUser.fullName,
                    deviceName = deviceName,
                    action = AuditAction.USER_PERMISSION_CHANGE,
                    entityType = "مستخدم وصلاحيات",
                    entityId = updated.id,
                    summary = "${if (isEdit) "تعديل صلاحيات" else "إضافة مستخدم"}: ${updated.fullName} (${updated.role.arabicTitle})"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(updated.companyId)
    }

    suspend fun toggleUserActiveStatus(adminUser: UserEntity, targetUser: UserEntity) {
        PermissionManager.requirePermission(adminUser, { it.canManageUsers }, "هذه العملية مخصصة لمدير النظام فقط")
        if (targetUser.role == UserRole.OWNER) {
            throw IllegalStateException("لا يمكن تعطيل حساب مدير النظام الرئيسي")
        }
        val updated = targetUser.copy(
            isActive = !targetUser.isActive,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        db.userSyncDao().upsertUser(updated)
        db.userSyncDao().insertAuditLog(
            AuditLogEntity(
                companyId = updated.companyId,
                userId = adminUser.id,
                userName = adminUser.fullName,
                deviceName = deviceName,
                action = AuditAction.UPDATE,
                entityType = "مستخدم وصلاحيات",
                entityId = updated.id,
                summary = "${if (updated.isActive) "تفعيل" else "تعطيل"} المستخدم: ${updated.fullName}"
            )
        )
        syncEngine.scheduleBackgroundSync(updated.companyId)
    }
}
