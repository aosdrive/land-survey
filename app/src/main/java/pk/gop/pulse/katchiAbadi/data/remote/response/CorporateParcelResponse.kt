package pk.gop.pulse.katchiAbadi.data.remote.response

data class CorporateParcelResponse(
    val success: Boolean,
    val data: List<CorporateParcel>,
    val message: String? = null
)

data class CorporateParcel(
    val id: Long,
    val parcelNo: Long,
    val subParcelNo: String?,
    val mauzaId: Long?,
    val khewatInfo: String?,
    val areaAssigned: String?,
    val parcelAreaKMF: String?,
    val parcelAreaAbadiDeh: Double?,
    val parcelType: String?,
    val stories: String?,
    val distance: Double?,
    val surveyStatusCode: Int,
    val attachedSurveyId: String?,
    val geomWKT: String?,
    val geomType: String?,
    val geomAreaSqmt: Double?,
    val calculatedArea: String?,
    val year: String?,
    val zone: String?,
    val division: String?,
    val section: String?,
    val farm: String?,
    val block: String?,
    val plot: String?,
    val ownershipStatus: String?,
    val lessorName: String?,
    val plotBifurcation: String?,
    val plotSizeAcres: Double?,
    val userId: Long?
)