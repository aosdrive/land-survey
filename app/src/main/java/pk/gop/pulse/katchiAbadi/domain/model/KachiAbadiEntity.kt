package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class KachiAbadiEntity(
    val id: Long,
    val lat: String,
    val lng: String,
    val name: String,
    val MauzaId: Long,
    @PrimaryKey(autoGenerate = true)
    val pkid: Long? = null,
)