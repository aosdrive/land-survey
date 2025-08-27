package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temp_survey_log")
data class TempSurveyLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val parcelId: Long,
    val parcelNo: String,
    val subParcelNo: String,
    val isSurveyed: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)