package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity

@Dao
interface TempSurveyFormDao {

    @Query("""
    UPDATE TempSurveyFormEntity 
    SET parcelNo = parcelId, 
        newStatusId = 1 + statusBit 
    WHERE newStatusId = 0
    """)
    suspend fun updateRecords(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(tempSurveyFormEntity: TempSurveyFormEntity)

    @Query("DELETE FROM TempSurveyFormEntity WHERE parcelNo = :parcelNo")
    suspend fun deleteAllSurveys(parcelNo: Long)

    @Query("SELECT * FROM TempSurveyFormEntity WHERE parcelNo = :parcelNo")
    fun getAllSurveysForm(parcelNo: Long): List<TempSurveyFormEntity>

}