package com.smartaccountant.pro.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class VoucherWithLines(
    @Embedded val voucher: VoucherEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "voucherId"
    )
    val lines: List<VoucherLineEntity>
)
