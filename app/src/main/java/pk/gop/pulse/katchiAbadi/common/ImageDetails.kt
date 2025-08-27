package pk.gop.pulse.katchiAbadi.common

import java.io.Serializable


data class ImageDetails(
    val type: String,
    val path: String
) : Serializable