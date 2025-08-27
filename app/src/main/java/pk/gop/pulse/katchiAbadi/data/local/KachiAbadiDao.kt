package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.KachiAbadiEntity

@Dao
interface KachiAbadiDao {
    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKachiAbadi(kachiAbadiEntity: KachiAbadiEntity)

    // Update
    @Update
    suspend fun updateKachiAbadi(kachiAbadiEntity: KachiAbadiEntity)

    // Select
    @Query("SELECT * FROM KachiAbadiEntity WHERE id = :id")
    suspend fun getKachiAbadiById(id: Int): KachiAbadiEntity?

    // Delete
    @Delete
    suspend fun deleteKachiAbadi(kachiAbadiEntity: KachiAbadiEntity)

    @Query("DELETE FROM KachiAbadiEntity")
    suspend fun deleteAllKachiAbadis()

    // Total Count
    @Query("SELECT count(*) FROM KachiAbadiEntity")
    suspend fun totalKachiAbadisCount(): Int
}