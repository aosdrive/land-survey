package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.KachiAbadiEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity

@Dao
interface TempSurveyLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TempSurveyLogEntity)

    @Query("DELETE FROM temp_survey_log WHERE parcelId = :parcelId AND subParcelNo = :subParcelNo")
    suspend fun deleteLog(parcelId: Long, subParcelNo: String)

    @Query("SELECT * FROM temp_survey_log WHERE parcelId = :parcelId")
    suspend fun getLogsForParcel(parcelId: Long): List<TempSurveyLogEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM temp_survey_log WHERE parcelId = :parcelId AND subParcelNo = :subParcelNo)")
    suspend fun isSubParcelSurveyed(parcelId: Long, subParcelNo: String): Boolean

    @Query("DELETE FROM temp_survey_log")
    suspend fun clearAllLogs()

}
