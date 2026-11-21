package com.smartaccountant.pro

import android.content.Context
import com.smartaccountant.pro.core.session.SessionStore
import com.smartaccountant.pro.core.sync.SyncEngine
import com.smartaccountant.pro.core.utils.DeviceIdentity
import com.smartaccountant.pro.core.utils.DocumentNumberGenerator
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.local.DatabaseSeeder
import com.smartaccountant.pro.data.remote.AppUpdateManager
import com.smartaccountant.pro.data.remote.CloudMediaStorage
import com.smartaccountant.pro.data.remote.CompanyBootstrapService
import com.smartaccountant.pro.data.remote.FirebaseAuthService
import com.smartaccountant.pro.data.remote.FirestoreSyncService
import com.smartaccountant.pro.domain.repository.AccountingRepository
import com.smartaccountant.pro.domain.repository.AdminRepository
import com.smartaccountant.pro.domain.repository.InvoiceRepository
import com.smartaccountant.pro.domain.repository.FinancialReportsRepository
import com.smartaccountant.pro.domain.repository.PartyRepository
import com.smartaccountant.pro.domain.repository.RecurringTransactionsRepository
import com.smartaccountant.pro.domain.repository.ProfitabilityRepository
import com.smartaccountant.pro.services.BluetoothPrinterService
import com.smartaccountant.pro.services.ExcelImportExportService
import com.smartaccountant.pro.services.PdfStatementGenerator
import com.smartaccountant.pro.services.VoucherAttachmentService

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val deviceName: String = DeviceIdentity.displayName(appContext)
    val db: AppDatabase = AppDatabase.getInstance(context)
    val seeder: DatabaseSeeder = DatabaseSeeder(db)
    val docNumberGenerator: DocumentNumberGenerator = DocumentNumberGenerator(context)
    val firebaseAuthService: FirebaseAuthService = FirebaseAuthService(context)
    val firestoreSyncService: FirestoreSyncService = FirestoreSyncService(context, firebaseAuthService)
    val appUpdateManager: AppUpdateManager = AppUpdateManager(appContext, firestoreSyncService)
    val cloudMediaStorage: CloudMediaStorage = CloudMediaStorage(context)
    val syncEngine: SyncEngine = SyncEngine(context, db, firebaseAuthService, firestoreSyncService, cloudMediaStorage, deviceName)
    val sessionStore: SessionStore = SessionStore(context)
    val companyBootstrapService: CompanyBootstrapService = CompanyBootstrapService(db, firestoreSyncService)

    val accountingRepository: AccountingRepository = AccountingRepository(db, syncEngine, docNumberGenerator, deviceName)
    val invoiceRepository: InvoiceRepository = InvoiceRepository(db, syncEngine, docNumberGenerator, deviceName)
    val adminRepository: AdminRepository = AdminRepository(db, syncEngine, deviceName)
    val financialReportsRepository: FinancialReportsRepository = FinancialReportsRepository(db)
    val partyRepository: PartyRepository = PartyRepository(db)
    val recurringTransactionsRepository: RecurringTransactionsRepository =
        RecurringTransactionsRepository(db, syncEngine, deviceName)
    val profitabilityRepository: ProfitabilityRepository = ProfitabilityRepository(db)

    val pdfGenerator: PdfStatementGenerator = PdfStatementGenerator(context)
    val voucherAttachmentService: VoucherAttachmentService = VoucherAttachmentService(context)
    val bluetoothPrinter: BluetoothPrinterService = BluetoothPrinterService(context)
    val excelService: ExcelImportExportService = ExcelImportExportService(context)
}
