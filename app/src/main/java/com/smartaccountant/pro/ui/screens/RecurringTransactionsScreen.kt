package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.RecurrenceFrequency
import com.smartaccountant.pro.data.model.RecurringTransactionEntity
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.ui.components.ConfirmDestructiveActionDialog
import com.smartaccountant.pro.ui.components.EmptyState
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(mainViewModel: MainViewModel, onBack: () -> Unit) {
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id.orEmpty()
    val user = session.currentUser
    val scope = rememberCoroutineScope()
    val transactions by mainViewModel.container.db.recurringTransactionDao()
        .observeRecurringTransactions(companyId).collectAsState(initial = emptyList())
    val accounts by mainViewModel.container.db.accountDao()
        .observeAccounts(companyId).collectAsState(initial = emptyList())
    val boxes = session.cashBoxes
    val currencies = session.currencies
    var editing by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecurringTransactionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المعاملات المتكررة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة معاملة")
                    }
                }
            )
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canManageRecurring }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (transactions.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.EventRepeat,
                        title = "لا توجد معاملات متكررة",
                        subtitle = "أنشئ جدولاً لسند قبض أو صرف متكرر.",
                        actionLabel = "أضف أول جدولة",
                        onAction = { editing = null; showEditor = true }
                    )
                }
                transactions.forEach { transaction ->
                    val accountName = accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "حساب غير متاح"
                    val boxName = boxes.firstOrNull { it.id == transaction.cashBoxId }?.name ?: "صندوق غير متاح"
                    val currency = currencies.firstOrNull { it.id == transaction.currencyId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventRepeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.description, fontWeight = FontWeight.Bold)
                                    Text("${transaction.voucherType.arabicTitle} • ${transaction.frequency.arabicTitle}", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = transaction.isActive,
                                    onCheckedChange = { active ->
                                        scope.launch {
                                            try {
                                                mainViewModel.container.recurringTransactionsRepository.save(
                                                    user ?: return@launch,
                                                    transaction.copy(
                                                        isActive = active,
                                                        updatedAt = System.currentTimeMillis()
                                                    )
                                                )
                                            } catch (e: Exception) {
                                                mainViewModel.showError(e, "تعذر تحديث الجدولة")
                                            }
                                        }
                                    }
                                )
                            }
                            Text("المبلغ: ${CurrencyFormatter.format(transaction.amount, currency?.symbol.orEmpty())} • $accountName")
                            Text("الصندوق: $boxName • التالي: ${DateUtils.formatDate(transaction.nextDueDateMillis)}")
                            Text("التنفيذ: ${transaction.executionCount}${transaction.occurrenceLimit?.let { " / $it" } ?: " / بلا نهاية"} • آخر تنفيذ: ${if (transaction.lastExecutedDateMillis > 0) DateUtils.formatDate(transaction.lastExecutedDateMillis) else "لم ينفذ بعد"}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { editing = transaction; showEditor = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تعديل")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingDelete = transaction
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("حذف")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        RecurringTransactionEditor(
            initial = editing,
            companyId = companyId,
            accounts = accounts,
            cashBoxes = boxes,
            currencies = currencies,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                scope.launch {
                    try {
                        user?.let { mainViewModel.container.recurringTransactionsRepository.save(it, updated) }
                        mainViewModel.showMessage("تم حفظ المعاملة المتكررة")
                        showEditor = false
                    } catch (e: Exception) {
                        mainViewModel.showError(e, "تعذر حفظ المعاملة")
                    }
                }
            }
        )
    }

    pendingDelete?.let { transaction ->
        ConfirmDestructiveActionDialog(
            title = "تأكيد حذف الجدولة",
            message = "سيتم إيقاف هذه المعاملة المتكررة وحذفها من قائمة الجداول.",
            confirmLabel = "حذف",
            onConfirm = {
                scope.launch {
                    try {
                        user?.let { mainViewModel.container.recurringTransactionsRepository.delete(it, transaction) }
                        mainViewModel.showMessage("تم حذف الجدولة")
                    } catch (e: Exception) {
                        mainViewModel.showError(e, "تعذر حذف الجدولة")
                    } finally {
                        pendingDelete = null
                    }
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringTransactionEditor(
    initial: RecurringTransactionEntity?,
    companyId: String,
    accounts: List<com.smartaccountant.pro.data.model.AccountEntity>,
    cashBoxes: List<com.smartaccountant.pro.data.model.CashBoxEntity>,
    currencies: List<com.smartaccountant.pro.data.model.CurrencyEntity>,
    onDismiss: () -> Unit,
    onSave: (RecurringTransactionEntity) -> Unit
) {
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var amountText by remember(initial) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var accountId by remember(initial, accounts) { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var cashBoxId by remember(initial, cashBoxes) { mutableStateOf(initial?.cashBoxId ?: cashBoxes.firstOrNull()?.id.orEmpty()) }
    var currencyId by remember(initial, currencies) { mutableStateOf(initial?.currencyId ?: currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id.orEmpty()) }
    var voucherType by remember(initial) { mutableStateOf(initial?.voucherType ?: VoucherType.PAYMENT) }
    var frequency by remember(initial) { mutableStateOf(initial?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var dueMillis by remember(initial) { mutableLongStateOf(initial?.nextDueDateMillis ?: System.currentTimeMillis()) }
    var dueDay by remember(initial) { mutableStateOf((initial?.dueDayOfMonth ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).toString()) }
    var occurrenceLimit by remember(initial) { mutableStateOf(initial?.occurrenceLimit?.toString().orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة معاملة متكررة" else "تعديل الجدولة") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(description, { description = it }, label = { Text("الوصف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    amountText, { amountText = it },
                    label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                RecurringSelector("الحساب", accounts.map { it.id to it.name }, accountId) { accountId = it }
                RecurringSelector("الصندوق", cashBoxes.map { it.id to it.name }, cashBoxId) { cashBoxId = it }
                RecurringSelector("العملة", currencies.map { it.id to "${it.name} (${it.symbol})" }, currencyId) { currencyId = it }
                Text("نوع السند")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(VoucherType.PAYMENT, VoucherType.RECEIPT).forEach { type ->
                        FilterChip(selected = voucherType == type, onClick = { voucherType = type }, label = { Text(type.arabicTitle) })
                    }
                }
                Text("التكرار")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurrenceFrequency.entries.forEach { item ->
                        FilterChip(selected = frequency == item, onClick = { frequency = item }, label = { Text(item.arabicTitle) })
                    }
                }
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("أول استحقاق: ${DateUtils.formatDate(dueMillis)}")
                }
                if (frequency == RecurrenceFrequency.MONTHLY || frequency == RecurrenceFrequency.YEARLY) {
                    OutlinedTextField(
                        dueDay, { dueDay = it },
                        label = { Text("يوم الاستحقاق (1-31)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                OutlinedTextField(
                    occurrenceLimit, { occurrenceLimit = it },
                    label = { Text("عدد التكرارات (اتركه فارغاً بلا نهاية)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull()
                val limit = occurrenceLimit.toIntOrNull()
                val parsedDay = dueDay.toIntOrNull() ?: Calendar.getInstance().apply { timeInMillis = dueMillis }.get(Calendar.DAY_OF_MONTH)
                if (description.isBlank() || amount == null || amount <= 0.0 ||
                    accountId.isBlank() || cashBoxId.isBlank() || currencyId.isBlank() ||
                    (occurrenceLimit.isNotBlank() && (limit == null || limit <= 0)) ||
                    parsedDay !in 1..31
                ) {
                    validationError = "أكمل الوصف والمبلغ والحساب والصندوق والعملة، وتحقق من قيم التكرار."
                    return@Button
                }
                onSave(
                    RecurringTransactionEntity(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        companyId = companyId,
                        description = description.trim(),
                        accountId = accountId,
                        cashBoxId = cashBoxId,
                        amount = amount,
                        currencyId = currencyId,
                        voucherType = voucherType,
                        frequency = frequency,
                        nextDueDateMillis = dueMillis,
                        dueDayOfMonth = parsedDay,
                        lastExecutedDateMillis = initial?.lastExecutedDateMillis ?: 0L,
                        occurrenceLimit = limit,
                        executionCount = initial?.executionCount ?: 0,
                        isActive = initial?.isActive ?: true,
                        createdByUserId = initial?.createdByUserId.orEmpty()
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = toUtcPickerDate(dueMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dueMillis = fromUtcPickerDate(it)
                        if (initial == null) dueDay = Calendar.getInstance().apply { timeInMillis = dueMillis }.get(Calendar.DAY_OF_MONTH).toString()
                    }
                    showDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringSelector(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.first == selectedId }?.second.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

private fun toUtcPickerDate(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromUtcPickerDate(pickerMillis: Long): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
