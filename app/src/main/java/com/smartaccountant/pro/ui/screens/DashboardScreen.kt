package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.domain.repository.OverdueBucket
import com.smartaccountant.pro.domain.repository.OverdueReport
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.ui.components.CalculatorDialog
import com.smartaccountant.pro.ui.components.CurvedHeader
import com.smartaccountant.pro.ui.components.UiErrorMessages
import com.smartaccountant.pro.ui.navigation.Screen
import com.smartaccountant.pro.ui.theme.*
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TopBarDark = com.smartaccountant.pro.ui.theme.TopBarDark
private val FabOrange = ActionOrange

private data class QuickAction(val title: String, val icon: ImageVector, val color: Color, val route: String)

private data class CategoryTile(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val balance: Double,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    mainViewModel: MainViewModel,
    navController: NavController
) {
    val session by mainViewModel.session.collectAsState()
    val scope = rememberCoroutineScope()
    val syncState by mainViewModel.syncState.collectAsState()
    val user = session.currentUser
    val company = session.activeCompany
    val companyId = company?.id ?: ""

    var showCalculator by remember { mutableStateOf(false) }
    var showCompanyDialog by remember { mutableStateOf(false) }
    var showFabDialog by remember { mutableStateOf(false) }
    var overdueBucket by remember { mutableStateOf<OverdueBucket?>(null) }
    var overdueReport by remember { mutableStateOf<OverdueReport?>(null) }
    var overdueLoading by remember { mutableStateOf(false) }
    var overdueError by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var dashboardTab by remember { mutableStateOf(0) }

    val accountsFlow = remember(companyId) { mainViewModel.container.db.accountDao().observeAccounts(companyId) }
    val productsFlow = remember(companyId) { mainViewModel.container.db.inventoryDao().observeProducts(companyId) }
    val vouchersFlow = remember(companyId) { mainViewModel.container.db.voucherDao().observeVouchers(companyId) }
    val invoicesFlow = remember(companyId) { mainViewModel.container.db.invoiceDao().observeInvoices(companyId) }
    val accounts by accountsFlow.collectAsState(initial = emptyList())
    val products by productsFlow.collectAsState(initial = emptyList())
    val vouchers by vouchersFlow.collectAsState(initial = emptyList())
    val invoices by invoicesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(companyId, user?.id, invoices, vouchers) {
        val currentUser = user ?: return@LaunchedEffect
        if (!PermissionManager.permissionsOf(currentUser).canViewPartyProfile || companyId.isBlank()) return@LaunchedEffect
        overdueLoading = true
        overdueError = null
        try {
            overdueReport = mainViewModel.container.partyRepository.getOverdueInvoices(
                currentUser, companyId, com.smartaccountant.pro.core.utils.DateUtils.startOfToday()
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            overdueError = UiErrorMessages.fromThrowable(e, "تعذر تحميل الفواتير المتأخرة")
        } finally {
            overdueLoading = false
        }
    }

    // ===== أرصدة كل الحسابات — من كل أنواع السندات والفواتير =====
    val allBalances by produceState<Map<String, Double>>(initialValue = emptyMap(), accounts, vouchers, invoices, companyId) {
        value = if (companyId.isBlank()) emptyMap() else
            mainViewModel.container.accountingRepository.calculateAllAccountBalances(companyId)
    }

    // ===== أرصدة التصنيفات المحاسبية =====
    val categoryBalances = remember(accounts, allBalances) {
        val map = mutableMapOf<AccountCategory, Double>()
        accounts.forEach { acc ->
            map[acc.category] = (map[acc.category] ?: 0.0) + (allBalances[acc.id] ?: 0.0)
        }
        map
    }

    val debtsTotal = remember(accounts, allBalances) {
        accounts.sumOf { kotlin.math.abs(allBalances[it.id] ?: 0.0) }
    }

    val purchasesTotal = remember(invoices) {
        invoices.filter { it.type == InvoiceType.PURCHASE }.sumOf { it.netTotal * it.exchangeRate }
    }
    val inventoryValue = remember(products) {
        products.sumOf { it.stockQuantity * it.purchasePrice1 }
    }
    val todayReceipts = remember(vouchers) {
        val start = com.smartaccountant.pro.core.utils.DateUtils.startOfToday()
        vouchers.filter { it.dateMillis >= start && it.type == VoucherType.RECEIPT }.sumOf { it.amount * it.exchangeRate }
    }

    val categoryTiles = listOf(
        CategoryTile("العملاء", Icons.Default.People, Color(0xFF1565C0), categoryBalances[AccountCategory.CUSTOMERS] ?: 0.0, Screen.Accounts.createRoute(AccountCategory.CUSTOMERS.name)),
        CategoryTile("الموردين", Icons.Default.LocalShipping, Color(0xFF6A1B9A), categoryBalances[AccountCategory.SUPPLIERS] ?: 0.0, Screen.Accounts.createRoute(AccountCategory.SUPPLIERS.name)),
        CategoryTile("الموظفين", Icons.Default.Badge, Color(0xFF00838F), categoryBalances[AccountCategory.EMPLOYEES] ?: 0.0, Screen.Accounts.createRoute(AccountCategory.EMPLOYEES.name)),
        CategoryTile("الديون", Icons.Default.Handshake, Color(0xFFBF360C), debtsTotal, Screen.Accounts.createRoute("DEBTS")),
        CategoryTile("الصرفيات", Icons.Default.Receipt, DebitRed, categoryBalances[AccountCategory.EXPENSES] ?: 0.0, Screen.Accounts.createRoute(AccountCategory.EXPENSES.name)),
        CategoryTile("المشتريات", Icons.Default.ShoppingCart, Color(0xFF4527A0), purchasesTotal, Screen.InvoicesList.route),
        CategoryTile("المخزون", Icons.Default.Inventory2, Color(0xFF2E7D32), inventoryValue, Screen.Inventory.route),
        CategoryTile("التقارير", Icons.Default.BarChart, Color(0xFF37474F), todayReceipts, Screen.Reports.route)
    )

    val quickRows = listOf(
        listOf(
            QuickAction("سند جديد", Icons.Default.Description, Color.Black, Screen.VoucherEntry.createRoute(VoucherType.RECEIPT.name)),
            QuickAction("حوالة جديدة", Icons.AutoMirrored.Filled.Send, Color.Black, Screen.Remittances.route),
            QuickAction("كشف حساب", Icons.Default.AccountBalance, Color.Black, Screen.Accounts.route)
        ),
        listOf(
            QuickAction("قيد بسيط", Icons.Default.EditNote, ActionOrange, Screen.VoucherEntry.createRoute(VoucherType.SIMPLE_JOURNAL.name)),
            QuickAction("الحركة اليومية", Icons.Default.Today, ActionOrange, Screen.Reports.route),
            QuickAction("صناديق النقدية", Icons.Default.AccountBalanceWallet, ActionOrange, Screen.CashBoxesAndCurrencies.route)
        ),
        listOf(
            QuickAction("فاتورة جديدة", Icons.AutoMirrored.Filled.ReceiptLong, Color.Black, Screen.InvoiceEditor.createRoute(InvoiceType.SALE.name)),
            QuickAction("صرف عملات", Icons.Default.CurrencyExchange, Color.Black, Screen.CashBoxesAndCurrencies.route),
            QuickAction("البحث الشامل", Icons.Default.Search, Color.Black, Screen.GlobalSearch.route)
        )
    ) + if (PermissionManager.permissionsOf(user).canManageRecurring) {
        listOf(listOf(QuickAction("المعاملات المتكررة", Icons.Default.EventRepeat, ActionOrange, Screen.RecurringTransactions.route)))
    } else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(company?.nameAr ?: "المحاسب الذكي برو", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text(
                            "${user?.fullName ?: ""} • ${user?.role?.arabicTitle ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    SyncBadge(syncState)
                    IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                        Icon(Icons.Default.Search, contentDescription = "بحث شامل", tint = Color.White)
                    }
                    IconButton(onClick = { showCalculator = true }) {
                        Icon(Icons.Default.Calculate, contentDescription = "الحاسبة", tint = Color.White)
                    }
                    IconButton(onClick = { mainViewModel.lockSession() }) {
                        Icon(Icons.Default.Lock, contentDescription = "قفل / تبديل مستخدم", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarDark, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFabDialog = true },
                containerColor = FabOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة سريعة")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    try {
                        // مزامنة سحابية فعلية: دفع المعلّق + سحب الجديد من الأجهزة الأخرى
                        if (companyId.isNotBlank()) {
                            mainViewModel.container.syncEngine.syncPendingNow(companyId)
                        }
                        accountsFlow.first()
                        productsFlow.first()
                        vouchersFlow.first()
                        invoicesFlow.first()
                        val currentUser = user
                        if (currentUser != null &&
                            PermissionManager.permissionsOf(currentUser).canViewPartyProfile &&
                            companyId.isNotBlank()
                        ) {
                            overdueReport = mainViewModel.container.partyRepository.getOverdueInvoices(
                                currentUser,
                                companyId,
                                com.smartaccountant.pro.core.utils.DateUtils.startOfToday()
                            )
                            overdueError = null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        overdueError = UiErrorMessages.fromThrowable(e, "تعذر تحديث بيانات لوحة التحكم")
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp)
        ) {
            // ===== هيدر ترحيبي خفيف وخالي من التكرار =====
            CurvedHeader(
                title = "مرحباً، ${user?.fullName?.substringBefore(" ") ?: ""}",
                subtitle = user?.role?.arabicTitle ?: "",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // ===== تبويبات لوحة التحكم =====
            TabRow(
                selectedTabIndex = dashboardTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Tab(
                    selected = dashboardTab == 0,
                    onClick = { dashboardTab = 0 },
                    text = { Text("الرئيسية", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = dashboardTab == 1,
                    onClick = { dashboardTab = 1 },
                    text = { Text("التصنيفات المحاسبية", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            if (dashboardTab == 0) {
                // ===== تبويب الرئيسية: أزرار سريعة + سيولة + متأخرات + آخر العمليات =====
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickRows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { action ->
                                QuickGridButton(action = action, onClick = { navController.navigate(action.route) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                LiquiditySummaryCards(
                    todayReceipts = todayReceipts,
                    purchasesTotal = purchasesTotal,
                    debtsTotal = debtsTotal,
                    inventoryValue = inventoryValue
                )

                CashFlowCard(vouchers = vouchers)

                if (user != null && PermissionManager.permissionsOf(user).canViewPartyProfile) {
                    OverdueInvoicesCard(
                        report = overdueReport,
                        selectedBucket = overdueBucket,
                        onBucketSelected = { overdueBucket = it },
                        loading = overdueLoading,
                        error = overdueError,
                        onInvoiceClick = { invoiceId ->
                            navController.navigate(Screen.InvoiceDetails.createRoute(invoiceId))
                        }
                    )
                }

                RecentOperationsCard(
                    vouchers = vouchers,
                    invoices = invoices,
                    accounts = accounts,
                    onVoucherClick = { navController.navigate(Screen.VoucherDetails.createRoute(it)) },
                    onInvoiceClick = { navController.navigate(Screen.InvoiceDetails.createRoute(it)) }
                )
            } else {
                // ===== تبويب التصنيفات: شبكة البطاقات فقط =====
                Text(
                    "التصنيفات المحاسبية",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryTiles.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { tile ->
                                CategoryCard(tile = tile, onClick = { navController.navigate(tile.route) }, modifier = Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    }

    if (showCalculator) {
        CalculatorDialog(onDismiss = { showCalculator = false }, onUseResult = { mainViewModel.showMessage("الناتج: $it") })
    }

    // ===== حوار FAB: كل الإجراءات السريعة =====
    if (showFabDialog) {
        AlertDialog(
            onDismissRequest = { showFabDialog = false },
            title = { Text("إجراءات سريعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickRows.flatten().forEach { action ->
                        FilledTonalButton(
                            onClick = { showFabDialog = false; navController.navigate(action.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(action.title)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    FilledTonalButton(
                        onClick = { showFabDialog = false; navController.navigate(Screen.Accounts.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("حساب / عميل جديد")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFabDialog = false }) { Text("إغلاق") } }
        )
    }

    if (showCompanyDialog) {
        var newName by remember { mutableStateOf("") }
        var newAddr by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("") }
        var ownerName by remember { mutableStateOf("") }
        var ownerEmail by remember { mutableStateOf("") }
        var ownerPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCompanyDialog = false },
            title = { Text("إدارة الشركات والمشاريع المستقلة") },
            text = {
                Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    session.companies.forEach { comp ->
                        FilledTonalButton(
                            onClick = { mainViewModel.switchCompany(comp); showCompanyDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (comp.id == company?.id) "✓ ${comp.nameAr}" else comp.nameAr) }
                    }
                    HorizontalDivider()
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("اسم الشركة أو الفرع") })
                    OutlinedTextField(value = newAddr, onValueChange = { newAddr = it }, label = { Text("العنوان") })
                    OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text("الهاتف") })
                    HorizontalDivider()
                    Text("بيانات مالك الشركة الجديدة (اختياري)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("اسم المالك") })
                    OutlinedTextField(value = ownerEmail, onValueChange = { ownerEmail = it }, label = { Text("بريد المالك") })
                    OutlinedTextField(value = ownerPin, onValueChange = { ownerPin = it }, label = { Text("PIN المالك") })
                    HorizontalDivider()
                    Text("قائمة التطبيق", style = MaterialTheme.typography.labelMedium)
                    val menuPerms = PermissionManager.permissionsOf(user)
                    if (menuPerms.canManageSettings) {
                        FilledTonalButton(
                            onClick = { showCompanyDialog = false; navController.navigate(Screen.Settings.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إعدادات التطبيق")
                        }
                    }
                    if (menuPerms.canManageUsers) {
                        FilledTonalButton(
                            onClick = { showCompanyDialog = false; navController.navigate(Screen.AdminUsers.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("المستخدمون والصلاحيات")
                        }
                    }
                    if (menuPerms.canViewAuditLogs) {
                        FilledTonalButton(
                            onClick = { showCompanyDialog = false; navController.navigate(Screen.AuditLogs.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("سجل الرقابة")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        mainViewModel.createNewCompany(
                            nameAr = newName.trim(),
                            addressAr = newAddr.trim(),
                            phone = newPhone.trim(),
                            ownerName = ownerName.trim(),
                            ownerEmail = ownerEmail.trim(),
                            ownerPin = ownerPin.trim()
                        )
                        showCompanyDialog = false
                    }
                }) { Text("إضافة والتبديل") }
            },
            dismissButton = { TextButton(onClick = { showCompanyDialog = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun OverdueInvoicesCard(
    report: OverdueReport?,
    selectedBucket: OverdueBucket?,
    onBucketSelected: (OverdueBucket?) -> Unit,
    loading: Boolean,
    error: String?,
    onInvoiceClick: (String) -> Unit
) {
    val invoices = report?.invoices.orEmpty().filter {
        selectedBucket == null || it.bucket == selectedBucket
    }
    val total = report?.let {
        selectedBucket?.let { bucket -> it.totalsByBucket[bucket] ?: 0.0 } ?: it.totalOutstanding
    } ?: 0.0
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = DebitRed)
                Spacer(Modifier.width(8.dp))
                Text("فواتير متأخرة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${invoices.size}", fontWeight = FontWeight.Bold, color = DebitRed)
            }
            Text(
                "إجمالي المتبقي: ${CurrencyFormatter.format(total)}",
                style = MaterialTheme.typography.titleSmall,
                color = DebitRed,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedBucket == null,
                    onClick = { onBucketSelected(null) },
                    label = { Text("الكل") }
                )
                OverdueBucket.values().forEach { bucket ->
                    FilterChip(
                        selected = selectedBucket == bucket,
                        onClick = { onBucketSelected(bucket) },
                        label = { Text(bucket.label) }
                    )
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            invoices.take(5).forEach { overdue ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onInvoiceClick(overdue.invoice.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(overdue.partyName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${overdue.invoice.displayNumber.ifBlank { "#${overdue.invoice.invoiceNumber}" }} • متأخرة ${overdue.daysLate} يوم",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(CurrencyFormatter.format(overdue.outstandingAmount), color = DebitRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (report != null && invoices.isEmpty() && error == null) {
                Text("لا توجد فواتير ضمن هذه الشريحة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SyncBadge(syncState: com.smartaccountant.pro.core.sync.SyncUiState) {
    val (label, color) = when {
        syncState.isSyncing -> "مزامنة..." to WarningAmber
        syncState.isOnlineCloudActive -> "متصل" to CreditGreen
        else -> "محلي" to Color(0xFF64B5F6)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.2f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickGridButton(action: QuickAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = action.color.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(action.icon, contentDescription = action.title, tint = action.color, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                action.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryCard(tile: CategoryTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = tile.color.copy(alpha = 0.14f)) {
                Icon(tile.icon, contentDescription = null, tint = tile.color, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Text(
                tile.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                CurrencyFormatter.format(tile.balance),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (tile.balance < 0.0) CreditGreen else DebitRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiquiditySummaryCards(
    todayReceipts: Double,
    purchasesTotal: Double,
    debtsTotal: Double,
    inventoryValue: Double
) {
    val items = listOf(
        Triple("مقبوضات اليوم", todayReceipts, CreditGreen),
        Triple("المشتريات", purchasesTotal, Color(0xFF4527A0)),
        Triple("الديون", debtsTotal, DebitRed),
        Triple("قيمة المخزون", inventoryValue, Color(0xFF2E7D32))
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value, color) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                CurrencyFormatter.format(value),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class DayFlow(val label: String, val receipts: Double, val payments: Double)

private fun buildWeekFlow(vouchers: List<com.smartaccountant.pro.data.model.VoucherEntity>): List<DayFlow> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val dayMs = 24L * 60 * 60 * 1000
    val dayFormat = SimpleDateFormat("EEE", Locale("ar"))
    return (6 downTo 0).map { i ->
        val start = todayStart - i * dayMs
        val end = start + dayMs
        val receipts = vouchers
            .filter { it.type == VoucherType.RECEIPT && it.dateMillis >= start && it.dateMillis < end }
            .sumOf { it.amount * it.exchangeRate }
        val payments = vouchers
            .filter { it.type == VoucherType.PAYMENT && it.dateMillis >= start && it.dateMillis < end }
            .sumOf { it.amount * it.exchangeRate }
        DayFlow(dayFormat.format(Date(start)), receipts, payments)
    }
}

@Composable
private fun CashFlowCard(vouchers: List<com.smartaccountant.pro.data.model.VoucherEntity>) {
    val days = remember(vouchers) { buildWeekFlow(vouchers) }
    val maxVal = remember(days) { days.maxOf { kotlin.math.max(it.receipts, it.payments) }.coerceAtLeast(1.0) }
    val weekIn = days.sumOf { it.receipts }
    val weekOut = days.sumOf { it.payments }
    val hasAny = days.any { it.receipts > 0.0 || it.payments > 0.0 }
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "التدفق النقدي — آخر 7 أيام",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowLegendDot(CreditGreen, "وارد ${CurrencyFormatter.format(weekIn)}")
                FlowLegendDot(DebitRed, "صادر ${CurrencyFormatter.format(weekOut)}")
            }
            if (!hasAny) {
                Text(
                    "لا توجد مقبوضات أو مدفوعات خلال آخر 7 أيام",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                    val groupW = size.width / days.size
                    val barW = groupW * 0.26f
                    val gap = groupW * 0.08f
                    val chartH = size.height
                    days.forEachIndexed { i, d ->
                        val x = i * groupW + groupW * 0.5f - barW - gap / 2f
                        val hIn = (d.receipts / maxVal * chartH).toFloat()
                        val hOut = (d.payments / maxVal * chartH).toFloat()
                        if (hIn > 0f) {
                            drawRoundRect(
                                color = CreditGreen,
                                topLeft = Offset(x, chartH - hIn),
                                size = Size(barW, hIn),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                        if (hOut > 0f) {
                            drawRoundRect(
                                color = DebitRed,
                                topLeft = Offset(x + barW + gap, chartH - hOut),
                                size = Size(barW, hOut),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, chartH - 0.5f),
                        end = Offset(size.width, chartH - 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { d ->
                        Text(
                            d.label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private data class DashboardRecentOp(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val dateMillis: Long,
    val isInvoice: Boolean,
    val isPositive: Boolean
)

@Composable
private fun RecentOperationsCard(
    vouchers: List<com.smartaccountant.pro.data.model.VoucherEntity>,
    invoices: List<com.smartaccountant.pro.data.model.InvoiceEntity>,
    accounts: List<com.smartaccountant.pro.data.model.AccountEntity>,
    onVoucherClick: (String) -> Unit = {},
    onInvoiceClick: (String) -> Unit = {}
) {
    val voucherOps = vouchers.filter { !it.isDeleted }.map { v ->
        val accName = accounts.find { it.id == v.accountId }?.name ?: "—"
        DashboardRecentOp(
            id = v.id,
            title = v.type.arabicTitle,
            subtitle = "$accName • ${com.smartaccountant.pro.core.utils.DateUtils.formatDate(v.dateMillis)}",
            amount = v.amount * v.exchangeRate,
            dateMillis = v.dateMillis,
            isInvoice = false,
            isPositive = v.type == VoucherType.RECEIPT || v.type == VoucherType.REMITTANCE_IN
        )
    }

    val invoiceOps = invoices.filter { !it.isDeleted }.map { inv ->
        val accName = accounts.find { it.id == inv.accountId }?.name ?: "—"
        DashboardRecentOp(
            id = inv.id,
            title = inv.type.arabicTitle,
            subtitle = "$accName • ${com.smartaccountant.pro.core.utils.DateUtils.formatDate(inv.dateMillis)}",
            amount = inv.netTotal * inv.exchangeRate,
            dateMillis = inv.dateMillis,
            isInvoice = true,
            isPositive = inv.type == com.smartaccountant.pro.data.model.InvoiceType.SALE || inv.type == com.smartaccountant.pro.data.model.InvoiceType.FREE_SALE
        )
    }

    val recentList = (voucherOps + invoiceOps).sortedByDescending { it.dateMillis }.take(8)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("آخر العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (recentList.isEmpty()) {
                Text("لا توجد عمليات بعد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recentList.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (item.isInvoice) onInvoiceClick(item.id) else onVoucherClick(item.id)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                CurrencyFormatter.format(item.amount),
                                color = if (item.isPositive) CreditGreen else DebitRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
