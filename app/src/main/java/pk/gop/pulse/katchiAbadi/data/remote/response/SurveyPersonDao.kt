package pk.gop.pulse.katchiAbadi.data.remote.response

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity

@Dao
interface SurveyPersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: SurveyPersonEntity)

    @Query("SELECT * FROM survey_persons WHERE surveyId = :surveyId")
    suspend fun getPersonsBySurvey(surveyId: Long): List<SurveyPersonEntity>
    @Query("SELECT * FROM survey_persons WHERE mauzaId = :mauzaId")
    suspend fun getPersonsForCurrentMouza(mauzaId: Long): List<SurveyPersonEntity>
   @Query("SELECT * FROM survey_persons ")
    suspend fun getallPersons(): List<SurveyPersonEntity>
    @Insert(onConflict = REPLACE)
    suspend fun insertAll(persons: List<SurveyPersonEntity>)
    @Query("DELETE FROM survey_persons WHERE mauzaId = :mauzaId")
    suspend fun deleteByMauzaId(mauzaId: Long)

    @Query("SELECT * FROM survey_persons WHERE surveyId = :surveyId")
    suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonEntity>



}