package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.temp

import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class GetAllTempSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(parcelNo: Long): List<TempSurveyFormEntity> {
        return repository.getAllTempData(parcelNo)
    }
}
