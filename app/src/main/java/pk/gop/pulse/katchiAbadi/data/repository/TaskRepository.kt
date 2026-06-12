package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.TaskSubmitDto
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TaskRepository"

@Singleton
class TaskRepository @Inject constructor(
    private val database: AppDatabase,
    private val serverApi: ServerApi,
    private val sharedPreferences: SharedPreferences
) {

    // ---------- Local queries used by the UI ----------

    fun livePendingTasks(): Flow<List<TaskEntity>> =
        database.taskDao().livePendingTasks()

    fun livePendingCount(): Flow<Int> =
        database.taskDao().livePendingCount()

    suspend fun getPendingTasks(): List<TaskEntity> =
        withContext(Dispatchers.IO) { database.taskDao().getUnsyncedTasks() }

    suspend fun deletePendingTask(task: TaskEntity) {
        withContext(Dispatchers.IO) { database.taskDao().deleteTask(task) }
    }

    // ---------- Upload a single task ----------

    suspend fun uploadTask(task: TaskEntity): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) {
                return@withContext Resource.Error("Please login again")
            }

            Log.d(TAG, "Uploading task taskId=${task.taskId}, parcelNo=${task.parcelNo}")

            // Convert image file paths -> base64 at upload time
            val imagePaths = task.picData
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val base64Images = imagePaths.mapNotNull { path -> convertImageToBase64(path) }
            Log.d(TAG, "Converted ${base64Images.size}/${imagePaths.size} images to base64")

            val pestTypeIds: List<Int> = task.pestTypeIds
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()

            val diseaseTypeIds: List<Int> = task.diseaseTypeIds
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()

            val dto = TaskSubmitDto(
                assignDate = task.assignDate,
                issueType = task.issueType,
                detail = task.details,
                images = base64Images,
                parcelId = task.parcelId,
                parcelNo = task.parcelNo,
                mauzaId = task.mauzaId,
                assignedByUserId = task.assignedByUserId,
                assignedToUserId = task.assignedToUserId,
                khewatInfo = task.khewatInfo,
                daysToComplete = task.daysToComplete,
                issueTypeId = task.issueTypeId,
                pestTypeIds = pestTypeIds,
                diseaseTypeIds = diseaseTypeIds
            )

            val response = serverApi.submitTask("Bearer $token", dto)

            if (response.isSuccessful && response.body()?.success == true) {
                database.taskDao().markTaskAsSynced(task.taskId)
                Log.d(TAG, "✅ Task ${task.taskId} uploaded & marked synced")
                Resource.Success(Unit)
            } else {
                val msg = response.body()?.message
                    ?: response.errorBody()?.string()
                    ?: "Server error ${response.code()}"
                Log.e(TAG, "❌ Upload failed for task ${task.taskId}: $msg")
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading task ${task.taskId}: ${e.message}", e)
            Resource.Error(e.localizedMessage ?: "Upload failed")
        }
    }

    // ---------- Upload everything pending (used by "Upload All") ----------

    suspend fun uploadAllPendingTasks(): UploadSummary = withContext(Dispatchers.IO) {
        val pending = database.taskDao().getUnsyncedTasks()
        var success = 0
        var failed = 0
        val failures = mutableListOf<String>()

        for (task in pending) {
            when (val result = uploadTask(task)) {
                is Resource.Success -> success++
                is Resource.Error -> {
                    failed++
                    failures.add("Parcel ${task.parcelNo}: ${result.message}")
                }
                else -> { /* Loading not used here */ }
            }
        }

        UploadSummary(
            total = pending.size,
            success = success,
            failed = failed,
            failures = failures
        )
    }

    data class UploadSummary(
        val total: Int,
        val success: Int,
        val failed: Int,
        val failures: List<String>
    )

    // ---------- Image -> Base64 (reads from disk at upload time) ----------

    private fun convertImageToBase64(imagePath: String, maxSizeKB: Int = 60): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "Image file not found: $imagePath")
                return null
            }

            val fileSizeKB = file.length() / 1024
            if (fileSizeKB < maxSizeKB) {
                val bytes = file.readBytes()
                return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

            val stream = ByteArrayOutputStream()
            var quality = 70
            do {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                quality -= 5
            } while (stream.size() / 1024 > maxSizeKB && quality > 20)

            bitmap.recycle()
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Base64 conversion error: ${e.message}", e)
            null
        }
    }
}