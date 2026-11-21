package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.*
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBoxesAndCurrenciesScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToBoxStatement: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val cashBoxes by mainViewModel.container.db.financeDao().observeCashBoxes(companyId).collectAsState(initial = emptyList())
    val vouchers by mainViewModel.container.db.voucherDao().observeVouchers(companyId).collectAsState(initial = emptyList())
    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())

    suspend fun recordOperation(entityType: String, entityId: String, action: AuditAction, summary: String) {
        val actor = user ?: return
        mainViewModel.container.db.userSyncDao().insertAuditLog(
            AuditLogEntity(
                companyId = companyId,
                userId = actor.id,
                userName = actor.fullName,
                deviceName = mainViewModel.container.deviceName,
                action = action,
                entityType = entityType,
                entityId = entityId,
                summary = summary
            )
        )
    }

    var showAddBox by remember { mutableStateOf(false) }
    var editingBox by remember { mutableStateOf<CashBoxEntity?>(null) }
    var transferPair by remember { mutableStateOf<Pair<CashBoxEntity, CashBoxEntity>?>(null) }
    var exchangeOpen by remember { mutableStateOf(false) }
    var showAddCurrency by remember { mutableStateOf(false) }
    var editingCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }

    fun balanceOf(boxId: String): Double {
        var bal = 0.0
        vouchers.forEach { v ->
            if (v.isDeleted) return@forEach
            when (v.type) {
                VoucherType.RECEIPT, VoucherType.REMITTANCE_IN, VoucherType.OPENING_BALANCE ->
                    if (v.cashBoxId == boxId) bal += v.amount
                VoucherType.PAYMENT, VoucherType.REMITTANCE_OUT, VoucherType.CURRENCY_EXCHANGE ->
                    if (v.cashBoxId == boxId) bal -= v.amount
                VoucherType.BOX_TRANSFER -> {
                    if (v.cashBoxId == boxId) bal -= v.amount
                    if (v.targetCashBoxId == boxId) bal += if (v.targetAmount > 0) v.targetAmount else v.amount
                }
                else -> {}
            }
        }
        return bal
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الصناديق والبنوك والعملات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    IconButton(onClick = { exchangeOpen = true }) { Icon(Icons.Default.CurrencyExchange, contentDescription = "مصارفة عملة") }
                    IconButton(onClick = { showAddBox = true }) { Icon(Icons.Default.Add, contentDescription = "إضافة صندوق") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== الصناديق النقدية =====
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الصناديق النقدية والمحافظ (${cashBoxes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddBox = true }) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("إضافة") }
            }

            if (cashBoxes.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text("لا توجد صناديق بعد — أضف أول صندوق نقدي أو محفظة.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            cashBoxes.forEach { box ->
                val balance = balanceOf(box.id)
                val boxCurrency = session.currencies.find { it.id == box.defaultCurrencyId } ?: session.currencies.firstOrNull { it.isDefault }
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = (if (box.isBank) Color(0xFF1565C0) else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)
                            ) {
                                Icon(
                                    if (box.isBank) Icons.Default.AccountBalance else Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (box.isBank) Color(0xFF1565C0) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp).size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(box.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${if (box.isBank) "بنك / محفظة" else "صندوق نقدي"} | ${boxCurrency?.name ?: "العملة الافتراضية"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (box.isDefault) "افتراضي" else "فرعي",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الرصيد الحالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyFormatter.format(balance),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (balance < 0) DebitRed else CreditGreen
                                )
                            }
                            OutlinedButton(onClick = { onNavigateToBoxStatement(box.id) }) {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("كشف الحركة")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = {
                                val target = cashBoxes.firstOrNull { it.id != box.id }
                                if (target != null) transferPair = box to target else mainViewModel.showMessage("أضف صندوقاً آخر أولاً لإجراء التحويل")
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تحويل")
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { editingBox = box }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                        }
                    }
                }
            }

            // ===== العملات =====
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("العملات وأسعار الصرف (${session.currencies.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddCurrency = true }) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("عملة") }
            }

            session.currencies.forEach { cur ->
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(cur.symbol.take(3), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${cur.name}${if (cur.isDefault) " [الافتراضية]" else ""}", fontWeight = FontWeight.Bold)
                            Text("شراء: ${CurrencyFormatter.format(cur.buyRate)} | بيع: ${CurrencyFormatter.format(cur.sellRate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editingCurrency = cur }) { Icon(Icons.Default.Edit, contentDescription = "تعديل سعر الصرف") }
                    }
                }
            }
        }
    }

    // ===== حوار إضافة صندوق =====
    if (showAddBox) {
        var name by remember { mutableStateOf("") }
        var isBank by remember { mutableStateOf(false) }
        var isDefault by remember { mutableStateOf(cashBoxes.isEmpty()) }
        AlertDialog(
            onDismissRequest = { showAddBox = false },
            title = { Text("إضافة صندوق / محفظة") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الصندوق *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isBank, onClick = { isBank = false }, label = { Text("صندوق نقدي") })
                        FilterChip(selected = isBank, onClick = { isBank = true }, label = { Text("بنك / محفظة") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isDefault, onClick = { isDefault = true }, label = { Text("افتراضي") })
                        FilterChip(selected = !isDefault, onClick = { isDefault = false }, label = { Text("فرعي") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isBlank()) return@Button
                    scope.launch {
                        val box = CashBoxEntity(
                            companyId = companyId, name = name.trim(), isBank = isBank, isDefault = isDefault,
                            defaultCurrencyId = session.currencies.firstOrNull { it.isDefault }?.id ?: ""
                        )
                        mainViewModel.container.db.financeDao().upsertCashBox(box)
                        recordOperation("صندوق نقدي", box.id, AuditAction.CREATE, "إضافة صندوق: ${box.name}")
                        mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                        showAddBox = false
                        mainViewModel.showMessage("تم إضافة الصندوق")
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAddBox = false }) { Text("إلغاء") } }
        )
    }

    // ===== حوار تعديل صندوق =====
    editingBox?.let { box ->
        var name by remember { mutableStateOf(box.name) }
        var isBank by remember { mutableStateOf(box.isBank) }
        var isDefault by remember { mutableStateOf(box.isDefault) }
        AlertDialog(
            onDismissRequest = { editingBox = null },
            title = { Text("تعديل الصندوق") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الصندوق") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isBank, onClick = { isBank = false }, label = { Text("صندوق نقدي") })
                        FilterChip(selected = isBank, onClick = { isBank = true }, label = { Text("بنك / محفظة") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isDefault, onClick = { isDefault = true }, label = { Text("افتراضي") })
                        FilterChip(selected = !isDefault, onClick = { isDefault = false }, label = { Text("فرعي") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val updatedBox = box.copy(
                            name = name.trim(),
                            isBank = isBank,
                            isDefault = isDefault,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING_UPDATE
                        )
                        mainViewModel.container.db.financeDao().upsertCashBox(updatedBox)
                        recordOperation("صندوق نقدي", box.id, AuditAction.UPDATE, "تعديل الصندوق: ${updatedBox.name}")
                        mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                        editingBox = null
                        mainViewModel.showMessage("تم حفظ الصندوق")
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editingBox = null }) { Text("إلغاء") } }
        )
    }

    // ===== حوار التحويل بين الصناديق =====
    transferPair?.let { (from, to) ->
        var amountText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var transferDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var showTransferDatePicker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { transferPair = null },
            title = { Text("تحويل بين الصناديق") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(from.name, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        Text(to.name, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("المبلغ *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { showTransferDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Event, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تاريخ التحويل: ${DateUtils.formatDate(transferDateMillis)}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    val currentUser = user ?: return@Button
                    if (amt <= 0) { mainViewModel.showMessage("أدخل مبلغاً صحيحاً"); return@Button }
                    scope.launch {
                        try {
                            mainViewModel.container.accountingRepository.saveVoucher(
                                currentUser = currentUser,
                                voucher = VoucherEntity(
                                    companyId = companyId, voucherNumber = 0, type = VoucherType.BOX_TRANSFER,
                                    accountId = accounts.firstOrNull()?.id ?: "",
                                    cashBoxId = from.id, targetCashBoxId = to.id,
                                    currencyId = session.currencies.firstOrNull { it.isDefault }?.id ?: "cur-yer",
                                    exchangeRate = 1.0, amount = amt, targetAmount = amt,
                                    dateMillis = transferDateMillis,
                                    description = "تحويل من ${from.name} إلى ${to.name} ${notes}".trim(),
                                    createdByUserId = currentUser.id, createdByUserName = currentUser.fullName
                                )
                            )
                            mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                            transferPair = null
                            mainViewModel.showMessage("تم تنفيذ التحويل")
                        } catch (e: Exception) { mainViewModel.showError(e, "تعذر تنفيذ التحويل") }
                    }
                }) { Text("تنفيذ التحويل") }
            },
            dismissButton = { TextButton(onClick = { transferPair = null }) { Text("إلغاء") } }
        )
        if (showTransferDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = toUtcPickerDate(transferDateMillis))
            DatePickerDialog(
                onDismissRequest = { showTransferDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { transferDateMillis = fromUtcPickerDate(it) }
                        showTransferDatePicker = false
                    }) { Text("اختيار") }
                },
                dismissButton = { TextButton(onClick = { showTransferDatePicker = false }) { Text("إلغاء") } }
            ) { DatePicker(state = pickerState) }
        }
    }

    // ===== حوار مصارفة عملة =====
    if (exchangeOpen) {
        var fromCurId by remember { mutableStateOf(session.currencies.firstOrNull()?.id ?: "") }
        var toCurId by remember { mutableStateOf(session.currencies.getOrNull(1)?.id ?: session.currencies.firstOrNull()?.id ?: "") }
        var amountText by remember { mutableStateOf("") }
        var rateText by remember { mutableStateOf("") }
        var cashBoxId by remember { mutableStateOf(cashBoxes.firstOrNull()?.id ?: "") }
        var exchangeDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var showExchangeDatePicker by remember { mutableStateOf(false) }
        val fromCur = session.currencies.find { it.id == fromCurId }
        val toCur = session.currencies.find { it.id == toCurId }
        AlertDialog(
            onDismissRequest = { exchangeOpen = false },
            title = { Text("مصارفة عملة") },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("من عملة", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        session.currencies.forEach { c ->
                            FilterChip(selected = fromCurId == c.id, onClick = { fromCurId = c.id; if (rateText.isBlank()) rateText = c.sellRate.toString() }, label = { Text(c.symbol, fontSize = 12.sp) })
                        }
                    }
                    Text("إلى عملة", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        session.currencies.forEach { c ->
                            FilterChip(selected = toCurId == c.id, onClick = { toCurId = c.id }, label = { Text(c.symbol, fontSize = 12.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("المبلغ") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = rateText, onValueChange = { rateText = it }, label = { Text("سعر الصرف") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    if (cashBoxes.isNotEmpty()) {
                        Text("الصندوق", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cashBoxes.forEach { b ->
                                FilterChip(selected = cashBoxId == b.id, onClick = { cashBoxId = b.id }, label = { Text(b.name, fontSize = 12.sp) })
                            }
                        }
                    }
                    OutlinedButton(onClick = { showExchangeDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Event, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تاريخ المصارفة: ${DateUtils.formatDate(exchangeDateMillis)}")
                    }
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    val rate = rateText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && rate > 0) {
                        Text("الناتج التقريبي: ${CurrencyFormatter.format(amt * rate)} ${toCur?.symbol ?: ""}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    val rate = rateText.toDoubleOrNull() ?: 0.0
                    val currentUser = user ?: return@Button
                    if (amt <= 0 || rate <= 0 || fromCurId == toCurId) { mainViewModel.showMessage("أدخل مبلغاً وسعراً صحيحين وعملتين مختلفتين"); return@Button }
                    scope.launch {
                        try {
                            mainViewModel.container.accountingRepository.saveVoucher(
                                currentUser = currentUser,
                                voucher = VoucherEntity(
                                    companyId = companyId, voucherNumber = 0, type = VoucherType.CURRENCY_EXCHANGE,
                                    accountId = accounts.firstOrNull()?.id ?: "",
                                    cashBoxId = cashBoxId.ifBlank { null },
                                    currencyId = fromCurId, targetCurrencyId = toCurId,
                                    exchangeRate = rate, amount = amt, targetAmount = amt * rate,
                                    dateMillis = exchangeDateMillis,
                                    description = "مصارفة ${fromCur?.name ?: ""} إلى ${toCur?.name ?: ""} بسعر $rate",
                                    createdByUserId = currentUser.id, createdByUserName = currentUser.fullName
                                )
                            )
                            mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                            exchangeOpen = false
                            mainViewModel.showMessage("تم تسجيل المصارفة")
                        } catch (e: Exception) { mainViewModel.showError(e, "تعذر تسجيل المصارفة") }
                    }
                }) { Text("حفظ المصارفة") }
            },
            dismissButton = { TextButton(onClick = { exchangeOpen = false }) { Text("إلغاء") } }
        )
        if (showExchangeDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = toUtcPickerDate(exchangeDateMillis))
            DatePickerDialog(
                onDismissRequest = { showExchangeDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { exchangeDateMillis = fromUtcPickerDate(it) }
                        showExchangeDatePicker = false
                    }) { Text("اختيار") }
                },
                dismissButton = { TextButton(onClick = { showExchangeDatePicker = false }) { Text("إلغاء") } }
            ) { DatePicker(state = pickerState) }
        }
    }

    // ===== حوار إضافة عملة =====
    if (showAddCurrency) {
        var name by remember { mutableStateOf("") }
        var symbol by remember { mutableStateOf("") }
        var buy by remember { mutableStateOf("") }
        var sell by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCurrency = false },
            title = { Text("إضافة عملة") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم العملة *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = symbol, onValueChange = { symbol = it }, label = { Text("الرمز") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = buy, onValueChange = { buy = it }, label = { Text("سعر الشراء") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("سعر البيع") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val b = buy.toDoubleOrNull() ?: 0.0
                    val s = sell.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || b <= 0 || s <= 0) { mainViewModel.showMessage("أدخل الاسم وسعري صرف صحيحين"); return@Button }
                    scope.launch {
                        val currency = CurrencyEntity(
                            companyId = companyId,
                            name = name.trim(),
                            symbol = symbol.trim().ifBlank { name.take(3) },
                            buyRate = b,
                            sellRate = s,
                            sortOrder = session.currencies.size + 1
                        )
                        mainViewModel.container.db.financeDao().upsertCurrency(currency)
                        recordOperation("عملة", currency.id, AuditAction.CREATE, "إضافة عملة: ${currency.name}")
                        mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                        showAddCurrency = false
                        mainViewModel.showMessage("تمت إضافة العملة")
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAddCurrency = false }) { Text("إلغاء") } }
        )
    }

    // ===== حوار تعديل عملة =====
    editingCurrency?.let { cur ->
        var buy by remember { mutableStateOf(cur.buyRate.toString()) }
        var sell by remember { mutableStateOf(cur.sellRate.toString()) }
        AlertDialog(
            onDismissRequest = { editingCurrency = null },
            title = { Text("تعديل أسعار: ${cur.name}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = buy, onValueChange = { buy = it }, label = { Text("سعر الشراء") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("سعر البيع") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val b = buy.toDoubleOrNull() ?: cur.buyRate
                    val s = sell.toDoubleOrNull() ?: cur.sellRate
                    scope.launch {
                        val updatedCurrency = cur.copy(
                            buyRate = b,
                            sellRate = s,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING_UPDATE
                        )
                        mainViewModel.container.db.financeDao().upsertCurrency(updatedCurrency)
                        recordOperation("عملة", cur.id, AuditAction.UPDATE, "تحديث أسعار العملة: ${cur.name}")
                        mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                        editingCurrency = null
                        mainViewModel.showMessage("تم تحديث أسعار الصرف")
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editingCurrency = null }) { Text("إلغاء") } }
        )
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
