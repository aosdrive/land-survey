package pk.gop.pulse.katchiAbadi.common

import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity

interface SurveyItemClickListener {
    fun onSurveyItemClicked(survey: SurveyEntity)
}
