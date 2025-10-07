package pk.gop.pulse.katchiAbadi.data.local

import com.google.gson.annotations.SerializedName

data class TaskSubmitDto (
    @SerializedName("AssignDate")
    val assignDate: String,
    @SerializedName("IssueType")
    val issueType: String,
    @SerializedName("Images")
    val images: List<String>,
    @SerializedName("Detail")
    val detail: String,
    @SerializedName("ParcelId")
    val parcelId: Long,
    @SerializedName("ParcelNo")
    val parcelNo: String = "",
    @SerializedName("MauzaId")
    val mauzaId: Long,
    @SerializedName("AssignedByUserId")
    val assignedByUserId: Long,
    @SerializedName("AssignedToUserId")
    val assignedToUserId: Long
)