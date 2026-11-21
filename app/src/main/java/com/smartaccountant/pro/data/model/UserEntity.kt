package com.smartaccountant.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

data class UserPermissions(
    val canViewAccounts: Boolean = true,
    val canAddAccount: Boolean = true,
    val canEditAccount: Boolean = false,
    val canDeleteAccount: Boolean = false,
    val canSetCreditLimit: Boolean = false,

    val canAddVoucher: Boolean = true,
    val canEditVoucher: Boolean = false,
    val canDeleteVoucher: Boolean = false,
    val canManageRemittances: Boolean = true,
    val canExchangeCurrency: Boolean = false,

    val canCreateSaleInvoice: Boolean = true,
    val canCreatePurchaseInvoice: Boolean = false,
    val canCreateReturnInvoice: Boolean = false,
    val canEditInvoice: Boolean = false,
    val canDeleteInvoice: Boolean = false,
    val canPostOrArchiveInvoice: Boolean = false,
    val canSellBelowCost: Boolean = false,
    val canApplyDiscount: Boolean = true,

    val canViewInventory: Boolean = true,
    val canAddEditProduct: Boolean = false,
    val canTransferStock: Boolean = false,
    val canViewCostAndProfit: Boolean = false,

    val canViewAllCashBoxes: Boolean = false,
    val restrictCashBoxAccess: Boolean = false,
    val allowedCashBoxIds: List<String> = emptyList(),
    val restrictWarehouseAccess: Boolean = false,
    val allowedWarehouseIds: List<String> = emptyList(),
    val canTransferBetweenBoxes: Boolean = false,
    val canViewStatements: Boolean = true,
    val canViewFinalReports: Boolean = false,
    val canViewFinancialReports: Boolean = false,
    val canViewPartyProfile: Boolean = false,
    val canSendPaymentReminder: Boolean = false,
    val canManageRecurring: Boolean = false,
    val canViewProfitability: Boolean = false,
    val canExportAndShare: Boolean = true,
    val canClosePeriod: Boolean = false,
    val canManageSettings: Boolean = false,
    val canManageUsers: Boolean = false,
    val canViewAuditLogs: Boolean = false
) {
    companion object {
        fun forRole(role: UserRole): UserPermissions = when (role) {
            UserRole.OWNER -> UserPermissions(
                canViewAccounts = true, canAddAccount = true, canEditAccount = true,
                canDeleteAccount = true, canSetCreditLimit = true, canAddVoucher = true,
                canEditVoucher = true, canDeleteVoucher = true, canManageRemittances = true,
                canExchangeCurrency = true, canCreateSaleInvoice = true, canCreatePurchaseInvoice = true,
                canCreateReturnInvoice = true, canEditInvoice = true, canDeleteInvoice = true,
                canPostOrArchiveInvoice = true, canSellBelowCost = true, canApplyDiscount = true,
                canViewInventory = true, canAddEditProduct = true, canTransferStock = true,
                canViewCostAndProfit = true, canViewAllCashBoxes = true, canTransferBetweenBoxes = true,
                canViewStatements = true, canViewFinalReports = true, canViewFinancialReports = true,
                canViewPartyProfile = true, canSendPaymentReminder = true,
                canManageRecurring = true, canViewProfitability = true,
                canExportAndShare = true,
                canClosePeriod = true, canManageSettings = true, canManageUsers = true,
                canViewAuditLogs = true
            )
            UserRole.MANAGER -> forRole(UserRole.OWNER).copy(canManageUsers = false, canClosePeriod = false)
            UserRole.ACCOUNTANT -> UserPermissions(
                canViewAccounts = true, canAddAccount = true, canEditAccount = true,
                canDeleteAccount = false, canSetCreditLimit = true, canAddVoucher = true,
                canEditVoucher = true, canDeleteVoucher = false, canManageRemittances = true,
                canExchangeCurrency = true, canCreateSaleInvoice = true, canCreatePurchaseInvoice = true,
                canCreateReturnInvoice = true, canEditInvoice = true, canDeleteInvoice = false,
                canPostOrArchiveInvoice = true, canSellBelowCost = false, canApplyDiscount = true,
                canViewInventory = true, canAddEditProduct = true, canTransferStock = true,
                canViewCostAndProfit = true, canViewAllCashBoxes = true, canTransferBetweenBoxes = true,
                canViewStatements = true, canViewFinalReports = true, canViewFinancialReports = true,
                canViewPartyProfile = true, canSendPaymentReminder = true,
                canManageRecurring = true, canViewProfitability = true,
                canExportAndShare = true,
                canClosePeriod = false, canManageSettings = false, canManageUsers = false,
                canViewAuditLogs = true
            )
            UserRole.STOREKEEPER -> UserPermissions(
                canViewAccounts = false, canAddAccount = false, canAddVoucher = false,
                canManageRemittances = false, canCreateSaleInvoice = false,
                canCreatePurchaseInvoice = true, canCreateReturnInvoice = true,
                canViewInventory = true, canAddEditProduct = true, canTransferStock = true,
                canViewCostAndProfit = false, canViewStatements = false, canExportAndShare = true
            )
            UserRole.CASHIER -> UserPermissions(
                canViewAccounts = true, canAddAccount = true, canAddVoucher = true,
                canManageRemittances = false, canCreateSaleInvoice = true,
                canCreatePurchaseInvoice = false, canCreateReturnInvoice = false,
                canApplyDiscount = true, canSellBelowCost = false, canViewInventory = true,
                canViewCostAndProfit = false, canViewAllCashBoxes = false,
                canViewStatements = true, canViewPartyProfile = true, canExportAndShare = true
            )
            UserRole.AUDITOR -> UserPermissions(
                canViewAccounts = true, canAddAccount = false, canAddVoucher = false,
                canManageRemittances = false, canCreateSaleInvoice = false,
                canViewInventory = true, canViewCostAndProfit = true,
                canViewAllCashBoxes = true, canViewStatements = true,
                canViewFinalReports = true, canViewFinancialReports = true,
                canViewPartyProfile = true,
                canViewProfitability = true,
                canExportAndShare = true, canViewAuditLogs = true
            )
            UserRole.CUSTOM -> UserPermissions()
        }
    }
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val firebaseUid: String = "",
    val fullName: String,
    val email: String,
    val phone: String = "",
    val pinHash: String = "", // SHA-256 salted hash — لا يتم تخزين PIN كنص صريح أبداً
    val role: UserRole = UserRole.CASHIER,
    val permissions: UserPermissions = UserPermissions.forRole(role),
    val isActive: Boolean = true,
    val lastDeviceName: String = "",
    val lastActiveAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val conflictJson: String? = null,
    val isDeleted: Boolean = false
)
