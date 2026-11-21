package com.smartaccountant.pro.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartaccountant.pro.data.model.*

class Converters {
    private val gson = Gson()

    @TypeConverter fun fromSyncStatus(v: SyncStatus): String = v.name
    @TypeConverter fun toSyncStatus(v: String): SyncStatus = SyncStatus.valueOf(v)

    @TypeConverter fun fromUserRole(v: UserRole): String = v.name
    @TypeConverter fun toUserRole(v: String): UserRole = UserRole.valueOf(v)

    @TypeConverter fun fromAccountCategory(v: AccountCategory): String = v.name
    @TypeConverter fun toAccountCategory(v: String): AccountCategory = AccountCategory.valueOf(v)

    @TypeConverter fun fromVoucherType(v: VoucherType): String = v.name
    @TypeConverter fun toVoucherType(v: String): VoucherType = VoucherType.valueOf(v)

    @TypeConverter fun fromInvoiceType(v: InvoiceType): String = v.name
    @TypeConverter fun toInvoiceType(v: String): InvoiceType = InvoiceType.valueOf(v)

    @TypeConverter fun fromPaymentMethod(v: PaymentMethod): String = v.name
    @TypeConverter fun toPaymentMethod(v: String): PaymentMethod = PaymentMethod.valueOf(v)

    @TypeConverter fun fromMeteringMode(v: MeteringMode): String = v.name
    @TypeConverter fun toMeteringMode(v: String): MeteringMode = MeteringMode.valueOf(v)

    @TypeConverter fun fromPaperSize(v: PaperSize): String = v.name
    @TypeConverter fun toPaperSize(v: String): PaperSize = PaperSize.valueOf(v)

    @TypeConverter fun fromRecurrenceFrequency(v: RecurrenceFrequency): String = v.name
    @TypeConverter fun toRecurrenceFrequency(v: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(v)

    @TypeConverter fun fromThemeMode(v: ThemeMode): String = v.name
    @TypeConverter fun toThemeMode(v: String): ThemeMode =
        runCatching { ThemeMode.valueOf(v) }.getOrDefault(ThemeMode.SYSTEM)

    @TypeConverter fun fromAuditAction(v: AuditAction): String = v.name
    @TypeConverter fun toAuditAction(v: String): AuditAction = AuditAction.valueOf(v)

    @TypeConverter fun fromUserPermissions(perms: UserPermissions): String = gson.toJson(perms)
    @TypeConverter fun toUserPermissions(json: String): UserPermissions {
        val permissions = gson.fromJson(json, UserPermissions::class.java) ?: UserPermissions()
        val fields = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        return permissions.copy(
            canViewFinancialReports = if (fields != null && !fields.has("canViewFinancialReports")) {
                permissions.canViewFinalReports
            } else permissions.canViewFinancialReports,
            canViewPartyProfile = if (fields != null && !fields.has("canViewPartyProfile")) {
                permissions.canViewStatements
            } else permissions.canViewPartyProfile,
            canViewProfitability = if (fields != null && !fields.has("canViewProfitability")) {
                permissions.canViewCostAndProfit
            } else permissions.canViewProfitability,
            canManageRecurring = if (fields != null && !fields.has("canManageRecurring")) {
                permissions.canManageSettings
            } else permissions.canManageRecurring
        )
    }

    @TypeConverter fun fromStringList(list: List<String>): String = gson.toJson(list)
    @TypeConverter fun toStringList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
