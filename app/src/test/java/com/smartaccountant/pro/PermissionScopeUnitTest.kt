package com.smartaccountant.pro

import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.model.UserEntity
import com.smartaccountant.pro.data.model.UserPermissions
import com.smartaccountant.pro.data.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionScopeUnitTest {
    private fun user(permissions: UserPermissions, active: Boolean = true) = UserEntity(
        companyId = "company-test",
        fullName = "Test User",
        email = "test@example.com",
        role = UserRole.CUSTOM,
        permissions = permissions,
        isActive = active
    )

    @Test
    fun unrestrictedLegacyUsersKeepAccess() {
        val user = user(UserPermissions())

        assertTrue(PermissionManager.canAccessCashBox(user, "box-1"))
        assertTrue(PermissionManager.canAccessWarehouse(user, "warehouse-1"))
    }

    @Test
    fun restrictedCashBoxAccessAllowsOnlySelectedBoxes() {
        val user = user(
            UserPermissions(
                restrictCashBoxAccess = true,
                allowedCashBoxIds = listOf("box-1")
            )
        )

        assertTrue(PermissionManager.canAccessCashBox(user, "box-1"))
        assertFalse(PermissionManager.canAccessCashBox(user, "box-2"))
    }

    @Test
    fun restrictedWarehouseAccessDeniesUnselectedAndEmptyScopes() {
        val selected = user(
            UserPermissions(
                restrictWarehouseAccess = true,
                allowedWarehouseIds = listOf("warehouse-1")
            )
        )
        val empty = user(UserPermissions(restrictWarehouseAccess = true))

        assertTrue(PermissionManager.canAccessWarehouse(selected, "warehouse-1"))
        assertFalse(PermissionManager.canAccessWarehouse(selected, "warehouse-2"))
        assertFalse(PermissionManager.canAccessWarehouse(empty, "warehouse-1"))
    }

    @Test
    fun inactiveUsersCannotAccessCashBoxesOrWarehouses() {
        val user = user(UserPermissions(), active = false)

        assertFalse(PermissionManager.canAccessCashBox(user, "box-1"))
        assertFalse(PermissionManager.canAccessWarehouse(user, "warehouse-1"))
    }
}
