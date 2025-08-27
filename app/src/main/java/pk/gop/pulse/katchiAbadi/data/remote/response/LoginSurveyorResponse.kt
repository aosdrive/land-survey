package pk.gop.pulse.katchiAbadi.data.remote.response

data class LoginSurveyorResponse(
    val userId: Long,
    val name: String,
    val token: String,
    val message: String
)
