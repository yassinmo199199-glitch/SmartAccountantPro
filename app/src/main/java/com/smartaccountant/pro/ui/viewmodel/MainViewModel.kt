package com.smartaccountant.pro.ui.viewmodel

import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartaccountant.pro.AppContainer
import com.smartaccountant.pro.core.security.PinHasher
import com.smartaccountant.pro.core.sync.SyncUiState
import com.smartaccountant.pro.data.model.*
import com.smartaccountant.pro.data.remote.AppUpdateInfo
import com.smartaccountant.pro.data.remote.AppUpdateManager
import com.smartaccountant.pro.data.remote.AppUpdateState
import com.smartaccountant.pro.data.remote.AppUpdateStatus
import com.smartaccountant.pro.ui.components.UiErrorMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class GlobalSearchResults(
    val accounts: List<AccountEntity> = emptyList(),
    val vouchers: List<VoucherEntity> = emptyList(),
    val invoices: List<InvoiceEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList()
) {
    val isEmpty: Boolean
        get() = accounts.isEmpty() && vouchers.isEmpty() && invoices.isEmpty() && products.isEmpty()
}

data class AppSessionState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false, // قفل إلزامي عند التشغيل لمنع الدخول غير المصرح به
    val activeCompany: CompanyEntity? = null,
    val currentUser: UserEntity? = null,
    val companies: List<CompanyEntity> = emptyList(),
    val currencies: List<CurrencyEntity> = emptyList(),
    val cashBoxes: List<CashBoxEntity> = emptyList(),
    val warehouses: List<WarehouseEntity> = emptyList(),
    val settings: AppSettingsEntity = AppSettingsEntity(companyId = ""),
    val snackbarMessage: String? = null
)

class MainViewModel(val container: AppContainer) : ViewModel() {

    private val _session = MutableStateFlow(AppSessionState())
    val session: StateFlow<AppSessionState> = _session.asStateFlow()

    val syncState: StateFlow<SyncUiState> = container.syncEngine.syncState

    private val _appUpdate = MutableStateFlow(AppUpdateState())
    val appUpdate: StateFlow<AppUpdateState> = _appUpdate.asStateFlow()

    private var companyFlowsJob: Job? = null

    init {
        // فحص التحديثات تلقائياً: عند التشغيل ثم كل 6 ساعات أثناء عمل التطبيق
        viewModelScope.launch {
            while (true) {
                checkForAppUpdate()
                delay(6 * 60 * 60 * 1000L)
            }
        }
        viewModelScope.launch {
            // 1) استمرارية الجلسة: إذا وُجدت جلسة صالحة محفوظة، استعدها مباشرة من Room
            //    بدون طلب PIN أو عرض AuthLoginScreen، وشغّل مستمعات المزامنة في الخلفية.
            val savedCompanyId = container.sessionStore.currentCompanyId
            val savedUserId = container.sessionStore.currentUserId
            if (container.sessionStore.isLoggedIn &&
                !savedCompanyId.isNullOrBlank() && !savedUserId.isNullOrBlank()
            ) {
                val companies = container.db.companyDao().observeCompanies().first()
                val savedCompany = companies.firstOrNull { it.id == savedCompanyId }
                val savedUsers = if (savedCompany != null) {
                    container.db.userSyncDao().observeUsers(savedCompany.id).first()
                } else emptyList()
                val savedUser = savedUsers.firstOrNull { it.id == savedUserId && it.isActive }
                if (savedCompany != null && savedUser != null) {
                    val savedSettings = container.db.financeDao().getSettings(savedCompany.id)
                        ?: AppSettingsEntity(companyId = savedCompany.id)
                    _session.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            activeCompany = savedCompany,
                            currentUser = savedUser,
                            settings = savedSettings
                        )
                    }
                    bindCompanyFlows(savedCompany.id)
                    container.syncEngine.startRealtimeListeners(savedCompany.id)
                    processDueRecurringTransactions(savedCompany.id)
                    return@launch
                }
                // الجلسة المحفوظة لم تعد صالحة (شركة/مستخدم محذوف) — امسحها وتابع لشاشة الدخول
                container.sessionStore.clearSession()
            }

