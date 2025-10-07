package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity


@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isSynced = 0")
    suspend fun getUnsyncedTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdOn DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("UPDATE tasks SET isSynced = 1 WHERE taskId = :taskId")
    suspend fun markTaskAsSynced(taskId: Long)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}