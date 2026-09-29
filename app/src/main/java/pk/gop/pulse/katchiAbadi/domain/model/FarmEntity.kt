package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "farms",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FarmEntity(
    @PrimaryKey
    val farmId: Long,               // Server ID
    val sectionId: Long,       // Local Section ID (Foreign Key)
    val farmName: String,
    val isActive: Boolean = true,
    val lastSynced: Long = System.currentTimeMillis()
)