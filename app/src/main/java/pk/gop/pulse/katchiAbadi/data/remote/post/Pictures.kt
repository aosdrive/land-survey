package pk.gop.pulse.katchiAbadi.data.remote.post

data class Pictures(
    val Number: Int,
    var OtherType: String? = null,
    var PicData: String,
    var Type: String,
    var Latitude: Double? = null,
    var Longitude: Double? = null,
    var Timestamp: Long? = null,
    var LocationAddress: String? = null,
    var Bearing: Float? = null
)