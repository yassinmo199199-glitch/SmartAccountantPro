package com.smartaccountant.pro.services

import android.content.Context
import com.smartaccountant.pro.data.model.*
import java.io.File
import java.io.InputStream

class ExcelImportExportService(private val context: Context) {

    fun exportStatementCsv(account: AccountEntity, rows: List<StatementRow>): File {
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(outDir, "statement_${account.name}_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder("﻿")
        sb.appendLine("التاريخ,نوع المستند,رقم المستند,عليه,له,البيان")
        for (r in rows) {
            sb.appendLine("${r.dateMillis},${r.docType},${r.docNumber},${r.debit},${r.credit},\"${r.description.replace("\"", "'")}\"")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun parseAccountsFromCsv(
        inputStream: InputStream,
        companyId: String,
        defaultCurrencyId: String
    ): List<AccountEntity> {
        val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines()
        val result = mutableListOf<AccountEntity>()
        for ((index, rawLine) in lines.withIndex()) {
            if (index == 0 && rawLine.contains("الاسم")) continue
            val cols = rawLine.split(",", ";", "\t").map { it.trim().removeSurrounding("\"") }
            if (cols.isEmpty() || cols[0].isBlank()) continue
            result.add(
                AccountEntity(
                    companyId = companyId,
                    name = cols[0],
                    notes = cols.getOrElse(1) { "" },
                    phone = cols.getOrElse(2) { "" },
                    openingDebit = cols.getOrElse(3) { "0" }.toDoubleOrNull() ?: 0.0,
                    openingCredit = cols.getOrElse(4) { "0" }.toDoubleOrNull() ?: 0.0,
                    defaultCurrencyId = defaultCurrencyId
                )
            )
        }
        return result
    }

    fun parseProductsFromCsv(
        inputStream: InputStream,
        companyId: String,
        warehouseId: String
    ): List<ProductEntity> {
        val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines()
        val result = mutableListOf<ProductEntity>()
        for ((index, rawLine) in lines.withIndex()) {
            if (index == 0 && rawLine.contains("الصنف")) continue
            val cols = rawLine.split(",", ";", "\t").map { it.trim().removeSurrounding("\"") }
            if (cols.isEmpty() || cols[0].isBlank()) continue
            result.add(
                ProductEntity(
                    companyId = companyId,
                    warehouseId = warehouseId,
                    name = cols[0],
                    barcode = cols.getOrElse(1) { "" },
                    unit1Name = cols.getOrElse(2) { "حبة" }.ifBlank { "حبة" },
                    purchasePrice1 = cols.getOrElse(3) { "0" }.toDoubleOrNull() ?: 0.0,
                    salePrice1 = cols.getOrElse(4) { "0" }.toDoubleOrNull() ?: 0.0,
                    stockQuantity = cols.getOrElse(5) { "0" }.toDoubleOrNull() ?: 0.0
                )
            )
        }
        return result
    }
}
