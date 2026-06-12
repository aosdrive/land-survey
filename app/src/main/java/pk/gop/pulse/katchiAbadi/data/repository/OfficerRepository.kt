package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.OfficerEntity
import pk.gop.pulse.katchiAbadi.domain.model.UserResponse
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OfficerRepository"

@Singleton
class OfficerRepository @Inject constructor(
    private val database: AppDatabase,
    private val serverApi: ServerApi,
    private val sharedPreferences: SharedPreferences
) {

    /**
     * Fetch officers from the server and cache them locally.
     * Call this when the user has internet (e.g. on login, after sync, on app start).
     * Silently returns false on failure — does NOT throw.
     */
    suspend fun refreshFromServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext false

            val response = serverApi.getAllUsers(token = "Bearer $token")
            if (!response.isSuccessful || response.body() == null) {
                Log.w(TAG, "refresh failed: ${response.code()}")
                return@withContext false
            }

            val users = response.body()!!
            val entities = users.map { user ->
                OfficerEntity(
                    id = user.id,
                    fullName = user.fullName,
                    userName = user.userName,
                    cnic = user.cnic,
                    mobileNo = user.mobileNo,
                    vendorName = user.vendorName,
                    roleName = user.roleName,
                    isNotActive = user.isNotActive,
                    createdOn = user.createdOn
                )
            }

            // Replace cache fully so removed officers disappear too
            database.officerDao().clearAll()
            database.officerDao().insertAll(entities)
            Log.d(TAG, "✅ Cached ${entities.size} officers locally")
            true
        } catch (e: Exception) {
            Log.e(TAG, "refreshFromServer error: ${e.message}", e)
            false
        }
    }

    /**
     * Returns officers as UserResponse list, ready for the existing UserSelectionDialog.
     * If cache is empty, tries one online refresh; otherwise returns whatever's local.
     */
    suspend fun getOfficers(): List<UserResponse> = withContext(Dispatchers.IO) {
        if (database.officerDao().count() == 0) {
            refreshFromServer()
        }

        database.officerDao().getAll().map { entity ->
            UserResponse(
                id = entity.id,
                fullName = entity.fullName,
                userName = entity.userName,
                cnic = entity.cnic,
                mobileNo = entity.mobileNo,
                vendorName = entity.vendorName,
                roleName = entity.roleName,
                isNotActive = entity.isNotActive,
                createdOn = entity.createdOn
            )
        }
    }
}