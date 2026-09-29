package pk.gop.pulse.katchiAbadi.data.remote.response

data class VarietyValidationPost(
    val ParcelId: Long,
    val OldVariety: String,
    val NewVariety: String,
    val IsMatched: Boolean,
    val Remarks: String,
    val Latitude: Double?,
    val Longitude: Double?,
    val ValidatedOnDevice: String
)

