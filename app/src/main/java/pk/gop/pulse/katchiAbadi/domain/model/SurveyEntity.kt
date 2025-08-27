package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SurveyEntity(
    val propertyId: Long,
    val propertyNo: String,
    val name: String,
    val fname: String,
    val area: String,
    val relation: String,
    val cnic: String,
    val gender: String,
    val kachiAbadiId: Long,
    val mauzaId: Long,
    val isAttached: Boolean,
    @PrimaryKey(autoGenerate = true)
    val pkId: Long? = null,
)



data class OwnerInfo(
    val name: String,
    val fname: String,
    val relation: String,
    val cnic: String,
    val gender: String,
)