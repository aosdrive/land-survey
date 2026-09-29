package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["farmId"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BlockEntity(
    @PrimaryKey
    val blockId: Long,              // Server ID (now PK, no autoGenerate)
    val farmId: Long,               // FK referencing FarmEntity.farmId
    val blockName: String,
    val isActive: Boolean = true,
    val lastSynced: Long = System.currentTimeMillis()
)