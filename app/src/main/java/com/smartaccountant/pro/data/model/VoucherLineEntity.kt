package com.smartaccountant.pro.data.model  
  
import androidx.room.Entity  
import androidx.room.PrimaryKey  
import java.util.UUID  
  
@Entity(tableName = "voucher_lines")  
data class VoucherLineEntity(  
    @PrimaryKey val id: String = UUID.randomUUID().toString(),  
    val voucherId: String,  
    val accountId: String,  
    val debit: Double,  
    val credit: Double,  
    val currencyId: String,  
    val exchangeRate: Double = 1.0,  
    val note: String = "",
    val costCenterId: String? = null
) 
