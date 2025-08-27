package pk.gop.pulse.katchiAbadi.domain.use_case.not_at_home

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.NAHRepository
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class NAHSaveUseCase @Inject constructor(
    private val repository: NAHRepository
) {
    suspend operator fun invoke(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        return repository.saveNotAtHomeData(notAtHomeSurveyFormEntity)
    }
}