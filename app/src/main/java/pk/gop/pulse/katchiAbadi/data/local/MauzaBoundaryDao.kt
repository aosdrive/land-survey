package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.MauzaBoundaryEntity

@Dao
interface MauzaBoundaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(boundary: MauzaBoundaryEntity)

    @Query("SELECT * FROM mauza_boundary WHERE mauzaId = :mauzaId LIMIT 1")
    suspend fun getByMauzaId(mauzaId: Long): MauzaBoundaryEntity?

    @Query("SELECT * FROM mauza_boundary ORDER BY mauzaName")
    suspend fun getAll(): List<MauzaBoundaryEntity>

    @Query("SELECT COUNT(*) FROM mauza_boundary")
    suspend fun count(): Int

    @Query("DELETE FROM mauza_boundary WHERE mauzaId = :mauzaId")
    suspend fun deleteByMauzaId(mauzaId: Long)
}