// domain/model/LessorEntity.kt
package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "lessors")
data class LessorEntity(
    @PrimaryKey
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("code")
    val code: String?,

    @SerializedName("zoneId")
    val zoneId: Int?,

    @SerializedName("isDeleted")
    val isDeleted: Boolean = false
)