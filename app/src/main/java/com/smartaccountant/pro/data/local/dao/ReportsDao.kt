package com.smartaccountant.pro.data.local.dao  
  
import androidx.room.Dao  
import androidx.room.Embedded
import androidx.room.Query
import com.smartaccountant.pro.data.model.VoucherEntity

data class AccountAggregatedBalance(  
    val accountId: String,  
    val totalDebit: Double,  
    val totalCredit: Double  
)  
  
@Dao  
interface ReportsDao {  
    @Query("""
        SELECT l.accountId, 
               COALESCE(SUM(l.debit * l.exchangeRate), 0.0) as totalDebit, 
               COALESCE(SUM(l.credit * l.exchangeRate), 0.0) as totalCredit 
        FROM voucher_lines l 
        INNER JOIN vouchers v ON l.voucherId = v.id 
        WHERE v.companyId = :companyId AND v.isDeleted = 0 
          AND (:fromMillis IS NULL OR v.dateMillis >= :fromMillis) 
          AND (:toMillis IS NULL OR v.dateMillis <= :toMillis) 
          AND (:excludeOpening = 0 OR v.type != 'OPENING_BALANCE') 
        GROUP BY l.accountId 
    """)
    suspend fun getVoucherBalances(companyId: String, fromMillis: Long?, toMillis: Long?, excludeOpening: Boolean = false): List<AccountAggregatedBalance>  

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isArchived = 0 AND isDeleted = 0")
    suspend fun getActiveVouchers(companyId: String): List<VoucherEntity>
    
    @Query("UPDATE vouchers SET isArchived = 1, updatedAt = :now, syncStatus = 'PENDING_UPDATE' WHERE companyId = :companyId AND dateMillis <= :cutoffMillis AND isDeleted = 0")
    suspend fun archiveVouchersBefore(companyId: String, cutoffMillis: Long, now: Long)
    
    @Query("UPDATE invoices SET isArchived = 1, updatedAt = :now, syncStatus = 'PENDING_UPDATE' WHERE companyId = :companyId AND dateMillis <= :cutoffMillis AND isDeleted = 0")
    suspend fun archiveInvoicesBefore(companyId: String, cutoffMillis: Long, now: Long)
    
    @Query("""
        SELECT l.accountId, 
               COALESCE(SUM(l.debit * l.exchangeRate), 0.0) as totalDebit, 
               COALESCE(SUM(l.credit * l.exchangeRate), 0.0) as totalCredit 
        FROM voucher_lines l 
        INNER JOIN vouchers v ON l.voucherId = v.id 
        WHERE v.companyId = :companyId AND v.isDeleted = 0 AND l.accountId = :accountId
          AND (:maxDateMillis IS NULL OR v.dateMillis < :maxDateMillis)
        GROUP BY l.accountId
    """)
    suspend fun getVoucherBalanceForAccount(companyId: String, accountId: String, maxDateMillis: Long?): AccountAggregatedBalance?
    
    // إضافة استعلام مخصص لكشف الحساب يجلب الأسطر الخاصة بالحساب مع رأس السند
    @Query("""
        SELECT v.*, l.debit, l.credit, l.note as lineNote, l.accountId as lineAccountId
        FROM vouchers v
        INNER JOIN voucher_lines l ON v.id = l.voucherId
        WHERE v.companyId = :companyId AND v.isDeleted = 0 AND l.accountId = :accountId
        ORDER BY v.dateMillis ASC, v.voucherNumber ASC
    """)
    suspend fun getAccountStatementLines(companyId: String, accountId: String): List<StatementLineRecord>

    // استعلام لجلب جميع السطور لدفتر اليومية العامة
    @Query("""
        SELECT v.*, l.debit, l.credit, l.note as lineNote, l.accountId as lineAccountId
        FROM vouchers v
        INNER JOIN voucher_lines l ON v.id = l.voucherId
        WHERE v.companyId = :companyId AND v.isDeleted = 0
        ORDER BY v.dateMillis ASC, v.voucherNumber ASC, l.debit DESC
    """)
    suspend fun getAllJournalLines(companyId: String): List<StatementLineRecord>
}

// كلاس جديد لحمل بيانات كشف الحساب واليومية العامة
data class StatementLineRecord(
    @Embedded val voucher: VoucherEntity,
    val debit: Double,
    val credit: Double,
    val lineNote: String,
    val lineAccountId: String = ""
) {
    val accountId: String
        get() = lineAccountId.ifBlank { voucher.accountId }
}
