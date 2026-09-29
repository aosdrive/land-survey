package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.SectionEntity

// ============================================
// SECTION DAO
// ============================================
@Dao
interface SectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: SectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Query("SELECT * FROM sections WHERE divisionId = :divisionId AND isActive = 1 ORDER BY sectionName ASC")
    suspend fun getSectionsByDivision(divisionId: Long): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE sectionId = :sectionId")
    suspend fun getSectionById(sectionId: Long): SectionEntity?

    @Query("DELETE FROM sections WHERE divisionId = :divisionId")
    suspend fun deleteSectionsByDivision(divisionId: Long)

    @Query("DELETE FROM sections")
    suspend fun deleteAllSections()

    @Update
    suspend fun updateSection(section: SectionEntity)

    @Query("SELECT * FROM sections WHERE isActive = 1 ORDER BY sectionName ASC")
    suspend fun getAllSections(): List<SectionEntity>
}