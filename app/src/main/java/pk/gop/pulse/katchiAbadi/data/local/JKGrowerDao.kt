package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.JKGrowerEntity

@Dao
interface JKGrowerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(growers: List<JKGrowerEntity>)

    @Query("SELECT * FROM jk_growers")
    suspend fun getAllGrowers(): List<JKGrowerEntity>

    @Query("SELECT * FROM jk_growers WHERE mouzaCode = :mouzaCode")
    suspend fun getGrowersByMouza(mouzaCode: String): List<JKGrowerEntity>

    @Query("SELECT * FROM jk_growers WHERE cnicNo = :cnic LIMIT 1")
    suspend fun getGrowerByCnic(cnic: String): JKGrowerEntity?

    @Query("SELECT COUNT(*) FROM jk_growers WHERE mouzaCode = :mouzaCode")
    suspend fun countByMouza(mouzaCode: String): Int

    @Query("DELETE FROM jk_growers WHERE mouzaCode = :mouzaCode")
    suspend fun deleteByMouza(mouzaCode: String)

    @Query("DELETE FROM jk_growers")
    suspend fun clearAll()
}