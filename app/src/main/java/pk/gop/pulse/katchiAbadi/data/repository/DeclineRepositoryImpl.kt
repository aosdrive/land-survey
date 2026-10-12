package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.ParcelDeclineDao
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAction
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclinePostDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "DeclineRepo"

class DeclineRepositoryImpl @Inject constructor(
    private val dao: ParcelDeclineDao,
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences,
    private val logger: ParcelActivityLogger
) {

    fun livePendingCount(): Flow<Int> = dao.livePendingDeclineCount()

    suspend fun saveDecline(decline: ParcelDeclineEntity): Long =
        withContext(Dispatchers.IO) {
            val id = dao.insertDecline(decline)
            Log.d(TAG, "Decline saved id=$id")
            id
        }

    /** Saves several declines in one DAO call (one row per parcel, all sharing the same picture set). */
    suspend fun saveDeclines(declines: List<ParcelDeclineEntity>): List<Long> =
        withContext(Dispatchers.IO) {
            val ids = dao.insertDeclines(declines)
            Log.d(TAG, "Declines saved ids=$ids")
            ids
        }

    suspend fun uploadDecline(decline: ParcelDeclineEntity): Resource<Unit> {
        return try {
            val encoded = withContext(Dispatchers.IO) {
                listOf(decline.pic1Path, decline.pic2Path, decline.pic3Path)
                    .map { encodeImage(it) }
            }

            if (encoded.any { it.isNullOrEmpty() }) {
                val err = "Some picture files are missing from the device."
                logUploadFailed(decline, err)
                return Resource.Error(err)
            }

            val body = listOf(
                ParcelDeclinePostDto(
                    declineGuid       = decline.declineGuid,
                    mauzaId           = decline.mauzaId,
                    mauzaName         = decline.mauzaName,
                    areaName          = decline.areaName,
                    parcelId          = decline.serverParcelId,
                    parcelNo          = decline.parcelNo,
                    subParcelNo       = decline.subParcelNo,
                    parcelStatus      = decline.parcelStatus,
                    khewatInfo        = decline.khewatInfo,
                    remarks           = decline.remarks,
                    geomWKT           = decline.geomWKT,
                    centroid          = decline.centroid,
                    calculatedArea    = decline.calculatedArea,
                    latitude          = decline.latitude,
                    longitude         = decline.longitude,
                    bearing           = decline.bearing,
                    timestamp         = decline.createdAt,
                    deviceCreatedDate = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US
                    ).format(Date(decline.createdAt)),
                    pic1 = encoded[0]!!,
                    pic2 = encoded[1]!!,
                    pic3 = encoded[2]!!
                )
            )

            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            val response = api.postParcelDeclines("Bearer $token", body)

            if (response.isSuccessful) {
                withContext(Dispatchers.IO) { dao.markUploaded(decline.id) }
                Log.d(TAG, "Decline ${decline.id} uploaded")
                logger.log(
                    parcelId = decline.serverParcelId ?: 0L, parcelNo = decline.parcelNo,
                    action = ParcelAction.DECLINE_UPLOADED, subParcelNo = decline.subParcelNo,
                    mauzaId = decline.mauzaId, areaName = decline.areaName,
                    details = "guid=${decline.declineGuid}, code=${response.code()}"
                )
                Resource.Success(Unit)
            } else {
                val err = response.errorBody()?.string() ?: "Unknown"
                val msg = "Server error: $err (code ${response.code()})"
                logUploadFailed(decline, msg)
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            val msg = e.message ?: "Upload failed"
            logUploadFailed(decline, msg)
            Resource.Error(msg)
        }
    }

    private fun logUploadFailed(decline: ParcelDeclineEntity, error: String) {
        logger.log(
            parcelId = decline.serverParcelId ?: 0L, parcelNo = decline.parcelNo,
            action = ParcelAction.DECLINE_UPLOAD_FAILED, subParcelNo = decline.subParcelNo,
            mauzaId = decline.mauzaId, areaName = decline.areaName,
            status = "FAILED", details = "guid=${decline.declineGuid}, error=$error"
        )
    }

    private fun encodeImage(path: String): String? = try {
        val file = File(path)
        if (!file.exists()) null
        else BitmapFactory.decodeFile(path)?.let { bmp ->
            ByteArrayOutputStream().use { os ->
                var quality = 100
                do {
                    os.reset()
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)
                    quality -= 5
                } while (os.size() / 1024 > 200 && quality > 75)
                Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Encode failed for $path: ${e.message}")
        null
    }

    suspend fun deleteDecline(decline: ParcelDeclineEntity) = withContext(Dispatchers.IO) {
        // Remove the row first so a failure here never leaves a row pointing at deleted pictures.
        dao.deleteDecline(decline)

        // Declines saved together share the same three picture files. Only unlink a file once
        // no other row (pending or uploaded) still references it.
        listOf(decline.pic1Path, decline.pic2Path, decline.pic3Path).distinct().forEach { path ->
            try {
                if (dao.countOtherRowsUsingPicture(decline.id, path) == 0) {
                    File(path).delete()
                } else {
                    Log.d(TAG, "Keeping shared picture $path (still referenced by another decline)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete $path: ${e.message}")
            }
        }
    }

    suspend fun getPending(): List<ParcelDeclineEntity> = withContext(Dispatchers.IO) {
        dao.getPendingDeclines()
    }
}