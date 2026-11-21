package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.model.CostCenterEntity
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostCentersScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    val costCenters by mainViewModel.container.db.costCenterDao().observeCostCenters(companyId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCenter by remember { mutableStateOf<CostCenterEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مراكز التكلفة (المشاريع)") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCenter = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مركز تكلفة")
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canManageSettings }) { // يمكن تخصيص الصلاحية لاحقاً
            if (!session.settings.enableCostCenters) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BusinessCenter, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("ميزة مراكز التكلفة معطلة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("يمكنك تفعيلها من شاشة الإعدادات > خيارات النظام", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@PermissionGuard
            }

            if (costCenters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("لا توجد مراكز تكلفة حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(costCenters) { center ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().clickable {
                                editingCenter = center
                                showAddDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(Icons.Default.BusinessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(center.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (center.code.isNotBlank()) {
                                        Text("كود: ${center.code}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CostCenterDialog(
            center = editingCenter,
            onDismiss = { showAddDialog = false },
            onSave = { name, code ->
                scope.launch {
                    val toSave = editingCenter?.copy(
                        name = name.trim(),
                        code = code.trim()
                    ) ?: CostCenterEntity(
                        companyId = companyId,
                        name = name.trim(),
                        code = code.trim()
                    )
                    mainViewModel.container.db.costCenterDao().upsertCostCenter(toSave)
                    showAddDialog = false
                    mainViewModel.showMessage("تم حفظ مركز التكلفة بنجاح")
                }
            }
        )
    }
}

@Composable
fun CostCenterDialog(
    center: CostCenterEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(center?.name ?: "") }
    var code by remember { mutableStateOf(center?.code ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (center == null) "إضافة مركز تكلفة (مشروع)" else "تعديل مركز تكلفة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المشروع / مركز التكلفة *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("رمز المركز (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, code) },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