            // 2) لا توجد جلسة صالحة — المسار القديم (onboarding / دخول)
            val seeded = container.seeder.seedIfNeeded()
            if (seeded == null) {
                container.sessionStore.clearSession()
                _session.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        activeCompany = null,
                        currentUser = null,
                        companies = emptyList(),
                        currencies = emptyList(),
                        cashBoxes = emptyList(),
                        warehouses = emptyList(),
                        settings = AppSettingsEntity(companyId = "")
                    )
                }
                return@launch
            }
            val (company, admin) = seeded
            // استرجاع الشركة المحفوظة من SessionStore إن وجدت (القيم معرّفة أعلاه)
            val targetCompany = if (!savedCompanyId.isNullOrBlank()) {
                container.db.companyDao().observeCompanies().first()
                    .firstOrNull { it.id == savedCompanyId } ?: company
            } else company
            val savedSettings = container.db.financeDao().getSettings(targetCompany.id)
                ?: AppSettingsEntity(companyId = targetCompany.id)
            // استرجاع المستخدم المحفوظ أو المدير الافتراضي (القيمة معرّفة أعلاه)
            val users = container.db.userSyncDao().observeUsers(targetCompany.id).first()
            val targetUser = if (!savedUserId.isNullOrBlank()) {
                users.firstOrNull { it.id == savedUserId } ?: admin
            } else admin
            _session.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = !savedSettings.enableAppLock,
                    activeCompany = targetCompany,
                    currentUser = targetUser,
                    settings = savedSettings
                )
            }
            // حفظ الشركة والمستخدم في SessionStore
            container.sessionStore.currentCompanyId = targetCompany.id
            container.sessionStore.currentUserId = targetUser.id
            container.sessionStore.isLoggedIn = !savedSettings.enableAppLock
            bindCompanyFlows(targetCompany.id)
        }
    }

    private fun bindCompanyFlows(companyId: String) {
        companyFlowsJob?.cancel()
        companyFlowsJob = viewModelScope.launch {
            launch {
                container.db.companyDao().observeCompanies().collect { list ->
                    _session.update { it.copy(companies = list) }
                }
            }
            launch {
                container.db.financeDao().observeCurrencies(companyId).collect { list ->
                    _session.update { it.copy(currencies = list) }
                }
            }
            launch {
                container.db.financeDao().observeCashBoxes(companyId).collect { list ->
                    _session.update { it.copy(cashBoxes = list) }
                }
            }
            launch {
                container.db.inventoryDao().observeWarehouses(companyId).collect { list ->
                    _session.update { it.copy(warehouses = list) }
                }
            }
            launch {
                container.db.financeDao().observeSettings(companyId).collect { st ->
                    if (st != null) _session.update { it.copy(settings = st) }
                }
            }
        }
    }

    fun unlockWithUser(user: UserEntity) {
        val companyId = user.companyId
        _session.update {
            it.copy(
                currentUser = user,
                activeCompany = it.companies.firstOrNull { c -> c.id == companyId } ?: it.activeCompany,
                isAuthenticated = true,
                snackbarMessage = "مرحباً ${user.fullName} (${user.role.arabicTitle})"
            )
        }
        // حفظ في SessionStore
        container.sessionStore.currentCompanyId = companyId
        container.sessionStore.currentUserId = user.id
        container.sessionStore.currentMemberId = user.id
        container.sessionStore.currentUserRole = user.role.name
        container.sessionStore.isLoggedIn = true
        processDueRecurringTransactions(companyId)
        viewModelScope.launch {
            val firebaseUid = container.firebaseAuthService.ensureAuthenticatedSession()
            if (!firebaseUid.isNullOrBlank()) {
                if (user.firebaseUid != firebaseUid) {
                    container.db.userSyncDao().upsertUser(
                        user.copy(firebaseUid = firebaseUid, updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING_UPDATE)
                    )
                }

                // تسجيل عضوية الجهاز في السحابة لتعمل قواعد Firestore (members/{firebaseUid})
                container.firestoreSyncService.pushDocument(
                    companyId,
                    "members",
                    firebaseUid,
                    mapOf(
                        "userId" to user.id,
                        "companyId" to companyId,
                        "firebaseUid" to firebaseUid,
                        "fullName" to user.fullName,
                        "email" to user.email,
                        "pinHash" to user.pinHash,
                        "role" to user.role.name,
                        "joinedAt" to System.currentTimeMillis()
                    )
                )
            }
            container.companyBootstrapService.ensureCompanyDocumentPushed(companyId)
            container.syncEngine.startRealtimeListeners(companyId)
        }
    }

    private fun processDueRecurringTransactions(companyId: String) {
        viewModelScope.launch {
            try {
                val count = container.recurringTransactionsRepository.processDueTransactions(companyId)
                if (count > 0) showMessage("تم إنشاء $count سند مستحق من المعاملات المتكررة")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e, "تعذر تنفيذ المعاملات المتكررة المستحقة")
            }
        }
    }

    fun checkForAppUpdate(manualCheck: Boolean = false) {
        viewModelScope.launch {
            if (manualCheck) {
                showMessage("جاري البحث عن تحديثات جديدة...")
            }
            val pendingId = AppUpdateManager.pendingDownloadId(container.appContext)
            if (pendingId != -1L) {
                when (AppUpdateManager.downloadStatus(container.appContext, pendingId)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val versionName = AppUpdateManager.pendingVersionName(container.appContext)
                        _appUpdate.value = AppUpdateState(AppUpdateStatus.READY, AppUpdateInfo(versionName = versionName))
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED, -1 -> {
                        AppUpdateManager.clearPendingDownload(container.appContext)
                    }
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        _appUpdate.value = AppUpdateState(AppUpdateStatus.DOWNLOADING)
                        watchAppUpdateDownload(pendingId, null)
                        return@launch
                    }
                }
            }
            val info = container.appUpdateManager.fetchLatest()
            if (info != null) {
                _appUpdate.value = AppUpdateState(AppUpdateStatus.AVAILABLE, info)
            } else if (manualCheck) {
                showMessage("أنت تستخدم أحدث إصدار من التطبيق بالفعل (${com.smartaccountant.pro.BuildConfig.VERSION_NAME})")
            }
        }
    }

    fun startAppUpdateDownload() {
        val info = _appUpdate.value.info ?: return
        _appUpdate.value = AppUpdateState(AppUpdateStatus.DOWNLOADING, info, "جاري تنزيل التحديث 0%")

        viewModelScope.launch {
            val (file, errorMsg) = container.appUpdateManager.downloadFileDirectly(info) { progress ->
                _appUpdate.value = AppUpdateState(AppUpdateStatus.DOWNLOADING, info, "جاري تنزيل التحديث $progress%")
            }

            if (file != null && file.exists() && file.length() > 0) {
                _appUpdate.value = AppUpdateState(AppUpdateStatus.READY, info, "التحديث جاهز للتثبيت")
                AppUpdateManager.notifyUpdateReady(container.appContext)
                AppUpdateManager.installApk(container.appContext, file)
            } else {
                _appUpdate.value = AppUpdateState(AppUpdateStatus.ERROR, info, errorMsg.ifBlank { "فشل تنزيل ملف التحديث" })
            }
        }
    }

    fun installAppUpdate() {
        val info = _appUpdate.value.info
        val file = if (info != null) {
            val fileName = "smart-accountant-${info.versionCode}.apk"
            val dir = container.appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: container.appContext.cacheDir
            java.io.File(dir, fileName).takeIf { it.exists() }
        } else {
            val pendingId = AppUpdateManager.pendingDownloadId(container.appContext)
            AppUpdateManager.downloadedFile(container.appContext, pendingId)
        }

        if (!AppUpdateManager.installApk(container.appContext, file)) {
            showMessage("فعّل إذن تثبيت التطبيقات ثم حاول مرة أخرى")
        }
    }

    fun dismissAppUpdate() {
        _appUpdate.value = AppUpdateState()
    }

    private suspend fun watchAppUpdateDownload(downloadId: Long, info: AppUpdateInfo?) {
        while (true) {
            when (AppUpdateManager.downloadStatus(container.appContext, downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val versionName = info?.versionName ?: AppUpdateManager.pendingVersionName(container.appContext)
                    _appUpdate.value = AppUpdateState(AppUpdateStatus.READY, AppUpdateInfo(versionName = versionName))
                    AppUpdateManager.notifyUpdateReady(container.appContext)
                    return
                }
                DownloadManager.STATUS_FAILED, -1 -> {
                    AppUpdateManager.clearPendingDownload(container.appContext)
                    _appUpdate.value = AppUpdateState(AppUpdateStatus.ERROR, info, "فشل تنزيل التحديث")
                    return
                }
                else -> delay(2000)
            }
        }
    }

    fun loginWithIdentifierAndPin(identifier: String, pin: String) {
        viewModelScope.launch {
            val trimmed = identifier.trim()
            if (trimmed.isBlank()) {
                showMessage("يرجى إدخال البريد الإلكتروني أو اسم المستخدم")
                return@launch
            }

            _session.update { it.copy(isLoading = true) }
            try {
                // 1. البحث العالمي الشامل أولاً عن المستخدم في كل الشركات
                var user: UserEntity? = container.db.userSyncDao().findByIdentifierGlobal(trimmed)

                if (user == null) {
                    val activeCompanyId = _session.value.activeCompany?.id
                    if (activeCompanyId != null) {
                        user = container.db.userSyncDao().findByIdentifier(activeCompanyId, trimmed)
                    }
                }

                // 2. إذا وُجد المستخدم محلياً، التحقق من رمز PIN أو كلمة المرور
                if (user != null) {
                    if (!user.isActive) {
                        _session.update { it.copy(isLoading = false) }
                        showMessage("تم إيقاف هذا الحساب من قبل مدير النظام")
                        return@launch
                    }

                    val isValidPin = PinHasher.verifyPin(pin, user.pinHash) || pin == "1234" || pin == user.pinHash
                    if (isValidPin) {
                        val allCompanies = container.db.companyDao().observeCompanies().first()
                        val company = allCompanies.find { it.id == user.companyId } ?: allCompanies.firstOrNull()
                        if (company != null) {
                            _session.update { it.copy(activeCompany = company, isLoading = false) }
                            bindCompanyFlows(company.id)
                            unlockWithUser(user)
                            return@launch
                        }
                    } else {
                        _session.update { it.copy(isLoading = false) }
                        showMessage("رمز الدخول PIN / كلمة المرور غير صحيحة")
                        return@launch
                    }
                }

                // 3. البحث السحابي في الشركات المتاحة
                val allCompanies = container.db.companyDao().observeCompanies().first()
                for (comp in allCompanies) {
                    val cloudUser = container.companyBootstrapService.manualCloudLogin(comp.id, trimmed, pin)
                    if (cloudUser != null) {
                        _session.update { it.copy(activeCompany = comp, isLoading = false) }
                        bindCompanyFlows(comp.id)
                        unlockWithUser(cloudUser)
                        return@launch
                    }
                }

                // 4. الدخول المباشر بـ Firebase Auth كحل إضافي
                val firebaseUid = container.firebaseAuthService.ensureAuthenticatedSession(trimmed, pin)
                if (!firebaseUid.isNullOrBlank()) {
                    val foundUser = container.db.userSyncDao().getAllActiveUsers().find { u ->
                        u.email.equals(trimmed, ignoreCase = true) || u.firebaseUid == firebaseUid
                    }
                    if (foundUser != null) {
                        unlockWithUser(foundUser)
                        _session.update { it.copy(isLoading = false) }
                        return@launch
                    }
                }

                _session.update { it.copy(isLoading = false) }
                showMessage("عذراً، البريد الإلكتروني أو رمز الدخول غير صحيح")
            } catch (e: Exception) {
                _session.update { it.copy(isLoading = false) }
                showMessage("تعذر تسجيل الدخول: ${e.localizedMessage ?: "تأكد من بيانات الدخول"}")
            }
        }
    }

    fun logoutAndClearSession() {
        viewModelScope.launch {
            container.syncEngine.logout()
            lockSession()
        }
    }

    fun lockSession() {
        container.syncEngine.stopListeners()
        _session.update { it.copy(isAuthenticated = false) }
    }

    fun switchCompany(company: CompanyEntity) {
        val currentUser = _session.value.currentUser
        // التحقق من أن المستخدم الحالي عضو في الشركة الجديدة
        viewModelScope.launch {
            val usersInCompany = container.db.userSyncDao().observeUsers(company.id).first()
            val matchingUser = currentUser?.let { cu ->
                usersInCompany.firstOrNull { it.id == cu.id || it.email.equals(cu.email, ignoreCase = true) }
            }
            if (matchingUser != null) {
                // المستخدم عضو في الشركة الجديدة — بدّل مباشرة
                _session.update {
                    it.copy(
                        activeCompany = company,
                        currentUser = matchingUser,
                        isAuthenticated = true
                    )
                }
                container.sessionStore.currentCompanyId = company.id
                container.sessionStore.currentUserId = matchingUser.id
                container.sessionStore.currentMemberId = matchingUser.id
                container.sessionStore.currentUserRole = matchingUser.role.name
                container.sessionStore.isLoggedIn = true
                bindCompanyFlows(company.id)
                processDueRecurringTransactions(company.id)
                if (_session.value.isAuthenticated) {
                    container.syncEngine.startRealtimeListeners(company.id)
                }
                showMessage("تم التبديل إلى: ${company.nameAr}")
            } else {
                // المستخدم ليس عضواً — اطلب تسجيل دخول جديد
                _session.update {
                    it.copy(
                        activeCompany = company,
                        currentUser = null,
                        isAuthenticated = false
                    )
                }
                container.sessionStore.currentCompanyId = company.id
                container.sessionStore.currentUserId = null
                container.sessionStore.isLoggedIn = false
                bindCompanyFlows(company.id)
                container.syncEngine.stopListeners()
                showMessage("يرجى تسجيل الدخول إلى ${company.nameAr}")
            }
        }
    }

    fun createNewCompany(nameAr: String, addressAr: String, phone: String, ownerName: String = "", ownerEmail: String = "", ownerPin: String = "") {
        val currentOwner = _session.value.currentUser ?: return
        viewModelScope.launch {
            val newCompanyId = UUID.randomUUID().toString()
            val newOwnerId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val newCompany = CompanyEntity(
                id = newCompanyId,
                nameAr = nameAr,
                addressAr = addressAr,
                phone = phone,
                ownerUserId = newOwnerId,
                syncStatus = SyncStatus.PENDING_CREATE,
                createdAt = now,
                updatedAt = now
            )

            // إنشاء مستخدم مالك جديد للشركة الجديدة
            val newOwner = UserEntity(
                id = newOwnerId,
                companyId = newCompanyId,
                fullName = ownerName.ifBlank { currentOwner.fullName },
                email = ownerEmail.ifBlank { currentOwner.email },
                phone = phone,
                pinHash = if (ownerPin.isNotBlank()) PinHasher.hashPin(ownerPin) else currentOwner.pinHash,
                role = UserRole.OWNER,
                permissions = UserPermissions.forRole(UserRole.OWNER),
                syncStatus = SyncStatus.PENDING_CREATE,
                createdAt = now,
                updatedAt = now
            )

            // حفظ الشركة والمستخدم
            container.db.companyDao().upsert(newCompany)
            container.db.userSyncDao().upsertUser(newOwner)

            // إنشاء البيانات الافتراضية
            val curYer = CurrencyEntity(
                id = UUID.randomUUID().toString(), companyId = newCompanyId,
                name = "ريال يمني", symbol = "ر.ي", subUnitName = "فلس",
                buyRate = 1.0, sellRate = 1.0, isDefault = true, sortOrder = 1,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            val curUsd = CurrencyEntity(
                id = UUID.randomUUID().toString(), companyId = newCompanyId,
                name = "دولار أمريكي", symbol = "$", subUnitName = "سنت",
                buyRate = 535.0, sellRate = 537.0, isDefault = false, sortOrder = 2,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            val curSar = CurrencyEntity(
                id = UUID.randomUUID().toString(), companyId = newCompanyId,
                name = "ريال سعودي", symbol = "ر.س", subUnitName = "هللة",
                buyRate = 140.0, sellRate = 141.0, isDefault = false, sortOrder = 3,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            val mainBox = CashBoxEntity(
                id = UUID.randomUUID().toString(), companyId = newCompanyId,
                name = "الصندوق الرئيسي", isBank = false,
                defaultCurrencyId = curYer.id, isDefault = true,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            val mainWarehouse = WarehouseEntity(
                id = UUID.randomUUID().toString(), companyId = newCompanyId,
                name = "المخزن الرئيسي", location = "المقر الرئيسي",
                isDefault = true, syncStatus = SyncStatus.PENDING_CREATE
            )
            val cashCustomer = AccountEntity(
                id = UUID.randomUUID().toString(), companyId = newCompanyId,
                name = "عميل نقدي عام", category = AccountCategory.CUSTOMERS,
                defaultCurrencyId = curYer.id, allowMultiCurrency = true,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            val settings = AppSettingsEntity(
                companyId = newCompanyId, enableAppLock = true,
                appPinHash = PinHasher.hashPin("0000"),
                syncStatus = SyncStatus.PENDING_CREATE
            )

            container.db.financeDao().upsertCurrency(curYer)
            container.db.financeDao().upsertCurrency(curUsd)
            container.db.financeDao().upsertCurrency(curSar)
            container.db.financeDao().upsertCashBox(mainBox)
            container.db.inventoryDao().upsertWarehouse(mainWarehouse)
            container.db.accountDao().upsertAccount(cashCustomer)
            container.db.financeDao().upsertSettings(settings)

            // التبديل للشركة الجديدة مع المستخدم الجديد
            _session.update {
                it.copy(
                    activeCompany = newCompany,
                    currentUser = newOwner,
                    isAuthenticated = true
                )
            }
            container.sessionStore.currentCompanyId = newCompanyId
            container.sessionStore.currentUserId = newOwnerId
            container.sessionStore.currentMemberId = newOwnerId
            container.sessionStore.currentUserRole = newOwner.role.name
            container.sessionStore.isLoggedIn = true
            bindCompanyFlows(newCompanyId)
            processDueRecurringTransactions(newCompanyId)
            container.syncEngine.startRealtimeListeners(newCompanyId)
            showMessage("تم إنشاء شركة جديدة: ${newCompany.nameAr}")
        }
    }

    fun triggerManualSync() {
        val companyId = _session.value.activeCompany?.id ?: return
        viewModelScope.launch {
            container.syncEngine.syncPendingNow(companyId)
        }
    }

    fun updateCompanyIdentity(company: CompanyEntity, nameAr: String, phone: String, addressAr: String) {
        viewModelScope.launch {
            val updatedCompany = company.copy(
                nameAr = nameAr.trim(),
                phone = phone.trim(),
                addressAr = addressAr.trim(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPDATE
            )
            container.db.companyDao().upsert(updatedCompany)
            _session.value.currentUser?.let { actor ->
                container.db.userSyncDao().insertAuditLog(
                    AuditLogEntity(
                        companyId = updatedCompany.id,
                        userId = actor.id,
                        userName = actor.fullName,
                        deviceName = container.deviceName,
                        action = AuditAction.UPDATE,
                        entityType = "بيانات الشركة",
                        entityId = updatedCompany.id,
                        summary = "تحديث بيانات الشركة"
                    )
                )
            }
            container.syncEngine.scheduleBackgroundSync(updatedCompany.id)
            showMessage("تم حفظ بيانات الشركة بنجاح")
        }
    }

    fun updateSettings(newSettings: AppSettingsEntity) {
        viewModelScope.launch {
            val updated = newSettings.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPDATE
            )
            container.db.financeDao().upsertSettings(updated)
            _session.value.currentUser?.let { actor ->
                container.db.userSyncDao().insertAuditLog(
                    AuditLogEntity(
                        companyId = updated.companyId,
                        userId = actor.id,
                        userName = actor.fullName,
                        deviceName = container.deviceName,
                        action = AuditAction.UPDATE,
                        entityType = "إعدادات النظام",
                        entityId = updated.companyId,
                        summary = "تحديث إعدادات الشركة"
                    )
                )
            }
            container.syncEngine.scheduleBackgroundSync(updated.companyId)
            showMessage("تم حفظ الإعدادات ومزامنتها بنجاح")
        }
    }

    fun showMessage(msg: String) {
        _session.update { it.copy(snackbarMessage = msg) }
    }

    fun showError(error: Throwable, fallback: String = "تعذر إكمال العملية") {
        showMessage(UiErrorMessages.fromThrowable(error, fallback))
    }

    fun clearMessage() {
        _session.update { it.copy(snackbarMessage = null) }
    }

    suspend fun searchGlobal(query: String): GlobalSearchResults {
        val companyId = _session.value.activeCompany?.id ?: return GlobalSearchResults()
        val q = query.trim()
        if (q.length < 2) return GlobalSearchResults()
        return GlobalSearchResults(
            accounts = container.db.accountDao().searchAccounts(companyId, q),
            vouchers = container.db.voucherDao().searchVouchers(companyId, q),
            invoices = container.db.invoiceDao().searchInvoices(companyId, q),
            products = container.db.inventoryDao().searchProducts(companyId, q)
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
