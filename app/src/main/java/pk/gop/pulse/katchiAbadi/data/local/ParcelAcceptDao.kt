package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAcceptEntity

@Dao
interface ParcelAcceptDao {

    @Insert
    suspend fun insertAccept(accept: ParcelAcceptEntity): Long

    @Query("SELECT * FROM parcel_accepts WHERE isUploaded = 0 ORDER BY createdAt ASC")
    suspend fun getPendingAccepts(): List<ParcelAcceptEntity>

    @Query("SELECT COUNT(*) FROM parcel_accepts WHERE isUploaded = 0")
    fun livePendingAcceptCount(): Flow<Int>

    @Query("UPDATE parcel_accepts SET isUploaded = 1, lastError = NULL WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("UPDATE parcel_accepts SET lastError = :error WHERE id = :id")
    suspend fun setLastError(id: Long, error: String)

    /** Parcel ids accepted on this device (pending or uploaded), to hide them from the map. */
    @Query("SELECT DISTINCT serverParcelId FROM parcel_accepts")
    suspend fun getAcceptedParcelIds(): List<Long>

    @Query("SELECT COUNT(*) FROM parcel_accepts WHERE serverParcelId = :parcelId")
    suspend fun countForParcel(parcelId: Long): Int

    /** Every accept on this device, pending or uploaded; Map Analysis dates re-surveys against them. */
    @Query("SELECT * FROM parcel_accepts")
    suspend fun getAllAccepts(): List<ParcelAcceptEntity>

    /** Deletes one accept, but only while it is still waiting for upload. Returns rows deleted. */
    @Query("DELETE FROM parcel_accepts WHERE id = :id AND isUploaded = 0")
    suspend fun deletePending(id: Long): Int

    /** Accepts of these parcels that have not reached the server yet. */
    @Query("SELECT COUNT(*) FROM parcel_accepts WHERE isUploaded = 0 AND serverParcelId IN (:parcelIds)")
    suspend fun countPendingForParcels(parcelIds: List<Long>): Int
}
