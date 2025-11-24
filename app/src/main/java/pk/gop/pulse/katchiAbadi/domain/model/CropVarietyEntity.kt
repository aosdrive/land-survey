package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crop_varieties")
data class CropVarietyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val value: String,
    val isSynced: Boolean = true // false means needs to be synced to server
)
