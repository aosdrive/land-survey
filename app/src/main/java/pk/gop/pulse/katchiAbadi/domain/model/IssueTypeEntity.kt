package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "issue_types")
data class IssueTypeEntity(
    @PrimaryKey val id: Int,
    val name: String
)