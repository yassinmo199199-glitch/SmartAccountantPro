package com.smartaccountant.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.UserPermissions
import com.smartaccountant.pro.data.model.VoucherType
import com.smartaccountant.pro.ui.navigation.Screen
import com.smartaccountant.pro.ui.theme.ActionOrange
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.theme.InfoBlue
import com.smartaccountant.pro.ui.theme.SecondaryTeal
import com.smartaccountant.pro.ui.theme.TopBarDark
import com.smartaccountant.pro.ui.viewmodel.MainViewModel

private data class MoreItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
    val visible: (UserPermissions) -> Boolean = { true }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    mainViewModel: MainViewModel,
    onNavigateToAdmin: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGeneralJournal: () -> Unit,
    onNavigateToCostCenters: () -> Unit,
    onNavigateToGlobalSearch: () -> Unit
) {
    val session by mainViewModel.session.collectAsState()
    val user = session.currentUser
    val perms = user?.let(PermissionManager::permissionsOf) ?: UserPermissions()

    val quickActions = listOf(
        MoreItem("البحث الشامل", "ابحث في كل كيانات الشركة", Icons.Default.Search, Color(0xFF37474F),
            onNavigateToGlobalSearch)
    ).filter { it.visible(perms) }

    val sections = listOf(
        MoreItem("اليومية العامة", "دفتر يومية يعرض جميع الحركات والقيود", Icons.Default.MenuBook, Color(0xFF1565C0),
            onNavigateToGeneralJournal) { it.canViewFinancialReports }
    ).filter { it.visible(perms) }

    val admin = listOf(
        MoreItem("مراكز التكلفة", "المشاريع ومراكز التكلفة الفرعية", Icons.Default.BusinessCenter, Color(0xFFEF6C00),
            onNavigateToCostCenters) { it.canManageSettings && session.settings.enableCostCenters },
        MoreItem("المستخدمون والصلاحيات", "إدارة الموظفين والأدوار", Icons.Default.ManageAccounts, Color(0xFF6A1B9A),
            onNavigateToAdmin) { it.canManageUsers },
        MoreItem("سجل الرقابة", "سجل العمليات والتعديلات", Icons.Default.History, Color(0xFF546E7A),
            onNavigateToAudit) { it.canViewAuditLogs },
        MoreItem("الإعدادات", "إعدادات الشركة والمزامنة", Icons.Default.Settings, Color(0xFF455A64),
            onNavigateToSettings) { it.canManageSettings },
        MoreItem("تحديثات التطبيق (OTA)", "البحث عن إصدار جديد وتحديث التطبيق مباشرة", Icons.Default.SystemUpdate, Color(0xFF0288D1),
            { mainViewModel.checkForAppUpdate(manualCheck = true) })
    ).filter { it.visible(perms) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المزيد", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoreSectionCard(title = "عمليات سريعة", icon = Icons.Default.Bolt, accent = ActionOrange, items = quickActions)
            MoreSectionCard(title = "الأقسام", icon = Icons.Default.GridView, accent = SecondaryTeal, items = sections)
            if (admin.isNotEmpty()) {
                MoreSectionCard(title = "الإدارة", icon = Icons.Default.AdminPanelSettings, accent = DebitRed, items = admin)
            }
        }
    }
}

@Composable
private fun MoreSectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    items: List<MoreItem>
) {
    if (items.isEmpty()) return
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items.forEach { item ->
                MoreNavRow(item = item)
            }
        }
    }
}

@Composable
private fun MoreNavRow(item: MoreItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = item.onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = CircleShape, color = item.color.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
