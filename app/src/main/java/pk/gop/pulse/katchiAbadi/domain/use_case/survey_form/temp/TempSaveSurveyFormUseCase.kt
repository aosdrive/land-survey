package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.temp

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class TempSaveSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(tempSurveyFormEntity: TempSurveyFormEntity): SimpleResource {
        return repository.saveTempData(tempSurveyFormEntity)
    }
}