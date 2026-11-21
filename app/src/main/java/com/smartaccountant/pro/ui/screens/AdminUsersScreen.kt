package com.smartaccountant.pro.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.smartaccountant.pro.data.model.UserEntity
import com.smartaccountant.pro.data.model.UserPermissions
import com.smartaccountant.pro.data.model.UserRole
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(mainViewModel: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val adminUser = session.currentUser

    val users by mainViewModel.container.adminRepository.observeUsers(companyId).collectAsState(initial = emptyList())
    var editingUser by remember { mutableStateOf<UserEntity?>(null) }
    var isNewUser by remember { mutableStateOf(false) }
    var inviteUser by remember { mutableStateOf<UserEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدير النظام: المستخدمون والصلاحيات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isNewUser = true
                    editingUser = UserEntity(companyId = companyId, fullName = "", email = "", phone = "", role = UserRole.CASHIER)
                },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("إضافة موظف جديد") }
            )
        }
    ) { padding ->
        PermissionGuard(user = adminUser, permissionCheck = { it.canManageUsers }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // زر بارز لإضافة موظف
                Button(
                    onClick = {
                        isNewUser = true
                        editingUser = UserEntity(companyId = companyId, fullName = "", email = "", phone = "", role = UserRole.CASHIER)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إضافة موظف جديد")
                }

                if (users.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "لا يوجد موظفون بعد — أضف أول موظف ليتمكن من الدخول إلى النظام.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                users.forEach { u ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (u.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        if (u.isActive) "نشط" else "موقوف",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        color = if (u.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    u.role.arabicTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "PIN: مشفّر (SHA-256)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                if (u.role != UserRole.OWNER) {
                                    Switch(
                                        checked = u.isActive,
                                        onCheckedChange = {
                                            adminUser?.let { admin ->
                                                scope.launch { mainViewModel.container.adminRepository.toggleUserActiveStatus(admin, u) }
                                            }
                                        }
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { inviteUser = u },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("بطاقة الدعوة")
                                }
                                OutlinedButton(
                                    onClick = { isNewUser = false; editingUser = u },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تعديل")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingUser?.let { target ->
        var fullName by remember { mutableStateOf(target.fullName) }
        var email by remember { mutableStateOf(target.email) }
        var phone by remember { mutableStateOf(target.phone) }
        var newPin by remember { mutableStateOf(if (isNewUser) "1234" else "") }
        var role by remember { mutableStateOf(target.role) }
        var perms by remember { mutableStateOf(target.permissions) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text(if (isNewUser) "إضافة مستخدم جديد" else "تعديل: ${target.fullName}") },
            text = {
                Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("الاسم الكامل *") })
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني *") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الجوال") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = newPin, onValueChange = { newPin = it },
                            label = { Text(if (isNewUser) "رمز PIN *" else "PIN جديد (اختياري)") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("قالب الدور الوظيفي:", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UserRole.entries.filter { it != UserRole.OWNER }.forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r; if (r != UserRole.CUSTOM) perms = UserPermissions.forRole(r) },
                                label = { Text(r.arabicTitle) }
                            )
                        }
                    }
                    HorizontalDivider()
                    Text("الصلاحيات المفصلة:", fontWeight = FontWeight.Bold)
                    Text("الحسابات والعملاء", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PermissionSwitchRow("عرض الحسابات والعملاء", perms.canViewAccounts) { perms = perms.copy(canViewAccounts = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إضافة حساب جديد", perms.canAddAccount) { perms = perms.copy(canAddAccount = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("تعديل بيانات الحسابات", perms.canEditAccount) { perms = perms.copy(canEditAccount = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("حذف الحسابات", perms.canDeleteAccount) { perms = perms.copy(canDeleteAccount = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("تحديد سقف المديونية", perms.canSetCreditLimit) { perms = perms.copy(canSetCreditLimit = it); role = UserRole.CUSTOM }
                    Text("السندات والحوالات", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PermissionSwitchRow("إضافة سندات قبض وصرف", perms.canAddVoucher) { perms = perms.copy(canAddVoucher = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("تعديل السندات", perms.canEditVoucher) { perms = perms.copy(canEditVoucher = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("حذف السندات", perms.canDeleteVoucher) { perms = perms.copy(canDeleteVoucher = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إدارة الحوالات المالية", perms.canManageRemittances) { perms = perms.copy(canManageRemittances = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("مصارفة العملات", perms.canExchangeCurrency) { perms = perms.copy(canExchangeCurrency = it); role = UserRole.CUSTOM }
                    Text("الفواتير والمبيعات", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PermissionSwitchRow("إنشاء فواتير البيع", perms.canCreateSaleInvoice) { perms = perms.copy(canCreateSaleInvoice = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إنشاء فواتير الشراء", perms.canCreatePurchaseInvoice) { perms = perms.copy(canCreatePurchaseInvoice = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إنشاء مردودات", perms.canCreateReturnInvoice) { perms = perms.copy(canCreateReturnInvoice = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("تعديل الفواتير غير المرحّلة", perms.canEditInvoice) { perms = perms.copy(canEditInvoice = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("حذف الفواتير", perms.canDeleteInvoice) { perms = perms.copy(canDeleteInvoice = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("أرشفة وترحيل الفواتير", perms.canPostOrArchiveInvoice) { perms = perms.copy(canPostOrArchiveInvoice = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("البيع بأقل من سعر الشراء", perms.canSellBelowCost) { perms = perms.copy(canSellBelowCost = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("منح خصومات", perms.canApplyDiscount) { perms = perms.copy(canApplyDiscount = it); role = UserRole.CUSTOM }
                    Text("المخازن والأصناف", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PermissionSwitchRow("عرض المخازن والأصناف", perms.canViewInventory) { perms = perms.copy(canViewInventory = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إضافة وتعديل الأصناف", perms.canAddEditProduct) { perms = perms.copy(canAddEditProduct = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("التحويل المخزني", perms.canTransferStock) { perms = perms.copy(canTransferStock = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إظهار سعر التكلفة والأرباح", perms.canViewCostAndProfit) { perms = perms.copy(canViewCostAndProfit = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("عرض تقرير ربحية الأصناف", perms.canViewProfitability) { perms = perms.copy(canViewProfitability = it); role = UserRole.CUSTOM }
                    Text("الصناديق والتقارير", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PermissionSwitchRow("الوصول لجميع الصناديق", perms.canViewAllCashBoxes) { perms = perms.copy(canViewAllCashBoxes = it); role = UserRole.CUSTOM }
                    if (!perms.canViewAllCashBoxes) {
                        PermissionSwitchRow("تقييد الوصول بصناديق محددة", perms.restrictCashBoxAccess) {
                            perms = perms.copy(restrictCashBoxAccess = it)
                            role = UserRole.CUSTOM
                        }
                        if (perms.restrictCashBoxAccess) {
                            session.cashBoxes.forEach { cashBox ->
                                PermissionSwitchRow(cashBox.name, cashBox.id in perms.allowedCashBoxIds) { allowed ->
                                    val ids = if (allowed) perms.allowedCashBoxIds + cashBox.id
                                    else perms.allowedCashBoxIds - cashBox.id
                                    perms = perms.copy(allowedCashBoxIds = ids.distinct())
                                    role = UserRole.CUSTOM
                                }
                            }
                            if (session.cashBoxes.isEmpty()) {
                                Text(
                                    "لا توجد صناديق مضافة بعد. عند تفعيل التقييد لن يستطيع المستخدم استخدام أي صندوق حتى تحدد صندوقاً مسموحاً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    PermissionSwitchRow("تقييد الوصول إلى مخازن محددة", perms.restrictWarehouseAccess) {
                        perms = perms.copy(restrictWarehouseAccess = it)
                        role = UserRole.CUSTOM
                    }
                    if (perms.restrictWarehouseAccess) {
                        OutlinedTextField(
                            value = perms.allowedWarehouseIds.joinToString(", "),
                            onValueChange = { value ->
                                perms = perms.copy(
                                    allowedWarehouseIds = value.split(",")
                                        .map(String::trim)
                                        .filter(String::isNotBlank)
                                        .distinct()
                                )
                                role = UserRole.CUSTOM
                            },
                            label = { Text("معرّفات المخازن المسموح بها") },
                            supportingText = { Text("أدخل معرّفات المخازن مفصولة بفواصل؛ اتركها فارغة لمنع الوصول عند تفعيل التقييد.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PermissionSwitchRow("التحويل بين الصناديق", perms.canTransferBetweenBoxes) { perms = perms.copy(canTransferBetweenBoxes = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("عرض كشوفات الحساب", perms.canViewStatements) { perms = perms.copy(canViewStatements = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("عرض ملف العميل / المورد", perms.canViewPartyProfile) { perms = perms.copy(canViewPartyProfile = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إرسال تذكير دفع عبر واتساب", perms.canSendPaymentReminder) { perms = perms.copy(canSendPaymentReminder = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("عرض التقارير المالية", perms.canViewFinancialReports) { perms = perms.copy(canViewFinancialReports = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إدارة المعاملات المتكررة", perms.canManageRecurring) { perms = perms.copy(canManageRecurring = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("تصدير ومشاركة PDF/CSV", perms.canExportAndShare) { perms = perms.copy(canExportAndShare = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إغلاق الحسابات وتدوير الأرصدة", perms.canClosePeriod) { perms = perms.copy(canClosePeriod = it); role = UserRole.CUSTOM }
                    Text("إدارة النظام", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PermissionSwitchRow("تعديل إعدادات التطبيق", perms.canManageSettings) { perms = perms.copy(canManageSettings = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("إدارة المستخدمين وصلاحياتهم", perms.canManageUsers) { perms = perms.copy(canManageUsers = it); role = UserRole.CUSTOM }
                    PermissionSwitchRow("الاطلاع على سجل الرقابة", perms.canViewAuditLogs) { perms = perms.copy(canViewAuditLogs = it); role = UserRole.CUSTOM }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val admin = adminUser ?: return@Button
                    if (fullName.isBlank() || email.isBlank() || (isNewUser && newPin.isBlank())) {
                        mainViewModel.showMessage("يرجى إدخال الاسم والبريد ورمز PIN")
                        return@Button
                    }
                    scope.launch {
                        try {
                            mainViewModel.container.adminRepository.saveUserAndPermissions(
                                adminUser = admin,
                                targetUser = target.copy(fullName = fullName.trim(), email = email.trim(), phone = phone.trim(), role = role, permissions = perms),
                                rawNewPin = newPin.ifBlank { null },
                                isEdit = !isNewUser
                            )
                            editingUser = null
                            mainViewModel.showMessage("تم حفظ بيانات وصلاحيات المستخدم بنجاح")
                        } catch (e: Exception) { mainViewModel.showError(e, "تعذر حفظ المستخدم والصلاحيات") }
                    }
                }) { Text("حفظ ومزامنة") }
            },
            dismissButton = { TextButton(onClick = { editingUser = null }) { Text("إلغاء") } }
        )
    }

    inviteUser?.let { inviteTarget ->
        InviteCardDialog(
            mainViewModel = mainViewModel,
            user = inviteTarget,
            companyId = companyId,
            onDismiss = { inviteUser = null }
        )
    }
}

@Composable
private fun InviteCardDialog(
    mainViewModel: MainViewModel,
    user: UserEntity,
    companyId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val session by mainViewModel.session.collectAsState()
    val companyName = session.activeCompany?.nameAr ?: ""
    val memberId = user.firebaseUid.ifBlank { user.id }
    val inviteJson = remember(user.id, companyId, memberId, companyName) {
        mainViewModel.container.companyBootstrapService.buildInviteJson(
            companyId = companyId,
            email = user.email,
            memberId = memberId,
            userId = user.id,
            fullName = user.fullName,
            role = user.role.name,
            companyName = companyName
        )
    }
    val qrBitmap by produceState<Bitmap?>(initialValue = null, inviteJson) {
        value = withContext(Dispatchers.Default) { generateQrBitmap(inviteJson, 512) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بطاقة دعوة: ${user.fullName}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR دعوة",
                        modifier = Modifier.size(180.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = inviteJson,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("كود الدعوة JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(inviteJson)) }) { Text("نسخ") }
                TextButton(onClick = { shareInvite(context, inviteJson) }) { Text("واتساب") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    bitmap
} catch (_: Exception) {
    null
}

private fun shareInvite(context: Context, text: String) {
    val whatsapp = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
    }
    if (whatsapp.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(whatsapp, "مشاركة عبر واتساب"))
    } else {
        val generic = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(generic, "مشاركة كود الدعوة"))
    }
}

@Composable
private fun PermissionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
