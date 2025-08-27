package pk.gop.pulse.katchiAbadi.domain.model
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "survey_images")
data class SurveyImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var surveyId: Long = 0,
    val uri: String,
    val type: String
)

