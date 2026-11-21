package com.smartaccountant.pro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.data.model.AccountCategory
import com.smartaccountant.pro.data.model.AccountEntity
import com.smartaccountant.pro.ui.components.PermissionGuard
import com.smartaccountant.pro.ui.theme.CreditGreen
import com.smartaccountant.pro.ui.theme.DebitRed
import com.smartaccountant.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

// دالة مساعدة لبناء شجرة الحسابات
data class AccountNode(
    val account: AccountEntity,
    val children: List<AccountNode> = emptyList(),
    var isExpanded: Boolean = false,
    val balance: Double = 0.0
)

fun buildAccountTree(accounts: List<AccountEntity>, balancesMap: Map<String, Double>): List<AccountNode> {
    val accountsById = accounts.associateBy { it.id }
    val childrenMap = accounts.groupBy { it.parentId }

    fun createNode(account: AccountEntity): AccountNode {
        val children = childrenMap[account.id]?.map { createNode(it) } ?: emptyList()
        val directBalance = balancesMap[account.id] ?: 0.0
        val totalBalance = directBalance + children.sumOf { it.balance }
        return AccountNode(account, children, balance = totalBalance)
    }

    // الجذر هم الحسابات التي ليس لها أب
    return childrenMap[null]?.map { createNode(it) } ?: emptyList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToAccountStatement: (String) -> Unit = {},
    onNavigateToPartyProfile: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser

    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }

    // فلتر التصنيفات (للتصفح كشجرة عامة أو التركيز على العملاء والموردين)
    val tabs = listOf("شجرة الحسابات (الكل)", "العملاء", "الموردين")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // تحميل الحسابات والأرصدة
    LaunchedEffect(companyId) {
        launch {
            mainViewModel.container.db.accountDao().observeAccounts(companyId).collect { list ->
                accounts = list
            }
        }
        launch {
            balances = mainViewModel.container.accountingRepository.calculateAllAccountBalances(companyId)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دليل الحسابات") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة حساب جديد")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة حساب جديد")
            }
        }
    ) { padding ->
        PermissionGuard(user = user, permissionCheck = { it.canViewAccounts }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
            ) {
                // ===== شريط البحث =====
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("بحث برقم الحساب أو الاسم أو الهاتف...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // ===== التبويبات الحديثة =====
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]).padding(horizontal = 16.dp),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    val filteredAccounts = accounts.filter {
                        (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)) &&
                        when (page) {
                            1 -> it.category == AccountCategory.CUSTOMERS
                            2 -> it.category == AccountCategory.SUPPLIERS
                            else -> true
                        }
                    }

                    if (filteredAccounts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                Text("لا توجد حسابات مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        // إذا كان عرض الشجرة (الصفحة 0) ولا يوجد بحث، نعرض الشجرة الهرمية
                        if (page == 0 && searchQuery.isBlank()) {
                            val tree = buildAccountTree(filteredAccounts, balances)
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                items(tree) { node ->
                                    AccountTreeNodeUI(
                                        node = node,
                                        level = 0,
                                        onNodeClick = { clickedNode ->
                                            if (clickedNode.children.isEmpty()) {
                                                onNavigateToAccountStatement(clickedNode.account.id)
                                            } else {
                                                editingAccount = clickedNode.account
                                                showAddDialog = true
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            // عرض قائمة مسطحة مبسطة (بحث أو تبويب عملاء/موردين)
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                items(filteredAccounts) { acc ->
                                    val bal = balances[acc.id] ?: 0.0
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                            onNavigateToAccountStatement(acc.id)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Icon(
                                                    imageVector = if (acc.category == AccountCategory.CUSTOMERS) Icons.Default.Person else if (acc.category == AccountCategory.SUPPLIERS) Icons.Default.LocalShipping else Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(acc.category.arabicTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    CurrencyFormatter.formatAmount(kotlin.math.abs(bal)),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (bal > 0) DebitRed else if (bal < 0) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    if (bal > 0) "مدين" else if (bal < 0) "دائن" else "مصفر",
                                                    fontSize = 10.sp,
                                                    color = if (bal > 0) DebitRed else if (bal < 0) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
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
        AccountAddEditDialog(
            mainViewModel = mainViewModel,
            editingAccount = editingAccount,
            accountsList = accounts,
            onDismiss = {
                showAddDialog = false
                editingAccount = null
            }
        )
    }
}

@Composable
fun AccountTreeNodeUI(
    node: AccountNode,
    level: Int,
    onNodeClick: (AccountNode) -> Unit
) {
    var expanded by remember { mutableStateOf(node.isExpanded) }
    val rotation by animateFloatAsState(targetValue = if (expanded) -90f else 0f) // RTL (Left arrow points down)

    val paddingStart = (level * 16).dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = paddingStart, top = 4.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (level == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
                .clickable {
                    if (node.children.isNotEmpty()) {
                        expanded = !expanded
                    } else {
                        onNodeClick(node)
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.children.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(28.dp)) // نفس المساحة بدون أيقونة السهم
            }

            // أيقونة المجلد أو الملف
            Icon(
                if (node.children.isNotEmpty()) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (level == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(node.account.name, fontWeight = if (level == 0) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            }

            Text(
                CurrencyFormatter.formatAmount(kotlin.math.abs(node.balance)),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (node.balance > 0) DebitRed else if (node.balance < 0) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                node.children.forEach { childNode ->
                    AccountTreeNodeUI(node = childNode, level = level + 1, onNodeClick = onNodeClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAddEditDialog(
    mainViewModel: MainViewModel,
    editingAccount: AccountEntity?,
    accountsList: List<AccountEntity>,
    onDismiss: () -> Unit
) {
    val session by mainViewModel.session.collectAsState()
    val companyId = session.activeCompany?.id ?: ""
    val user = session.currentUser ?: return
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(editingAccount?.name ?: "") }
    var phone by remember { mutableStateOf(editingAccount?.phone ?: "") }
    var address by remember { mutableStateOf(editingAccount?.address ?: "") }
    var selectedCategory by remember { mutableStateOf(editingAccount?.category ?: AccountCategory.CUSTOMERS) }
    var selectedParentId by remember { mutableStateOf(editingAccount?.parentId) }
    var selectedCurrencyId by remember { mutableStateOf(editingAccount?.defaultCurrencyId ?: session.currencies.firstOrNull { it.isDefault }?.id ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingAccount == null) "إضافة حساب جديد" else "تعديل حساب") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم الحساب *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // اختيار التصنيف
                var catExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory.arabicTitle, onValueChange = {}, readOnly = true,
                        label = { Text("التصنيف *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        AccountCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.arabicTitle) },
                                onClick = { selectedCategory = cat; catExpanded = false }
                            )
                        }
                    }
                }

                // الحساب الرئيسي (الأب)
                var parentExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = parentExpanded, onExpandedChange = { parentExpanded = it }) {
                    val parentName = accountsList.find { it.id == selectedParentId }?.name ?: "حساب رئيسي (بدون أب)"
                    OutlinedTextField(
                        value = parentName, onValueChange = {}, readOnly = true,
                        label = { Text("يتفرع من (الحساب الأب)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("حساب رئيسي (بدون أب)", fontWeight = FontWeight.Bold) },
                            onClick = { selectedParentId = null; parentExpanded = false }
                        )
                        accountsList.filter { it.id != editingAccount?.id }.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = { selectedParentId = acc.id; parentExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // العملة الافتراضية
                var curExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = curExpanded, onExpandedChange = { curExpanded = it }) {
                    val curName = session.currencies.find { it.id == selectedCurrencyId }?.name ?: ""
                    OutlinedTextField(
                        value = curName, onValueChange = {}, readOnly = true,
                        label = { Text("العملة الافتراضية") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = curExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = curExpanded, onDismissRequest = { curExpanded = false }) {
                        session.currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${c.symbol})") },
                                onClick = { selectedCurrencyId = c.id; curExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) {
                    mainViewModel.showMessage("الاسم مطلوب")
                    return@Button
                }
                scope.launch {
                    try {
                        val finalAccount = editingAccount?.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            category = selectedCategory,
                            parentId = selectedParentId,
                            defaultCurrencyId = selectedCurrencyId
                        ) ?: AccountEntity(
                            companyId = companyId,
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            category = selectedCategory,
                            parentId = selectedParentId,
                            defaultCurrencyId = selectedCurrencyId,
                            allowMultiCurrency = true
                        )
                        mainViewModel.container.accountingRepository.saveAccount(user, finalAccount, isEdit = editingAccount != null)
                        mainViewModel.showMessage(if (editingAccount == null) "تم إضافة الحساب" else "تم تعديل الحساب")
                        onDismiss()
                    } catch (e: Exception) {
                        mainViewModel.showError(e, "فشل حفظ الحساب")
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
