package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.domain.repository.PartyLedgerEntry
import com.smartaccountant.pro.domain.repository.PartyProfileData
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.EmptyState
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.components.UiErrorMessages
import com.smartaccountant.pro.ui.navigation.Screen
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val partyTabs = listOf("كشف الحساب", "الفواتير", "المدفوعات", "الملاحظات")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyProfileScreen(
    accountId: String,
    mainViewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id.orEmpty()
    val user = session.currentUser
    var profile by remember(accountId, companyId) { mutableStateOf<PartyProfileData?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permissions = PermissionManager.permissionsOf(user)

    LaunchedEffect(accountId, companyId, user?.id) {
        val currentUser = user ?: return@LaunchedEffect
        if (companyId.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            profile = mainViewModel.container.partyRepository.getPartyProfile(currentUser, companyId, accountId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = UiErrorMessages.fromThrowable(e, "تعذر تحميل ملف الطرف")
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.account?.name ?: "ملف الطرف") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    val party = profile?.account
                    if (party != null) {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val activeCompany = session.activeCompany ?: return@launch
                                    val pdfFile = mainViewModel.container.pdfGenerator.generateAccountStatementPdf(
                                        company = activeCompany,
                                        account = party,
                                        currency = session.currencies.find { it.id == party.defaultCurrencyId } ?: session.currencies.first(),
                                        rows = emptyList(),
                                        totalDebit = 0.0,
                                        totalCredit = 0.0,
                                        netBalance = profile?.currentBalance ?: 0.0,
                                        settings = session.settings
                                    )
                                    val printers = mainViewModel.container.bluetoothPrinter.getPairedPrinters()
                                    val targetDevice = printers.firstOrNull()
                                    if (targetDevice != null) {
                                        mainViewModel.container.bluetoothPrinter.printArabicReceiptAsRaster(
                                            deviceAddress = targetDevice.address,
                                            receiptContent = pdfFile.readText(),
                                            is80mm = true
                                        )
                                    } else {
                                        mainViewModel.showMessage("يرجى إقران طابعة بلوتوث أولاً")
                                    }
                                } catch (e: Exception) {
                                    mainViewModel.showError(e, "فشل الطباعة")
                                }
                            }
                        }) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة")
                        }

                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val activeCompany = session.activeCompany ?: return@launch
                                    val pdfFile = mainViewModel.container.pdfGenerator.generateAccountStatementPdf(
                                        company = activeCompany,
                                        account = party,
                                        currency = session.currencies.find { it.id == party.defaultCurrencyId } ?: session.currencies.first(),
                                        rows = emptyList(),
                                        totalDebit = 0.0,
                                        totalCredit = 0.0,
                                        netBalance = profile?.currentBalance ?: 0.0,
                                        settings = session.settings
                                    )
                                    MessagingShareService.openPdf(context, pdfFile)
                                } catch (e: Exception) {
                                    mainViewModel.showError(e, "فشل تصدير كشف الحساب")
                                }
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                        }

                        if (party.phone.isNotBlank()) {
                            IconButton(onClick = {
                                val currentUser = user ?: return@IconButton
                                scope.launch {
                                    try {
                                        val message = mainViewModel.container.partyRepository.createPaymentReminder(
                                            currentUser, companyId, accountId
                                        )
                                        MessagingShareService.sendWhatsAppMessage(context, party.phone, message)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        mainViewModel.showError(e, "تعذر فتح واتساب")
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال واتساب", tint = CreditGreen)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewPartyProfile }) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
                profile?.let { data ->
                    val invoiceType = when (data.account.category) {
                        AccountCategory.SUPPLIERS -> InvoiceType.PURCHASE
                        else -> InvoiceType.SALE
                    }
                    val canCreateInvoice = when (invoiceType) {
                        InvoiceType.PURCHASE -> permissions.canCreatePurchaseInvoice
                        else -> permissions.canCreateSaleInvoice
                    }
                    PartySummaryCard(data)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        partyTabs.forEachIndexed { index, title ->
                            FilterChip(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                label = { Text(title) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (permissions.canSendPaymentReminder && data.account.phone.isNotBlank() &&
                            data.receivableOrPayable > 0.0
                        ) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val currentUser = user ?: return@FilledTonalButton
                                    scope.launch {
                                        try {
                                            val message = mainViewModel.container.partyRepository.createPaymentReminder(
                                                currentUser, companyId, accountId
                                            )
                                            MessagingShareService.sendWhatsAppMessage(context, data.account.phone, message)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            mainViewModel.showError(e, "تعذر إرسال تذكير الدفع")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("تذكير دفع")
                            }
                        }
                        if (canCreateInvoice) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    navController.navigate(
                                        Screen.InvoiceEditor.createRoute(invoiceType.name, data.account.id)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("فاتورة جديدة")
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            0 -> AccountStatementContent(
                                accountId = accountId,
                                mainViewModel = mainViewModel,
                                showAccountSelector = false,
                                onNavigateToInvoice = { invoiceId -> navController.navigate(Screen.InvoiceDetails.createRoute(invoiceId)) },
                                onNavigateToVoucher = { voucherId -> navController.navigate(Screen.VoucherDetails.createRoute(voucherId)) },
                                onNavigateToNewVoucher = { accId -> navController.navigate(Screen.VoucherEntry.createRoute(type = "RECEIPT", accountId = accId)) }
                            )
                            1 -> PartyInvoicesTab(
                                data = data,
                                navController = navController,
                                actionLabel = if (canCreateInvoice) "أضف أول فاتورة" else "كشف الحساب",
                                onAction = {
                                    if (canCreateInvoice) {
                                        navController.navigate(Screen.InvoiceEditor.createRoute(invoiceType.name, data.account.id))
                                    } else selectedTab = 0
                                }
                            )
                            2 -> PartyPaymentsTab(data) { selectedTab = 0 }
                            else -> PartyNotesTab(data.account.notes) { selectedTab = 0 }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartySummaryCard(data: PartyProfileData) {
    val account = data.account
    val withinCreditLimit = account.creditLimit <= 0.0 || data.receivableOrPayable <= account.creditLimit
    val statusColor = if (withinCreditLimit) CreditGreen else DebitRed
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(account.category.arabicTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.12f)) {
                    Text(
                        if (withinCreditLimit) "ضمن الحد" else "تجاوز الحد",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            HorizontalDivider()
            PartyInfoRow("الهاتف", account.phone.ifBlank { "غير مسجل" })
            PartyInfoRow("البريد", account.email.ifBlank { "غير مسجل" })
            PartyInfoRow("حد الائتمان", if (account.creditLimit > 0) CurrencyFormatter.format(account.creditLimit) else "غير محدد")
            PartyInfoRow(
                "الرصيد الحالي",
                "${CurrencyFormatter.format(kotlin.math.abs(data.currentBalance))} ${if (data.currentBalance > 0) "مدين" else if (data.currentBalance < 0) "دائن" else "متوازن"}"
            )
            PartyInfoRow("المبلغ المستحق", CurrencyFormatter.format(data.receivableOrPayable))
            Text(
                ArabicTafqeet.convert(data.receivableOrPayable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PartyInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PartyStatementTab(
    entries: List<PartyLedgerEntry>,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("حركات الحساب مع الرصيد التراكمي بعد كل حركة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        entries.asReversed().forEach { entry ->
            PartyLedgerCard(entry)
        }
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.AccountBalanceWallet,
                title = "لا توجد حركات مسجلة",
                subtitle = "ستظهر هنا الفواتير والسندات المرتبطة بهذا الطرف.",
                actionLabel = actionLabel,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun PartyPaymentsTab(data: PartyProfileData, onAction: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("الرصيد التراكمي الحالي", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.format(kotlin.math.abs(data.currentBalance)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (data.currentBalance <= 0) CreditGreen else DebitRed
                )
                Text(if (data.currentBalance <= 0) "دائن / لصالح الطرف" else "مدين / على الطرف", style = MaterialTheme.typography.bodySmall)
            }
        }
        data.payments.asReversed().forEach { entry -> PartyLedgerCard(entry) }
        if (data.payments.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Payments,
                title = "لا توجد مدفوعات مسجلة",
                subtitle = "ستظهر هنا سندات القبض والصرف المرتبطة بهذا الطرف.",
                actionLabel = "عرض كشف الحساب",
                onAction = onAction
            )
        }
    }
}

@Composable
private fun PartyLedgerCard(entry: PartyLedgerEntry) {
    val color = when {
        entry.balanceChange < 0 -> CreditGreen
        entry.balanceChange > 0 -> DebitRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.description, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(DateUtils.formatDate(entry.dateMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(CurrencyFormatter.format(entry.amount), fontWeight = FontWeight.Bold, color = color)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("تغير الرصيد: ${CurrencyFormatter.format(entry.balanceChange)}", style = MaterialTheme.typography.bodySmall, color = color)
                Text("الرصيد: ${CurrencyFormatter.format(kotlin.math.abs(entry.runningBalance))}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PartyInvoicesTab(
    data: PartyProfileData,
    navController: NavController,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.invoices.asReversed().forEach { invoice ->
            val outstanding = data.invoiceOutstandingById[invoice.id]
                ?: ((invoice.netTotal - invoice.paidAmount).coerceAtLeast(0.0) * invoice.exchangeRate)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate(Screen.InvoiceDetails.createRoute(invoice.id))
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${invoice.type.arabicTitle} ${invoice.displayNumber.ifBlank { "#${invoice.invoiceNumber}" }}", fontWeight = FontWeight.Bold)
                        Text(
                            "${DateUtils.formatDate(invoice.dateMillis)} • الاستحقاق ${DateUtils.formatDate(invoice.dueDateMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(if (invoice.paymentMethod.name == "CREDIT") "آجل" else "نقدي", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(CurrencyFormatter.format(invoice.netTotal * invoice.exchangeRate), fontWeight = FontWeight.Bold)
                        Text("متبقي ${CurrencyFormatter.format(outstanding)}", color = if (outstanding > 0.0) DebitRed else CreditGreen, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (data.invoices.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ReceiptLong,
                title = "لا توجد فواتير مرحلة",
                subtitle = "ستظهر هنا الفواتير المرحلة لهذا الطرف.",
                actionLabel = actionLabel,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun PartyNotesTab(notes: String, onAction: () -> Unit) {
    if (notes.isBlank()) {
        EmptyState(
            icon = Icons.Default.StickyNote2,
            title = "لا توجد ملاحظات",
            subtitle = "لا توجد ملاحظات محفوظة لهذا الطرف حالياً.",
            actionLabel = "كشف الحساب",
            onAction = onAction
        )
    } else {
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text(notes, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
