package pk.gop.pulse.katchiAbadi.common

import android.widget.Button
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails

interface SavedItemClickListener {
    fun onUploadItemClicked(survey: SurveyMergeDetails, uploadButton: Button)
    fun onDeleteItemClicked(survey: SurveyMergeDetails)
    fun onViewItemClicked(survey: SurveyMergeDetails)
}