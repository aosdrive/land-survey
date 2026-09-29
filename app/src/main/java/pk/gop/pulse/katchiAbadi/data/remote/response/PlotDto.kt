package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class PlotDto(
    @SerializedName("plot_id")     val plotId: Long = 0,
    @SerializedName("plot_Name")   val plotName: String = "",
    @SerializedName("block_id")    val blockId: Long = 0
)