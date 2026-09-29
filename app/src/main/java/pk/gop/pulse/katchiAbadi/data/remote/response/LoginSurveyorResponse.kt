package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class LoginSurveyorResponse(
    val userId: Long,
    val name: String,
    val token: String,
    val message: String,
    val roleName: String? = null,
    val groupId: Int? = null,
    val groupName: String? = null,
    val unitId: Int? = null,
    val unitName: String? = null,
    val unitArray: String? = null,

    @SerializedName(
        value = "isCorporateUser",
        alternate = ["isCorperateUser", "IsCorporateUser", "IsCorperateUser"]
    )
    val isCorporateUser: Boolean? = null
)
