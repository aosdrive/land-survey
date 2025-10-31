package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class TaskListResponse(
    val success: Boolean,
    val data: List<TaskItem>,
    val count: Int
)

data class TaskItem(
    val id: Int,
    val assign_Date: String,
    val issue_Type: String,
    val picData: String?,
    val detail: String?,
    val parcel_id: Long,
    val parcelNo: String,
    val mauzaId: Long,
    val status: String,
    val createdOn: String,
    val updatedOn: String?,
    val assignedByUser: String?,
//    @SerializedName("daysToComplete")
//    val daysToComplete: Int? = 0,
)