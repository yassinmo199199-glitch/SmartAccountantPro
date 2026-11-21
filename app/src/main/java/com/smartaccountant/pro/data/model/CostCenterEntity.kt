package com.smartaccountant.pro.data.model  
  
import androidx.room.Entity  
import androidx.room.PrimaryKey  
import java.util.UUID  
  
@Entity(tableName = "cost_centers")  
data class CostCenterEntity(  
    @PrimaryKey val id: String = UUID.randomUUID().toString(),  
    val companyId: String,  
    val name: String,  
    val code: String = "",  
    val isDeleted: Boolean = false,  
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE  
) 
