package pk.gop.pulse.katchiAbadi.common

import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.KachiAbadiList

typealias SimpleResource = Resource<Unit>
//typealias SimpleResource = Resource<Unit, Any?>

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T): Resource<T>(data)
    class Error<T>(message: String): Resource<T>(message = message)
    class Loading<T>: Resource<T>()
    class Unspecified<T> : Resource<T>()

    companion object {
        fun unknownError(): String {
            return "Unknown error"
        }
    }
}


sealed class ResourceSealed<T, I>(
    val data: T? = null,
    val info: I? = null,
    val message: String? = null
) {
    class Success<T, I>(data: T, info: I? = null) : ResourceSealed<T, I>(data, info)
    class Error<T, I>(message: String, info: I? = null) : ResourceSealed<T, I>(info = info, message = message)
    class Loading<T, I> : ResourceSealed<T, I>()
    class Unspecified<T, I> : ResourceSealed<T, I>()

    companion object {
        fun unknownError(): String {
            return "Unknown error"
        }
    }
}



