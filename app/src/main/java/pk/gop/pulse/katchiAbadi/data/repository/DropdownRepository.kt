package pk.gop.pulse.katchiAbadi.data.repository

import android.util.Log
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyRequest
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.CropEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropVarietyEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropdownRepository @Inject constructor(
    private val database: AppDatabase,
    private val serverApi: ServerApi
) {

    // ========== CROPS ==========
    suspend fun getCrops(forceRefresh: Boolean = false): List<String> {
        return try {
            // Check if we need to fetch from server
            val localCount = database.cropDao().getCropCount()

            if (forceRefresh || localCount == 0) {
                // Fetch from server
                val response = serverApi.getCrops()
                if (response.isSuccessful && response.body() != null) {
                    val serverCrops = response.body()!!.map {
                        CropEntity(value = it.value, isSynced = true)
                    }

                    // Clear and insert new data
                    database.cropDao().deleteAllCrops()
                    database.cropDao().insertAllCrops(serverCrops)

                    Log.d("DropdownRepo", "Crops synced from server: ${serverCrops.size}")
                }
            }

            // Return from local database
            val crops = database.cropDao().getAllCrops()
            crops.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching crops: ${e.message}")
            // Return local data even if sync fails
            database.cropDao().getAllCrops().map { it.value }
        }
    }

    // ========== CROP TYPES ==========
    suspend fun getCropTypes(forceRefresh: Boolean = false): List<String> {
        return try {
            val localCount = database.cropTypeDao().getCropTypeCount()

            if (forceRefresh || localCount == 0) {
                val response = serverApi.getCropTypes()
                if (response.isSuccessful && response.body() != null) {
                    val serverCropTypes = response.body()!!.map {
                        CropTypeEntity(value = it.value, isSynced = true)
                    }

                    database.cropTypeDao().deleteAllCropTypes()
                    database.cropTypeDao().insertAllCropTypes(serverCropTypes)

                    Log.d("DropdownRepo", "Crop types synced from server: ${serverCropTypes.size}")
                }
            }

            val cropTypes = database.cropTypeDao().getAllCropTypes()
            cropTypes.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching crop types: ${e.message}")
            database.cropTypeDao().getAllCropTypes().map { it.value }
        }
    }

    // ========== VARIETIES ==========
    suspend fun getVarieties(forceRefresh: Boolean = false): List<String> {
        return try {
            val localCount = database.cropVarietyDao().getVarietyCount()

            if (forceRefresh || localCount == 0) {
                val response = serverApi.getCropVarieties()
                if (response.isSuccessful && response.body() != null) {
                    val serverVarieties = response.body()!!.map {
                        CropVarietyEntity(value = it.value, isSynced = true)
                    }

                    // Don't delete unsynced local varieties
                    val unsyncedVarieties = database.cropVarietyDao().getUnsyncedVarieties()

                    database.cropVarietyDao().deleteAllVarieties()
                    database.cropVarietyDao().insertAllVarieties(serverVarieties)

                    // Re-insert unsynced varieties
                    unsyncedVarieties.forEach {
                        database.cropVarietyDao().insertVariety(it)
                    }

                    Log.d("DropdownRepo", "Varieties synced from server: ${serverVarieties.size}")
                }
            }

            val varieties = database.cropVarietyDao().getAllVarieties()
            varieties.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching varieties: ${e.message}")
            database.cropVarietyDao().getAllVarieties().map { it.value }
        }
    }

    suspend fun addVariety(varietyName: String): AddVarietyResult {
        return try {
            // Check if exists locally
            val existing = database.cropVarietyDao().getVarietyByValue(varietyName)
            if (existing != null) {
                return AddVarietyResult.AlreadyExists
            }

            // Try to add to server
            try {
                val response = serverApi.addCropVariety(AddVarietyRequest(varietyName))
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!

                    if (result.alreadyExists == true) {
                        // Server says it exists, add to local as synced
                        val variety = CropVarietyEntity(value = varietyName, isSynced = true)
                        database.cropVarietyDao().insertVariety(variety)
                        return AddVarietyResult.AlreadyExists
                    } else {
                        // Successfully added to server
                        val variety = CropVarietyEntity(value = varietyName, isSynced = true)
                        database.cropVarietyDao().insertVariety(variety)
                        return AddVarietyResult.Success
                    }
                } else {
                    // Server request failed, add locally as unsynced
                    val variety = CropVarietyEntity(value = varietyName, isSynced = false)
                    database.cropVarietyDao().insertVariety(variety)
                    return AddVarietyResult.SavedOffline
                }
            } catch (e: Exception) {
                // Network error, add locally as unsynced
                Log.e("DropdownRepo", "Network error adding variety: ${e.message}")
                val variety = CropVarietyEntity(value = varietyName, isSynced = false)
                database.cropVarietyDao().insertVariety(variety)
                return AddVarietyResult.SavedOffline
            }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error adding variety: ${e.message}")
            AddVarietyResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun syncUnsyncedVarieties(): Int {
        var syncedCount = 0
        try {
            val unsyncedVarieties = database.cropVarietyDao().getUnsyncedVarieties()

            unsyncedVarieties.forEach { variety ->
                try {
                    val response = serverApi.addCropVariety(AddVarietyRequest(variety.value))
                    if (response.isSuccessful) {
                        // Mark as synced
                        val updated = variety.copy(isSynced = true)
                        database.cropVarietyDao().updateVariety(updated)
                        syncedCount++
                    }
                } catch (e: Exception) {
                    Log.e("DropdownRepo", "Failed to sync variety: ${variety.value}")
                }
            }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error syncing varieties: ${e.message}")
        }
        return syncedCount
    }
}

sealed class AddVarietyResult {
    object Success : AddVarietyResult()
    object AlreadyExists : AddVarietyResult()
    object SavedOffline : AddVarietyResult()
    data class Error(val message: String) : AddVarietyResult()
}