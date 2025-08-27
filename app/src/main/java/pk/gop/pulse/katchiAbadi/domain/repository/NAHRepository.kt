package pk.gop.pulse.katchiAbadi.domain.repository

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity

interface NAHRepository {
    suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource
}
