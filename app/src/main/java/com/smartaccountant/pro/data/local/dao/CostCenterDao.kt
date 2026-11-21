package com.smartaccountant.pro.data.local.dao  
  
import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.smartaccountant.pro.data.model.CostCenterEntity  
import kotlinx.coroutines.flow.Flow  
  
@Dao  
interface CostCenterDao {  
    @Query("SELECT * FROM cost_centers WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")  
    fun observeCostCenters(companyId: String): Flow<List<CostCenterEntity>>  
  
    @Upsert  
    suspend fun upsertCostCenter(costCenter: CostCenterEntity)  
} 
