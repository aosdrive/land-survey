package pk.gop.pulse.katchiAbadi.data.remote.response

data class MouzaAssignedDto(
    val mozaID: Long,
    val mozaName: String,
    val feetPerMarla: Int,
    val kachiAbadiList: List<KachiAbadiList>,
)

data class MauzaDetail(
    val mauzaSurveyOverrideAccess: Boolean,
    val tehsilId: Int,
    val tehsilName: String,
    val mauzaName: String,
    val mauzaId: Long,
    val unit: Int,
    val isPlraMauza: Boolean,
    val serviceURL: String
)
data class Settings(
    val meterDistance: String,
    val meterAccuracy: String,
    val allowedDownloadableAreas: String,
    val minScaleService: String,
    val maxScaleService: String,
    val minScaleTiles: String,
    val maxScaleTiles: String
)
data class MauzaSyncResponse(
    val mauzaDetails: List<MauzaDetail>,
    val settings: Settings
)


data class ApiResponse(
    val code: Int,
    val message: String,
    val data: MouzaAssignedDto,
    val info: Info
)


data class Info(
    val meter_distance: String,
    val meter_accuracy: String,
    val allowed_downloadable_areas: String,
    val min_scale: String,
    val max_scale: String,
    val downlaod_data: String,
)