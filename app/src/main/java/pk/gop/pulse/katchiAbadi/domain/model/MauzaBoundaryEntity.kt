package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mauza_boundary")
data class MauzaBoundaryEntity(
    @PrimaryKey val mauzaId: Long,
    val mauzaName: String,
    val groupId: Long? = null,
    val unitId: Long? = null,
    val zoneId: Long? = null,
    val circleId: Long? = null,
    val area: String? = null,
    val geomWKT: String,
    val geomType: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)
