package pk.gop.pulse.katchiAbadi.domain.model

import com.google.gson.annotations.SerializedName

data class ParcelAcceptPostDto(
    @SerializedName("AcceptGuid")        val acceptGuid: String,
    @SerializedName("MauzaId")           val mauzaId: Long,
    @SerializedName("MauzaName")         val mauzaName: String,
    @SerializedName("AreaName")          val areaName: String,
    @SerializedName("ParcelId")          val parcelId: Long,
    @SerializedName("ParcelNo")          val parcelNo: String,
    @SerializedName("ParcelStatus")      val parcelStatus: String,
    @SerializedName("KhewatInfo")        val khewatInfo: String,
    @SerializedName("Remarks")           val remarks: String,
    @SerializedName("Latitude")          val latitude: Double?,
    @SerializedName("Longitude")         val longitude: Double?,
    @SerializedName("DeviceCreatedDate") val deviceCreatedDate: String?
)

data class ParcelAcceptResponse(
    @SerializedName(value = "Message", alternate = ["message"]) val message: String?,
    @SerializedName(value = "Saved", alternate = ["saved"]) val saved: Int?,
    @SerializedName(value = "Skipped", alternate = ["skipped"]) val skipped: Int?,
    @SerializedName(value = "Results", alternate = ["results"]) val results: List<ParcelAcceptResult>?
)

data class ParcelAcceptResult(
    @SerializedName(value = "AcceptGuid", alternate = ["acceptGuid"]) val acceptGuid: String?,
    /** "Saved", "Duplicate" or "ParcelNotFound". */
    @SerializedName(value = "Status", alternate = ["status"]) val status: String?
)
