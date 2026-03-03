package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.SowingPersonEntity

@Dao
interface SowingPersonDao {

    @Insert
    suspend fun insertAll(entities: List<SowingPersonEntity>)

    @Query("SELECT * FROM sowing_person WHERE surveyId = :surveyId")
    suspend fun getBySurveyId(surveyId: Long): List<SowingPersonEntity>

    @Query("DELETE FROM sowing_person WHERE surveyId = :surveyId")
    suspend fun deleteBySurvey(surveyId: Long)
}