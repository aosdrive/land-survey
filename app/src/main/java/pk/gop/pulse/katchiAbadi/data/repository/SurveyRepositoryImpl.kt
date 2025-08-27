package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import javax.inject.Inject

class SurveyRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : SurveyRepository {

    override fun getSurveyList(showAll: Boolean): Flow<List<SurveyEntity>> {
        val id = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        return if (showAll) {
            db.surveyDao().getSurveysForKachiAbadiShowAll(id)
        } else {
            db.surveyDao().getSurveysForKachiAbadi(id)
        }
    }

    override fun getFilteredSurveyList(value: String, showAll: Boolean): Flow<List<SurveyEntity>> {
        val id = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        return if (showAll) {
            db.surveyDao().getFilterSurveysForKachiAbadiShowAll(id, value)
        } else {
            db.surveyDao().getFilterSurveysForKachiAbadi(id, value)

        }
    }


}