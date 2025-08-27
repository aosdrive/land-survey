package pk.gop.pulse.katchiAbadi.domain.use_case.saved

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.repository.SavedRepository
import javax.inject.Inject

class PostSavedRecordUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    suspend operator fun invoke(parcelNo: Long, uniqueId: String): SimpleResource {
        return repository.postSavedData(parcelNo, uniqueId)
    }
}
