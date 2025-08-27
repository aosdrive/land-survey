package pk.gop.pulse.katchiAbadi.data.remote.response

data class LoginDto(
    val name: String,
    val token: String,
    val userID: Long,
    val mauzaId: Long,
    val mauzaName: String,
    val cnic: String,
    val changePassword: Boolean
)