package com.smartaccountant.pro.core.security

import com.smartaccountant.pro.data.model.UserEntity
import com.smartaccountant.pro.data.model.UserPermissions
import com.smartaccountant.pro.data.model.UserRole

class PermissionException(message: String) : SecurityException(message)

object PermissionManager {
    fun isOwner(user: UserEntity?): Boolean = user?.role == UserRole.OWNER && user.isActive

    fun permissionsOf(user: UserEntity?): UserPermissions {
        if (user == null || !user.isActive) return UserPermissions(
            canViewAccounts = false, canAddAccount = false, canAddVoucher = false,
            canManageRemittances = false, canCreateSaleInvoice = false, canApplyDiscount = false,
            canViewInventory = false, canViewStatements = false, canViewPartyProfile = false,
            canSendPaymentReminder = false, canExportAndShare = false
        )
        return if (user.role == UserRole.OWNER) UserPermissions.forRole(UserRole.OWNER) else user.permissions
    }

    fun requirePermission(user: UserEntity?, check: (UserPermissions) -> Boolean, errorMessage: String) {
        if (user == null || !user.isActive) {
            throw PermissionException("الحساب غير نشط أو لم يتم تسجيل الدخول")
        }
        if (user.role == UserRole.OWNER) return
        if (!check(user.permissions)) {
            throw PermissionException(errorMessage)
        }
    }

    fun canAccessCashBox(user: UserEntity?, cashBoxId: String): Boolean {
        if (user == null || !user.isActive) return false
        if (user.role == UserRole.OWNER || user.permissions.canViewAllCashBoxes) return true
        return !user.permissions.restrictCashBoxAccess || cashBoxId in user.permissions.allowedCashBoxIds
    }

    fun canAccessWarehouse(user: UserEntity?, warehouseId: String): Boolean {
        if (user == null || !user.isActive) return false
        if (user.role == UserRole.OWNER) return true
        return !user.permissions.restrictWarehouseAccess || warehouseId in user.permissions.allowedWarehouseIds
    }
}
