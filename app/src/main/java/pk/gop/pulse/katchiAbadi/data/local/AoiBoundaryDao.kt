package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.AoiBoundaryEntity

@Dao
interface AoiBoundaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AoiBoundaryEntity>)

    @Query("SELECT * FROM aoi_boundaries ORDER BY tehsil ASC")
    suspend fun getAll(): List<AoiBoundaryEntity>

    @Query("SELECT * FROM aoi_boundaries WHERE tehsil = :tehsil")
    suspend fun getByTehsil(tehsil: String): List<AoiBoundaryEntity>

    @Query("DELETE FROM aoi_boundaries WHERE tehsil = :tehsil")
    suspend fun deleteByTehsil(tehsil: String)

    @Query("SELECT DISTINCT tehsil FROM aoi_boundaries")
    suspend fun getAllTehsils(): List<String>

    @Query("SELECT COUNT(*) FROM aoi_boundaries")
    suspend fun count(): Int
}