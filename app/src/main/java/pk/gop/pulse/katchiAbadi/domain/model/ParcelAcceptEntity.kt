package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An "Accept" of an "Unmapped cane cells" parcel from the Map Analysis screen.
 * Saved offline first; uploading it sends the parcel back for survey on the server.
 */
@Entity(
    tableName = "parcel_accepts",
    indices = [Index(value = ["serverParcelId"])]
)
data class ParcelAcceptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val acceptGuid: String,
    val mauzaId: Long,
    val mauzaName: String,
    val areaName: String,
    val serverParcelId: Long,
    val parcelNo: String,
    val parcelStatus: String,
    val khewatInfo: String,
    val remarks: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Long = System.currentTimeMillis(),
    val isUploaded: Boolean = false,
    /** Last upload error, shown to the surveyor; null once uploaded or never tried. */
    val lastError: String? = null
)
