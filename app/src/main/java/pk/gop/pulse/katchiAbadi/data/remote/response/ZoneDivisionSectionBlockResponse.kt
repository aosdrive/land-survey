package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class ZoneDivisionSectionBlockResponse(
    @SerializedName("zones")     val zones: List<ZoneDto> = emptyList(),
    @SerializedName("divisions") val divisions: List<DivisionDto> = emptyList(),
    @SerializedName("sections")  val sections: List<SectionDto> = emptyList(),
    @SerializedName("farms")     val farms: List<FarmDto> = emptyList(),
    @SerializedName("blocks")    val blocks: List<BlockDto> = emptyList(),
    @SerializedName("plots")     val plots: List<PlotDto> = emptyList()
)