package com.smartaccountant.pro.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.data.model.VoucherLineEntity
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.ui.components.CalculatorDialog
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.WarningAmber
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class VoucherLineUIState(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String = "",
    val debit: String = "",
    val credit: String = "",
    val note: String = "",
    val costCenterId: String? = null
) {
    val debitValue: Double get() = debit.toDoubleOrNull() ?: 0.0
    val creditValue: Double get() = credit.toDoubleOrNull() ?: 0.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherEntryScreen(
    initialType: VoucherType,
    initialAccountId: String = "",
    editVoucherId: String? = null,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())
    val costCenters by mainViewModel.container.db.costCenterDao().observeCostCenters(companyId).collectAsState(initial = emptyList())
    var selectedType by remember { mutableStateOf(initialType) }

    // Standard Voucher States (Receipt / Payment)
    var selectedAccountId by remember { mutableStateOf(initialAccountId) }
    var selectedCashBoxId by remember { mutableStateOf(session.cashBoxes.firstOrNull { it.isDefault }?.id ?: session.cashBoxes.firstOrNull()?.id ?: "") }
    var amountText by remember { mutableStateOf("") }

    // Header States
    var voucherDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var selectedCurrencyId by remember { mutableStateOf(session.currencies.firstOrNull { it.isDefault }?.id ?: "") }

    // Lines State for Compound Journal Entry
    var voucherLines by remember {
        mutableStateOf(listOf(
            VoucherLineUIState(),
            VoucherLineUIState()
        ))
    }

    val totalDebit = voucherLines.sumOf { it.debitValue }
    val totalCredit = voucherLines.sumOf { it.creditValue }
    val balanceDifference = totalDebit - totalCredit
    val isBalanced = kotlin.math.abs(balanceDifference) < 0.001

    var showCalc by remember { mutableStateOf(false) }
    val attachmentPathState = remember { mutableStateOf<String?>(null) }
    var attachmentPath by attachmentPathState
    val savedAttachmentPathState = remember { mutableStateOf<String?>(null) }
    var savedAttachmentPath by savedAttachmentPathState
    var isCopyingAttachment by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            attachmentPathState.value
                ?.takeIf { it != savedAttachmentPathState.value }
                ?.let(mainViewModel.container.voucherAttachmentService::deleteLocalImage)
        }
    }

    fun attachImageUri(uri: Uri) {
        scope.launch {
            isCopyingAttachment = true
            try {
                val imageFile = withContext(Dispatchers.IO) {
                    mainViewModel.container.voucherAttachmentService.copyImage(companyId, uri)
                }
                attachmentPath?.let(mainViewModel.container.voucherAttachmentService::deleteLocalImage)
                attachmentPath = imageFile.absolutePath
            } catch (error: Exception) {
                mainViewModel.showError(error, "تعذر إرفاق صورة السند")
            } finally {
                isCopyingAttachment = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) attachImageUri(uri)
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) attachImageUri(uri)
    }

    fun launchCameraCapture() {
        val dir = File(context.filesDir, "attachments").apply { if (!isDirectory) mkdirs() }
        val file = File(dir, "voucher_cam_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    var editingVoucher by remember { mutableStateOf<VoucherEntity?>(null) }
    var editLoadFailed by remember { mutableStateOf(false) }

    // Load existing voucher if editing
    LaunchedEffect(editVoucherId, companyId) {
        val voucherId = editVoucherId ?: return@LaunchedEffect
        val voucher = mainViewModel.container.db.voucherDao().getVoucherInCompany(companyId, voucherId)?.takeIf { !it.isDeleted }

        if (voucher == null || !com.smartaccountant.pro.domain.repository.AccountingRepository.isManuallyDeletable(voucher)) {
            editLoadFailed = true
            return@LaunchedEffect
        }

        editingVoucher = voucher
        selectedType = voucher.type
        selectedCurrencyId = voucher.currencyId
        voucherDateMillis = voucher.dateMillis
        description = voucher.description
        attachmentPath = voucher.attachmentUrl.takeIf { it.isNotBlank() && !it.startsWith("http") }
        savedAttachmentPath = attachmentPath

        if (voucher.type == VoucherType.RECEIPT || voucher.type == VoucherType.PAYMENT) {
            selectedAccountId = voucher.accountId
            selectedCashBoxId = voucher.cashBoxId ?: ""
            amountText = if (voucher.amount > 0) voucher.amount.toString() else ""
        }

        // Load Lines if compound
        val lines = mainViewModel.container.db.voucherDao().getVoucherLines(voucherId)
        if (lines.isNotEmpty()) {
            voucherLines = lines.map {
                VoucherLineUIState(
                    id = it.id,
                    accountId = it.accountId,
                    debit = if (it.debit > 0) it.debit.toString() else "",
                    credit = if (it.credit > 0) it.credit.toString() else "",
                    note = it.note,
                    costCenterId = it.costCenterId
                )
            }
        }
    }

    LaunchedEffect(editLoadFailed) {
        if (editLoadFailed) {
            mainViewModel.showMessage("هذا السند غير متاح للتعديل")
            onBack()
        }
    }

    val currentCurrency = session.currencies.find { it.id == selectedCurrencyId } ?: session.currencies.firstOrNull()
    val todayText = remember { SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date()) }
    val quickDescriptions = listOf("دفعة من الحساب", "سداد فاتورة", "توريد نقدي", "تسوية حساب", "مصروفات عامة")

    fun submitSingleVoucher() {
        val currentUser = user ?: return
        if (isCopyingAttachment) {
            mainViewModel.showMessage("انتظر حتى يكتمل تحميل الصورة")
            return
        }
        val amountVal = amountText.toDoubleOrNull() ?: 0.0
        if (selectedAccountId.isBlank()) {
            mainViewModel.showMessage("يرجى اختيار الحساب / الطرف")
            return
        }
        if (selectedCashBoxId.isBlank()) {
            mainViewModel.showMessage("يرجى اختيار الصندوق أو البنك")
            return
        }
        if (amountVal <= 0) {
            mainViewModel.showMessage("يرجى إدخال مبلغ صحيح أكبر من الصفر")
            return
        }

        scope.launch {
            try {
                val finalDescription = buildString {
                    append(description.trim())
                    if (referenceNumber.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append("مرجع: ").append(referenceNumber.trim())
                    }
                }

                val localAttachment = attachmentPath?.let(::File)
                val attachmentUrl = localAttachment?.absolutePath.orEmpty()
                val base = editingVoucher

                val voucherToSave = base?.copy(
                    type = selectedType,
                    accountId = selectedAccountId,
                    cashBoxId = selectedCashBoxId,
                    currencyId = currentCurrency?.id ?: base.currencyId,
                    exchangeRate = currentCurrency?.sellRate ?: base.exchangeRate,
                    amount = amountVal,
                    description = finalDescription,
                    attachmentUrl = attachmentUrl,
                    dateMillis = voucherDateMillis,
                    updatedAt = System.currentTimeMillis()
                ) ?: VoucherEntity(
                    companyId = companyId,
                    voucherNumber = 0,
                    type = selectedType,
                    accountId = selectedAccountId,
                    cashBoxId = selectedCashBoxId,
                    currencyId = currentCurrency?.id ?: "cur-yer",
                    exchangeRate = currentCurrency?.sellRate ?: 1.0,
                    amount = amountVal,
                    description = finalDescription,
                    attachmentUrl = attachmentUrl,
                    dateMillis = voucherDateMillis
                )

                mainViewModel.container.accountingRepository.saveVoucher(
                    currentUser = currentUser,
                    voucher = voucherToSave,
                    isEdit = base != null
                )

                mainViewModel.showMessage(if (base != null) "تم تحديث السند بنجاح" else "تم حفظ ${selectedType.arabicTitle} بنجاح")
                savedAttachmentPath = attachmentPath
                onBack()
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر حفظ السند")
            }
        }
    }

    fun submitCompoundVoucher() {
        val currentUser = user ?: return
        if (isCopyingAttachment) {
            mainViewModel.showMessage("انتظر حتى يكتمل تحميل الصورة")
            return
        }
        if (!isBalanced) {
            mainViewModel.showMessage("لا يمكن الحفظ: القيد غير متزن (الفرق: $balanceDifference)")
            return
        }
        val activeLines = voucherLines.filter { it.accountId.isNotBlank() && (it.debitValue > 0 || it.creditValue > 0) }
        if (activeLines.isEmpty()) {
            mainViewModel.showMessage("يجب إدخال سطر واحد صحيح على الأقل")
            return
        }

        scope.launch {
            try {
                val finalDescription = buildString {
                    append(description.trim())
                    if (referenceNumber.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append("مرجع: ").append(referenceNumber.trim())
                    }
                }

                val localAttachment = attachmentPath?.let(::File)
                val attachmentUrl = localAttachment?.absolutePath.orEmpty()
                val base = editingVoucher

                val voucherToSave = base?.copy(
                    type = VoucherType.SIMPLE_JOURNAL,
                    currencyId = currentCurrency?.id ?: base.currencyId,
                    exchangeRate = currentCurrency?.sellRate ?: base.exchangeRate,
                    amount = totalDebit,
                    description = finalDescription,
                    attachmentUrl = attachmentUrl,
                    dateMillis = voucherDateMillis,
                    updatedAt = System.currentTimeMillis()
                ) ?: VoucherEntity(
                    companyId = companyId,
                    voucherNumber = 0,
                    type = VoucherType.SIMPLE_JOURNAL,
                    accountId = "COMPOUND",
                    currencyId = currentCurrency?.id ?: "cur-yer",
                    exchangeRate = currentCurrency?.sellRate ?: 1.0,
                    amount = totalDebit,
                    description = finalDescription,
                    attachmentUrl = attachmentUrl,
                    dateMillis = voucherDateMillis
                )

                val dbLines = activeLines.map { line ->
                    VoucherLineEntity(
                        id = if (base != null && line.id.length > 10) line.id else UUID.randomUUID().toString(),
                        voucherId = voucherToSave.id,
                        accountId = line.accountId,
                        debit = line.debitValue,
                        credit = line.creditValue,
                        currencyId = currentCurrency?.id ?: "cur-yer",
                        exchangeRate = currentCurrency?.sellRate ?: 1.0,
                        note = line.note,
                        costCenterId = line.costCenterId
                    )
                }

                mainViewModel.container.accountingRepository.saveCompoundVoucher(
                    currentUser = currentUser,
                    voucher = voucherToSave,
                    lines = dbLines,
                    isEdit = base != null
                )

                mainViewModel.showMessage(if (base != null) "تم تحديث القيد بنجاح" else "تم حفظ القيد بنجاح")
                savedAttachmentPath = attachmentPath
                onBack()
            } catch (e: Exception) {
                mainViewModel.showError(e, "تعذر حفظ السند")
            }
        }
    }

    val isSingleMode = selectedType == VoucherType.RECEIPT || selectedType == VoucherType.PAYMENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editingVoucher != null) "تعديل ${selectedType.arabicTitle}"
                        else "إدخال ${selectedType.arabicTitle}"
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = { IconButton(onClick = { showCalc = true }) { Icon(Icons.Default.Calculate, contentDescription = "الحاسبة") } }
            )
        },
        bottomBar = {
            if (!isSingleMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المدين: ${totalDebit}", color = DebitRed, fontWeight = FontWeight.Bold)
                            Text("إجمالي الدائن: ${totalCredit}", color = CreditGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = if (isBalanced) CreditGreen else WarningAmber
                            val statusText = if (isBalanced) "متزن" else "غير متزن (الفرق: ${kotlin.math.abs(balanceDifference)})"
                            Icon(
                                imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

                            Button(
                                onClick = { submitCompoundVoucher() },
                                enabled = isBalanced && totalDebit > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("حفظ القيد", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { p -> if (editVoucherId != null) p.canEditVoucher else p.canAddVoucher }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== محدد نوع السند (أزرار التبديل السريعة) =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == VoucherType.RECEIPT,
                        onClick = { selectedType = VoucherType.RECEIPT },
                        label = { Text("سند قبض", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallReceived, contentDescription = null, tint = CreditGreen) }
                    )
                    FilterChip(
                        selected = selectedType == VoucherType.PAYMENT,
                        onClick = { selectedType = VoucherType.PAYMENT },
                        label = { Text("سند صرف", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = null, tint = DebitRed) }
                    )
                    FilterChip(
                        selected = selectedType == VoucherType.SIMPLE_JOURNAL,
                        onClick = { selectedType = VoucherType.SIMPLE_JOURNAL },
                        label = { Text("قيد محاسبي مركب", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) }
                    )
                }

                // ===== الترويسة العلوية =====
                val headerColor = when (selectedType) {
                    VoucherType.RECEIPT -> Color(0xFF1B5E20)
                    VoucherType.PAYMENT -> Color(0xFFB71C1C)
                    else -> Color(0xFF1B3A6B)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(headerColor, headerColor.copy(alpha = 0.85f))),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                                Icon(
                                    imageVector = when (selectedType) {
                                        VoucherType.RECEIPT -> Icons.AutoMirrored.Filled.CallReceived
                                        VoucherType.PAYMENT -> Icons.AutoMirrored.Filled.CallMade
                                        else -> Icons.Default.AccountBalance
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (editingVoucher != null) "تعديل ${selectedType.arabicTitle}" else selectedType.arabicTitle,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(todayText, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (isSingleMode) {
                    // ==========================================
                    // ===== نموذج السند الفردي (قبض / صرف) =====
                    // ==========================================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. اختيار الحساب (الطرف / العميل / المورد)
                            var accountExpanded by remember { mutableStateOf(false) }
                            var accountSearchQuery by remember { mutableStateOf("") }
                            val filteredAccounts = remember(accounts, accountSearchQuery) {
                                if (accountSearchQuery.isBlank()) accounts
                                else accounts.filter { it.name.contains(accountSearchQuery, ignoreCase = true) || it.phone.contains(accountSearchQuery) }
                            }
                            val selectedAccount = accounts.find { it.id == selectedAccountId }

                            ExposedDropdownMenuBox(
                                expanded = accountExpanded,
                                onExpandedChange = { accountExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedAccount?.let { "${it.name} (${it.category.arabicTitle})" } ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (selectedType == VoucherType.RECEIPT) "المستلم منه (الحساب / العميل)" else "المصروف له (الحساب / المورد)") },
                                    placeholder = { Text("اختر الحساب...") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                                    shape = RoundedCornerShape(12.dp),
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
                                    filteredAccounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(acc.name, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        if (acc.phone.isNotBlank()) "${acc.category.arabicTitle} • هاتف: ${acc.phone}"
                                                        else acc.category.arabicTitle,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedAccountId = acc.id
                                                accountExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 2. اختيار الصندوق / البنك
                            var cashBoxExpanded by remember { mutableStateOf(false) }
                            val selectedCashBox = session.cashBoxes.find { it.id == selectedCashBoxId }

                            ExposedDropdownMenuBox(
                                expanded = cashBoxExpanded,
                                onExpandedChange = { cashBoxExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedCashBox?.let { "${it.name} ${if (it.isBank) "(بنك)" else "(صندوق)"}" } ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (selectedType == VoucherType.RECEIPT) "الصندوق / البنك المستلِم" else "الصندوق / البنك المصروف منه") },
                                    placeholder = { Text("اختر الصندوق أو البنك...") },
                                    leadingIcon = { Icon(if (selectedCashBox?.isBank == true) Icons.Default.AccountBalance else Icons.Default.PointOfSale, contentDescription = null) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cashBoxExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = cashBoxExpanded,
                                    onDismissRequest = { cashBoxExpanded = false }
                                ) {
                                    session.cashBoxes.forEach { box ->
                                        DropdownMenuItem(
                                            text = { Text("${box.name} ${if (box.isBank) "(بنك)" else "(صندوق)"}") },
                                            onClick = {
                                                selectedCashBoxId = box.id
                                                cashBoxExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 3. المبلغ + التفقيط العربي + الحاسبة
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("مبلغ السند") },
                                leadingIcon = { Text(currentCurrency?.symbol ?: "ريال", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { showCalc = true }) {
                                        Icon(Icons.Default.Calculate, contentDescription = "الحاسبة", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                            if (parsedAmount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = ArabicTafqeet.convert(parsedAmount, currentCurrency?.name ?: "ريال", currentCurrency?.subUnitName ?: "فلس"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            // 4. التاريخ والعملة
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(DateUtils.formatDate(voucherDateMillis), fontSize = 13.sp)
                                }

                                var currencyExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = currencyExpanded,
                                    onExpandedChange = { currencyExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = currentCurrency?.name ?: "العملة",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("العملة") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
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
                            }

                            // 5. البيان والاقترحات السريعة
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                label = { Text("البيان / ملاحظة السند") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                quickDescriptions.forEach { qd ->
                                    SuggestionChip(
                                        onClick = { description = qd },
                                        label = { Text(qd, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // 6. رقم المرجع (اختياري)
                            OutlinedTextField(
                                value = referenceNumber,
                                onValueChange = { referenceNumber = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("رقم المرجع / الشيك (اختياري)") },
                                leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                                shape = RoundedCornerShape(12.dp)
                            )

                            // 7. مرفق الصورة
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("مرفق صورة السند (اختياري)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { launchCameraCapture() },
                                            enabled = !isCopyingAttachment,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("كاميرا")
                                        }
                                        OutlinedButton(
                                            onClick = { imagePicker.launch("image/*") },
                                            enabled = !isCopyingAttachment,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("معرض")
                                        }
                                    }

                                    attachmentPath?.let { path ->
                                        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                                            AsyncImage(
                                                model = File(path),
                                                contentDescription = "صورة المرفق",
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { attachmentPath = null },
                                                modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "حذف الصورة", tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // 8. زر الحفظ الرئيسي للسند الفردي
                            Button(
                                onClick = { submitSingleVoucher() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedType == VoucherType.RECEIPT) CreditGreen else DebitRed
                                )
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (editingVoucher != null) "تحديث ${selectedType.arabicTitle}" else "حفظ ${selectedType.arabicTitle}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // ==============================================
                    // ===== نموذج القيد المحاسبي المركب (الجدول) =====
                    // ==============================================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(DateUtils.formatDate(voucherDateMillis), fontSize = 13.sp)
                                }

                                var currencyExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = currencyExpanded,
                                    onExpandedChange = { currencyExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = currentCurrency?.name ?: "العملة",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("العملة") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
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
                            }
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                label = { Text("البيان العام للقيد") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // ===== جدول السطور =====
                    Text("تفاصيل القيد (الأسطر)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp))

                    voucherLines.forEachIndexed { index, line ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.width(8.dp))

                                    var accountExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }, modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = accounts.find { it.id == line.accountId }?.name ?: "",
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("اختر الحساب...") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                                            accounts.forEach { acc ->
                                                DropdownMenuItem(
                                                    text = { Text(acc.name) },
                                                    onClick = {
                                                        val newLines = voucherLines.toMutableList()
                                                        newLines[index] = line.copy(accountId = acc.id)
                                                        voucherLines = newLines
                                                        accountExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    IconButton(onClick = {
                                        if (voucherLines.size > 2) {
                                            val newLines = voucherLines.toMutableList()
                                            newLines.removeAt(index)
                                            voucherLines = newLines
                                        } else {
                                            mainViewModel.showMessage("يجب أن يحتوي القيد على سطرين كحد أدنى")
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف السطر", tint = DebitRed)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = line.debit,
                                        onValueChange = {
                                            val newLines = voucherLines.toMutableList()
                                            newLines[index] = line.copy(debit = it, credit = "")
                                            voucherLines = newLines
                                        },
                                        label = { Text("مدين (+)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = line.credit,
                                        onValueChange = {
                                            val newLines = voucherLines.toMutableList()
                                            newLines[index] = line.copy(credit = it, debit = "")
                                            voucherLines = newLines
                                        },
                                        label = { Text("دائن (-)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = line.note,
                                        onValueChange = {
                                            val newLines = voucherLines.toMutableList()
                                            newLines[index] = line.copy(note = it)
                                            voucherLines = newLines
                                        },
                                        placeholder = { Text("ملاحظة السطر (اختياري)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (costCenters.isNotEmpty()) {
                                        var ccExpanded by remember { mutableStateOf(false) }
                                        ExposedDropdownMenuBox(
                                            expanded = ccExpanded,
                                            onExpandedChange = { ccExpanded = it },
                                            modifier = Modifier.weight(0.7f)
                                        ) {
                                            OutlinedTextField(
                                                value = costCenters.find { it.id == line.costCenterId }?.name ?: "مركز التكلفة",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ccExpanded) },
                                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(expanded = ccExpanded, onDismissRequest = { ccExpanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("بدون مركز", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                                    onClick = {
                                                        val newLines = voucherLines.toMutableList()
                                                        newLines[index] = line.copy(costCenterId = null)
                                                        voucherLines = newLines
                                                        ccExpanded = false
                                                    }
                                                )
                                                costCenters.forEach { cc ->
                                                    DropdownMenuItem(
                                                        text = { Text(cc.name) },
                                                        onClick = {
                                                            val newLines = voucherLines.toMutableList()
                                                            newLines[index] = line.copy(costCenterId = cc.id)
                                                            voucherLines = newLines
                                                            ccExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            val newLines = voucherLines.toMutableList()
                            newLines.add(VoucherLineUIState())
                            voucherLines = newLines
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إضافة سطر جديد للقيد", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = voucherDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            voucherDateMillis = it
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCalc) {
        CalculatorDialog(
            onDismiss = { showCalc = false },
            onUseResult = { calculatedVal ->
                if (isSingleMode) {
                    amountText = if (calculatedVal % 1.0 == 0.0) calculatedVal.toLong().toString() else calculatedVal.toString()
                } else if (voucherLines.isNotEmpty()) {
                    val newLines = voucherLines.toMutableList()
                    val last = newLines.last()
                    newLines[newLines.lastIndex] = last.copy(debit = calculatedVal.toString())
                    voucherLines = newLines
                }
                showCalc = false
            }
        )
    }
}
