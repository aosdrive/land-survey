package pk.gop.pulse.katchiAbadi.data.remote.response

data class VersionCheckResponse(
    val success: Boolean,
    val message: String,
    val isUpdateRequired: Boolean,
    val currentVersion: String? = null,
    val minRequiredVersion: String? = null,
    val shouldLogout: Boolean? = null,
)