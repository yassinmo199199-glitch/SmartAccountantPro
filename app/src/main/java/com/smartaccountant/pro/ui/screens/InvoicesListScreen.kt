package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.EmptyState
import com.smartaccountant.pro.ui.navigation.Screen
import com.smartaccountant.pro.ui.theme.ActionOrange
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.TopBarDark
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val InvoiceListBlack = TopBarDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesListScreen(mainViewModel: MainViewModel, navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val invoicesFlow = remember(companyId) { mainViewModel.container.db.invoiceDao().observeInvoices(companyId) }
    val invoices by invoicesFlow.collectAsState(initial = emptyList())
    var isRefreshing by remember { mutableStateOf(false) }
    var invoiceToArchive by remember { mutableStateOf<com.smartaccountant.pro.data.model.InvoiceEntity?>(null) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<InvoiceType?>(null) }
    var unpostedOnly by rememberSaveable { mutableStateOf(false) }
    var unpaidOnly by rememberSaveable { mutableStateOf(false) }
    val visibleInvoices = invoices.filter { invoice ->
        val query = searchText.trim()
        (selectedType == null || invoice.type == selectedType) &&
            (!unpostedOnly || !invoice.isPosted) &&
            (!unpaidOnly || invoice.netTotal > invoice.paidAmount) &&
            (query.isBlank() ||
                invoice.displayNumber.contains(query, ignoreCase = true) ||
                invoice.invoiceNumber.toString().contains(query) ||
                invoice.createdByUserName.contains(query, ignoreCase = true) ||
                invoice.notes.contains(query, ignoreCase = true))
    }
    val permissions = user?.let(PermissionManager::permissionsOf)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل الفواتير والمردودات (${invoices.size})", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InvoiceListBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.InvoiceEditor.createRoute(InvoiceType.SALE.name)) },
                containerColor = ActionOrange,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "فاتورة جديدة")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    try {
                        if (companyId.isNotBlank()) {
                            mainViewModel.container.syncEngine.syncPendingNow(companyId)
                        }
                        invoicesFlow.first()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mainViewModel.showError(e, "تعذر تحديث قائمة الفواتير")
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { navController.navigate(Screen.Pos.route) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("فتح نقطة البيع السريعة (POS)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("بحث برقم الفاتورة أو الموظف أو الملاحظات") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == null,
                            onClick = { selectedType = null },
                            label = { Text("الكل") }
                        )
                        InvoiceType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = if (selectedType == type) null else type },
                                label = { Text(type.arabicTitle) }
                            )
                        }
                        FilterChip(
                            selected = unpostedOnly,
                            onClick = { unpostedOnly = !unpostedOnly },
                            label = { Text("غير مرحّلة") },
                            leadingIcon = if (unpostedOnly) {
                                { Icon(Icons.Default.LockOpen, contentDescription = null) }
                            } else null
                        )
                        FilterChip(
                            selected = unpaidOnly,
                            onClick = { unpaidOnly = !unpaidOnly },
                            label = { Text("غير مسددة") },
                            leadingIcon = if (unpaidOnly) {
                                { Icon(Icons.Default.Payments, contentDescription = null) }
                            } else null
                        )
                    }
                    val outstanding = visibleInvoices.sumOf {
                        (it.netTotal - it.paidAmount).coerceAtLeast(0.0)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي المتبقي للنتائج", color = Color(0xFF00695C), fontWeight = FontWeight.SemiBold)
                            Text(
                                CurrencyFormatter.format(outstanding),
                                color = Color(0xFF00695C),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        "عرض ${visibleInvoices.size} من ${invoices.size} فاتورة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (visibleInvoices.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = if (invoices.isEmpty()) "لا توجد فواتير بعد" else "لا توجد نتائج مطابقة",
                        subtitle = if (invoices.isEmpty()) "ابدأ بإضافة أول فاتورة إلى السجل." else "غيّر البحث أو المرشحات لعرض الفواتير.",
                        actionLabel = if (invoices.isEmpty()) "أضف أول فاتورة" else "مسح المرشحات",
                        onAction = {
                            if (invoices.isEmpty()) {
                                navController.navigate(Screen.InvoiceEditor.createRoute(InvoiceType.SALE.name))
                            } else {
                                searchText = ""
                                selectedType = null
                                unpostedOnly = false
                                unpaidOnly = false
                            }
                        }
                    )
                }
            }
            items(visibleInvoices, key = { it.id }) { inv ->
                val accent = when (inv.type) {
                    InvoiceType.SALE, InvoiceType.FREE_SALE -> Color(0xFF00897B)
                    InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> Color(0xFF1565C0)
                    InvoiceType.SALE_RETURN, InvoiceType.PURCHASE_RETURN -> Color(0xFFE65100)
                    InvoiceType.QUOTATION -> Color(0xFF7B1FA2)
                    InvoiceType.ORDER -> Color(0xFF3949AB)
                }
                val tint = when (inv.type) {
                    InvoiceType.SALE, InvoiceType.FREE_SALE -> Color(0xFFE0F2F1)
                    InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> Color(0xFFE3F2FD)
                    InvoiceType.SALE_RETURN, InvoiceType.PURCHASE_RETURN -> Color(0xFFFFF3E0)
                    InvoiceType.QUOTATION -> Color(0xFFF3E5F5)
                    InvoiceType.ORDER -> Color(0xFFE8EAF6)
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Screen.InvoiceDetails.createRoute(inv.id)) },
                    shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = tint)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (inv.type) {
                                    InvoiceType.SALE, InvoiceType.FREE_SALE -> Icons.Default.PointOfSale
                                    InvoiceType.PURCHASE, InvoiceType.FREE_PURCHASE -> Icons.Default.LocalShipping
                                    InvoiceType.SALE_RETURN, InvoiceType.PURCHASE_RETURN -> Icons.Default.Replay
                                    InvoiceType.QUOTATION -> Icons.Default.RequestQuote
                                    InvoiceType.ORDER -> Icons.Default.ShoppingCartCheckout
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${inv.type.arabicTitle} (${inv.displayNumber.ifBlank { "#${inv.invoiceNumber}" }})${if (inv.isPosted) " [مرحّلة]" else ""}${if (inv.isArchived) " [مؤرشفة]" else ""}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "الإجمالي: ${CurrencyFormatter.format(inv.netTotal)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DebitRed
                            )
                            Text(
                                "${DateUtils.formatDate(inv.dateMillis)} • ${inv.createdByUserName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row {
                            IconButton(
                                enabled = user?.let { PermissionManager.permissionsOf(it).canExportAndShare } == true,
                                onClick = {
                                    val company = session.activeCompany ?: return@IconButton
                                    scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) {
                                                val items = mainViewModel.container.db.invoiceDao().getInvoiceItems(inv.id)
                                                val account = mainViewModel.container.db.accountDao().getAccountById(inv.accountId)
                                                val currency = mainViewModel.container.db.financeDao().getCurrencyById(inv.currencyId)
                                                mainViewModel.container.pdfGenerator.generateInvoicePdf(
                                                    company = company,
                                                    invoice = inv,
                                                    items = items,
                                                    account = account,
                                                    currency = currency,
                                                    settings = session.settings
                                                )
                                            }
                                            MessagingShareService.openPdf(context, file)
                                        } catch (e: Exception) {
                                            mainViewModel.showError(e, "تعذر إنشاء أو عرض ملف الفاتورة")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "عرض الفاتورة PDF", tint = ActionOrange)
                            }
                            IconButton(
                                enabled = user?.let { PermissionManager.permissionsOf(it).canExportAndShare } == true,
                                onClick = {
                                    val company = session.activeCompany ?: return@IconButton
                                    scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) {
                                                val items = mainViewModel.container.db.invoiceDao().getInvoiceItems(inv.id)
                                                val account = mainViewModel.container.db.accountDao().getAccountById(inv.accountId)
                                                val currency = mainViewModel.container.db.financeDao().getCurrencyById(inv.currencyId)
                                                mainViewModel.container.pdfGenerator.generateInvoicePdf(
                                                    company = company,
                                                    invoice = inv,
                                                    items = items,
                                                    account = account,
                                                    currency = currency,
                                                    settings = session.settings
                                                )
                                            }
                                            MessagingShareService.sharePdfToWhatsApp(context, file)
                                        } catch (e: Exception) {
                                            mainViewModel.showError(e, "تعذر إنشاء أو مشاركة ملف الفاتورة")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة الفاتورة PDF")
                            }
                            if (permissions?.canPostOrArchiveInvoice == true) IconButton(onClick = {
                                val currentUser = user ?: return@IconButton
                                scope.launch {
                                    try {
                                        mainViewModel.container.invoiceRepository.togglePostInvoice(currentUser, inv)
                                        mainViewModel.showMessage(if (inv.isPosted) "تم إلغاء الترحيل" else "تم ترحيل وقفل الفاتورة")
                                    } catch (e: Exception) { mainViewModel.showError(e, "تعذر ترحيل الفاتورة") }
                                }
                            }) {
                                Icon(if (inv.isPosted) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "ترحيل")
                            }
                            if (permissions?.canPostOrArchiveInvoice == true) IconButton(onClick = {
                                if (inv.isArchived) {
                                    val currentUser = user ?: return@IconButton
                                    scope.launch {
                                        try {
                                            mainViewModel.container.invoiceRepository.toggleArchiveInvoice(currentUser, inv)
                                            mainViewModel.showMessage("تم إلغاء أرشفة الفاتورة")
                                        } catch (e: Exception) {
                                            mainViewModel.showError(e, "تعذر إلغاء أرشفة الفاتورة")
                                        }
                                    }
                                } else invoiceToArchive = inv
                            }) {
                                Icon(if (inv.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = "أرشفة")
                            }
                        }
                    }

                    }
                    }
                }

                invoiceToArchive?.let { invoice ->
                    AlertDialog(
                        onDismissRequest = { invoiceToArchive = null },
                        title = { Text("تأكيد أرشفة الفاتورة") },
                        text = { Text("ستُستبعد الفاتورة من القوائم والتقارير حتى إلغاء أرشفتها.") },
                        confirmButton = {
                            TextButton(onClick = {
                                val currentUser = user
                                if (currentUser == null) {
                                    invoiceToArchive = null
                                    return@TextButton
                                }
                                scope.launch {
                                    try {
                                        mainViewModel.container.invoiceRepository.toggleArchiveInvoice(currentUser, invoice)
                                        mainViewModel.showMessage("تمت أرشفة الفاتورة")
                                    } catch (e: Exception) {
                                        mainViewModel.showError(e, "تعذر أرشفة الفاتورة")
                                    } finally {
                                        invoiceToArchive = null
                                    }
                                }
                            }) { Text("أرشفة", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { invoiceToArchive = null }) { Text("إلغاء") } }
                    )
                }
        }
    }
}
