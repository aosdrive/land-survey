package pk.gop.pulse.katchiAbadi.data.remote.request

data class UpdatePasswordRequest(
    val cnic: String,
    val password: String,
)
