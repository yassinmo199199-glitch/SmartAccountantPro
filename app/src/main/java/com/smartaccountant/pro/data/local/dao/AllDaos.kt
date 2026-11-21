package com.smartaccountant.pro.data.local.dao

import androidx.room.*
import com.smartaccountant.pro.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CompanyEntity?

    @Upsert
    suspend fun upsert(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingCompanies(): List<CompanyEntity>
}

@Dao
interface UserSyncDao {
    @Query("SELECT * FROM users WHERE companyId = :companyId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeUsers(companyId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE companyId = :companyId AND (email = :identifier OR phone = :identifier) AND isDeleted = 0 LIMIT 1")
    suspend fun findByIdentifier(companyId: String, identifier: String): UserEntity?

    @Query("SELECT * FROM users WHERE (email = :identifier OR phone = :identifier) AND isDeleted = 0 ORDER BY createdAt ASC LIMIT 1")
    suspend fun findByIdentifierGlobal(identifier: String): UserEntity?

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingUsers(companyId: String): List<UserEntity>

    @Query("SELECT * FROM audit_logs WHERE companyId = :companyId ORDER BY timestamp DESC LIMIT 300")
    fun observeAuditLogs(companyId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingAuditLogs(companyId: String): List<AuditLogEntity>
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM currencies WHERE companyId = :companyId AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeCurrencies(companyId: String): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE id = :id LIMIT 1")
    suspend fun getCurrencyById(id: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE companyId = :companyId AND isDeleted = 0")
    suspend fun getCurrencies(companyId: String): List<CurrencyEntity>

    @Query("SELECT * FROM currencies WHERE companyId = :companyId AND id = :currencyId AND isDeleted = 0 LIMIT 1")
    suspend fun getCurrencyInCompany(companyId: String, currencyId: String): CurrencyEntity?

    @Upsert
    suspend fun upsertCurrency(currency: CurrencyEntity)

    @Query("SELECT * FROM currencies WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingCurrencies(companyId: String): List<CurrencyEntity>

    @Query("SELECT * FROM cash_boxes WHERE companyId = :companyId AND isDeleted = 0 ORDER BY isDefault DESC, name ASC")
    fun observeCashBoxes(companyId: String): Flow<List<CashBoxEntity>>

    @Query("SELECT * FROM cash_boxes WHERE id = :id LIMIT 1")
    suspend fun getCashBoxById(id: String): CashBoxEntity?

    @Query("SELECT * FROM cash_boxes WHERE companyId = :companyId AND id = :cashBoxId AND isDeleted = 0 LIMIT 1")
    suspend fun getCashBoxInCompany(companyId: String, cashBoxId: String): CashBoxEntity?

    @Upsert
    suspend fun upsertCashBox(box: CashBoxEntity)

    @Query("SELECT * FROM cash_boxes WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingCashBoxes(companyId: String): List<CashBoxEntity>

    @Query("SELECT * FROM app_settings WHERE companyId = :companyId LIMIT 1")
    fun observeSettings(companyId: String): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE companyId = :companyId LIMIT 1")
    suspend fun getSettings(companyId: String): AppSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: AppSettingsEntity)
}

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions WHERE companyId = :companyId AND isDeleted = 0 ORDER BY nextDueDateMillis ASC")
    fun observeRecurringTransactions(companyId: String): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE companyId = :companyId AND isDeleted = 0 ORDER BY nextDueDateMillis ASC")
    suspend fun getRecurringTransactions(companyId: String): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE companyId = :companyId AND isActive = 1 AND isDeleted = 0 AND nextDueDateMillis <= :asOfMillis ORDER BY nextDueDateMillis ASC")
    suspend fun getDueRecurringTransactions(companyId: String, asOfMillis: Long): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingRecurringTransactions(companyId: String): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE companyId = :companyId AND id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getRecurringTransaction(companyId: String, id: String): RecurringTransactionEntity?

    @Upsert
    suspend fun upsertRecurringTransaction(transaction: RecurringTransactionEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")
    fun observeAccounts(companyId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE companyId = :companyId AND isDeleted = 0")
    suspend fun getAllActiveAccounts(companyId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE companyId = :companyId AND id = :accountId AND isDeleted = 0 LIMIT 1")
    suspend fun getPartyAccount(companyId: String, accountId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE companyId = :companyId AND id = :accountId AND isDeleted = 0 LIMIT 1")
    suspend fun getAccountInCompany(companyId: String, accountId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Upsert
    suspend fun upsertAccount(account: AccountEntity)

    @Upsert
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingAccounts(companyId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE companyId = :companyId AND isDeleted = 0 AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%') ORDER BY name ASC LIMIT 20")
    suspend fun searchAccounts(companyId: String, query: String): List<AccountEntity>
}

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 ORDER BY dateMillis DESC, voucherNumber DESC")
    fun observeVouchers(companyId: String): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers WHERE id = :id LIMIT 1")
    suspend fun getVoucherById(id: String): VoucherEntity?

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND id = :id LIMIT 1")
    suspend fun getVoucherInCompany(companyId: String, id: String): VoucherEntity?

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND (accountId = :accountId OR counterAccountId = :accountId) AND isDeleted = 0 ORDER BY dateMillis ASC")
    fun observeAccountVouchers(companyId: String, accountId: String): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND (accountId = :accountId OR counterAccountId = :accountId) AND isDeleted = 0 ORDER BY dateMillis ASC")
    suspend fun getAccountVouchers(companyId: String, accountId: String): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND (accountId = :accountId OR counterAccountId = :accountId) AND isDeleted = 0 ORDER BY dateMillis ASC, voucherNumber ASC")
    suspend fun getPartyStatement(companyId: String, accountId: String): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND accountId = :accountId AND isDeleted = 0 AND type IN ('RECEIPT', 'PAYMENT', 'REMITTANCE_IN', 'REMITTANCE_OUT') ORDER BY dateMillis DESC, voucherNumber DESC")
    suspend fun getPartyPayments(companyId: String, accountId: String): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 AND dateMillis <= :asOfMillis AND type IN ('RECEIPT', 'PAYMENT', 'REMITTANCE_IN', 'REMITTANCE_OUT') ORDER BY dateMillis ASC, voucherNumber ASC")
    suspend fun getCollectionPayments(companyId: String, asOfMillis: Long): List<VoucherEntity>

    @Query("SELECT COALESCE(MAX(voucherNumber), 0) + 1 FROM vouchers WHERE companyId = :companyId")
    suspend fun getNextVoucherNumber(companyId: String): Long

    @Upsert
    suspend fun upsertVoucher(voucher: VoucherEntity)

    @Upsert
    suspend fun upsertVoucherLines(lines: List<VoucherLineEntity>)

    @Query("DELETE FROM voucher_lines WHERE voucherId = :voucherId")
    suspend fun deleteVoucherLines(voucherId: String)

    @Query("SELECT * FROM voucher_lines WHERE voucherId = :voucherId")
    suspend fun getVoucherLines(voucherId: String): List<VoucherLineEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0")
    suspend fun getAllVouchers(companyId: String): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 AND dateMillis BETWEEN :fromMillis AND :toMillis")
    suspend fun getVouchersForReportPeriod(companyId: String, fromMillis: Long, toMillis: Long): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 AND dateMillis <= :asOfMillis")
    suspend fun getVouchersThroughDate(companyId: String, asOfMillis: Long): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingVouchers(companyId: String): List<VoucherEntity>

    @Query("UPDATE vouchers SET isDeleted = 1, updatedAt = :now, syncStatus = 'PENDING_DELETE' WHERE companyId = :companyId AND dateMillis < :cutoffMillis AND isDeleted = 0")
    suspend fun softDeleteBefore(companyId: String, cutoffMillis: Long, now: Long)

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 AND (displayNumber LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR senderName LIKE '%' || :query || '%' OR receiverName LIKE '%' || :query || '%' OR remittanceNumber LIKE '%' || :query || '%') ORDER BY dateMillis DESC, voucherNumber DESC LIMIT 20")
    suspend fun searchVouchers(companyId: String, query: String): List<VoucherEntity>
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM warehouses WHERE companyId = :companyId AND isDeleted = 0 ORDER BY isDefault DESC, name ASC")
    fun observeWarehouses(companyId: String): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses WHERE id = :id LIMIT 1")
    suspend fun getWarehouseById(id: String): WarehouseEntity?

    @Upsert
    suspend fun upsertWarehouse(warehouse: WarehouseEntity)

    @Query("SELECT * FROM warehouses WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingWarehouses(companyId: String): List<WarehouseEntity>

    @Query("SELECT * FROM products WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")
    fun observeProducts(companyId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE companyId = :companyId AND barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun findByBarcode(companyId: String, barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Upsert
    suspend fun upsertProduct(product: ProductEntity)

    @Upsert
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingProducts(companyId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE companyId = :companyId AND isDeleted = 0 AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name ASC LIMIT 20")
    suspend fun searchProducts(companyId: String, query: String): List<ProductEntity>

    // سجل الحركات المخزنية الحتمي لإعادة احتساب المخزون بدقة عبر الأجهزة
    @Upsert
    suspend fun upsertStockMovement(movement: StockMovementEntity)

    @Query("UPDATE stock_movements SET isActive = :isActive, updatedAt = :now, syncStatus = 'PENDING_UPDATE' WHERE sourceDocId = :sourceDocId")
    suspend fun setMovementsActiveBySource(sourceDocId: String, isActive: Boolean, now: Long)

    @Query("SELECT COALESCE(SUM(deltaQuantity), 0.0) FROM stock_movements WHERE productId = :productId AND isActive = 1")
    suspend fun computeExactStock(productId: String): Double

    @Query("UPDATE products SET stockQuantity = :exactQty, updatedAt = :now WHERE id = :productId")
    suspend fun setExactStock(productId: String, exactQty: Double, now: Long)

    @Query("SELECT * FROM stock_movements WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingStockMovements(companyId: String): List<StockMovementEntity>
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 ORDER BY dateMillis DESC, invoiceNumber DESC")
    fun observeInvoices(companyId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND accountId = :accountId AND isDeleted = 0 AND isArchived = 0 AND isPosted = 1 ORDER BY dateMillis DESC, invoiceNumber DESC")
    suspend fun getPostedInvoicesForCompanyAccount(companyId: String, accountId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND accountId = :accountId AND isDeleted = 0 AND isArchived = 0 ORDER BY dateMillis ASC")
    fun observeAccountInvoices(companyId: String, accountId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND accountId = :accountId AND isDeleted = 0 AND isArchived = 0 ORDER BY dateMillis ASC")
    suspend fun getAccountInvoices(companyId: String, accountId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND accountId = :accountId AND isDeleted = 0 ORDER BY dateMillis ASC")
    suspend fun getAllAccountInvoices(companyId: String, accountId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND accountId = :accountId AND isDeleted = 0 AND isArchived = 0 ORDER BY dateMillis DESC, invoiceNumber DESC")
    suspend fun getPartyInvoices(companyId: String, accountId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId AND isDeleted = 0")
    suspend fun getInvoiceItems(invoiceId: String): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_items WHERE companyId = :companyId AND invoiceId = :invoiceId AND isDeleted = 0")
    suspend fun getInvoiceItemsForCompany(companyId: String, invoiceId: String): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_items WHERE id = :itemId LIMIT 1")
    suspend fun getInvoiceItemById(itemId: String): InvoiceItemEntity?

    @Query("SELECT COALESCE(MAX(invoiceNumber), 0) + 1 FROM invoices WHERE companyId = :companyId")
    suspend fun getNextInvoiceNumber(companyId: String): Long

    @Upsert
    suspend fun upsertInvoice(invoice: InvoiceEntity)

    @Upsert
    suspend fun upsertInvoiceItems(items: List<InvoiceItemEntity>)

    @Query("UPDATE invoice_items SET isDeleted = 1, updatedAt = :now, syncStatus = 'PENDING_DELETE' WHERE invoiceId = :invoiceId")
    suspend fun softDeleteItemsByInvoice(invoiceId: String, now: Long)

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND isArchived = 0")
    suspend fun getAllInvoices(companyId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND isArchived = 0 AND isPosted = 1 AND dateMillis BETWEEN :fromMillis AND :toMillis")
    suspend fun getPostedInvoicesForReportPeriod(companyId: String, fromMillis: Long, toMillis: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND isArchived = 0 AND isPosted = 1 AND dateMillis <= :asOfMillis")
    suspend fun getPostedInvoicesThroughDate(companyId: String, asOfMillis: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND isArchived = 0 AND isPosted = 1 AND paymentMethod = 'CREDIT' AND dateMillis <= :asOfMillis AND type IN ('SALE', 'FREE_SALE', 'PURCHASE', 'FREE_PURCHASE')")
    suspend fun getPostedCreditInvoicesForAging(companyId: String, asOfMillis: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND isArchived = 0 AND isPosted = 1 AND paymentMethod = 'CREDIT' AND dueDateMillis < :todayStartMillis AND type IN ('SALE', 'FREE_SALE', 'PURCHASE', 'FREE_PURCHASE') ORDER BY dueDateMillis ASC, invoiceNumber ASC")
    suspend fun getOverdueInvoices(companyId: String, todayStartMillis: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND isArchived = 0 AND isPosted = 1 AND paymentMethod = 'CREDIT' AND dateMillis <= :asOfMillis AND type IN ('SALE', 'FREE_SALE', 'PURCHASE', 'FREE_PURCHASE') ORDER BY dateMillis ASC, invoiceNumber ASC")
    suspend fun getPostedCreditInvoicesThroughDate(companyId: String, asOfMillis: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingInvoices(companyId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoice_items WHERE companyId = :companyId AND syncStatus != 'SYNCED'")
    suspend fun getPendingInvoiceItems(companyId: String): List<InvoiceItemEntity>

    @Query("UPDATE invoices SET isDeleted = 1, updatedAt = :now, syncStatus = 'PENDING_DELETE' WHERE companyId = :companyId AND dateMillis < :cutoffMillis AND isDeleted = 0")
    suspend fun softDeleteInvoicesBefore(companyId: String, cutoffMillis: Long, now: Long)

    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND isDeleted = 0 AND (displayNumber LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR CAST(invoiceNumber AS TEXT) LIKE '%' || :query || '%') ORDER BY dateMillis DESC, invoiceNumber DESC LIMIT 20")
    suspend fun searchInvoices(companyId: String, query: String): List<InvoiceEntity>
}
