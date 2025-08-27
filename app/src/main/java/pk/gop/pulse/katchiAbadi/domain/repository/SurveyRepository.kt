package pk.gop.pulse.katchiAbadi.domain.repository

import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import kotlinx.coroutines.flow.Flow

interface SurveyRepository {
    fun getSurveyList(showAll: Boolean): Flow<List<SurveyEntity>>
    fun getFilteredSurveyList(value: String, showAll: Boolean): Flow<List<SurveyEntity>>
}