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
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { log ->
            log.userName.contains(searchQuery, ignoreCase = true) ||
            log.summary.contains(searchQuery, ignoreCase = true) ||
            log.entityType.contains(searchQuery, ignoreCase = true) ||
            log.deviceName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل الرقابة وتدقيق العمليات (${filteredLogs.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        PermissionGuard(user = session.currentUser, permissionCheck = { it.canViewAuditLogs }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم المستخدم، العملية، الجهاز...") },
                    leadingIcon = { Icon(Icons.Default.ManageSearch, contentDescription = "بحث") },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.ManageSearch,
                                title = "لا توجد عمليات مطابقة",
                                subtitle = "لم يتم العثور على أي حركات مسجلة تطابق بحثك.",
                                actionLabel = "رجوع",
                                onAction = onBack
                            )
                        }
                    }
                    items(filteredLogs, key = { it.id }) { log ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${log.action.arabicTitle} • ${log.entityType}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(DateUtils.formatDateTime(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                Text("المستخدم: ${log.userName}  |  الجهاز: ${log.deviceName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
