package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disease_types")
data class DiseaseTypeEntity(
    @PrimaryKey val id: Int,
    val name: String
)