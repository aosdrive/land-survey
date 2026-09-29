package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class FarmDto(
    @SerializedName("farm_id")     val farmId: Long = 0,
    @SerializedName("farm_Name")   val farmName: String = "",
    @SerializedName("section_id")  val sectionId: Long = 0
)