package pk.gop.pulse.katchiAbadi.domain.repository

import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.data.remote.response.ApiResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaDetail
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.data.remote.response.Settings

interface MenuRepository {
    suspend fun syncAndSaveData(
        mauzaId: Long,
        abadiId: Long,
        mauzaName: String,
        abadiName: String
    ): SimpleResource

    suspend fun mouzaAssignedData(): ResourceSealed<MouzaAssignedDto, Info>
    suspend fun fetchMauzaSyncData(): ResourceSealed<List<MauzaDetail>, Settings>
}