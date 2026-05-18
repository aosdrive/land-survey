package pk.gop.pulse.katchiAbadi.data.repository

import android.util.Log
import pk.gop.pulse.katchiAbadi.data.local.DiseaseTypeDao
import pk.gop.pulse.katchiAbadi.data.local.IssueTypeDao
import pk.gop.pulse.katchiAbadi.data.local.PestTypeDao
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.DiseaseTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.IssueTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.PestTypeEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookupRepository @Inject constructor(
    private val api: ServerApi,
    private val issueTypeDao: IssueTypeDao,
    private val pestTypeDao: PestTypeDao,
    private val diseaseTypeDao: DiseaseTypeDao
) {
    companion object { private const val TAG = "LookupRepository" }

    suspend fun getIssueTypes(): List<IssueTypeEntity> {
        try {
            val response = api.getIssueTypes()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.mapNotNull {
                    if (it.name.isNullOrBlank()) null
                    else IssueTypeEntity(id = it.id, name = it.name)
                }
                if (items.isNotEmpty()) {
                    issueTypeDao.clearAll()
                    issueTypeDao.insertAll(items)
                    Log.d(TAG, " Synced ${items.size} issue types")
                    return items
                }
            } else {
                Log.w(TAG, "Issue types sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Issue types sync exception, falling back to cache: ${e.message}")
        }
        return issueTypeDao.getAll()
    }

    suspend fun getPestTypes(): List<PestTypeEntity> {
        try {
            val response = api.getPestTypes()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.mapNotNull {
                    if (it.name.isNullOrBlank()) null
                    else PestTypeEntity(id = it.id, name = it.name)
                }
                if (items.isNotEmpty()) {
                    pestTypeDao.clearAll()
                    pestTypeDao.insertAll(items)
                    Log.d(TAG, " Synced ${items.size} pest types")
                    return items
                }
            } else {
                Log.w(TAG, "Pest types sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pest types sync exception, falling back to cache: ${e.message}")
        }
        return pestTypeDao.getAll()
    }

    suspend fun getDiseaseTypes(): List<DiseaseTypeEntity> {
        try {
            val response = api.getDiseaseTypes()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.mapNotNull {
                    if (it.name.isNullOrBlank()) null
                    else DiseaseTypeEntity(id = it.id, name = it.name)
                }
                if (items.isNotEmpty()) {
                    diseaseTypeDao.clearAll()
                    diseaseTypeDao.insertAll(items)
                    Log.d(TAG, " Synced ${items.size} disease types")
                    return items
                }
            } else {
                Log.w(TAG, "Disease types sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Disease types sync exception, falling back to cache: ${e.message}")
        }
        return diseaseTypeDao.getAll()
    }
}