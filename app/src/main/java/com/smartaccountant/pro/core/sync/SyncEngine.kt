package com.smartaccountant.pro.core.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.smartaccountant.pro.MainActivity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.smartaccountant.pro.core.config.AppConfig
import com.smartaccountant.pro.core.session.SessionStore
import com.smartaccountant.pro.core.utils.DeviceIdentity
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.*
import com.smartaccountant.pro.data.remote.CloudMediaStorage
import com.smartaccountant.pro.data.remote.FirebaseAuthService
import com.smartaccountant.pro.data.remote.FirestoreSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SyncUiState(
    val isOnlineCloudActive: Boolean = false,
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSyncTimeMillis: Long? = null,
    val statusMessage: String = "وضع العمل المحلي السريع (جاهز للمزامنة)"
)

class SyncEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val authService: FirebaseAuthService,
    private val remote: FirestoreSyncService,
    private val cloudMediaStorage: CloudMediaStorage,
    private val deviceName: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<ListenerRegistration>()
    private val gson = Gson()
    private val sessionStore by lazy { SessionStore(context) }
    private val legacyDeviceName = DeviceIdentity.legacyName()

    // حالة الاتصال الفعلية: شبكة + مصادقة + استجابة Firestore
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasNetwork = false
    val isOnline: Boolean get() = hasNetwork
    private var listenersActive = false
    private var cloudReachable = true
    private var autoSyncJob: Job? = null
    private var autoSyncCompanyId: String? = null

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private fun showRealtimeNotification(
        title: String,
        body: String,
        notificationId: Int,
        documentId: String? = null,
        destination: String = "reports"
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "realtime_operations"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "العمليات الفورية",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات فورية عند وصول سندات أو فواتير جديدة من أجهزة أخرى"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_NOTIFICATION_DESTINATION, destination)
                documentId?.let { putExtra(MainActivity.EXTRA_NOTIFICATION_DOCUMENT_ID, it) }
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            nm.notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    /** مستمع مخصص يرصد المستندات المضافة (ADDED) فقط مع بيانات التغيير */
    private fun <T> listenAddedDocuments(
        companyId: String,
        collection: String,
        clazz: Class<T>,
        onAdded: (T) -> Unit
    ): ListenerRegistration? {
        if (!remote.isFirebaseConfigured()) return null
        val cid = companyId.trim().ifBlank { sessionStore.currentCompanyId?.takeIf { it.isNotBlank() } } ?: return null
        return try {
            FirebaseFirestore.getInstance()
                .collection("companies")
                .document(cid)
                .collection(collection)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    snapshot.documentChanges.forEach { change ->
                        if (change.type == DocumentChange.Type.ADDED) {
                            try {
                                val json = gson.toJson(change.document.data)
                                val item = gson.fromJson(json, clazz)
                                onAdded(item)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    fun startRealtimeListeners(companyId: String, scheduleInitialSync: Boolean = true) {
        stopListeners()
        if (!AppConfig.ENABLE_CLOUD_SYNC || !remote.isFirebaseConfigured()) {
            _syncState.value = SyncUiState(
                isOnlineCloudActive = false,
                statusMessage = "وضع محلي نشط (أضف google-services.json لتفعيل السحابة)"
            )
            return
        }

        if (scheduleInitialSync) scheduleBackgroundSync(companyId)
        val listenerStartedAt = System.currentTimeMillis()
        scope.launch {
            val uid = authService.ensureAuthenticatedSession()
            if (uid.isNullOrBlank()) {
                _syncState.value = _syncState.value.copy(
                    isOnlineCloudActive = false,
                    statusMessage = "تعذر مصادقة Firebase Auth — يعمل محلياً"
                )
                return@launch
            }

            listenersActive = true
            cloudReachable = true
            registerNetworkCallback()
            // عرض أخطاء مستمعي المجموعات الفرعية في حالة المزامنة بدل ابتلاعها
            remote.onSyncError = { collection, message ->
                _syncState.value = _syncState.value.copy(
                    statusMessage = "خطأ مزامنة ($collection): $message"
                )
            }
            _syncState.value = _syncState.value.copy(
                statusMessage = "جارٍ الاتصال بالسحابة..."
            )

            // 0. Heartbeat: مستمع على وثيقة الشركة لكشف انقطاع الشبكة أو رفض الصلاحيات فوراً
            FirebaseFirestore.getInstance()
                .collection("companies")
                .document(companyId)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        cloudReachable = false
                        _syncState.value = _syncState.value.copy(
                            isOnlineCloudActive = false,
                            statusMessage = "تعذر الاتصال بالسحابة: ${error.message ?: "تحقق من الإنترنت وقواعد Firestore"}"
                        )
                    } else if (snap != null) {
                        cloudReachable = true
                        updateOnlineState("متصل بالسحابة — مزامنة فورية بين الأجهزة")
                    }
                }
                .also { listeners.add(it) }

            // مزامنة تلقائية كل 10 ثوانٍ ما دام التطبيق مفتوحاً ومتصلاً
            startAutoSyncLoop(companyId)

            // تسجيل رمز FCM Token للجهاز لسحب الإشعارات الفورية
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        val tokenData = mapOf(
                            "token" to token,
                            "deviceName" to deviceName,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        scope.launch(Dispatchers.IO) {
                            remote.pushDocument(companyId, "fcm_tokens", kotlin.math.abs(deviceName.hashCode()).toString(), tokenData)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 0. Company Entity (مستمع فوري لكائن وثيقة الشركة لمزامنة تغيير اسم الشركة والشعار مع كل الأجهزة)
            remote.listenCompanyDocument(companyId, CompanyEntity::class.java) { updatedCompany ->
                if (updatedCompany != null) {
                    scope.launch {
                        db.companyDao().upsert(updatedCompany.copy(syncStatus = SyncStatus.SYNCED))
                    }
                }
            }?.let { listeners.add(it) }

            // 0.1 Push Notifications (مستمع الإشعارات الفورية للعمليات الصادرة من أجهزة الموظفين الآخرين)
            remote.listenCollection(companyId, "push_notifications", Map::class.java) { items ->
                val prefs = context.getSharedPreferences("sync_engine_prefs", Context.MODE_PRIVATE)
                val lastNotifiedTime = prefs.getLong("last_notified_push_$companyId", 0L)
                var maxTime = lastNotifiedTime

                items.forEach { notifMap ->
                    val title = notifMap["title"] as? String ?: return@forEach
                    val body = notifMap["body"] as? String ?: return@forEach
                    val senderDevice = notifMap["senderDevice"] as? String ?: ""
                    val time = (notifMap["timestamp"] as? Number)?.toLong() ?: 0L

                    if (senderDevice != deviceName && time > lastNotifiedTime) {
                        if (time > maxTime) maxTime = time
                        showRealtimeNotification(
                            title = title,
                            body = body,
                            notificationId = (time % 100000).toInt()
                        )
                    }
                }
                if (maxTime > lastNotifiedTime) {
                    prefs.edit().putLong("last_notified_push_$companyId", maxTime).apply()
                }
            }?.let { listeners.add(it) }

            // 1. Users
            remote.listenCollection(companyId, "users", UserEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.userSyncDao().getUserById(r.id)
                        when (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt)) {
                            MergeDecision.ACCEPT_REMOTE -> db.userSyncDao().upsertUser(r.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null))
                            MergeDecision.FLAG_CONFLICT -> l?.let {
                                db.userSyncDao().upsertUser(it.copy(syncStatus = SyncStatus.CONFLICT, conflictJson = gson.toJson(r)))
                            }
                            MergeDecision.KEEP_LOCAL_PENDING -> {}
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 2. Currencies
            remote.listenCollection(companyId, "currencies", CurrencyEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.financeDao().getCurrencyById(r.id)
                        if (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt) == MergeDecision.ACCEPT_REMOTE) {
                            db.financeDao().upsertCurrency(r.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 3. CashBoxes
            remote.listenCollection(companyId, "cash_boxes", CashBoxEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.financeDao().getCashBoxById(r.id)
                        if (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt) == MergeDecision.ACCEPT_REMOTE) {
                            db.financeDao().upsertCashBox(r.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 4. Warehouses
            remote.listenCollection(companyId, "warehouses", WarehouseEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.inventoryDao().getWarehouseById(r.id)
                        if (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt) == MergeDecision.ACCEPT_REMOTE) {
                            db.inventoryDao().upsertWarehouse(r.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 5. App Settings
            remote.listenCollection(companyId, "app_settings", AppSettingsEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.financeDao().getSettings(r.companyId)
                        if (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt) == MergeDecision.ACCEPT_REMOTE) {
                            db.financeDao().upsertSettings(r.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 6. Recurring Transactions
            remote.listenCollection(companyId, "recurringTransactions", RecurringTransactionEntity::class.java) { items ->
                scope.launch {
                    items.filter { it.companyId == companyId }.forEach { remoteItem ->
                        val local = db.recurringTransactionDao()
                            .getRecurringTransaction(companyId, remoteItem.id)
                        when (ConflictResolver.decide(local?.syncStatus, local?.updatedAt, remoteItem.updatedAt)) {
                            MergeDecision.ACCEPT_REMOTE ->
                                db.recurringTransactionDao().upsertRecurringTransaction(
                                    remoteItem.copy(syncStatus = SyncStatus.SYNCED)
                                )
                            MergeDecision.FLAG_CONFLICT -> local?.let {
                                db.recurringTransactionDao().upsertRecurringTransaction(
                                    it.copy(syncStatus = SyncStatus.CONFLICT, conflictJson = gson.toJson(remoteItem))
                                )
                            }
                            MergeDecision.KEEP_LOCAL_PENDING -> Unit
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 7. Accounts
            remote.listenCollection(companyId, "accounts", AccountEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.accountDao().getAccountById(r.id)
                        when (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt)) {
                            MergeDecision.ACCEPT_REMOTE -> db.accountDao().upsertAccount(r.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null))
                            MergeDecision.FLAG_CONFLICT -> l?.let {
                                db.accountDao().upsertAccount(it.copy(syncStatus = SyncStatus.CONFLICT, conflictJson = gson.toJson(r)))
                            }
                            MergeDecision.KEEP_LOCAL_PENDING -> {}
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 7. Vouchers
            remote.listenCollection(companyId, "vouchers", VoucherEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.voucherDao().getVoucherById(r.id)
                        when (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt)) {
                            MergeDecision.ACCEPT_REMOTE -> db.voucherDao().upsertVoucher(r.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null))
                            MergeDecision.FLAG_CONFLICT -> l?.let {
                                db.voucherDao().upsertVoucher(it.copy(syncStatus = SyncStatus.CONFLICT, conflictJson = gson.toJson(r)))
                            }
                            MergeDecision.KEEP_LOCAL_PENDING -> {}
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 8. Products
            remote.listenCollection(companyId, "products", ProductEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.inventoryDao().getProductById(r.id)
                        if (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt) == MergeDecision.ACCEPT_REMOTE) {
                            db.inventoryDao().upsertProduct(r.copy(syncStatus = SyncStatus.SYNCED))
                            val exact = db.inventoryDao().computeExactStock(r.id)
                            db.inventoryDao().setExactStock(r.id, exact, System.currentTimeMillis())
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 9. Stock Movements (يضمن تطابق المخزون الحسابي 100% بين جميع الأجهزة)
            remote.listenCollection(companyId, "stock_movements", StockMovementEntity::class.java) { items ->
                scope.launch {
                    val affectedProductIds = mutableSetOf<String>()
                    items.forEach { m ->
                        db.inventoryDao().upsertStockMovement(m.copy(syncStatus = SyncStatus.SYNCED))
                        affectedProductIds.add(m.productId)
                    }
                    affectedProductIds.forEach { prodId ->
                        val exact = db.inventoryDao().computeExactStock(prodId)
                        db.inventoryDao().setExactStock(prodId, exact, System.currentTimeMillis())
                    }
                }
            }?.let { listeners.add(it) }

            // 10. Invoices
            remote.listenCollection(companyId, "invoices", InvoiceEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.invoiceDao().getInvoiceById(r.id)
                        when (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt)) {
                            MergeDecision.ACCEPT_REMOTE -> db.invoiceDao().upsertInvoice(r.copy(syncStatus = SyncStatus.SYNCED, conflictJson = null))
                            MergeDecision.FLAG_CONFLICT -> l?.let {
                                db.invoiceDao().upsertInvoice(it.copy(syncStatus = SyncStatus.CONFLICT, conflictJson = gson.toJson(r)))
                            }
                            MergeDecision.KEEP_LOCAL_PENDING -> {}
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 11. Invoice Items
            remote.listenCollection(companyId, "invoice_items", InvoiceItemEntity::class.java) { items ->
                scope.launch {
                    items.forEach { r ->
                        val l = db.invoiceDao().getInvoiceItemById(r.id)
                        if (ConflictResolver.decide(l?.syncStatus, l?.updatedAt, r.updatedAt) == MergeDecision.ACCEPT_REMOTE) {
                            db.invoiceDao().upsertInvoiceItems(listOf(r.copy(syncStatus = SyncStatus.SYNCED)))
                        }
                    }
                }
            }?.let { listeners.add(it) }

            // 12. Audit Logs (Push + Pull)
            remote.listenCollection(companyId, "audit_logs", AuditLogEntity::class.java) { items ->
                scope.launch {
                    items.forEach { log ->
                        db.userSyncDao().insertAuditLog(log.copy(syncStatus = SyncStatus.SYNCED))
                    }
                }
            }?.let { listeners.add(it) }

            // إشعار واحد لكل عملية مسجلة في سجل التدقيق، بما فيها السندات والقيود والفواتير.
            listenAddedDocuments(companyId, "audit_logs", AuditLogEntity::class.java) { log ->
                val isRecent = log.timestamp >= listenerStartedAt
                val fromOtherDevice = log.deviceName.isNotBlank() &&
                    log.deviceName != deviceName &&
                    log.deviceName != legacyDeviceName
                if (isRecent && fromOtherDevice) {
                    val isVoucher = VoucherType.values().any { it.arabicTitle == log.entityType }
                    val isInvoice = InvoiceType.values().any { it.arabicTitle == log.entityType }
                    val destination = when {
                        isVoucher -> MainActivity.DESTINATION_VOUCHER
                        isInvoice -> MainActivity.DESTINATION_INVOICES
                        else -> MainActivity.DESTINATION_AUDIT
                    }
                    val title = when {
                        isVoucher -> "عملية سند: ${log.entityType}"
                        isInvoice -> "عملية فاتورة: ${log.entityType}"
                        else -> "عملية جديدة: ${log.entityType}"
                    }
                    showRealtimeNotification(
                        title = title,
                        body = "الموظف ${log.userName} — ${log.action.arabicTitle}: ${log.summary}",
                        notificationId = ("operation_" + log.id).hashCode(),
                        documentId = log.entityId.takeIf { isVoucher },
                        destination = destination
                    )
                }
            }?.let { listeners.add(it) }
        }
    }

    suspend fun pullRemoteOperationsAndNotify(companyId: String) {
        if (companyId.isBlank()) return
        val prefs = context.getSharedPreferences("sync_engine_bg_prefs", Context.MODE_PRIVATE)
        var lastNotifiedTime = prefs.getLong("last_notified_bg_log_time_$companyId", 0L)
        if (lastNotifiedTime == 0L) {
            lastNotifiedTime = System.currentTimeMillis() - (60 * 1000L)
            prefs.edit().putLong("last_notified_bg_log_time_$companyId", lastNotifiedTime).apply()
        }

        try {
            val logs = remote.fetchCollectionSince(companyId, "audit_logs", AuditLogEntity::class.java, lastNotifiedTime)
            var maxTimestamp = lastNotifiedTime

            logs.forEach { log ->
                if (log.timestamp > maxTimestamp) maxTimestamp = log.timestamp
                val fromOtherDevice = log.deviceName.isNotBlank() &&
                        log.deviceName != deviceName &&
                        log.deviceName != legacyDeviceName

                if (log.timestamp > lastNotifiedTime && fromOtherDevice) {
                    db.userSyncDao().insertAuditLog(log.copy(syncStatus = SyncStatus.SYNCED))
                    val isVoucher = VoucherType.entries.any { it.arabicTitle == log.entityType }
                    val isInvoice = InvoiceType.entries.any { it.arabicTitle == log.entityType }
                    val destination = when {
                        isVoucher -> MainActivity.DESTINATION_VOUCHER
                        isInvoice -> MainActivity.DESTINATION_INVOICES
                        else -> MainActivity.DESTINATION_AUDIT
                    }
                    val title = when {
                        isVoucher -> "عملية سند جديدة: ${log.entityType}"
                        isInvoice -> "عملية فاتورة جديدة: ${log.entityType}"
                        else -> "عملية جديدة: ${log.entityType}"
                    }
                    showRealtimeNotification(
                        title = title,
                        body = "الموظف ${log.userName} — ${log.action.arabicTitle}: ${log.summary}",
                        notificationId = ("bg_op_" + log.id).hashCode(),
                        documentId = log.entityId.takeIf { isVoucher },
                        destination = destination
                    )
                }
            }
            if (maxTimestamp > lastNotifiedTime) {
                prefs.edit().putLong("last_notified_bg_log_time_$companyId", maxTimestamp).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerPushNotification(companyId: String, title: String, body: String) {
        if (!remote.isFirebaseConfigured()) return
        scope.launch(Dispatchers.IO) {
            try {
                val notifData = mapOf(
                    "title" to title,
                    "body" to body,
                    "senderDevice" to deviceName,
                    "timestamp" to System.currentTimeMillis()
                )
                remote.pushDocument(companyId, "push_notifications", java.util.UUID.randomUUID().toString(), notifData)

                sendDirectFcmToCompanyTokens(companyId, title, body)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun sendDirectFcmToCompanyTokens(companyId: String, title: String, body: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val tokensSnap = db.collection("companies").document(companyId).collection("fcm_tokens").get().await()
            val tokenList = tokensSnap.documents.mapNotNull { it.getString("token") }.distinct()

            val client = okhttp3.OkHttpClient()
            val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            for (t in tokenList) {
                val payload = mapOf(
                    "to" to t,
                    "priority" to "high",
                    "notification" to mapOf(
                        "title" to title,
                        "body" to body,
                        "sound" to "default"
                    ),
                    "data" to mapOf(
                        "title" to title,
                        "body" to body,
                        "senderDevice" to deviceName
                    )
                )
                val bodyJson = gson.toJson(payload)
                val reqBody = bodyJson.toRequestBody(jsonMediaType)
                val request = okhttp3.Request.Builder()
                    .url("https://fcm.googleapis.com/fcm/send")
                    .post(reqBody)
                    .build()
                client.newCall(request).execute().close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** مزامنة تلقائية دورية كل 10 ثوانٍ أثناء فتح التطبيق */
    private fun startAutoSyncLoop(companyId: String) {
        autoSyncJob?.cancel()
        autoSyncCompanyId = companyId
        autoSyncJob = scope.launch {
            while (isActive) {
                delay(10_000)
                val cid = autoSyncCompanyId ?: break
                if (hasNetwork && listenersActive && cloudReachable) {
                    syncPendingNow(cid)
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        hasNetwork = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                hasNetwork = true
                updateOnlineState("متصل بالسحابة — مزامنة فورية بين الأجهزة")
            }
            override fun onLost(network: Network) {
                hasNetwork = false
                updateOnlineState("لا يوجد اتصال بالإنترنت — وضع محلي")
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
        updateOnlineState(if (hasNetwork) "جارٍ الاتصال بالسحابة..." else "لا يوجد اتصال بالإنترنت — وضع محلي")
    }

    private fun updateOnlineState(onlineMessage: String) {
        val online = listenersActive && hasNetwork && cloudReachable
        _syncState.value = _syncState.value.copy(
            isOnlineCloudActive = online,
            statusMessage = when {
                !hasNetwork -> "لا يوجد اتصال بالإنترنت — وضع محلي"
                !cloudReachable -> _syncState.value.statusMessage
                online -> onlineMessage
                else -> _syncState.value.statusMessage
            }
        )
    }

    fun stopListeners() {
        autoSyncJob?.cancel()
        autoSyncJob = null
        autoSyncCompanyId = null
        listeners.forEach { it.remove() }
        listeners.clear()
        listenersActive = false
        cloudReachable = true
        remote.onSyncError = null
        networkCallback?.let {
            try {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        _syncState.value = _syncState.value.copy(
            isOnlineCloudActive = false,
            statusMessage = "تم إيقاف المزامنة — وضع محلي"
        )
    }

    /** تسجيل خروج كامل: إيقاف المزامنة، مسح الجلسة، ومسح كل بيانات Room المحلية */
    suspend fun logout() {
        stopListeners()
        sessionStore.clearSession()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
        }
        _syncState.value = SyncUiState(
            isOnlineCloudActive = false,
            isSyncing = false,
            pendingCount = 0,
            conflictCount = 0,
            lastSyncTimeMillis = null,
            statusMessage = "تم تسجيل الخروج ومسح البيانات المحلية"
        )
    }

    suspend fun syncPendingNow(companyId: String): Boolean {
        val pendingUsers = db.userSyncDao().getPendingUsers(companyId)
        val pendingCurrencies = db.financeDao().getPendingCurrencies(companyId)
        val pendingBoxes = db.financeDao().getPendingCashBoxes(companyId)
        val pendingWarehouses = db.inventoryDao().getPendingWarehouses(companyId)
        val settings = db.financeDao().getSettings(companyId)
        val pendingAccounts = db.accountDao().getPendingAccounts(companyId)
        val pendingVouchers = db.voucherDao().getPendingVouchers(companyId)
        val pendingProducts = db.inventoryDao().getPendingProducts(companyId)
        val pendingMovements = db.inventoryDao().getPendingStockMovements(companyId)
        val pendingInvoices = db.invoiceDao().getPendingInvoices(companyId)
        val pendingInvoiceItems = db.invoiceDao().getPendingInvoiceItems(companyId)
        val pendingRecurringTransactions = db.recurringTransactionDao().getPendingRecurringTransactions(companyId)
        val pendingLogs = db.userSyncDao().getPendingAuditLogs(companyId)
        val settingsPending = settings != null && settings.syncStatus != SyncStatus.SYNCED

        val totalPending = pendingUsers.size + pendingCurrencies.size + pendingBoxes.size +
            pendingWarehouses.size + pendingAccounts.size + pendingVouchers.size +
            pendingProducts.size + pendingMovements.size + pendingInvoices.size +
            pendingInvoiceItems.size + pendingLogs.size
            + pendingRecurringTransactions.size + (if (settingsPending) 1 else 0)

        // لا شيء للمزامنة: تحديث الحالة بهدوء دون وميض "جارٍ المزامنة"
        if (totalPending == 0) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                pendingCount = 0,
                lastSyncTimeMillis = System.currentTimeMillis(),
                statusMessage = "متزامن بالكامل مع السحابة"
            )
            return true
        }

        _syncState.value = _syncState.value.copy(isSyncing = true, pendingCount = totalPending)

        if (!remote.isFirebaseConfigured()) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                pendingCount = totalPending,
                statusMessage = "محفوظ محلياً ($totalPending سجل بانتظار ربط السحابة)"
            )
            return true
        }

        val authenticatedUid = try {
            authService.ensureAuthenticatedSession()
        } catch (_: Exception) {
            null
        }
        if (authenticatedUid.isNullOrBlank()) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                pendingCount = totalPending,
                statusMessage = "تعذرت مصادقة Firebase؛ ستتم إعادة المحاولة عند توفر الاتصال"
            )
            return false
        }

        var pushFailed = false
        try {
            pendingUsers.filter { it.syncStatus != SyncStatus.CONFLICT }.forEach { u ->
                if (remote.pushDocument(companyId, "users", u.id, u.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.userSyncDao().upsertUser(u.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingCurrencies.forEach { c ->
                if (remote.pushDocument(companyId, "currencies", c.id, c.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.financeDao().upsertCurrency(c.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingBoxes.forEach { b ->
                if (remote.pushDocument(companyId, "cash_boxes", b.id, b.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.financeDao().upsertCashBox(b.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingWarehouses.forEach { w ->
                if (remote.pushDocument(companyId, "warehouses", w.id, w.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.inventoryDao().upsertWarehouse(w.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            if (settings != null && settings.syncStatus != SyncStatus.SYNCED) {
                if (remote.pushDocument(companyId, "app_settings", settings.companyId, settings.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.financeDao().upsertSettings(settings.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingAccounts.filter { it.syncStatus != SyncStatus.CONFLICT }.forEach { acc ->
                if (remote.pushDocument(companyId, "accounts", acc.id, acc.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.accountDao().upsertAccount(acc.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingVouchers.filter { it.syncStatus != SyncStatus.CONFLICT }.forEach { v ->
                var voucherToSync = v
                if (v.attachmentUrl.isNotBlank() && !v.attachmentUrl.startsWith("https://") && !v.attachmentUrl.startsWith("http://") && !v.attachmentUrl.startsWith("data:")) {
                    try {
                        val attachment = File(v.attachmentUrl).canonicalFile
                        if (attachment.isFile) {
                            val uploadedUrl = cloudMediaStorage.compressAndUploadAttachment(companyId, attachment, "vouchers")
                            if (uploadedUrl.isNotBlank()) {
                                voucherToSync = v.copy(attachmentUrl = uploadedUrl)
                                db.voucherDao().upsertVoucher(voucherToSync)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (remote.pushDocument(companyId, "vouchers", voucherToSync.id, voucherToSync.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.voucherDao().upsertVoucher(voucherToSync.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingProducts.filter { it.syncStatus != SyncStatus.CONFLICT }.forEach { p ->
                if (remote.pushDocument(companyId, "products", p.id, p.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.inventoryDao().upsertProduct(p.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingMovements.forEach { m ->
                if (remote.pushDocument(companyId, "stock_movements", m.id, m.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.inventoryDao().upsertStockMovement(m.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingInvoices.filter { it.syncStatus != SyncStatus.CONFLICT }.forEach { inv ->
                var invoiceToSync = inv
                if (inv.attachmentUrl.isNotBlank() && !inv.attachmentUrl.startsWith("https://") && !inv.attachmentUrl.startsWith("http://") && !inv.attachmentUrl.startsWith("data:")) {
                    try {
                        val attachment = File(inv.attachmentUrl).canonicalFile
                        if (attachment.isFile) {
                            val uploadedUrl = cloudMediaStorage.compressAndUploadAttachment(companyId, attachment, "invoices")
                            if (uploadedUrl.isNotBlank()) {
                                invoiceToSync = inv.copy(attachmentUrl = uploadedUrl)
                                db.invoiceDao().upsertInvoice(invoiceToSync)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (remote.pushDocument(companyId, "invoices", invoiceToSync.id, invoiceToSync.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.invoiceDao().upsertInvoice(invoiceToSync.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }
            pendingInvoiceItems.forEach { item ->
                if (remote.pushDocument(companyId, "invoice_items", item.id, item.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.invoiceDao().upsertInvoiceItems(listOf(item.copy(syncStatus = SyncStatus.SYNCED)))
                } else pushFailed = true
            }
            pendingRecurringTransactions.filter { it.syncStatus != SyncStatus.CONFLICT }.forEach { transaction ->
                if (remote.pushDocument(
                        companyId,
                        "recurringTransactions",
                        transaction.id,
                        transaction.copy(syncStatus = SyncStatus.SYNCED)
                    )
                ) {
                    db.recurringTransactionDao().upsertRecurringTransaction(
                        transaction.copy(syncStatus = SyncStatus.SYNCED)
                    )
                } else pushFailed = true
            }
            pendingLogs.forEach { log ->
                if (remote.pushDocument(companyId, "audit_logs", log.id, log.copy(syncStatus = SyncStatus.SYNCED))) {
                    db.userSyncDao().insertAuditLog(log.copy(syncStatus = SyncStatus.SYNCED))
                } else pushFailed = true
            }

            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                pendingCount = if (pushFailed) totalPending else 0,
                lastSyncTimeMillis = if (pushFailed) _syncState.value.lastSyncTimeMillis else System.currentTimeMillis(),
                statusMessage = if (pushFailed) "تعذرت مزامنة بعض السجلات؛ ستتم إعادة المحاولة عند توفر الاتصال"
                else "متزامن بالكامل مع السحابة (13 مجموعة)"
            )
            return !pushFailed
        } catch (error: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                statusMessage = "تعذرت المزامنة: ${error.message ?: "ستتم إعادة المحاولة تلقائياً"}"
            )
            return false
        }
    }

    fun scheduleBackgroundSync(companyId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val data = workDataOf("companyId" to companyId)
        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "instant_sync_$companyId",
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }
}
