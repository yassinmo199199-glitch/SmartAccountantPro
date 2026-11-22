package com.smartaccountant.pro.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.smartaccountant.pro.ui.theme.DebitRed
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.data.remote.AppUpdateStatus
import com.smartaccountant.pro.ui.screens.*
import com.smartaccountant.pro.ui.theme.ActionOrange
import com.smartaccountant.pro.ui.theme.TopBarDark
import com.smartaccountant.pro.ui.viewmodel.MainViewModel

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val navigateRoute: String
)

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    openVoucherId: String? = null,
    onVoucherOpened: () -> Unit = {},
    openInvoiceList: Boolean = false,
    onInvoiceListOpened: () -> Unit = {},
    openAuditLog: Boolean = false,
    onAuditLogOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    val isRootDestination = currentRoute == Screen.Dashboard.route || currentRoute == null
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isRootDestination) {
        showExitConfirmDialog = true
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("تأكيد الخروج", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت تأكيد من رغبتك في الخروج من تطبيق المحاسب الذكي برو؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) {
                    Text("خروج", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    LaunchedEffect(openVoucherId, openInvoiceList, openAuditLog) {
        when {
            openVoucherId?.isNotBlank() == true -> {
                navController.navigate(Screen.VoucherDetails.createRoute(openVoucherId)) {
                    launchSingleTop = true
                }
                onVoucherOpened()
            }
            openInvoiceList -> {
                navController.navigate(Screen.InvoicesList.route) {
                    launchSingleTop = true
                }
                onInvoiceListOpened()
            }
            openAuditLog -> {
                navController.navigate(Screen.AuditLogs.route) {
                    launchSingleTop = true
                }
                onAuditLogOpened()
            }
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem("الرئيسية", Icons.Default.Home, Screen.Dashboard.route, Screen.Dashboard.route),
        BottomNavItem("الفواتير", Icons.AutoMirrored.Filled.ReceiptLong, Screen.InvoicesList.route, Screen.InvoicesList.route),
        BottomNavItem("الحسابات", Icons.Default.AccountBalance, Screen.Accounts.route, Screen.Accounts.createRoute()),
        BottomNavItem("التقارير", Icons.Default.BarChart, Screen.Reports.route, Screen.Reports.route),
        BottomNavItem("المزيد", Icons.Default.Menu, Screen.More.route, Screen.More.route)
    )
    val topLevelRoutes = bottomNavItems.map { it.route }.toSet()
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.navigateRoute) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = TopBarDark,
                                indicatorColor = ActionOrange,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = Modifier.padding(innerPadding).imePadding()
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(mainViewModel = mainViewModel, navController = navController)
        }
        composable(
            route = Screen.Accounts.route,
            arguments = listOf(navArgument("category") { nullable = true; defaultValue = null })
        ) {
            AccountsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAccountStatement = { accountId ->
                    navController.navigate(Screen.AccountStatement.createRoute(accountId))
                },
                onNavigateToPartyProfile = { accountId ->
                    navController.navigate(Screen.PartyProfile.createRoute(accountId))
                }
            )
        }
        composable(
            route = Screen.AccountStatement.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStack ->
            val accountId = backStack.arguments?.getString("accountId") ?: ""
            AccountStatementScreen(
                accountId = accountId,
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToInvoice = { invoiceId -> navController.navigate(Screen.InvoiceDetails.createRoute(invoiceId)) },
                onNavigateToVoucher = { voucherId -> navController.navigate(Screen.VoucherDetails.createRoute(voucherId)) },
                onNavigateToNewVoucher = { accId -> navController.navigate(Screen.VoucherEntry.createRoute(type = "RECEIPT", accountId = accId)) }
            )
        }
        composable(
            route = Screen.PartyProfile.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStack ->
            val accountId = backStack.arguments?.getString("accountId") ?: ""
            PartyProfileScreen(accountId = accountId, mainViewModel = mainViewModel, navController = navController)
        }
        composable(
            route = Screen.VoucherDetails.route,
            arguments = listOf(navArgument("voucherId") { type = NavType.StringType })
        ) { backStack ->
            val voucherId = backStack.arguments?.getString("voucherId") ?: ""
            VoucherDetailsScreen(
                voucherId = voucherId,
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onEdit = { vid -> navController.navigate(Screen.VoucherEntry.createRoute(voucherId = vid)) }
            )
        }
        composable(
            route = Screen.VoucherEntry.route,
            arguments = listOf(
                navArgument("type") { defaultValue = VoucherType.RECEIPT.name },
                navArgument("accountId") { defaultValue = "" },
                navArgument("voucherId") { defaultValue = "" }
            )
        ) { backStack ->
            val typeStr = backStack.arguments?.getString("type") ?: VoucherType.RECEIPT.name
            val type = runCatching { VoucherType.valueOf(typeStr) }.getOrDefault(VoucherType.RECEIPT)
            val accountId = backStack.arguments?.getString("accountId").orEmpty()
            val editVoucherId = backStack.arguments?.getString("voucherId")?.takeIf { it.isNotBlank() }
            VoucherEntryScreen(initialType = type, initialAccountId = accountId, editVoucherId = editVoucherId, mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Remittances.route) {
            RemittanceScreen(mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.CashBoxesAndCurrencies.route) {
            CashBoxesAndCurrenciesScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToBoxStatement = { boxId -> navController.navigate(Screen.CashBoxStatement.createRoute(boxId)) }
            )
        }
        composable(
            route = Screen.CashBoxStatement.route,
            arguments = listOf(navArgument("cashBoxId") { type = NavType.StringType })
        ) { backStack ->
            val cashBoxId = backStack.arguments?.getString("cashBoxId") ?: ""
            CashBoxStatementScreen(
                cashBoxId = cashBoxId,
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToVoucher = { voucherId -> navController.navigate(Screen.VoucherDetails.createRoute(voucherId)) },
                onNavigateToInvoice = { invoiceId -> navController.navigate(Screen.InvoiceDetails.createRoute(invoiceId)) }
            )
        }
        composable(
            route = Screen.InvoiceEditor.route,
            arguments = listOf(
                navArgument("type") { defaultValue = InvoiceType.SALE.name },
                navArgument("accountId") { defaultValue = "" }
            )
        ) { backStack ->
            val typeStr = backStack.arguments?.getString("type") ?: InvoiceType.SALE.name
            val type = runCatching { InvoiceType.valueOf(typeStr) }.getOrDefault(InvoiceType.SALE)
            val accountId = backStack.arguments?.getString("accountId").orEmpty()
            InvoiceEditorScreen(initialType = type, mainViewModel = mainViewModel, onBack = { navController.popBackStack() }, preselectedAccountId = accountId)
        }
        composable(Screen.InvoicesList.route) {
            InvoicesListScreen(mainViewModel = mainViewModel, navController = navController)
        }
        composable(
            route = Screen.InvoiceDetails.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })
        ) { backStack ->
            val invoiceId = backStack.arguments?.getString("invoiceId") ?: ""
            InvoiceDetailsScreen(invoiceId = invoiceId, mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Inventory.route) {
            InventoryScreen(mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Reports.route) {
            ReportsScreen(
                mainViewModel = mainViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AdminUsers.route) {
            AdminUsersScreen(mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.AuditLogs.route) {
            AuditLogScreen(mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.GlobalSearch.route) {
            GlobalSearchScreen(
                mainViewModel = mainViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onManageRecurring = { navController.navigate(Screen.RecurringTransactions.route) }
            )
        }
        composable(Screen.RecurringTransactions.route) {
            RecurringTransactionsScreen(mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.GeneralJournal.route) {
            GeneralJournalScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToVoucher = { voucherId -> navController.navigate(Screen.VoucherDetails.createRoute(voucherId)) }
            )
        }
        composable(Screen.Pos.route) {
            PosScreen(mainViewModel = mainViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.More.route) {
            MoreScreen(
                mainViewModel = mainViewModel,
                onNavigateToAdmin = { navController.navigate(Screen.AdminUsers.route) },
                onNavigateToAudit = { navController.navigate(Screen.AuditLogs.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToGeneralJournal = { navController.navigate(Screen.GeneralJournal.route) },
                onNavigateToCostCenters = { navController.navigate(Screen.CostCenters.route) },
                onNavigateToGlobalSearch = { navController.navigate(Screen.GlobalSearch.route) }
            )
        }
    }
    }

    AppUpdateDialog(mainViewModel = mainViewModel)
}

@Composable
private fun AppUpdateDialog(mainViewModel: MainViewModel) {
    val update by mainViewModel.appUpdate.collectAsState()
    val info = update.info
    val mandatory = info?.mandatory == true

    when (update.status) {
        AppUpdateStatus.AVAILABLE -> AlertDialog(
            onDismissRequest = { if (!mandatory) mainViewModel.dismissAppUpdate() },
            title = { Text("تحديث جديد متوفر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الإصدار ${info?.versionName?.ifBlank { info?.versionCode?.toString() ?: "" }}")
                    info?.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (mandatory) {
                        Text("هذا التحديث إلزامي لاستمرار استخدام التطبيق", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mainViewModel.startAppUpdateDownload() }) { Text("تنزيل الآن") }
            },
            dismissButton = if (mandatory) null else {
                { TextButton(onClick = { mainViewModel.dismissAppUpdate() }) { Text("لاحقاً") } }
            }
        )
        AppUpdateStatus.DOWNLOADING -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("جاري تنزيل التحديث في الخلفية...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            if (update.message.isNotBlank()) update.message else "يمكنك الاستمرار في استخدام التطبيق بحرية",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { mainViewModel.dismissAppUpdate() }) {
                        Text("إخفاء", fontSize = 12.sp)
                    }
                }
            }
        }
        AppUpdateStatus.READY -> AlertDialog(
            onDismissRequest = { if (!mandatory) mainViewModel.dismissAppUpdate() },
            title = { Text("التحديث جاهز") },
            text = { Text("تم تنزيل التحديث بنجاح. اضغط تثبيت للمتابعة.") },
            confirmButton = {
                Button(onClick = { mainViewModel.installAppUpdate() }) { Text("تثبيت") }
            },
            dismissButton = if (mandatory) null else {
                { TextButton(onClick = { mainViewModel.dismissAppUpdate() }) { Text("لاحقاً") } }
            }
        )
        AppUpdateStatus.ERROR -> AlertDialog(
            onDismissRequest = { mainViewModel.dismissAppUpdate() },
            title = { Text("تعذر تحديث التطبيق") },
            text = { Text(update.message.ifBlank { "حدث خطأ أثناء تنزيل التحديث" }) },
            confirmButton = {
                Button(onClick = { mainViewModel.checkForAppUpdate() }) { Text("إعادة المحاولة") }
            },
            dismissButton = { TextButton(onClick = { mainViewModel.dismissAppUpdate() }) { Text("إغلاق") } }
        )
        AppUpdateStatus.IDLE -> Unit
    }
}
