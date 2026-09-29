package pk.gop.pulse.katchiAbadi.data.remote.response

data class VarietyValidationResponse(
    val Message: String?,
    val Validated: Int?,
    val ProcessedParcelIds: List<Long>?,
    val NotFoundParcelIds: List<Long>?
)
