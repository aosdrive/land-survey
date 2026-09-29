package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.DivisionEntity

// ============================================
// DIVISION DAO
// ============================================
@Dao
interface DivisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDivision(division: DivisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDivisions(divisions: List<DivisionEntity>)

    @Query("SELECT * FROM divisions WHERE zoneId = :zoneId AND isActive = 1 ORDER BY divisionName ASC")
    suspend fun getDivisionsByZone(zoneId: Long): List<DivisionEntity>

    @Query("SELECT * FROM divisions WHERE divisionId = :divisionId")
    suspend fun getDivisionById(divisionId: Long): DivisionEntity?

    @Query("DELETE FROM divisions WHERE zoneId = :zoneId")
    suspend fun deleteDivisionsByZone(zoneId: Long)

    @Query("DELETE FROM divisions")
    suspend fun deleteAllDivisions()

    @Update
    suspend fun updateDivision(division: DivisionEntity)

    @Query("SELECT * FROM divisions WHERE isActive = 1 ORDER BY divisionName ASC")
    suspend fun getAllDivisions(): List<DivisionEntity>
}