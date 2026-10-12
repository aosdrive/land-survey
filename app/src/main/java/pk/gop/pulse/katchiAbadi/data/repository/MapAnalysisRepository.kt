package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.AnalyticsParcelDao
import pk.gop.pulse.katchiAbadi.data.local.ParcelAcceptDao
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.AnalyticsParcelDto
import pk.gop.pulse.katchiAbadi.domain.model.AnalyticsParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAcceptEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAcceptPostDto
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MapAnalysisRepo"

/**
 * Map Analysis data: the offline cache of GetAnalyticsParcels, and the offline-first
 * accepts of "Unmapped cane cells" parcels with their manual upload.
 */
class MapAnalysisRepository(
    private val analyticsDao: AnalyticsParcelDao,
    private val acceptDao: ParcelAcceptDao,
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences,
    private val logger: ParcelActivityLogger
) {

    /** Rows for the map, plus where they came from. */
    data class AnalyticsLoad(
        val rows: List<AnalyticsParcelDto>,
        val fromCache: Boolean,
        val cachedAt: Long?
    )

    data class AcceptUploadSummary(
        val uploaded: Int,
        val failed: Int,
        val errors: List<String>
    )

    // A second tap on Upload while one is running must not post the same rows twice.
    private val uploadMutex = Mutex()

    // ================= ANALYTICS CACHE =================

    /**
     * Server first; on success the mauza's cache is replaced. When the server can't be
     * reached, the last cached copy is returned instead. Throws only if there is neither.
     */
    suspend fun loadAnalyticsParcels(mauzaId: Int): AnalyticsLoad = withContext(Dispatchers.IO) {
        try {
            val rows = api.getAnalyticsParcels(mauzaId, null).features.orEmpty()
            cacheAnalyticsParcels(mauzaId, rows)
            AnalyticsLoad(rows, fromCache = false, cachedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Analytics fetch failed, trying cache: ${e.message}")
            val cached = analyticsDao.getForMauza(mauzaId)
            if (cached.isEmpty()) throw e
            AnalyticsLoad(
                rows = cached.map { it.toDto() },
                fromCache = true,
                cachedAt = analyticsDao.cachedAt(mauzaId)
            )
        }
    }

    /** Saves a fresh server copy for offline use. Also called from the mauza download. */
    suspend fun cacheAnalyticsParcels(mauzaId: Int, rows: List<AnalyticsParcelDto>) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            analyticsDao.replaceForMauza(mauzaId, rows.map { AnalyticsParcelEntity.from(it, now) })
            Log.d(TAG, "Cached ${rows.size} analytics parcels for mauza $mauzaId")
        }

    // ================= ACCEPTS =================

    fun livePendingAcceptCount(): Flow<Int> = acceptDao.livePendingAcceptCount()

    suspend fun acceptedParcelIds(): Set<Long> = withContext(Dispatchers.IO) {
        acceptDao.getAcceptedParcelIds().toSet()
    }

    suspend fun isAccepted(parcelId: Long): Boolean = withContext(Dispatchers.IO) {
        acceptDao.countForParcel(parcelId) > 0
    }

    /** True when any of these parcels has an accept that is still waiting for upload. */
    suspend fun hasPendingAccept(parcelIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        parcelIds.isNotEmpty() && acceptDao.countPendingForParcels(parcelIds) > 0
    }

    /** Removals still waiting for upload, oldest first (the Pending Survey Removals screen). */
    suspend fun getPendingAccepts(): List<ParcelAcceptEntity> = withContext(Dispatchers.IO) {
        acceptDao.getPendingAccepts()
    }

    /** Deletes a removal that has not been uploaded. An uploaded one can no longer be taken back. */
    suspend fun deleteAccept(accept: ParcelAcceptEntity) = withContext(Dispatchers.IO) {
        val deleted = acceptDao.deletePending(accept.id)
        if (deleted == 0) {
            throw IllegalStateException(
                "The survey removal for parcel ${accept.parcelNo} was already uploaded and can not be deleted."
            )
        }
        logger.log(
            parcelId = accept.serverParcelId,
            parcelNo = accept.parcelNo,
            action = ParcelAction.ACCEPT_DELETED,
            mauzaId = accept.mauzaId,
            areaName = accept.areaName,
            details = "status=${accept.parcelStatus}, guid=${accept.acceptGuid}, remarks=${accept.remarks}"
        )
        Log.d(TAG, "Accept deleted id=${accept.id} parcel=${accept.serverParcelId}")
    }

    suspend fun saveAccept(accept: ParcelAcceptEntity): Long = withContext(Dispatchers.IO) {
        val id = acceptDao.insertAccept(accept)
        logger.log(
            parcelId = accept.serverParcelId,
            parcelNo = accept.parcelNo,
            action = ParcelAction.ACCEPTED,
            mauzaId = accept.mauzaId,
            areaName = accept.areaName,
            details = "status=${accept.parcelStatus}, guid=${accept.acceptGuid}, remarks=${accept.remarks}"
        )
        Log.d(TAG, "Accept saved id=$id parcel=${accept.serverParcelId}")
        id
    }

    /** Uploads every pending accept in one request. Safe to call repeatedly. */
    suspend fun uploadPendingAccepts(): Resource<AcceptUploadSummary> = uploadMutex.withLock {
        withContext(Dispatchers.IO) {
            val pending = acceptDao.getPendingAccepts()
            if (pending.isEmpty()) {
                return@withContext Resource.Success(AcceptUploadSummary(0, 0, emptyList()))
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val body = pending.map { a ->
                ParcelAcceptPostDto(
                    acceptGuid = a.acceptGuid,
                    mauzaId = a.mauzaId,
                    mauzaName = a.mauzaName,
                    areaName = a.areaName,
                    parcelId = a.serverParcelId,
                    parcelNo = a.parcelNo,
                    parcelStatus = a.parcelStatus,
                    khewatInfo = a.khewatInfo,
                    remarks = a.remarks,
                    latitude = a.latitude,
                    longitude = a.longitude,
                    deviceCreatedDate = dateFormat.format(Date(a.createdAt))
                )
            }

            try {
                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                val response = api.postParcelAccepts("Bearer $token", body)

                if (!response.isSuccessful) {
                    val err = "Server error: ${response.errorBody()?.string() ?: "Unknown"} (code ${response.code()})"
                    pending.forEach { markFailed(it, err) }
                    return@withContext Resource.Error(err)
                }

                val resultsByGuid = response.body()?.results.orEmpty()
                    .filter { it.acceptGuid != null }
                    .associateBy { it.acceptGuid!!.lowercase(Locale.US) }

                var uploaded = 0
                val errors = mutableListOf<String>()
                pending.forEach { a ->
                    when (val status = resultsByGuid[a.acceptGuid.lowercase(Locale.US)]?.status) {
                        // Duplicate = the server already has it from an earlier attempt.
                        // SavedInactive = recorded, but the parcel was already split/deactivated.
                        "Saved", "Duplicate", "SavedInactive" -> {
                            acceptDao.markUploaded(a.id)
                            logger.log(
                                parcelId = a.serverParcelId, parcelNo = a.parcelNo,
                                action = ParcelAction.ACCEPT_UPLOADED,
                                mauzaId = a.mauzaId, areaName = a.areaName,
                                details = "server=$status, guid=${a.acceptGuid}"
                            )
                            uploaded++
                        }
                        else -> {
                            val err = if (status == "ParcelNotFound")
                                "Parcel ${a.parcelNo} is not active on the server."
                            else
                                "No server result for parcel ${a.parcelNo}."
                            markFailed(a, err)
                            errors.add(err)
                        }
                    }
                }

                // Rejections of individual rows won't change on retry.
                Resource.Success(AcceptUploadSummary(uploaded, errors.size, errors))
            } catch (e: Exception) {
                // A server without AddParcelAccept answers 200 with its HTML login page,
                // which Gson can't parse. Say that plainly instead of a JSON parser error.
                val err = if (e is com.google.gson.JsonParseException || e is com.google.gson.stream.MalformedJsonException ||
                    e is IllegalStateException)
                    "The server does not support accept upload yet (AddParcelAccept is not deployed). " +
                        "Your accepts are kept on this device."
                else
                    e.message ?: "Upload failed"
                Log.e(TAG, "Accept upload failed: $err", e)
                pending.forEach { markFailed(it, err) }
                Resource.Error(err)
            }
        }
    }

    private suspend fun markFailed(accept: ParcelAcceptEntity, error: String) {
        acceptDao.setLastError(accept.id, error.take(500))
        logger.log(
            parcelId = accept.serverParcelId, parcelNo = accept.parcelNo,
            action = ParcelAction.ACCEPT_UPLOAD_FAILED,
            mauzaId = accept.mauzaId, areaName = accept.areaName,
            status = "FAILED", details = "guid=${accept.acceptGuid}, error=$error"
        )
    }
}
