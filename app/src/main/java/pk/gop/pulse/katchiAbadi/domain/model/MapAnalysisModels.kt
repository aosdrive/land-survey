package pk.gop.pulse.katchiAbadi.domain.model

import com.google.gson.annotations.SerializedName

data class LookupItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?
) {
    override fun toString(): String = name ?: "Unnamed"
}

data class BoundaryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("areaAcres") val areaAcres: Double?,
    @SerializedName("geometryWKT") val geometryWKT: String?
)

data class SugarcaneFeatureDto(
    @SerializedName("id") val id: Int,
    @SerializedName("mouza") val mouza: String?,
    @SerializedName("circleName") val circleName: String?,
    @SerializedName("unitName") val unitName: String?,
    @SerializedName("groupName") val groupName: String?,
    @SerializedName("areaAcres") val areaAcres: Double?,
    @SerializedName("computedAreaAcres") val computedAreaAcres: Double?,
    @SerializedName("year") val year: String?,
    @SerializedName("geometryWKT") val geometryWKT: String?
)

data class SugarcaneResponseDto(
    @SerializedName("mauzaId") val mauzaId: Int,
    @SerializedName("featureCount") val featureCount: Int,
    @SerializedName("totalAreaAcres") val totalAreaAcres: Double?,
    @SerializedName("features") val features: List<SugarcaneFeatureDto>?
)

data class GrowerFeatureDto(
    @SerializedName("growerCode") val growerCode: String?,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("lastName") val lastName: String?,
    @SerializedName("mobile") val mobile: String?,
    @SerializedName("nic") val nic: String?,
    @SerializedName("khewatInfo") val khewatInfo: String?,
    @SerializedName("muraba") val muraba: String?,
    @SerializedName("cropType") val cropType: String?,
    @SerializedName("variety") val variety: String?,
    @SerializedName("mauzaName") val mauzaName: String?,
    @SerializedName("ownershipType") val ownershipType: String?,
    @SerializedName("parcelNo") val parcelNo: String?,
    @SerializedName("subParcelNo") val subParcelNo: String?,
    @SerializedName("area") val area: Double?,
    @SerializedName("areaAcres") val areaAcres: Double?,
    @SerializedName("geometryWKT") val geometryWKT: String?
)


data class AnalyticsParcelDto(
    @SerializedName("id") val id: Int,
    @SerializedName("parcelId") val parcelId: Int,
    @SerializedName("mauzaId") val mauzaId: Int,
    @SerializedName("parcelNo") val parcelNo: String?,
    @SerializedName("khewat") val khewat: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("growerName") val growerName: String?,
    @SerializedName("growerCode") val growerCode: String?,
    @SerializedName("growerMobile") val growerMobile: String?,
    @SerializedName("cropType") val cropType: String?,
    @SerializedName("parcelAcres") val parcelAcres: Double?,
    @SerializedName("caneAcres") val caneAcres: Double?,
    @SerializedName("canePct") val canePct: Double?,
    @SerializedName("missingAcres") val missingAcres: Double?,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lng") val lng: Double?,
    @SerializedName("computedAcres") val computedAcres: Double?,
    @SerializedName("geometryWKT") val geometryWKT: String?
)

data class AnalyticsParcelsResponse(
    @SerializedName("mauzaId") val mauzaId: Int,
    @SerializedName("featureCount") val featureCount: Int,
    @SerializedName("features") val features: List<AnalyticsParcelDto>?
)