package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "property_types")
data class PropertyTypeEntity(
    @PrimaryKey val value: String,
    val sortOrder: Int = 0
)