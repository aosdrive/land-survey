package pk.gop.pulse.katchiAbadi.data.remote.request

data class OtpVerificationRequest(
    val cnic: String,
    val otp: Int,
)
