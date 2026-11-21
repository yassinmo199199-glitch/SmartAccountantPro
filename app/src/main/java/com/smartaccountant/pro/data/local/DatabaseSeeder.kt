package com.smartaccountant.pro.data.local

import com.smartaccountant.pro.data.model.*
import kotlinx.coroutines.flow.first

class DatabaseSeeder(private val db: AppDatabase) {
    /**
     * لا ينشئ أي شركة أو مستخدم افتراضي — الشركة تُنشأ فقط عبر تدفق
     * إنشاء شركة جديد (CompanyBootstrapService / MainViewModel.createNewCompany).
     * يعيد null عندما لا توجد أي شركة في قاعدة البيانات (حالة onboarding).
     */
    suspend fun seedIfNeeded(): Pair<CompanyEntity, UserEntity>? {
        val existingCompanies = db.companyDao().observeCompanies().first()
        if (existingCompanies.isEmpty()) return null
        val company = existingCompanies.first()
        val users = db.userSyncDao().observeUsers(company.id).first()
        val admin = users.firstOrNull { it.role == UserRole.OWNER } ?: users.firstOrNull()
            ?: return null
        return company to admin
    }
}
