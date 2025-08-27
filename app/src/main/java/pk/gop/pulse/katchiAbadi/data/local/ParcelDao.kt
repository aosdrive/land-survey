package pk.gop.pulse.katchiAbadi.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
//import pk.gop.pulse.katchiAbadi.activities.DownloadedAreaModel
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus

@Dao
interface ParcelDao {
    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcel(parcelEntity: ParcelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcels(parcels: List<ParcelEntity>)

    // Update
    @Update
    suspend fun updateParcel(parcelEntity: ParcelEntity)

    @Query("Update ParcelEntity set newStatusId = :statusBit, status = :parcelStatus where  centroidGeom = :centroidGeom")
    suspend fun updateParcelSurveyStatus(statusBit: Int, parcelStatus: ParcelStatus, centroidGeom: String): Int

    @Query("""
    UPDATE ParcelEntity 
    SET parcelNo = id, 
        newStatusId = CASE 
                        WHEN isSurveyed = 1 THEN 2 
                        ELSE 1 
                      END
    WHERE newStatusId = 0
    """)
    suspend fun updateRecords(): Int

    @Query("Update ParcelEntity set newStatusId = :statusBit, status = :parcelStatus  where  parcelNo = :parcelNo")
    suspend fun updateParcelSurveyStatusWrtParcelId(statusBit: Int, parcelStatus: ParcelStatus, parcelNo: Long): Int

    @Query("SELECT newStatusId FROM ParcelEntity where kachiAbadiId = :kachiAbadiId and parcelNo = :parcelNo")
    suspend fun getNewStatusId(parcelNo: Long, kachiAbadiId: Long): Int

    // Select
    @Query("SELECT * FROM ParcelEntity WHERE parcelNo = :parcelNo and kachiAbadiId = :kachiAbadiId")
    suspend fun getParcelById(parcelNo: Long, kachiAbadiId: Long): ParcelEntity?

   @Query("SELECT * FROM ParcelEntity WHERE kachiAbadiId = :kachiAbadiId")
    suspend fun getAllParcelsWithAreaId(kachiAbadiId: Long): List<ParcelEntity>

    @Query("SELECT * FROM ParcelEntity WHERE parcelNo IN (:ids) and kachiAbadiId = :kachiAbadiId")
    suspend fun searchParcels(ids: List<Long>, kachiAbadiId: Long): List<ParcelEntity>

    @Query("SELECT * FROM ParcelEntity where newStatusId in (1,4,8) and kachiAbadiId = :kachiAbadiId and parcelNo != :parcelNo order by parcelNo ")
    suspend fun getAllUnSurveyedParcels(kachiAbadiId: Long, parcelNo: Long): List<ParcelEntity>

    // Delete
    @Delete
    suspend fun deleteParcel(parcelEntity: ParcelEntity)

    @Query("DELETE FROM ParcelEntity")
    suspend fun deleteAllParcels()

    @Query("DELETE FROM ParcelEntity WHERE kachiAbadiId = :abadiId")
    suspend fun deleteAllParcelsWrtArea(abadiId: Long)

    // Total Count
    @Query("SELECT count(*) FROM ParcelEntity WHERE kachiAbadiId = :kachiAbadiId")
    suspend fun totalParcelsCountWithAreaId(kachiAbadiId: Long):  Int

    // Total Count
    @Query("SELECT count(*) FROM ParcelEntity where newStatusId IN (:statusBits) and  kachiAbadiId = :kachiAbadiId and status = :status")
    suspend fun totalParcelsCountWrtStatusId(statusBits: List<Int>, kachiAbadiId: Long, status: String): Int

    //multiple area downloads
    @Query("SELECT count(Distinct kachiAbadiId) FROM ParcelEntity")
    suspend fun getDistinctParcelCount(): Int

    @Query("SELECT count(Distinct kachiAbadiId) FROM ParcelEntity")
    fun getDistinctParcelLivedataCount(): LiveData<Int>

//    @Query("SELECT Distinct mauzaId, mauzaName, kachiAbadiId, abadiName, 0 as isSelected FROM ParcelEntity")
//    fun liveTotalDownloadedAreas(): LiveData<List<DownloadedAreaModel>>

    @Query("SELECT parcelNo FROM ParcelEntity where centroidGeom = :centroid order by parcelNo desc limit 1")
    fun getParcelIdByCentroid(centroid: String): Long

//    @Query("SELECT Distinct mauzaId, mauzaName, kachiAbadiId, abadiName, 0 as isSelected FROM ParcelEntity order by parcelNo desc")
//    suspend fun lastDownloadedArea(): DownloadedAreaModel?

    @Query("SELECT * FROM ParcelEntity WHERE kachiAbadiId = :kachiAbadiId  and mauzaId = :mauzaId limit 1")
    suspend fun getSingleParcel(kachiAbadiId: Long, mauzaId: Long): ParcelEntity?

    @Transaction
    suspend fun updateAllTablesInTransaction(
        surveyFormDao: SurveyFormDao,
        tempSurveyFormDao: TempSurveyFormDao,
        notAtHomeSurveyFormDao: NotAtHomeSurveyFormDao
    ) {
        surveyFormDao.updateRecords()
        tempSurveyFormDao.updateRecords()
        notAtHomeSurveyFormDao.updateRecords()
        updateRecords()
    }

    @Query("""SELECT COUNT(pkId) AS totalCount
            FROM (
                SELECT pkId FROM SurveyFormEntity  
                UNION ALL
                SELECT pkId FROM TempSurveyFormEntity  
                UNION ALL
                SELECT pkId FROM NotAtHomeSurveyFormEntity 
            ) AS combinedTables""")
    suspend fun checkDataSaved(): Int


}