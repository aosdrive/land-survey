package pk.gop.pulse.katchiAbadi.data.remote.response


import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity

@Dao
interface NewSurveyNewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: NewSurveyNewEntity): Long

    @Query("SELECT * FROM new_surveys WHERE pkId = :id")
    suspend fun getSurveyById(id: Long): NewSurveyNewEntity?

    @Query("SELECT COUNT(pkId ) as totalCount FROM new_surveys WHERE statusBit = 0")
    suspend fun totalPendingCount(): Int
//   @Query("SELECT COUNT(pkId ) as totalCount FROM new_surveys WHERE statusBit = 0")
//   fun liveTotalPendingCount(): LiveData<Int>
//@Query("SELECT * FROM new_surveys WHERE statusBit = 0")
//   fun getAllPendingSurveysLiveData(): LiveData<NewSurveyNewEntity>



    @Query("SELECT COUNT(pkId) FROM new_surveys WHERE statusBit = 0")
    fun liveTotalPendingCount(): Flow<Int> // ✅ Use Flow instead of LiveData


    @Query("SELECT * FROM new_surveys WHERE parcelId = :parcelId AND statusBit = 0")
    fun getCompleteRecord(parcelId: Long ): List<NewSurveyNewEntity>

    @Query("SELECT * FROM new_surveys WHERE statusBit = 0 LIMIT 1")
    suspend fun getOnePendingSurvey(): NewSurveyNewEntity?



    @Query("SELECT * FROM new_surveys WHERE statusBit = 0")
    fun getAllPendingSurveys(): Flow<List<NewSurveyNewEntity>>

    @Query("SELECT * FROM new_surveys")
    fun getAllSurveys(): List<NewSurveyNewEntity>

    @Delete
    suspend fun deleteSurvey(survey: NewSurveyNewEntity)

    @Update
    suspend fun updateSurvey(survey: NewSurveyNewEntity)

    @Query("UPDATE new_surveys SET statusBit = 1 WHERE pkId = :pkId")
    suspend fun markAsUploaded(pkId: Long)

    @Query("SELECT * FROM new_surveys WHERE parcelId = :parcelId AND (statusBit = 0 OR statusBit IS NULL)")
    suspend fun getSurveysByParcelId(parcelId: Long): List<NewSurveyNewEntity>

    @Query("SELECT * FROM active_parcels WHERE parcelNo = :parcelNo AND mauzaId = :mauzaId AND areaAssigned = :areaName AND isActivate = 1")
    suspend fun getActiveParcelsByParcelNumber(parcelNo: Long, mauzaId: Long, areaName: String): List<ActiveParcelEntity>

}
