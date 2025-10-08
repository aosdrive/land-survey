package pk.gop.pulse.katchiAbadi.data.local

data class TaskUpdateDto(
    val taskId: Int,
    val status: String,
    val feedback: String,
    val updatedByUserId: Long
)

data class TaskUpdateResponse(
    val success: Boolean,
    val message: String
)