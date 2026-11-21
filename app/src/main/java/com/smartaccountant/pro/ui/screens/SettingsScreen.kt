package com.smartaccountant.pro.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.PaperSize
import com.smartaccountant.pro.data.model.SyncStatus
import com.smartaccountant.pro.data.model.ThemeMode
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.WarningAmber
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(mainViewModel: MainViewModel, onBack: () -> Unit, onManageRecurring: () -> Unit = {}) {
    val session by mainViewModel.session.collectAsState()
    val syncState by mainViewModel.syncState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val company = session.activeCompany
    var st by remember(session.settings) { mutableStateOf(session.settings) }

    var companyName by remember(company) { mutableStateOf(company?.nameAr ?: "") }
    var companyPhone by remember(company) { mutableStateOf(company?.phone ?: "") }
    var companyAddress by remember(company) { mutableStateOf(company?.addressAr ?: "") }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val db = mainViewModel.container.db
                        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
                        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            dbFile.inputStream().use { input -> input.copyTo(out) }
                        } != null
                    } catch (e: Exception) { false }
                }
                mainViewModel.showMessage(if (success) "تم إنشاء النسخة الاحتياطية بنجاح" else "فشل إنشاء النسخة الاحتياطية")
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        PermissionGuard(user = session.currentUser, permissionCheck = { it.canManageSettings }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 1) هوية الشركة والفرع =====
                SettingsCard(title = "هوية الشركة والفرع", icon = Icons.Default.Storefront) {
                    company?.let { comp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("كود الشركة السحابي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(comp.id, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(comp.id))
                                    mainViewModel.showMessage("تم نسخ كود الشركة")
                                }) { Icon(Icons.Default.ContentCopy, contentDescription = "نسخ") }
                                IconButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "كود شركة ${comp.nameAr}: ${comp.id}")
                                    }
                                    context.startActivity(Intent.createChooser(send, "مشاركة كود الشركة"))
                                }) { Icon(Icons.Default.Share, contentDescription = "مشاركة") }
                            }
                        }

                        OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("اسم المتجر / الشركة *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = companyPhone, onValueChange = { companyPhone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = companyAddress, onValueChange = { companyAddress = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(
                            onClick = {
                                if (companyName.isBlank()) {
                                    mainViewModel.showMessage("اسم الشركة مطلوب")
                                    return@Button
                                }
                                scope.launch {
                                    mainViewModel.updateCompanyIdentity(
                                        company = comp,
                                        nameAr = companyName,
                                        phone = companyPhone,
                                        addressAr = companyAddress
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("حفظ بيانات الشركة") }
                    } ?: Text("لا توجد شركة نشطة", color = MaterialTheme.colorScheme.error)
                }

                // ===== 2) حالة المزامنة والجلسة =====
                SettingsCard(title = "حالة المزامنة والجلسة", icon = Icons.Default.CloudSync) {
                    val user = session.currentUser
                    InfoRow("المستخدم الحالي", user?.fullName ?: "—")
                    InfoRow("البريد / المعرف", user?.email?.ifBlank { user.id } ?: "—")
                    InfoRow("الدور", user?.role?.arabicTitle ?: "—")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    val (syncLabel, syncColor) = when {
                        syncState.isSyncing -> "جاري المزامنة..." to WarningAmber
                        syncState.isOnlineCloudActive -> "متصل بالسحابة" to CreditGreen
                        else -> "وضع محلي (بدون سحابة)" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    InfoRow("حالة الاتصال", syncLabel, valueColor = syncColor)
                    InfoRow("رسائل بانتظار المزامنة", "${syncState.pendingCount}")
                    if (syncState.conflictCount > 0) {
                        InfoRow("تعارضات تحتاج مراجعة", "${syncState.conflictCount}", valueColor = DebitRed)
                    }
                    syncState.lastSyncTimeMillis?.let {
                        InfoRow("آخر مزامنة ناجحة", DateUtils.formatDate(it))
                    }
                    Text(syncState.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Button(
                        onClick = {
                            company?.let { comp ->
                                mainViewModel.triggerManualSync()
                                mainViewModel.showMessage("تم طلب المزامنة اليدوية")
                            }
                        },
                        enabled = !syncState.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (syncState.isSyncing) "جارٍ المزامنة..." else "مزامنة يدوية شاملة الآن")
                    }
                }

                // ===== 2ب) مظهر التطبيق =====
                SettingsCard(title = "مظهر التطبيق", icon = Icons.Default.Brightness6) {
                    Text("وضع الألوان", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = st.themeMode == mode,
                                onClick = { st = st.copy(themeMode = mode) },
                                label = { Text(mode.arabicTitle) }
                            )
                        }
                    }
                }

                SettingsCard(title = "إشعارات العمليات", icon = Icons.Default.Notifications) {
                    val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
                    val channelEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                        notificationManager?.getNotificationChannel("realtime_operations")?.importance
                            ?.let { it != android.app.NotificationManager.IMPORTANCE_NONE } != false
                    val notificationsEnabled =
                        NotificationManagerCompat.from(context).areNotificationsEnabled() && channelEnabled
                    InfoRow(
                        "حالة إشعارات العمليات",
                        if (notificationsEnabled) "مفعّلة" else "متوقفة",
                        valueColor = if (notificationsEnabled) CreditGreen else DebitRed
                    )
                    Text(
                        "تظهر الإشعارات عند وصول عمليات جديدة من أجهزة الشركة الأخرى عبر Firebase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("فتح إعدادات إشعارات التطبيق")
                    }
                }

                // ===== 3) خيارات النظام المحاسبي =====
                SettingsCard(title = "خيارات النظام المحاسبي", icon = Icons.Default.Tune) {
                    SettingSwitch("تفعيل ضريبة القيمة المضافة في المبيعات", st.enableTax) { st = st.copy(enableTax = it) }
                    SettingSwitch("تفعيل الخصم في المبيعات", st.enableDiscount) { st = st.copy(enableDiscount = it) }
                    SettingSwitch("السماح بالبيع بسعر أقل من سعر الشراء", st.allowSellBelowCost) { st = st.copy(allowSellBelowCost = it) }
                    SettingSwitch("إنشاء سند قبض تلقائي عند ترحيل فاتورة البيع النقدية", st.autoReceiptForCashSales) { st = st.copy(autoReceiptForCashSales = it) }
                    SettingSwitch("إنشاء سند صرف تلقائي للمردود النقدي المرتبط", st.autoReverseSalesReturns) { st = st.copy(autoReverseSalesReturns = it) }
                    SettingSwitch("اعتماد المخزون عند البيع", st.enforceStockOnSale) { st = st.copy(enforceStockOnSale = it) }
                    SettingSwitch("استخدام الوحدات المخزنية المتعددة (3 وحدات)", st.enableMultiUnits) { st = st.copy(enableMultiUnits = it) }
                    SettingSwitch("تفعيل نظام التمتير والمقاسات", st.enableMeteringSystem) { st = st.copy(enableMeteringSystem = it) }
                    SettingSwitch("أقل من 100 سم يُحتسب متر كامل", st.minMeterUnder100CmCountsAs1Meter) { st = st.copy(minMeterUnder100CmCountsAs1Meter = it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    SettingSwitch("تفعيل نظام مراكز التكلفة", st.enableCostCenters) { st = st.copy(enableCostCenters = it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingSwitch("استخدام أزرار (له / عليه) بدلاً من زر الحفظ في السندات", st.useDebitCreditButtonsInsteadOfSave) { st = st.copy(useDebitCreditButtonsInsteadOfSave = it) }
                    SettingSwitch("إظهار الرصيد السابق في السند", st.showPreviousBalanceInVoucher) { st = st.copy(showPreviousBalanceInVoucher = it) }
                    SettingSwitch("إظهار الرصيد المتبقي في السند", st.showRemainingBalanceInVoucher) { st = st.copy(showRemainingBalanceInVoucher = it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingSwitch("قفل التطبيق عند الفتح", st.enableAppLock) { st = st.copy(enableAppLock = it) }
                    SettingSwitch("تفعيل قفل التطبيق بالبصمة", st.enableBiometric) { st = st.copy(enableBiometric = it) }
                }

                if (PermissionManager.permissionsOf(session.currentUser).canManageRecurring) {
                    SettingsCard(title = "المعاملات المجدولة", icon = Icons.Default.EventRepeat) {
                        Text(
                            "إدارة سندات القبض والصرف الدورية. تُنشأ الاستحقاقات عند فتح الشركة النشطة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onManageRecurring, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.EventRepeat, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إدارة المعاملات المتكررة")
                        }
                    }
                }

                // ===== 4) الطباعة والترويسات =====
                SettingsCard(title = "الطباعة والترويسات", icon = Icons.Default.Print) {
                    Text("مقاس الورق الافتراضي", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperSize.entries.forEach { size ->
                            FilterChip(selected = st.defaultPaperSize == size, onClick = { st = st.copy(defaultPaperSize = size) }, label = { Text(size.arabicTitle) })
                        }
                    }
                    OutlinedTextField(value = st.invoiceFooterNote, onValueChange = { st = st.copy(invoiceFooterNote = it) }, label = { Text("ترويسة/ملاحظة أسفل الفواتير") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = st.voucherFooterNote, onValueChange = { st = st.copy(voucherFooterNote = it) }, label = { Text("ترويسة/ملاحظة أسفل السندات") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = st.statementFooterNote, onValueChange = { st = st.copy(statementFooterNote = it) }, label = { Text("ترويسة/ملاحظة أسفل كشوفات الحساب") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = st.balanceConfirmationNote, onValueChange = { st = st.copy(balanceConfirmationNote = it) }, label = { Text("نص مصادقة الرصيد") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }

                // ===== 5) النسخ الاحتياطي والاستعادة =====
                SettingsCard(title = "النسخ الاحتياطي والاستعادة", icon = Icons.Default.Backup) {
                    Text(
                        "احفظ نسخة كاملة من قاعدة البيانات على جهازك أو سحابة الهاتف، واستعدها عند الحاجة. يُنصح بأخذ نسخة احتياطية قبل أي استعادة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { backupExportLauncher.launch("smart_accountant_backup_${System.currentTimeMillis()}.db") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("نسخ احتياطي")
                        }
                        OutlinedButton(
                            onClick = {
                                backupImportLauncher.launch(
                                    arrayOf("application/octet-stream", "application/x-sqlite3", "application/database")
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("استعادة")
                        }
                    }
                }

                Button(onClick = { mainViewModel.updateSettings(st) }, modifier = Modifier.fillMaxWidth()) {
                    Text("حفظ كافة الإعدادات")
                }

                // ===== 5) تسجيل الخروج =====
                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تسجيل الخروج وتبديل الشركة")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DebitRed) },
            title = { Text("تأكيد تسجيل الخروج") },
            text = { Text("سيتم تسجيل الخروج ومسح الجلسة والبيانات المحلية على هذا الجهاز، والعودة إلى شاشة الدخول. هل أنت متأكد؟") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        mainViewModel.logoutAndClearSession()
                    }
                }) { Text("تأكيد الخروج", color = DebitRed) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("إلغاء") } }
        )
    }

    pendingRestoreUri?.let { restoreUri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DebitRed) },
            title = { Text("تأكيد استعادة النسخة الاحتياطية") },
            text = { Text("سيتم استبدال جميع البيانات الحالية على هذا الجهاز بمحتوى النسخة الاحتياطية نهائياً. هل أنت متأكد؟") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            try {
                                AppDatabase.closeAndReset()
                                val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
                                context.contentResolver.openInputStream(restoreUri)?.use { input ->
                                    dbFile.outputStream().use { output -> input.copyTo(output) }
                                } ?: return@withContext false
                                File(dbFile.path + "-wal").delete()
                                File(dbFile.path + "-shm").delete()
                                true
                            } catch (e: Exception) { false }
                        }
                        if (success) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            launchIntent?.let { context.startActivity(it) }
                            android.os.Process.killProcess(android.os.Process.myPid())
                        } else {
                            mainViewModel.showMessage("فشلت الاستعادة — لم يتم تغيير البيانات")
                        }
                    }
                }) { Text("استعادة الآن", color = DebitRed) }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
