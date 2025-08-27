package pk.gop.pulse.katchiAbadi.domain.use_case.saved

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails
import pk.gop.pulse.katchiAbadi.domain.repository.SavedRepository
import javax.inject.Inject

class DeleteSavedRecordUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    suspend operator fun invoke(survey: SurveyMergeDetails): SimpleResource {
        return repository.deleteSavedRecord(survey)
    }
}
