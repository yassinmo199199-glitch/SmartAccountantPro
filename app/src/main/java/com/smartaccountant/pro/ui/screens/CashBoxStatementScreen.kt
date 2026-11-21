package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.CashBoxEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.PaymentMethod
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

data class CashBoxMovementRow(
    val id: String,
    val dateMillis: Long,
    val docNumber: String,
    val typeName: String,
    val description: String,
    val inAmount: Double,  // مقبوضات / وارد (+)
    val outAmount: Double, // مصروفات / صادر (-)
    val balanceAfter: Double,
    val isInvoice: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBoxStatementScreen(
    cashBoxId: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToVoucher: (String) -> Unit,
    onNavigateToInvoice: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    var currentBoxId by remember { mutableStateOf(cashBoxId) }
    var selectedBox by remember { mutableStateOf<CashBoxEntity?>(null) }

    // Date Range Filters
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    // Options
    var showRunningBalance by remember { mutableStateOf(true) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    // Data
    var rows by remember { mutableStateOf<List<CashBoxMovementRow>>(emptyList()) }
    var previousBalance by remember { mutableDoubleStateOf(0.0) }
    var totalIn by remember { mutableDoubleStateOf(0.0) }
    var totalOut by remember { mutableDoubleStateOf(0.0) }
    var netBalance by remember { mutableDoubleStateOf(0.0) }

    fun loadMovementData() {
        if (companyId.isBlank() || currentBoxId.isBlank()) return
        scope.launch {
            val box = session.cashBoxes.find { it.id == currentBoxId }
                ?: mainViewModel.container.db.financeDao().getCashBoxById(currentBoxId)
            selectedBox = box
            if (box == null) return@launch

            val allVouchers = mainViewModel.container.db.reportsDao().getActiveVouchers(companyId)
            val allInvoices = mainViewModel.container.db.invoiceDao().getPostedInvoicesThroughDate(companyId, System.currentTimeMillis())
                .filter { !it.isDeleted }

            val autoReceiptIds = allInvoices.filter { it.paymentMethod == PaymentMethod.CASH }
                .map { "invpay-${it.id}" }.toSet()

            var prevBal = 0.0
            var runningBal = 0.0
            val movementList = mutableListOf<CashBoxMovementRow>()

            for (v in allVouchers) {
                if (v.id in autoReceiptIds) continue
                val isSourceBox = v.cashBoxId == box.id
                val isTargetBox = v.targetCashBoxId == box.id

                if (!isSourceBox && !isTargetBox) continue

                var inVal = 0.0
                var outVal = 0.0

                when (v.type) {
                    VoucherType.RECEIPT -> {
                        if (isSourceBox) inVal = v.amount * v.exchangeRate
                    }
                    VoucherType.PAYMENT -> {
                        if (isSourceBox) outVal = v.amount * v.exchangeRate
                    }
                    VoucherType.BOX_TRANSFER -> {
                        if (isSourceBox) outVal = v.amount * v.exchangeRate
                        if (isTargetBox) inVal = (v.targetAmount ?: v.amount) * v.exchangeRate
                    }
                    VoucherType.CURRENCY_EXCHANGE -> {
                        if (isSourceBox) outVal = v.amount * v.exchangeRate
                        if (isTargetBox) inVal = (v.targetAmount ?: v.amount) * v.exchangeRate
                    }
                    VoucherType.REMITTANCE_IN -> {
                        if (isSourceBox) inVal = v.amount * v.exchangeRate
                    }
                    VoucherType.REMITTANCE_OUT -> {
                        if (isSourceBox) outVal = v.amount * v.exchangeRate
                    }
                    else -> {
                        if (isSourceBox) {
                            if (v.amount > 0) inVal = v.amount * v.exchangeRate
                        }
                    }
                }

                if (inVal == 0.0 && outVal == 0.0) continue

                val isBeforeFrom = fromDateMillis != null && v.dateMillis < fromDateMillis!!
                val isAfterTo = toDateMillis != null && v.dateMillis > toDateMillis!!

                if (isBeforeFrom) {
                    prevBal += (inVal - outVal)
                    runningBal += (inVal - outVal)
                } else if (!isAfterTo) {
                    runningBal += (inVal - outVal)
                    movementList.add(
                        CashBoxMovementRow(
                            id = v.id,
                            dateMillis = v.dateMillis,
                            docNumber = if (v.displayNumber.isNotBlank()) v.displayNumber else "#${v.voucherNumber}",
                            typeName = v.type.arabicTitle,
                            description = if (v.description.isNotBlank()) v.description else "حركة ${v.type.arabicTitle}",
                            inAmount = inVal,
                            outAmount = outVal,
                            balanceAfter = runningBal,
                            isInvoice = false
                        )
                    )
                }
            }

            for (inv in allInvoices) {
                if (inv.cashBoxId != box.id && inv.paymentMethod != PaymentMethod.CASH) continue
                if (inv.cashBoxId != box.id) continue

                var inVal = 0.0
                var outVal = 0.0

                val netVal = inv.netTotal * inv.exchangeRate
                val paidVal = inv.paidAmount * inv.exchangeRate

                when (inv.type) {
                    InvoiceType.SALE, InvoiceType.FREE_SALE -> {
                        val cashVal = if (inv.paymentMethod == PaymentMethod.CASH) netVal else paidVal
                        if (cashVal > 0) inVal = cashVal
                    }
                    InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> {
                        val cashVal = if (inv.paymentMethod == PaymentMethod.CASH) netVal else paidVal
                        if (cashVal > 0) outVal = cashVal
                    }
                    InvoiceType.SALE_RETURN -> {
                        if (inv.paymentMethod == PaymentMethod.CASH) outVal = netVal
                    }
                    InvoiceType.PURCHASE_RETURN -> {
                        if (inv.paymentMethod == PaymentMethod.CASH) inVal = netVal
                    }
                    else -> {}
                }

                if (inVal == 0.0 && outVal == 0.0) continue

                val isBeforeFrom = fromDateMillis != null && inv.dateMillis < fromDateMillis!!
                val isAfterTo = toDateMillis != null && inv.dateMillis > toDateMillis!!

                if (isBeforeFrom) {
                    prevBal += (inVal - outVal)
                    runningBal += (inVal - outVal)
                } else if (!isAfterTo) {
                    runningBal += (inVal - outVal)
                    movementList.add(
                        CashBoxMovementRow(
                            id = inv.id,
                            dateMillis = inv.dateMillis,
                            docNumber = if (inv.displayNumber.isNotBlank()) inv.displayNumber else "#${inv.invoiceNumber}",
                            typeName = inv.type.arabicTitle,
                            description = if (inv.notes.isNotBlank()) inv.notes else "فاتورة ${inv.type.arabicTitle}",
                            inAmount = inVal,
                            outAmount = outVal,
                            balanceAfter = runningBal,
                            isInvoice = true
                        )
                    )
                }
            }

            val sortedList = movementList.sortedBy { it.dateMillis }
            var currentBal = prevBal
            var sumIn = 0.0
            var sumOut = 0.0

            val finalRows = sortedList.map { row ->
                currentBal += (row.inAmount - row.outAmount)
                sumIn += row.inAmount
                sumOut += row.outAmount
                row.copy(balanceAfter = currentBal)
            }

            previousBalance = prevBal
            totalIn = sumIn
            totalOut = sumOut
            netBalance = currentBal
            rows = finalRows
        }
    }

    LaunchedEffect(currentBoxId, fromDateMillis, toDateMillis) {
        loadMovementData()
    }

    val box = selectedBox
    val boxCurrency = session.currencies.find { it.id == box?.defaultCurrencyId } ?: session.currencies.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (box != null) "حركة ${box.name} (${if (box.isBank) "بنك" else "صندوق"})" else "حركة الصندوق",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = {
                    IconButton(onClick = { showOptionsDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "خيارات العرض")
                    }
                    IconButton(onClick = {
                        val activeCompany = session.activeCompany ?: return@IconButton
                        val activeBox = selectedBox ?: return@IconButton
                        scope.launch {
                            try {
                                val pdfFile = mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                                    company = activeCompany,
                                    reportTitle = "كشف حركة ${activeBox.name}",
                                    periodLabel = "الفترة: ${DateUtils.formatDate(fromDateMillis ?: 0L)} - ${DateUtils.formatDate(toDateMillis ?: System.currentTimeMillis())}",
                                    headers = listOf("التاريخ", "المستند", "البيان", "وارد (+)", "صادر (-)", "الرصيد"),
                                    rows = rows.map { r ->
                                        listOf(
                                            DateUtils.formatDate(r.dateMillis),
                                            r.docNumber,
                                            r.description,
                                            if (r.inAmount > 0) CurrencyFormatter.formatAmount(r.inAmount) else "-",
                                            if (r.outAmount > 0) CurrencyFormatter.formatAmount(r.outAmount) else "-",
                                            CurrencyFormatter.formatAmount(r.balanceAfter)
                                        )
                                    },
                                    footerRows = listOf(
                                        listOf("الإجمالي", "", "", CurrencyFormatter.formatAmount(totalIn), CurrencyFormatter.formatAmount(totalOut), CurrencyFormatter.formatAmount(netBalance))
                                    ),
                                    settings = session.settings
                                )
                                MessagingShareService.openPdf(context, pdfFile)
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل تصدير كشف حركة الصندوق")
                            }
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                    }
                }
            )
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewStatements }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // ===== شريط اختيار الصندوق =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    session.cashBoxes.forEach { b ->
                        FilterChip(
                            selected = currentBoxId == b.id,
                            onClick = { currentBoxId = b.id },
                            label = { Text(b.name, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(if (b.isBank) Icons.Default.AccountBalance else Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                // ===== بطاقة الملخص الفعلي للصندوق =====
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الرصيد السابق:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                CurrencyFormatter.formatAmount(previousBalance) + " " + (boxCurrency?.symbol ?: ""),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المقبوضات (+):", fontSize = 12.sp, color = CreditGreen, fontWeight = FontWeight.Medium)
                            Text(CurrencyFormatter.formatAmount(totalIn), fontWeight = FontWeight.Bold, color = CreditGreen)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المصروفات (-):", fontSize = 12.sp, color = DebitRed, fontWeight = FontWeight.Medium)
                            Text(CurrencyFormatter.formatAmount(totalOut), fontWeight = FontWeight.Bold, color = DebitRed)
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("الرصيد الحالي المتوفر:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                CurrencyFormatter.formatAmount(netBalance) + " " + (boxCurrency?.symbol ?: ""),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (netBalance >= 0) CreditGreen else DebitRed
                            )
                        }
                    }
                }

                // ===== ترويسة الجدول =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("التاريخ / نوع المستند / البيان", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("وارد (+)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("صادر (-)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (showRunningBalance) {
                        Text("الرصيد", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                // ===== قائمة الحركات النقدية (قابلة للنقر) =====
                if (rows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد حركات نقدية مسجلة لهذا الصندوق في هذه الفترة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rows) { row ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (row.isInvoice) onNavigateToInvoice(row.id)
                                        else onNavigateToVoucher(row.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(2f)) {
                                        Text(DateUtils.formatDate(row.dateMillis), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("${row.typeName} ${row.docNumber}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(row.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                    Text(
                                        text = if (row.inAmount > 0) CurrencyFormatter.formatAmount(row.inAmount) else "-",
                                        modifier = Modifier.weight(1f),
                                        color = if (row.inAmount > 0) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (row.outAmount > 0) CurrencyFormatter.formatAmount(row.outAmount) else "-",
                                        modifier = Modifier.weight(1f),
                                        color = if (row.outAmount > 0) DebitRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    if (showRunningBalance) {
                                        Text(
                                            text = CurrencyFormatter.formatAmount(row.balanceAfter),
                                            modifier = Modifier.weight(1.2f),
                                            color = if (row.balanceAfter >= 0) CreditGreen else DebitRed,
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }

    // ===== حوار خيارات عرض وتصفية كشف حركة الصندوق =====
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("خيارات عرض وتصفية الحركة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("الفترة الزمنية:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showFromDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (fromDateMillis != null) DateUtils.formatDate(fromDateMillis!!) else "من تاريخ")
                        }
                        OutlinedButton(
                            onClick = { showToDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (toDateMillis != null) DateUtils.formatDate(toDateMillis!!) else "إلى تاريخ")
                        }
                    }

                    if (fromDateMillis != null || toDateMillis != null) {
                        TextButton(onClick = {
                            fromDateMillis = null
                            toDateMillis = null
                        }) {
                            Text("إلغاء تصفية التاريخ", color = DebitRed)
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إظهار عمود الرصيد التراكمي", fontSize = 13.sp)
                        Switch(checked = showRunningBalance, onCheckedChange = { showRunningBalance = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOptionsDialog = false }) { Text("تم") }
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
