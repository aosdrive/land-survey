data class ActiveParcelResponse(
    val distinctKhewats: List<Long>,
    val parcelsData: List<ActiveParcelDto>,
    val parcelIdForRevisitCases: List<Long>
)