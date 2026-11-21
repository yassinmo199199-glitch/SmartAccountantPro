package com.smartaccountant.pro.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.model.*
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.WarningAmber
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditorScreen(
    initialType: InvoiceType,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    preselectedAccountId: String = ""
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser
    val canViewCost = PermissionManager.permissionsOf(user).canViewCostAndProfit

    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())
    val products by mainViewModel.container.db.inventoryDao().observeProducts(companyId).collectAsState(initial = emptyList())
    val invoices by mainViewModel.container.db.invoiceDao().observeInvoices(companyId).collectAsState(initial = emptyList())

    var selectedType by remember { mutableStateOf(initialType) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedAccountId by remember { mutableStateOf(preselectedAccountId) }
    var originalInvoiceId by remember { mutableStateOf<String?>(null) }
    var selectedCashBoxId by remember { mutableStateOf("") }
    var dueDateMillis by remember { mutableLongStateOf(defaultInvoiceDueDate()) }
    var invoiceDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showInvoiceDatePicker by remember { mutableStateOf(false) }
    var barcodeSearch by remember { mutableStateOf("") }
    val cartItems = remember { mutableStateListOf<InvoiceItemEntity>() }

    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var selectedUnitLevel by remember { mutableIntStateOf(1) }
    var meteringMode by remember { mutableStateOf(MeteringMode.NONE) }
    var dimLength by remember { mutableStateOf("1") }
    var dimWidth by remember { mutableStateOf("1") }
    var piecesText by remember { mutableStateOf("1") }
    var qtyText by remember { mutableStateOf("1") }
    var customPriceText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var isDiscountPct by remember { mutableStateOf(session.settings.discountAsPercentageDefault) }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(accounts, preselectedAccountId) {
        selectedAccountId = accounts.firstOrNull { it.id == preselectedAccountId }?.id
            ?: accounts.firstOrNull()?.id.orEmpty()
    }
    LaunchedEffect(session.cashBoxes) { if (selectedCashBoxId.isEmpty()) selectedCashBoxId = session.cashBoxes.firstOrNull()?.id ?: "" }

    val subTotal = cartItems.sumOf { it.lineTotal }
    val (discountValue, taxValue, netTotal) = mainViewModel.container.invoiceRepository.calculateInvoiceTotals(
        subTotal = subTotal,
        discountInput = discountText.toDoubleOrNull() ?: 0.0,
        isDiscountPercentage = isDiscountPct,
        enableTax = session.settings.enableTax,
        taxPercent = session.settings.defaultTaxPercent,
        calculateTaxAfterDiscount = session.settings.calculateTaxAfterDiscount
    )

    val headerColor = when (selectedType) {
        InvoiceType.SALE, InvoiceType.FREE_SALE -> CreditGreen
        InvoiceType.PURCHASE -> Color(0xFF1565C0)
        else -> WarningAmber
    }
    val defaultCurrency = session.currencies.firstOrNull { it.isDefault } ?: session.currencies.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${selectedType.arabicTitle} — فوترة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ===== الترويسة الملونة: النوع + الدفع =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(headerColor, headerColor.copy(alpha = 0.82f))),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(selectedType.arabicTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("فاتورة جديدة — ${session.activeCompany?.nameAr ?: ""}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InvoiceType.entries.forEach { t ->
                                FilterChip(
                                    selected = selectedType == t,
                                    onClick = { selectedType = t; originalInvoiceId = null },
                                    label = { Text(t.arabicTitle, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.White,
                                        selectedLabelColor = headerColor
                                    )
                                )
                            }
                        }
                        if (selectedType != InvoiceType.SALE_RETURN && selectedType != InvoiceType.PURCHASE_RETURN) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaymentMethod.entries.forEach { pm ->
                                    FilterChip(
                                        selected = paymentMethod == pm,
                                        onClick = { paymentMethod = pm },
                                        label = { Text(pm.arabicTitle, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = headerColor
                                        )
                                    )
                                }
                            }
                        } else {
                            Text(
                                "طريقة الدفع تُحدد من الفاتورة الأصلية: ${invoices.firstOrNull { it.id == originalInvoiceId }?.paymentMethod?.arabicTitle ?: "اختر الفاتورة الأصلية"}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showInvoiceDatePicker = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Event, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تاريخ الفاتورة: ${com.smartaccountant.pro.core.utils.DateUtils.formatDate(invoiceDateMillis)}")
                            }
                            if (paymentMethod == PaymentMethod.CREDIT) {
                                OutlinedButton(
                                    onClick = { showDueDatePicker = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.EventAvailable, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("الاستحقاق: ${com.smartaccountant.pro.core.utils.DateUtils.formatDate(dueDateMillis)}")
                                }
                            }
                        }
                    }
                }
            }

            // ===== العميل/المورد + الصندوق + الباركود =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(7.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("العميل / المورد والصندوق", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountPicker(
                            accounts = accounts,
                            selectedId = selectedAccountId,
                            onSelect = { selectedAccountId = it; originalInvoiceId = null },
                            modifier = Modifier.weight(1f)
                        )
                        CashBoxPicker(cashBoxes = session.cashBoxes, selectedId = selectedCashBoxId, onSelect = { selectedCashBoxId = it }, modifier = Modifier.weight(1f))
                    }
                    if (selectedType == InvoiceType.SALE_RETURN || selectedType == InvoiceType.PURCHASE_RETURN) {
                        val sourceTypes = if (selectedType == InvoiceType.SALE_RETURN) {
                            setOf(InvoiceType.SALE, InvoiceType.FREE_SALE)
                        } else {
                            setOf(InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE)
                        }
                        val originalInvoices = invoices.filter {
                            it.accountId == selectedAccountId && it.type in sourceTypes &&
                                it.isPosted && !it.isArchived && !it.isDeleted
                        }
                        SourceInvoicePicker(
                            invoices = originalInvoices,
                            selectedId = originalInvoiceId,
                            onSelect = { selected ->
                                originalInvoiceId = selected.id
                                paymentMethod = selected.paymentMethod
                            }
                        )
                        if (originalInvoices.isEmpty()) {
                            Text(
                                "لا توجد فواتير مرحّلة لهذا الحساب يمكن إرجاعها.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = barcodeSearch, onValueChange = { barcodeSearch = it },
                            label = { Text("امسح أو اكتب الباركود") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                val found = products.find { it.barcode == barcodeSearch.trim() }
                                if (found != null) {
                                    val price = if (selectedType == InvoiceType.PURCHASE) found.purchasePrice1 else found.salePrice1
                                    cartItems.add(
                                        InvoiceItemEntity(
                                            companyId = companyId, invoiceId = "", productId = found.id, productName = found.name,
                                            unitName = found.unit1Name, quantity = 1.0, unitPrice = price, costPriceAtTime = found.purchasePrice1, lineTotal = price
                                        )
                                    )
                                    barcodeSearch = ""
                                } else mainViewModel.showMessage("لا يوجد صنف بهذا الباركود")
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) { Text("إضافة") }
                    }
                }
            }

            // ===== إضافة صنف =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(7.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("إضافة صنف للفاتورة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }

                    ProductPicker(
                        products = products,
                        selected = selectedProduct,
                        onSelect = { prod ->
                            selectedProduct = prod
                            selectedUnitLevel = 1
                            meteringMode = MeteringMode.NONE
                            qtyText = "1"
                            customPriceText = (if (selectedType == InvoiceType.PURCHASE) prod.purchasePrice1 else prod.salePrice1).toString()
                        }
                    )

                    selectedProduct?.let { prod ->
                        if (session.settings.enableMultiUnits) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedUnitLevel == 1, onClick = { selectedUnitLevel = 1; customPriceText = prod.salePrice1.toString() }, label = { Text(prod.unit1Name) })
                                if (prod.unit2Name.isNotBlank()) FilterChip(selected = selectedUnitLevel == 2, onClick = { selectedUnitLevel = 2; customPriceText = prod.salePrice2.toString() }, label = { Text(prod.unit2Name) })
                                if (prod.unit3Name.isNotBlank()) FilterChip(selected = selectedUnitLevel == 3, onClick = { selectedUnitLevel = 3; customPriceText = prod.salePrice3.toString() }, label = { Text(prod.unit3Name) })
                            }
                        }
                        if (session.settings.enableMeteringSystem) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MeteringMode.entries.forEach { m ->
                                    FilterChip(selected = meteringMode == m, onClick = { meteringMode = m }, label = { Text(m.arabicTitle, fontSize = 12.sp) })
                                }
                            }
                        }
                        if (meteringMode != MeteringMode.NONE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = dimLength, onValueChange = { dimLength = it }, label = { Text("الطول / المساحة") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(value = dimWidth, onValueChange = { dimWidth = it }, label = { Text("العرض / الارتفاع") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(value = piecesText, onValueChange = { piecesText = it }, label = { Text("العدد") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // عدّاد الكمية + / -
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("الكمية", style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { qtyText = ((qtyText.toDoubleOrNull() ?: 1.0) - 1.0).coerceAtLeast(0.5).toString() }) {
                                            Icon(Icons.Default.Remove, contentDescription = "إنقاص")
                                            }
                                            Text(qtyText.ifBlank { "1" }, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), style = MaterialTheme.typography.titleMedium)
                                        IconButton(onClick = { qtyText = ((qtyText.toDoubleOrNull() ?: 1.0) + 1.0).toString() }) {
                                            Icon(Icons.Default.Add, contentDescription = "زيادة")
                                        }
                                    }
                                }
                                OutlinedTextField(value = customPriceText, onValueChange = { customPriceText = it }, label = { Text("سعر الوحدة") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                        }
                        val activeUnitFactor = when (selectedUnitLevel) { 2 -> prod.unit2Factor; 3 -> prod.unit3Factor; else -> 1.0 }
                        val enteredUnitPrice = customPriceText.toDoubleOrNull() ?: prod.salePrice1
                        if (selectedType == InvoiceType.SALE && enteredUnitPrice < prod.purchasePrice1 * activeUnitFactor) {
                            Text(
                                "تنبيه: سعر البيع أقل من تكلفة الصنف" +
                                    if (canViewCost) " (${CurrencyFormatter.format(prod.purchasePrice1 * activeUnitFactor)})" else "",
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                val pieces = piecesText.toIntOrNull() ?: 1
                                val len = dimLength.toDoubleOrNull() ?: 1.0
                                val wid = dimWidth.toDoubleOrNull() ?: 1.0
                                val qty = if (meteringMode == MeteringMode.NONE) qtyText.toDoubleOrNull() ?: 1.0
                                else mainViewModel.container.invoiceRepository.calculateMeteredQuantity(meteringMode, len, wid, wid, pieces, session.settings.minMeterUnder100CmCountsAs1Meter)
                                val price = customPriceText.toDoubleOrNull() ?: prod.salePrice1
                                val unitName = when (selectedUnitLevel) { 2 -> prod.unit2Name; 3 -> prod.unit3Name; else -> prod.unit1Name }
                                val factor = when (selectedUnitLevel) { 2 -> prod.unit2Factor; 3 -> prod.unit3Factor; else -> 1.0 }
                                cartItems.add(
                                    InvoiceItemEntity(
                                        companyId = companyId, invoiceId = "", productId = prod.id, productName = prod.name,
                                        unitName = unitName, unitFactor = factor, meteringMode = meteringMode,
                                        dimLength = len, dimWidth = wid, pieceCount = pieces, quantity = qty,
                                        unitPrice = price, costPriceAtTime = prod.purchasePrice1 * factor, lineTotal = qty * price
                                    )
                                )
                                qtyText = "1"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إدراج البند في الفاتورة")
                        }
                    }
                }
            }

            // ===== أصناف الفاتورة =====
            if (cartItems.isNotEmpty()) {
                if (selectedType == InvoiceType.SALE && cartItems.any { it.unitPrice < it.costPriceAtTime }) {
                    val belowCost = cartItems.filter { it.unitPrice < it.costPriceAtTime }
                    Text(
                        "تنبيه: يوجد صنف في الفاتورة بسعر بيع أقل من التكلفة" +
                            if (canViewCost) " (${belowCost.joinToString { it.productName }})" else "",
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("أصناف الفاتورة (${cartItems.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                cartItems.forEachIndexed { idx, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${item.productName} (${item.unitName})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { cartItems.removeAt(idx) }) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = DebitRed) }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                // عدّاد كمية داخل السلة
                                IconButton(onClick = {
                                    val newQty = (item.quantity - 1.0).coerceAtLeast(0.5)
                                    cartItems[idx] = item.copy(quantity = newQty, lineTotal = newQty * item.unitPrice)
                                }) { Icon(Icons.Default.Remove, contentDescription = "إنقاص", modifier = Modifier.size(18.dp)) }
                                Text("${item.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                                IconButton(onClick = {
                                    val newQty = item.quantity + 1.0
                                    cartItems[idx] = item.copy(quantity = newQty, lineTotal = newQty * item.unitPrice)
                                }) { Icon(Icons.Default.Add, contentDescription = "زيادة", modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.weight(1f))
                                Text("× ${CurrencyFormatter.format(item.unitPrice)}", style = MaterialTheme.typography.bodySmall)
                                Text(" = ${CurrencyFormatter.format(item.lineTotal)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ===== الخصم والملاحظات =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(7.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("الخصم والملاحظات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    if (session.settings.enableDiscount) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = discountText, onValueChange = { discountText = it }, label = { Text(if (isDiscountPct) "نسبة الخصم %" else "مبلغ الخصم") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
                            FilterChip(selected = isDiscountPct, onClick = { isDiscountPct = !isDiscountPct }, label = { Text(if (isDiscountPct) "نسبة %" else "مبلغ ثابت") })
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات الفاتورة") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                }
            }

            // ===== ملخص الفاتورة =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryRow("الإجمالي قبل الخصم", CurrencyFormatter.format(subTotal))
                    if (discountValue > 0) SummaryRow("الخصم", "-${CurrencyFormatter.format(discountValue)}", DebitRed)
                    if (taxValue > 0) SummaryRow("الضريبة (${session.settings.defaultTaxPercent}%)", "+${CurrencyFormatter.format(taxValue)}", CreditGreen)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("صافي الفاتورة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(CurrencyFormatter.format(netTotal), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                ArabicTafqeet.convert(netTotal, defaultCurrency?.name ?: "ريال", defaultCurrency?.subUnitName ?: ""),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // ===== أزرار الحفظ والمشاركة =====
            Button(
                onClick = {
                    val currentUser = user ?: return@Button
                    if (cartItems.isEmpty()) { mainViewModel.showMessage("املأ الفاتورة أولاً"); return@Button }
                    if ((selectedType == InvoiceType.SALE_RETURN || selectedType == InvoiceType.PURCHASE_RETURN) &&
                        originalInvoiceId == null
                    ) {
                        mainViewModel.showMessage("اختر الفاتورة الأصلية للمردود")
                        return@Button
                    }
                    val defaultCur = session.currencies.firstOrNull { it.isDefault }
                    val defaultWh = session.warehouses.firstOrNull { it.isDefault } ?: session.warehouses.firstOrNull()
                    scope.launch {
                        try {
                            mainViewModel.container.invoiceRepository.saveInvoice(
                                currentUser = currentUser,
                                invoice = InvoiceEntity(
                                    companyId = companyId, invoiceNumber = 0, type = selectedType, paymentMethod = paymentMethod,
                                    dateMillis = invoiceDateMillis,
                                    accountId = selectedAccountId, originalInvoiceId = originalInvoiceId,
                                    cashBoxId = selectedCashBoxId.ifBlank { session.cashBoxes.firstOrNull()?.id },
                                    warehouseId = defaultWh?.id ?: "wh-main",
                                    currencyId = defaultCur?.id ?: "cur-yer",
                                    exchangeRate = defaultCur?.sellRate ?: 1.0, subTotal = subTotal, discountAmount = discountValue,
                                    isDiscountPercentage = isDiscountPct, taxAmount = taxValue, netTotal = netTotal,
                                    paidAmount = if (paymentMethod == PaymentMethod.CASH) netTotal else 0.0,
                                    dueDateMillis = dueDateMillis,
                                    notes = notes.trim()
                                ),
                                items = cartItems.toList()
                            )
                            mainViewModel.showMessage("تم حفظ الفاتورة بنجاح")
                            onBack()
                        } catch (e: Exception) {
                            mainViewModel.showError(e, "تعذر حفظ الفاتورة")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ وترحيل الفاتورة", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val text = buildInvoiceShareText(
                        companyName = session.activeCompany?.nameAr ?: "",
                        typeTitle = selectedType.arabicTitle,
                        paymentTitle = paymentMethod.arabicTitle,
                        accountName = accounts.find { it.id == selectedAccountId }?.name ?: "",
                        items = cartItems.toList(),
                        subTotal = subTotal, discount = discountValue, tax = taxValue, net = netTotal,
                        currencySymbol = defaultCurrency?.symbol ?: ""
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "مشاركة الفاتورة عبر").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مشاركة الفاتورة نصياً عبر واتساب")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showInvoiceDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toUtcPickerDate(invoiceDateMillis))
        DatePickerDialog(
            onDismissRequest = { showInvoiceDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { invoiceDateMillis = fromUtcPickerDate(it) }
                    showInvoiceDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showInvoiceDatePicker = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showDueDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toUtcPickerDate(dueDateMillis))
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dueDateMillis = fromUtcPickerDate(it) }
                    showDueDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

private fun defaultInvoiceDueDate(): Long = java.util.Calendar.getInstance().apply {
    add(java.util.Calendar.DAY_OF_MONTH, 30)
    set(java.util.Calendar.HOUR_OF_DAY, 23)
    set(java.util.Calendar.MINUTE, 59)
    set(java.util.Calendar.SECOND, 59)
    set(java.util.Calendar.MILLISECOND, 999)
}.timeInMillis

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

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceInvoicePicker(
    invoices: List<InvoiceEntity>,
    selectedId: String?,
    onSelect: (InvoiceEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = invoices.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let {
                "${it.type.arabicTitle} ${it.displayNumber.ifBlank { "#${it.invoiceNumber}" }}"
            } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("الفاتورة الأصلية *") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            invoices.forEach { invoice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${invoice.displayNumber.ifBlank { "#${invoice.invoiceNumber}" }} • " +
                                "${com.smartaccountant.pro.core.utils.DateUtils.formatDate(invoice.dateMillis)} • " +
                                CurrencyFormatter.format(invoice.netTotal)
                        )
                    },
                    onClick = { onSelect(invoice); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(accounts: List<AccountEntity>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = accounts.find { it.id == selectedId }
    val filtered = remember(query, accounts) {
        if (query.isBlank()) accounts else accounts.filter { it.name.contains(query.trim(), true) }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (expanded || query.isNotBlank()) query else (selected?.name ?: "اختر العميل / المورد"),
            onValueChange = { query = it; expanded = true },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            label = { Text("العميل / المورد") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filtered.take(50).forEach { acc ->
                DropdownMenuItem(
                    text = { Text("${acc.name} (${acc.category.arabicTitle})") },
                    onClick = { onSelect(acc.id); query = ""; expanded = false }
                )
            }
            if (filtered.isEmpty()) DropdownMenuItem(text = { Text("لا توجد نتائج") }, onClick = { expanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashBoxPicker(cashBoxes: List<CashBoxEntity>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cashBoxes.find { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name ?: "الصندوق",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            label = { Text("الصندوق") },
            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cashBoxes.forEach { box ->
                DropdownMenuItem(
                    text = { Text("${box.name}${if (box.isBank) " (بنك)" else ""}") },
                    onClick = { onSelect(box.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPicker(products: List<ProductEntity>, selected: ProductEntity?, onSelect: (ProductEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, products) {
        if (query.isBlank()) products else products.filter {
            it.name.contains(query.trim(), true) || it.barcode.contains(query.trim(), true)
        }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (expanded || query.isNotBlank()) query else (selected?.let { "${it.name} — متوفر: ${it.stockQuantity} ${it.unit1Name}" } ?: "ابحث عن صنف بالاسم أو الباركود"),
            onValueChange = { query = it; expanded = true },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            label = { Text("الصنف") },
            leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filtered.take(50).forEach { prod ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(prod.name)
                            Text("متوفر: ${prod.stockQuantity} ${prod.unit1Name} | بيع: ${CurrencyFormatter.format(prod.salePrice1)}", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = { onSelect(prod); query = ""; expanded = false }
                )
            }
            if (filtered.isEmpty()) DropdownMenuItem(text = { Text("لا توجد نتائج") }, onClick = { expanded = false })
        }
    }
}

private fun buildInvoiceShareText(
    companyName: String,
    typeTitle: String,
    paymentTitle: String,
    accountName: String,
    items: List<InvoiceItemEntity>,
    subTotal: Double,
    discount: Double,
    tax: Double,
    net: Double,
    currencySymbol: String
): String {
    val sb = StringBuilder()
    sb.appendLine("فاتورة $typeTitle - $companyName")
    sb.appendLine("العميل/المورد: $accountName | الدفع: $paymentTitle")
    sb.appendLine("----------------------------------------")
    items.forEach { item ->
        sb.appendLine("${item.productName} | ${item.quantity} ${item.unitName} × ${item.unitPrice} = ${item.lineTotal}")
    }
    sb.appendLine("----------------------------------------")
    sb.appendLine("الإجمالي: $subTotal")
    if (discount > 0) sb.appendLine("الخصم: -$discount")
    if (tax > 0) sb.appendLine("الضريبة: +$tax")
    sb.appendLine("الصافي: $net $currencySymbol")
    return sb.toString()
}
