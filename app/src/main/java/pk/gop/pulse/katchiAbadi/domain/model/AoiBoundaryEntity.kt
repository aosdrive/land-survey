package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aoi_boundaries")
data class AoiBoundaryEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val tehsil: String,
    val theId: String,
    val district: String,
    val area: Double,
    val geomWKT: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
