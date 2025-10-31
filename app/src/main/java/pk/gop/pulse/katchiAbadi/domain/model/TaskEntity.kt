package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val taskId: Long = 0,
    val assignDate: String = "",
    val issueType: String = "",
    val details: String = "",
    val picData: String = "",
    val parcelId: Long = 0,
    val parcelNo: String = "",
    val mauzaId: Long = 0,
    val assignedByUserId: Long = 0,
    val assignedToUserId: Long = 0,
    val khewatInfo: String="",
    val createdOn: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
//    val daysToComplete: Int

)