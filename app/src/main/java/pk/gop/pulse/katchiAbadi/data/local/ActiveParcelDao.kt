package pk.gop.pulse.katchiAbadi.data.local


import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus

@Dao
interface ActiveParcelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveParcels(parcels: List<ActiveParcelEntity>)

    @Query("DELETE FROM active_parcels WHERE mauzaId = :mauzaId AND areaAssigned = :area")
    suspend fun deleteParcelsByMauzaAndArea(mauzaId: Long, area: String)

    @Query("SELECT * FROM active_parcels WHERE mauzaId = :mauzaId AND areaAssigned = :area")
    suspend fun getParcelsByMauzaAndArea(mauzaId: Long, area: String): List<ActiveParcelEntity>

    @Query("SELECT DISTINCT mauzaId, mauzaName, areaAssigned FROM active_parcels")
    suspend fun getDistinctDownloadedAreas(): List<MauzaAreaEntry>


    @Query("SELECT * FROM active_parcels WHERE id IN (:ids)")
    suspend fun searchParcels(ids: List<Long>): List<ActiveParcelEntity>

    @Query("SELECT COUNT(*) FROM active_parcels WHERE mauzaId = :mauzaId AND areaAssigned = :area")
    suspend fun getParcelsCountByMauzaAndArea(mauzaId: Long, area: String): Int

    @Query("Update active_parcels set surveyStatusCode = :statusBit, surveyId = :surveyId where  id = :parcelId")
    suspend fun updateParcelSurveyStatus(statusBit: Int, surveyId: Long, parcelId: Long): Int

    @Query("SELECT * FROM active_parcels where surveyId is null and mauzaId = :mauzaId and parcelNo != :parcelNo order by parcelNo ")
    suspend fun getAllUnSurveyedParcels(mauzaId: Long, parcelNo: Long): List<ActiveParcelEntity>


    @Query("SELECT * FROM active_parcels WHERE id = :parcelId")
    suspend fun getParcelById(parcelId: Long): ActiveParcelEntity?


    @Query("UPDATE active_parcels SET surveyStatusCode = :statusCode WHERE id = :parcelId")
    suspend fun updateSurveyStatus(parcelId: Long, statusCode: Int)

    @Query("SELECT surveyStatusCode FROM active_parcels WHERE id = :parcelId")
    suspend fun getSurveyStatus(parcelId: Long): Int?

    @Query("SELECT MAX(id) FROM active_parcels")
    suspend fun getMaxParcelId(): Long?


    // Updated to include only active parcels
    @Query("SELECT * FROM active_parcels WHERE mauzaId = :mauzaId AND areaAssigned = :area AND isActivate = 1")
    suspend fun getActiveParcelsByMauzaAndArea(mauzaId: Long, area: String): List<ActiveParcelEntity>

    // Updated to include only active areas
    @Query("SELECT DISTINCT mauzaId, mauzaName, areaAssigned FROM active_parcels WHERE isActivate = 1")
    suspend fun getDistinctActiveDownloadedAreas(): List<MauzaAreaEntry>

    @Query("SELECT COUNT(*) FROM active_parcels WHERE mauzaId = :mauzaId AND areaAssigned = :area AND isActivate = 1")
    suspend fun getActiveParcelsCountByMauzaAndArea(mauzaId: Long, area: String): Int


    // Get only active unsurveyed parcels
    @Query("SELECT * FROM active_parcels where surveyId is null and mauzaId = :mauzaId and parcelNo != :parcelNo and isActivate = 1 order by parcelNo ")
    suspend fun getAllActiveUnSurveyedParcels(mauzaId: Long, parcelNo: Long): List<ActiveParcelEntity>


    // New methods for IsActivate management
    @Query("UPDATE active_parcels SET isActivate = :isActivate WHERE id = :parcelId")
    suspend fun updateParcelActivationStatus(parcelId: Long, isActivate: Boolean)

    @Query("UPDATE active_parcels SET isActivate = 0 WHERE id = :parcelId")
    suspend fun deactivateParcel(parcelId: Long)

    @Query("UPDATE active_parcels SET isActivate = 1 WHERE id = :parcelId")
    suspend fun activateParcel(parcelId: Long)

    @Query("SELECT * FROM active_parcels WHERE isActivate = 1")
    suspend fun getAllActiveParcels(): List<ActiveParcelEntity>

    @Query("SELECT * FROM active_parcels WHERE isActivate = 0")
    suspend fun getAllDeactivatedParcels(): List<ActiveParcelEntity>

    @Query("SELECT * FROM active_parcels WHERE pkid = :pkid")
    suspend fun getParcelByPkid(pkid: Long): ActiveParcelEntity?

    @Query("SELECT * FROM active_parcels WHERE id IN (:ids) AND isActivate = 1")
    suspend fun searchActiveParcels(ids: List<Long>): List<ActiveParcelEntity>


    // Add these methods to your ActiveParcelDao interface:

    @Query("UPDATE active_parcels SET isActivate = :isActivate WHERE pkid = :pkid")
    suspend fun updateParcelActivationStatusByPkid(pkid: Long, isActivate: Boolean)

    @Query("UPDATE active_parcels SET isActivate = 0 WHERE pkid = :pkid")
    suspend fun deactivateParcelByPkid(pkid: Long)

    @Query("UPDATE active_parcels SET isActivate = 1 WHERE pkid = :pkid")
    suspend fun activateParcelByPkid(pkid: Long)

    // Also add this for debugging
    @Query("SELECT COUNT(*) FROM active_parcels WHERE parcelNo = :parcelNo AND isActivate = 1 AND mauzaId = :mauzaId AND areaAssigned = :area")
    suspend fun countActiveParcelsByNumber(parcelNo: Long, mauzaId: Long, area: String): Int


    // Add these to your ActiveParcelDao interface:

    @Query("SELECT * FROM active_parcels WHERE parcelNo = :parcelNo AND mauzaId = :mauzaId AND areaAssigned = :area ORDER BY subParcelNo")
    suspend fun getParcelsByNumber(parcelNo: Long, mauzaId: Long, area: String): List<ActiveParcelEntity>

    @Query("SELECT * FROM active_parcels WHERE parcelNo = :parcelNo AND mauzaId = :mauzaId AND areaAssigned = :areaName AND isActivate = 0 LIMIT 1")
    suspend fun getDeactivatedParcelByNumber(parcelNo: Long, mauzaId: Long, areaName: String): ActiveParcelEntity?

    @Query("SELECT * FROM active_parcels WHERE parcelNo = :parcelNo AND mauzaId = :mauzaId AND areaAssigned = :areaName AND isActivate = 1")
    suspend fun getActiveParcelsByNumber(parcelNo: Long, mauzaId: Long, areaName: String): List<ActiveParcelEntity>

    // Also add this method to track the original parcel ID during split

    // Transaction method to handle parcel splitting
    @Transaction
    suspend fun splitParcel(originalParcelId: Long, newParcels: List<ActiveParcelEntity>): List<Long> {
        // Delete the original parcel
        deleteParcelById(originalParcelId)
        insertParcels(newParcels)           // insert new

        // Insert new split parcels and return their IDs
        return insertParcels(newParcels)
    }

    @Query("DELETE FROM active_parcels WHERE id = :parcelId")
    suspend fun deleteParcelById(parcelId: Long)

    // Helper method to insert multiple parcels and return their IDs
    @Insert
    suspend fun insertParcels(parcels: List<ActiveParcelEntity>): List<Long>


//    val surveyStatusCode: Int,
//    val surveyId: Int

    @Query(
        """
    SELECT COUNT(*) FROM (
        SELECT mauzaId, areaAssigned FROM active_parcels GROUP BY mauzaId, areaAssigned
    )
"""
    )
    suspend fun getDistinctParcelCount(): Int


//    getDistinctDownloadedAreas

//    // Update
//    @Update
//    suspend fun updateParcel(activeParcelEntity: ActiveParcelEntity)
//
    @Query("Update active_parcels set surveyStatusCode = :statusBit  where  centroid = :centroidGeom")
    suspend fun updateParcelSurveyStatus(statusBit: Int, centroidGeom: String): Int


}

data class MauzaAreaEntry(
    val mauzaId: Long,
    val mauzaName: String,
    val areaAssigned: String
)
