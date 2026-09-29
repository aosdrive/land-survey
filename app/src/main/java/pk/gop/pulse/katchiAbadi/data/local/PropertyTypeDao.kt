package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.PropertyTypeEntity

@Dao
interface PropertyTypeDao {

    @Query("SELECT * FROM property_types ORDER BY sortOrder ASC, value ASC")
    suspend fun getAll(): List<PropertyTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PropertyTypeEntity>)

    @Query("DELETE FROM property_types")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM property_types")
    suspend fun count(): Int
}