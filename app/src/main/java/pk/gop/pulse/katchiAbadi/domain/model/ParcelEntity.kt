package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity
@TypeConverters(StatusConverter::class)
data class ParcelEntity(
    val id: Long,
    val geom: String,
    val centroidGeom: String,
    val isSurveyed: Boolean,
    val distance: Double,
    val mauzaId: Long,
    val mauzaName: String,
    val kachiAbadiId: Long,
    val abadiName: String,

    val parcelNo: Long,
    val subParcelNo: String,
    val newStatusId: Int,
    val subParcelsStatusList: String,
    @PrimaryKey(autoGenerate = true)
    val pkId: Long? = null,
    val status: ParcelStatus = ParcelStatus.DEFAULT,
)

enum class ParcelStatus {
    DEFAULT,
    MERGE,
    NOT_AT_HOME,
    IN_PROCESS
}

class StatusConverter {
    @TypeConverter
    fun fromParcelStatus(parcelStatus: ParcelStatus): String {
        return parcelStatus.name
    }

    @TypeConverter
    fun toParcelStatus(value: String): ParcelStatus {
        return enumValueOf(value)
    }
}