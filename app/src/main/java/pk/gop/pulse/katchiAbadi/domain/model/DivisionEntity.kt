package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// ============================================
// DIVISION ENTITY (Foreign Key: Zone)
// ============================================
@Entity(
    tableName = "divisions",
    foreignKeys = [
        ForeignKey(
            entity = ZoneEntity::class,
            parentColumns = ["zoneId"],
            childColumns = ["zoneId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DivisionEntity(
    @PrimaryKey
    val divisionId: Long,               // Server ID
    val zoneId: Long,              // Local Zone ID (Foreign Key)
    val divisionName: String,
    val isActive: Boolean = true,
    val lastSynced: Long = System.currentTimeMillis()
)