package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.*
import pk.gop.pulse.katchiAbadi.domain.model.LessorEntity

@Dao
interface LessorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lessors: List<LessorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lessor: LessorEntity): Long

    @Query("SELECT * FROM lessors WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getAllLessors(): List<LessorEntity>

    @Query("SELECT * FROM lessors WHERE id = :id")
    suspend fun getLessorById(id: Int): LessorEntity?

    @Query("DELETE FROM lessors")
    suspend fun clearAll()
}