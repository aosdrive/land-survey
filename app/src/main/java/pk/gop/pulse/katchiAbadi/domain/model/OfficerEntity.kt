package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "officers")
data class OfficerEntity(
    @PrimaryKey
    val id: Long,
    val fullName: String,
    val userName: String,
    val cnic: String,
    val mobileNo: String,
    val vendorName: String?,
    val roleName: String?,
    val isNotActive: Boolean,
    val createdOn: String,
    val cachedAt: Long = System.currentTimeMillis()
)