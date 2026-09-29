package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "irrigation_source")
data class IrrigationSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val value: String,
    val isSynced: Boolean = true
)
