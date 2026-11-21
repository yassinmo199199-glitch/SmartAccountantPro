package com.smartaccountant.pro.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Dashboard : Screen("dashboard", "الرئيسية")
    data object Accounts : Screen("accounts?category={category}", "الحسابات والعملاء") {
        fun createRoute(category: String? = null) = if (category.isNullOrBlank()) "accounts" else "accounts?category=$category"
    }
    data object AccountStatement : Screen("statement/{accountId}", "كشف حساب") {
        fun createRoute(accountId: String) = "statement/$accountId"
    }
    data object PartyProfile : Screen("party_profile/{accountId}", "ملف الطرف") {
        fun createRoute(accountId: String) = "party_profile/$accountId"
    }
    data object VoucherDetails : Screen("voucher_details/{voucherId}", "تفاصيل السند") {
        fun createRoute(voucherId: String) = "voucher_details/$voucherId"
    }
    data object VoucherEntry : Screen("voucher_entry?type={type}&accountId={accountId}&voucherId={voucherId}", "إدخال سند") {
        fun createRoute(type: String = "RECEIPT", accountId: String = "", voucherId: String? = null) =
            "voucher_entry?type=$type&accountId=$accountId" + (voucherId?.let { "&voucherId=$it" } ?: "")
    }
    data object Remittances : Screen("remittances", "الحوالات المالية")
    data object CashBoxesAndCurrencies : Screen("boxes_currencies", "الصناديق والعملات")
    data object CashBoxStatement : Screen("cash_box_statement/{cashBoxId}", "كشف حركة الصندوق") {
        fun createRoute(cashBoxId: String) = "cash_box_statement/$cashBoxId"
    }
    data object InvoiceEditor : Screen("invoice_editor?type={type}&accountId={accountId}", "فاتورة جديدة") {
        fun createRoute(type: String = "SALE", accountId: String = "") = "invoice_editor?type=$type&accountId=$accountId"
    }
    data object InvoicesList : Screen("invoices_list", "سجل الفواتير")
    data object InvoiceDetails : Screen("invoice_details/{invoiceId}", "تفاصيل الفاتورة") {
        fun createRoute(invoiceId: String) = "invoice_details/$invoiceId"
    }
    data object Inventory : Screen("inventory", "المخازن والأصناف")
    data object Reports : Screen("reports", "التقارير وإغلاق الفترة")
    data object AdminUsers : Screen("admin_users", "إدارة المستخدمين والصلاحيات")
    data object AuditLogs : Screen("audit_logs", "سجل الرقابة والتدقيق")
    data object Settings : Screen("settings", "الإعدادات الشاملة")
    data object RecurringTransactions : Screen("recurring_transactions", "المعاملات المتكررة")
    data object GlobalSearch : Screen("global_search", "بحث شامل")
    data object GeneralJournal : Screen("general_journal", "الدفتر العام")
    data object CostCenters : Screen("cost_centers", "مراكز التكلفة")
    data object More : Screen("more", "المزيد")
}
