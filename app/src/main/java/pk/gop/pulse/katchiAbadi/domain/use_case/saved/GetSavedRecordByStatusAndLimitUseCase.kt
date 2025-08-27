package pk.gop.pulse.katchiAbadi.domain.use_case.saved

import androidx.lifecycle.LiveData
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails
import pk.gop.pulse.katchiAbadi.domain.repository.SavedRepository
import javax.inject.Inject

class GetSavedRecordByStatusAndLimitUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    operator fun invoke(statusBit: Int): LiveData<SurveyMergeDetails> {
        return repository.getSavedRecordByStatusAndLimit(statusBit)
    }
}
