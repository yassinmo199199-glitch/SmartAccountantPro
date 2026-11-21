package com.smartaccountant.pro.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.*
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.WarningAmber
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class RemittanceStatusFilter(val label: String) { ALL("الكل"), PENDING("قيد الانتظار"), RECEIVED("مستلمة"), CANCELLED("ملغاة") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemittanceScreen(mainViewModel: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val vouchers by mainViewModel.container.db.voucherDao().observeVouchers(companyId).collectAsState(initial = emptyList())
    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())

    var isIncoming by remember { mutableStateOf(true) }
    var statusFilter by remember { mutableStateOf(RemittanceStatusFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewRemittance by remember { mutableStateOf(false) }

    val remittances = remember(vouchers, isIncoming) {
        val targetType = if (isIncoming) VoucherType.REMITTANCE_IN else VoucherType.REMITTANCE_OUT
        vouchers.filter { it.type == targetType }
    }
    val filtered = remember(remittances, statusFilter, searchQuery) {
        remittances.filter { v ->
            val statusOk = when (statusFilter) {
                RemittanceStatusFilter.ALL -> true
                RemittanceStatusFilter.PENDING -> v.syncStatus != SyncStatus.SYNCED
                RemittanceStatusFilter.RECEIVED -> v.syncStatus == SyncStatus.SYNCED
                RemittanceStatusFilter.CANCELLED -> v.isDeleted
            }
            val q = searchQuery.trim()
            val searchOk = q.isEmpty() ||
                v.remittanceNumber.contains(q, true) ||
                v.senderName.contains(q, true) ||
                v.receiverName.contains(q, true) ||
                v.transferCompany.contains(q, true) ||
                v.displayNumber.contains(q, true)
            statusOk && searchOk
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الحوالات المالية") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        },
        floatingActionButton = {
            if (PermissionManager.permissionsOf(user).canManageRemittances) {
                FloatingActionButton(onClick = { showNewRemittance = true }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "حوالة جديدة")
                }
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canManageRemittances }) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ===== تبويب واردة / صادرة =====
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isIncoming,
                        onClick = { isIncoming = true },
                        label = { Text("واردة (${vouchers.count { it.type == VoucherType.REMITTANCE_IN }})") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.MoveToInbox, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = !isIncoming,
                        onClick = { isIncoming = false },
                        label = { Text("صادرة (${vouchers.count { it.type == VoucherType.REMITTANCE_OUT }})") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // ===== بحث + فلتر الحالة =====
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    placeholder = { Text("بحث برقم الحوالة أو الاسم...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "مسح") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RemittanceStatusFilter.entries.forEach { f ->
                        FilterChip(selected = statusFilter == f, onClick = { statusFilter = f }, label = { Text(f.label, fontSize = 12.sp) })
                    }
                }

                // ===== قائمة الحوالات =====
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { v ->
                        RemittanceCard(
                            voucher = v,
                            isIncoming = isIncoming,
                            onShare = {
                                val text = buildRemittanceShareText(v, session.activeCompany?.nameAr ?: "", session.currencies.find { it.id == v.currencyId }?.symbol ?: "")
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                                context.startActivity(Intent.createChooser(send, "مشاركة إشعار الحوالة").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            }
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    if (remittances.isEmpty()) "لا توجد حوالات ${if (isIncoming) "واردة" else "صادرة"} — اضغط + لإضافة حوالة"
                                    else "لا توجد نتائج مطابقة",
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== حوار حوالة جديدة =====
    if (showNewRemittance) {
        RemittanceEntryDialog(
            isIncoming = isIncoming,
            accounts = accounts,
            cashBoxes = session.cashBoxes,
            currencies = session.currencies,
            onDismiss = { showNewRemittance = false },
            onSave = { data ->
                val currentUser = user ?: return@RemittanceEntryDialog
                scope.launch {
                    try {
                        val type = if (data.isIncoming) VoucherType.REMITTANCE_IN else VoucherType.REMITTANCE_OUT
                        val cur = data.currency
                        mainViewModel.container.accountingRepository.saveVoucher(
                            currentUser = currentUser,
                            voucher = VoucherEntity(
                                companyId = companyId, voucherNumber = 0, type = type,
                                accountId = data.accountId,
                                cashBoxId = data.cashBoxId.ifBlank { session.cashBoxes.firstOrNull()?.id },
                                currencyId = cur?.id ?: "cur-yer",
                                exchangeRate = cur?.sellRate ?: 1.0,
                                amount = data.amount,
                                dateMillis = data.dateMillis,
                                transferCompany = data.network,
                                remittanceNumber = data.number,
                                senderName = data.sender,
                                receiverName = data.receiver,
                                commissionAmount = data.commission,
                                description = "حوالة ${if (data.isIncoming) "واردة" else "صادرة"} عبر ${data.network} رقم ${data.number} (${data.sender} -> ${data.receiver}) ${data.notes}".trim(),
                                createdByUserId = currentUser.id, createdByUserName = currentUser.fullName
                            )
                        )
                        mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                        showNewRemittance = false
                        mainViewModel.showMessage("تم حفظ الحوالة بنجاح")
                    } catch (e: Exception) {
                        mainViewModel.showError(e, "تعذر حفظ الحوالة")
                    }
                }
            }
        )
    }
}

data class RemittanceFormData(
    val isIncoming: Boolean,
    val accountId: String,
    val cashBoxId: String,
    val currency: CurrencyEntity?,
    val network: String,
    val number: String,
    val sender: String,
    val receiver: String,
    val amount: Double,
    val commission: Double,
    val notes: String,
    val dateMillis: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemittanceEntryDialog(
    isIncoming: Boolean,
    accounts: List<AccountEntity>,
    cashBoxes: List<CashBoxEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onSave: (RemittanceFormData) -> Unit
) {
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var cashBoxId by remember { mutableStateOf(cashBoxes.firstOrNull()?.id ?: "") }
    var currencyId by remember { mutableStateOf(currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id ?: "") }
    var network by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var commissionText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isIncoming) Icons.Default.MoveToInbox else Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (isIncoming) CreditGreen else DebitRed
                )
                Spacer(Modifier.width(8.dp))
                Text("حوالة ${if (isIncoming) "واردة" else "صادرة"} جديدة")
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // الحساب + الصندوق + العملة
                Text("البيانات المالية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                AccountDropdown(accounts = accounts, selectedId = accountId, onSelect = { accountId = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedBox = cashBoxes.find { it.id == cashBoxId }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = selectedBox?.name ?: "الصندوق",
                                onValueChange = {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                label = { Text("الصندوق") }, singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                cashBoxes.forEach { b ->
                                    DropdownMenuItem(text = { Text(b.name) }, onClick = { cashBoxId = b.id; expanded = false })
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedCur = currencies.find { it.id == currencyId }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = selectedCur?.let { "${it.name} (${it.symbol})" } ?: "العملة",
                                onValueChange = {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                label = { Text("العملة") }, singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                currencies.forEach { c ->
                                    DropdownMenuItem(text = { Text("${c.name} (${c.symbol})") }, onClick = { currencyId = c.id; expanded = false })
                                }
                            }
                        }
                    }
                }

                // بيانات الحوالة
                Text("بيانات الحوالة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = network, onValueChange = { network = it }, label = { Text("شبكة التحويل / الصراف *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("رقم الحوالة *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text(if (isIncoming) "اسم المرسل" else "اسم المرسل") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = receiver, onValueChange = { receiver = it }, label = { Text(if (isIncoming) "اسم المستفيد" else "اسم المستفيد") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("المبلغ *") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = commissionText, onValueChange = { commissionText = it }, label = { Text("العمولة") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تاريخ الحوالة: ${DateUtils.formatDate(dateMillis)}")
                }
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt <= 0 || network.isBlank() || number.isBlank() || accountId.isBlank()) {
                    errorMsg = "يرجى إدخال المبلغ ورقم الحوالة والشبكة والحساب"
                    return@Button
                }
                errorMsg = ""
                onSave(
                    RemittanceFormData(
                        isIncoming = isIncoming, accountId = accountId, cashBoxId = cashBoxId,
                        currency = currencies.find { it.id == currencyId },
                        network = network.trim(), number = number.trim(),
                        sender = sender.trim(), receiver = receiver.trim(),
                        amount = amt, commission = commissionText.toDoubleOrNull() ?: 0.0,
                        notes = notes.trim(), dateMillis = dateMillis
                    )
                )
            }) { Text("حفظ الحوالة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toUtcPickerDate(dateMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateMillis = fromUtcPickerDate(it) }
                    showDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }
        ) { DatePicker(state = pickerState) }
    }
}

private fun toUtcPickerDate(localMillis: Long): Long {
    val local = java.util.Calendar.getInstance().apply { timeInMillis = localMillis }
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromUtcPickerDate(pickerMillis: Long): Long {
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return java.util.Calendar.getInstance().apply {
        clear()
        set(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH), utc.get(java.util.Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(java.util.Calendar.MILLISECOND, 999)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(accounts: List<AccountEntity>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "اختر الحساب",
            onValueChange = {}, readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text("الحساب المرتبط *") }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acc ->
                DropdownMenuItem(text = { Text("${acc.name} (${acc.category.arabicTitle})") }, onClick = { onSelect(acc.id); expanded = false })
            }
        }
    }
}

@Composable
private fun RemittanceCard(voucher: VoucherEntity, isIncoming: Boolean, onShare: () -> Unit) {
    val accent = if (isIncoming) CreditGreen else DebitRed
    val statusText = when {
        voucher.isDeleted -> "ملغاة"
        voucher.syncStatus == SyncStatus.SYNCED -> "مستلمة / مكتملة"
        else -> "قيد الانتظار"
    }
    val statusColor = when {
        voucher.isDeleted -> MaterialTheme.colorScheme.onSurfaceVariant
        voucher.syncStatus == SyncStatus.SYNCED -> CreditGreen
        else -> WarningAmber
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f)) {
                    Icon(
                        if (isIncoming) Icons.Default.MoveToInbox else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null, tint = accent, modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("حوالة رقم ${voucher.remittanceNumber.ifBlank { voucher.displayNumber.ifBlank { "#${voucher.voucherNumber}" } }}", fontWeight = FontWeight.Bold)
                    Text(
                        "${voucher.senderName.ifBlank { "—" }} → ${voucher.receiverName.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(CurrencyFormatter.format(voucher.amount), fontWeight = FontWeight.Bold, color = accent)
                    if (voucher.commissionAmount > 0) Text("عمولة: ${CurrencyFormatter.format(voucher.commissionAmount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        if (voucher.transferCompany.isNotBlank()) append("${voucher.transferCompany} | ")
                        append(statusText)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مشاركة", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun buildRemittanceShareText(v: VoucherEntity, companyName: String, currencySymbol: String): String {
    val sb = StringBuilder()
    sb.appendLine("إشعار حوالة ${if (v.type == VoucherType.REMITTANCE_IN) "واردة" else "صادرة"} - $companyName")
    sb.appendLine("رقم الحوالة: ${v.remittanceNumber.ifBlank { v.displayNumber }}")
    if (v.transferCompany.isNotBlank()) sb.appendLine("شبكة التحويل: ${v.transferCompany}")
    sb.appendLine("المرسل: ${v.senderName.ifBlank { "—" }}")
    sb.appendLine("المستفيد: ${v.receiverName.ifBlank { "—" }}")
    sb.appendLine("المبلغ: ${v.amount} $currencySymbol")
    if (v.commissionAmount > 0) sb.appendLine("العمولة: ${v.commissionAmount} $currencySymbol")
    sb.appendLine("----------------------------------------")
    sb.appendLine(v.description)
    return sb.toString()
}
