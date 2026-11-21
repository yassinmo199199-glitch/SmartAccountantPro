package com.smartaccountant.pro.domain.repository

import androidx.room.withTransaction
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.sync.SyncEngine
import com.smartaccountant.pro.core.utils.DocumentNumberGenerator
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.*

class InvoiceRepository(
    private val db: AppDatabase,
    private val syncEngine: SyncEngine,
    private val docNumberGen: DocumentNumberGenerator,
    private val deviceName: String
) {
    fun calculateMeteredQuantity(
        mode: MeteringMode,
        length: Double,
        width: Double,
        height: Double,
        pieces: Int,
        min100CmAs1Meter: Boolean
    ): Double {
        if (mode == MeteringMode.NONE) return pieces.toDouble()
        val effectiveLength = if (min100CmAs1Meter && length > 0 && length < 1.0) 1.0 else length
        val effectiveWidth = if (min100CmAs1Meter && width > 0 && width < 1.0) 1.0 else width
        return when (mode) {
            MeteringMode.LENGTH_WIDTH -> effectiveLength * effectiveWidth * pieces
            MeteringMode.AREA_HEIGHT -> effectiveLength * height * pieces
            MeteringMode.NONE -> pieces.toDouble()
        }
    }

    fun calculateInvoiceTotals(
        subTotal: Double,
        discountInput: Double,
        isDiscountPercentage: Boolean,
        enableTax: Boolean,
        taxPercent: Double,
        calculateTaxAfterDiscount: Boolean
    ): Triple<Double, Double, Double> {
        val discountVal = if (isDiscountPercentage) (subTotal * discountInput / 100.0) else discountInput
        val afterDiscount = (subTotal - discountVal).coerceAtLeast(0.0)
        val taxBase = if (calculateTaxAfterDiscount) afterDiscount else subTotal
        val taxVal = if (enableTax) (taxBase * taxPercent / 100.0) else 0.0
        val netTotal = afterDiscount + taxVal
        return Triple(discountVal, taxVal, netTotal)
    }

    suspend fun saveInvoice(
        currentUser: UserEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        isEdit: Boolean = false
    ) {
        require(invoice.companyId == currentUser.companyId) { "لا يمكن حفظ فاتورة لشركة أخرى" }
        if (isEdit) {
            PermissionManager.requirePermission(currentUser, { it.canEditInvoice }, "لا تملك صلاحية تعديل الفواتير")
            if (invoice.isPosted) {
                throw IllegalStateException("الفاتورة مرحّلة ومقفلة؛ يجب إلغاء الترحيل أولاً قبل التعديل")
            }
        } else {
            when (invoice.type) {
                InvoiceType.SALE, InvoiceType.FREE_SALE, InvoiceType.QUOTATION, InvoiceType.ORDER ->
                    PermissionManager.requirePermission(currentUser, { it.canCreateSaleInvoice }, "لا تملك صلاحية إنشاء فواتير البيع")
                InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE ->
                    PermissionManager.requirePermission(currentUser, { it.canCreatePurchaseInvoice }, "لا تملك صلاحية إنشاء فواتير الشراء")
                InvoiceType.SALE_RETURN, InvoiceType.PURCHASE_RETURN ->
                    PermissionManager.requirePermission(currentUser, { it.canCreateReturnInvoice }, "لا تملك صلاحية إنشاء مردودات")
            }
        }
        if (invoice.type == InvoiceType.SALE_RETURN || invoice.type == InvoiceType.PURCHASE_RETURN) {
            validateReturnInvoice(invoice)
        } else {
            require(invoice.originalInvoiceId == null) { "لا يمكن ربط فاتورة عادية بمردود أصلي" }
        }
        if (invoice.discountAmount > 0) {
            PermissionManager.requirePermission(currentUser, { it.canApplyDiscount }, "لا تملك صلاحية منح خصومات")
        }
        invoice.cashBoxId?.let { cashBoxId ->
            if (!PermissionManager.canAccessCashBox(currentUser, cashBoxId)) {
                throw SecurityException("غير مصرح لك باستخدام هذا الصندوق النقدي")
            }
        }
        invoice.warehouseId.takeIf { it.isNotBlank() }?.let { warehouseId ->
            if (!PermissionManager.canAccessWarehouse(currentUser, warehouseId)) {
                throw SecurityException("غير مصرح لك باستخدام هذا المخزن")
            }
        }
        invoice.targetWarehouseId?.takeIf { it.isNotBlank() }?.let { warehouseId ->
            if (!PermissionManager.canAccessWarehouse(currentUser, warehouseId)) {
                throw SecurityException("غير مصرح لك باستخدام المخزن المستهدف")
            }
        }

        val settings = db.financeDao().getSettings(invoice.companyId)
        for (item in items) {
            if (invoice.type == InvoiceType.SALE && settings?.allowSellBelowCost == false &&
                !currentUser.permissions.canSellBelowCost && item.unitPrice < item.costPriceAtTime
            ) {
                throw IllegalStateException("لا يمكن بيع الصنف (${item.productName}) بسعر أقل من سعر الشراء")
            }
            if (invoice.type == InvoiceType.SALE && settings?.enforceStockOnSale == true && !invoice.isArchived) {
                val prod = db.inventoryDao().getProductById(item.productId)
                val neededBaseQty = item.quantity * item.unitFactor
                if (prod != null && prod.stockQuantity < neededBaseQty) {
                    throw IllegalStateException("لا توجد كمية كافية في المخزن للصنف: ${item.productName}")
                }
            }
        }
        val sourceItems = if (invoice.type == InvoiceType.SALE_RETURN || invoice.type == InvoiceType.PURCHASE_RETURN) {
            prepareReturnItems(invoice, items)
        } else items

        val now = System.currentTimeMillis()
        val invNum = if (invoice.invoiceNumber > 0) invoice.invoiceNumber
        else db.invoiceDao().getNextInvoiceNumber(invoice.companyId)
        val dispNum = invoice.displayNumber.ifBlank {
            docNumberGen.formatInvoiceDisplayNumber(invNum, invoice.dateMillis)
        }

        val finalInvoice = invoice.copy(
            invoiceNumber = invNum,
            displayNumber = dispNum,
            createdByUserId = if (isEdit) invoice.createdByUserId else currentUser.id,
            createdByUserName = if (isEdit) invoice.createdByUserName else currentUser.fullName,
            deviceId = deviceName,
            updatedAt = now,
            syncStatus = if (isEdit) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_CREATE
        )

        db.withTransaction {
            if (isEdit) {
                db.invoiceDao().softDeleteItemsByInvoice(finalInvoice.id, now)
                db.inventoryDao().setMovementsActiveBySource(finalInvoice.id, false, now)
            }

            db.invoiceDao().upsertInvoice(finalInvoice)
            val preparedItems = sourceItems.map {
                it.copy(
                    invoiceId = finalInvoice.id,
                    companyId = finalInvoice.companyId,
                    updatedAt = now,
                    syncStatus = if (isEdit) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_CREATE,
                    isDeleted = false
                )
            }
            db.invoiceDao().upsertInvoiceItems(preparedItems)

            if (finalInvoice.type.stockSign != 0) {
                for (item in preparedItems) {
                    val delta = item.quantity * item.unitFactor * finalInvoice.type.stockSign
                    val movement = StockMovementEntity(
                        id = "${finalInvoice.id}_${item.id}",
                        companyId = finalInvoice.companyId,
                        productId = item.productId,
                        warehouseId = finalInvoice.warehouseId,
                        sourceDocId = finalInvoice.id,
                        deltaQuantity = delta,
                        isActive = !finalInvoice.isArchived,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_CREATE
                    )
                    db.inventoryDao().upsertStockMovement(movement)
                    val exactQty = db.inventoryDao().computeExactStock(item.productId)
                    db.inventoryDao().setExactStock(item.productId, exactQty, now)
                }
            }

            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = finalInvoice.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = if (isEdit) AuditAction.UPDATE else AuditAction.CREATE,
                    entityType = finalInvoice.type.arabicTitle,
                    entityId = finalInvoice.id,
                    summary = "${finalInvoice.type.arabicTitle} ($dispNum) بإجمالي ${finalInvoice.netTotal}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(finalInvoice.companyId)
    }

    suspend fun togglePostInvoice(currentUser: UserEntity, invoice: InvoiceEntity) {
        require(invoice.companyId == currentUser.companyId) { "لا يمكن ترحيل فاتورة لشركة أخرى" }
        PermissionManager.requirePermission(currentUser, { it.canPostOrArchiveInvoice }, "لا تملك صلاحية ترحيل الفواتير")
        val newPosted = !invoice.isPosted
        val now = System.currentTimeMillis()
        val settings = db.financeDao().getSettings(invoice.companyId)
        db.withTransaction {
            if (newPosted &&
                (invoice.type == InvoiceType.SALE_RETURN || invoice.type == InvoiceType.PURCHASE_RETURN)
            ) {
                validateReturnInvoice(invoice)
            }
            db.invoiceDao().upsertInvoice(
                invoice.copy(isPosted = newPosted, updatedAt = now, syncStatus = SyncStatus.PENDING_UPDATE)
            )
            syncAutomaticVoucher(
                currentUser, invoice, settings, shouldExist = newPosted && !invoice.isArchived, now = now
            )
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = invoice.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = AuditAction.POST,
                    entityType = invoice.type.arabicTitle,
                    entityId = invoice.id,
                    summary = "${if (newPosted) "ترحيل وقفل" else "إلغاء ترحيل"} فاتورة ${invoice.displayNumber}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(invoice.companyId)
    }

    private suspend fun syncAutomaticVoucher(
        user: UserEntity,
        invoice: InvoiceEntity,
        settings: AppSettingsEntity?,
        shouldExist: Boolean,
        now: Long
    ) {
        val (voucherId, voucherType, enabled) = when {
            invoice.type in setOf(InvoiceType.SALE, InvoiceType.FREE_SALE) &&
                invoice.paymentMethod == PaymentMethod.CASH ->
                Triple(
                    AutomaticVoucherIds.cashSaleReceipt(invoice.id),
                    VoucherType.RECEIPT,
                    settings?.autoReceiptForCashSales == true
                )
            invoice.type == InvoiceType.SALE_RETURN && invoice.paymentMethod == PaymentMethod.CASH ->
                Triple(
                    AutomaticVoucherIds.cashReturnPayment(invoice.id),
                    VoucherType.PAYMENT,
                    settings?.autoReverseSalesReturns == true
                )
            else -> return
        }
        val oldVoucher = db.voucherDao().getVoucherInCompany(invoice.companyId, voucherId)
        if (shouldExist && enabled) {
            val cashBoxId = invoice.cashBoxId
                ?: error("حدد الصندوق النقدي قبل الترحيل التلقائي")
            require(db.financeDao().getCashBoxInCompany(invoice.companyId, cashBoxId) != null) {
                "صندوق الفاتورة لا يتبع الشركة النشطة"
            }
            require(db.financeDao().getCurrencyInCompany(invoice.companyId, invoice.currencyId) != null) {
                "عملة الفاتورة لا تتبع الشركة النشطة"
            }
            val voucherNumber = oldVoucher?.voucherNumber ?: db.voucherDao().getNextVoucherNumber(invoice.companyId)
            db.voucherDao().upsertVoucher(
                VoucherEntity(
                    id = voucherId,
                    companyId = invoice.companyId,
                    voucherNumber = voucherNumber,
                    displayNumber = oldVoucher?.displayNumber.orEmpty(),
                    type = voucherType,
                    dateMillis = invoice.dateMillis,
                    accountId = invoice.accountId,
                    cashBoxId = cashBoxId,
                    currencyId = invoice.currencyId,
                    exchangeRate = invoice.exchangeRate,
                    amount = invoice.netTotal,
                    description = if (voucherType == VoucherType.RECEIPT) {
                        "قبض تلقائي عن فاتورة نقدية ${invoice.displayNumber}"
                    } else {
                        "رد نقدي تلقائي عن مردود ${invoice.displayNumber}"
                    },
                    createdByUserId = user.id,
                    createdByUserName = user.fullName,
                    deviceId = deviceName,
                    createdAt = oldVoucher?.createdAt ?: now,
                    updatedAt = now,
                    syncStatus = if (oldVoucher == null) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE,
                    isDeleted = false
                )
            )
        } else if (oldVoucher != null && !oldVoucher.isDeleted) {
            db.voucherDao().upsertVoucher(
                oldVoucher.copy(
                    isDeleted = true,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_DELETE
                )
            )
        }
    }

    private suspend fun validateReturnInvoice(invoice: InvoiceEntity) {
        val originalId = invoice.originalInvoiceId
            ?: throw IllegalArgumentException("يجب ربط المردود بالفاتورة الأصلية")
        val original = db.invoiceDao().getInvoiceById(originalId)
            ?.takeIf {
                it.companyId == invoice.companyId && it.accountId == invoice.accountId &&
                    it.isPosted && !it.isArchived && !it.isDeleted
            } ?: throw IllegalArgumentException("الفاتورة الأصلية غير متاحة أو لا تتبع الحساب المحدد")
        val expectedOriginalTypes = when (invoice.type) {
            InvoiceType.SALE_RETURN -> setOf(InvoiceType.SALE, InvoiceType.FREE_SALE)
            InvoiceType.PURCHASE_RETURN -> setOf(InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE)
            else -> emptySet()
        }

        require(original.type in expectedOriginalTypes) { "نوع الفاتورة الأصلية لا يطابق نوع المردود" }
        val account = db.accountDao().getAccountInCompany(invoice.companyId, invoice.accountId)
            ?: throw IllegalArgumentException("الحساب المحدد لا يتبع الشركة النشطة")
        val expectedCategory = if (invoice.type == InvoiceType.SALE_RETURN) {
            AccountCategory.CUSTOMERS
        } else {
            AccountCategory.SUPPLIERS
        }
        require(account.category == expectedCategory) { "تصنيف الحساب لا يطابق نوع المردود" }
        require(original.paymentMethod == invoice.paymentMethod) {
            "طريقة دفع المردود يجب أن تطابق طريقة دفع الفاتورة الأصلية"
        }
        require(original.currencyId == invoice.currencyId) { "عملة المردود يجب أن تطابق عملة الفاتورة الأصلية" }
        val previouslyReturned = db.invoiceDao().getAllInvoices(invoice.companyId)
            .filter {
                it.id != invoice.id && it.originalInvoiceId == original.id && it.isPosted &&
                    !it.isArchived && !it.isDeleted
            }
            .sumOf { it.netTotal }
        require(invoice.netTotal <= (original.netTotal - previouslyReturned + 0.000001)) {
            "قيمة المردود تتجاوز المبلغ المتبقي من الفاتورة الأصلية"
        }
    }

    private suspend fun prepareReturnItems(
        invoice: InvoiceEntity,
        returnItems: List<InvoiceItemEntity>
    ): List<InvoiceItemEntity> {
        require(returnItems.isNotEmpty()) { "يجب إضافة أصناف للمردود" }
        val originalId = invoice.originalInvoiceId
            ?: throw IllegalArgumentException("يجب ربط المردود بالفاتورة الأصلية")
        val originalItems = db.invoiceDao().getInvoiceItemsForCompany(invoice.companyId, originalId)
        val sourceTotals = originalItems.groupBy { it.productId }.mapValues { (_, rows) ->
            val baseQuantity = rows.sumOf { it.quantity * it.unitFactor }
            val baseCost = rows.sumOf { it.quantity * it.costPriceAtTime }
            baseQuantity to baseCost
        }
        val previousReturns = db.invoiceDao().getAllInvoices(invoice.companyId)
            .filter {
                it.id != invoice.id && it.originalInvoiceId == originalId && it.isPosted &&
                    !it.isArchived && !it.isDeleted
            }
            .flatMap { db.invoiceDao().getInvoiceItemsForCompany(invoice.companyId, it.id) }
            .groupBy { it.productId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity * it.unitFactor } }
        val requestedByProduct = returnItems.groupBy { it.productId }
        requestedByProduct.forEach { (productId, rows) ->
            val (sourceQuantity, _) = sourceTotals[productId]
                ?: throw IllegalArgumentException("الصنف ${rows.first().productName} غير موجود في الفاتورة الأصلية")
            val previouslyReturned = previousReturns[productId] ?: 0.0
            val requested = rows.sumOf { it.quantity * it.unitFactor }
            require(requested > 0.0 && requested + previouslyReturned <= sourceQuantity + 0.000001) {
                "كمية مردود ${rows.first().productName} تتجاوز الكمية المتبقية من الفاتورة الأصلية"
            }
        }
        return returnItems.map { item ->
            val (sourceQuantity, sourceCost) = sourceTotals.getValue(item.productId)
            val baseUnitCost = if (sourceQuantity > 0.0) sourceCost / sourceQuantity else 0.0
            item.copy(costPriceAtTime = baseUnitCost * item.unitFactor)
        }
    }

    /**
     * تسجيل دفعة (قبض) على فاتورة بيع آجلة.
     * ينشئ سند قبض مرتبطاً بالفاتورة (id بادئته invpay-) ليظهر في الصندوق النقدي
     * وكشوف الحساب، ويحدّث paidAmount حتى تنعكس الدفعة في أرصدة الحسابات والتقارير.
     * دوال حساب الأرصدة تتجاهل سندات invpay- لأن paidAmount يمثّلها.
     */
    suspend fun recordInvoicePayment(
        currentUser: UserEntity,
        invoice: InvoiceEntity,
        amount: Double,
        cashBoxId: String,
        note: String = ""
    ) {
        require(invoice.companyId == currentUser.companyId) { "لا يمكن تسجيل دفعة لفاتورة شركة أخرى" }
        PermissionManager.requirePermission(currentUser, { it.canAddVoucher }, "لا تملك صلاحية تسجيل سندات قبض")
        require(invoice.type == InvoiceType.SALE || invoice.type == InvoiceType.FREE_SALE) {
            "تسجيل الدفعات متاح لفواتير البيع فقط"
        }
        require(!invoice.isArchived && !invoice.isDeleted) { "الفاتورة مؤرشفة أو محذوفة" }
        val outstanding = invoice.netTotal - invoice.paidAmount
        require(amount > 0.0) { "أدخل مبلغ دفعة صحيحاً" }
        require(amount <= outstanding + 0.000001) { "مبلغ الدفعة يتجاوز المتبقي على الفاتورة" }
        require(PermissionManager.canAccessCashBox(currentUser, cashBoxId)) { "غير مصرح لك باستخدام هذا الصندوق النقدي" }
        require(db.financeDao().getCashBoxInCompany(invoice.companyId, cashBoxId) != null) {
            "الصندوق المحدد لا يتبع الشركة النشطة"
        }
        require(db.financeDao().getCurrencyInCompany(invoice.companyId, invoice.currencyId) != null) {
            "عملة الفاتورة لا تتبع الشركة النشطة"
        }

        val now = System.currentTimeMillis()
        val voucherId = "invpay-${invoice.id}-$now"
        val voucherNumber = db.voucherDao().getNextVoucherNumber(invoice.companyId)
        val invoiceLabel = invoice.displayNumber.ifBlank { "#${invoice.invoiceNumber}" }
        val description = buildString {
            append("دفعة عن فاتورة $invoiceLabel")
            if (note.isNotBlank()) append(" — ").append(note.trim())
        }

        db.withTransaction {
            db.voucherDao().upsertVoucher(
                VoucherEntity(
                    id = voucherId,
                    companyId = invoice.companyId,
                    voucherNumber = voucherNumber,
                    type = VoucherType.RECEIPT,
                    dateMillis = now,
                    accountId = invoice.accountId,
                    cashBoxId = cashBoxId,
                    currencyId = invoice.currencyId,
                    exchangeRate = invoice.exchangeRate,
                    amount = amount,
                    description = description,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.fullName,
                    deviceId = deviceName,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_CREATE,
                    isDeleted = false
                )
            )
            db.invoiceDao().upsertInvoice(
                invoice.copy(
                    paidAmount = invoice.paidAmount + amount,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_UPDATE
                )
            )
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = invoice.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = AuditAction.CREATE,
                    entityType = "دفعة فاتورة",
                    entityId = invoice.id,
                    summary = "تسجيل دفعة $amount على فاتورة $invoiceLabel"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(invoice.companyId)
    }

    suspend fun toggleArchiveInvoice(currentUser: UserEntity, invoice: InvoiceEntity) {
        PermissionManager.requirePermission(currentUser, { it.canPostOrArchiveInvoice }, "لا تملك صلاحية أرشفة الفواتير")
        val items = db.invoiceDao().getInvoiceItems(invoice.id)
        val newArchiveState = !invoice.isArchived
        val now = System.currentTimeMillis()

        db.withTransaction {
            db.invoiceDao().upsertInvoice(
                invoice.copy(isArchived = newArchiveState, updatedAt = now, syncStatus = SyncStatus.PENDING_UPDATE)
            )
            db.inventoryDao().setMovementsActiveBySource(invoice.id, !newArchiveState, now)
            syncAutomaticVoucher(
                currentUser,
                invoice,
                db.financeDao().getSettings(invoice.companyId),
                shouldExist = invoice.isPosted && !newArchiveState,
                now = now
            )
            for (item in items) {
                val exactQty = db.inventoryDao().computeExactStock(item.productId)
                db.inventoryDao().setExactStock(item.productId, exactQty, now)
            }
            db.userSyncDao().insertAuditLog(
                AuditLogEntity(
                    companyId = invoice.companyId,
                    userId = currentUser.id,
                    userName = currentUser.fullName,
                    deviceName = deviceName,
                    action = AuditAction.ARCHIVE,
                    entityType = invoice.type.arabicTitle,
                    entityId = invoice.id,
                    summary = "${if (newArchiveState) "أرشفة" else "إلغاء أرشفة"} فاتورة ${invoice.displayNumber}"
                )
            )
        }
        syncEngine.scheduleBackgroundSync(invoice.companyId)
    }
}
