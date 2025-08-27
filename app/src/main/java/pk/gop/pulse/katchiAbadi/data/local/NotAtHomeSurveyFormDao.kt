package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.RecordDetails

@Dao
interface NotAtHomeSurveyFormDao {

    @Query("""
    UPDATE NotAtHomeSurveyFormEntity 
    SET parcelNo = parcelId, 
        newStatusId = 1 + statusBit 
    WHERE newStatusId = 0
    """)
    suspend fun updateRecords(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurveys(surveys: List<NotAtHomeSurveyFormEntity>)

    @Query("DELETE FROM NotAtHomeSurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId")
    suspend fun deleteAllSurveys(parcelNo: Long, uniqueId: String)

    @Query("SELECT * FROM NotAtHomeSurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId")
    suspend fun getAllSurveysForm(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity>

    @Query("SELECT DISTINCT * FROM NotAtHomeSurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId ORDER BY pkId DESC LIMIT 1")
    suspend fun getSurveyForm(parcelNo: Long, uniqueId: String): NotAtHomeSurveyFormEntity

    @Query("SELECT DISTINCT * FROM NotAtHomeSurveyFormEntity WHERE centroidGeom = :centroidGeom ORDER BY pkId DESC LIMIT 1")
    suspend fun getSurveyForm(centroidGeom: String): NotAtHomeSurveyFormEntity

    @Query("SELECT * FROM NotAtHomeSurveyFormEntity WHERE centroidGeom = :centroidGeom ORDER BY pkId")
    suspend fun getAllSurveyFormWrtCentroid(centroidGeom: String): List<NotAtHomeSurveyFormEntity>

    @Query("SELECT * FROM NotAtHomeSurveyFormEntity WHERE parcelNo = :parcelNo AND uniqueId = :uniqueId  and statusBit = 0 and " +
            "visitCount < 2 and interviewStatus = 'Respondent Not Present' GROUP BY subParcelId ORDER BY" +
            " subParcelId,MAX(gpsTimestamp) DESC")
    suspend fun getAllNAHForm(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity>

    @Query("select count(DISTINCT subParcelId) as count from NotAtHomeSurveyFormEntity WHERE" +
            " parcelNo = :parcelNo and uniqueId = :uniqueId and interviewStatus = 'Respondent Not Present' and visitCount != 2 " +
            "and statusBit = 0")
    suspend fun getCountNAHForm(parcelNo: Long, uniqueId: String): Int?

    @Query("SELECT count(visitCount) as count from NotAtHomeSurveyFormEntity WHERE " +
            "parcelNo= :parcelNo  and uniqueId = :uniqueId and subParcelId = :subParcelId")
    suspend fun getSubParcelVisitCount(parcelNo: Long, subParcelId: Int, uniqueId: String): Int

    @Query("UPDATE NotAtHomeSurveyFormEntity set statusBit = :statusBit where " +
            "parcelNo= :parcelNo  and uniqueId = :uniqueId and subParcelId = :subParcelId")
    suspend fun updateSurveyStatusWrtParcel(statusBit: Int, parcelNo: Long, subParcelId: Int, uniqueId: String): Int

    @Query("UPDATE NotAtHomeSurveyFormEntity set statusBit = :statusBit where " +
            "parcelNo= :parcelNo and uniqueId = :uniqueId ")
    suspend fun updateAllSurveyStatusWrtParcel(statusBit: Int, parcelNo: Long, uniqueId: String): Int

    @Query("select distinct parcelOperationValue from NotAtHomeSurveyFormEntity where statusBit = 0 and parcelOperation = 'Merge'")
    suspend fun getAllUniqueMergedParcels(): List<String>

    // Get all unique centroids for updating status of the synced parcels in case of multiple area downloads
    @Query("select distinct centroidGeom from NotAtHomeSurveyFormEntity where statusBit = 0")
    suspend fun getAllUniqueCentroids(): List<String>

    @Query("select distinct surveyId from NotAtHomeSurveyFormEntity where statusBit = 0")
    suspend fun getAllUniquePropertyIds(): List<Long>

    @Query("select distinct parcelOperationValue from NotAtHomeSurveyFormEntity where parcelOperation = 'Merge' and statusBit = 0")
    suspend fun getAllParcelOperationValues(): List<String>


    @Query("DELETE FROM NotAtHomeSurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId")
    suspend fun deleteSavedRecord(parcelNo: Long, uniqueId: String): Int


    @Query("SELECT centroidGeom,surveyId,newStatusId  FROM NotAtHomeSurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId")
    suspend fun getRecord(parcelNo: Long, uniqueId: String): List<RecordDetails>

}