package com.smartaccountant.pro.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val companyId: String,
    val enableTax: Boolean = false,
    val defaultTaxPercent: Double = 15.0,
    val calculateTaxAfterDiscount: Boolean = true,
    val enableDiscount: Boolean = true,
    val discountAsPercentageDefault: Boolean = false,
    val allowSellBelowCost: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val autoReceiptForCashSales: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val autoReverseSalesReturns: Boolean = false,
    val allowDuplicateItemsInInvoice: Boolean = true,
    val enforceStockOnSale: Boolean = true,
    val enableExpiryTracking: Boolean = true,
    val enableMultiUnits: Boolean = true,
    val enableMeteringSystem: Boolean = true,
    val minMeterUnder100CmCountsAs1Meter: Boolean = true,
    val enableCostCenters: Boolean = false, // تمت إضافة خيار لتفعيل/تعطيل مراكز التكلفة
    val useDebitCreditButtonsInsteadOfSave: Boolean = false,
    val showPreviousBalanceInVoucher: Boolean = true,
    val showRemainingBalanceInVoucher: Boolean = true,
    val postIncomingRemittanceAsPayment: Boolean = false,
    val defaultPaperSize: PaperSize = PaperSize.A4,
    val bluetoothPrinterAddress: String = "",
    val bluetoothPrinterName: String = "",
    val showLogoAndStampOnInvoices: Boolean = true,
    val showLogoAndStampOnVouchers: Boolean = true,
    val showLogoAndStampOnStatements: Boolean = true,
    val showTaxQrOnInvoice: Boolean = true,
    val repeatHeaderEveryPage: Boolean = true,
    val signatureLabel1: String = "المستلم",
    val signatureLabel2: String = "المحاسب",
    val signatureLabel3: String = "المدير المالي",
    val signatureLabel4: String = "الاعتماد",
    val invoiceFooterNote: String = "شكراً لتعاملكم معنا",
    val voucherFooterNote: String = "",
    val statementFooterNote: String = "",
    val balanceConfirmationNote: String = "نرجو منكم مطابقة ومصادقة الأرصدة أدناه وذلك كجزء من إجراءاتنا مع كافة العملاء ولما فيه مصلحة الجميع.",
    val smsHeaderText: String = "عميلنا المحترم:",
    val smsFooterText: String = "نود إشعاركم بالقيد على حسابكم لدينا.",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val enableAppLock: Boolean = true,
    val enableBiometric: Boolean = false,
    val appPinHash: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE
)
