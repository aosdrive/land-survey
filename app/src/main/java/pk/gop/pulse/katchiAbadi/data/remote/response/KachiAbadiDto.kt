package pk.gop.pulse.katchiAbadi.data.remote.response

data class KachiAbadiDto(
    val katchiAbadiID: Long,
    val katchiAbadiName: String,
    val latitude: Double,
    val longitude: Double,
    val surveyList: List<Survey>,
    val parcelsList: List<Parcels>
)