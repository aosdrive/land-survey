package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class NotAtHomeSaveUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        return repository.saveNotAtHomeData(notAtHomeSurveyFormEntity)
    }
}