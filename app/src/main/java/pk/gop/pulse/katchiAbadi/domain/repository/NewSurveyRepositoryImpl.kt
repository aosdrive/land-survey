package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.SpatialReferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.ActiveParcelDao
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.post.Pictures
import pk.gop.pulse.katchiAbadi.data.remote.response.NewSurveyNewDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyImageDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyPersonDao
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonPost
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPostNew
import pk.gop.pulse.katchiAbadi.domain.repository.NewSurveyRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

private const val TAG = "SurveyRepository"

// Helper data class for passing multiple values
private data class ParcelData(
    val geomWKT: String,
    val centroid: String,
    val khewatInfo: String,
    val parcelAreaKMF: String,
    val calculatedArea: String
)

class NewSurveyRepositoryImpl @Inject constructor(
    private val dao: NewSurveyNewDao,
    private val imageDao: SurveyImageDao,
    private val personDao: SurveyPersonDao,
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences,
    private val activeParcelDao: ActiveParcelDao
) : NewSurveyRepository {

    private val wgs84 by lazy {
        SpatialReferences.getWgs84()
    }

    override suspend fun getAllSurveys(): List<NewSurveyNewEntity> {
        return dao.getAllSurveys()
    }

    override suspend fun getActiveParcelById(parcelId: Long): ActiveParcelEntity? {
        return activeParcelDao.getParcelById(parcelId)
    }

    override fun getAllPendingSurveys(): Flow<List<NewSurveyNewEntity>> =
        dao.getAllPendingSurveys()

    override fun getTotalPendingCount(): Flow<Int> =
        dao.liveTotalPendingCount()

    override suspend fun deleteSurvey(survey: NewSurveyNewEntity): Resource<Unit> {
        return try {
            Log.d(TAG, "=== DELETE SURVEY STARTED ===")
            Log.d(TAG, "Survey: parcelId=${survey.parcelId}, parcelNo=${survey.parcelNo}, operation=${survey.parcelOperation}")

            dao.deleteSurvey(survey)
            Log.d(TAG, "Survey deleted from database")

            when (survey.parcelOperation) {
                "Merge" -> {
                    Log.d(TAG, "=== HANDLING MERGE DELETE ===")
                    activeParcelDao.updateSurveyStatus(survey.parcelId, 1)
                    Log.d(TAG, "✓ Reset parent parcel ${survey.parcelId} to unsurveyed")

                    if (survey.parcelOperationValue.isNotBlank()) {
                        val mergedParcelIds = survey.parcelOperationValue
                            .split(",")
                            .mapNotNull { it.trim().toLongOrNull() }
                            .filter { it != survey.parcelId }

                        Log.d(TAG, "Found ${mergedParcelIds.size} merged child parcels: $mergedParcelIds")

                        for (childParcelId in mergedParcelIds) {
                            val childSurveys = dao.getSurveysByParcelId(childParcelId)
                            if (childSurveys.isEmpty()) {
                                activeParcelDao.updateSurveyStatus(childParcelId, 1)
                                Log.d(TAG, "✓ Reset child parcel $childParcelId to unsurveyed")
                            } else {
                                Log.d(TAG, "Child parcel $childParcelId has other surveys, skipping reset")
                            }
                        }
                    }
                }

                "Split" -> {
                    Log.d(TAG, "=== HANDLING SPLIT DELETE ===")
                    val allSplitSurveys = dao.getSurveysByParcelId(survey.parcelId)

                    if (allSplitSurveys.isEmpty()) {
                        activeParcelDao.updateSurveyStatus(survey.parcelId, 1)
                        Log.d(TAG, "✓ Reset split parcel ${survey.parcelId} to unsurveyed")
                    }
                }

                else -> {
                    Log.d(TAG, "=== HANDLING SAME/OTHER DELETE ===")
                    val remainingSurveys = dao.getSurveysByParcelId(survey.parcelId)

                    if (remainingSurveys.isEmpty()) {
                        activeParcelDao.updateSurveyStatus(survey.parcelId, 1)
                        Log.d(TAG, "✓ Reset parcel ${survey.parcelId} to unsurveyed")
                    }
                }
            }

            Log.d(TAG, "=== DELETE SURVEY COMPLETED ===")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}", e)
            Resource.Error(e.message ?: "Delete failed")
        }
    }

    override suspend fun uploadSurvey(
        context: Context,
        survey: NewSurveyNewEntity
    ): Resource<Unit> {
        return try {
            Log.d(TAG, "=== uploadSurvey START ===")
            Log.d(TAG, "App Version: ${Constants.VERSION_NAME}")
            Log.d(TAG, "Survey object: parcelId=${survey.parcelId}, parcelOperation=${survey.parcelOperation}")

            // ===== VALIDATE GROWER CODES =====
            val hasGrowerCodes = withContext(Dispatchers.IO) {
                val persons = personDao.getPersonsForSurvey(survey.pkId)

                if (persons.isEmpty()) {
                    Log.w(TAG, "⚠️ No persons found for survey pkId=${survey.pkId}")
                    // Only fail if this is not a split parcel operation
                    val localParcel = activeParcelDao.getParcelById(survey.parcelId)
                    val isSplitParcel = localParcel != null &&
                            localParcel.subParcelNo.isNotBlank() &&
                            localParcel.subParcelNo != "0"

                    if (!isSplitParcel) {
                        Log.e(TAG, "❌ No persons found for non-split parcel")
                        return@withContext false
                    } else {
                        Log.d(TAG, "ℹ️ Split parcel - may include unsurveyed parcels without persons")
                        return@withContext true
                    }
                }

                val validGrowerCodes = persons.filter { person ->
                    !person.growerCode.isNullOrBlank() && person.growerCode.trim().isNotEmpty()
                }

                Log.d(TAG, "Found ${persons.size} persons, ${validGrowerCodes.size} have grower codes")
                validGrowerCodes.isNotEmpty()
            }

            if (!hasGrowerCodes) {
                Log.e(TAG, "❌ UPLOAD BLOCKED: No grower codes found for survey")
                return Resource.Error("Your grower code data is missing. Please survey again to add grower details.")
            }

            Log.d(TAG, "✅ Grower code validation passed")

            // ✅ NEW: Track if this is a split parcel being uploaded with "Same" operation
            var isSplitParcelWithSameOperation = false
            var originalParcelIdToDeactivate: Long? = null

            // ===== PROCESS PARCELS BASED ON OPERATION =====
            val subparcels = withContext(Dispatchers.IO) {
                when (survey.parcelOperation) {
                    "Split" -> {
                        originalParcelIdToDeactivate = survey.parcelId
                        processSplitParcels(survey)
                    }
                    "Merge" -> processMergeParcels(survey)
                    "Same" -> {
                        // ✅ Check if this is actually a split parcel
                        val localParcel = activeParcelDao.getParcelById(survey.parcelId)
                        if (localParcel != null &&
                            localParcel.subParcelNo.isNotBlank() &&
                            localParcel.subParcelNo != "0") {

                            Log.d(TAG, "⚠️ Detected: Split parcel with 'Same' operation")
                            Log.d(TAG, "   This will be treated as a NEW parcel upload")
                            Log.d(TAG, "   Need to find and deactivate original parent parcel")

                            isSplitParcelWithSameOperation = true

                            // Find the original parent parcel (the one with subParcelNo = "0" or blank)
                            val allParcelsWithSameNumber = activeParcelDao.getActiveParcelsByMauzaAndArea(
                                localParcel.mauzaId,
                                localParcel.areaAssigned
                            ).filter {
                                it.parcelNo == localParcel.parcelNo
                            }

                            // Find original parent
                            val originalParent = allParcelsWithSameNumber.firstOrNull {
                                (it.subParcelNo.isBlank() || it.subParcelNo == "0") &&
                                        it.isActivate == true
                            }

                            if (originalParent != null) {
                                originalParcelIdToDeactivate = originalParent.id
                                Log.d(TAG, "✅ Found original parent parcel to deactivate:")
                                Log.d(TAG, "   ID: ${originalParent.id}")
                                Log.d(TAG, "   ParcelNo: ${originalParent.parcelNo}")
                                Log.d(TAG, "   SubParcelNo: '${originalParent.subParcelNo}'")
                                Log.d(TAG, "   isActivate: ${originalParent.isActivate}")
                            } else {
                                Log.w(TAG, "⚠️ Original parent parcel not found - may already be deactivated")

                                // Check if it exists but is inactive
                                val inactiveParent = activeParcelDao.getParcelsByMauzaAndArea(
                                    localParcel.mauzaId,
                                    localParcel.areaAssigned
                                ).firstOrNull {
                                    it.parcelNo == localParcel.parcelNo &&
                                            (it.subParcelNo.isBlank() || it.subParcelNo == "0")
                                }

                                if (inactiveParent != null) {
                                    Log.d(TAG, "   Found inactive parent: ID=${inactiveParent.id}, isActivate=${inactiveParent.isActivate}")
                                }
                            }
                        }
                        processSameParcels(survey)
                    }
                    else -> processOtherParcels(survey)
                }
            }

            if (subparcels.isEmpty()) {
                Log.e(TAG, "No parcels found to upload")
                return Resource.Error("No parcels found to upload")
            }

            Log.d(TAG, "Found ${subparcels.size} parcels to upload")

            // ===== BUILD SURVEY POSTS =====
            val posts = withContext(Dispatchers.IO) {
                buildSurveyPosts(context, survey, subparcels)
            }

            // ===== UPLOAD TO SERVER =====
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            Log.d(TAG, "=== UPLOAD SUMMARY ===")
            Log.d(TAG, "Total surveys to upload: ${posts.size}")
            Log.d(TAG, "Token present: ${token.isNotEmpty()}")

            posts.forEachIndexed { index, post ->
                Log.d(TAG, "Upload ${index + 1}: ParcelNo=${post.parcelNo}, SubParcel=${post.subParcelNo}, HasGeometry=${post.geomWKT?.isNotEmpty() == true}")
            }

            val response = api.postSurveyDataNew("Bearer $token", posts)

            Log.d(TAG, "=== UPLOAD RESPONSE ===")
            Log.d(TAG, "Response successful: ${response.isSuccessful}")
            Log.d(TAG, "Response code: ${response.code()}")

            when {
                response.isSuccessful && response.body() != null -> {
                    Log.i(TAG, "Survey upload successful - marking surveys as uploaded")
                    withContext(Dispatchers.IO) {
                        // Mark surveys as uploaded
                        subparcels.forEach { subSurvey ->
                            dao.markAsUploaded(subSurvey.pkId)
                            Log.d(TAG, "Marked survey pkId=${subSurvey.pkId} as uploaded")
                        }

                        // ✅ ENHANCED: Deactivate original parcel for split operations
                        if (survey.parcelOperation == "Split" || isSplitParcelWithSameOperation) {
                            if (originalParcelIdToDeactivate != null) {
                                Log.d(TAG, "=== DEACTIVATING ORIGINAL PARCEL AFTER SPLIT UPLOAD ===")
                                Log.d(TAG, "Reason: ${if (survey.parcelOperation == "Split") "Direct split operation" else "Split parcel with 'Same' operation"}")
                                deactivateOriginalParcelWithVerification(originalParcelIdToDeactivate!!)
                            } else {
                                Log.w(TAG, "⚠️ No original parcel ID found to deactivate")
                            }
                        }
                    }
                    Log.i(TAG, "=== UPLOAD COMPLETED SUCCESSFULLY ===")
                    Resource.Success(Unit)
                }

                response.code() == 401 -> handleUnauthorizedResponse(response)
                else -> {
                    val errorBody = response.errorBody()?.string() ?: "Unknown"
                    val errorMessage = "Server error: $errorBody (code ${response.code()})"
                    Log.e(TAG, "Upload failed: $errorMessage")
                    Resource.Error(errorMessage)
                }
            }

        } catch (e: retrofit2.HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during survey upload", e)
            Resource.Error(e.message ?: "Unexpected error")
        }
    }

    // ===== HELPER: DEACTIVATE ORIGINAL PARCEL WITH VERIFICATION =====
    private suspend fun deactivateOriginalParcelWithVerification(originalParcelId: Long) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "--- Deactivating Original Parcel After Split Upload ---")

                // Get original parcel before deactivation
                val parcelBefore = activeParcelDao.getParcelById(originalParcelId)
                if (parcelBefore == null) {
                    Log.e(TAG, "❌ Original parcel not found with ID: $originalParcelId")
                    return@withContext
                }

                Log.d(TAG, "✅ Found original parcel:")
                Log.d(TAG, "   ID: ${parcelBefore.id}")
                Log.d(TAG, "   ParcelNo: ${parcelBefore.parcelNo}")
                Log.d(TAG, "   SubParcelNo: '${parcelBefore.subParcelNo}'")
                Log.d(TAG, "   isActivate (BEFORE): ${parcelBefore.isActivate}")

                // Deactivate the parcel
                activeParcelDao.deactivateParcel(originalParcelId)
                Log.d(TAG, "✅ Executed deactivation for parcel ID: $originalParcelId")

                // Verify deactivation
                val parcelAfter = activeParcelDao.getParcelById(originalParcelId)
                if (parcelAfter != null) {
                    Log.d(TAG, "✅ Verification - Original parcel after deactivation:")
                    Log.d(TAG, "   ID: ${parcelAfter.id}")
                    Log.d(TAG, "   ParcelNo: ${parcelAfter.parcelNo}")
                    Log.d(TAG, "   SubParcelNo: '${parcelAfter.subParcelNo}'")
                    Log.d(TAG, "   isActivate (AFTER): ${parcelAfter.isActivate}")

                    if (parcelAfter.isActivate == false) {
                        Log.d(TAG, "✅✅ SUCCESS: Original parcel successfully deactivated!")
                    } else {
                        Log.e(TAG, "❌❌ ERROR: Original parcel still active after deactivation attempt!")
                    }
                } else {
                    Log.e(TAG, "❌ Parcel not found after deactivation attempt")
                }

                // Count active split parcels
                val activeSplitParcels = activeParcelDao.getActiveParcelsByMauzaAndArea(
                    parcelBefore.mauzaId,
                    parcelBefore.areaAssigned
                ).filter {
                    it.parcelNo == parcelBefore.parcelNo &&
                            it.isActivate == true &&
                            it.subParcelNo.isNotBlank() &&
                            it.subParcelNo != "0"
                }

                Log.d(TAG, "=== SPLIT PARCEL STATUS ===")
                Log.d(TAG, "Active split parcels for ParcelNo ${parcelBefore.parcelNo}: ${activeSplitParcels.size}")
                activeSplitParcels.forEachIndexed { index, splitParcel ->
                    Log.d(TAG, "Split Parcel ${index + 1}:")
                    Log.d(TAG, "   ID: ${splitParcel.id}")
                    Log.d(TAG, "   SubParcelNo: ${splitParcel.subParcelNo}")
                    Log.d(TAG, "   isActivate: ${splitParcel.isActivate}")
                }

                Log.d(TAG, "=== DEACTIVATION COMPLETE ===")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deactivating original parcel: ${e.message}", e)
            }
        }
    }

    private suspend fun processSplitParcels(survey: NewSurveyNewEntity): List<NewSurveyNewEntity> {
        Log.d(TAG, "=== PROCESSING SPLIT PARCELS FOR UPLOAD ===")
        Log.d(TAG, "Processing split parcel upload for parcelId=${survey.parcelId}")

        val originalParcel = activeParcelDao.getParcelById(survey.parcelId)
        if (originalParcel == null) {
            Log.e(TAG, "❌ Original parcel not found")
            return emptyList()
        }

        Log.d(TAG, "✅ Original parcel details:")
        Log.d(TAG, "   ID: ${originalParcel.id}")
        Log.d(TAG, "   ParcelNo: ${originalParcel.parcelNo}")
        Log.d(TAG, "   SubParcelNo: ${originalParcel.subParcelNo}")
        Log.d(TAG, "   isActivate: ${originalParcel.isActivate}")
        Log.d(TAG, "   This parcel ID will be sent to server for deactivation")

        val mauzaId = originalParcel.mauzaId
        val areaName = originalParcel.areaAssigned
        val allActiveParcels = activeParcelDao.getActiveParcelsByMauzaAndArea(mauzaId, areaName)

        // ✅ Get all split parcels (with non-empty subParcelNo)
        val splitParcels = allActiveParcels.filter {
            it.parcelNo == originalParcel.parcelNo &&
                    it.isActivate == true &&
                    it.subParcelNo.isNotBlank() &&
                    it.subParcelNo != "0"
        }.sortedBy { it.subParcelNo }

        Log.d(TAG, "=== SPLIT PARCELS FOUND ===")
        Log.d(TAG, "Total split parcels to upload: ${splitParcels.size}")

        splitParcels.forEachIndexed { index, parcel ->
            Log.d(TAG, "Split Parcel ${index + 1}:")
            Log.d(TAG, "   Local ID: ${parcel.id}")
            Log.d(TAG, "   ParcelNo: ${parcel.parcelNo}")
            Log.d(TAG, "   SubParcelNo: ${parcel.subParcelNo}")
            Log.d(TAG, "   SurveyStatus: ${parcel.surveyStatusCode}")
            Log.d(TAG, "   isActivate: ${parcel.isActivate}")
            Log.d(TAG, "   GeomWKT: ${if (parcel.geomWKT.isNullOrEmpty()) "❌ NULL/EMPTY" else "✅ Present (${parcel.geomWKT.length} chars)"}")
        }

        val splitSurveys = mutableListOf<NewSurveyNewEntity>()

        // ✅ Upload ALL split parcels (surveyed and unsurveyed)
        splitParcels.forEach { splitParcel ->
            // ✅ CRITICAL FIX: Use splitParcel.id so geometry lookup works
            val splitSurvey = survey.copy(
                pkId = 0,
                parcelId = splitParcel.id,                   // ✅ Use split parcel's local ID
                parcelNo = splitParcel.parcelNo.toString(),  // ✅ Use split parcel number
                subParcelNo = splitParcel.subParcelNo,       // ✅ Use split parcel's subParcelNo
                parcelOperation = "New",
                parcelOperationValue = survey.parcelId.toString() // ✅ Original parcel ID for server to deactivate
            )
            splitSurveys.add(splitSurvey)
            Log.d(TAG, "✅ Created survey record:")
            Log.d(TAG, "   ParcelId: ${splitParcel.id}")
            Log.d(TAG, "   SubParcel: ${splitParcel.subParcelNo}")
            Log.d(TAG, "   Operation: New")
            Log.d(TAG, "   OriginalParcelID: ${survey.parcelId}")
        }

        Log.d(TAG, "=== SPLIT PARCEL PROCESSING COMPLETE ===")
        Log.d(TAG, "Created ${splitSurveys.size} survey records (including unsurveyed split parcels)")
        return splitSurveys
    }
    // ===== PROCESS MERGE PARCELS =====
