package com.smartaccountant.pro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.smartaccountant.pro.core.security.BiometricAuthHelper
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun AuthLoginScreen(
    activity: FragmentActivity,
    mainViewModel: MainViewModel
) {
    val session by mainViewModel.session.collectAsState()
    var identifier by remember { mutableStateOf(session.currentUser?.email ?: "") }
    var pinCode by remember { mutableStateOf("") }
    var showCreateCompany by remember { mutableStateOf(false) }
    var showManualLogin by remember { mutableStateOf(false) }
    var showInviteLogin by remember { mutableStateOf(false) }

    val companyId = session.activeCompany?.id ?: ""
    val companyUsers by mainViewModel.container.db.userSyncDao()
        .observeUsers(companyId)
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("المحاسب الذكي برو", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(session.activeCompany?.nameAr ?: "النظام المحاسبي السحابي المتكامل", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("البريد الإلكتروني أو رقم الجوال") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pinCode,
            onValueChange = { pinCode = it },
            label = { Text("رمز الدخول PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { mainViewModel.loginWithIdentifierAndPin(identifier, pinCode) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("تسجيل الدخول الآمن") }

        if (session.settings.enableBiometric && BiometricAuthHelper.isBiometricAvailable(activity)) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    BiometricAuthHelper.authenticate(
                        activity = activity,
                        onSuccess = { session.currentUser?.let { mainViewModel.unlockWithUser(it) } },
                        onError = { mainViewModel.showMessage(it) }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("الدخول بالبصمة الحيوية")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showCreateCompany = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("إنشاء شركة / متجر جديد") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showManualLogin = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("تسجيل دخول يدوي سحابي") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showInviteLogin = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("دخول سريع للموظفين (QR / كود الدعوة)") }

        if (companyUsers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Text("اختر حساب المستخدم لتعبئة البريد:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            companyUsers.filter { it.isActive }.forEach { user ->
                FilledTonalButton(
                    onClick = {
                        identifier = user.email
                        pinCode = "" // يطلب إدخال PIN دائماً — لا يكشف الرمز أبداً
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${user.fullName} — (${user.role.arabicTitle})")
                }
            }
        }

        if (showCreateCompany) {
            CreateCompanyDialog(mainViewModel = mainViewModel, onDismiss = { showCreateCompany = false })
        }
        if (showManualLogin) {
            ManualCloudLoginDialog(mainViewModel = mainViewModel, onDismiss = { showManualLogin = false })
        }
        if (showInviteLogin) {
            InviteCodeLoginDialog(mainViewModel = mainViewModel, onDismiss = { showInviteLogin = false })
        }

        val companySelectionList by mainViewModel.companySelectionList.collectAsState()
        if (companySelectionList.isNotEmpty()) {
            CompanySelectionDialog(
                companies = companySelectionList,
                onSelectCompany = { comp -> mainViewModel.selectCompanyAndLogin(comp) },
                onDismiss = { mainViewModel.dismissCompanySelection() }
            )
        }
    }
}

@Composable
private fun CompanySelectionDialog(
    companies: List<com.smartaccountant.pro.data.model.CompanySelectionInfo>,
    onSelectCompany: (com.smartaccountant.pro.data.model.CompanySelectionInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text("اختر الشركة للدخول", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("حسابك معتمد في عدة شركات، يرجى تحديد الشركة المراد متابعة العمل عليها:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                companies.forEach { comp ->
                    Card(
                        onClick = { onSelectCompany(comp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (comp.isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    imageVector = if (comp.isOwner) Icons.Default.AdminPanelSettings else Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = if (comp.isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(comp.companyName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("كود الشركة: ${comp.companyId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("الصفة: ${comp.roleTitle}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun InviteCodeLoginDialog(mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    var inviteJson by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دخول سريع للموظفين (QR / كود الدعوة)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                OutlinedTextField(
                    value = inviteJson,
                    onValueChange = { inviteJson = it },
                    label = { Text("الصق كود الدعوة JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showScanner = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("مسح QR بالكاميرا")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN الموظف") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val raw = inviteJson.trim()
                    if (raw.isBlank() || pin.isBlank()) {
                        mainViewModel.showMessage("يرجى لصق كود الدعوة وإدخال PIN")
                        return@TextButton
                    }
                    val invite = mainViewModel.container.companyBootstrapService.parseInviteJson(raw)
                    if (invite == null || invite.companyId.isBlank() || invite.memberId.isBlank()) {
                        mainViewModel.showMessage("كود الدعوة غير صالح")
                        return@TextButton
                    }
                    isLoading = true
                    scope.launch {
                        val user = mainViewModel.container.companyBootstrapService.joinWithInvite(raw, pin)
                        if (user == null) {
                            isLoading = false
                            mainViewModel.showMessage("فشل الدخول — تحقق من كود الدعوة/PIN")
                        } else {
                            val company = mainViewModel.container.db.companyDao().getById(user.companyId)
                            isLoading = false
                            if (company == null) {
                                mainViewModel.showMessage("تم سحب البيانات لكن تعذر العثور على الشركة")
                            } else {
                                mainViewModel.unlockWithUser(user)
                                mainViewModel.switchCompany(company)
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) { Text(if (isLoading) "جارٍ الدخول..." else "دخول") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )

    if (showScanner) {
        QrScannerDialog(
            onResult = { scanned ->
                inviteJson = scanned
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }
}

@Composable
private fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scanned by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مسح QR") },
        text = {
            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                cameraProvider = provider
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                val scanner = BarcodeScanning.getClient(
                                    BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                        .build()
                                )
                                val executor = Executors.newSingleThreadExecutor()
                                @androidx.camera.core.ExperimentalGetImage
                                analysis.setAnalyzer(executor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    try {
                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(inputImage)
                                            .addOnSuccessListener(ContextCompat.getMainExecutor(ctx)) { barcodes ->
                                                if (!scanned) {
                                                    barcodes.firstOrNull()?.rawValue?.let { value ->
                                                        scanned = true
                                                        onResult(value)
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } catch (_: Exception) {
                                        imageProxy.close()
                                    }
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            } catch (_: Exception) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            } else {
                Text("يلزم إذن الكاميرا لمسح QR")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun CreateCompanyDialog(mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    var nameAr by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var ownerEmail by remember { mutableStateOf("") }
    var ownerPin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء شركة / متجر جديد") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text("اسم الشركة") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("اسم المدير / المالك") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = ownerEmail, onValueChange = { ownerEmail = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ownerPin,
                    onValueChange = { ownerPin = it },
                    label = { Text("PIN المدير") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nameAr.isBlank() || ownerName.isBlank() || ownerEmail.isBlank() || ownerPin.isBlank()) {
                        mainViewModel.showMessage("يرجى ملء جميع الحقول")
                        return@TextButton
                    }
                    isLoading = true
                    scope.launch {
                        val result = mainViewModel.container.companyBootstrapService.createCompany(
                            nameAr = nameAr.trim(),
                            ownerName = ownerName.trim(),
                            ownerEmail = ownerEmail.trim(),
                            ownerPin = ownerPin
                        )
                        isLoading = false
                        if (result == null) {
                            mainViewModel.showMessage("تعذر إنشاء الشركة — تأكد من تفعيل السحابة")
                        } else {
                            val (company, owner) = result
                            mainViewModel.unlockWithUser(owner)
                            mainViewModel.switchCompany(company)
                            onDismiss()
                        }
                    }
                },
                enabled = !isLoading
            ) { Text(if (isLoading) "جارٍ الإنشاء..." else "إنشاء") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ManualCloudLoginDialog(mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    var companyId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل دخول يدوي سحابي") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                OutlinedTextField(value = companyId, onValueChange = { companyId = it }, label = { Text("كود الشركة") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (companyId.isBlank() || email.isBlank() || pin.isBlank()) {
                        mainViewModel.showMessage("يرجى ملء جميع الحقول")
                        return@TextButton
                    }
                    isLoading = true
                    scope.launch {
                        val user = mainViewModel.container.companyBootstrapService.manualCloudLogin(
                            companyId = companyId.trim(),
                            email = email.trim(),
                            pin = pin
                        )
                        if (user == null) {
                            isLoading = false
                            mainViewModel.showMessage("فشل الدخول — تحقق من كود الشركة/البريد/PIN")
                        } else {
                            val company = mainViewModel.container.db.companyDao().getById(user.companyId)
                            isLoading = false
                            if (company == null) {
                                mainViewModel.showMessage("تم سحب البيانات لكن تعذر العثور على الشركة")
                            } else {
                                mainViewModel.unlockWithUser(user)
                                mainViewModel.switchCompany(company)
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) { Text(if (isLoading) "جارٍ الدخول..." else "دخول") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
