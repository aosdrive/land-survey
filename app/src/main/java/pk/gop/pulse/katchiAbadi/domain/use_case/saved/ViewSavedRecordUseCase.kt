package pk.gop.pulse.katchiAbadi.domain.use_case.saved

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SavedRepository
import javax.inject.Inject

class ViewSavedRecordUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    suspend operator fun invoke(parcelNo: Long, uniqueId: String): List<SurveyFormEntity> {
        return repository.viewSavedData(parcelNo, uniqueId)
    }
    suspend operator fun invoke(parcelId: Long): List<NewSurveyNewEntity> {
        return repository.viewSavedDataNew(parcelId)
    }
}
