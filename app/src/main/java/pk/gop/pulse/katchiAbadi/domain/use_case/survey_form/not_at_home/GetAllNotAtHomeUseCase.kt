package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home

import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class GetAllNotAtHomeUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity> {
        return repository.getAllNotAtHomeData(parcelNo,uniqueId)
    }
}
