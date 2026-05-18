package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.PestTypeEntity

@Dao
interface PestTypeDao {
    @Query("SELECT * FROM pest_types ORDER BY id ASC")
    suspend fun getAll(): List<PestTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PestTypeEntity>)

    @Query("DELETE FROM pest_types")
    suspend fun clearAll()
}