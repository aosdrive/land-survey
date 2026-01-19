package pk.gop.pulse.katchiAbadi.data.remote.request

data class LoginRequest(
    val cnic: String,
    val password: String,
    val Mode: String = "Android",  // Add this line
    val appVersion: String? = null // Add this

)
