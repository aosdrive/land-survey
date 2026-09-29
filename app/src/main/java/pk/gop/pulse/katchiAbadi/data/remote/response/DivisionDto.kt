package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class DivisionDto(
    @SerializedName("division_id")    val divisionId: Long = 0,
    @SerializedName("division_Name")  val divisionName: String = "",
    @SerializedName("zone_id")        val zoneId: Long = 0
)