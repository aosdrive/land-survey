package pk.gop.pulse.katchiAbadi.domain.model

data class UserResponse(
    val id: Long,
    val fullName: String,
    val userName: String,
    val cnic: String,
    val mobileNo: String,
    val vendorName: String?,
    val roleName: String?,
    val isNotActive: Boolean,
    val createdOn: String
)
