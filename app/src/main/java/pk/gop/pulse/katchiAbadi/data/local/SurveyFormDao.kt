package pk.gop.pulse.katchiAbadi.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.RecordDetails
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails

@Dao
interface SurveyFormDao {

    @Query("""
    UPDATE SurveyFormEntity 
    SET parcelNo = parcelId, 
        newStatusId = 1 + statusBit 
    WHERE newStatusId = 0
    """)
    suspend fun updateRecords(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(surveyFormEntity: SurveyFormEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurveys(surveys: List<SurveyFormEntity>)

    @Query("SELECT * FROM SurveyFormEntity WHERE statusBit = 0  ORDER BY `pkId` DESC")
    fun getAllSurveysForm(): Flow<List<SurveyFormEntity>>

    @Query("SELECT * FROM SurveyFormEntity WHERE statusBit = :statusBit ORDER BY `pkId` DESC LIMIT 1")
    fun getByStatusAndLimit(statusBit: Int): LiveData<SurveyFormEntity>

    @Query("Update SurveyFormEntity set statusBit = 1 where parcelNo = :parcelNo and uniqueId = :uniqueId and pkId = :pkId")
    suspend fun updateSurveyStatusWrtParcel(parcelNo: Long, uniqueId: String, pkId: Long): Int

    @Delete
    suspend fun deleteSurvey(surveyFormEntity: SurveyFormEntity)

    @Query("SELECT COUNT(DISTINCT uniqueId) as totalCount FROM SurveyFormEntity WHERE statusBit = 0")
    suspend fun totalPendingCount(): Int

    @Query("SELECT count(*) FROM SurveyFormEntity  WHERE statusBit = 0")
    fun totalPendingRecordCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT parcelNo) as totalCount FROM SurveyFormEntity WHERE statusBit = 0")
    fun totalDistinctPendingRecordCount(): Flow<Int>

    @Query("SELECT kachiAbadiId,parcelNo, parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel, parcelOperationValue, " +
            "GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
                "FROM SurveyFormEntity WHERE statusBit = 0 GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC")
    fun getSavedRecordsDetails(): LiveData<List<SurveyMergeDetails>>
//
//    @Query(
//        "SELECT kachiAbadiId,parcelNo, parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel, parcelOperationValue," +
//                " GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
//                "FROM SurveyFormEntity WHERE statusBit = 1 GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC"
//    )
    @Query(
        "SELECT kachiAbadiId,parcelNo, parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel, parcelOperationValue," +
                " GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
                "FROM SurveyFormEntity GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC"
    )
    fun getSentRecordsDetails(): LiveData<List<SurveyMergeDetails>>

    @Query("SELECT * FROM SurveyFormEntity WHERE parcelNo = :parcelNo AND uniqueId =:uniqueId " +
            "AND statusBit = 0")
    fun getCompleteRecord(parcelNo: Long, uniqueId: String): List<SurveyFormEntity>

    @Query("SELECT centroidGeom,surveyId,newStatusId  FROM SurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId")
    fun getRecord(parcelNo: Long, uniqueId: String): List<RecordDetails>

    @Query("SELECT COUNT(DISTINCT parcelNo) as totalCount FROM SurveyFormEntity WHERE statusBit = 0")
    fun totalSavedRecordCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT uniqueId) as totalCount FROM SurveyFormEntity WHERE statusBit = 0")
    fun liveTotalPendingCount(): LiveData<Int>

    @Query("DELETE FROM SurveyFormEntity WHERE parcelNo = :parcelNo and uniqueId = :uniqueId")
    fun deleteSavedRecord(parcelNo: Long, uniqueId: String): Int

    @Query("SELECT COUNT(DISTINCT uniqueId) as totalCount FROM SurveyFormEntity")
//    @Query("SELECT COUNT(DISTINCT uniqueId) as totalCount FROM SurveyFormEntity WHERE statusBit = 1")
    fun liveTotalSentCount(): LiveData<Int>

    @Query("SELECT kachiAbadiId,parcelNo, parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel,parcelOperationValue," +
            " GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
            "FROM SurveyFormEntity WHERE statusBit = :statusBit GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC LIMIT 1")
    fun getSavedRecordsByStatusAndLimit(statusBit: Int): LiveData<SurveyMergeDetails>

    @Query(
        "SELECT kachiAbadiId,parcelNo,parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel, parcelOperationValue," +
                " GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
                "FROM SurveyFormEntity WHERE statusBit = 0 GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC")
    fun getListSavedRecordsDetails(): List<SurveyMergeDetails>


