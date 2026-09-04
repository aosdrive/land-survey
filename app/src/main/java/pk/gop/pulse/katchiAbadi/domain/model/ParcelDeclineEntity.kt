package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parcel_declines")
data class ParcelDeclineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val declineGuid: String,
    val mauzaId: Long,
    val mauzaName: String,
    val areaName: String,
    val serverParcelId: Long?,
    val parcelNo: String,
    val subParcelNo: String,
    val parcelStatus: String,
    val khewatInfo: String,
    val remarks: String,
    val geomWKT: String?,
    val centroid: String?,
    val calculatedArea: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val bearing: Float? = null,
    val pic1Path: String,
    val pic2Path: String,
    val pic3Path: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isUploaded: Boolean = false
)