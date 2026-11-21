package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.ProductEntity
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.ui.navigation.Screen
import com.smartaccountant.pro.ui.viewmodel.GlobalSearchResults
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    mainViewModel: MainViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(GlobalSearchResults()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = GlobalSearchResults()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350) // debounce
        results = mainViewModel.searchGlobal(q)
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("البحث الشامل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("ابحث بالاسم، الرقم، الهاتف، الباركود...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            when {
                query.trim().length < 2 -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "اكتب حرفين على الأقل للبحث في الحسابات والسندات والفواتير والأصناف",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                searching -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                results.isEmpty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("لا توجد نتائج مطابقة")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (results.accounts.isNotEmpty()) {
                            item { SearchSectionHeader("الحسابات والأطراف", results.accounts.size) }
                            items(results.accounts, key = { "acc_${it.id}" }) { account ->
                                SearchResultRow(
                                    icon = Icons.Default.Person,
                                    title = account.name,
                                    subtitle = account.phone.ifBlank { account.category.arabicTitle },
                                    onClick = {
                                        navController.navigate(Screen.PartyProfile.createRoute(account.id))
                                    }
                                )
                            }
                        }
                        if (results.invoices.isNotEmpty()) {
                            item { SearchSectionHeader("الفواتير", results.invoices.size) }
                            items(results.invoices, key = { "inv_${it.id}" }) { invoice ->
                                SearchResultRow(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    title = "${invoice.type.arabicTitle} — ${invoice.displayNumber.ifBlank { invoice.invoiceNumber.toString() }}",
                                    subtitle = "الإجمالي: ${invoice.netTotal}",
                                    onClick = {
                                        navController.navigate(Screen.InvoiceDetails.createRoute(invoice.id))
                                    }
                                )
                            }
                        }
                        if (results.vouchers.isNotEmpty()) {
                            item { SearchSectionHeader("السندات والقيود", results.vouchers.size) }
                            items(results.vouchers, key = { "vch_${it.id}" }) { voucher ->
                                SearchResultRow(
                                    icon = Icons.Default.Description,
                                    title = "${voucher.type.arabicTitle} — ${voucher.displayNumber.ifBlank { voucher.voucherNumber.toString() }}",
                                    subtitle = voucher.description.ifBlank { "المبلغ: ${voucher.amount}" },
                                    onClick = {
                                        navController.navigate(Screen.VoucherDetails.createRoute(voucher.id))
                                    }
                                )
                            }
                        }
                        if (results.products.isNotEmpty()) {
                            item { SearchSectionHeader("الأصناف", results.products.size) }
                            items(results.products, key = { "prd_${it.id}" }) { product ->
                                SearchResultRow(
                                    icon = Icons.Default.Inventory2,
                                    title = product.name,
                                    subtitle = product.barcode.ifBlank { product.categoryName },
                                    onClick = { navController.navigate(Screen.Inventory.route) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, count: Int) {
    Text(
        "$title ($count)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
