package pk.gop.pulse.katchiAbadi.common

import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity

interface SubParcelItemClickListener {
    fun onSelectItemClicked(item: SubParcel)
}

interface RejectedSubParcelItemClickListener {
    fun onSelectItemClicked(item: RejectedSubParcel)
}

interface NAHSubParcelItemClickListener {
    fun onSelectItemClicked(item: NotAtHomeSurveyFormEntity)
}
