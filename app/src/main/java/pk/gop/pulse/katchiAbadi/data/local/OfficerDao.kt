package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.OfficerEntity

@Dao
interface OfficerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(officers: List<OfficerEntity>)

    @Query("SELECT * FROM officers ORDER BY fullName ASC")
    suspend fun getAll(): List<OfficerEntity>

    @Query("SELECT COUNT(*) FROM officers")
    suspend fun count(): Int

    @Query("DELETE FROM officers")
    suspend fun clearAll()
}