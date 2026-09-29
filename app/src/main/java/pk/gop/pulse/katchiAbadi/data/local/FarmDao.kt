package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.*
import pk.gop.pulse.katchiAbadi.domain.model.FarmEntity

@Dao
interface FarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarms(farms: List<FarmEntity>)

    @Query("SELECT * FROM farms WHERE sectionId = :sectionId")
    suspend fun getFarmsBySection(sectionId: Long): List<FarmEntity>

    @Query("SELECT * FROM farms")
    suspend fun getAllFarms(): List<FarmEntity>

    @Query("SELECT * FROM farms WHERE farmId = :farmId")
    suspend fun getFarmById(farmId: Long): FarmEntity?

    @Query("DELETE FROM farms")
    suspend fun deleteAllFarms()
}