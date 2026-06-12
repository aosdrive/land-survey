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

    // =================================================================
    // CACHE-FIRST READERS (used by TaskAssignActivity)
    //
    // If the cache has data → return it instantly (works offline).
    // Only if cache is empty → try the network as a last resort.
    // =================================================================

    suspend fun getIssueTypes(): List<IssueTypeEntity> {
        val cached = issueTypeDao.getAll()
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Returning ${cached.size} issue types from cache")
            return cached
        }
        // Cache empty → try network as last resort
        Log.d(TAG, "Issue type cache empty, trying network")
        refreshIssueTypes()
        return issueTypeDao.getAll()
    }

    suspend fun getPestTypes(): List<PestTypeEntity> {
        val cached = pestTypeDao.getAll()
        if (cached.isNotEmpty()) return cached
        refreshPestTypes()
        return pestTypeDao.getAll()
    }

    suspend fun getDiseaseTypes(): List<DiseaseTypeEntity> {
        val cached = diseaseTypeDao.getAll()
        if (cached.isNotEmpty()) return cached
        refreshDiseaseTypes()
        return diseaseTypeDao.getAll()
    }

    // =================================================================
    // NETWORK REFRESH (call these when the user IS online — e.g. login)
    //
    // refreshAll() pre-caches everything in one go. Silent: returns
    // Boolean, never throws.
    // =================================================================

    suspend fun refreshAll(): Boolean {
        val a = refreshIssueTypes()
        val b = refreshPestTypes()
        val c = refreshDiseaseTypes()
        return a && b && c
    }

    suspend fun refreshIssueTypes(): Boolean {
        return try {
            val response = api.getIssueTypes()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.mapNotNull {
                    if (it.name.isNullOrBlank()) null
                    else IssueTypeEntity(id = it.id, name = it.name)
                }
                if (items.isNotEmpty()) {
                    issueTypeDao.clearAll()
                    issueTypeDao.insertAll(items)
                    Log.d(TAG, "✅ Cached ${items.size} issue types")
                    true
                } else false
            } else {
                Log.w(TAG, "Issue types refresh failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Issue types refresh exception: ${e.message}")
            false
        }
    }

    suspend fun refreshPestTypes(): Boolean {
        return try {
            val response = api.getPestTypes()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.mapNotNull {
                    if (it.name.isNullOrBlank()) null
                    else PestTypeEntity(id = it.id, name = it.name)
                }
                if (items.isNotEmpty()) {
                    pestTypeDao.clearAll()
                    pestTypeDao.insertAll(items)
                    Log.d(TAG, "✅ Cached ${items.size} pest types")
                    true
                } else false
            } else {
                Log.w(TAG, "Pest types refresh failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pest types refresh exception: ${e.message}")
            false
        }
    }

    suspend fun refreshDiseaseTypes(): Boolean {
        return try {
            val response = api.getDiseaseTypes()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.mapNotNull {
                    if (it.name.isNullOrBlank()) null
                    else DiseaseTypeEntity(id = it.id, name = it.name)
                }
                if (items.isNotEmpty()) {
                    diseaseTypeDao.clearAll()
                    diseaseTypeDao.insertAll(items)
                    Log.d(TAG, "✅ Cached ${items.size} disease types")
                    true
                } else false
            } else {
                Log.w(TAG, "Disease types refresh failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Disease types refresh exception: ${e.message}")
            false
        }
    }
}