package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.domain.repository.AccountingRepository
import com.smartaccountant.pro.domain.repository.AgingAccountSummary
import com.smartaccountant.pro.domain.repository.AgingBucket
import com.smartaccountant.pro.domain.repository.AgingReport
import com.smartaccountant.pro.domain.repository.BalanceSheetReport
import com.smartaccountant.pro.domain.repository.FinancialReportLine
import com.smartaccountant.pro.domain.repository.ProfitLossReport
import com.smartaccountant.pro.domain.repository.ProfitabilityReport
import com.smartaccountant.pro.domain.repository.TrialBalanceLine
import com.smartaccountant.pro.domain.repository.TrialBalanceReport
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.components.SyncStatusDot
import com.smartaccountant.pro.ui.components.TableHeaderRow
import com.smartaccountant.pro.ui.components.UiErrorMessages
import com.smartaccountant.pro.ui.navigation.Screen
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private enum class ReportPeriod(val label: String) { TODAY("اليوم"), WEEK("هذا الأسبوع"), MONTH("هذا الشهر"), ALL("كل الفترات") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(mainViewModel: MainViewModel, navController: androidx.navigation.NavController, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val vouchers by mainViewModel.container.db.voucherDao().observeVouchers(companyId).collectAsState(initial = emptyList())
    val invoices by mainViewModel.container.db.invoiceDao().observeInvoices(companyId).collectAsState(initial = emptyList())
    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())
    val products by mainViewModel.container.db.inventoryDao().observeProducts(companyId).collectAsState(initial = emptyList())
    var showClosePeriodConfirm by remember { mutableStateOf(false) }
    var isExportingReport by remember { mutableStateOf(false) }
    var voucherPendingDelete by remember { mutableStateOf<VoucherEntity?>(null) }

    var period by remember { mutableStateOf(ReportPeriod.MONTH) }
    var selectedTab by remember { mutableStateOf(0) }
    var voucherSearch by remember { mutableStateOf("") }
    var voucherTypeFilter by remember { mutableStateOf<VoucherType?>(null) }
    var voucherFromMillis by remember { mutableStateOf(startOfCurrentMonth()) }
    var voucherToMillis by remember { mutableStateOf(endOfToday()) }
    var reportFromMillis by remember { mutableStateOf(startOfCurrentMonth()) }
    var reportToMillis by remember { mutableStateOf(endOfToday()) }
    var balanceSheetDateMillis by remember { mutableStateOf(endOfToday()) }
    var agingDateMillis by remember { mutableStateOf(endOfToday()) }
    var agingCategory by remember { mutableStateOf(AccountCategory.CUSTOMERS) }
    var agingBucketFilter by remember { mutableStateOf<AgingBucket?>(null) }
    var profitLossReport by remember { mutableStateOf<ProfitLossReport?>(null) }
    var balanceSheetReport by remember { mutableStateOf<BalanceSheetReport?>(null) }
    var agingReport by remember { mutableStateOf<AgingReport?>(null) }
    var profitabilityReport by remember { mutableStateOf<ProfitabilityReport?>(null) }
    var financialReportLoading by remember { mutableStateOf(false) }
    var financialReportError by remember { mutableStateOf<String?>(null) }
    val permissions = PermissionManager.permissionsOf(user)
    val canViewFinancialReports = permissions.canViewFinancialReports
    val canViewProfitability = permissions.canViewProfitability
    var trialBalanceReport by remember { mutableStateOf<TrialBalanceReport?>(null) }
    val visibleTabs = buildList {
        if (canViewFinancialReports) {
            add(0 to "كشف الحساب والتفاصيل")
            add(1 to "ميزان المراجعة (6 أعمدة)")
            add(2 to "السندات والقيود")
            add(3 to "الفواتير والمبيعات")
            add(4 to "أرصدة الحسابات")
            add(5 to "قائمة الدخل")
            add(6 to "الميزانية العمومية")
            add(7 to "أعمار الديون")
        }
        if (canViewProfitability) add(8 to "ربحية الأصناف")
    }

    LaunchedEffect(user?.id, canViewFinancialReports, canViewProfitability) {
        if (!canViewFinancialReports && canViewProfitability) selectedTab = 8
    }

    LaunchedEffect(
        selectedTab, user?.id, companyId, reportFromMillis, reportToMillis,
        balanceSheetDateMillis, agingDateMillis, agingCategory
    ) {
        val currentUser = user ?: return@LaunchedEffect
        if (companyId.isBlank()) return@LaunchedEffect
        financialReportError = null
        when (selectedTab) {
            1 -> {
                financialReportLoading = true
                try {
                    trialBalanceReport = mainViewModel.container.financialReportsRepository.getTrialBalance(
                        currentUser, companyId, reportFromMillis, reportToMillis
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    financialReportError = UiErrorMessages.fromThrowable(e, "تعذر تحميل ميزان المراجعة")
                } finally {
                    financialReportLoading = false
                }
            }
            5 -> {
                financialReportLoading = true
                try {
                    profitLossReport = mainViewModel.container.financialReportsRepository.getProfitLoss(
                        currentUser, companyId, reportFromMillis, reportToMillis
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    financialReportError = UiErrorMessages.fromThrowable(e, "تعذر تحميل قائمة الدخل")
                } finally {
                    financialReportLoading = false
                }
            }
            6 -> {
                financialReportLoading = true
                try {
                    balanceSheetReport = mainViewModel.container.financialReportsRepository.getBalanceSheet(
                        currentUser, companyId, balanceSheetDateMillis
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    financialReportError = UiErrorMessages.fromThrowable(e, "تعذر تحميل الميزانية العمومية")
                } finally {
                    financialReportLoading = false
                }
            }
            7 -> {
                financialReportLoading = true
                try {
                    agingReport = mainViewModel.container.financialReportsRepository.getAging(
                        currentUser, companyId, agingDateMillis, agingCategory
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    financialReportError = UiErrorMessages.fromThrowable(e, "تعذر تحميل أعمار الديون")
                } finally {
                    financialReportLoading = false
                }
            }
            8 -> {
                financialReportLoading = true
                try {
                    profitabilityReport = mainViewModel.container.profitabilityRepository.getProfitability(
                        currentUser, companyId
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    financialReportError = UiErrorMessages.fromThrowable(e, "تعذر تحميل ربحية الأصناف")
                } finally {
                    financialReportLoading = false
                }
            }
        }
    }

    val periodStart = remember(period) {
        val cal = Calendar.getInstance()
        when (period) {
            ReportPeriod.TODAY -> DateUtils.startOfToday()
            ReportPeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.ALL -> 0L
        }
    }

    val periodVouchers = remember(vouchers, periodStart) { vouchers.filter { it.dateMillis >= periodStart && !it.isDeleted } }
    val periodInvoices = remember(invoices, periodStart) { invoices.filter { it.dateMillis >= periodStart && !it.isArchived && !it.isDeleted } }

    val totalReceipts = periodVouchers.filter { it.type == VoucherType.RECEIPT }.sumOf { it.amount * it.exchangeRate }
    val totalPayments = periodVouchers.filter { it.type == VoucherType.PAYMENT }.sumOf { it.amount * it.exchangeRate }
    val netFlow = totalReceipts - totalPayments
    // الإجماليات تشمل الفواتير المرحلة فقط (المسودات لا تُحتسب مبيعات/مشتريات فعلية)
    val postedPeriodInvoices = remember(periodInvoices) { periodInvoices.filter { it.isPosted } }
    val totalSales = postedPeriodInvoices.filter { it.type == InvoiceType.SALE }.sumOf { it.netTotal * it.exchangeRate }
    val totalPurchases = postedPeriodInvoices.filter { it.type == InvoiceType.PURCHASE }.sumOf { it.netTotal * it.exchangeRate }
    val stockValuation = products.sumOf { it.stockQuantity * it.purchasePrice1 }

    val filteredVouchers = remember(periodVouchers, voucherSearch, voucherTypeFilter, accounts, voucherFromMillis, voucherToMillis) {
        val query = voucherSearch.trim()
        periodVouchers.filter { v ->
            v.dateMillis >= voucherFromMillis && v.dateMillis <= voucherToMillis &&
                (voucherTypeFilter == null || v.type == voucherTypeFilter) &&
                (query.isEmpty() || v.description.contains(query, true) ||
                    accounts.find { it.id == v.accountId }?.name?.contains(query, true) == true)
        }
    }

    // أرصدة الحسابات تُحسب بنفس منطق AccountsScreen/DashboardScreen (كل أنواع السندات + الفواتير + الأرصدة الافتتاحية)
    val accountBalances by produceState<List<Triple<AccountEntity, Double, String>>>(
        initialValue = emptyList(),
        accounts, vouchers, invoices, companyId, periodStart
    ) {
        value = if (companyId.isBlank()) emptyList() else {
            val netBalances = mainViewModel.container.accountingRepository.calculateAllAccountBalances(
                companyId = companyId,
                fromMillis = periodStart.takeIf { it > 0L },
                toMillis = null
            )
            accounts.map { acc -> Triple(acc, netBalances[acc.id] ?: 0.0, acc.category.arabicTitle) }
                .sortedByDescending { kotlin.math.abs(it.second) }
        }
    }

    fun deleteVoucher(voucher: VoucherEntity) {
        val currentUser = user ?: return
        scope.launch {
            try {
                mainViewModel.container.accountingRepository.deleteVoucher(currentUser, voucher)
                mainViewModel.showMessage("تم حذف السند بنجاح")
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر حذف السند")
            }
        }
    }

    fun exportTrialBalance() {
        val activeCompany = session.activeCompany ?: return
        isExportingReport = true
        scope.launch {
            try {
                val repo = mainViewModel.container.accountingRepository
                val summaries = accounts.mapNotNull { repo.calculateAccountBalance(companyId, it.id) }
                    .filter { it.totalDebit > 0.0 || it.totalCredit > 0.0 }
                    .sortedBy { it.account.name }
                val rows = summaries.map { s ->
                    listOf(
                        s.account.name,
                        s.account.category.arabicTitle,
                        CurrencyFormatter.format(s.totalDebit),
                        CurrencyFormatter.format(s.totalCredit),
                        CurrencyFormatter.format(s.netBalance)
                    )
                }
                val totalDebit = summaries.sumOf { it.totalDebit }
                val totalCredit = summaries.sumOf { it.totalCredit }
                val file = withContext(Dispatchers.IO) {
                    mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                        company = activeCompany,
                        reportTitle = "ميزان المراجعة",
                        periodLabel = period.label,
                        headers = listOf("الحساب", "التصنيف", "إجمالي المدين", "إجمالي الدائن", "الصافي"),
                        rows = rows,
                        footerRows = listOf(
                            listOf(
                                "الإجمالي", "",
                                CurrencyFormatter.format(totalDebit),
                                CurrencyFormatter.format(totalCredit),
                                CurrencyFormatter.format(totalDebit - totalCredit)
                            )
                        ),
                        settings = session.settings
                    )
                }
                MessagingShareService.openPdf(context, file)
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إنشاء ميزان المراجعة")
            } finally {
                isExportingReport = false
            }
        }
    }

    fun exportVouchersList() {
        val activeCompany = session.activeCompany ?: return
        if (!PermissionManager.permissionsOf(user).canExportAndShare) return
        isExportingReport = true
        scope.launch {
            try {
                val rows = filteredVouchers.map { v ->
                    listOf(
                        v.type.arabicTitle,
                        accounts.find { it.id == v.accountId }?.name ?: "—",
                        DateUtils.formatDate(v.dateMillis),
                        v.description.ifBlank { "—" },
                        CurrencyFormatter.format(v.amount * v.exchangeRate)
                    )
                }
                val receipts = filteredVouchers.filter { it.type == VoucherType.RECEIPT }.sumOf { it.amount * it.exchangeRate }
                val payments = filteredVouchers.filter { it.type == VoucherType.PAYMENT }.sumOf { it.amount * it.exchangeRate }
                val file = withContext(Dispatchers.IO) {
                    mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                        company = activeCompany,
                        reportTitle = "كشف السندات والقيود",
                        periodLabel = "${DateUtils.formatDate(voucherFromMillis)} — ${DateUtils.formatDate(voucherToMillis)}",
                        headers = listOf("النوع", "الحساب", "التاريخ", "البيان", "المبلغ"),
                        rows = rows,
                        footerRows = listOf(
                            listOf("الإجمالي", "", "", "سندات القبض", CurrencyFormatter.format(receipts)),
                            listOf("", "", "", "سندات الصرف", CurrencyFormatter.format(payments)),
                            listOf("", "", "", "الصافي", CurrencyFormatter.format(receipts - payments))
                        ),
                        settings = session.settings
                    )
                }
                MessagingShareService.openPdf(context, file)
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إنشاء كشف السندات")
            } finally {
                isExportingReport = false
            }
        }
    }

    fun exportBalanceSheet() {
        val activeCompany = session.activeCompany ?: return
        val currentUser = user ?: return
        val report = balanceSheetReport ?: return
        if (!PermissionManager.permissionsOf(user).canExportAndShare) return
        isExportingReport = true
        scope.launch {
            try {
                val rows = report.assets.map { listOf("أصل", it.name, CurrencyFormatter.format(it.amount)) } +
                    report.liabilities.map { listOf("خصم", it.name, CurrencyFormatter.format(it.amount)) } +
                    report.equity.map { listOf("حقوق ملكية", it.name, CurrencyFormatter.format(it.amount)) }
                val file = withContext(Dispatchers.IO) {
                    mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                        company = activeCompany,
                        reportTitle = "الميزانية العمومية",
                        periodLabel = "حتى ${DateUtils.formatDate(balanceSheetDateMillis)}",
                        headers = listOf("القسم", "الحساب", "المبلغ"),
                        rows = rows,
                        footerRows = listOf(
                            listOf("الإجمالي", "الأصول", CurrencyFormatter.format(report.totalAssets)),
                            listOf("الإجمالي", "الخصوم + حقوق الملكية", CurrencyFormatter.format(report.totalLiabilitiesAndEquity)),
                            listOf("الفرق", "", CurrencyFormatter.format(report.balanceDifference))
                        ),
                        settings = session.settings
                    )
                }
                MessagingShareService.openPdf(context, file)
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إنشاء الميزانية")
            } finally {
                isExportingReport = false
            }
        }
    }

    fun exportAging() {
        val activeCompany = session.activeCompany ?: return
        val report = agingReport ?: return
        if (!PermissionManager.permissionsOf(user).canExportAndShare) return
        isExportingReport = true
        scope.launch {
            try {
                val rows = report.accounts.map { a ->
                    listOf(
                        a.accountName,
                        CurrencyFormatter.format(a.amountIn(AgingBucket.DAYS_0_30)),
                        CurrencyFormatter.format(a.amountIn(AgingBucket.DAYS_31_60)),
                        CurrencyFormatter.format(a.amountIn(AgingBucket.DAYS_61_90)),
                        CurrencyFormatter.format(a.amountIn(AgingBucket.OVER_90)),
                        CurrencyFormatter.format(a.total)
                    )
                }
                val file = withContext(Dispatchers.IO) {
                    mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                        company = activeCompany,
                        reportTitle = "تقرير أعمار الديون — ${agingCategory.arabicTitle}",
                        periodLabel = "حتى ${DateUtils.formatDate(agingDateMillis)}",
                        headers = listOf("الحساب", "0-30", "31-60", "61-90", "90+", "الإجمالي"),
                        rows = rows,
                        footerRows = listOf(
                            listOf(
                                "الإجمالي", "", "", "", "",
                                CurrencyFormatter.format(report.total)
                            )
                        ),
                        settings = session.settings
                    )
                }
                MessagingShareService.openPdf(context, file)
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إنشاء تقرير الأعمار")
            } finally {
                isExportingReport = false
            }
        }
    }

    fun exportProfitLoss() {
        val activeCompany = session.activeCompany ?: return
        val currentUser = user ?: return
        isExportingReport = true
        scope.launch {
            try {
                val report = mainViewModel.container.financialReportsRepository.getProfitLoss(
                    currentUser, companyId, reportFromMillis, reportToMillis
                )
                val rows = report.revenues.map { listOf(it.name, "إيراد", CurrencyFormatter.format(it.amount)) } +
                    listOf(listOf("— المصروفات —", "", "")) +
                    report.expenses.map { listOf(it.name, "مصروف", CurrencyFormatter.format(it.amount)) }
                val file = withContext(Dispatchers.IO) {
                    mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                        company = activeCompany,
                        reportTitle = "قائمة الأرباح والخسائر",
                        periodLabel = "${DateUtils.formatDate(reportFromMillis)} — ${DateUtils.formatDate(reportToMillis)}",
                        headers = listOf("الحساب", "النوع", "المبلغ"),
                        rows = rows,
                        footerRows = listOf(
                            listOf("إجمالي الإيرادات", "", CurrencyFormatter.format(report.totalRevenue)),
                            listOf("إجمالي المصروفات", "", CurrencyFormatter.format(report.totalExpense)),
                            listOf("صافي الربح / الخسارة", "", CurrencyFormatter.format(report.netProfit))
                        ),
                        settings = session.settings
                    )
                }
                MessagingShareService.openPdf(context, file)
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إنشاء قائمة الأرباح والخسائر")
            } finally {
                isExportingReport = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مركز التقارير المالية") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewFinancialReports || it.canViewProfitability }) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ===== شريط الفلترة الزمنية =====
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportPeriod.values().forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(p.label) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // ===== بطاقة الرصيد الرئيسية + بطاقات الملخص =====
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // بطاقة الرصيد الرئيسية — أسلوب المحافظ الإلكترونية
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xFF1B3A6B), Color(0xFF0F1F38))),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.padding(9.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("صافي السيولة — ${period.label}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                        Text(
                                            CurrencyFormatter.format(netFlow),
                                            color = if (netFlow >= 0) CreditGreen else DebitRed,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HeroStat("المقبوضات", CurrencyFormatter.format(totalReceipts), CreditGreen, Modifier.weight(1f))
                                    HeroStat("المصروفات", CurrencyFormatter.format(totalPayments), DebitRed, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("إجمالي المبيعات", CurrencyFormatter.format(totalSales), Icons.Default.Receipt, Color(0xFF00695C), Modifier.weight(1f))
                        SummaryCard("إجمالي المشتريات", CurrencyFormatter.format(totalPurchases), Icons.Default.ShoppingCart, Color(0xFFEF6C00), Modifier.weight(1f))
                    }
                    if (PermissionManager.permissionsOf(user).canExportAndShare) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { exportTrialBalance() },
                                enabled = !isExportingReport,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ميزان المراجعة PDF", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { exportProfitLoss() },
                                enabled = !isExportingReport,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("أرباح وخسائر PDF", fontSize = 12.sp)
                            }
                        }
                    }
                    if (user != null && PermissionManager.permissionsOf(user).canClosePeriod) {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DebitRed.copy(alpha = 0.08f)), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, DebitRed.copy(alpha = 0.25f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LockClock, contentDescription = null, tint = DebitRed, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("إغلاق الحسابات وتدوير الأرصدة", fontWeight = FontWeight.Bold, color = DebitRed, fontSize = 13.sp)
                                }
                                Button(onClick = { showClosePeriodConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = DebitRed), modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp)) {
                                    Text("تنفيذ إغلاق الحسابات", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ===== التبويبات =====
                ScrollableTabRow(
                    selectedTabIndex = visibleTabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    visibleTabs.forEachIndexed { index, (tabId, title) ->
                        Tab(
                            selected = selectedTab == tabId,
                            onClick = { selectedTab = tabId },
                            text = { Text(title, fontSize = 13.sp) }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            var selectedReportAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
                            AccountStatementContent(
                                accountId = selectedReportAccountId,
                                mainViewModel = mainViewModel,
                                showAccountSelector = true,
                                onAccountSelected = { selectedReportAccountId = it },
                                onNavigateToInvoice = { invoiceId -> navController.navigate(Screen.InvoiceDetails.createRoute(invoiceId)) },
                                onNavigateToVoucher = { voucherId -> navController.navigate(Screen.VoucherDetails.createRoute(voucherId)) },
                                onNavigateToNewVoucher = { accId -> navController.navigate(Screen.VoucherEntry.createRoute(type = "RECEIPT", accountId = accId)) }
                            )
                        }
                        1 -> TrialBalanceTab(
                            fromMillis = reportFromMillis,
                            toMillis = reportToMillis,
                            onFromSelected = { reportFromMillis = it },
                            onToSelected = { reportToMillis = it },
                            report = trialBalanceReport,
                            loading = financialReportLoading,
                            error = financialReportError
                        )
                        2 -> VouchersTab(
                            filteredVouchers,
                            accounts,
                            voucherSearch,
                            { voucherSearch = it },
                            voucherTypeFilter,
                            onTypeFilter = { voucherTypeFilter = it },
                            fromMillis = voucherFromMillis,
                            toMillis = voucherToMillis,
                            onFromSelected = { voucherFromMillis = it },
                            onToSelected = { voucherToMillis = it },
                            onNewVoucher = { navController.navigate(Screen.VoucherEntry.createRoute()) },
                            onExport = { exportVouchersList() },
                            canExport = PermissionManager.permissionsOf(user).canExportAndShare,
                            canEditVoucher = PermissionManager.permissionsOf(user).canEditVoucher,
                            canDeleteVoucher = PermissionManager.permissionsOf(user).canDeleteVoucher,
                            onOpenVoucher = { voucher ->
                                navController.navigate(Screen.VoucherDetails.createRoute(voucher.id))
                            },
                            onEditVoucher = { voucher ->
                                navController.navigate(Screen.VoucherEntry.createRoute(voucherId = voucher.id))
                            },
                            onDeleteVoucher = { voucher -> voucherPendingDelete = voucher }
                        )
                        3 -> InvoicesTab(periodInvoices, totalSales, totalPurchases, stockValuation, navController)
                        4 -> AccountsTab(accountBalances, navController)
                        5 -> ProfitLossTab(
                            fromMillis = reportFromMillis,
                            toMillis = reportToMillis,
                            onFromSelected = { reportFromMillis = it },
                            onToSelected = { reportToMillis = it },
                            report = profitLossReport,
                            loading = financialReportLoading,
                            error = financialReportError
                        )
                        6 -> BalanceSheetTab(
                            asOfMillis = balanceSheetDateMillis,
                            onDateSelected = { balanceSheetDateMillis = it },
                            report = balanceSheetReport,
                            loading = financialReportLoading,
                            error = financialReportError,
                            onExport = { exportBalanceSheet() },
                            canExport = PermissionManager.permissionsOf(user).canExportAndShare && balanceSheetReport != null
                        )
                        7 -> AgingTab(
                            asOfMillis = agingDateMillis,
                            onDateSelected = { agingDateMillis = it },
                            category = agingCategory,
                            onCategorySelected = { agingCategory = it },
                            bucketFilter = agingBucketFilter,
                            onBucketFilterSelected = { agingBucketFilter = it },
                            report = agingReport,
                            loading = financialReportLoading,
                            error = financialReportError,
                            onExport = { exportAging() },
                            canExport = PermissionManager.permissionsOf(user).canExportAndShare && agingReport != null
                        )
                        8 -> ProfitabilityTab(
                            report = profitabilityReport,
                            loading = financialReportLoading,
                            error = financialReportError,
                            currencyName = session.currencies.firstOrNull { it.isDefault }?.name ?: "ريال",
                            subUnitName = session.currencies.firstOrNull { it.isDefault }?.subUnitName.orEmpty()
                        )
                        else -> Text("لا يوجد تقرير متاح")
                    }
                }
            }
        }
    }

    voucherPendingDelete?.let { voucherToDelete ->
        AlertDialog(
            onDismissRequest = { voucherPendingDelete = null },
            title = { Text("تأكيد حذف السند") },
            text = { Text("سيتم حذف «${voucherToDelete.type.arabicTitle}» نهائياً من السجلات المحلية وسيُزامَن الحذف مع باقي الأجهزة. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    voucherPendingDelete = null
                    deleteVoucher(voucherToDelete)
                }) { Text("حذف", color = DebitRed) }
            },
            dismissButton = { TextButton(onClick = { voucherPendingDelete = null }) { Text("إلغاء") } }
        )
    }

    if (showClosePeriodConfirm && user != null) {
        AlertDialog(
            onDismissRequest = { showClosePeriodConfirm = false },
            title = { Text("تنبيه قبل إغلاق الحسابات!!!") },
            text = { Text("سيتم احتساب الأرصدة النهائية لكل حساب ولكل عملة وترحيلها كأرصدة افتتاحية للفترة الجديدة. هل تريد المتابعة؟") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            val count = mainViewModel.container.accountingRepository.closeAccountingPeriod(user, companyId, System.currentTimeMillis())
                            showClosePeriodConfirm = false
                            mainViewModel.showMessage("تم إغلاق الحسابات وترحيل $count رصيد افتتاحي بنجاح")
                        } catch (e: Exception) { mainViewModel.showError(e, "تعذر إغلاق الفترة") }
                    }
                }) { Text("تأكيد الإغلاق والترحيل") }
            },
            dismissButton = { TextButton(onClick = { showClosePeriodConfirm = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VouchersTab(
    vouchers: List<VoucherEntity>,
    accounts: List<AccountEntity>,
    search: String,
    onSearch: (String) -> Unit,
    typeFilter: VoucherType?,
    onTypeFilter: (VoucherType?) -> Unit,
    fromMillis: Long,
    toMillis: Long,
    onFromSelected: (Long) -> Unit,
    onToSelected: (Long) -> Unit,
    onNewVoucher: () -> Unit,
    onExport: () -> Unit,
    canExport: Boolean,
    canEditVoucher: Boolean,
    canDeleteVoucher: Boolean,
    onOpenVoucher: (VoucherEntity) -> Unit,
    onEditVoucher: (VoucherEntity) -> Unit,
    onDeleteVoucher: (VoucherEntity) -> Unit
) {
    val totalReceipts = vouchers.filter { it.type == VoucherType.RECEIPT }.sumOf { it.amount * it.exchangeRate }
    val totalPayments = vouchers.filter { it.type == VoucherType.PAYMENT }.sumOf { it.amount * it.exchangeRate }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportDateField("من", fromMillis, Modifier.weight(1f), onFromSelected)
            ReportDateField("إلى", toMillis, Modifier.weight(1f), onToSelected, isEndDate = true)
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("بحث في البيان أو اسم الحساب...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = typeFilter == null, onClick = { onTypeFilter(null) }, label = { Text("الكل") })
            VoucherType.values().forEach { type ->
                FilterChip(
                    selected = typeFilter == type,
                    onClick = { onTypeFilter(if (typeFilter == type) null else type) },
                    label = { Text(type.shortCode) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onNewVoucher, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("سند جديد", fontSize = 12.sp)
            }
            if (canExport) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تصدير PDF", fontSize = 12.sp)
                }
            }
        }
        // بطاقة إجماليات الفلتر الحالي
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("عدد السندات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${vouchers.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي القبض", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.format(totalReceipts), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CreditGreen, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي الصرف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.format(totalPayments), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DebitRed, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الصافي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        CurrencyFormatter.format(totalReceipts - totalPayments),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (totalReceipts - totalPayments >= 0) CreditGreen else DebitRed,
                        maxLines = 1
                    )
                }
            }
        }
        TableHeaderRow(
            columns = listOf(
                "النوع" to 0.9f,
                "البيان / الحساب" to 2.2f,
                "التاريخ" to 1.1f,
                "المبلغ" to 1.2f
            )
        )
        vouchers.forEach { v ->
            val accountName = accounts.find { it.id == v.accountId }?.name ?: "حساب"
            val isReceipt = v.type == VoucherType.RECEIPT
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenVoucher(v) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = (if (isReceipt) CreditGreen else DebitRed).copy(alpha = 0.12f)) {
                        Icon(
                            if (isReceipt) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = if (isReceipt) CreditGreen else DebitRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(v.type.arabicTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(v.description.ifBlank { "بدون بيان" }, fontSize = 12.sp, maxLines = 1)
                        Text("${accountName} • ${DateUtils.formatDate(v.dateMillis)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SyncStatusDot(v.syncStatus)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        CurrencyFormatter.format(v.amount * v.exchangeRate),
                        fontWeight = FontWeight.Bold,
                        color = if (isReceipt) CreditGreen else DebitRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "إجراءات السند",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("عرض التفاصيل") },
                                onClick = { menuExpanded = false; onOpenVoucher(v) }
                            )
                            if (canEditVoucher && AccountingRepository.isEntryEditable(v)) {
                                DropdownMenuItem(
                                    text = { Text("تعديل السند") },
                                    onClick = { menuExpanded = false; onEditVoucher(v) }
                                )
                            }
                            if (canDeleteVoucher && AccountingRepository.isManuallyDeletable(v)) {
                                DropdownMenuItem(
                                    text = { Text("حذف السند", color = DebitRed) },
                                    onClick = { menuExpanded = false; onDeleteVoucher(v) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (vouchers.isEmpty()) Text("لا توجد سندات في هذه الفترة", modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InvoicesTab(invoices: List<InvoiceEntity>, totalSales: Double, totalPurchases: Double, stockValuation: Double, navController: androidx.navigation.NavController) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("المبيعات", CurrencyFormatter.format(totalSales), Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF00695C), Modifier.weight(1f))
            SummaryCard("المشتريات", CurrencyFormatter.format(totalPurchases), Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFEF6C00), Modifier.weight(1f))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                }
                Column {
                    Text("تقييم المخزون الحالي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.format(stockValuation), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        invoices.forEach { inv ->
            val isSale = inv.type == InvoiceType.SALE || inv.type == InvoiceType.SALE_RETURN || inv.type == InvoiceType.FREE_SALE
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.InvoiceDetails.createRoute(inv.id)) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = (if (isSale) CreditGreen else DebitRed).copy(alpha = 0.12f)) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = if (isSale) CreditGreen else DebitRed, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${inv.type.arabicTitle} #${inv.displayNumber.ifBlank { inv.invoiceNumber.toString() }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            when {
                                inv.isArchived -> "مؤرشفة"
                                inv.isPosted -> "مرحّلة"
                                else -> "مسودة"
                            } + " • " + DateUtils.formatDate(inv.dateMillis),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SyncStatusDot(inv.syncStatus)
                    Spacer(Modifier.width(6.dp))
                    Text(CurrencyFormatter.format(inv.netTotal * inv.exchangeRate), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (invoices.isEmpty()) Text("لا توجد فواتير في هذه الفترة", modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountsTab(balances: List<Triple<AccountEntity, Double, String>>, navController: androidx.navigation.NavController) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المدينون (لهم رصيد مدين) والدائنون (عليهم رصيد دائن) مرتبة حسب الأكبر رصيداً", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        balances.forEach { (acc, balance, category) ->
            val isDebit = balance >= 0
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate(Screen.AccountStatement.createRoute(acc.id))
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = (if (isDebit) CreditGreen else DebitRed).copy(alpha = 0.12f)) {
                        Icon(
                            if (isDebit) Icons.Default.Person else Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = if (isDebit) CreditGreen else DebitRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("$category • ${if (isDebit) "مدين (له)" else "دائن (عليه)"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        CurrencyFormatter.format(kotlin.math.abs(balance)),
                        fontWeight = FontWeight.Bold,
                        color = if (isDebit) CreditGreen else DebitRed
                    )
                }
            }
        }
        if (balances.isEmpty()) Text("لا توجد حسابات", modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfitabilityTab(
    report: ProfitabilityReport?,
    loading: Boolean,
    error: String?,
    currencyName: String,
    subUnitName: String
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            report == null -> Text("لا توجد بيانات ربحية متاحة")
            else -> {
                SummaryCard(
                    "إجمالي تكلفة البضاعة المباعة",
                    CurrencyFormatter.format(report.totalCostOfGoodsSold),
                    Icons.Default.Inventory2,
                    DebitRed,
                    Modifier.fillMaxWidth()
                )
                Text(
                    ArabicTafqeet.convert(report.totalCostOfGoodsSold, currencyName, subUnitName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("أعلى 10 أصناف حسب الربح", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (report.products.isEmpty()) {
                    Text("لا توجد فواتير بيع أو مردودات مرحلة تحتوي أصنافاً.")
                }
                report.products.forEachIndexed { index, product ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}. ${product.productName}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text(CurrencyFormatter.format(product.profit), color = if (product.profit >= 0) CreditGreen else DebitRed, fontWeight = FontWeight.Bold)
                            }
                            Text("الكمية الصافية: ${CurrencyFormatter.format(product.quantity)}")
                            Text("الإيراد: ${CurrencyFormatter.format(product.revenue)} • COGS: ${CurrencyFormatter.format(product.costOfGoodsSold)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitLossTab(
    fromMillis: Long,
    toMillis: Long,
    onFromSelected: (Long) -> Unit,
    onToSelected: (Long) -> Unit,
    report: ProfitLossReport?,
    loading: Boolean,
    error: String?
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportDateField("من تاريخ", fromMillis, Modifier.weight(1f), onFromSelected)
            ReportDateField("إلى تاريخ", toMillis, Modifier.weight(1f), onToSelected, isEndDate = true)
        }
        ReportLoadStatus(loading, error)
        report?.let {
            ReportAmountCard("إجمالي الإيرادات", it.totalRevenue, CreditGreen)
            ReportLinesCard("الإيرادات", it.revenues, it.totalRevenue)
            ReportLinesCard("المصروفات", it.expenses, it.totalExpense)
            ReportAmountCard(
                if (it.netProfit >= 0.0) "صافي الربح" else "صافي الخسارة",
                it.netProfit,
                if (it.netProfit >= 0.0) CreditGreen else DebitRed
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceSheetTab(
    asOfMillis: Long,
    onDateSelected: (Long) -> Unit,
    report: BalanceSheetReport?,
    loading: Boolean,
    error: String?,
    onExport: () -> Unit,
    canExport: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ReportDateField("الرصيد حتى تاريخ", asOfMillis, Modifier.weight(1f), onDateSelected, isEndDate = true)
            if (canExport) {
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ReportLoadStatus(loading, error)
        report?.let {
            ReportLinesCard("الأصول", it.assets, it.totalAssets)
            ReportLinesCard("الخصوم", it.liabilities, it.totalLiabilities)
            ReportLinesCard("حقوق الملكية", it.equity, it.totalEquity)
            ReportAmountCard("إجمالي الأصول", it.totalAssets, CreditGreen)
            ReportAmountCard("الخصوم + حقوق الملكية", it.totalLiabilitiesAndEquity, DebitRed)
            ReportAmountCard(
                if (kotlin.math.abs(it.balanceDifference) < 0.01) "فرق الميزانية — متوازنة" else "فرق الميزانية",
                it.balanceDifference,
                if (kotlin.math.abs(it.balanceDifference) < 0.01) CreditGreen else DebitRed
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgingTab(
    asOfMillis: Long,
    onDateSelected: (Long) -> Unit,
    category: AccountCategory,
    onCategorySelected: (AccountCategory) -> Unit,
    bucketFilter: AgingBucket?,
    onBucketFilterSelected: (AgingBucket?) -> Unit,
    report: AgingReport?,
    loading: Boolean,
    error: String?,
    onExport: () -> Unit,
    canExport: Boolean
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ReportDateField("الأعمار حتى تاريخ", asOfMillis, Modifier.weight(1f), onDateSelected, isEndDate = true)
            if (canExport) {
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = it }
        ) {
            OutlinedTextField(
                value = category.arabicTitle,
                onValueChange = {},
                readOnly = true,
                label = { Text("نوع الحساب") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                listOf(AccountCategory.CUSTOMERS, AccountCategory.SUPPLIERS).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.arabicTitle) },
                        onClick = {
                            onCategorySelected(option)
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = bucketFilter == null,
                onClick = { onBucketFilterSelected(null) },
                label = { Text("كل الأعمار") }
            )
            AgingBucket.values().forEach { bucket ->
                FilterChip(
                    selected = bucketFilter == bucket,
                    onClick = { onBucketFilterSelected(bucket) },
                    label = { Text(bucket.label) }
                )
            }
        }
        ReportLoadStatus(loading, error)
        report?.let { result ->
            val total = bucketFilter?.let { result.totals[it] ?: 0.0 }
                ?: result.total
            ReportAmountCard("إجمالي الديون المستحقة", total, DebitRed)
            result.accounts.forEach { account ->
                val value = bucketFilter?.let(account::amountIn) ?: account.total
                if (value > 0.0) {
                    AgingAccountCard(account, bucketFilter, value)
                }
            }
            if (result.accounts.isEmpty()) {
                Text(
                    "لا توجد فواتير آجلة غير مسددة في هذا التاريخ",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AgingAccountCard(
    account: AgingAccountSummary,
    bucketFilter: AgingBucket?,
    displayedTotal: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.accountName, fontWeight = FontWeight.Bold)
                    Text(account.category.arabicTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(CurrencyFormatter.format(displayedTotal), fontWeight = FontWeight.Bold, color = DebitRed)
            }
            if (bucketFilter == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AgingBucket.values().forEach { bucket ->
                        Column {
                            Text(bucket.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.format(account.amountIn(bucket)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text(bucketFilter.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                ArabicTafqeet.convert(kotlin.math.abs(displayedTotal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportLinesCard(
    title: String,
    lines: List<FinancialReportLine>,
    total: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (lines.isEmpty()) {
                Text("لا توجد حركة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.format(line.amount), fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الإجمالي", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(CurrencyFormatter.format(total), fontWeight = FontWeight.Bold)
            }
            Text(
                ArabicTafqeet.convert(kotlin.math.abs(total)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportAmountCard(title: String, amount: Double, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color)
            Text(CurrencyFormatter.format(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                ArabicTafqeet.convert(kotlin.math.abs(amount)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportLoadStatus(loading: Boolean, error: String?) {
    if (loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (error != null) {
        Text(error, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDateField(
    label: String,
    dateMillis: Long,
    modifier: Modifier,
    onDateSelected: (Long) -> Unit,
    isEndDate: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(DateUtils.formatDate(dateMillis), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toPickerDateMillis(dateMillis))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(fromPickerDateMillis(it, isEndDate)) }
                    showPicker = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun startOfCurrentMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun toPickerDateMillis(localMillis: Long): Long {
    val localDate = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(localDate.get(Calendar.YEAR), localDate.get(Calendar.MONTH), localDate.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromPickerDateMillis(pickerMillis: Long, isEndDate: Boolean): Long {
    val utcDate = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH))
        if (isEndDate) {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        } else {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrialBalanceTab(
    fromMillis: Long,
    toMillis: Long,
    onFromSelected: (Long) -> Unit,
    onToSelected: (Long) -> Unit,
    report: TrialBalanceReport?,
    loading: Boolean,
    error: String?
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showFromPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("من: ${DateUtils.formatDate(fromMillis)}", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showToPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إلى: ${DateUtils.formatDate(toMillis)}", fontSize = 12.sp)
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        report?.let { rep ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الحساب", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text("أول (مدين)", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text("أول (دائن)", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text("حركة (مدين)", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text("حركة (دائن)", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text("رصيد (مدين)", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text("رصيد (دائن)", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(rep.lines) { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(line.accountName, modifier = Modifier.width(110.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(if (line.openingDebit > 0) CurrencyFormatter.formatAmount(line.openingDebit) else "—", modifier = Modifier.width(75.dp), fontSize = 11.sp)
                                Text(if (line.openingCredit > 0) CurrencyFormatter.formatAmount(line.openingCredit) else "—", modifier = Modifier.width(75.dp), fontSize = 11.sp)
                                Text(if (line.periodDebit > 0) CurrencyFormatter.formatAmount(line.periodDebit) else "—", modifier = Modifier.width(75.dp), fontSize = 11.sp, color = DebitRed)
                                Text(if (line.periodCredit > 0) CurrencyFormatter.formatAmount(line.periodCredit) else "—", modifier = Modifier.width(75.dp), fontSize = 11.sp, color = CreditGreen)
                                Text(if (line.finalDebit > 0) CurrencyFormatter.formatAmount(line.finalDebit) else "—", modifier = Modifier.width(75.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DebitRed)
                                Text(if (line.finalCredit > 0) CurrencyFormatter.formatAmount(line.finalCredit) else "—", modifier = Modifier.width(75.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CreditGreen)
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1F38))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الإجمالي العام", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text(CurrencyFormatter.formatAmount(rep.totalOpeningDebit), modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text(CurrencyFormatter.formatAmount(rep.totalOpeningCredit), modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text(CurrencyFormatter.formatAmount(rep.totalPeriodDebit), modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text(CurrencyFormatter.formatAmount(rep.totalPeriodCredit), modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text(CurrencyFormatter.formatAmount(rep.totalFinalDebit), modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                        Text(CurrencyFormatter.formatAmount(rep.totalFinalCredit), modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }

    if (showFromPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = fromMillis)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let(onFromSelected); showFromPicker = false }) { Text("تأكيد") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showToPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toMillis)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let(onToSelected); showToPicker = false }) { Text("تأكيد") } }
        ) { DatePicker(state = pickerState) }
    }
}
