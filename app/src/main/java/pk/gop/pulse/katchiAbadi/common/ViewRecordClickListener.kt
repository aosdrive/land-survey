package pk.gop.pulse.katchiAbadi.common

import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity

interface ViewRecordClickListener {
    fun onViewImagesClicked(survey: SurveyFormEntity)
}