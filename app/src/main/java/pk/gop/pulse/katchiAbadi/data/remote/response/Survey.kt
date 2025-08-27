package pk.gop.pulse.katchiAbadi.data.remote.response

data class Survey(
    val propertyId: Long,
    val propertyNo: String?,
    val name: String?,
    val fName: String?,
    val area: String?,
    val relation: String,
    val cnic: String?,
    val gender: String,
    val isAttach: Boolean
)