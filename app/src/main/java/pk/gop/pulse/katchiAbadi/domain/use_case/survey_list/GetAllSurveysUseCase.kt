package pk.gop.pulse.katchiAbadi.domain.use_case.survey_list

import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllSurveysUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    operator fun invoke(showAll: Boolean): Flow<List<SurveyEntity>> {
        return repository.getSurveyList(showAll)
    }
}
