package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.ZoneEntity

// ============================================
// ZONE DAO
// ============================================
@Dao
interface ZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: ZoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<ZoneEntity>)

    @Query("SELECT * FROM zones WHERE isActive = 1 ORDER BY zoneName ASC")
    suspend fun getAllZones(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE zoneId = :zoneId")
    suspend fun getZoneById(zoneId: Long): ZoneEntity?

    @Query("DELETE FROM zones")
    suspend fun deleteAllZones()

    @Update
    suspend fun updateZone(zone: ZoneEntity)
}