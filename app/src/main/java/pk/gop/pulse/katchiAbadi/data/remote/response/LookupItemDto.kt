package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class LookupItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?
)