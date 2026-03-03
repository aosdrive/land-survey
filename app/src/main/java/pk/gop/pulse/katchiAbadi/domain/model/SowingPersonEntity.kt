package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sowing_person")
data class SowingPersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val surveyId: Long,           // foreign key to NewSurveyNewEntity.pkId
    val name: String,
    val cnic: String,             // keep formatted 12345-1234567-1
    val growerCode: String? = null
)