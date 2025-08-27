package pk.gop.pulse.katchiAbadi.data.remote.response

import androidx.annotation.Keep

@Keep data class BasicApiDto<T>(
    val message: String,
    val data: T? = null,
    val code: Int
)

@Keep data class KatchiAbadiApiDto<T>(
    val message: String,
    val kachiAbadi: T? = null,
    val code: Int
)

//@Keep data class DataApiDto(
//    val message: String? = null,
//    val code: Int,
//    val status: Boolean,
//)

@Keep data class PostApiDto(
    val message: String? = null,
    val code: Int,
)

@Keep
data class BasicInfoDto<T, Info>(
    val message: String,
    val data: T? = null,
    val code: Int,
    val info: Info? = null // Add the info field as nullable
)