package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parcel_activity_log",
    indices = [Index("parcelId"), Index("mauzaId"), Index("isUploaded")]
)
data class ParcelActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val parcelId: Long,               // local/server id (negative = locally created)
    val parcelNo: String,
    val subParcelNo: String = "",
    val mauzaId: Long,
    val areaName: String = "",

    val action: String,               // ParcelAction.* constant
    val status: String,               // "OK" | "FAILED"
    val details: String = "",         // free text / JSON (e.g. merged ids, old→new variety)
    val relatedParcelIds: String = "",// comma-separated (merge/split children)

    val surveyPkId: Long? = null,     // local survey row agar related ho
    val userId: Long,
    val userName: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val appVersion: String,
    val deviceInfo: String,
    val createdAt: Long = System.currentTimeMillis(),

    val isUploaded: Boolean = false
)

object ParcelAction {
    const val SURVEY_STARTED       = "SURVEY_STARTED"
    const val SURVEY_SAVED         = "SURVEY_SAVED"
    const val SURVEY_UPDATED       = "SURVEY_UPDATED"
    const val SURVEY_DELETED       = "SURVEY_DELETED"
    const val SURVEY_UPLOADED      = "SURVEY_UPLOADED"
    const val SURVEY_UPLOAD_FAILED = "SURVEY_UPLOAD_FAILED"
    const val SPLIT                = "SPLIT"
    const val MERGE_SELECTED       = "MERGE_SELECTED"
    const val DRAWN                = "DRAWN"
    const val VARIETY_VALIDATED    = "VARIETY_VALIDATED"
    const val VALIDATION_UPLOADED  = "VALIDATION_UPLOADED"
    const val TASK_ASSIGNED        = "TASK_ASSIGNED"
    const val DECLINED             = "DECLINED"
    const val PARCEL_DEACTIVATED   = "PARCEL_DEACTIVATED"
    const val SYNCED               = "SYNCED"
}