package com.smartaccountant.pro.core.session

import android.content.Context

/**
 * يخزن جلسة الشركة الحالية على مستوى الجهاز:
 * - currentCompanyId: معرف الشركة النشطة
 * - currentUserId: معرف المستخدم المحلي النشط
 * - currentMemberId: معرف العضو في Firestore (عادةً = firebaseUid)
 * - currentUserRole: دور المستخدم داخل الشركة النشطة (OWNER/ADMIN/ACCOUNTANT/...)
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("smart_acc_session", Context.MODE_PRIVATE)

    var currentCompanyId: String?
        get() = prefs.getString(KEY_COMPANY_ID, null)
        set(value) = prefs.edit().putString(KEY_COMPANY_ID, value).apply()

    var currentUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var currentMemberId: String?
        get() = prefs.getString(KEY_MEMBER_ID, null)
        set(value) = prefs.edit().putString(KEY_MEMBER_ID, value).apply()

    var currentUserRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    /**
     * true عند وجود جلسة صالحة محفوظة (تم تسجيل الدخول ولم يضغط المستخدم خروجاً).
     * يُستخدم في init الخاص بـ MainViewModel لتخطي شاشة تسجيل الدخول.
     */
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    /** حفظ جلسة كاملة دفعة واحدة */
    fun saveSession(companyId: String?, userId: String?, memberId: String?, role: String?) {
        prefs.edit()
            .putString(KEY_COMPANY_ID, companyId)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_MEMBER_ID, memberId)
            .putString(KEY_USER_ROLE, role)
            .putBoolean(KEY_IS_LOGGED_IN, !companyId.isNullOrBlank() && !userId.isNullOrBlank())
            .apply()
    }

    /** مسح مفاتيح الجلسة فقط (بدون مسح تفضيلات أخرى) */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_COMPANY_ID)
            .remove(KEY_USER_ID)
            .remove(KEY_MEMBER_ID)
            .remove(KEY_USER_ROLE)
            .remove(KEY_IS_LOGGED_IN)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COMPANY_ID = "current_company_id"
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_MEMBER_ID = "current_member_id"
        private const val KEY_USER_ROLE = "current_user_role"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}