// ===== PROCESS MERGE PARCELS (FINAL CORRECT FIX) =====
    //
    // SCENARIO: Merging a split parcel (e.g. ParcelNo=775, SubParcel=2) with normal parcels
    //
    // RULES:
    //   1. Split parcel (SubParcel != "" && != "0") → send as "New"
    //      - If original parent is still ACTIVE locally  → ParcelOperationValue = originalParentId
    //        (first split parcel being uploaded, server needs to deactivate original)
    //      - If original parent is already INACTIVE locally → ParcelOperationValue = ""
    //        (original already deactivated when first split was uploaded, don't deactivate again)
    //   2. Normal merged parcels → always send as "Merge" with empty ParcelOperationValue
    //
// ===== PROCESS MERGE PARCELS (FINAL CORRECT FIX - includes unsurveyed sibling) =====
    //
    // FULL FLOW for split parcel in merge:
    //   1. Unsurveyed sibling split parcel(s) → send as "New" with empty persons/pictures
    //      (same as pure split upload — server creates unsurveyed record)
    //   2. Surveyed split parcel (main) → send as "New" with full survey data
    //   3. Normal merged parcels → send as "Merge" with empty ParcelOperationValue
    //
    // Original parent deactivation:
    //   - Only sent via opValue on the FIRST split parcel to be uploaded
    //   - If original is already inactive → empty opValue (don't deactivate again)
    //
    private suspend fun processMergeParcels(survey: NewSurveyNewEntity): List<NewSurveyNewEntity> {
        Log.d(TAG, "=== PROCESS MERGE PARCELS ===")
        Log.d(TAG, "Main parcelId=${survey.parcelId}, operation=${survey.parcelOperation}")

        val mainParcel = activeParcelDao.getParcelById(survey.parcelId)
        if (mainParcel == null) {
            Log.e(TAG, "❌ Main parcel not found for ID=${survey.parcelId}")
            return emptyList()
        }

        Log.d(TAG, "Main parcel => ParcelNo=${mainParcel.parcelNo}, SubParcelNo='${mainParcel.subParcelNo}', isActivate=${mainParcel.isActivate}")

        val mainIsSplitParcel = mainParcel.subParcelNo.isNotBlank() && mainParcel.subParcelNo != "0"
        Log.d(TAG, "Main parcel isSplitParcel=$mainIsSplitParcel")

        val mergeSurveys = mutableListOf<NewSurveyNewEntity>()

        val mergedParcelIds = survey.parcelOperationValue
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it != survey.parcelId }

        Log.d(TAG, "Other parcels to merge: $mergedParcelIds")

        if (mainIsSplitParcel) {
            // =========================================================
            // CASE: Main parcel is a SPLIT parcel
            // =========================================================
            Log.d(TAG, "=== SPLIT PARCEL MERGE FLOW ===")

            // Find original parent (subParcelNo = "" or "0")
            val allParcelsWithSameNumber = activeParcelDao.getParcelsByMauzaAndArea(
                mainParcel.mauzaId,
                mainParcel.areaAssigned
            ).filter {
                it.parcelNo == mainParcel.parcelNo &&
                        (it.subParcelNo.isBlank() || it.subParcelNo == "0")
            }
            val originalParent = allParcelsWithSameNumber.firstOrNull()

            Log.d(TAG, "Original parent: ${
                if (originalParent != null)
                    "ID=${originalParent.id}, isActivate=${originalParent.isActivate}"
                else "NOT FOUND"
            }")

            // ✅ Find ALL sibling split parcels (same parcelNo, different subParcelNo)
            // These are the unsurveyed siblings that also need to be sent to server
            val allSiblingsSplitParcels = activeParcelDao.getParcelsByMauzaAndArea(
                mainParcel.mauzaId,
                mainParcel.areaAssigned
            ).filter {
                it.parcelNo == mainParcel.parcelNo &&
                        it.subParcelNo.isNotBlank() &&
                        it.subParcelNo != "0" &&
                        it.id != mainParcel.id  // exclude the main (surveyed) one
            }

            Log.d(TAG, "Found ${allSiblingsSplitParcels.size} unsurveyed sibling split parcel(s): ${allSiblingsSplitParcels.map { "ID=${it.id}, SubParcel=${it.subParcelNo}" }}")

            // Determine opValue for original parent deactivation
            val originalIsStillActive = originalParent?.isActivate == true
            val originalParentIdStr = if (originalIsStillActive) originalParent!!.id.toString() else ""

            Log.d(TAG, "Original parent deactivation: ${if (originalIsStillActive) "YES → ID=${originalParent!!.id}" else "NO (already inactive or not found)"}")

            // ✅ STEP 1: Send unsurveyed sibling split parcels FIRST
            // They need the deactivation opValue only on the FIRST one sent
            // (to mirror what pure split upload does)
            var deactivationOpValueUsed = false

            allSiblingsSplitParcels.forEachIndexed { index, sibling ->
                val siblingOpValue = if (!deactivationOpValueUsed && originalIsStillActive) {
                    deactivationOpValueUsed = true
                    originalParentIdStr  // first sibling carries deactivation request
                } else {
                    ""  // subsequent siblings send empty opValue
                }

                // ✅ Create an UNSURVEYED survey record for this sibling
                // No persons, no pictures — server will create it with SurveyStatusCode=1
                val siblingSurvey = survey.copy(
                    parcelId = sibling.id,
                    parcelNo = sibling.parcelNo.toString(),
                    subParcelNo = sibling.subParcelNo,
                    parcelOperation = "New",
                    parcelOperationValue = siblingOpValue,
                    // ✅ Clear survey-specific data so server treats as unsurveyed
                    propertyType = "",      // empty → isSurveyed=false on server
                    ownershipStatus = "",
                    variety = "",
                    cropType = "",
                    crop = "",
                    year = "",
                    area = "",
                    remarks = "",
                    isGeometryCorrect = false
                )
                mergeSurveys.add(siblingSurvey)

                Log.d(TAG, "✅ Added UNSURVEYED sibling split parcel as 'New':")
                Log.d(TAG, "   parcelId=${sibling.id}, ParcelNo=${sibling.parcelNo}, SubParcelNo=${sibling.subParcelNo}")
                Log.d(TAG, "   parcelOperationValue='$siblingOpValue'")
            }

            // ✅ STEP 2: Send the SURVEYED main split parcel
            val mainOpValue = if (!deactivationOpValueUsed && originalIsStillActive) {
                // No siblings were sent yet, main carries deactivation
                originalParentIdStr
            } else {
                // Siblings already carried deactivation, or original already inactive
                ""
            }

            val splitParcelSurvey = survey.copy(
                parcelId = mainParcel.id,
                parcelNo = mainParcel.parcelNo.toString(),
                subParcelNo = mainParcel.subParcelNo,
                parcelOperation = "New",
                parcelOperationValue = mainOpValue
                // ✅ Keep all survey data (persons, pictures, propertyType etc.) intact
            )
            mergeSurveys.add(splitParcelSurvey)

            Log.d(TAG, "✅ Added SURVEYED main split parcel as 'New':")
            Log.d(TAG, "   parcelId=${mainParcel.id}, ParcelNo=${mainParcel.parcelNo}, SubParcelNo=${mainParcel.subParcelNo}")
            Log.d(TAG, "   parcelOperationValue='$mainOpValue'")

            // ✅ STEP 3: Add normal merged parcels as "Merge"
            mergedParcelIds.forEach { mergedParcelId ->
                addMergedParcel(survey, mergedParcelId, mergeSurveys)
            }

        } else {
            // =========================================================
            // CASE: Main parcel is a NORMAL parcel (unchanged)
            // =========================================================
            Log.d(TAG, "=== NORMAL PARCEL MERGE FLOW ===")

            val mainSurvey = survey.copy(
                parcelOperation = "Merge",
                parcelOperationValue = survey.parcelOperationValue
            )
            mergeSurveys.add(mainSurvey)
            Log.d(TAG, "✅ Added main normal parcel as 'Merge' with value: ${survey.parcelOperationValue}")

            mergedParcelIds.forEach { mergedParcelId ->
                addMergedParcel(survey, mergedParcelId, mergeSurveys)
            }
        }

        Log.d(TAG, "=== MERGE PROCESSING COMPLETE: ${mergeSurveys.size} records ===")
        mergeSurveys.forEachIndexed { i, s ->
            Log.d(TAG, "  Record ${i + 1}: parcelId=${s.parcelId}, ParcelNo=${s.parcelNo}, SubParcel=${s.subParcelNo}, op=${s.parcelOperation}, opValue='${s.parcelOperationValue}', propertyType='${s.propertyType}'")
        }
        return mergeSurveys
    }

    // ===== HELPER: Add a single merged parcel (normal or split) =====
    private suspend fun addMergedParcel(
        survey: NewSurveyNewEntity,
        mergedParcelId: Long,
        mergeSurveys: MutableList<NewSurveyNewEntity>
    ) {
        val mergedParcel = activeParcelDao.getParcelById(mergedParcelId)
        if (mergedParcel == null) {
            Log.e(TAG, "❌ Merged parcel not found locally for ID=$mergedParcelId")
            return
        }

        val mergedIsSplit = mergedParcel.subParcelNo.isNotBlank() && mergedParcel.subParcelNo != "0"
        Log.d(TAG, "Processing merged parcel ID=$mergedParcelId: ParcelNo=${mergedParcel.parcelNo}, SubParcelNo='${mergedParcel.subParcelNo}', isSplit=$mergedIsSplit")

        if (mergedIsSplit) {
            // Split parcel being merged — send as "New" with deactivation logic
            val allParcelsWithSameNumber = activeParcelDao.getParcelsByMauzaAndArea(
                mergedParcel.mauzaId,
                mergedParcel.areaAssigned
            ).filter {
                it.parcelNo == mergedParcel.parcelNo &&
                        (it.subParcelNo.isBlank() || it.subParcelNo == "0")
            }
            val originalParent = allParcelsWithSameNumber.firstOrNull()
            val opValue = if (originalParent?.isActivate == true) originalParent.id.toString() else ""

            val mergedSurvey = survey.copy(
                pkId = survey.pkId,
                parcelId = mergedParcel.id,
                parcelNo = mergedParcel.parcelNo.toString(),
                subParcelNo = mergedParcel.subParcelNo,
                parcelOperation = "New",
                parcelOperationValue = opValue
            )
            mergeSurveys.add(mergedSurvey)
            Log.d(TAG, "✅ Added split merged parcel as 'New': ID=${mergedParcel.id}, opValue='$opValue'")
        } else {
            // Normal parcel — send as "Merge"
            val mergedSurvey = survey.copy(
                pkId = survey.pkId,
                parcelId = mergedParcelId,
                parcelNo = mergedParcel.parcelNo.toString(),
                subParcelNo = mergedParcel.subParcelNo,
                parcelOperation = "Merge",
                parcelOperationValue = ""
            )
            mergeSurveys.add(mergedSurvey)
            Log.d(TAG, "✅ Added normal merged parcel as 'Merge': ID=$mergedParcelId, ParcelNo=${mergedParcel.parcelNo}")
        }
    }
    // ===== getParcelDataForOperation =====
    private suspend fun getParcelDataForOperation(subSurvey: NewSurveyNewEntity): ParcelData {
        Log.d(TAG, "=== Getting parcel data for operation ===")
        Log.d(TAG, "ParcelId: ${subSurvey.parcelId}, Op: ${subSurvey.parcelOperation}, SubParcel: ${subSurvey.subParcelNo}")

        return when (subSurvey.parcelOperation) {

            "New" -> {
                // Always fetch geometry from the local parcel ID directly
                // (works for both split-only uploads AND split-in-merge uploads)
                val parcel = activeParcelDao.getParcelById(subSurvey.parcelId)
                if (parcel != null) {
                    val calculatedArea = calculateAreaFromGeometry(parcel.geomWKT)
                    Log.d(TAG, "✅ [New] Found parcel ID=${parcel.id}, SubParcel=${parcel.subParcelNo}, area=$calculatedArea, geomLen=${parcel.geomWKT?.length ?: 0}")

                    if (parcel.geomWKT.isNullOrEmpty()) {
                        Log.e(TAG, "❌❌ CRITICAL: Parcel ${parcel.id} has NO geometry!")
                    }

                    // Get khewatInfo / parcelAreaKMF from original parent if sub-parcel
                    val isSub = parcel.subParcelNo.isNotBlank() && parcel.subParcelNo != "0"
                    val khewatParent = if (isSub) {
                        activeParcelDao.getParcelsByMauzaAndArea(
                            parcel.mauzaId, parcel.areaAssigned
                        ).firstOrNull {
                            it.parcelNo == parcel.parcelNo &&
                                    (it.subParcelNo.isBlank() || it.subParcelNo == "0")
                        }
                    } else null

                    ParcelData(
                        geomWKT = parcel.geomWKT ?: "",
                        centroid = parcel.centroid ?: "",
                        khewatInfo = khewatParent?.khewatInfo ?: parcel.khewatInfo ?: "",
                        parcelAreaKMF = khewatParent?.parcelAreaKMF ?: parcel.parcelAreaKMF ?: "",
                        calculatedArea = calculatedArea
                    )
                } else {
                    Log.e(TAG, "❌❌ [New] Parcel NOT FOUND for ID=${subSurvey.parcelId}")
                    ParcelData("", "", "", "", "")
                }
            }

            "Same", "Merge" -> {
                val parcel = activeParcelDao.getParcelById(subSurvey.parcelId)
                if (parcel != null) {
                    val calculatedArea = calculateAreaFromGeometry(parcel.geomWKT)
                    Log.d(TAG, "✅ [${subSurvey.parcelOperation}] Found parcel ID=${parcel.id}, SubParcel=${parcel.subParcelNo}, area=$calculatedArea, geomLen=${parcel.geomWKT?.length ?: 0}")
                    ParcelData(
                        geomWKT = parcel.geomWKT ?: "",
                        centroid = parcel.centroid ?: "",
                        khewatInfo = parcel.khewatInfo ?: "",
                        parcelAreaKMF = parcel.parcelAreaKMF ?: "",
                        calculatedArea = calculatedArea
                    )
                } else {
                    Log.e(TAG, "❌ [${subSurvey.parcelOperation}] Parcel NOT FOUND for ID=${subSurvey.parcelId}")
                    ParcelData("", "", "", "", "")
                }
            }

            else -> {
                Log.e(TAG, "❌ Unknown operation: ${subSurvey.parcelOperation}")
                ParcelData("", "", "", "", "")
            }
        }
    }

    // ===== PROCESS SAME PARCELS =====
    private suspend fun processSameParcels(survey: NewSurveyNewEntity): List<NewSurveyNewEntity> {
        Log.d(TAG, "Processing 'Same' operation for parcelId=${survey.parcelId}")
        val localParcel = activeParcelDao.getParcelById(survey.parcelId)

        if (localParcel != null && localParcel.subParcelNo.isNotBlank() && localParcel.subParcelNo != "0") {
            Log.d(TAG, "Detected split parcel with 'Same' operation - need to upload ALL split parcels")
            Log.d(TAG, "Parcel details: ID=${localParcel.id}, ParcelNo=${localParcel.parcelNo}, SubParcel=${localParcel.subParcelNo}")

            // ✅ Find all split parcels with the same ParcelNo
            val allSplitParcels = activeParcelDao.getActiveParcelsByMauzaAndArea(
                localParcel.mauzaId,
                localParcel.areaAssigned
            ).filter {
                it.parcelNo == localParcel.parcelNo &&
                        it.isActivate == true &&
                        it.subParcelNo.isNotBlank() &&
                        it.subParcelNo != "0"
            }.sortedBy { it.subParcelNo }

            Log.d(TAG, "Found ${allSplitParcels.size} total split parcels for ParcelNo ${localParcel.parcelNo}")

            // ✅ Find original parent for server deactivation
            val allParcelsWithSameNumber = activeParcelDao.getParcelsByMauzaAndArea(
                localParcel.mauzaId,
                localParcel.areaAssigned
            ).filter {
                it.parcelNo == localParcel.parcelNo
            }

            val originalParent = allParcelsWithSameNumber.firstOrNull {
                it.subParcelNo.isBlank() || it.subParcelNo == "0"
            }

            val originalParentId = originalParent?.id?.toString() ?: ""
            Log.d(TAG, "Original parent parcel for server deactivation: ID=$originalParentId")

            val cleanSurveys = mutableListOf<NewSurveyNewEntity>()

            // ✅ Create survey records for ALL split parcels
            allSplitParcels.forEach { splitParcel ->
                // Check if this split parcel has been surveyed
                val existingSurvey = if (splitParcel.id == survey.parcelId) {
                    // This is the current parcel being uploaded - use its survey data
                    dao.getCompleteRecord(survey.parcelId).firstOrNull()
                } else {
                    // Check if this split parcel has a survey
                    dao.getSurveysByParcelId(splitParcel.id).firstOrNull()
                }

                if (existingSurvey != null) {
                    // ✅ CRITICAL FIX: Preserve correct parcelId, parcelNo, and subParcelNo from split parcel
                    val surveyRecord = existingSurvey.copy(
                        parcelId = splitParcel.id,           // ✅ Use split parcel ID
                        parcelNo = splitParcel.parcelNo.toString(), // ✅ Use split parcel number
                        subParcelNo = splitParcel.subParcelNo,      // ✅ Use split parcel's subParcelNo
                        parcelOperation = "New",
                        parcelOperationValue = originalParentId
                    )
                    cleanSurveys.add(surveyRecord)
                    Log.d(TAG, "✅ Added SURVEYED split parcel: ID=${splitParcel.id}, SubParcel=${splitParcel.subParcelNo}, HasSurveyData=true")
                } else {
                    // ✅ CRITICAL FIX: Create minimal record with correct IDs from split parcel
                    val minimalSurvey = survey.copy(
                        pkId = 0,
                        parcelId = splitParcel.id,                  // ✅ Use split parcel ID
                        parcelNo = splitParcel.parcelNo.toString(), // ✅ Use split parcel number
                        subParcelNo = splitParcel.subParcelNo,      // ✅ Use split parcel's subParcelNo
                        parcelOperation = "New",
                        parcelOperationValue = originalParentId,
                        propertyType = "",
                        ownershipStatus = "",
                        variety = "",
                        cropType = "",
                        crop = "",
                        year = "",
                        area = "",
                        remarks = "",
                        isGeometryCorrect = false
                    )
                    cleanSurveys.add(minimalSurvey)
                    Log.d(TAG, "⚠️ Added UNSURVEYED split parcel: ID=${splitParcel.id}, SubParcel=${splitParcel.subParcelNo}, HasSurveyData=false")
                }
            }

            Log.d(TAG, "Converted ${cleanSurveys.size} survey records (surveyed + unsurveyed split parcels)")
            return cleanSurveys
        } else {
            Log.d(TAG, "Normal 'Same' operation - proceeding with existing parcel")
            return dao.getCompleteRecord(survey.parcelId)
        }
    }

    // ===== PROCESS OTHER PARCELS =====
    private suspend fun processOtherParcels(survey: NewSurveyNewEntity): List<NewSurveyNewEntity> {
        Log.d(TAG, "Processing other operation: ${survey.parcelOperation}")
        return dao.getCompleteRecord(survey.parcelId)
    }

    // ===== BUILD SURVEY POSTS =====
    private suspend fun buildSurveyPosts(
        context: Context,
        survey: NewSurveyNewEntity,
        subparcels: List<NewSurveyNewEntity>
    ): List<SurveyPostNew> {
        return subparcels.mapIndexed { index, subSurvey ->
            Log.d(TAG, "=== Processing Survey ${index + 1}/${subparcels.size} ===")
            Log.d(TAG, "Survey Details: pkId=${subSurvey.pkId}, parcelNo=${subSurvey.parcelNo}, subParcelNo=${subSurvey.subParcelNo}")

            val sourceSurveyPkId = subSurvey.pkId

            // ✅ Check if this is a surveyed or unsurveyed parcel
            val isSurveyed = sourceSurveyPkId > 0 && subSurvey.propertyType.isNotBlank()

            // Get images (only for surveyed parcels)
            val images = if (isSurveyed) {
                imageDao.getImagesBySurvey(sourceSurveyPkId)
            } else {
                emptyList()
            }
            val pictures = convertSurveyImagesToPictures(context, images)
            Log.d(TAG, "Found ${images.size} images for survey pkId=${sourceSurveyPkId}")

            // Get persons (only for surveyed parcels)
            val personsEntities = if (isSurveyed) {
                personDao.getPersonsForSurvey(sourceSurveyPkId)
            } else {
                emptyList()
            }
            val persons = convertPersonsToSurveyPersonPost(personsEntities)
            Log.d(TAG, "Found ${personsEntities.size} persons for survey pkId=${sourceSurveyPkId}")

            // ✅ Get parcel data (geometry, khewatInfo, etc.)
            val parcelData = getParcelDataForOperation(subSurvey)

            Log.d(TAG, "=== PARCEL DATA ===")
            Log.d(TAG, "IsSurveyed: $isSurveyed")
            Log.d(TAG, "ParcelId: ${subSurvey.parcelId}")
            Log.d(TAG, "SubParcelNo: ${subSurvey.subParcelNo}")
            Log.d(TAG, "CalculatedArea: ${parcelData.calculatedArea} Acres")
            Log.d(TAG, "GeomWKT: ${if (parcelData.geomWKT.isNotEmpty()) "Present (${parcelData.geomWKT.length} chars)" else "❌ EMPTY/NULL"}")
            Log.d(TAG, "Centroid: ${if (parcelData.centroid.isNotEmpty()) parcelData.centroid else "❌ EMPTY/NULL"}")
            Log.d(TAG, "KhewatInfo: ${parcelData.khewatInfo}")
            Log.d(TAG, "ParcelAreaKMF: ${parcelData.parcelAreaKMF}")

            if (parcelData.geomWKT.isEmpty()) {
                Log.e(TAG, "❌❌ CRITICAL ERROR: Empty geometry for parcel ${subSurvey.parcelNo}-${subSurvey.subParcelNo}")
            }
            // Build the survey post
            val surveyPost = buildCleanSurveyPostNew(
                subSurvey,
                pictures,
                persons,
                parcelData.geomWKT,
                parcelData.centroid,
                parcelData.khewatInfo,
                parcelData.parcelAreaKMF,
                parcelData.calculatedArea,
                100 // distance
            )

            logSurveyPost(surveyPost, index)
            surveyPost
        }
    }


    private suspend fun calculateAreaFromGeometry(geomWKT: String?): String {
        return withContext(Dispatchers.Default) {
            try {
                if (geomWKT.isNullOrEmpty()) {
                    Log.w(TAG, "Empty geometry provided for area calculation")
                    return@withContext ""
                }

                // Handle both POLYGON and MULTIPOLYGON
                val polygon = when {
                    geomWKT.contains("MULTIPOLYGON") -> {
                        val polygons = Utility.getMultiPolygonFromString(geomWKT, wgs84)
                        polygons?.firstOrNull()
                    }
                    geomWKT.contains("POLYGON") -> {
                        Utility.getPolygonFromString(geomWKT, wgs84)
                    }
                    else -> null
                }

                if (polygon != null && !polygon.isEmpty) {
                    // Calculate area in square feet
                    val areaSqFt = GeometryEngine.areaGeodetic(
                        polygon,
                        AreaUnit(AreaUnitId.SQUARE_FEET),
                        GeodeticCurveType.NORMAL_SECTION
                    )

                    // Convert to Acres (1 Acre = 43,560 sq ft)
                    val areaAcres = areaSqFt / 43560.0

                    val formattedArea = String.format(Locale.US, "%.4f", areaAcres)
                    Log.d(TAG, "Area calculation: $areaSqFt sq ft = $formattedArea Acres")

                    formattedArea
                } else {
                    Log.e(TAG, "Failed to parse geometry for area calculation")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating area from geometry: ${e.message}", e)
                ""
            }
        }
    }

    // ===== BUILD CLEAN SURVEY POST =====
    private fun buildCleanSurveyPostNew(
        survey: NewSurveyNewEntity,
        pictures: List<Pictures>,
        persons: List<SurveyPersonPost>,
        geomWKT: String,
        centroid: String,
        khewatInfo: String,
        parcelAreaKMF: String,
        calculatedArea: String,
        distance: Int = 100
    ): SurveyPostNew {
        // ✅ For split parcels (or any "New" operation), don't send parcel ID
        val parcelIdToSend = if (survey.parcelOperation == "New") {
            0L // Let server create new
        } else {
            survey.parcelId ?: 0L
        }

        return SurveyPostNew(
            propertyType = survey.propertyType,
            ownershipStatus = survey.ownershipStatus,
            variety = survey.variety,
            cropType = survey.cropType,
            crop = survey.crop,
            year = survey.year,
            area = survey.area,
            calculatedArea = calculatedArea,
            isGeometryCorrect = survey.isGeometryCorrect,
            remarks = survey.remarks,
            mauzaId = survey.mauzaId,
            areaName = survey.areaName,
            parcelId = parcelIdToSend,
            parcelNo = survey.parcelNo,
            subParcelNo = survey.subParcelNo,
            parcelOperation = survey.parcelOperation,
            parcelOperationValue = survey.parcelOperationValue,
            geomWKT = if (geomWKT.isNotEmpty()) geomWKT else null,
            centriod = if (centroid.isNotEmpty()) centroid else null,
            khewatInfo = khewatInfo,
            parcelAreaKMF = parcelAreaKMF,
            distance = distance,
            pictures = pictures,
            persons = persons
        )
    }

    // ===== HELPER: CONVERT PERSONS =====
    private fun convertPersonsToSurveyPersonPost(personsEntities: List<SurveyPersonEntity>): List<SurveyPersonPost> {
        return personsEntities.map { person ->
            SurveyPersonPost(
                personId = person.id ?: 0L,
                firstName = person.firstName ?: "",
                lastName = person.lastName ?: "",
                gender = person.gender ?: "",
                relation = person.relation ?: "",
                religion = person.religion ?: "",
                mobile = person.mobile ?: "",
                nic = person.nic ?: "",
                growerCode = person.growerCode ?: "",
                personArea = person.personArea?.toString() ?: "",
                ownershipType = person.ownershipType ?: "",
                address = person.address,
                extra1 = person.extra1 ?: "",
                extra2 = person.extra2 ?: "",
                mauzaId = person.mauzaId ?: 0L,
                mauzaName = person.mauzaName ?: ""
            )
        }
    }

    // ===== HELPER: CONVERT IMAGES =====
    private fun convertSurveyImagesToPictures(
        context: Context,
        images: List<SurveyImage>
    ): List<Pictures> = images.map { img ->
        Log.d(TAG, "=== Converting Image to Pictures ===")
        Log.d(TAG, "Image ID: ${img.id}, Type: ${img.type}")

        val base64Encoded = try {
            val uri = Uri.parse(img.uri)
            val inputStream = when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> File(uri.path ?: "").inputStream()
                null -> File(img.uri).inputStream()
                else -> null
            }

            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bmp ->
                    ByteArrayOutputStream().use { os ->
                        var quality = 100
                        do {
                            os.reset()
                            bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)
                            quality -= 5
                        } while (os.size() / 1024 > 200 && quality > 75)
                        Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
                    }
                } ?: "Image decoding failed"
            } ?: "InputStream not found"
        } catch (e: Exception) {
            "Image not found: ${e.localizedMessage}"
        }

        Pictures(
            Number = img.id.toInt(),
            Type = img.type,
            PicData = base64Encoded,
            OtherType = img.type,
            Latitude = img.latitude,
            Longitude = img.longitude,
            Timestamp = img.timestamp,
            LocationAddress = img.locationAddress,
            Bearing = img.bearing
        )
    }

    // ===== HELPER: LOG SURVEY POST =====
    private fun logSurveyPost(surveyPost: SurveyPostNew, index: Int) {
        Log.d(TAG, "=== Survey Post ${index + 1} Details ===")
        Log.d(TAG, "ParcelNo: ${surveyPost.parcelNo}, SubParcel: ${surveyPost.subParcelNo}")
        Log.d(TAG, "ParcelId: ${surveyPost.parcelId}")
        Log.d(TAG, "Parcel Operation: ${surveyPost.parcelOperation}")
        Log.d(TAG, "Parcel Operation Value: ${surveyPost.parcelOperationValue}")
        Log.d(TAG, "CalculatedArea: ${surveyPost.calculatedArea} Acres")

        // ✅ CRITICAL VALIDATION
        if (surveyPost.geomWKT.isNullOrEmpty()) {
            Log.e(TAG, "❌❌ CRITICAL ERROR: GeomWKT is NULL/EMPTY for ${surveyPost.parcelNo}-${surveyPost.subParcelNo}")
            Log.e(TAG, "   This will cause NULL geometry in SQL Server!")
        } else {
            Log.d(TAG, "✅ GeomWKT: Present (${surveyPost.geomWKT.length} chars)")
            Log.d(TAG, "   Preview: ${surveyPost.geomWKT.take(100)}...")
        }

        if (surveyPost.centriod.isNullOrEmpty()) {
            Log.e(TAG, "❌ WARNING: Centroid is NULL/EMPTY")
        } else {
            Log.d(TAG, "✅ Centroid: ${surveyPost.centriod}")
        }

        if (surveyPost.parcelOperation == "New" && surveyPost.parcelOperationValue.isNotBlank()) {
            Log.d(TAG, "📤 SERVER DEACTIVATION REQUEST:")
            Log.d(TAG, "   Original Parcel ID to deactivate: ${surveyPost.parcelOperationValue}")
            Log.d(TAG, "   New Parcel being created: ${surveyPost.parcelNo}-${surveyPost.subParcelNo}")
        }

        Log.d(TAG, "KhewatInfo: ${surveyPost.khewatInfo}")
        Log.d(TAG, "Pictures Count: ${surveyPost.pictures.size}")
        Log.d(TAG, "Persons Count: ${surveyPost.persons.size}")
    }

    // ===== HELPER: HANDLE UNAUTHORIZED =====
    private fun handleUnauthorizedResponse(response: retrofit2.Response<*>): Resource<Unit> {
        val errorBody = response.errorBody()?.string() ?: ""
        Log.e(TAG, "401 Unauthorized - Error body: $errorBody")

        try {
            val gson = com.google.gson.Gson()
            val errorMap = gson.fromJson(errorBody, Map::class.java)

            val isUpdateRequired = errorMap["isUpdateRequired"] as? Boolean ?: false
            val shouldLogout = errorMap["shouldLogout"] as? Boolean ?: false
            val message = errorMap["message"] as? String ?: ""

            if (isUpdateRequired || shouldLogout) {
                Log.e(TAG, "VERSION OUTDATED: $message")
                sharedPreferences.edit().clear().apply()
                return Resource.Error("APP_VERSION_OUTDATED: $message")
            }
        } catch (parseError: Exception) {
            Log.e(TAG, "Error parsing 401 response: ${parseError.message}")
        }

        if (errorBody.contains("outdated version", ignoreCase = true) ||
            errorBody.contains("isUpdateRequired", ignoreCase = true) ||
            errorBody.contains("shouldLogout", ignoreCase = true) ||
            errorBody.contains("App version header", ignoreCase = true)
        ) {
            Log.e(TAG, "VERSION OUTDATED detected in error body")
            sharedPreferences.edit().clear().apply()
            return Resource.Error("APP_VERSION_OUTDATED: You are using an outdated version. Please contact admin to get the latest app.")
        }

        return Resource.Error("Unauthorized: $errorBody")
    }

    // ===== HELPER: HANDLE HTTP EXCEPTION =====
    private fun handleHttpException(e: retrofit2.HttpException): Resource<Unit> {
        Log.e(TAG, "HTTP Exception during survey upload: ${e.message}", e)

        if (e.code() == 401) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            Log.e(TAG, "HTTP 401 error body: $errorBody")

            if (errorBody.contains("isUpdateRequired", ignoreCase = true) ||
                errorBody.contains("shouldLogout", ignoreCase = true) ||
                errorBody.contains("outdated version", ignoreCase = true) ||
                errorBody.contains("App version header", ignoreCase = true)
            ) {
                Log.e(TAG, "VERSION OUTDATED in HTTP exception")
                sharedPreferences.edit().clear().apply()
                return Resource.Error("APP_VERSION_OUTDATED: You are using an outdated version. Please contact admin to get the latest app.")
            }
        }

        return Resource.Error(e.message ?: "HTTP error occurred")
    }

    // Keep the old method for backward compatibility (now it calls the new one)
    private suspend fun deactivateOriginalParcel(originalParcelId: Long) {
        deactivateOriginalParcelWithVerification(originalParcelId)
    }

    // ===== OTHER INTERFACE METHODS =====
    override suspend fun getOnePendingSurvey(): NewSurveyNewEntity? =
        dao.getOnePendingSurvey()

    override suspend fun getSurveyById(id: Long): NewSurveyNewEntity? {
        return try {
            dao.getSurveyById(id)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonEntity> {
        return personDao.getPersonsForSurvey(surveyId)
    }

    suspend fun getGrowerCodesForParcel(parcelId: Long): List<String> {
        return personDao.getGrowerCodesForParcel(parcelId)
    }
}