package pk.gop.pulse.katchiAbadi.domain.use_case.menu

import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.domain.repository.MenuRepository
import javax.inject.Inject

class GetMouzaDataUseCase @Inject constructor(
    private val repository: MenuRepository
) {
    suspend operator fun invoke(): ResourceSealed<MouzaAssignedDto, Info> {
        return repository.mouzaAssignedData()
    }
}