package com.smartaccountant.pro.ui.screens

import android.content.Intent
import android.net.Uri
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.data.model.AuditAction
import com.smartaccountant.pro.data.model.AuditLogEntity
import com.smartaccountant.pro.data.model.VoucherEntity
import com.smartaccountant.pro.data.model.VoucherLineEntity
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.services.MessagingShareService
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherDetailsScreen(
    voucherId: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""

    var voucher by remember { mutableStateOf<VoucherEntity?>(null) }
    var voucherLines by remember { mutableStateOf<List<VoucherLineEntity>>(emptyList()) }
    var accountsMap by remember { mutableStateOf<Map<String, AccountEntity>>(emptyMap()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(voucherId, companyId) {
        isLoading = true
        voucher = mainViewModel.container.db.voucherDao().getVoucherInCompany(companyId, voucherId)
        if (voucher != null) {
            voucherLines = mainViewModel.container.db.voucherDao().getVoucherLines(voucherId)

            val accountIds = (voucherLines.map { it.accountId } + listOfNotNull(voucher?.accountId, voucher?.counterAccountId)).toSet()
            val accountsList = mainViewModel.container.db.accountDao().getAllActiveAccounts(companyId)
            accountsMap = accountsList.filter { it.id in accountIds }.associateBy { it.id }
        }
        isLoading = false
    }

    val v = voucher
    val currency = session.currencies.find { it.id == v?.currencyId } ?: session.currencies.firstOrNull()

    fun generateVoucherPdfFile(): File {
        val activeCompany = session.activeCompany ?: throw IllegalStateException("لا توجد شركة نشطة")
        val currentVoucher = v ?: throw IllegalStateException("السند غير موجود")
        val partyAccount = accountsMap[currentVoucher.accountId]
        val counterAcc = accountsMap[currentVoucher.counterAccountId]
        val cashBox = session.cashBoxes.find { it.id == currentVoucher.cashBoxId }

        return mainViewModel.container.pdfGenerator.generateVoucherPdf(
            company = activeCompany,
            voucher = currentVoucher,
            account = partyAccount,
            counterAccount = counterAcc,
            cashBoxName = cashBox?.name,
            currency = currency,
            settings = session.settings
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (v != null) "مستند ${v.type.arabicTitle}" else "تفاصيل المستند", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = {
                    v?.let { currentVoucher ->
                        // 1. WhatsApp Share
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val pdfFile = generateVoucherPdfFile()
                                    val partyAcc = accountsMap[currentVoucher.accountId]
                                    val msg = "مستند ${currentVoucher.type.arabicTitle} رقم: ${currentVoucher.displayNumber}\nالمبلغ: ${CurrencyFormatter.formatAmount(currentVoucher.amount)} ${currency?.symbol ?: ""}\nالتاريخ: ${DateUtils.formatDate(currentVoucher.dateMillis)}"
                                    MessagingShareService.sendWhatsAppMessage(context, partyAcc?.phone.orEmpty(), msg)
                                    MessagingShareService.openPdf(context, pdfFile)
                                } catch (e: Exception) {
                                    mainViewModel.showError(e, "فشل المشاركة عبر واتساب")
                                }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "مشاركة واتساب", tint = CreditGreen)
                        }

                        // 2. Export PDF
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val pdfFile = generateVoucherPdfFile()
                                    MessagingShareService.openPdf(context, pdfFile)
                                } catch (e: Exception) {
                                    mainViewModel.showError(e, "فشل تصدير مستند PDF")
                                }
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                        }

                        // 3. Edit & Delete if manually deletable
                        if (com.smartaccountant.pro.domain.repository.AccountingRepository.isManuallyDeletable(currentVoucher)) {
                            IconButton(onClick = { onEdit(currentVoucher.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل المستند")
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف المستند", tint = DebitRed)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (v == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("المستند غير موجود أو تم حذفه", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        val headerColor = when (v.type) {
            VoucherType.RECEIPT -> Color(0xFF1B5E20)
            VoucherType.PAYMENT -> Color(0xFFB71C1C)
            else -> Color(0xFF1B3A6B)
        }

        val partyAccount = accountsMap[v.accountId]
        val cashBox = session.cashBoxes.find { it.id == v.cashBoxId }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== ترويسة المستند الرسمي (Official Document Banner) =====
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
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = v.type.arabicTitle,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = v.displayNumber.ifBlank { "#${v.voucherNumber}" },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text("التاريخ الرسمي:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                Text(DateUtils.formatDate(v.dateMillis), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("المبلغ الإجمالي:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                Text(
                                    "${CurrencyFormatter.formatAmount(v.amount)} ${currency?.symbol ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 22.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = ArabicTafqeet.convert(v.amount, currency?.name ?: "ريال", currency?.subUnitName ?: "فلس"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
            }

            // ===== تفاصيل أطراف المستند =====
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (v.type == VoucherType.RECEIPT || v.type == VoucherType.PAYMENT) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (v.type == VoucherType.RECEIPT) "المستلم منه (الحساب):" else "المصروف له (الحساب):",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = partyAccount?.name ?: "غير محدد",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (v.type == VoucherType.RECEIPT) "الصندوق / البنك المستلم:" else "الصندوق / البنك المصروف منه:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = cashBox?.name ?: "الصندوق الرئيسي",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (v.description.isNotBlank()) {
                        HorizontalDivider()
                        Text("البيان والملاحظات:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ===== جدول أسطر القيد (إن وجد) =====
            if (voucherLines.isNotEmpty()) {
                Text("سطور وتفاصيل القيود:", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الحساب", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("مدين (+)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("دائن (-)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        HorizontalDivider()

                        voucherLines.forEach { line ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(2f)) {
                                        Text(
                                            text = accountsMap[line.accountId]?.name ?: "حساب محذوف",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        if (line.note.isNotBlank()) {
                                            Text(line.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        text = if (line.debit > 0) CurrencyFormatter.formatAmount(line.debit) else "-",
                                        modifier = Modifier.weight(1f),
                                        color = if (line.debit > 0) DebitRed else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (line.credit > 0) CurrencyFormatter.formatAmount(line.credit) else "-",
                                        modifier = Modifier.weight(1f),
                                        color = if (line.credit > 0) CreditGreen else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الإجمالي", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                CurrencyFormatter.formatAmount(voucherLines.sumOf { it.debit }),
                                modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DebitRed
                            )
                            Text(
                                CurrencyFormatter.formatAmount(voucherLines.sumOf { it.credit }),
                                modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CreditGreen
                            )
                        }
                    }
                }
            }

            // ===== المرفقات (إن وجدت) =====
            if (v.attachmentUrl.isNotBlank()) {
                Text("المرفقات والصور:", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val url = v.attachmentUrl
                    val isRemoteOrBase64 = url.startsWith("http") || url.startsWith("data:")
                    val localFileExists = !isRemoteOrBase64 && File(url).exists()

                    if (isRemoteOrBase64 || localFileExists) {
                        AsyncImage(
                            model = if (isRemoteOrBase64) url else File(url),
                            contentDescription = "صورة المرفق",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW)
                                        val uri = if (isRemoteOrBase64) Uri.parse(url)
                                        else androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", File(url)
                                        )
                                        intent.setDataAndType(uri, "image/*")
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        mainViewModel.showMessage("لا يوجد تطبيق لفتح الصورة")
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("جاري مزامنة صورة المرفق من السحابة...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ===== معلومات التدقيق والإدخال =====
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("سجل التدقيق والإدخال:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("المستخدم المنشئ: ${v.createdByUserName.ifBlank { "المحاسب" }}", fontSize = 12.sp)
                    Text("اسم الجهاز: ${v.deviceId}", fontSize = 12.sp)
                    Text("تاريخ الإنشاء: ${DateUtils.formatDateTime(v.createdAt)}", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DebitRed) },
                title = { Text("تأكيد حذف المستند") },
                text = { Text("هل أنت متأكد من حذف ${v.type.arabicTitle} رقم ${v.displayNumber} نهائياً؟") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            try {
                                val currentUser = session.currentUser
                                if (currentUser != null) {
                                    mainViewModel.container.accountingRepository.deleteVoucher(currentUser, v)
                                    mainViewModel.showMessage("تم حذف ${v.type.arabicTitle} بنجاح")
                                    onBack()
                                }
                            } catch (e: Exception) {
                                mainViewModel.showError(e, "فشل حذف المستند")
                            }
                        }
                    }) {
                        Text("تأكيد الحذف", color = DebitRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") }
                }
            )
        }
    }
}
