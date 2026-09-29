package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "plots",
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["blockId"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlotEntity(
    @PrimaryKey
    val plotId: Long,               // Server ID (now PK, no autoGenerate)
    val blockId: Long,              // FK referencing BlockEntity.blockId
    val plotName: String,
    val isActive: Boolean = true,
    val lastSynced: Long = System.currentTimeMillis()
)