package com.smartaccountant.pro.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartaccountant.pro.core.security.PinHasher
import com.smartaccountant.pro.core.session.SessionStore
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.*
import java.security.SecureRandom
import kotlinx.coroutines.flow.first

/**
 * يدير دورة حياة الشركة على مستوى السحابة:
 * - إنشاء شركة جديدة مع مديرها
 * - الانضمام عبر كود دعوة (QR/نص)
 * - الدخول اليدوي بكود الشركة + البريد + PIN
 * - سحب كامل بيانات شركة إلى Room
 */
class CompanyBootstrapService(
    private val db: AppDatabase,
    private val firestore: FirestoreSyncService
) {
    private val gson = Gson()
    private val secureRandom = SecureRandom()

    companion object {
        private const val TAG = "CompanyBootstrap"
    }

    private fun firebaseContextOrNull(): Context? = try {
        FirebaseApp.getInstance().applicationContext
    } catch (_: Exception) {
        null
    }

    private fun currentCompanyIdOrNull(): String? {
        val context = firebaseContextOrNull() ?: return null
        return SessionStore(context).currentCompanyId?.takeIf { it.isNotBlank() }
    }

    private suspend fun ensureAnonymousAuthOrNull(): String? {
        val context = firebaseContextOrNull() ?: return null
        return FirebaseAuthService(context).ensureAnonymousAuth()
    }

    data class InvitePayload(
        val companyId: String = "",
        val email: String = "",
        val memberId: String = "",
        val userId: String = "",
        val fullName: String = "",
        val role: String = "",
        val companyName: String = ""
    )

    /** توليد كود شركة قصير فريد (6 خانات) */
    fun generateCompanyCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    fun buildInviteJson(
        companyId: String,
        email: String,
        memberId: String,
        userId: String = memberId,
        fullName: String = "",
        role: String = "",
        companyName: String = ""
    ): String = gson.toJson(InvitePayload(companyId, email, memberId, userId, fullName, role, companyName))

    fun parseInviteJson(raw: String): InvitePayload? = try {
        gson.fromJson(raw, InvitePayload::class.java)
    } catch (_: Exception) {
        null
    }

    /**
     * إنشاء شركة جديدة بالكامل: محلياً + سحابياً.
     * يعيد (company, owner) عند النجاح.
     */
    suspend fun createCompany(
        nameAr: String,
        ownerName: String,
        ownerEmail: String,
        ownerPin: String,
        phone: String = ""
    ): Pair<CompanyEntity, UserEntity>? {
        if (!firestore.isFirebaseConfigured()) return null
        val firebaseUid = ensureAnonymousAuthOrNull() ?: return null
        val companyId = generateCompanyCode()
        val ownerId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val company = CompanyEntity(
            id = companyId,
            nameAr = nameAr.trim(),
            phone = phone.trim(),
            ownerUserId = ownerId,
            syncStatus = SyncStatus.SYNCED,
            createdAt = now,
            updatedAt = now
        )
        val owner = UserEntity(
            id = ownerId,
            companyId = companyId,
            firebaseUid = firebaseUid,
            fullName = ownerName.trim(),
            email = ownerEmail.trim(),
            pinHash = PinHasher.hashPin(ownerPin),
            role = UserRole.OWNER,
            permissions = UserPermissions.forRole(UserRole.OWNER),
            syncStatus = SyncStatus.SYNCED,
            createdAt = now,
            updatedAt = now
        )

        // البيانات الافتراضية للشركة الجديدة
        val curYer = CurrencyEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "ريال يمني", symbol = "ر.ي", subUnitName = "فلس",
            buyRate = 1.0, sellRate = 1.0, isDefault = true, sortOrder = 1,
            syncStatus = SyncStatus.SYNCED
        )
        val curUsd = CurrencyEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "دولار أمريكي", symbol = "$", subUnitName = "سنت",
            buyRate = 535.0, sellRate = 537.0, isDefault = false, sortOrder = 2,
            syncStatus = SyncStatus.SYNCED
        )
        val curSar = CurrencyEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "ريال سعودي", symbol = "ر.س", subUnitName = "هللة",
            buyRate = 140.0, sellRate = 141.0, isDefault = false, sortOrder = 3,
            syncStatus = SyncStatus.SYNCED
        )
        val mainBox = CashBoxEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "الصندوق الرئيسي", isBank = false,
            defaultCurrencyId = curYer.id, isDefault = true, syncStatus = SyncStatus.SYNCED
        )
        val mainWarehouse = WarehouseEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "المخزن الرئيسي", location = "المقر الرئيسي",
            isDefault = true, syncStatus = SyncStatus.SYNCED
        )
        val cashCustomer = AccountEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "عميل نقدي عام", category = AccountCategory.CUSTOMERS,
            defaultCurrencyId = curYer.id, allowMultiCurrency = true,
            syncStatus = SyncStatus.SYNCED
        )
        val rootAssets = AccountEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "1- الأصول", category = AccountCategory.ASSETS,
            defaultCurrencyId = curYer.id, allowMultiCurrency = true, syncStatus = SyncStatus.SYNCED
        )
        val rootLiabilities = AccountEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "2- الخصوم والالتزامات", category = AccountCategory.LIABILITIES,
            defaultCurrencyId = curYer.id, allowMultiCurrency = true, syncStatus = SyncStatus.SYNCED
        )
        val rootEquity = AccountEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "3- حقوق الملكية", category = AccountCategory.EQUITY,
            defaultCurrencyId = curYer.id, allowMultiCurrency = true, syncStatus = SyncStatus.SYNCED
        )
        val rootRevenues = AccountEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "4- الإيرادات", category = AccountCategory.REVENUES,
            defaultCurrencyId = curYer.id, allowMultiCurrency = true, syncStatus = SyncStatus.SYNCED
        )
        val rootExpenses = AccountEntity(
            id = java.util.UUID.randomUUID().toString(), companyId = companyId,
            name = "5- المصروفات", category = AccountCategory.EXPENSES,
            defaultCurrencyId = curYer.id, allowMultiCurrency = true, syncStatus = SyncStatus.SYNCED
        )
        val settings = AppSettingsEntity(
            companyId = companyId, enableAppLock = true,
            appPinHash = PinHasher.hashPin("0000"), syncStatus = SyncStatus.SYNCED
        )

        // حفظ محلياً
        db.companyDao().upsert(company)
        db.userSyncDao().upsertUser(owner)
        db.financeDao().upsertCurrency(curYer)
        db.financeDao().upsertCurrency(curUsd)
        db.financeDao().upsertCurrency(curSar)
        db.financeDao().upsertCashBox(mainBox)
        db.inventoryDao().upsertWarehouse(mainWarehouse)
        db.accountDao().upsertAccount(cashCustomer)
        db.accountDao().upsertAccounts(listOf(rootAssets, rootLiabilities, rootEquity, rootRevenues, rootExpenses))
        db.financeDao().upsertSettings(settings)

        // دفع سحابياً: العضوية أولاً حتى تسمح قواعد Firestore بباقي الكتابات
        // معرف وثيقة العضو = firebaseUid التزاماً بقواعد Firestore (members/{auth.uid})
        val memberDocId = firebaseUid.ifBlank { owner.id }
        firestore.pushMemberDocument(
            companyId, memberDocId,
            mapOf(
                "userId" to owner.id,
                "companyId" to companyId,
                "firebaseUid" to firebaseUid,
                "email" to owner.email,
                "fullName" to owner.fullName,
                "pinHash" to owner.pinHash,
                "role" to owner.role.name,
                "joinedAt" to now
            )
        )
        firestore.pushCompanyDocument(companyId, companyPayloadWithOwnerUid(company, firebaseUid))
        firestore.pushDocument(companyId, "users", owner.id, owner)
        firestore.pushDocument(companyId, "currencies", curYer.id, curYer)
        firestore.pushDocument(companyId, "currencies", curUsd.id, curUsd)
        firestore.pushDocument(companyId, "currencies", curSar.id, curSar)
        firestore.pushDocument(companyId, "cash_boxes", mainBox.id, mainBox)
        firestore.pushDocument(companyId, "warehouses", mainWarehouse.id, mainWarehouse)
        firestore.pushDocument(companyId, "accounts", cashCustomer.id, cashCustomer)
        firestore.pushDocument(companyId, "app_settings", companyId, settings)

        return company to owner
    }

    /**
     * التحقق من عضو عبر كود الدعوة + PIN مباشرة من Firestore.
     * يعيد UserEntity المنسوخ محلياً عند النجاح.
     */
    suspend fun joinWithInvite(inviteJson: String, pin: String): UserEntity? {
        val invite = parseInviteJson(inviteJson) ?: return null
        val cid = invite.companyId.trim()
        val targetUserId = invite.userId.ifBlank { invite.memberId }.trim()
        if (cid.isBlank() || targetUserId.isBlank()) return null
        val currentUid = ensureAnonymousAuthOrNull() ?: return null

        // سجّل هذا الجهاز كعضو في الشركة حتى تسمح قواعد Firestore بقراءة users/{userId}
        ensureSelfMemberDocument(
            companyId = cid,
            uid = currentUid,
            userId = targetUserId,
            email = invite.email,
            fullName = invite.fullName,
            role = invite.role
        )

        val user = firestore.getDocument(cid, "users", targetUserId, UserEntity::class.java)
            ?: firestore.fetchCollection(cid, "users", UserEntity::class.java)
                .firstOrNull { it.id == targetUserId || it.email.equals(invite.email.trim(), ignoreCase = true) }
            ?: return null

        if (!user.isActive) return null
        if (invite.email.isNotBlank() && user.email.isNotBlank() &&
            !user.email.equals(invite.email.trim(), ignoreCase = true)
        ) return null
        Log.d(TAG, "joinWithInvite: user=${user.email} pinHash='${user.pinHash.take(20)}...' len=${user.pinHash.length} hasColon=${user.pinHash.contains(":")} verify=${PinHasher.verifyPin(pin, user.pinHash)}")
        if (!PinHasher.verifyPin(pin, user.pinHash)) return null

        firestore.pushMemberDocument(
            cid,
            currentUid,
            mapOf(
                "userId" to user.id,
                "companyId" to cid,
                "firebaseUid" to currentUid,
                "email" to user.email,
                "fullName" to user.fullName,
                "pinHash" to user.pinHash,
                "role" to user.role.name,
                "joinedAt" to System.currentTimeMillis()
            )
        )
        return pullCompanyAndGetUser(cid, user.id, invite.companyName)
    }

    private suspend fun ensureSelfMemberDocument(
        companyId: String,
        uid: String,
        userId: String,
        email: String,
        fullName: String,
        role: String
    ) {
        if (uid.isBlank() || userId.isBlank()) return
        val existing = firestore.getDocument(companyId, "members", uid, Map::class.java)
        if (existing != null) return
        val data = mutableMapOf<String, Any?>(
            "userId" to userId,
            "companyId" to companyId,
            "firebaseUid" to uid,
            "email" to email.trim(),
            "fullName" to fullName.trim(),
            "role" to role.trim(),
            "joinedAt" to System.currentTimeMillis()
        )
        firestore.pushMemberDocument(companyId, uid, data)
    }

    /**
     * دخول يدوي: كود الشركة + البريد/الجوال + PIN.
     * قواعد Firestore تشترط وجود members/{auth.uid} لقراءة أي بيانات داخل الشركة،
     * لذلك نسجّل عضوية مؤقتة ذاتية (مسموحة: uid == memberId) ثم نقرأ users ونتحقق من PIN.
     * عند فشل التحقق تُحذف العضوية المؤقتة فلا يبقى للجهاز وصول.
     */
    suspend fun manualCloudLogin(companyId: String, email: String, pin: String): UserEntity? {
        var cid = companyId.trim()
        val identifier = email.trim()
        // تسامح: إذا لُصق كود دعوة JSON كاملاً في حقل كود الشركة، استخرج companyId منه
        if (cid.startsWith("{")) {
            val invite = parseInviteJson(cid)
            if (invite != null && invite.companyId.isNotBlank()) {
                cid = invite.companyId.trim()
                Log.d(TAG, "manualLogin: extracted companyId=$cid from invite JSON")
            }
        }
        if (cid.isBlank() || identifier.isBlank()) { Log.w(TAG, "manualLogin: empty input"); return null }
        val uid = ensureAnonymousAuthOrNull() ?: run { Log.w(TAG, "manualLogin: auth failed"); return null }
        Log.d(TAG, "manualLogin: start cid=$cid uid=$uid id=$identifier")

        // هل الجهاز عضواً فعلياً من قبل؟ اقرأ وثيقة عضويته الخاصة (مسموح: وثيقته فقط)
        val existingMember = firestore.getDocument(cid, "members", uid, Map::class.java)
        val alreadyMember = (existingMember?.get("userId") as? String)?.isNotBlank() == true
        var tempMembershipCreated = false

        if (!alreadyMember) {
            // سجّل عضوية مؤقتة — القواعد تسمح بكتابة members/{auth.uid} فقط
            val tempDoc = mapOf(
                "companyId" to cid,
                "firebaseUid" to uid,
                "email" to identifier,
                "fullName" to "",
                "role" to "",
                "pendingLogin" to true,
                "joinedAt" to System.currentTimeMillis()
            )
            if (!firestore.pushMemberDocument(cid, uid, tempDoc)) {
                Log.w(TAG, "manualLogin: temp member push failed")
                return null
            }
            tempMembershipCreated = true
            Log.d(TAG, "manualLogin: temp member created")
        }

        // ابحث في users (مجموعة users قابلة للقراءة الآن بعد العضوية)
        val users = firestore.fetchCollection(cid, "users", UserEntity::class.java)
        Log.d(TAG, "manualLogin: fetched ${users.size} users")
        val user = users.firstOrNull {
            (it.email.equals(identifier, ignoreCase = true) || it.phone == identifier) &&
                !it.isDeleted && it.isActive
        }
        if (user == null) {
            Log.w(TAG, "manualLogin: user not found among ${users.size} (emails: ${users.take(5).map { it.email }})")
            if (tempMembershipCreated) firestore.deleteDocument(cid, "members", uid)
            return null
        }
        if (!PinHasher.verifyPin(pin, user.pinHash)) {
            Log.w(TAG, "manualLogin: PIN mismatch for ${user.email} pinHash='${user.pinHash.take(20)}' len=${user.pinHash.length} hasColon=${user.pinHash.contains(":")}")
            if (tempMembershipCreated) firestore.deleteDocument(cid, "members", uid)
            return null
        }
        Log.d(TAG, "manualLogin: PIN ok for ${user.email} id=${user.id}")

        // نجاح — حدّث وثيقة العضو بالبيانات الكاملة واربطها بالمستخدم
        firestore.pushMemberDocument(
            cid,
            uid,
            mapOf(
                "userId" to user.id,
                "companyId" to cid,
                "firebaseUid" to uid,
                "email" to user.email,
                "fullName" to user.fullName,
                "pinHash" to user.pinHash,
                "role" to user.role.name,
                "joinedAt" to System.currentTimeMillis()
            )
        )
        return pullCompanyAndGetUser(cid, user.id)
    }

    /** يتأكد من وجود وثيقة الشركة سحابياً ويعيد دفعها من النسخة المحلية عند الحاجة */
    suspend fun ensureCompanyDocumentPushed(companyId: String) {
        val cid = companyId.trim().ifBlank { return }
        val currentUid = ensureAnonymousAuthOrNull() ?: return
        if (firestore.getCompanyDocument(cid, CompanyEntity::class.java) != null) return
        val localCompany = db.companyDao().getById(cid) ?: return
        if (localCompany.ownerUserId.isBlank()) return
        val ownerUid = db.userSyncDao().getUserById(localCompany.ownerUserId)?.firebaseUid
            ?.ifBlank { currentUid } ?: currentUid
        firestore.pushCompanyDocument(cid, companyPayloadWithOwnerUid(localCompany, ownerUid))
    }

    /**
     * سحب كل بيانات شركة من Firestore إلى Room محلياً.
     */
    suspend fun pullCompanyData(companyId: String) {
        val cid = companyId.trim().ifBlank { currentCompanyIdOrNull() ?: return }
        val currentUid = ensureAnonymousAuthOrNull() ?: return

        // وثيقة الشركة نفسها: companies/{companyId}
        val company = firestore.getCompanyDocument(cid, CompanyEntity::class.java)
        if (company != null) {
            db.companyDao().upsert(company.copy(syncStatus = SyncStatus.SYNCED))
        } else {
            // إصلاح رجعي: إذا كانت الشركة موجودة محلياً لكن وثيقتها مفقودة سحابياً أعد دفعها
            val localCompany = db.companyDao().getById(cid)
            if (localCompany != null) {
                val ownerUid = db.userSyncDao().getUserById(localCompany.ownerUserId)?.firebaseUid
                    ?.ifBlank { currentUid } ?: currentUid
                firestore.pushCompanyDocument(cid, companyPayloadWithOwnerUid(localCompany, ownerUid))
                db.companyDao().upsert(localCompany.copy(syncStatus = SyncStatus.SYNCED))
            }
        }

        firestore.fetchCollection(cid, "users", UserEntity::class.java)
            .filter { !it.isDeleted }
            .forEach { db.userSyncDao().upsertUser(it.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null)) }

        firestore.fetchCollection(cid, "currencies", CurrencyEntity::class.java)
            .forEach { db.financeDao().upsertCurrency(it.copy(syncStatus = SyncStatus.SYNCED)) }

        firestore.fetchCollection(cid, "cash_boxes", CashBoxEntity::class.java)
            .forEach { db.financeDao().upsertCashBox(it.copy(syncStatus = SyncStatus.SYNCED)) }

        firestore.fetchCollection(cid, "warehouses", WarehouseEntity::class.java)
            .forEach { db.inventoryDao().upsertWarehouse(it.copy(syncStatus = SyncStatus.SYNCED)) }

        firestore.fetchCollection(cid, "accounts", AccountEntity::class.java)
            .filter { !it.isDeleted }
            .forEach { db.accountDao().upsertAccount(it.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null)) }

        firestore.fetchCollection(cid, "vouchers", VoucherEntity::class.java)
            .filter { !it.isDeleted }
            .forEach { db.voucherDao().upsertVoucher(it.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null)) }

        firestore.fetchCollection(cid, "products", ProductEntity::class.java)
            .filter { !it.isDeleted }
            .forEach { db.inventoryDao().upsertProduct(it.copy(syncStatus = SyncStatus.SYNCED)) }

        firestore.fetchCollection(cid, "stock_movements", StockMovementEntity::class.java)
            .forEach { db.inventoryDao().upsertStockMovement(it.copy(syncStatus = SyncStatus.SYNCED)) }

        // إعادة احتساب المخزون من الحركات
        val productIds = db.inventoryDao().observeProducts(cid).first().map { it.id }
        productIds.forEach { pid ->
            val exact = db.inventoryDao().computeExactStock(pid)
            db.inventoryDao().setExactStock(pid, exact, System.currentTimeMillis())
        }

        firestore.fetchCollection(cid, "invoices", InvoiceEntity::class.java)
            .filter { !it.isDeleted }
            .forEach { db.invoiceDao().upsertInvoice(it.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null)) }

        firestore.fetchCollection(cid, "invoice_items", InvoiceItemEntity::class.java)
            .filter { !it.isDeleted }
            .forEach { db.invoiceDao().upsertInvoiceItems(listOf(it.copy(syncStatus = SyncStatus.SYNCED))) }

        firestore.fetchCollection(cid, "app_settings", AppSettingsEntity::class.java)
            .firstOrNull()?.let { db.financeDao().upsertSettings(it.copy(syncStatus = SyncStatus.SYNCED)) }
            ?: db.financeDao().getSettings(cid)?.let {
                db.financeDao().upsertSettings(it.copy(syncStatus = SyncStatus.SYNCED))
            }
    }

    /** سحب بيانات الشركة ثم إرجاع المستخدم المحلي المطابق */
    private suspend fun pullCompanyAndGetUser(companyId: String, memberId: String, companyName: String = ""): UserEntity? {
        pullCompanyData(companyId)
        ensureLocalCompany(companyId, companyName)
        val users = db.userSyncDao().observeUsers(companyId).first()
        return users.firstOrNull { it.id == memberId }
            ?: users.firstOrNull { it.role == UserRole.OWNER }
            ?: users.firstOrNull()
    }

    private suspend fun ensureLocalCompany(companyId: String, nameAr: String = "") {
        if (db.companyDao().getById(companyId) != null) return
        val now = System.currentTimeMillis()
        db.companyDao().upsert(
            CompanyEntity(
                id = companyId,
                nameAr = nameAr.ifBlank { "مجموعة" },
                syncStatus = SyncStatus.SYNCED,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun companyPayloadWithOwnerUid(company: CompanyEntity, ownerUid: String): Map<String, Any?> {
        val json = gson.toJson(company)
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        val map = gson.fromJson<Map<String, Any?>>(json, mapType).toMutableMap()
        map["ownerUid"] = ownerUid
        return map
    }
}
