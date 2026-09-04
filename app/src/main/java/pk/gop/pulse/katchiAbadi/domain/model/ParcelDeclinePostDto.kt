package pk.gop.pulse.katchiAbadi.domain.model

import com.google.gson.annotations.SerializedName

data class ParcelDeclinePostDto(
    @SerializedName("DeclineGuid")       val declineGuid: String,
    @SerializedName("MauzaId")           val mauzaId: Long,
    @SerializedName("MauzaName")         val mauzaName: String,
    @SerializedName("AreaName")          val areaName: String,
    @SerializedName("ParcelId")          val parcelId: Long?,
    @SerializedName("ParcelNo")          val parcelNo: String,
    @SerializedName("SubParcelNo")       val subParcelNo: String,
    @SerializedName("ParcelStatus")      val parcelStatus: String,
    @SerializedName("KhewatInfo")        val khewatInfo: String,
    @SerializedName("Remarks")           val remarks: String,
    @SerializedName("GeomWKT")           val geomWKT: String?,
    @SerializedName("Centriod")          val centroid: String?,
    @SerializedName("CalculatedArea")    val calculatedArea: Double?,
    @SerializedName("Latitude")          val latitude: Double?,
    @SerializedName("Longitude")         val longitude: Double?,
    @SerializedName("Bearing")           val bearing: Float?,
    @SerializedName("Timestamp")         val timestamp: Long?,
    @SerializedName("DeviceCreatedDate") val deviceCreatedDate: String?,
    @SerializedName("Pic1")              val pic1: String,
    @SerializedName("Pic2")              val pic2: String,
    @SerializedName("Pic3")              val pic3: String
)