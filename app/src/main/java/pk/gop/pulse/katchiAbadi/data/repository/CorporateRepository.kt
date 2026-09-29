package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.DropdownItem
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.BlockEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropVarietyEntity
import pk.gop.pulse.katchiAbadi.domain.model.DivisionEntity
import pk.gop.pulse.katchiAbadi.domain.model.FarmEntity
import pk.gop.pulse.katchiAbadi.domain.model.IrrigationSourceEntity
import pk.gop.pulse.katchiAbadi.domain.model.PlotEntity
import pk.gop.pulse.katchiAbadi.domain.model.PropertyTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.SectionEntity
import pk.gop.pulse.katchiAbadi.domain.model.ZoneEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Corporate Farms hierarchy and lookup data.
 *
 * Deliberately kept separate from the Katchi Abadi DropdownRepository - corporate
 * endpoints live on different controllers and cache into different tables.
 *
 * Every fetch is server-first but falls back to the local cache when the network
 * fails, because the surveyor is usually offline.
 */
@Singleton
class CorporateRepository @Inject constructor(
    private val api: ServerApi,
    private val db: AppDatabase,
    private val prefs: SharedPreferences
) {

    private val tag = "CorporateRepo"

    private fun bearer(): String? =
        prefs.getString(Constants.SHARED_PREF_TOKEN, "")
            ?.takeIf { it.isNotEmpty() }
            ?.let { "Bearer $it" }

    /**
     * Fetch from the server, cache the result, and fall back to the cache when the
     * network or server fails. A failed cache insert never blocks the UI - the
     * server data is returned regardless.
     */
    private suspend fun <D, E> fetch(
        what: String,
        call: suspend (String) -> retrofit2.Response<List<D>>,
        map: (D) -> E,
        cache: suspend (List<E>) -> Unit,
        cached: suspend () -> List<E>
    ): Resource<List<E>> = withContext(Dispatchers.IO) {
        val token = bearer() ?: return@withContext Resource.Error("No auth token")
        try {
            val response = call(token)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val entities = body.map(map)
                try {
                    cache(entities)
                } catch (e: Exception) {
                    // For example when an FK parent is not cached yet - still show the data.
                    Log.w(tag, "$what cache insert failed: ${e.message}")
                }
                Resource.Success(entities)
            } else {
                Log.w(tag, "$what: server ${response.code()}, cache se de raha hoon")
                Resource.Success(cached())
            }
        } catch (e: Exception) {
            Log.e(tag, "$what failed: ${e.message}, cache se de raha hoon")
            try {
                Resource.Success(cached())
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load $what")
            }
        }
    }

    suspend fun getZones(): Resource<List<ZoneEntity>> = fetch(
        what = "zones",
        call = { api.getZones(it) },
        map = { ZoneEntity(zoneId = it.zoneId, zoneName = it.zoneName) },
        cache = { db.zoneDao().insertZones(it) },
        cached = { db.zoneDao().getAllZones() }
    )

    suspend fun getDivisions(zoneId: Long): Resource<List<DivisionEntity>> = fetch(
        what = "divisions",
        call = { api.getDivisions(it, zoneId) },
        map = { DivisionEntity(divisionId = it.divisionId, zoneId = it.zoneId, divisionName = it.divisionName) },
        cache = { db.divisionDao().insertDivisions(it) },
        cached = { db.divisionDao().getDivisionsByZone(zoneId) }
    )

    suspend fun getSections(divisionId: Long): Resource<List<SectionEntity>> = fetch(
        what = "sections",
        call = { api.getSections(it, divisionId) },
        map = { SectionEntity(sectionId = it.sectionId, divisionId = it.divisionId, sectionName = it.sectionName) },
        cache = { db.sectionDao().insertSections(it) },
        cached = { db.sectionDao().getSectionsByDivision(divisionId) }
    )

    suspend fun getFarms(sectionId: Long): Resource<List<FarmEntity>> = fetch(
        what = "farms",
        call = { api.getFarms(it, sectionId) },
        map = { FarmEntity(farmId = it.farmId, sectionId = it.sectionId, farmName = it.farmName) },
        cache = { db.farmDao().insertFarms(it) },
        cached = { db.farmDao().getFarmsBySection(sectionId) }
    )

    suspend fun getBlocks(farmId: Long): Resource<List<BlockEntity>> = fetch(
        what = "blocks",
        call = { api.getBlocks(it, farmId) },
        map = { BlockEntity(blockId = it.blockId, farmId = it.farmId, blockName = it.blockName) },
        cache = { db.blockDao().insertBlocks(it) },
        cached = { db.blockDao().getBlocksByFarm(farmId) }
    )

    suspend fun getPlots(blockId: Long): Resource<List<PlotEntity>> = fetch(
        what = "plots",
        call = { api.getPlots(it, blockId) },
        map = { PlotEntity(plotId = it.plotId, blockId = it.blockId, plotName = it.plotName) },
        cache = { db.plotDao().insertPlots(it) },
        cached = { db.plotDao().getPlotsByBlock(blockId) }
    )

    /** Irrigation sources - cache-first, these rarely change. */
    suspend fun getIrrigationSources(forceRefresh: Boolean = false): List<String> =
        withContext(Dispatchers.IO) {
            try {
                if (forceRefresh || db.irrigationSourceDao().getSourceCount() == 0) {
                    val response = api.getIrrigationSources()
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        val rows = body.map { IrrigationSourceEntity(value = it.value, isSynced = true) }
                        db.irrigationSourceDao().deleteAllSources()
                        db.irrigationSourceDao().insertAllSources(rows)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "irrigation sources failed: ${e.message}, cache se de raha hoon")
            }
            db.irrigationSourceDao().getAllSources().map { it.value }
        }

    /** Corporate property types - cache-first. */
    suspend fun getPropertyTypes(forceRefresh: Boolean = false): List<String> =
        withContext(Dispatchers.IO) {
            try {
                if (forceRefresh || db.propertyTypeDao().count() == 0) {
                    val response = api.getPropertyTypes()
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        val rows = body.mapIndexed { i, item ->
                            PropertyTypeEntity(value = item.value, sortOrder = i)
                        }
                        db.propertyTypeDao().clear()
                        db.propertyTypeDao().insertAll(rows)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "property types failed: ${e.message}, cache se de raha hoon")
            }
            db.propertyTypeDao().getAll().map { it.value }
        }

    /**
     * Cache-first lookup shared by the corporate dropdowns. Corporate crops, crop types and
     * varieties come from their own endpoints but reuse the same local tables as the Katchi
     * Abadi lists - a device only ever belongs to one kind of user at a time.
     */
    private suspend fun <E> lookup(
        what: String,
        count: suspend () -> Int,
        fetch: suspend () -> retrofit2.Response<List<DropdownItem>>,
        map: (DropdownItem) -> E,
        clear: suspend () -> Unit,
        insert: suspend (List<E>) -> Unit,
        read: suspend () -> List<String>,
        forceRefresh: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            if (forceRefresh || count() == 0) {
                val response = fetch()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    clear()
                    insert(body.map(map))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "$what failed: ${e.message}, serving from cache")
        }
        read()
    }

    suspend fun getCrops(forceRefresh: Boolean = false): List<String> = lookup(
        what = "corporate crops",
        count = { db.cropDao().getCropCount() },
        fetch = { api.getCorporateCrops() },
        map = { CropEntity(value = it.value, isSynced = true) },
        clear = { db.cropDao().deleteAllCrops() },
        insert = { db.cropDao().insertAllCrops(it) },
        read = { db.cropDao().getAllCrops().map { c -> c.value } },
        forceRefresh = forceRefresh
    )

    suspend fun getCropTypes(forceRefresh: Boolean = false): List<String> = lookup(
        what = "corporate crop types",
        count = { db.cropTypeDao().getCropTypeCount() },
        fetch = { api.getCorporateCropTypes() },
        map = { CropTypeEntity(value = it.value, isSynced = true) },
        clear = { db.cropTypeDao().deleteAllCropTypes() },
        insert = { db.cropTypeDao().insertAllCropTypes(it) },
        read = { db.cropTypeDao().getAllCropTypes().map { c -> c.value } },
        forceRefresh = forceRefresh
    )

    suspend fun getVarieties(forceRefresh: Boolean = false): List<String> = lookup(
        what = "corporate varieties",
        count = { db.cropVarietyDao().getVarietyCount() },
        fetch = { api.getCorporateCropVarieties() },
        map = { CropVarietyEntity(value = it.value, isSynced = true) },
        clear = { db.cropVarietyDao().deleteAllVarieties() },
        insert = { db.cropVarietyDao().insertAllVarieties(it) },
        read = { db.cropVarietyDao().getAllVarieties().map { c -> c.value } },
        forceRefresh = forceRefresh
    )
}
