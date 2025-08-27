package pk.gop.pulse.katchiAbadi.domain.model
import androidx.room.Entity
import androidx.room.PrimaryKey

//@Entity(tableName = "survey_persons")
//data class SurveyPersonEntity(
//    @PrimaryKey(autoGenerate = true)
//    val id: Long = 0,
//    var surveyId: Long = 0,
//    var ownershipType: String = "",
//    var personId: Int = 0,
//    var firstName: String = "",
//    var lastName: String = "",
//    var gender: String = "",
//    var relation: String = "",
//    var religion: String = "",
//    var mobile: String = "",
//    var nic: String = "",
//    var khewatNo: String = "",
//    var personArea: String = "",
//    var khewatId: Long = 0
//)

@Entity(tableName = "survey_persons")
data class SurveyPersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    var surveyId: Long = 0,
    var ownershipType: String = "",

    var personId: Long = 0,
    var firstName: String = "",
    var lastName: String = "",
    var gender: String = "",

    var relation: String = "",
    var religion: String = "",
    var mobile: String = "",
    var nic: String = "",

    var growerCode: String = "",
    var personArea: String = "",

    var extra1: String = "",
    var extra2: String = "",

    var mauzaId: Long = 0,
    var mauzaName: String = ""
)