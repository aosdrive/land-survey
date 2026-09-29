package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class ZoneDto(
    @SerializedName("zone_id")   val zoneId: Long = 0,
    @SerializedName("zone_Name") val zoneName: String = ""
)