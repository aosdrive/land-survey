package pk.gop.pulse.katchiAbadi.data.local

import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity

data class SurveyWithKhewat(
    val survey: NewSurveyNewEntity,
    val khewatInfo: String
)