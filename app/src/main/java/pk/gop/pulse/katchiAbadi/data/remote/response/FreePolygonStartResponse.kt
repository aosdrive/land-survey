package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class FreePolygonStartResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("surveyedCount") val surveyedCount: Int = 0,
    @SerializedName("deactivatedCount") val deactivatedCount: Int = 0
)
