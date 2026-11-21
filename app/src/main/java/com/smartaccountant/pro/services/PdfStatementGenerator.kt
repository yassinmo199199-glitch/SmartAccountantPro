package com.smartaccountant.pro.services

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.core.config.AppConfig
import com.smartaccountant.pro.core.utils.CurrencyFormatter
import com.smartaccountant.pro.core.utils.DateUtils
import com.smartaccountant.pro.data.model.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class StatementRow(
    val dateMillis: Long,
    val docType: String,
    val docNumber: Long,
    val debit: Double,
    val credit: Double,
    val description: String
)

class PdfStatementGenerator(private val context: Context) {

    fun generateInvoicePdf(
        company: CompanyEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        account: AccountEntity?,
        currency: CurrencyEntity?,
        settings: AppSettingsEntity
    ): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 24
        val contentWidth = pageWidth - margin * 2
        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 9.5f
            isAntiAlias = true
        }
        val headerPaint = TextPaint(bodyPaint).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val tableWidths = listOf(203, 70, 90, 90, contentWidth - 453)
        val tableHeaders = listOf("الصنف", "الكمية", "الوحدة", "السعر", "الإجمالي")
        val rows = items.map {
            listOf(
                it.productName,
                CurrencyFormatter.format(it.quantity),
                it.unitName,
                CurrencyFormatter.format(it.unitPrice, currency?.symbol.orEmpty()),
                CurrencyFormatter.format(it.lineTotal, currency?.symbol.orEmpty())
            )
        }
        val rowPaint = TextPaint(bodyPaint)
        val rowHeights = rows.map { tableRowHeight(it, tableWidths, rowPaint, minimumHeight = 30f) }
        val pageGroups = mutableListOf<MutableList<Int>>()
        var currentGroup = mutableListOf<Int>()
        var usedHeight = 0f
        rows.indices.forEach { index ->
            val height = rowHeights[index]
            if (currentGroup.isNotEmpty() && usedHeight + height > 470f) {
                pageGroups.add(currentGroup)
                currentGroup = mutableListOf()
                usedHeight = 0f
            }
            currentGroup.add(index)
            usedHeight += height
        }
        if (currentGroup.isEmpty()) currentGroup = mutableListOf()
        pageGroups.add(currentGroup)

        pageGroups.forEachIndexed { pageIndex, rowIndexes ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
            val canvas = page.canvas
            var y = margin.toFloat()
            val companyInfo = listOf(company.addressAr, company.phone, company.taxNumber)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            val headerHeight = if (companyInfo.isBlank()) 56f else 72f
            canvas.drawRoundRect(
                RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + headerHeight),
                9f, 9f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 45) }
            )
            var headerY = y + 8f
            headerY = drawRtlBlock(
                canvas, company.nameAr, TextPaint(titlePaint).apply { color = Color.WHITE },
                margin + 10f, headerY, contentWidth - 20, Layout.Alignment.ALIGN_CENTER
            )
            if (companyInfo.isNotBlank()) {
                drawRtlBlock(
                    canvas, companyInfo, TextPaint(bodyPaint).apply { color = Color.WHITE },
                    margin + 10f, headerY, contentWidth - 20, Layout.Alignment.ALIGN_CENTER
                )
            }
            y += headerHeight + 9f
            y = drawRtlBlock(
                canvas,
                "${invoice.type.arabicTitle}  ${invoice.displayNumber.ifBlank { "#${invoice.invoiceNumber}" }}",
                titlePaint, margin.toFloat(), y, contentWidth, Layout.Alignment.ALIGN_CENTER
            )
            val details = listOf(
                "التاريخ: ${DateUtils.formatDateTime(invoice.dateMillis)}",
                "الحساب: ${account?.name ?: "—"}",
                "طريقة الدفع: ${invoice.paymentMethod.arabicTitle}",
                "الموظف: ${invoice.createdByUserName.ifBlank { "—" }}"
            )
            details.forEach { detail ->
                y = drawRtlBlock(canvas, detail, bodyPaint, margin.toFloat(), y + 2f, contentWidth, Layout.Alignment.ALIGN_NORMAL)
            }
            y += 6f
            y = drawTableRow(
                canvas, tableHeaders, tableWidths, margin.toFloat(), y, headerPaint,
                Color.rgb(38, 50, 56), Color.WHITE, borderPaint, minimumHeight = 30f
            )
            rowIndexes.forEachIndexed { index, rowIndex ->
                y = drawTableRow(
                    canvas, rows[rowIndex], tableWidths, margin.toFloat(), y, rowPaint,
                    if (rowIndex % 2 == 0) Color.WHITE else Color.rgb(246, 248, 250),
                    Color.BLACK, borderPaint, minimumHeight = 30f
                )
            }
            if (pageIndex == pageGroups.lastIndex) {
                y += 12f
                val summaryRows = listOf(
                    "الإجمالي قبل الخصم" to invoice.subTotal,
                    "الخصم" to invoice.discountAmount,
                    "الضريبة" to invoice.taxAmount,
                    "الصافي" to invoice.netTotal,
                    "المدفوع" to invoice.paidAmount,
                    "المتبقي" to (invoice.netTotal - invoice.paidAmount).coerceAtLeast(0.0)
                )
                summaryRows.forEachIndexed { index, (label, amount) ->
                    val cells = listOf(
                        CurrencyFormatter.format(amount, currency?.symbol.orEmpty()),
                        label
                    )
                    y = drawTableRow(
                        canvas, cells, listOf(contentWidth - 145, 145), margin.toFloat(), y,
                        if (index == 3) TextPaint(titlePaint) else bodyPaint,
                        if (index == 3) Color.rgb(235, 238, 240) else Color.WHITE,
                        Color.BLACK, borderPaint, minimumHeight = 28f
                    )
                }
                if (invoice.notes.isNotBlank()) {
                    y = drawRtlBlock(canvas, "ملاحظات: ${invoice.notes}", bodyPaint, margin.toFloat(), y + 10f, contentWidth, Layout.Alignment.ALIGN_NORMAL)
                }
                if (settings.invoiceFooterNote.isNotBlank()) {
                    drawRtlBlock(
                        canvas, settings.invoiceFooterNote, bodyPaint, margin.toFloat(),
                        minOf(y + 18f, pageHeight - 55f), contentWidth, Layout.Alignment.ALIGN_CENTER
                    )
                }
            }
            pdf.finishPage(page)
        }

        val safeNumber = invoice.displayNumber.ifBlank { invoice.invoiceNumber.toString() }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(context.cacheDir, "invoice_${safeNumber}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun generateVoucherPdf(
        company: CompanyEntity,
        voucher: VoucherEntity,
        account: AccountEntity?,
        counterAccount: AccountEntity?,
        cashBoxName: String?,
        currency: CurrencyEntity?,
        settings: AppSettingsEntity
    ): File {
        val pdf = PdfDocument()
        val pageWidth = if (settings.defaultPaperSize == PaperSize.A4) 595 else 320
        val pageHeight = 842
        val margin = 24
        val contentWidth = pageWidth - margin * 2
        val labelWidth = if (pageWidth > 400) 150 else 92
        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = if (pageWidth > 400) 11f else 10f
            isAntiAlias = true
        }
        val headerPaint = TextPaint(titlePaint).apply { color = Color.WHITE; textSize = 17f }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val labelPaint = TextPaint(bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val tableWidths = listOf(contentWidth - labelWidth, labelWidth)
        var pageNumber = 0
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageNumber).create())
        var canvas = page.canvas

        fun drawHeader(): Float {
            val headerHeight = if (company.addressAr.isBlank() && company.phone.isBlank()) 58f else 76f
            canvas.drawRoundRect(
                RectF(margin.toFloat(), margin.toFloat(), (pageWidth - margin).toFloat(), margin + headerHeight),
                10f, 10f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 45) }
            )
            var headerY = margin + 10f
            headerY = drawRtlBlock(canvas, company.nameAr, headerPaint, margin + 10f, headerY, contentWidth - 20, Layout.Alignment.ALIGN_CENTER)
            if (company.addressAr.isNotBlank() || company.phone.isNotBlank()) {
                val info = listOf(company.addressAr, company.phone).filter { it.isNotBlank() }.joinToString(" | ")
                drawRtlBlock(
                    canvas,
                    info,
                    TextPaint(bodyPaint).apply { color = Color.WHITE; textSize = 9f },
                    margin + 10f,
                    headerY,
                    contentWidth - 20,
                    Layout.Alignment.ALIGN_CENTER
                )
            }
            var nextY = margin + headerHeight + 10f
            nextY = drawRtlBlock(canvas, voucher.type.arabicTitle, titlePaint, margin.toFloat(), nextY, contentWidth, Layout.Alignment.ALIGN_CENTER) + 2f
            nextY = drawTableRow(
                canvas,
                listOf("القيمة", "البيان"),
                tableWidths,
                margin.toFloat(),
                nextY,
                labelPaint,
                Color.rgb(38, 50, 56),
                Color.WHITE,
                borderPaint,
                minimumHeight = 30f
            )
            return nextY
        }

        fun nextPage() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageNumber).create())
            canvas = page.canvas
        }

        var y = drawHeader()
        val rows = buildList {
            add("رقم السند" to voucher.displayNumber.ifBlank { "#${voucher.voucherNumber}" })
            add("التاريخ" to DateUtils.formatDateTime(voucher.dateMillis))
            add("الحساب" to (account?.name ?: "—"))
            counterAccount?.let { add("الحساب المقابل" to it.name) }
            cashBoxName?.takeIf { it.isNotBlank() }?.let { add("الصندوق" to it) }
            currency?.let { add("العملة" to it.name) }
            add("المبلغ" to CurrencyFormatter.format(voucher.amount, currency?.symbol.orEmpty()))
            currency?.let { add("المبلغ كتابةً" to ArabicTafqeet.convert(voucher.amount, it.name, it.subUnitName)) }
            add("البيان" to voucher.description.ifBlank { "—" })
            add("الموظف" to voucher.createdByUserName.ifBlank { "—" })
        }
        rows.forEachIndexed { index, (label, value) ->
            val rowPaint = TextPaint(bodyPaint).apply {
                if (label == "المبلغ") {
                    textSize += 1f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
            }
            val cells = listOf(value, label)
            val rowHeight = tableRowHeight(cells, tableWidths, rowPaint, minimumHeight = 32f)
            if (y + rowHeight > pageHeight - 115f) {
                nextPage()
                y = drawHeader()
            }
            y = drawTableRow(
                canvas,
                cells,
                tableWidths,
                margin.toFloat(),
                y,
                rowPaint,
                if (index % 2 == 0) Color.WHITE else Color.rgb(246, 248, 250),
                Color.BLACK,
                borderPaint,
                minimumHeight = 32f
            )
        }

        loadVoucherAttachment(voucher.attachmentUrl)?.let { bitmap ->
            val maxWidth = contentWidth.toFloat()
            val maxHeight = 180f
            val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height, 1f)
            val imageWidth = bitmap.width * scale
            val imageHeight = bitmap.height * scale
            if (y + imageHeight + 24f > pageHeight - 115f) {
                nextPage()
                y = drawHeader()
            }
            y += 12f
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(margin.toFloat(), y, margin + imageWidth, y + imageHeight),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            y += imageHeight + 12f
            bitmap.recycle()
        }

        if (settings.voucherFooterNote.isNotBlank()) {
            y = drawRtlBlock(canvas, settings.voucherFooterNote, bodyPaint, margin.toFloat(), y + 10f, contentWidth)
        }
        if (y + 60f > pageHeight - 48f) {
            nextPage()
            y = drawHeader()
        }
        y += 26f
        drawRtlBlock(
            canvas,
            "${settings.signatureLabel1}                         ${settings.signatureLabel2}",
            bodyPaint,
            margin.toFloat(),
            y,
            contentWidth,
            Layout.Alignment.ALIGN_CENTER
        )
        drawRtlBlock(
            canvas,
            "صفحة $pageNumber — ${DateUtils.formatDateTime(System.currentTimeMillis())}",
            TextPaint(bodyPaint).apply { textSize = 8f; color = Color.DKGRAY },
            margin.toFloat(),
            (pageHeight - 36).toFloat(),
            contentWidth,
            Layout.Alignment.ALIGN_CENTER
        )
        pdf.finishPage(page)

        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeNumber = voucher.displayNumber.ifBlank { voucher.voucherNumber.toString() }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(outDir, "voucher_${safeNumber}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /**
     * يولد ملف PDF عربي متصل الحروف بالكامل باستخدام StaticLayout + TextDirectionHeuristics.RTL
     * مع دعم تعدد الصفحات وتكرار الترويسة وترقيم الصفحات
     */
    fun generateAccountStatementPdf(
        company: CompanyEntity,
        account: AccountEntity,
        currency: CurrencyEntity,
        rows: List<StatementRow>,
        totalDebit: Double,
        totalCredit: Double,
        netBalance: Double,
        settings: AppSettingsEntity,
        isBalanceConfirmation: Boolean = false
    ): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 24
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 9.5f
            isAntiAlias = true
        }
        val headerPaint = TextPaint(bodyPaint).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val tableWidths = listOf(68, 105, 78, 78, 84, contentWidth - 413)
        val tableHeaders = listOf("التاريخ", "المستند", "عليه", "له", "الرصيد", "البيان")
        var runningBalance = 0.0
        val tableRows = if (isBalanceConfirmation) emptyList() else rows.mapIndexed { index, row ->
            runningBalance += row.debit - row.credit
            listOf(
                DateUtils.formatDate(row.dateMillis),
                "${row.docType}\n#${row.docNumber}",
                if (row.debit == 0.0) "—" else CurrencyFormatter.format(row.debit),
                if (row.credit == 0.0) "—" else CurrencyFormatter.format(row.credit),
                CurrencyFormatter.format(if (index == rows.lastIndex) netBalance else runningBalance),
                row.description.ifBlank { "—" }
            )
        }
        val rowPaint = TextPaint(bodyPaint)
        val rowHeights = tableRows.map { tableRowHeight(it, tableWidths, rowPaint, minimumHeight = 30f) }
        val pageGroups = mutableListOf<MutableList<Int>>()
        if (tableRows.isEmpty()) {
            pageGroups.add(mutableListOf())
        } else {
            var group = mutableListOf<Int>()
            var usedHeight = 0f
            tableRows.indices.forEach { index ->
                val height = rowHeights[index]
                if (group.isNotEmpty() && usedHeight + height > 440f) {
                    pageGroups.add(group)
                    group = mutableListOf()
                    usedHeight = 0f
                }
                group.add(index)
                usedHeight += height
            }
            pageGroups.add(group)
        }

        pageGroups.forEachIndexed { pageIndex, rowIndexes ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin.toFloat()

            if (pageIndex == 0 || settings.repeatHeaderEveryPage) {
                val headerHeight = if (company.addressAr.isBlank() && company.phone.isBlank()) 56f else 72f
                canvas.drawRoundRect(
                    RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + headerHeight),
                    9f, 9f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 45) }
                )
                var headerY = y + 8f
                headerY = drawRtlBlock(
                    canvas,
                    company.nameAr,
                    TextPaint(titlePaint).apply { color = Color.WHITE },
                    margin + 10f,
                    headerY,
                    contentWidth - 20,
                    Layout.Alignment.ALIGN_CENTER
                )
                if (company.addressAr.isNotBlank() || company.phone.isNotBlank()) {
                    val companyInfo = listOf(company.addressAr, company.phone).filter { it.isNotBlank() }.joinToString(" | ")
                    drawRtlBlock(
                        canvas,
                        companyInfo,
                        TextPaint(bodyPaint).apply { color = Color.WHITE },
                        margin + 10f,
                        headerY,
                        contentWidth - 20,
                        Layout.Alignment.ALIGN_CENTER
                    )
                }
                y += headerHeight + 9f
                val docTitle = if (isBalanceConfirmation) "مستند مصادقة ومطابقة رصيد" else "كشف حساب تفصيلي"
                y = drawRtlBlock(canvas, docTitle, titlePaint, margin.toFloat(), y, contentWidth, Layout.Alignment.ALIGN_CENTER)
                y = drawRtlBlock(
                    canvas,
                    "الحساب: ${account.name}    العملة: ${currency.name}    تاريخ الطباعة: ${DateUtils.formatDate(System.currentTimeMillis())}",
                    bodyPaint,
                    margin.toFloat(),
                    y,
                    contentWidth,
                    Layout.Alignment.ALIGN_CENTER
                )
                val periodLabel = if (rows.isEmpty()) {
                    "الفترة: لا توجد حركات"
                } else {
                    "الفترة من ${DateUtils.formatDate(rows.first().dateMillis)} إلى ${DateUtils.formatDate(rows.last().dateMillis)}"
                }
                y = drawRtlBlock(canvas, periodLabel, bodyPaint, margin.toFloat(), y, contentWidth, Layout.Alignment.ALIGN_CENTER) + 4f
            }

            if (isBalanceConfirmation) {
                y = drawRtlBlock(canvas, settings.balanceConfirmationNote, bodyPaint, margin.toFloat(), y, contentWidth)
            } else {
                y = drawTableRow(
                    canvas,
                    tableHeaders,
                    tableWidths,
                    margin.toFloat(),
                    y,
                    headerPaint,
                    Color.rgb(38, 50, 56),
                    Color.WHITE,
                    borderPaint,
                    minimumHeight = 32f,
                    alignments = List(tableHeaders.size) { Layout.Alignment.ALIGN_CENTER }
                )
                rowIndexes.forEachIndexed { index, rowIndex ->
                    val cells = tableRows[rowIndex]
                    y = drawTableRow(
                        canvas,
                        cells,
                        tableWidths,
                        margin.toFloat(),
                        y,
                        rowPaint,
                        if ((rowIndex % 2) == 0) Color.WHITE else Color.rgb(246, 248, 250),
                        Color.BLACK,
                        borderPaint,
                        minimumHeight = 30f,
                        alignments = listOf(
                            Layout.Alignment.ALIGN_CENTER,
                            Layout.Alignment.ALIGN_CENTER,
                            Layout.Alignment.ALIGN_CENTER,
                            Layout.Alignment.ALIGN_CENTER,
                            Layout.Alignment.ALIGN_CENTER,
                            Layout.Alignment.ALIGN_NORMAL
                        )
                    )
                }
            }

            if (pageIndex == pageGroups.lastIndex) {
                val summaryPaint = TextPaint(titlePaint).apply { textSize = 11f }
                y += 12f
                y = drawTableRow(
                    canvas,
                    listOf(
                        CurrencyFormatter.format(totalDebit, currency.symbol),
                        "إجمالي عليه",
                        CurrencyFormatter.format(totalCredit, currency.symbol),
                        "إجمالي له"
                    ),
                    listOf(140, 133, 140, contentWidth - 413),
                    margin.toFloat(),
                    y,
                    summaryPaint,
                    Color.rgb(238, 242, 245),
                    Color.BLACK,
                    borderPaint,
                    minimumHeight = 34f,
                    alignments = List(4) { Layout.Alignment.ALIGN_CENTER }
                )
                val statusLabel = if (netBalance >= 0) "الرصيد النهائي عليه" else "الرصيد النهائي له"
                y = drawTableRow(
                    canvas,
                    listOf(CurrencyFormatter.format(kotlin.math.abs(netBalance), currency.symbol), statusLabel),
                    listOf(contentWidth - 165, 165),
                    margin.toFloat(),
                    y,
                    TextPaint(titlePaint).apply { textSize = 12f },
                    Color.rgb(225, 235, 242),
                    Color.BLACK,
                    borderPaint,
                    minimumHeight = 36f
                )
                y = drawRtlBlock(
                    canvas,
                    ArabicTafqeet.convert(netBalance, currency.name, currency.subUnitName),
                    bodyPaint,
                    margin.toFloat(),
                    y + 6f,
                    contentWidth,
                    Layout.Alignment.ALIGN_CENTER
                ) + 10f
                drawRtlBlock(
                    canvas,
                    "${settings.signatureLabel1}        ${settings.signatureLabel2}        ${settings.signatureLabel3}        ${settings.signatureLabel4}",
                    bodyPaint,
                    margin.toFloat(),
                    y,
                    contentWidth,
                    Layout.Alignment.ALIGN_CENTER
                )
            }

            drawRtlBlock(
                canvas,
                "صفحة ${pageIndex + 1} من ${pageGroups.size} — تاريخ الطباعة: ${DateUtils.formatDateTime(System.currentTimeMillis())}",
                bodyPaint.apply { textSize = 9f },
                margin.toFloat(),
                (pageHeight - 32).toFloat(),
                contentWidth,
                Layout.Alignment.ALIGN_CENTER
            )
            pdf.finishPage(page)
        }

        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(outDir, "statement_${account.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /**
     * يولد تقريراً جدولياً عاماً متعدد الصفحات (ميزان مراجعة، أرباح وخسائر، إلخ)
     * مع ترويسة شركة متكررة， صفحة وترقيم الصفحات
     */
    fun generateTabularReportPdf(
        company: CompanyEntity,
        reportTitle: String,
        periodLabel: String,
        headers: List<String>,
        rows: List<List<String>>,
        footerRows: List<List<String>> = emptyList(),
        settings: AppSettingsEntity,
        columnWidths: List<Int>? = null
    ): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 24
        val contentWidth = pageWidth - margin * 2
        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 9.5f
            isAntiAlias = true
        }
        val smallPaint = TextPaint(bodyPaint).apply { textSize = 8.5f; color = Color.DKGRAY }
        val headerPaint = TextPaint(bodyPaint).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val widths = if (columnWidths != null && columnWidths.size == headers.size) {
            columnWidths
        } else {
            val base = contentWidth / headers.size.coerceAtLeast(1)
            List(headers.size) { base }.toMutableList().also {
                if (it.isNotEmpty()) it[it.lastIndex] += contentWidth - base * it.size
            }
        }
        val rowPaint = TextPaint(bodyPaint)
        val rowHeights = rows.map { tableRowHeight(it, widths, rowPaint, minimumHeight = 28f) }
        val pageGroups = mutableListOf<MutableList<Int>>()
        var currentGroup = mutableListOf<Int>()
        var usedHeight = 0f
        rows.indices.forEach { index ->
            val height = rowHeights[index]
            if (currentGroup.isNotEmpty() && usedHeight + height > 600f) {
                pageGroups.add(currentGroup)
                currentGroup = mutableListOf()
                usedHeight = 0f
            }
            currentGroup.add(index)
            usedHeight += height
        }
        if (currentGroup.isNotEmpty() || pageGroups.isEmpty()) pageGroups.add(currentGroup)

        pageGroups.forEachIndexed { pageIndex, rowIndexes ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
            val canvas = page.canvas
            var y = margin.toFloat()
            val companyInfo = listOf(company.addressAr, company.phone, company.taxNumber)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            val headerHeight = if (companyInfo.isBlank()) 52f else 68f
            canvas.drawRoundRect(
                RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + headerHeight),
                9f, 9f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 45) }
            )
            var headerY = y + 8f
            headerY = drawRtlBlock(
                canvas, company.nameAr, TextPaint(titlePaint).apply { color = Color.WHITE },
                margin + 10f, headerY, contentWidth - 20, Layout.Alignment.ALIGN_CENTER
            )
            if (companyInfo.isNotBlank()) {
                drawRtlBlock(
                    canvas, companyInfo, TextPaint(bodyPaint).apply { color = Color.WHITE; textSize = 8.5f },
                    margin + 10f, headerY, contentWidth - 20, Layout.Alignment.ALIGN_CENTER
                )
            }
            y += headerHeight + 8f
            y = drawRtlBlock(canvas, reportTitle, titlePaint, margin.toFloat(), y, contentWidth, Layout.Alignment.ALIGN_CENTER)
            y = drawRtlBlock(
                canvas, "الفترة: $periodLabel — تاريخ الطباعة: ${DateUtils.formatDateTime(System.currentTimeMillis())}",
                smallPaint, margin.toFloat(), y, contentWidth, Layout.Alignment.ALIGN_CENTER
            )
            y += 4f
            y = drawTableRow(
                canvas, headers, widths, margin.toFloat(), y, headerPaint,
                Color.rgb(38, 50, 56), Color.WHITE, borderPaint, minimumHeight = 30f
            )
            rowIndexes.forEachIndexed { index, rowIndex ->
                y = drawTableRow(
                    canvas, rows[rowIndex], widths, margin.toFloat(), y, rowPaint,
                    if (rowIndex % 2 == 0) Color.WHITE else Color.rgb(246, 248, 250),
                    Color.BLACK, borderPaint, minimumHeight = 28f
                )
            }
            if (pageIndex == pageGroups.lastIndex) {
                footerRows.forEach { footerRow ->
                    y = drawTableRow(
                        canvas, footerRow, widths, margin.toFloat(), y,
                        TextPaint(titlePaint).apply { textSize = 10f },
                        Color.rgb(232, 238, 244), Color.BLACK, borderPaint, minimumHeight = 30f
                    )
                }
                if (settings.invoiceFooterNote.isNotBlank()) {
                    drawRtlBlock(
                        canvas, settings.invoiceFooterNote, bodyPaint, margin.toFloat(),
                        minOf(y + 14f, pageHeight - 55f), contentWidth, Layout.Alignment.ALIGN_CENTER
                    )
                }
            }
            drawRtlBlock(
                canvas,
                "صفحة ${pageIndex + 1} من ${pageGroups.size}",
                smallPaint,
                margin.toFloat(),
                (pageHeight - 32).toFloat(),
                contentWidth,
                Layout.Alignment.ALIGN_CENTER
            )
            pdf.finishPage(page)
        }

        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeTitle = reportTitle.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(outDir, "report_${safeTitle}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun drawRtlBlock(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(2f, 1.1f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 6f
    }

    private fun tableRowHeight(
        cells: List<String>,
        widths: List<Int>,
        paint: TextPaint,
        minimumHeight: Float
    ): Float {
        val contentHeight = cells.mapIndexed { index, text ->
            val cellPaint = TextPaint(paint)
            StaticLayout.Builder.obtain(text, 0, text.length, cellPaint, (widths[index] - 12).coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(1f, 1.05f)
                .build()
                .height
        }.maxOrNull()?.toFloat() ?: 0f
        return maxOf(minimumHeight, contentHeight + 12f)
    }

    private fun drawTableRow(
        canvas: Canvas,
        cells: List<String>,
        widths: List<Int>,
        x: Float,
        y: Float,
        paint: TextPaint,
        backgroundColor: Int,
        textColor: Int,
        borderPaint: Paint,
        minimumHeight: Float,
        alignments: List<Layout.Alignment> = List(cells.size) { Layout.Alignment.ALIGN_CENTER }
    ): Float {
        val rowHeight = tableRowHeight(cells, widths, paint, minimumHeight)
        var cellX = x
        cells.forEachIndexed { index, text ->
            val width = widths[index]
            canvas.drawRect(cellX, y, cellX + width, y + rowHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
            })
            canvas.drawRect(cellX, y, cellX + width, y + rowHeight, borderPaint)

            val cellPaint = TextPaint(paint).apply { color = textColor }
            val textWidth = (width - 12).coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, cellPaint, textWidth)
                .setAlignment(alignments.getOrElse(index) { Layout.Alignment.ALIGN_CENTER })
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(1f, 1.05f)
                .build()
            canvas.save()
            canvas.translate(cellX + 6f, y + (rowHeight - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
            cellX += width
        }
        return y + rowHeight
    }

    private fun loadVoucherAttachment(attachmentUrl: String): android.graphics.Bitmap? {
        if (attachmentUrl.isBlank()) return null
        val imageFile = if (!attachmentUrl.startsWith("https://")) {
            File(attachmentUrl).takeIf { it.isFile }
                ?: throw IllegalStateException("تعذر العثور على صورة مرفق السند")
        } else {
            val uri = Uri.parse(attachmentUrl)
            val allowedHosts = setOfNotNull(
                "firebasestorage.googleapis.com",
                "firebasestorage.app",
                Uri.parse(AppConfig.CLOUDFLARE_R2_PUBLIC_BASE_URL).host
            )
            require(uri.host in allowedHosts) { "رابط صورة المرفق غير موثوق" }

            val tempFile = File(context.cacheDir, "voucher_attachment_${UUID.randomUUID()}")
            val connection = URL(attachmentUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/*")
            try {
                val responseCode = connection.responseCode
                check(responseCode in 200..299) { "تعذر تحميل صورة المرفق (HTTP $responseCode)" }
                check(connection.contentType?.startsWith("image/") == true) { "مرفق السند ليس صورة" }
                check(connection.contentLengthLong <= MAX_ATTACHMENT_SIZE_BYTES) { "حجم صورة المرفق يتجاوز الحد المسموح" }
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            check(totalBytes <= MAX_ATTACHMENT_SIZE_BYTES) { "حجم صورة المرفق يتجاوز الحد المسموح" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                tempFile
            } catch (error: Exception) {
                tempFile.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "تعذر قراءة صورة مرفق السند" }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 1800 || bounds.outHeight / sampleSize > 1800) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(
                imageFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: throw IllegalStateException("تعذر فك ترميز صورة مرفق السند")
        } finally {
            if (imageFile.parentFile == context.cacheDir) imageFile.delete()
        }
    }

    private companion object {
        const val MAX_ATTACHMENT_SIZE_BYTES = 15L * 1024 * 1024
    }
}
