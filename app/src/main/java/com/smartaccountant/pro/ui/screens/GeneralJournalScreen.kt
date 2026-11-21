package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.local.dao.StatementLineRecord
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralJournalScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToVoucher: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    var journalLines by remember { mutableStateOf<List<StatementLineRecord>>(emptyList()) }
    var accountsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var totalDebit by remember { mutableDoubleStateOf(0.0) }
    var totalCredit by remember { mutableDoubleStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(companyId) {
        isLoading = true
        // جلب جميع الحسابات لتعيين أسمائها
        val allAccounts = mainViewModel.container.db.accountDao().getAllActiveAccounts(companyId)
        accountsMap = allAccounts.associate { it.id to it.name }

        // استعلام لجلب جميع السطور المحاسبية مرتبة تاريخياً
        // يمكننا استخدام ReportsDao ونضيف دالة تجلب كل السطور
        val lines = mainViewModel.container.db.reportsDao().getAllJournalLines(companyId)
        journalLines = lines

        var tDebit = 0.0
        var tCredit = 0.0
        for (l in lines) {
            tDebit += l.debit * l.voucher.exchangeRate
            tCredit += l.credit * l.voucher.exchangeRate
        }
        totalDebit = tDebit
        totalCredit = tCredit
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر اليومية العامة") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                val activeCompany = session.activeCompany ?: return@launch
                                val pdfFile = mainViewModel.container.pdfGenerator.generateTabularReportPdf(
                                    company = activeCompany,
                                    reportTitle = "دفتر اليومية العامة",
                                    periodLabel = "تاريخ التصدير: ${DateUtils.formatDate(System.currentTimeMillis())}",
                                    headers = listOf("رقم القيد", "التاريخ", "الحساب والبيان", "مدين (+)", "دائن (-)"),
                                    rows = journalLines.map { l ->
                                        listOf(
                                            l.voucher.displayNumber,
                                            DateUtils.formatDate(l.voucher.dateMillis),
                                            (accountsMap[l.accountId] ?: "") + if (l.lineNote.isNotBlank()) " (${l.lineNote})" else "",
                                            if (l.debit > 0) CurrencyFormatter.formatAmount(l.debit) else "-",
                                            if (l.credit > 0) CurrencyFormatter.formatAmount(l.credit) else "-"
                                        )
                                    },
                                    footerRows = listOf(
                                        listOf("الإجمالي", "", "", CurrencyFormatter.formatAmount(totalDebit), CurrencyFormatter.formatAmount(totalCredit))
                                    ),
                                    settings = session.settings
                                )
                                MessagingShareService.openPdf(context, pdfFile)
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل تصدير دفتر اليومية")
                            }
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي المدين:", fontWeight = FontWeight.Bold)
                        Text(CurrencyFormatter.formatAmount(totalDebit), color = DebitRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي الدائن:", fontWeight = FontWeight.Bold)
                        Text(CurrencyFormatter.formatAmount(totalCredit), color = CreditGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewFinancialReports }) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@PermissionGuard
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // ترويسة الجدول
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("رقم القيد", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("البيان / الحساب", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("مدين", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("دائن", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(journalLines) { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToVoucher(line.voucher.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.voucher.displayNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(DateUtils.formatDate(line.voucher.dateMillis), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(modifier = Modifier.weight(2f)) {
                                Text(accountsMap[line.accountId] ?: "حساب محذوف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val desc = if (line.lineNote.isNotBlank()) line.lineNote else line.voucher.description
                                if (desc.isNotBlank()) {
                                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            Text(
                                text = if (line.debit > 0) CurrencyFormatter.formatAmount(line.debit) else "-",
                                modifier = Modifier.weight(1f),
                                color = if (line.debit > 0) DebitRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (line.credit > 0) CurrencyFormatter.formatAmount(line.credit) else "-",
                                modifier = Modifier.weight(1f),
                                color = if (line.credit > 0) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
