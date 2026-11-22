package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.data.model.*
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.TopBarDark
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private data class PosCartItem(
    val product: ProductEntity,
    var quantity: Double,
    var unitPrice: Double
) {
    val lineTotal: Double
        get() = quantity * unitPrice
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val products by mainViewModel.container.db.inventoryDao().observeProducts(companyId).collectAsState(initial = emptyList())
    val accounts by mainViewModel.container.db.accountDao().observeAccounts(companyId).collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    val cartItems = remember { mutableStateListOf<PosCartItem>() }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedAccountId by remember { mutableStateOf("") }
    var paidAmountInput by remember { mutableStateOf("") }
    var discountInput by remember { mutableStateOf("") }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
            p.barcode.contains(searchQuery, ignoreCase = true)
        }
    }

    val cartSubtotal = cartItems.sumOf { it.lineTotal }
    val discountVal = discountInput.toDoubleOrNull() ?: 0.0
    val taxRate = if (session.settings.enableTax) session.settings.defaultTaxPercent else 0.0
    val taxVal = (cartSubtotal - discountVal).coerceAtLeast(0.0) * (taxRate / 100.0)
    val netTotal = (cartSubtotal - discountVal + taxVal).coerceAtLeast(0.0)

    fun addToCart(product: ProductEntity) {
        val existing = cartItems.find { it.product.id == product.id }
        if (existing != null) {
            existing.quantity += 1.0
            val idx = cartItems.indexOf(existing)
            cartItems[idx] = existing.copy(quantity = existing.quantity)
        } else {
            cartItems.add(PosCartItem(product, 1.0, product.salePrice1))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نقطة البيع السريعة (POS)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    if (cartItems.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text("${cartItems.sumOf { it.quantity }.toInt()}") } }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "السلة", modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarDark, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            if (cartItems.isNotEmpty()) {
                Surface(
                    color = Color(0xFF0F1F38),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("إجمالي الفاتورة (${cartItems.size} صنف)", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            Text(CurrencyFormatter.formatAmount(netTotal), color = CreditGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showCheckoutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CreditGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("دفع وحفظ الفاتورة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canCreateSaleInvoice }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن صنف بالاسم أو الباركود...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = null) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1.3f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addToCart(product) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("المتوفر: ${product.stockQuantity.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatAmount(product.salePrice1), color = Color(0xFF1565C0), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("سلة الفاتورة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))

                            if (cartItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("انقر على أي صنف لإضافته للفاتورة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(cartItems) { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                                Text("${item.quantity.toInt()} × ${CurrencyFormatter.formatAmount(item.unitPrice)}", fontSize = 10.sp, color = Color.Gray)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (item.quantity > 1) {
                                                            val idx = cartItems.indexOf(item)
                                                            cartItems[idx] = item.copy(quantity = item.quantity - 1)
                                                        } else {
                                                            cartItems.remove(item)
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp)) }

                                                Text("${item.quantity.toInt()}", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))

                                                IconButton(
                                                    onClick = {
                                                        val idx = cartItems.indexOf(item)
                                                        cartItems[idx] = item.copy(quantity = item.quantity + 1)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCheckoutDialog && user != null) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showCheckoutDialog = false },
            title = { Text("إتمام فاتورة البيع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("طريقة الدفع:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedPaymentMethod == PaymentMethod.CASH,
                            onClick = { selectedPaymentMethod = PaymentMethod.CASH },
                            label = { Text("نقداً") }
                        )
                        FilterChip(
                            selected = selectedPaymentMethod == PaymentMethod.CREDIT,
                            onClick = { selectedPaymentMethod = PaymentMethod.CREDIT },
                            label = { Text("آجل") }
                        )
                    }

                    if (selectedPaymentMethod == PaymentMethod.CREDIT) {
                        Text("العميل / الحساب:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val customers = accounts.filter { it.category == AccountCategory.CUSTOMERS }
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = customers.find { it.id == selectedAccountId }?.name ?: "اختر العميل...",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                customers.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name) },
                                        onClick = { selectedAccountId = c.id; expanded = false }
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("صافي المطلوب:", fontWeight = FontWeight.Bold)
                        Text(CurrencyFormatter.formatAmount(netTotal), fontWeight = FontWeight.Bold, color = CreditGreen)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                val invoiceItems = cartItems.map { item ->
                                    InvoiceItemEntity(
                                        companyId = companyId,
                                        invoiceId = "",
                                        productId = item.product.id,
                                        productName = item.product.name,
                                        unitName = item.product.unit1Name,
                                        quantity = item.quantity,
                                        unitPrice = item.unitPrice,
                                        costPriceAtTime = item.product.purchasePrice1,
                                        lineTotal = item.lineTotal
                                    )
                                }
                                val customerAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull { it.category == AccountCategory.CUSTOMERS }
                                val defaultCashBox = session.cashBoxes.firstOrNull()

                                val invoice = InvoiceEntity(
                                    companyId = companyId,
                                    invoiceNumber = 0L,
                                    displayNumber = "",
                                    type = InvoiceType.SALE,
                                    paymentMethod = selectedPaymentMethod,
                                    accountId = customerAccount?.id ?: "",
                                    cashBoxId = defaultCashBox?.id ?: "",
                                    warehouseId = session.warehouses.firstOrNull()?.id ?: "",
                                    currencyId = session.currencies.firstOrNull { it.isDefault }?.id ?: "",
                                    subTotal = cartSubtotal,
                                    discountAmount = discountVal,
                                    taxAmount = taxVal,
                                    netTotal = netTotal,
                                    paidAmount = if (selectedPaymentMethod == PaymentMethod.CASH) netTotal else 0.0,
                                    notes = "فاتورة نقاط بيع سريعة (POS)",
                                    createdByUserId = user.id,
                                    createdByUserName = user.fullName,
                                    isPosted = true
                                )

                                mainViewModel.container.invoiceRepository.saveInvoice(
                                    currentUser = user,
                                    invoice = invoice,
                                    items = invoiceItems
                                )

                                mainViewModel.showMessage("تم حفظ فاتورة البيع بنجاح!")
                                cartItems.clear()
                                showCheckoutDialog = false
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل حفظ فاتورة البيع")
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving && cartItems.isNotEmpty()
                ) {
                    Text("تأكيد وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckoutDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
