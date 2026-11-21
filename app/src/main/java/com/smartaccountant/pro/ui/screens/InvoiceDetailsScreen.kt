package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceItemEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.ActionOrange
import com.smartaccountant.pro.ui.theme.TopBarDark
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val InvoiceDetailsHeader = TopBarDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailsScreen(invoiceId: String, mainViewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val company = session.activeCompany
    var invoice by remember(invoiceId) { mutableStateOf<InvoiceEntity?>(null) }
    var items by remember(invoiceId) { mutableStateOf<List<InvoiceItemEntity>>(emptyList()) }
    var isLoading by remember(invoiceId) { mutableStateOf(true) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var paymentBoxId by remember { mutableStateOf("") }
    var isRecordingPayment by remember { mutableStateOf(false) }
    var isSendingReminder by remember { mutableStateOf(false) }

    LaunchedEffect(invoiceId, refreshKey) {
        isLoading = true
        val loaded = mainViewModel.container.db.invoiceDao().getInvoiceById(invoiceId)
            ?.takeUnless { it.isDeleted || it.companyId != company?.id }
        invoice = loaded
        items = loaded?.let { mainViewModel.container.db.invoiceDao().getInvoiceItems(it.id) } ?: emptyList()
        isLoading = false
    }

    val accounts by mainViewModel.container.db.accountDao()
        .observeAccounts(company?.id.orEmpty()).collectAsState(initial = emptyList())
    val currentInvoice = invoice
    val account = accounts.find { it.id == currentInvoice?.accountId }
    val cashBoxName = session.cashBoxes.find { it.id == currentInvoice?.cashBoxId }?.name
    val currency = session.currencies.find { it.id == currentInvoice?.currencyId }
    val canShare = PermissionManager.permissionsOf(session.currentUser).canExportAndShare
    val canCreateSale = PermissionManager.permissionsOf(session.currentUser).canCreateSaleInvoice
    val permissions = PermissionManager.permissionsOf(session.currentUser)
    val canRecordPayment = permissions.canAddVoucher
    val canRemind = permissions.canSendPaymentReminder

    fun recordPayment() {
        val user = session.currentUser ?: return
        val inv = currentInvoice ?: return
        val amount = paymentAmountText.toDoubleOrNull() ?: 0.0
        val boxId = paymentBoxId.ifBlank { session.cashBoxes.firstOrNull()?.id.orEmpty() }
        if (amount <= 0.0) {
            mainViewModel.showMessage("أدخل مبلغ دفعة صحيحاً")
            return
        }
        if (boxId.isBlank()) {
            mainViewModel.showMessage("اختر الصندوق الذي استُلمت فيه الدفعة")
            return
        }
        isRecordingPayment = true
        scope.launch {
            try {
                mainViewModel.container.invoiceRepository.recordInvoicePayment(
                    currentUser = user, invoice = inv, amount = amount,
                    cashBoxId = boxId, note = paymentNote
                )
                mainViewModel.showMessage("تم تسجيل الدفعة بنجاح")
                showPaymentDialog = false
                paymentAmountText = ""
                paymentNote = ""
                refreshKey++
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر تسجيل الدفعة")
            } finally {
                isRecordingPayment = false
            }
        }
    }

    fun sendPaymentReminder() {
        val user = session.currentUser ?: return
        val inv = currentInvoice ?: return
        val partyAccount = account ?: return
        if (partyAccount.phone.isBlank()) {
            mainViewModel.showMessage("لا يوجد رقم هاتف مسجل لهذا الحساب")
            return
        }
        isSendingReminder = true
        scope.launch {
            try {
                val message = mainViewModel.container.partyRepository.createPaymentReminder(
                    user, inv.companyId, inv.accountId
                )
                MessagingShareService.sendWhatsAppMessage(context, partyAccount.phone, message)
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إرسال تذكير الدفع")
            } finally {
                isSendingReminder = false
            }
        }
    }

    fun convertToInvoice() {
        val user = session.currentUser ?: return
        val source = currentInvoice ?: return
        if (source.type != InvoiceType.QUOTATION && source.type != InvoiceType.ORDER) return
        isConverting = true
        scope.launch {
            try {
                val newInvoice = InvoiceEntity(
                    id = UUID.randomUUID().toString(),
                    companyId = source.companyId,
                    invoiceNumber = 0,
                    displayNumber = "",
                    type = InvoiceType.SALE,
                    paymentMethod = source.paymentMethod,
                    dateMillis = System.currentTimeMillis(),
                    dueDateMillis = java.util.Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        add(java.util.Calendar.DAY_OF_MONTH, 30)
                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                        set(java.util.Calendar.MINUTE, 59)
                        set(java.util.Calendar.SECOND, 59)
                        set(java.util.Calendar.MILLISECOND, 999)
                    }.timeInMillis,
                    accountId = source.accountId,
                    warehouseId = source.warehouseId,
                    targetWarehouseId = source.targetWarehouseId,
                    cashBoxId = source.cashBoxId,
                    currencyId = source.currencyId,
                    exchangeRate = source.exchangeRate,
                    subTotal = source.subTotal,
                    discountAmount = source.discountAmount,
                    isDiscountPercentage = source.isDiscountPercentage,
                    taxAmount = source.taxAmount,
                    netTotal = source.netTotal,
                    paidAmount = 0.0,
                    notes = source.notes,
                    isArchived = false,
                    isPosted = false,
                    createdByUserId = user.id,
                    createdByUserName = user.fullName
                )
                val newItems = items.map {
                    it.copy(
                        id = UUID.randomUUID().toString(),
                        invoiceId = newInvoice.id,
                        isDeleted = false
                    )
                }
                mainViewModel.container.invoiceRepository.saveInvoice(user, newInvoice, newItems)
                mainViewModel.showMessage("تم تحويل ${source.type.arabicTitle} إلى فاتورة بيع بنجاح")
                showConvertDialog = false
                onBack()
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر تحويل المستند إلى فاتورة")
            } finally {
                isConverting = false
            }
        }
    }

    fun shareInvoicePdf(toWhatsApp: Boolean) {
        val activeCompany = company ?: return
        val selectedInvoice = currentInvoice ?: return
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    mainViewModel.container.pdfGenerator.generateInvoicePdf(
                        company = activeCompany,
                        invoice = selectedInvoice,
                        items = items,
                        account = account,
                        currency = currency,
                        settings = session.settings
                    )
                }
                if (toWhatsApp) {
                    MessagingShareService.sharePdfToWhatsApp(context, file)
                } else {
                    MessagingShareService.shareFile(context, file)
                }
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر إنشاء أو مشاركة ملف الفاتورة")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل الفاتورة", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InvoiceDetailsHeader)
            )
        }
    ) { padding ->
        PermissionGuard(user = session.currentUser, permissionCheck = { it.canViewStatements }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    currentInvoice == null -> Text("تعذر العثور على الفاتورة أو لم تعد متاحة")
                    else -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                InvoiceDetailRow("الحقل", "التفاصيل", header = true)
                                InvoiceDetailRow("نوع الفاتورة", currentInvoice.type.arabicTitle)
                                InvoiceDetailRow(
                                    "رقم الفاتورة",
                                    currentInvoice.displayNumber.ifBlank { "#${currentInvoice.invoiceNumber}" }
                                )
                                InvoiceDetailRow("التاريخ", DateUtils.formatDateTime(currentInvoice.dateMillis))
                                InvoiceDetailRow("الحساب", account?.name ?: "—")
                                currentInvoice.originalInvoiceId?.let { originalId ->
                                    InvoiceDetailRow("الفاتورة الأصلية", originalId)
                                }
                                InvoiceDetailRow(
                                    "طريقة الدفع",
                                    currentInvoice.paymentMethod.arabicTitle
                                )
                                cashBoxName?.let { InvoiceDetailRow("الصندوق", it) }
                                currency?.let {
                                    InvoiceDetailRow(
                                        "العملة",
                                        "${it.name} (${it.symbol}) • سعر الصرف: ${currentInvoice.exchangeRate}"
                                    )
                                }
                                InvoiceDetailRow("الموظف", currentInvoice.createdByUserName.ifBlank { "—" })
                                InvoiceDetailRow(
                                    "الحالة",
                                    buildString {
                                        append(if (currentInvoice.isPosted) "مرحّلة" else "غير مرحّلة")
                                        if (currentInvoice.isArchived) append(" • مؤرشفة")
                                        append(if (currentInvoice.syncStatus.name == "SYNCED") " • متزامنة" else " • بانتظار المزامنة")
                                    }
                                )
                                if (currentInvoice.notes.isNotBlank()) {
                                    InvoiceDetailRow("ملاحظات", currentInvoice.notes)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                InvoiceDetailRow("البند", "الكمية × السعر = الإجمالي", header = true)
                                if (items.isEmpty()) {
                                    Text(
                                        "لا توجد بنود مسجلة",
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    items.forEach { item ->
                                        InvoiceItemRow(item)
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                InvoiceDetailRow("الإجمالي الفرعي", CurrencyFormatter.format(currentInvoice.subTotal, currency?.symbol.orEmpty()))
                                if (currentInvoice.discountAmount > 0) {
                                    InvoiceDetailRow(
                                        "الخصم${if (currentInvoice.isDiscountPercentage) " (${currentInvoice.discountAmount}%)" else ""}",
                                        "- ${CurrencyFormatter.format(currentInvoice.discountAmount, currency?.symbol.orEmpty())}"
                                    )
                                }
                                if (currentInvoice.taxAmount > 0) {
                                    InvoiceDetailRow("الضريبة", CurrencyFormatter.format(currentInvoice.taxAmount, currency?.symbol.orEmpty()))
                                }
                                InvoiceDetailRow(
                                    "الصافي",
                                    CurrencyFormatter.format(currentInvoice.netTotal, currency?.symbol.orEmpty()),
                                    emphasize = true
                                )
                                InvoiceDetailRow(
                                    "المدفوع",
                                    CurrencyFormatter.format(currentInvoice.paidAmount, currency?.symbol.orEmpty())
                                )
                                val outstanding = (currentInvoice.netTotal - currentInvoice.paidAmount).coerceAtLeast(0.0)
                                InvoiceDetailRow(
                                    "المتبقي",
                                    CurrencyFormatter.format(outstanding, currency?.symbol.orEmpty()),
                                    emphasize = outstanding > 0
                                )
                            }
                        }

                val outstandingNow = (currentInvoice.netTotal - currentInvoice.paidAmount).coerceAtLeast(0.0)
                val isSaleInvoice = currentInvoice.type == InvoiceType.SALE || currentInvoice.type == InvoiceType.FREE_SALE
                if (isSaleInvoice && outstandingNow > 0.0 && !currentInvoice.isArchived) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (canRecordPayment) {
                            Button(
                                onClick = {
                                    paymentAmountText = outstandingNow.toString()
                                    paymentBoxId = currentInvoice.cashBoxId ?: session.cashBoxes.firstOrNull()?.id.orEmpty()
                                    showPaymentDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تسجيل دفعة")
                            }
                        }
                        if (canRemind && account?.phone?.isNotBlank() == true) {
                            OutlinedButton(
                                onClick = { sendPaymentReminder() },
                                enabled = !isSendingReminder,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSendingReminder) "جارٍ الإرسال..." else "تذكير واتساب")
                            }
                        }
                    }
                }

                        if ((currentInvoice.type == InvoiceType.QUOTATION || currentInvoice.type == InvoiceType.ORDER) && canCreateSale) {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { showConvertDialog = true },
                                enabled = !isConverting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Text(if (isConverting) "جارٍ التحويل..." else "تحويل إلى فاتورة بيع")
                            }
                        }

                        if (canShare) {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { shareInvoicePdf(toWhatsApp = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(14.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = ActionOrange
                                )
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("مشاركة الفاتورة PDF إلى واتساب")
                            }
                            OutlinedButton(
                                onClick = { shareInvoicePdf(toWhatsApp = false) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("مشاركة PDF عبر التطبيقات")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRecordingPayment) showPaymentDialog = false },
            title = { Text("تسجيل دفعة على الفاتورة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val inv = currentInvoice
                    if (inv != null) {
                        val outstanding = (inv.netTotal - inv.paidAmount).coerceAtLeast(0.0)
                        Text(
                            "المتبقي: ${CurrencyFormatter.format(outstanding, currency?.symbol.orEmpty())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = { Text("مبلغ الدفعة *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    var boxExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = boxExpanded, onExpandedChange = { boxExpanded = it }) {
                        OutlinedTextField(
                            value = session.cashBoxes.find { it.id == paymentBoxId }?.name ?: "اختر الصندوق",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الصندوق الذي استُلمت فيه الدفعة *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boxExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = boxExpanded, onDismissRequest = { boxExpanded = false }) {
                            session.cashBoxes.forEach { box ->
                                DropdownMenuItem(
                                    text = { Text(box.name) },
                                    onClick = { paymentBoxId = box.id; boxExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = paymentNote,
                        onValueChange = { paymentNote = it },
                        label = { Text("ملاحظة (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { recordPayment() },
                    enabled = !isRecordingPayment
                ) { Text(if (isRecordingPayment) "جارٍ التسجيل..." else "تأكيد الدفعة") }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }, enabled = !isRecordingPayment) { Text("إلغاء") }
            }
        )
    }

    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text("تأكيد التحويل") },
            text = { Text("سيتم إنشاء فاتورة بيع جديدة بنفس البنود والإجماليات. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = { convertToInvoice() }) { Text("تحويل") }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun InvoiceDetailRow(label: String, value: String, emphasize: Boolean = false, header: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) InvoiceDetailsHeader else Color.Transparent),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = if (header) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(0.9f).padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Text(
            value,
            fontWeight = if (header || emphasize) FontWeight.Bold else FontWeight.Medium,
            color = when {
                header -> Color.White
                emphasize -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1.3f).padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
    if (!header) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun InvoiceItemRow(item: InvoiceItemEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(item.productName, fontWeight = FontWeight.Bold)
        Text(
            "${item.quantity} ${item.unitName} × ${CurrencyFormatter.format(item.unitPrice)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.meteringMode != com.smartaccountant.pro.data.model.MeteringMode.NONE) {
            Text(
                "تمتير: ${item.meteringMode.arabicTitle} — ${item.dimLength}×${item.dimWidth}×${item.dimHeight} × ${item.pieceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                CurrencyFormatter.format(item.lineTotal),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}
