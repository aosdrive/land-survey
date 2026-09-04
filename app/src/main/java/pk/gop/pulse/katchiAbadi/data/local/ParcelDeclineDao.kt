package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity

@Dao
interface ParcelDeclineDao {

    @Insert
    suspend fun insertDecline(decline: ParcelDeclineEntity): Long

    @Query("SELECT * FROM parcel_declines WHERE isUploaded = 0 ORDER BY createdAt DESC")
    suspend fun getPendingDeclines(): List<ParcelDeclineEntity>

    @Query("SELECT * FROM parcel_declines WHERE isUploaded = 0 ORDER BY createdAt DESC")
    fun livePendingDeclines(): Flow<List<ParcelDeclineEntity>>

    @Query("SELECT COUNT(*) FROM parcel_declines WHERE isUploaded = 0")
    fun livePendingDeclineCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM parcel_declines WHERE isUploaded = 0")
    suspend fun pendingDeclineCount(): Int

    @Query("UPDATE parcel_declines SET isUploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    /** Ek parcel par pehle se decline maujood hai ya nahi. */
    @Query("SELECT COUNT(*) FROM parcel_declines WHERE serverParcelId = :parcelId")
    suspend fun countForParcel(parcelId: Long): Int

    @Query("SELECT * FROM parcel_declines WHERE id = :id")
    suspend fun getDeclineById(id: Long): ParcelDeclineEntity?

    @Delete
    suspend fun deleteDecline(decline: ParcelDeclineEntity)
}