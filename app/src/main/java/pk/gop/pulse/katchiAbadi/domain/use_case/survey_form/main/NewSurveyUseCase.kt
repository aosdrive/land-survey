package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.SurveyWithKhewat
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.domain.repository.NewSurveyRepository
import javax.inject.Inject

class NewSurveyUseCase @Inject constructor(
    private val repository: NewSurveyRepository
) {

    fun getAllPendingSurveys(): Flow<List<NewSurveyNewEntity>> {
        return repository.getAllPendingSurveys()
    }

    fun getTotalPendingCount(): Flow<Int> {
        return repository.getTotalPendingCount()
    }

    suspend fun deleteSurvey(survey: NewSurveyNewEntity): Resource<Unit> {
        return repository.deleteSurvey(survey)
    }

    //    suspend fun uploadSurvey(survey: NewSurveyNewEntity): Resource<Unit> {
//        return repository.uploadSurvey(survey)
//    }
    suspend fun uploadSurvey(context: Context, survey: NewSurveyNewEntity): Resource<Unit> {
        return repository.uploadSurvey(context, survey)
    }


    suspend fun getOnePendingSurvey(): NewSurveyNewEntity? {
        return repository.getOnePendingSurvey()
    }

    suspend fun getSurveyById(id: Long): NewSurveyNewEntity? {
        return try {
            // Assuming you have a repository with this method
            repository.getSurveyById(id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonEntity> {
        return repository.getPersonsForSurvey(surveyId)
    }

    suspend fun getAllSurveys() = repository.getAllSurveys()
    suspend fun getActiveParcelById(parcelId: Long) = repository.getActiveParcelById(parcelId)

    // In NewSurveyUseCase
    fun getAllPendingSurveysWithKhewat(): Flow<List<SurveyWithKhewat>> {
        return getAllPendingSurveys().map { surveys ->
            surveys.map { survey ->
                val parcel = getActiveParcelById(survey.parcelId)
                SurveyWithKhewat(
                    survey = survey,
                    khewatInfo = parcel?.khewatInfo ?: "N/A"
                )
            }
        }
    }

}