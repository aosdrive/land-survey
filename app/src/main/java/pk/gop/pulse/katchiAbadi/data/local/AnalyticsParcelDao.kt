package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import pk.gop.pulse.katchiAbadi.domain.model.AnalyticsParcelEntity

@Dao
interface AnalyticsParcelDao {

    @Query("SELECT * FROM analytics_parcels WHERE mauzaId = :mauzaId")
    suspend fun getForMauza(mauzaId: Int): List<AnalyticsParcelEntity>

    @Query("SELECT MAX(cachedAt) FROM analytics_parcels WHERE mauzaId = :mauzaId")
    suspend fun cachedAt(mauzaId: Int): Long?

    @Query("DELETE FROM analytics_parcels WHERE mauzaId = :mauzaId")
    suspend fun deleteForMauza(mauzaId: Int)

    @Insert
    suspend fun insertAll(rows: List<AnalyticsParcelEntity>)

    /** Swaps the whole cached set for one mauza atomically, so a failed write never leaves it half-empty. */
    @Transaction
    suspend fun replaceForMauza(mauzaId: Int, rows: List<AnalyticsParcelEntity>) {
        deleteForMauza(mauzaId)
        insertAll(rows)
    }
}
