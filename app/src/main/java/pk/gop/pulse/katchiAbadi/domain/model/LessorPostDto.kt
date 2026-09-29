package pk.gop.pulse.katchiAbadi.domain.model

import com.google.gson.annotations.SerializedName

data class LessorPostDto(
    @SerializedName("lessorId")
    val lessorId: Int,           // 0 if ad-hoc, real Id otherwise

    @SerializedName("lessorName")
    val lessorName: String,

    @SerializedName("lessorCode")
    val lessorCode: String,

    @SerializedName("lessorType")
    val lessorType: String
)
