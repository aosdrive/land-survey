package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_parcels")
data class ActiveParcelEntity(
    @PrimaryKey(autoGenerate = true)
    val pkid: Long = 0,  // <-- Let Room assign this val id: Long,
    val id: Long ,
    val parcelNo: Long,
    val subParcelNo: String,
    val mauzaId: Long,
    val mauzaName: String,
    val khewatInfo: String,
    val areaAssigned: String,
    val geomWKT: String,
    val centroid: String,
    val distance: Int,
    val parcelType: String,
    val parcelAreaKMF: String?,
    val parcelAreaAbadiDeh: String?,
    val surveyStatusCode: Int,
    val surveyId: Int?,
    val isActivate: Boolean = true,  // Added IsActivate attribute with default value
    val unitId: Long? = null,
    val groupId: Long? = null

)

object SurveyStatusCodes {
    const val DEFAULT = 1
    const val MERGE = 0
    const val NOT_AT_HOME = 0
    const val SURVEYED = 2
    // ... define others as needed
}