package pk.gop.pulse.katchiAbadi.domain.use_case.menu

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.repository.MenuRepository
import javax.inject.Inject

class GetSyncDataUseCase @Inject constructor(
    private val repository: MenuRepository
) {
    suspend operator fun invoke(
        mauzaId: Long,
        abadiId: Long,
        mauzaName: String,
        abadiName: String
    ): SimpleResource {
        return repository.syncAndSaveData(mauzaId, abadiId, mauzaName, abadiName)
    }
}