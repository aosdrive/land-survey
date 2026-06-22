package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.util.Log
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.JKGrowerDao
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.response.JKGrowerDto
import pk.gop.pulse.katchiAbadi.domain.model.JKGrowerEntity
import javax.inject.Inject

private const val TAG = "JKGrowerRepository"

class JKGrowerRepositoryImpl @Inject constructor(
    private val api: ServerApi,
    private val jkGrowerDao: JKGrowerDao,
    private val sharedPreferences: SharedPreferences
) {

    suspend fun syncGrowersForMouza(mouzaCode: String, forceRefresh: Boolean = false): List<JKGrowerEntity> {
        // Serve from cache if available and not forcing a refresh
        if (!forceRefresh) {
            val cached = jkGrowerDao.getGrowersByMouza(mouzaCode)
            if (cached.isNotEmpty()) {
                Log.d(TAG, "Serving ${cached.size} growers for mouza $mouzaCode from cache")
                return cached
            }
        }

        return try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            val response = api.getJKGrowersByMouza(mouzaCode, "Bearer $token")

            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                val entities = dtos.toEntities(mouzaCode)

                // Replace cache for this mouza
                jkGrowerDao.deleteByMouza(mouzaCode)
                jkGrowerDao.insertAll(entities)

                Log.d(TAG, "Synced ${entities.size} growers for mouza $mouzaCode")
                entities
            } else {
                Log.e(TAG, "Fetch failed: ${response.code()} ${response.errorBody()?.string()}")
                // Fall back to whatever is cached
                jkGrowerDao.getGrowersByMouza(mouzaCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing growers: ${e.message}", e)
            jkGrowerDao.getGrowersByMouza(mouzaCode)
        }
    }

    suspend fun getGrowersByMouza(mouzaCode: String): List<JKGrowerEntity> =
        jkGrowerDao.getGrowersByMouza(mouzaCode)

    suspend fun getGrowerByCnic(cnic: String): JKGrowerEntity? =
        jkGrowerDao.getGrowerByCnic(cnic)

    private fun List<JKGrowerDto>.toEntities(mouzaKey: String): List<JKGrowerEntity> = map { dto ->
        JKGrowerEntity(
            circleCode = dto.circleCode.orEmpty(),
            circle = dto.circle.orEmpty(),
            mouzaCode = mouzaKey,
            mouzaName = dto.mouzaName.orEmpty(),
            passbookNo = dto.passbookNo.orEmpty(),
            growerName = dto.growerName.orEmpty(),
            fatherName = dto.fatherName.orEmpty(),
            cnicNo = dto.cnicNo.orEmpty(),
            mobileNo = dto.mobileNo.orEmpty()
        )
    }
}