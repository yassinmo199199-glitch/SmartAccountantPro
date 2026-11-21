package com.smartaccountant.pro.data.model

enum class SyncStatus {
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNCED,
    CONFLICT
}

enum class UserRole(val arabicTitle: String) {
    OWNER("مدير النظام الرئيسي"),
    MANAGER("مدير فرع / مشرف"),
    ACCOUNTANT("محاسب"),
    STOREKEEPER("أمين مخزن"),
    CASHIER("كاشير / مبيعات"),
    AUDITOR("مراقب تقارير فقط"),
    CUSTOM("صلاحيات مخصصة")
}

enum class AccountCategory(val arabicTitle: String, val isDebitNature: Boolean) {
    ASSETS("الأصول", true),
    LIABILITIES("الخصوم", false),
    EQUITY("حقوق الملكية", false),
    CUSTOMERS("العملاء", true),
    SUPPLIERS("الموردون", false),
    EXPENSES("المصروفات", true),
    REVENUES("الإيرادات", false),
    BANKS_EXCHANGERS("البنوك والصرافون", true),
    EMPLOYEES("الموظفون", true),
    OTHER("حسابات عامة أخرى", true)
}

enum class VoucherType(val arabicTitle: String, val shortCode: String) {
    RECEIPT("سند قبض", "س.ق"),
    PAYMENT("سند صرف", "س.ص"),
    SIMPLE_JOURNAL("قيد بسيط", "ق.ب"),
    ADJUSTMENT("قيد تسوية", "ق.ت"),
    EXPRESS("سند سريع", "س.س"),
    OPENING_BALANCE("قيد رصيد افتتاحي مرحّل", "ر.ف"),
    REMITTANCE_IN("سند استلام حوالة", "ح.واردة"),
    REMITTANCE_OUT("سند إرسال حوالة", "ح.صادرة"),
    CURRENCY_EXCHANGE("سند صرف عملات", "ص.ع"),
    BOX_TRANSFER("تحويل بين الصناديق", "ت.ص")
}

enum class InvoiceType(val arabicTitle: String, val shortCode: String, val stockSign: Int) {
    SALE("فاتورة بيع", "ف.ب", -1),
    PURCHASE("فاتورة شراء", "ف.ش", +1),
    SALE_RETURN("مردود بيع", "م.ب", +1),
    PURCHASE_RETURN("مردود شراء", "م.ش", -1),
    QUOTATION("عرض سعر", "ع.س", 0),
    ORDER("طلبية", "ط.ب", 0),
    FREE_SALE("فاتورة حرة / خدمية", "ح.ب", 0),
    FREE_PURCHASE("فاتورة شراء حرة", "ح.ش", 0)
}

enum class PaymentMethod(val arabicTitle: String) {
    CASH("نقدي"),
    CREDIT("آجل")
}

enum class MeteringMode(val arabicTitle: String) {
    NONE("بدون تمتير (كمية عادية)"),
    LENGTH_WIDTH("طول × عرض × عدد"),
    AREA_HEIGHT("مساحة × ارتفاع × عدد")
}

enum class PaperSize(val arabicTitle: String, val widthMm: Int) {
    A4("ورق A4 قياسي", 210),
    THERMAL_80MM("طابعة حرارية 80mm", 80),
    THERMAL_58MM("طابعة حرارية 58mm", 58)
}

enum class ThemeMode(val arabicTitle: String) {
    SYSTEM("تلقائي حسب النظام"),
    LIGHT("فاتح"),
    DARK("داكن")
}

enum class AuditAction(val arabicTitle: String) {
    CREATE("إضافة سجل"),
    UPDATE("تعديل سجل"),
    DELETE("حذف سجل"),
    ARCHIVE("أرشفة / إلغاء أرشفة"),
    POST("ترحيل مستند"),
    CLOSE_PERIOD("إغلاق حسابات الفترة"),
    USER_PERMISSION_CHANGE("تعديل صلاحيات مستخدم"),
    CONFLICT_RESOLVED("حل تعارض مزامنة"),
    SYNC("مزامنة سحابية")
}
