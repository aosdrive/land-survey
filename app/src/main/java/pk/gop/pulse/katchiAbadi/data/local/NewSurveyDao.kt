package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity

@Dao
interface NewSurveyDao {

    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(surveyEntity: SurveyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurveys(surveys: List<SurveyEntity>)

    // Update
    @Update
    suspend fun updateSurvey(surveyEntity: SurveyEntity)

    @Query("Update SurveyEntity set isAttached = :statusBit where  propertyId = :propertyId")
    suspend fun updateSurveyStatus(statusBit: Boolean, propertyId: Long): Int


//    @Query("Update SurveyEntity set isAttached = 0")
    @Query("UPDATE SurveyEntity SET isAttached = 0 WHERE propertyId IN (SELECT surveyId FROM TempSurveyFormEntity)")
    suspend fun updateAllSurveyStatus(): Int

    @Query("SELECT SurveyEntity.* FROM SurveyEntity LEFT JOIN NotAtHomeSurveyFormEntity ON " +
            "SurveyEntity.propertyNo = NotAtHomeSurveyFormEntity.propertyNumber " +
            "WHERE SurveyEntity.kachiAbadiId = :kachiAbadiId AND SurveyEntity.isAttached = 0 " +
            "AND NotAtHomeSurveyFormEntity.propertyNumber IS NULL")
    fun getSurveysForKachiAbadi(kachiAbadiId: Long): Flow<List<SurveyEntity>>


    @Query("SELECT SurveyEntity.* FROM SurveyEntity LEFT JOIN NotAtHomeSurveyFormEntity ON " +
            "SurveyEntity.propertyNo = NotAtHomeSurveyFormEntity.propertyNumber " +
            "WHERE SurveyEntity.kachiAbadiId = :kachiAbadiId " +
            "AND NotAtHomeSurveyFormEntity.propertyNumber IS NULL")
    fun getSurveysForKachiAbadiShowAll(kachiAbadiId: Long): Flow<List<SurveyEntity>>

    @Query("SELECT SurveyEntity.* FROM SurveyEntity LEFT JOIN NotAtHomeSurveyFormEntity ON " +
            "SurveyEntity.propertyNo = NotAtHomeSurveyFormEntity.propertyNumber " +
            "WHERE SurveyEntity.kachiAbadiId = :kachiAbadiId AND SurveyEntity.isAttached = 0 " +
            "AND NotAtHomeSurveyFormEntity.propertyNumber IS NULL AND " +
            "(SurveyEntity.propertyNo LIKE '%' || :value || '%' COLLATE NOCASE " +
            "OR SurveyEntity.name LIKE '%' || :value || '%' COLLATE NOCASE " +
            "OR SurveyEntity.cnic LIKE '%' || :value || '%' COLLATE NOCASE " +
            "OR SurveyEntity.fname LIKE '%' || :value || '%' COLLATE NOCASE)"
    )
    fun getFilterSurveysForKachiAbadi(kachiAbadiId: Long, value: String?): Flow<List<SurveyEntity>>

    @Query("SELECT SurveyEntity.* FROM SurveyEntity LEFT JOIN NotAtHomeSurveyFormEntity ON " +
            "SurveyEntity.propertyNo = NotAtHomeSurveyFormEntity.propertyNumber " +
            "WHERE SurveyEntity.kachiAbadiId = :kachiAbadiId " +
            "AND NotAtHomeSurveyFormEntity.propertyNumber IS NULL AND " +
            "(SurveyEntity.propertyNo LIKE '%' || :value || '%' COLLATE NOCASE " +
            "OR SurveyEntity.name LIKE '%' || :value || '%' COLLATE NOCASE " +
            "OR SurveyEntity.cnic LIKE '%' || :value || '%' COLLATE NOCASE " +
            "OR SurveyEntity.fname LIKE '%' || :value || '%' COLLATE NOCASE)"
    )
    fun getFilterSurveysForKachiAbadiShowAll(kachiAbadiId: Long, value: String?): Flow<List<SurveyEntity>>

    // Delete
    @Delete
    suspend fun deleteSurvey(surveyEntity: SurveyEntity)

    @Query("DELETE FROM SurveyEntity")
    suspend fun deleteAllSurveys()

    @Query("DELETE FROM SurveyEntity WHERE kachiAbadiId = :abadiId")
    suspend fun deleteAllSurveysWrtArea(abadiId: Long)

    @Query("SELECT * FROM new_surveys WHERE pkId = :id")
    suspend fun getSurveyById(id: Long): NewSurveyNewEntity?
}