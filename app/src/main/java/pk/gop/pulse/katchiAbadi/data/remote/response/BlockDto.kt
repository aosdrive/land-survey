package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class BlockDto(
    @SerializedName("block_id")    val blockId: Long = 0,
    @SerializedName("block_Name")  val blockName: String = "",
    @SerializedName("farm_id")     val farmId: Long = 0
)