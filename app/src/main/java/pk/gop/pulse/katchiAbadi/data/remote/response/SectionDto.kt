package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class SectionDto(
    @SerializedName("section_id")     val sectionId: Long = 0,
    @SerializedName("section_Name")   val sectionName: String = "",
    @SerializedName("division_id")    val divisionId: Long = 0
)