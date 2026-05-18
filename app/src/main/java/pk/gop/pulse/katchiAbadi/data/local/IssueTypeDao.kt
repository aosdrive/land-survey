package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.IssueTypeEntity

@Dao
interface IssueTypeDao {
    @Query("SELECT * FROM issue_types ORDER BY id ASC")
    suspend fun getAll(): List<IssueTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<IssueTypeEntity>)

    @Query("DELETE FROM issue_types")
    suspend fun clearAll()
}