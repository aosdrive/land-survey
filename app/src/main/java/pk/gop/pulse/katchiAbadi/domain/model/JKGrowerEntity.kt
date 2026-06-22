package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jk_growers")
data class JKGrowerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val circleCode: String = "",
    val circle: String = "",
    val mouzaCode: String = "",
    val mouzaName: String = "",
    val passbookNo: String = "",
    val growerName: String = "",
    val fatherName: String = "",
    val cnicNo: String = "",
    val mobileNo: String = ""
)