package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.ParcelActivityLogEntity

@Dao
interface ParcelActivityLogDao {
    @Insert
    suspend fun insert(log: ParcelActivityLogEntity): Long

    @Query("SELECT * FROM parcel_activity_log ORDER BY createdAt DESC LIMIT :limit")
    fun liveRecent(limit: Int = 300): Flow<List<ParcelActivityLogEntity>>

    @Query("SELECT * FROM parcel_activity_log WHERE parcelId = :parcelId ORDER BY createdAt DESC")
    suspend fun forParcel(parcelId: Long): List<ParcelActivityLogEntity>

    @Query("SELECT * FROM parcel_activity_log WHERE mauzaId = :mauzaId ORDER BY createdAt DESC")
    fun liveForMauza(mauzaId: Long): Flow<List<ParcelActivityLogEntity>>

    @Query("SELECT * FROM parcel_activity_log WHERE isUploaded = 0 ORDER BY createdAt ASC LIMIT 200")
    suspend fun pendingUpload(): List<ParcelActivityLogEntity>

    @Query("UPDATE parcel_activity_log SET isUploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM parcel_activity_log WHERE isUploaded = 0")
    fun livePendingCount(): Flow<Int>

    @Query("DELETE FROM parcel_activity_log WHERE isUploaded = 1 AND createdAt < :olderThan")
    suspend fun pruneUploaded(olderThan: Long)
}