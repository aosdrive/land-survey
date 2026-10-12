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

    /** Inserts several declines in one transaction (multi-parcel decline sharing one picture set). */
    @Insert
    suspend fun insertDeclines(declines: List<ParcelDeclineEntity>): List<Long>

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

    /** Parcels declined on this device (pending or uploaded), shown as declined on Map Analysis. */
    @Query("SELECT DISTINCT serverParcelId FROM parcel_declines WHERE serverParcelId IS NOT NULL")
    suspend fun getDeclinedParcelIds(): List<Long>

    @Query("SELECT * FROM parcel_declines WHERE id = :id")
    suspend fun getDeclineById(id: Long): ParcelDeclineEntity?

    /**
     * How many rows other than [excludeId] (pending or uploaded) still reference the picture at [path].
     * Declines saved together share the same three files, so a file may only be unlinked when this is 0.
     */
    @Query("SELECT COUNT(*) FROM parcel_declines WHERE id != :excludeId AND (pic1Path = :path OR pic2Path = :path OR pic3Path = :path)")
    suspend fun countOtherRowsUsingPicture(excludeId: Long, path: String): Int

    @Delete
    suspend fun deleteDecline(decline: ParcelDeclineEntity)
}