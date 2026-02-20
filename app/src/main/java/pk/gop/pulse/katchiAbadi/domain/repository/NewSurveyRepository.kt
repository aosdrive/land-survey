package pk.gop.pulse.katchiAbadi.domain.repository

import android.content.Context
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.SurveyWithKhewat
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity

interface NewSurveyRepository {
    fun getAllPendingSurveys(): Flow<List<NewSurveyNewEntity>>
    fun getTotalPendingCount(): Flow<Int>

    suspend fun deleteSurvey(survey: NewSurveyNewEntity): Resource<Unit>

    //    suspend fun uploadSurvey(survey: NewSurveyNewEntity): Resource<Unit>
    suspend fun uploadSurvey(context: Context, survey: NewSurveyNewEntity): Resource<Unit>
    suspend fun getOnePendingSurvey(): NewSurveyNewEntity?

    suspend fun getSurveyById(id: Long): NewSurveyNewEntity?
    suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonEntity>


    suspend fun getAllSurveys(): List<NewSurveyNewEntity>
    suspend fun getActiveParcelById(parcelId: Long): ActiveParcelEntity?
}