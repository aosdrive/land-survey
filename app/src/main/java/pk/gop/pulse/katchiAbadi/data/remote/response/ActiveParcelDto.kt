
data class ActiveParcelDto(
    val id: Long,
    val parcelNo: Long,
    val subParcelNo: String,
    val mauzaId: Long,
    val khewatInfo: String,
    val areaAssigned: String,
    val geomWKT: String,
    val centriod: String,
    val distance: Int,
    val parcelType: String,
    val parcelAreaKMF: String?,
    val parcelAreaAbadiDeh: String?,
    val surveyStatusCode: Int,
    val surveyId: Int?
)