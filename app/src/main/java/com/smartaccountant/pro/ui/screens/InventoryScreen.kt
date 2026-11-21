package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.data.model.AuditAction
import com.smartaccountant.pro.data.model.AuditLogEntity
import com.smartaccountant.pro.data.model.ProductEntity
import com.smartaccountant.pro.data.model.StockMovementEntity
import com.smartaccountant.pro.services.BarcodeScannerService
import com.smartaccountant.pro.ui.components.EmptyState
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(mainViewModel: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser
    val canSeeCost = PermissionManager.permissionsOf(user).canViewCostAndProfit
    val canEdit = PermissionManager.permissionsOf(user).canAddEditProduct

    val productsFlow = remember(companyId) { mainViewModel.container.db.inventoryDao().observeProducts(companyId) }
    val products by productsFlow.collectAsState(initial = emptyList())
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showAddProduct by remember { mutableStateOf(false) }

    val filtered = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter { it.name.contains(searchQuery.trim(), true) || it.barcode.contains(searchQuery.trim(), true) || it.categoryName.contains(searchQuery.trim(), true) }
    }
    val totalCount = products.size
    val stockValue = products.sumOf { it.stockQuantity * it.purchasePrice1 }
    val lowStock = products.count { it.stockQuantity < it.minStockAlert }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المخزون والأصناف") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { showAddProduct = true }) { Icon(Icons.Default.Add, contentDescription = "إضافة صنف") }
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewInventory }) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ===== بحث =====
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("بحث بالاسم أو الباركود أو الفئة...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "مسح") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // ===== بطاقات الملخص =====
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard("إجمالي الأصناف", "$totalCount", Icons.Default.Inventory2, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    SummaryCard("قيمة المخزون", CurrencyFormatter.format(stockValue), Icons.Default.AccountBalanceWallet, CreditGreen, Modifier.weight(1f))
                    SummaryCard("أوشكت على النفاد", "$lowStock", Icons.Default.Warning, if (lowStock > 0) DebitRed else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))

                // ===== قائمة الأصناف =====
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                productsFlow.first()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "تعذر تحديث الأصناف")
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { prod ->
                        val isLow = prod.stockQuantity < prod.minStockAlert
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            onClick = { if (canEdit) editingProduct = prod }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            buildString {
                                                if (prod.categoryName.isNotBlank()) append("${prod.categoryName} | ")
                                                if (prod.barcode.isNotBlank()) append("باركود: ${prod.barcode}") else append("بدون باركود")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // الكمية مع تنبيه لوني
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isLow) DebitRed.copy(alpha = 0.12f) else CreditGreen.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "${prod.stockQuantity} ${prod.unit1Name}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLow) DebitRed else CreditGreen,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                if (isLow) {
                                    Text(
                                        "تنبيه: الكمية أقل من الحد الأدنى (${prod.minStockAlert})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DebitRed
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("بيع: ${CurrencyFormatter.format(prod.salePrice1)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    if (canSeeCost) Text("شراء: ${CurrencyFormatter.format(prod.purchasePrice1)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.Inventory2,
                                title = if (products.isEmpty()) "لا توجد أصناف بعد" else "لا توجد نتائج مطابقة",
                                subtitle = if (products.isEmpty()) "أضف أول صنف لإدارة المخزون." else "تحقق من الاسم أو الباركود أو الفئة.",
                                actionLabel = when {
                                    products.isEmpty() && canEdit -> "أضف أول صنف"
                                    products.isEmpty() -> "تحديث القائمة"
                                    else -> "مسح البحث"
                                },
                                onAction = {
                                    when {
                                        products.isEmpty() && canEdit -> showAddProduct = true
                                        products.isEmpty() -> scope.launch { productsFlow.first() }
                                        else -> searchQuery = ""
                                    }
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    // ===== نافذة إضافة/تعديل صنف =====
    val dialogProduct = if (showAddProduct) ProductEntity(companyId = companyId, warehouseId = session.warehouses.firstOrNull { it.isDefault }?.id ?: "wh-main", name = "") else editingProduct
    if (dialogProduct != null) {
        ProductEditDialog(
            product = dialogProduct,
            isNew = showAddProduct,
            warehouses = session.warehouses,
            onDismiss = { showAddProduct = false; editingProduct = null },
            onSave = { saved, initQty ->
                val isNewProduct = showAddProduct
                scope.launch {
                    val defaultWh = saved.warehouseId.ifBlank { session.warehouses.firstOrNull { it.isDefault }?.id ?: "wh-main" }
                    val finalProduct = saved.copy(
                        warehouseId = defaultWh,
                        stockQuantity = if (isNewProduct) initQty else saved.stockQuantity,
                        createdByUserId = if (isNewProduct) user?.id ?: "" else saved.createdByUserId
                    )
                    mainViewModel.container.db.withTransaction {
                        mainViewModel.container.db.inventoryDao().upsertProduct(finalProduct)
                        if (initQty != 0.0 && isNewProduct) {
                            mainViewModel.container.db.inventoryDao().upsertStockMovement(
                                StockMovementEntity(
                                    id = "${finalProduct.id}_opening", companyId = companyId, productId = finalProduct.id,
                                    warehouseId = defaultWh, sourceDocId = finalProduct.id, deltaQuantity = initQty
                                )
                            )
                        }
                        user?.let { actor ->
                            mainViewModel.container.db.userSyncDao().insertAuditLog(
                                AuditLogEntity(
                                    companyId = companyId,
                                    userId = actor.id,
                                    userName = actor.fullName,
                                    deviceName = mainViewModel.container.deviceName,
                                    action = if (isNewProduct) AuditAction.CREATE else AuditAction.UPDATE,
                                    entityType = "صنف مخزني",
                                    entityId = finalProduct.id,
                                    summary = "${if (isNewProduct) "إضافة" else "تعديل"} الصنف: ${finalProduct.name}"
                                )
                            )
                        }
                    }
                    mainViewModel.container.syncEngine.scheduleBackgroundSync(companyId)
                    showAddProduct = false
                    editingProduct = null
                    mainViewModel.showMessage(if (isNewProduct) "تمت إضافة الصنف ومزامنته" else "تم حفظ الصنف")
                }
            }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditDialog(
    product: ProductEntity,
    isNew: Boolean,
    warehouses: List<com.smartaccountant.pro.data.model.WarehouseEntity>,
    onDismiss: () -> Unit,
    onSave: (ProductEntity, Double) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var category by remember { mutableStateOf(product.categoryName) }
    var barcode by remember { mutableStateOf(product.barcode) }
    var unit1 by remember { mutableStateOf(product.unit1Name.ifBlank { "حبة" }) }
    var buyPrice by remember { mutableStateOf(if (product.purchasePrice1 > 0) product.purchasePrice1.toString() else "") }
    var salePrice by remember { mutableStateOf(if (product.salePrice1 > 0) product.salePrice1.toString() else "") }
    var minStock by remember { mutableStateOf(if (product.minStockAlert > 0) product.minStockAlert.toString() else "5") }
    var unit2 by remember { mutableStateOf(product.unit2Name) }
    var unit2Factor by remember { mutableStateOf(if (product.unit2Factor > 0) product.unit2Factor.toString() else "12") }
    var salePrice2 by remember { mutableStateOf(if (product.salePrice2 > 0) product.salePrice2.toString() else "") }
    var initialStock by remember { mutableStateOf(if (isNew) "0" else "") }
    var selectedWarehouseId by remember { mutableStateOf(product.warehouseId.ifBlank { warehouses.firstOrNull { it.isDefault }?.id ?: "" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (isNew) "إضافة صنف جديد" else "تعديل: ${product.name}")
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // البيانات الأساسية
                Text("البيانات الأساسية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الصنف *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("الفئة") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = minStock, onValueChange = { minStock = it }, label = { Text("حد النفاد") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود") }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = { barcode = BarcodeScannerService.generateUniqueBarcode() }) { Icon(Icons.Default.QrCode, contentDescription = "توليد باركود") }
                }

                // الأسعار والمخزون
                Text("الأسعار والمخزون", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = unit1, onValueChange = { unit1 = it }, label = { Text("الوحدة الأساسية") }, modifier = Modifier.weight(1f), singleLine = true)
                    if (isNew) OutlinedTextField(value = initialStock, onValueChange = { initialStock = it }, label = { Text("كمية افتتاحية") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = buyPrice, onValueChange = { buyPrice = it }, label = { Text("سعر الشراء") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = salePrice, onValueChange = { salePrice = it }, label = { Text("سعر البيع") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                // الوحدة الثانية
                Text("الوحدة الثانية (اختياري)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = unit2, onValueChange = { unit2 = it }, label = { Text("اسم الوحدة 2") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = unit2Factor, onValueChange = { unit2Factor = it }, label = { Text("التعبئة") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = salePrice2, onValueChange = { salePrice2 = it }, label = { Text("السعر 2") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                // المخزن
                if (warehouses.size > 1) {
                    Text("المخزن", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        warehouses.forEach { wh ->
                            FilterChip(
                                selected = selectedWarehouseId == wh.id,
                                onClick = { selectedWarehouseId = wh.id },
                                label = { Text(wh.name, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                val updated = product.copy(
                    name = name.trim(),
                    categoryName = category.trim().ifBlank { "عام" },
                    barcode = barcode.trim(),
                    unit1Name = unit1.trim().ifBlank { "حبة" },
                    purchasePrice1 = buyPrice.toDoubleOrNull() ?: 0.0,
                    salePrice1 = salePrice.toDoubleOrNull() ?: 0.0,
                    minStockAlert = minStock.toDoubleOrNull() ?: 5.0,
                    unit2Name = unit2.trim(),
                    unit2Factor = unit2Factor.toDoubleOrNull() ?: 1.0,
                    salePrice2 = salePrice2.toDoubleOrNull() ?: 0.0,
                    warehouseId = selectedWarehouseId,
                    updatedAt = System.currentTimeMillis()
                )
                onSave(updated, if (isNew) initialStock.toDoubleOrNull() ?: 0.0 else 0.0)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
