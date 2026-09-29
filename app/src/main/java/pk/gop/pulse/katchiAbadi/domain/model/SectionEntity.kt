package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// ============================================
// SECTION ENTITY (Foreign Key: Division)
// ============================================
@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = DivisionEntity::class,
            parentColumns = ["divisionId"],
            childColumns = ["divisionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SectionEntity(
    @PrimaryKey
    val sectionId: Long,                // Server ID
    val divisionId: Long,          // Local Division ID (Foreign Key)
    val sectionName: String,
    val isActive: Boolean = true,
    val lastSynced: Long = System.currentTimeMillis()
)