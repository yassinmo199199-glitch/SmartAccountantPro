package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.components.EmptyState
import com.smartaccountant.pro.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(mainViewModel: MainViewModel, onBack: () -> Unit) {
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val logs by mainViewModel.container.adminRepository.observeAuditLogs(companyId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل الرقابة وتدقيق العمليات (${logs.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        PermissionGuard(user = session.currentUser, permissionCheck = { it.canViewAuditLogs }) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.ManageSearch,
                            title = "لا توجد عمليات مسجلة",
                            subtitle = "ستظهر هنا العمليات والتغييرات التي تُسجل في سجل التدقيق.",
                            actionLabel = "رجوع",
                            onAction = onBack
                        )
                    }
                }
                items(logs, key = { it.id }) { log ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${log.action.arabicTitle} - ${log.entityType}", fontWeight = FontWeight.Bold)
                                Text(DateUtils.formatDateTime(log.timestamp), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("المستخدم: ${log.userName} | الجهاز: ${log.deviceName}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
