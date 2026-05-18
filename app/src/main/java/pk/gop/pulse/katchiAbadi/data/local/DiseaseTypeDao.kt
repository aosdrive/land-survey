package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.DiseaseTypeEntity

@Dao
interface DiseaseTypeDao {
    @Query("SELECT * FROM disease_types ORDER BY id ASC")
    suspend fun getAll(): List<DiseaseTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DiseaseTypeEntity>)

    @Query("DELETE FROM disease_types")
    suspend fun clearAll()
}