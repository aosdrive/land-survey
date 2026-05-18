package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pest_types")
data class PestTypeEntity(
    @PrimaryKey val id: Int,
    val name: String
)