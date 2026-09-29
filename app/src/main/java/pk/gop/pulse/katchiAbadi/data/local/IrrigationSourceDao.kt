package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.*
import pk.gop.pulse.katchiAbadi.domain.model.IrrigationSourceEntity

@Dao
interface IrrigationSourceDao {
    @Query("SELECT * FROM irrigation_source ORDER BY value ASC")
    suspend fun getAllSources(): List<IrrigationSourceEntity>

    @Query("SELECT COUNT(*) FROM irrigation_source")
    suspend fun getSourceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSources(sources: List<IrrigationSourceEntity>)

    @Query("DELETE FROM irrigation_source")
    suspend fun deleteAllSources()
}
