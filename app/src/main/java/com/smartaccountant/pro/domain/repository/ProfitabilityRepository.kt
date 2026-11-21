package com.smartaccountant.pro.domain.repository

import com.smartaccountant.pro.core.security.PermissionManager
import com.smartaccountant.pro.data.local.AppDatabase
import com.smartaccountant.pro.data.model.InvoiceEntity
import com.smartaccountant.pro.data.model.InvoiceItemEntity
import com.smartaccountant.pro.data.model.InvoiceType
import com.smartaccountant.pro.data.model.UserEntity

data class ProductProfitability(
    val productId: String,
    val productName: String,
    val quantity: Double,
    val revenue: Double,
    val costOfGoodsSold: Double,
    val profit: Double
)

data class ProfitabilityReport(
    val products: List<ProductProfitability>,
    val totalCostOfGoodsSold: Double
)

class ProfitabilityRepository(private val db: AppDatabase) {
    suspend fun getProfitability(user: UserEntity, companyId: String): ProfitabilityReport {
        require(user.companyId == companyId) { "لا يمكن عرض ربحية شركة أخرى" }
        PermissionManager.requirePermission(
            user, { it.canViewProfitability }, "لا تملك صلاحية عرض ربحية الأصناف"
        )
        val invoices = db.invoiceDao().getPostedInvoicesThroughDate(companyId, Long.MAX_VALUE)
            .filter { it.type == InvoiceType.SALE || it.type == InvoiceType.SALE_RETURN }
        val items = invoices.flatMap { invoice ->
            db.invoiceDao().getInvoiceItemsForCompany(companyId, invoice.id).map { invoice to it }
        }
        return ProfitabilityCalculations.calculate(items)
    }
}

object ProfitabilityCalculations {
    fun calculate(items: List<Pair<InvoiceEntity, InvoiceItemEntity>>): ProfitabilityReport {
        val totals = linkedMapOf<String, ProductProfitability>()
        items.forEach { (invoice, item) ->
            if (!invoice.isPosted || invoice.isArchived || invoice.isDeleted || item.isDeleted) return@forEach
            val isSale = invoice.type == InvoiceType.SALE
            val isReturn = invoice.type == InvoiceType.SALE_RETURN
            if (!isSale && !isReturn) return@forEach
            val sign = if (isSale) 1.0 else -1.0
            val quantity = item.quantity * sign
            val revenue = item.lineTotal * invoice.exchangeRate * sign
            val cost = item.costPriceAtTime * item.quantity * invoice.exchangeRate * sign
            val old = totals[item.productId]
            totals[item.productId] = ProductProfitability(
                productId = item.productId,
                productName = item.productName,
                quantity = (old?.quantity ?: 0.0) + quantity,
                revenue = (old?.revenue ?: 0.0) + revenue,
                costOfGoodsSold = (old?.costOfGoodsSold ?: 0.0) + cost,
                profit = (old?.profit ?: 0.0) + revenue - cost
            )
        }
        val products = totals.values
            .filter { it.quantity > 0.0 || it.revenue != 0.0 || it.costOfGoodsSold != 0.0 }
            .sortedByDescending { it.profit }
            .take(10)
        return ProfitabilityReport(
            products = products,
            totalCostOfGoodsSold = totals.values.sumOf { it.costOfGoodsSold }
        )
    }
}
