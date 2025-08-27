package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class SaveSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(surveyFormEntity: SurveyFormEntity): SimpleResource {
        return repository.saveData(surveyFormEntity)
    }
}