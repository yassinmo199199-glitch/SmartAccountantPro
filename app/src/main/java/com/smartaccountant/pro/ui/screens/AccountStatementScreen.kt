package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.PaymentMethod
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.services.StatementRow
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.ActionOrange
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.TopBarDark
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

data class StatementRowUiModel(
    val id: String,
    val dateMillis: Long,
    val docNumber: String,
    val refNumber: String = "",
    val typeName: String,
    val description: String,
    val lineNote: String = "",
    val debit: Double,
    val credit: Double,
    val balanceAfter: Double,
    val currencyId: String,
    val isInvoice: Boolean,
    val hasAttachment: Boolean = false
)

enum class PeriodQuickFilter {
    ALL, MONTHLY, YEARLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountStatementScreen(
    accountId: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToInvoice: (String) -> Unit,
    onNavigateToVoucher: (String) -> Unit,
    onNavigateToNewVoucher: (String) -> Unit = {}
) {
    AccountStatementContent(
        accountId = accountId,
        mainViewModel = mainViewModel,
        showAccountSelector = false,
        onBack = onBack,
        onNavigateToInvoice = onNavigateToInvoice,
        onNavigateToVoucher = onNavigateToVoucher,
        onNavigateToNewVoucher = onNavigateToNewVoucher
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountStatementContent(
    accountId: String,
    mainViewModel: MainViewModel,
    showAccountSelector: Boolean = false,
    onAccountSelected: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onNavigateToInvoice: (String) -> Unit,
    onNavigateToVoucher: (String) -> Unit,
    onNavigateToNewVoucher: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())
    var currentAccountId by remember(accountId) { mutableStateOf(accountId.ifBlank { accounts.firstOrNull()?.id ?: "" }) }

    LaunchedEffect(accounts) {
        if (currentAccountId.isBlank() && accounts.isNotEmpty()) {
            currentAccountId = accounts.first().id
            onAccountSelected(currentAccountId)
        }
    }

    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var selectedCurrencyId by remember { mutableStateOf<String?>(null) }

    // Search and Top Bar Options
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Period Quick Filters
    var quickFilter by remember { mutableStateOf(PeriodQuickFilter.ALL) }
    var showPreviousBalance by remember { mutableStateOf(true) }

    // Date Range Filters
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    // Display Options
    var showRunningBalance by remember { mutableStateOf(true) }
    var showLineNotes by remember { mutableStateOf(true) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    // Data
    var statementRows by remember { mutableStateOf<List<StatementRowUiModel>>(emptyList()) }
    var previousBalance by remember { mutableDoubleStateOf(0.0) }
    var netBalance by remember { mutableDoubleStateOf(0.0) }
    var totalDebit by remember { mutableDoubleStateOf(0.0) }
    var totalCredit by remember { mutableDoubleStateOf(0.0) }

    fun shiftDates(days: Int) {
        val shiftMillis = days * 24L * 60L * 60L * 1000L
        if (fromDateMillis != null) fromDateMillis = fromDateMillis!! + shiftMillis
        if (toDateMillis != null) toDateMillis = toDateMillis!! + shiftMillis
    }

    fun applyQuickFilter(filter: PeriodQuickFilter) {
        quickFilter = filter
        val cal = Calendar.getInstance()
        when (filter) {
            PeriodQuickFilter.ALL -> {
                fromDateMillis = null
                toDateMillis = null
            }
            PeriodQuickFilter.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                fromDateMillis = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                toDateMillis = cal.timeInMillis
            }
            PeriodQuickFilter.YEARLY -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                fromDateMillis = cal.timeInMillis
                cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                toDateMillis = cal.timeInMillis
            }
        }
    }

    fun loadStatementData() {
        if (companyId.isBlank() || currentAccountId.isBlank()) return
        scope.launch {
            val acc = mainViewModel.container.db.accountDao().getAccountInCompany(companyId, currentAccountId)
            if (acc == null) return@launch
            account = acc
            if (selectedCurrencyId == null) selectedCurrencyId = acc.defaultCurrencyId

            val voucherLines = mainViewModel.container.db.reportsDao().getAccountStatementLines(companyId, acc.id)
            val directVouchers = mainViewModel.container.db.voucherDao().getAccountVouchers(companyId, acc.id)
                .filter { !it.isDeleted }
            val invoices = mainViewModel.container.db.invoiceDao().getAllAccountInvoices(companyId, acc.id)
                .filter { it.isPosted && !it.isDeleted }

            val autoReceiptIds = invoices.filter { it.paymentMethod == PaymentMethod.CASH }
                .map { "invpay-${it.id}" }.toSet()

            val processedVoucherIds = mutableSetOf<String>()

            var runningBalance = acc.openingDebit - acc.openingCredit
            var prevBal = runningBalance

            val rows = mutableListOf<StatementRowUiModel>()

            for (line in voucherLines) {
                if (line.voucher.id in autoReceiptIds) continue
                processedVoucherIds.add(line.voucher.id)

                val isBeforeFromDate = fromDateMillis != null && line.voucher.dateMillis < fromDateMillis!!
                val isAfterToDate = toDateMillis != null && line.voucher.dateMillis > toDateMillis!!

                val isSameCurrency = selectedCurrencyId == null || line.voucher.currencyId == selectedCurrencyId
                val valDebit = if (isSameCurrency) line.debit else line.debit * line.voucher.exchangeRate
                val valCredit = if (isSameCurrency) line.credit else line.credit * line.voucher.exchangeRate

                if (isBeforeFromDate) {
                    prevBal += (valDebit - valCredit)
                    runningBalance += (valDebit - valCredit)
                } else if (!isAfterToDate) {
                    runningBalance += (valDebit - valCredit)

                    val desc = line.voucher.description.ifBlank { "سند ${line.voucher.type.arabicTitle}" }

                    rows.add(
                        StatementRowUiModel(
                            id = line.voucher.id,
                            dateMillis = line.voucher.dateMillis,
                            docNumber = if (line.voucher.displayNumber.isNotBlank()) line.voucher.displayNumber else "${line.voucher.voucherNumber}",
                            typeName = line.voucher.type.arabicTitle,
                            description = desc,
                            lineNote = line.lineNote,
                            debit = valDebit,
                            credit = valCredit,
                            balanceAfter = runningBalance,
                            currencyId = line.voucher.currencyId,
                            isInvoice = false,
                            hasAttachment = line.voucher.attachmentUrl.isNotBlank()
                        )
                    )
                }
            }

            for (v in directVouchers) {
                if (v.id in processedVoucherIds || v.id in autoReceiptIds) continue
                processedVoucherIds.add(v.id)

                var vDebit = 0.0
                var vCredit = 0.0

                val isSameCurrency = selectedCurrencyId == null || v.currencyId == selectedCurrencyId
                val amt = if (isSameCurrency) v.amount else v.amount * v.exchangeRate

                when (v.type) {
                    com.smartaccountant.pro.data.model.VoucherType.RECEIPT, com.smartaccountant.pro.data.model.VoucherType.REMITTANCE_IN -> {
                        if (v.accountId == acc.id) vCredit = amt
                        else vDebit = amt
                    }
                    com.smartaccountant.pro.data.model.VoucherType.PAYMENT, com.smartaccountant.pro.data.model.VoucherType.REMITTANCE_OUT -> {
                        if (v.accountId == acc.id) vDebit = amt
                        else vCredit = amt
                    }
                    else -> {
                        if (v.accountId == acc.id) vDebit = amt
                        else if (v.counterAccountId == acc.id) vCredit = amt
                    }
                }

                if (vDebit == 0.0 && vCredit == 0.0) continue

                val isBeforeFromDate = fromDateMillis != null && v.dateMillis < fromDateMillis!!
                val isAfterToDate = toDateMillis != null && v.dateMillis > toDateMillis!!

                if (isBeforeFromDate) {
                    prevBal += (vDebit - vCredit)
                    runningBalance += (vDebit - vCredit)
                } else if (!isAfterToDate) {
                    runningBalance += (vDebit - vCredit)
                    rows.add(
                        StatementRowUiModel(
                            id = v.id,
                            dateMillis = v.dateMillis,
                            docNumber = if (v.displayNumber.isNotBlank()) v.displayNumber else "${v.voucherNumber}",
                            typeName = v.type.arabicTitle,
                            description = v.description.ifBlank { "سند ${v.type.arabicTitle}" },
                            lineNote = "",
                            debit = vDebit,
                            credit = vCredit,
                            balanceAfter = runningBalance,
                            currencyId = v.currencyId,
                            isInvoice = false,
                            hasAttachment = v.attachmentUrl.isNotBlank()
                        )
                    )
                }
            }

            for (inv in invoices) {
                val isBeforeFromDate = fromDateMillis != null && inv.dateMillis < fromDateMillis!!
                val isAfterToDate = toDateMillis != null && inv.dateMillis > toDateMillis!!

                val isSameCurrency = selectedCurrencyId == null || inv.currencyId == selectedCurrencyId
                val invTotal = if (isSameCurrency) inv.netTotal else inv.netTotal * inv.exchangeRate
                val invPaid = if (isSameCurrency) inv.paidAmount else inv.paidAmount * inv.exchangeRate

                var vDebit = 0.0
                var vCredit = 0.0

                when (inv.type) {
                    InvoiceType.SALE, InvoiceType.FREE_SALE -> {
                        vDebit = invTotal
                        if (inv.paymentMethod == PaymentMethod.CASH || invPaid > 0) vCredit = invPaid
                    }
                    InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> {
                        vCredit = invTotal
                        if (inv.paymentMethod == PaymentMethod.CASH || invPaid > 0) vDebit = invPaid
                    }
                    InvoiceType.SALE_RETURN -> vCredit = invTotal
                    InvoiceType.PURCHASE_RETURN -> vDebit = invTotal
                    else -> {}
                }

                if (isBeforeFromDate) {
                    prevBal += (vDebit - vCredit)
                    runningBalance += (vDebit - vCredit)
                } else if (!isAfterToDate) {
                    runningBalance += (vDebit - vCredit)
                    rows.add(
                        StatementRowUiModel(
                            id = inv.id,
                            dateMillis = inv.dateMillis,
                            docNumber = if (inv.displayNumber.isNotBlank()) inv.displayNumber else "${inv.invoiceNumber}",
                            typeName = inv.type.arabicTitle,
                            description = if (inv.notes.isNotBlank()) inv.notes else "فاتورة ${inv.type.arabicTitle}",
                            lineNote = "",
                            debit = vDebit,
                            credit = vCredit,
                            balanceAfter = runningBalance,
                            currencyId = inv.currencyId,
                            isInvoice = true
                        )
                    )
                }
            }

            var sortedRows = rows.sortedBy { it.dateMillis }
            if (searchQuery.isNotBlank()) {
                sortedRows = sortedRows.filter {
                    it.description.contains(searchQuery, ignoreCase = true) ||
                            it.docNumber.contains(searchQuery) ||
                            it.typeName.contains(searchQuery, ignoreCase = true)
                }
            }

            var currentBal = prevBal
            var sumDebit = 0.0
            var sumCredit = 0.0

            val finalRows = sortedRows.map { row ->
                currentBal += (row.debit - row.credit)
                sumDebit += row.debit
                sumCredit += row.credit
                row.copy(balanceAfter = currentBal)
            }

            previousBalance = prevBal
            statementRows = finalRows
            totalDebit = sumDebit
            totalCredit = sumCredit
            netBalance = currentBal
        }
    }

    LaunchedEffect(currentAccountId, selectedCurrencyId, fromDateMillis, toDateMillis, searchQuery) {
        loadStatementData()
    }

    val acc = account
    val activeCurrency = session.currencies.find { it.id == selectedCurrencyId } ?: session.currencies.firstOrNull()

    fun generateStatementPdfFile(): java.io.File {
        val activeCompany = session.activeCompany ?: throw IllegalStateException("لا توجد شركة نشطة")
        val currentAcc = acc ?: throw IllegalStateException("لا يوجد حساب محدد")
        return mainViewModel.container.pdfGenerator.generateAccountStatementPdf(
            company = activeCompany,
            account = currentAcc,
            currency = activeCurrency ?: session.currencies.first(),
            rows = statementRows.map { r ->
                StatementRow(
                    dateMillis = r.dateMillis,
                    docType = r.typeName,
                    docNumber = r.docNumber.filter { it.isDigit() }.toLongOrNull() ?: 0L,
                    debit = r.debit,
                    credit = r.credit,
                    description = if (showLineNotes && r.lineNote.isNotBlank()) "${r.description} | ${r.lineNote}" else r.description
                )
            },
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            netBalance = netBalance,
            settings = session.settings
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في الكشف...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    } else {
                        Text(acc?.name ?: "كشف الحساب", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "بحث", tint = Color.White)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                val pdfFile = generateStatementPdfFile()
                                val printers = mainViewModel.container.bluetoothPrinter.getPairedPrinters()
                                val targetDevice = printers.firstOrNull()
                                if (targetDevice != null) {
                                    mainViewModel.container.bluetoothPrinter.printArabicReceiptAsRaster(
                                        deviceAddress = targetDevice.address,
                                        receiptContent = pdfFile.readText(),
                                        is80mm = true
                                    )
                                } else {
                                    mainViewModel.showMessage("يرجى إقران طابعة بلوتوث حرارية أولاً")
                                }
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل الطباعة")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة", tint = Color.White)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                val pdfFile = generateStatementPdfFile()
                                MessagingShareService.openPdf(context, pdfFile)
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل تصدير كشف الحساب")
                            }
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", tint = Color.White)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                val currentAcc = acc ?: return@launch
                                val pdfFile = generateStatementPdfFile()
                                val messageText = "كشف حساب: ${currentAcc.name}\nالرصيد النهائي: ${CurrencyFormatter.formatAmount(kotlin.math.abs(netBalance))} ${activeCurrency?.symbol ?: ""}"
                                MessagingShareService.sendWhatsAppMessage(context, currentAcc.phone, messageText)
                                MessagingShareService.openPdf(context, pdfFile)
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل المشاركة عبر واتساب")
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "مشاركة واتساب", tint = CreditGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarDark)
            )
        },
        floatingActionButton = {
            if (acc != null) {
                FloatingActionButton(
                    onClick = { onNavigateToNewVoucher(acc.id) },
                    containerColor = ActionOrange,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "سند جديد", modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            // ===== شريط الإجماليات السفلي الداكن المماثل للصورة المرجعية =====
            Surface(
                color = Color(0xFF0F1F38),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("عليه : ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(CurrencyFormatter.formatAmount(totalDebit), color = DebitRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("له : ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(CurrencyFormatter.formatAmount(totalCredit), color = CreditGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الرصيد : ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = CurrencyFormatter.formatAmount(kotlin.math.abs(netBalance)),
                                color = if (netBalance > 0) DebitRed else if (netBalance < 0) CreditGreen else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = CurrencyFormatter.formatAmount(kotlin.math.abs(netBalance)) +
                                    if (netBalance > 0) " (مدين)" else if (netBalance < 0) " (دائن)" else " (مصفر)",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewStatements }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White)
            ) {
                // ===== اختيار الحساب (يظهر إذا كان مستدعياً من شاشة التقارير) =====
                if (showAccountSelector) {
                    var accountExpanded by remember { mutableStateOf(false) }
                    var accountSearchQuery by remember { mutableStateOf("") }
                    val filteredAccounts = remember(accounts, accountSearchQuery) {
                        if (accountSearchQuery.isBlank()) accounts
                        else accounts.filter { it.name.contains(accountSearchQuery, ignoreCase = true) || it.phone.contains(accountSearchQuery) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = accountExpanded,
                            onExpandedChange = { accountExpanded = it },
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            OutlinedTextField(
                                value = acc?.let { "${it.name} (${it.category.arabicTitle})" } ?: "اختر الحساب...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("عرض كشف حساب العميل / الحساب") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = accountExpanded,
                                onDismissRequest = { accountExpanded = false }
                            ) {
                                OutlinedTextField(
                                    value = accountSearchQuery,
                                    onValueChange = { accountSearchQuery = it },
                                    placeholder = { Text("ابحث عن حساب...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    singleLine = true
                                )
                                filteredAccounts.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text("${a.name} (${a.category.arabicTitle})") },
                                        onClick = {
                                            currentAccountId = a.id
                                            onAccountSelected(a.id)
                                            accountExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== قسم الفلاتر والخيارات العلوية المماثل للصورة المرجعية =====
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 1. اختيار العملة
                        var currencyExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = currencyExpanded,
                            onExpandedChange = { currencyExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = activeCurrency?.let { "${it.name} (${it.symbol})" } ?: "ريال يمني",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("العملة", fontSize = 11.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth().height(48.dp)
                            )
                            ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                                session.currencies.forEach { cur ->
                                    DropdownMenuItem(
                                        text = { Text("${cur.name} (${cur.symbol})") },
                                        onClick = { selectedCurrencyId = cur.id; currencyExpanded = false }
                                    )
                                }
                            }
                        }

                        // 2. خيارات التصفية السريعة (شهري / الكل / سنوي / رصيد سابق)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RadioButton(
                                    selected = quickFilter == PeriodQuickFilter.MONTHLY,
                                    onClick = { applyQuickFilter(PeriodQuickFilter.MONTHLY) }
                                )
                                Text("شهري", fontSize = 12.sp, fontWeight = FontWeight.Medium)

                                RadioButton(
                                    selected = quickFilter == PeriodQuickFilter.ALL,
                                    onClick = { applyQuickFilter(PeriodQuickFilter.ALL) }
                                )
                                Text("الكل", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                RadioButton(
                                    selected = quickFilter == PeriodQuickFilter.YEARLY,
                                    onClick = { applyQuickFilter(PeriodQuickFilter.YEARLY) }
                                )
                                Text("سنوي", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showPreviousBalance,
                                    onCheckedChange = { showPreviousBalance = it }
                                )
                                Text("رصيد سابق", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { showOptionsDialog = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "تصفية", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // 3. شريط التواريخ مع أزرار التنقل السريع [< من 1/1/2023] إلى [إلى 14/11/2026 >]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(onClick = { shiftDates(-30) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "السابق", modifier = Modifier.size(18.dp))
                            }

                            OutlinedButton(
                                onClick = { showFromDatePicker = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("من: ${if (fromDateMillis != null) DateUtils.formatDate(fromDateMillis!!) else "الكل"}", fontSize = 11.sp)
                            }

                            Text("إلى", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            OutlinedButton(
                                onClick = { showToDatePicker = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("إلى: ${if (toDateMillis != null) DateUtils.formatDate(toDateMillis!!) else "اليوم"}", fontSize = 11.sp)
                            }

                            IconButton(onClick = { shiftDates(+30) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "التالي", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // ===== ترويسة الأعمدة بالترتيب المطلوبة من اليمين إلى اليسار =====
                // (التاريخ , نوع المستند , رقم المستند , البيان , رقم المرجع , مدين , دائن , الرصيد)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("التاريخ", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text("نوع المستند", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text("رقم المستند", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text("البيان", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text("رقم المرجع", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text("مدين", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text("دائن", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    if (showRunningBalance) {
                        Text("الرصيد", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }
                    Text("إجراءات", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }

                // ===== قائمة حركات الكشف =====
                var activeMenuRowId by remember { mutableStateOf<String?>(null) }
                if (statementRows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد حركات مسجلة لهذا الحساب", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(statementRows) { row ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (row.isInvoice) onNavigateToInvoice(row.id)
                                                else onNavigateToVoucher(row.id)
                                            },
                                            onLongClick = {
                                                activeMenuRowId = row.id
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    // 1. التاريخ
                                    Text(
                                        text = DateUtils.formatDate(row.dateMillis),
                                        modifier = Modifier.width(85.dp),
                                        fontSize = 11.sp,
                                        color = Color(0xFF1565C0),
                                        fontWeight = FontWeight.Medium
                                    )

                                    // 2. نوع المستند
                                    Text(
                                        text = row.typeName,
                                        modifier = Modifier.width(90.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 3. رقم المستند
                                    Text(
                                        text = row.docNumber,
                                        modifier = Modifier.width(80.dp),
                                        fontSize = 11.sp,
                                        color = DebitRed,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // 4. البيان
                                    Text(
                                        text = row.description,
                                        modifier = Modifier.width(130.dp),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 5. رقم المرجع
                                    Text(
                                        text = row.refNumber.ifBlank { "—" },
                                        modifier = Modifier.width(75.dp),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )

                                    // 6. مدين
                                    Text(
                                        text = if (row.debit > 0) CurrencyFormatter.formatAmount(row.debit) else "—",
                                        modifier = Modifier.width(75.dp),
                                        color = DebitRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )

                                    // 7. دائن
                                    Text(
                                        text = if (row.credit > 0) CurrencyFormatter.formatAmount(row.credit) else "—",
                                        modifier = Modifier.width(75.dp),
                                        color = CreditGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )

                                    // 8. الرصيد
                                    if (showRunningBalance) {
                                        Text(
                                            text = CurrencyFormatter.formatAmount(kotlin.math.abs(row.balanceAfter)),
                                            modifier = Modifier.width(90.dp),
                                            color = Color(0xFF1565C0),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // أزرار التحكم السريعة للسطر
                                    Row(
                                        modifier = Modifier.width(85.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (row.isInvoice) onNavigateToInvoice(row.id)
                                                else onNavigateToVoucher(row.id)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(15.dp), tint = Color(0xFF1565C0))
                                        }

                                        IconButton(
                                            onClick = {
                                                if (row.isInvoice) onNavigateToInvoice(row.id)
                                                else onNavigateToVoucher(row.id)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(15.dp), tint = DebitRed)
                                        if (row.hasAttachment) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = "مرفق صورة", modifier = Modifier.size(14.dp), tint = DebitRed)
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                            }

                            DropdownMenu(
                                expanded = activeMenuRowId == row.id,
                                onDismissRequest = { activeMenuRowId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("عرض التفاصيل") },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                    onClick = {
                                        activeMenuRowId = null
                                        if (row.isInvoice) onNavigateToInvoice(row.id)
                                        else onNavigateToVoucher(row.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== حوار خيارات العرض المتقدمة =====
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("خيارات عرض وتصدير الكشف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إظهار عمود الرصيد التراكمي", fontSize = 13.sp)
                        Switch(checked = showRunningBalance, onCheckedChange = { showRunningBalance = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إظهار الرصيد السابق", fontSize = 13.sp)
                        Switch(checked = showPreviousBalance, onCheckedChange = { showPreviousBalance = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إظهار تفاصيل السطور والملاحظات", fontSize = 13.sp)
                        Switch(checked = showLineNotes, onCheckedChange = { showLineNotes = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOptionsDialog = false }) { Text("تطبيق") }
            }
        )
    }

    if (showFromDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = fromDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDateMillis = datePickerState.selectedDateMillis
                    showFromDatePicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showToDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toDateMillis = datePickerState.selectedDateMillis
                    showToDatePicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
}
}


