package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class MauzaBoundaryResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: MauzaBoundaryDto? = null
)

data class MauzaBoundaryDto(
    @SerializedName("mauzaId") val mauzaId: Long,
    @SerializedName("mauzaName") val mauzaName: String? = null,
    @SerializedName("groupId") val groupId: Long? = null,
    @SerializedName("unitId") val unitId: Long? = null,
    @SerializedName("zoneId") val zoneId: Long? = null,
    @SerializedName("circleId") val circleId: Long? = null,
    @SerializedName("area") val area: String? = null,
    @SerializedName("geomWKT") val geomWKT: String? = null,
    @SerializedName("geomType") val geomType: String? = null
)
