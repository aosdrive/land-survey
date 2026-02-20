package pk.gop.pulse.katchiAbadi.data.remote.request

import com.google.gson.annotations.SerializedName

data class OnboardingUploadDto(
    @SerializedName("CNIC")
    val cnic: String,
    @SerializedName("Contact")
    val contact: String,
    @SerializedName("ImageBase64")
    val imageBase64: String?,
    @SerializedName("FingerprintStatus")
    val fingerprintStatus: String
)

data class OnboardingResponse(
    @SerializedName("Success")
    val success: Boolean,
    @SerializedName("Id")
    val id: Int
)