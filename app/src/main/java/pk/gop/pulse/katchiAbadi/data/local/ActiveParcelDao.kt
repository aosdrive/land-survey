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
//    @Query("Update active_parcels set surveyStatusCode = :statusBit  where  centroid = :centroidGeom")
//    suspend fun updateParcelSurveyStatus(statusBit: Int, centroidGeom: String): Int


}

data class MauzaAreaEntry(
    val mauzaId: Long,
    val mauzaName: String,
    val areaAssigned: String
)
