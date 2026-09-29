package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "new_survey_lessors")
data class SurveyLessorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val surveyId: Long = 0,
    val parcelId: Long = 0,
    val parcelNo: String = "",
    val subParcelNo: String = "",
    val lessorId: Int,             // 0 for ad-hoc
    val lessorName: String,
    val lessorCode: String = "",
    val lessorType: String = "Single",
    val isSynced: Boolean = false
)
