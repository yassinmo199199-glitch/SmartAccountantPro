package com.smartaccountant.pro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartaccountant.pro.ui.navigation.AppNavigation
import com.smartaccountant.pro.ui.screens.AuthLoginScreen
import com.smartaccountant.pro.ui.theme.SmartAccountantTheme
import com.smartaccountant.pro.ui.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {
    private val pendingVoucherId = mutableStateOf<String?>(null)
    private val openInvoiceListFromNotification = mutableStateOf(false)
    private val openAuditLogFromNotification = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        val appContainer = (application as SmartAccountantApp).container

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(appContainer))
                val session by mainViewModel.session.collectAsState()
                val darkTheme = when (session.settings.themeMode) {
                    com.smartaccountant.pro.data.model.ThemeMode.DARK -> true
                    com.smartaccountant.pro.data.model.ThemeMode.LIGHT -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                SmartAccountantTheme(darkTheme = darkTheme) {
                    val voucherId = pendingVoucherId.value
                    val openInvoices = openInvoiceListFromNotification.value
                    val openAuditLog = openAuditLogFromNotification.value
                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(session.snackbarMessage) {
                        session.snackbarMessage?.let { msg ->
                            snackbarHostState.showSnackbar(msg)
                            mainViewModel.clearMessage()
                        }
                    }

                    Scaffold { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            when {
                                session.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                !session.isAuthenticated -> Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { authPadding ->
                                    Box(modifier = Modifier.fillMaxSize().padding(authPadding).imePadding()) {
                                        AuthLoginScreen(activity = this@MainActivity, mainViewModel = mainViewModel)
                                    }
                                }
                                else -> AppNavigation(
                                    mainViewModel = mainViewModel,
                                    snackbarHostState = snackbarHostState,
                                    openVoucherId = voucherId,
                                    onVoucherOpened = { pendingVoucherId.value = null },
                                    openInvoiceList = openInvoices,
                                    onInvoiceListOpened = { openInvoiceListFromNotification.value = false },
                                    openAuditLog = openAuditLog,
                                    onAuditLogOpened = { openAuditLogFromNotification.value = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        when (intent.getStringExtra(EXTRA_NOTIFICATION_DESTINATION)) {
            DESTINATION_VOUCHER -> {
                pendingVoucherId.value = intent.getStringExtra(EXTRA_NOTIFICATION_DOCUMENT_ID)
                openInvoiceListFromNotification.value = false
                openAuditLogFromNotification.value = false
            }
            DESTINATION_INVOICES -> {
                pendingVoucherId.value = null
                openInvoiceListFromNotification.value = true
                openAuditLogFromNotification.value = false
            }
            DESTINATION_AUDIT -> {
                pendingVoucherId.value = null
                openInvoiceListFromNotification.value = false
                openAuditLogFromNotification.value = true
            }
        }
        intent.removeExtra(EXTRA_NOTIFICATION_DESTINATION)
        intent.removeExtra(EXTRA_NOTIFICATION_DOCUMENT_ID)
    }

    companion object {
        const val EXTRA_NOTIFICATION_DESTINATION = "notification_destination"
        const val EXTRA_NOTIFICATION_DOCUMENT_ID = "notification_document_id"
        const val DESTINATION_VOUCHER = "voucher"
        const val DESTINATION_INVOICES = "invoices"
        const val DESTINATION_AUDIT = "audit"
    }
}
