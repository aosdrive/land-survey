package pk.gop.pulse.katchiAbadi.data.remote.response

import com.google.gson.annotations.SerializedName

data class JKGrowerDto(
    @SerializedName("circleCode") val circleCode: String? = null,
    @SerializedName("circle") val circle: String? = null,
    @SerializedName("mouzaCode") val mouzaCode: String? = null,
    @SerializedName("mouzaName") val mouzaName: String? = null,
    @SerializedName("passbookNo") val passbookNo: String? = null,
    @SerializedName("growerName") val growerName: String? = null,
    @SerializedName("fatherName") val fatherName: String? = null,
    @SerializedName("cnicNo") val cnicNo: String? = null,
    @SerializedName("mobileNo") val mobileNo: String? = null
)