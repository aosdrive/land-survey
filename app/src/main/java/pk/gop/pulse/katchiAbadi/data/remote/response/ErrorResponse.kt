package pk.gop.pulse.katchiAbadi.data.remote.response

data class ErrorResponse(
    val message: String? = null,
    val errorCode: String? = null,
    val statusCode: Int? = null,
    val error: String? = null,
    val title: String? = null
)