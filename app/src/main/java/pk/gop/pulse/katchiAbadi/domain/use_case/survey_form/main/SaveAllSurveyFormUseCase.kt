package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class SaveAllSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(surveyFormEntityList: List<SurveyFormEntity>): SimpleResource {
        return repository.saveAllData(surveyFormEntityList)
    }
}