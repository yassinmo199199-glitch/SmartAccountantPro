package com.smartaccountant.pro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import com.smartaccountant.pro.data.local.dao.*
import com.smartaccountant.pro.data.model.*

@Database(
    entities = [
        CompanyEntity::class,
        UserEntity::class,
        CurrencyEntity::class,
        CashBoxEntity::class,
        AccountEntity::class,
        VoucherEntity::class,
        WarehouseEntity::class,
        ProductEntity::class,
        StockMovementEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        RecurringTransactionEntity::class,
        AppSettingsEntity::class,
        AuditLogEntity::class,
        VoucherLineEntity::class,
        CostCenterEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun userSyncDao(): UserSyncDao
    abstract fun financeDao(): FinanceDao
    abstract fun accountDao(): AccountDao
    abstract fun voucherDao(): VoucherDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun reportsDao(): ReportsDao
    abstract fun costCenterDao(): CostCenterDao

    companion object {
        const val DB_NAME = "smart_accountant_pro_hardened.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_movements` (" +
                        "`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, " +
                        "`warehouseId` TEXT NOT NULL, `sourceDocId` TEXT NOT NULL, " +
                        "`deltaQuantity` REAL NOT NULL, `isActive` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN dueDateMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE invoices SET dueDateMillis = dateMillis WHERE dueDateMillis = 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN originalInvoiceId TEXT")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoReceiptForCashSales INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoReverseSalesReturns INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                        "`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`accountId` TEXT NOT NULL, `cashBoxId` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                        "`currencyId` TEXT NOT NULL, `voucherType` TEXT NOT NULL, `frequency` TEXT NOT NULL, " +
                        "`nextDueDateMillis` INTEGER NOT NULL, `dueDayOfMonth` INTEGER NOT NULL, " +
                        "`lastExecutedDateMillis` INTEGER NOT NULL, `occurrenceLimit` INTEGER, " +
                        "`executionCount` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                        "`createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`syncStatus` TEXT NOT NULL, `conflictJson` TEXT, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // إنشاء جدول خطوط السندات للقيود المركبة
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `voucher_lines` (" +
                        "`id` TEXT NOT NULL, `voucherId` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`debit` REAL NOT NULL, `credit` REAL NOT NULL, `currencyId` TEXT NOT NULL, " +
                        "`exchangeRate` REAL NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                // ترحيل السندات القديمة لتصبح مدخلات في جدول السطور (VoucherLineEntity)
                val cursor = db.query("SELECT * FROM vouchers WHERE isDeleted = 0")
                while (cursor.moveToNext()) {
                    val vid = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    val acc = cursor.getString(cursor.getColumnIndexOrThrow("accountId"))
                    val counter = cursor.getString(cursor.getColumnIndexOrThrow("counterAccountId"))
                    val cur = cursor.getString(cursor.getColumnIndexOrThrow("currencyId"))
                    val rate = cursor.getDouble(cursor.getColumnIndexOrThrow("exchangeRate"))
                    val amt = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"))
                    val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                    
                    val uuid1 = UUID.randomUUID().toString()
                    val uuid2 = UUID.randomUUID().toString()

                    when (type) {
                        "RECEIPT", "REMITTANCE_IN" -> {
                            // حساب الصندوق (الأساسي) مدين، الحساب الدائن (العميل) دائن
                            if (counter != null && counter.isNotBlank()) {
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid1', '$vid', '$counter', $amt, 0.0, '$cur', $rate, '$desc')")
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid2', '$vid', '$acc', 0.0, $amt, '$cur', $rate, '$desc')")
                            }
                        }
                        "PAYMENT", "REMITTANCE_OUT" -> {
                            // حساب المورد مدين، الصندوق دائن
                            if (counter != null && counter.isNotBlank()) {
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid1', '$vid', '$acc', $amt, 0.0, '$cur', $rate, '$desc')")
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid2', '$vid', '$counter', 0.0, $amt, '$cur', $rate, '$desc')")
                            }
                        }
                        "SIMPLE_JOURNAL", "ADJUSTMENT", "EXPRESS" -> {
                            // accountId مدين، counterAccountId دائن
                            if (counter != null && counter.isNotBlank()) {
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid1', '$vid', '$acc', $amt, 0.0, '$cur', $rate, '$desc')")
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid2', '$vid', '$counter', 0.0, $amt, '$cur', $rate, '$desc')")
                            }
                        }
                        "OPENING_BALANCE" -> {
                            if (amt >= 0) {
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid1', '$vid', '$acc', $amt, 0.0, '$cur', $rate, '$desc')")
                            } else {
                                db.execSQL("INSERT INTO voucher_lines VALUES ('$uuid1', '$vid', '$acc', 0.0, ${kotlin.math.abs(amt)}, '$cur', $rate, '$desc')")
                            }
                        }
                    }
                }
                cursor.close()
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // إضافة parentId لدعم شجرة الحسابات
                db.execSQL("ALTER TABLE accounts ADD COLUMN parentId TEXT DEFAULT NULL")
                // إضافة isArchived لدعم إغلاق السنة المالية
                db.execSQL("ALTER TABLE vouchers ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // إنشاء جدول مراكز التكلفة
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cost_centers` (" +
                        "`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`code` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                        "`syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                // إضافة مركز التكلفة لأسطر القيد
                db.execSQL("ALTER TABLE voucher_lines ADD COLUMN costCenterId TEXT DEFAULT NULL")
                // إضافة خيار التفعيل للإعدادات
                db.execSQL("ALTER TABLE app_settings ADD COLUMN enableCostCenters INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    // مخطط multi-tenant تغيّر جذرياً (companyId في كل الجداول + companies/users)
                    // بدون migration كامل — عند عدم التطابق نمسح القاعدة المحلية وتُعاد مزامنتها من السحابة
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
