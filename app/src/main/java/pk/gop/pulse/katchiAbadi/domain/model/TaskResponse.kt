package pk.gop.pulse.katchiAbadi.domain.model

data class TaskResponse(
    val success: Boolean,
    val message: String,
    val taskId: Long?
)