    @Query(
        "SELECT kachiAbadiId,parcelNo,parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel, parcelOperationValue," +
                " GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
                "FROM SurveyFormEntity WHERE statusBit = 0 GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC LIMIT 1")
    suspend fun getListSavedRecordsDetailsByLimit(): SurveyMergeDetails

    @Query(
        "SELECT kachiAbadiId,parcelNo,parcelOperation, GROUP_CONCAT(DISTINCT subParcelId) as subParcel, parcelOperationValue," +
                " GROUP_CONCAT(DISTINCT DATE(gpsTimestamp)) as gpsTimestamp, latitude, longitude, uniqueId, isRevisit, centroidGeom " +
                "FROM SurveyFormEntity GROUP BY parcelNo, uniqueId ORDER BY MAX(gpsTimestamp) DESC"
    )
    fun getAllSurveyRecordsDetails(): List<SurveyMergeDetails>

    @Query("Update SurveyFormEntity set statusBit = 0")
    suspend fun updateSurveyStatusUnSent(): Int

    @Query("select distinct parcelOperationValue from SurveyFormEntity where statusBit = 0 and parcelOperation = 'Merge'")
    suspend fun getAllUniqueMergedParcels(): List<String>

    // Get all unique centroids for updating status of the synced parcels in case of multiple area downloads
    @Query("select distinct centroidGeom from SurveyFormEntity where statusBit = 0")
    suspend fun getAllUniqueCentroids(): List<String>

    @Query("select distinct surveyId from SurveyFormEntity where statusBit = 0")
    suspend fun getAllUniquePropertyIds(): List<Long>

    @Query("select distinct parcelOperationValue from SurveyFormEntity where parcelOperation = 'Merge' and statusBit = 0")
    suspend fun getAllParcelOperationValues(): List<String>

    // Recovery Query
    @Query("SELECT * FROM SurveyFormEntity WHERE parcelNo = :parcelNo AND uniqueId =:uniqueId")
    fun getCompleteSavedRecord(parcelNo: Long, uniqueId: String): List<SurveyFormEntity>

    // Recovery Query
    @Query("SELECT COUNT(DISTINCT uniqueId) as totalCount FROM SurveyFormEntity")
    suspend fun totalCount(): Int

    // Recovery Query
    @Query("""SELECT COUNT(DISTINCT qrCode) AS totalCount
            FROM (
                SELECT qrCode FROM SurveyFormEntity  
                UNION ALL
                SELECT qrCode FROM TempSurveyFormEntity  
                UNION ALL
                SELECT qrCode FROM NotAtHomeSurveyFormEntity 
            ) AS combinedTables WHERE qrCode =:qrCode""")
    suspend fun checkQRCode(qrCode: String): Int

    // Query 1: Check if the qrCode exists in SurveyFormEntity with specific parcelNo and subParcelId
    @Query("""
        SELECT COUNT(*) 
        FROM (
            SELECT pkId 
            FROM SurveyFormEntity 
            WHERE qrCode = :qrCode AND parcelNo = :parcelNo AND subParcelId = :subParcelId 
            ORDER BY pkId DESC 
            LIMIT 1
        ) AS LimitedRows;
""")
    suspend fun checkQRCodeInSurveyForm(qrCode: String, parcelNo: Long, subParcelId: Int): Int


    // Query 1: Check if the qrCode exists in NotAtHomeSurveyFormEntity with specific parcelNo and subParcelId
    @Query("""
    SELECT COUNT(*) 
    FROM NotAtHomeSurveyFormEntity 
    WHERE qrCode = :qrCode AND parcelNo = :parcelNo AND subParcelId = :subParcelId
""")
    suspend fun checkQRCodeInNotAtHome(qrCode: String, parcelNo: Long, subParcelId: Int): Int

    // Query 2: Check if the qrCode exists in any of the tables
    @Query("""
    SELECT COUNT(*) 
    FROM (
        SELECT qrCode FROM SurveyFormEntity WHERE qrCode = :qrCode
        UNION ALL
        SELECT qrCode FROM TempSurveyFormEntity WHERE qrCode = :qrCode
        UNION ALL
        SELECT qrCode FROM NotAtHomeSurveyFormEntity WHERE qrCode = :qrCode
    ) AS combinedTables
""")
    suspend fun checkQRCodeInAllTables(qrCode: String): Int

}