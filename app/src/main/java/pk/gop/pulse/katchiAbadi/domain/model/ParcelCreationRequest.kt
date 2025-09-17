package pk.gop.pulse.katchiAbadi.domain.model

import com.google.gson.annotations.SerializedName
import pk.gop.pulse.katchiAbadi.data.remote.post.RetakePicture

data class ParcelCreationRequest(
    @SerializedName("ParcelId") val parcelId: Long,
    @SerializedName("ParcelNo") val parcelNo: String,
    @SerializedName("SubParcelNo") val subParcelNo: String,
    @SerializedName("MauzaId") val mauzaId: Long,
    @SerializedName("Area") val area: String?,
    @SerializedName("GeomWKT") val geomWKT: String,
    @SerializedName("Centroid") val centroid: String,
    @SerializedName("PropertyType") val propertyType: String,
    @SerializedName("OwnershipStatus") val ownershipStatus: String,
    @SerializedName("Variety") val variety: String,
    @SerializedName("CropType") val cropType: String,
    @SerializedName("Crop") val crop: String,
    @SerializedName("Year") val year: String,
    @SerializedName("Remarks") val remarks: String,
    @SerializedName("IsGeometryCorrect") val isGeometryCorrect: Boolean,
    @SerializedName("ParcelOperation") val parcelOperation: String,
    @SerializedName("ParcelOperationValue") val parcelOperationValue: String,
    @SerializedName("AreaName") val areaName: String,
    @SerializedName("Persons") val persons: List<SurveyPersonPost> = emptyList(),
    @SerializedName("Pictures") val pictures: List<RetakePicture> = emptyList()
)
