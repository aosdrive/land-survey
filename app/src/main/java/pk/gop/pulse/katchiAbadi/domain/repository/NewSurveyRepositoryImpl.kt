package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.ActiveParcelDao
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.post.Pictures
//import pk.gop.pulse.katchiAbadi.data.local.dao.NewSurveyNewDao
import pk.gop.pulse.katchiAbadi.data.remote.response.NewSurveyNewDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyImageDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyPersonDao
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelCreationRequest
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonPost
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPostNew
import pk.gop.pulse.katchiAbadi.domain.repository.NewSurveyRepository
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject


private const val TAG = "SurveyRepository" //

class NewSurveyRepositoryImpl @Inject constructor(
    private val dao: NewSurveyNewDao,
    private val imageDao: SurveyImageDao,
    private val personDao: SurveyPersonDao,
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences,
    private val activeParcelDao: ActiveParcelDao
) : NewSurveyRepository {

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

            // Delete the survey from database
            dao.deleteSurvey(survey)
            Log.d(TAG, "Survey deleted from database")

            // Handle different parcel operations
            when (survey.parcelOperation) {
                "Merge" -> {
                    Log.d(TAG, "=== HANDLING MERGE DELETE ===")

                    // 1. Reset the PARENT parcel status
                    activeParcelDao.updateSurveyStatus(survey.parcelId, 1) // 1 = Unsurveyed
                    Log.d(TAG, "✓ Reset parent parcel ${survey.parcelId} to unsurveyed")

                    // 2. Reset ALL MERGED CHILD parcels
                    if (survey.parcelOperationValue.isNotBlank()) {
                        val mergedParcelIds = survey.parcelOperationValue
                            .split(",")
                            .mapNotNull { it.trim().toLongOrNull() }
                            .filter { it != survey.parcelId } // Exclude parent parcel

                        Log.d(TAG, "Found ${mergedParcelIds.size} merged child parcels: $mergedParcelIds")

                        for (childParcelId in mergedParcelIds) {
                            // Check if this child parcel has any other surveys
                            val childSurveys = dao.getSurveysByParcelId(childParcelId)

                            if (childSurveys.isEmpty()) {
                                activeParcelDao.updateSurveyStatus(childParcelId, 1) // 1 = Unsurveyed
                                Log.d(TAG, "✓ Reset child parcel $childParcelId to unsurveyed")
                            } else {
                                Log.d(TAG, "Child parcel $childParcelId has other surveys, skipping reset")
                            }
                        }
                    } else {
                        Log.w(TAG, "Merge operation but no parcelOperationValue found")
                    }
                }

                "Split" -> {
                    Log.d(TAG, "=== HANDLING SPLIT DELETE ===")

                    // For split parcels, check if this was the original parcel
                    // Get all surveys for this parcel number
                    val allSplitSurveys = dao.getSurveysByParcelId(survey.parcelId)

                    if (allSplitSurveys.isEmpty()) {
                        // No more surveys for this split parcel, reset status
                        activeParcelDao.updateSurveyStatus(survey.parcelId, 1)
                        Log.d(TAG, "✓ Reset split parcel ${survey.parcelId} to unsurveyed")

                        // If this was part of a split operation, you might want to reactivate the original parcel
                        // This depends on your business logic
                    }
                }

                else -> {
                    Log.d(TAG, "=== HANDLING SAME/OTHER DELETE ===")

                    // Check if there are any other surveys for this parcel
                    val remainingSurveys = dao.getSurveysByParcelId(survey.parcelId)

                    if (remainingSurveys.isEmpty()) {
                        // No more surveys for this parcel, set status back to unsurveyed (1)
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

            // ===== NEW: VALIDATE GROWER CODES BEFORE PROCEEDING =====
            val hasGrowerCodes = withContext(Dispatchers.IO) {
                val persons = personDao.getPersonsForSurvey(survey.pkId)

                if (persons.isEmpty()) {
                    Log.e(TAG, "❌ No persons found for survey pkId=${survey.pkId}")
                    return@withContext false
                }

                // Check if at least one person has a valid grower code
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
            // ===== END VALIDATION =====

            val subparcels = withContext(Dispatchers.IO) {
                when (survey.parcelOperation) {
                    "Split" -> {
                        Log.d(TAG, "Processing split parcel upload for parcelId=${survey.parcelId}")

                        val originalParcel = activeParcelDao.getParcelById(survey.parcelId)
                        if (originalParcel != null) {

                            // Get all split parcels (excluding the original)
                            val mauzaId = originalParcel.mauzaId
                            val areaName = originalParcel.areaAssigned
                            val allActiveParcels = activeParcelDao.getActiveParcelsByMauzaAndArea(mauzaId, areaName)
                            val splitParcels = allActiveParcels.filter {
                                it.parcelNo == originalParcel.parcelNo &&
                                        it.isActivate == true &&
                                        it.subParcelNo.isNotBlank() &&
                                        it.subParcelNo != "0"
                            }

                            Log.d(TAG, "Found ${splitParcels.size} split parcels to upload (excluding original)")
                            Log.d(TAG, "Original parcel deactivated: ID=${survey.parcelId}")

                            val splitSurveys = mutableListOf<NewSurveyNewEntity>()

                            splitParcels.forEach { splitParcel ->
                                val splitSurvey = survey.copy(
                                    pkId = 0,
                                    parcelId = 0L,
                                    parcelNo = splitParcel.parcelNo.toString(),
                                    subParcelNo = splitParcel.subParcelNo,
                                    parcelOperation = "New",
                                    parcelOperationValue = survey.parcelId.toString()
                                )
                                splitSurveys.add(splitSurvey)
                                Log.d(TAG, "Created clean survey record for split parcel: ID=${splitParcel.id}, SubParcel=${splitParcel.subParcelNo}, KhewatInfo=${originalParcel.khewatInfo}")
                            }

                            Log.d(TAG, "Created ${splitSurveys.size} clean survey records for split parcels")
                            splitSurveys
                        } else {
                            Log.w(TAG, "Original parcel not found")
                            emptyList()
                        }
                    }

                    "Merge" -> {
                        Log.d(TAG, "Processing merge parcel upload for parcelId=${survey.parcelId}")

                        val mainParcel = activeParcelDao.getParcelById(survey.parcelId)
                        if (mainParcel == null) {
                            Log.e(TAG, "Main parcel not found for merge operation")
                            return@withContext emptyList()
                        }

                        val mergeSurveys = mutableListOf<NewSurveyNewEntity>()

                        val mainSurvey = survey.copy(
                            parcelOperation = "Merge",
                            parcelOperationValue = survey.parcelOperationValue
                        )
                        mergeSurveys.add(mainSurvey)
                        Log.d(TAG, "Added main parcel to merge with ParcelOperationValue: ${survey.parcelOperationValue}")

                        val mergedParcelIds = survey.parcelOperationValue
                            .split(",")
                            .mapNotNull { it.trim().toLongOrNull() }
                            .filter { it != survey.parcelId }

                        Log.d(TAG, "Found ${mergedParcelIds.size} additional parcels to merge: $mergedParcelIds")

                        mergedParcelIds.forEach { mergedParcelId ->
                            val mergedParcel = activeParcelDao.getParcelById(mergedParcelId)
                            if (mergedParcel != null) {
                                val mergedSurvey = survey.copy(
                                    pkId = survey.pkId,
                                    parcelId = mergedParcelId,
                                    parcelNo = mergedParcel.parcelNo.toString(),
                                    subParcelNo = mergedParcel.subParcelNo,
                                    parcelOperation = "Merge",
                                    parcelOperationValue = ""
                                )
                                mergeSurveys.add(mergedSurvey)
                                Log.d(TAG, "Added merged parcel with EMPTY ParcelOperationValue: ID=$mergedParcelId")
                            }
                        }

                        Log.d(TAG, "Created ${mergeSurveys.size} survey records for merge operation")
                        mergeSurveys
                    }

                    "Same" -> {
                        Log.d(TAG, "Processing 'Same' operation for parcelId=${survey.parcelId}")
                        val localParcel = activeParcelDao.getParcelById(survey.parcelId)

                        if (localParcel != null && localParcel.subParcelNo.isNotBlank() && localParcel.subParcelNo != "0") {
                            Log.d(TAG, "Detected split parcel with 'Same' operation - treating as new parcel")
                            Log.d(TAG, "Parcel details: ID=${localParcel.id}, ParcelNo=${localParcel.parcelNo}, SubParcel=${localParcel.subParcelNo}, KhewatInfo=${localParcel.khewatInfo}")

                            val surveyRecords = dao.getCompleteRecord(survey.parcelId)
                            val cleanSurveys = surveyRecords.map { surveyRecord ->
                                surveyRecord.copy(
                                    parcelOperation = "New",
                                    parcelOperationValue = ""
                                )
                            }
                            Log.d(TAG, "Converted ${cleanSurveys.size} survey records to clean 'New' operations")
                            cleanSurveys
                        } else {
                            Log.d(TAG, "Normal 'Same' operation - proceeding with existing parcel")
                            if (localParcel != null) {
                                Log.d(TAG, "KhewatInfo for parcel: ${localParcel.khewatInfo}")
                            }
                            dao.getCompleteRecord(survey.parcelId)
                        }
                    }

                    else -> {
                        Log.d(TAG, "Processing other operation: ${survey.parcelOperation}")
                        val localParcel = activeParcelDao.getParcelById(survey.parcelId)
                        if (localParcel != null) {
                            Log.d(TAG, "KhewatInfo for parcel: ${localParcel.khewatInfo}")
                        }
                        dao.getCompleteRecord(survey.parcelId)
                    }
                }
            }

            if (subparcels.isEmpty()) {
                Log.e(TAG, "No parcels found to upload")
                return Resource.Error("No parcels found to upload")
            }

            Log.d(TAG, "Found ${subparcels.size} parcels to upload")

            // Build posts with clean data
            val posts = withContext(Dispatchers.IO) {
                subparcels.mapIndexed { index, subSurvey ->
                    Log.d(TAG, "=== Processing Survey ${index + 1}/${subparcels.size} ===")
                    Log.d(TAG, "Survey Details: pkId=${subSurvey.pkId}, parcelNo=${subSurvey.parcelNo}, subParcelNo=${subSurvey.subParcelNo}")

                    val sourceSurveyPkId = subSurvey.pkId

                    val images = imageDao.getImagesBySurvey(sourceSurveyPkId)
                    val pictures = convertSurveyImagesToPictures(context, images)
                    Log.d(TAG, "Found ${images.size} images for survey pkId=${sourceSurveyPkId}")

                    val personsEntities = personDao.getPersonsForSurvey(sourceSurveyPkId)
                    val persons = convertPersonsToSurveyPersonPost(personsEntities)
                    Log.d(TAG, "Found ${personsEntities.size} persons for survey pkId=${sourceSurveyPkId}")

                    val (khewatInfo, parcelAreaKMF) = when (survey.parcelOperation) {
                        "Split" -> {
                            Log.d(TAG, "Getting khewatInfo and parcelAreaKMF for split parcel at index $index")
                            val originalParcel = activeParcelDao.getParcelById(survey.parcelId)
                            Pair(
                                originalParcel?.khewatInfo ?: "",
                                originalParcel?.parcelAreaKMF ?: ""
                            )
                        }
                        else -> {
                            Log.d(TAG, "Getting khewatInfo and parcelAreaKMF for operation: ${survey.parcelOperation}")
                            val localParcel = activeParcelDao.getParcelById(subSurvey.parcelId)
                            Pair(
                                localParcel?.khewatInfo ?: "",
                                localParcel?.parcelAreaKMF ?: ""
                            )
                        }
                    }
                    Log.d(TAG, "KhewatInfo from ActiveParcelEntity: $khewatInfo")
                    Log.d(TAG, "ParcelAreaKMF from ActiveParcelEntity: $parcelAreaKMF")

                    val (geomWKT, centroid) = when (survey.parcelOperation) {
                        "Split" -> {
                            Log.d(TAG, "Getting geometry for split parcel at index $index")
                            val originalParcel = activeParcelDao.getParcelById(survey.parcelId)
                            if (originalParcel != null) {
                                val splitParcels = activeParcelDao.getActiveParcelsByMauzaAndArea(
                                    originalParcel.mauzaId,
                                    originalParcel.areaAssigned
                                ).filter {
                                    it.parcelNo == originalParcel.parcelNo &&
                                            it.isActivate == true &&
                                            it.subParcelNo.isNotBlank() &&
                                            it.subParcelNo != "0"
                                }

                                if (index < splitParcels.size) {
                                    val splitParcel = splitParcels[index]
                                    Log.d(TAG, "Split parcel geometry: ID=${splitParcel.id}, geomWKT length=${splitParcel.geomWKT?.length ?: 0}")
                                    Log.d(TAG, "Split parcel centroid: ${splitParcel.centroid}")
                                    Pair(splitParcel.geomWKT ?: "", splitParcel.centroid ?: "")
                                } else {
                                    Log.w(TAG, "No split parcel found at index $index")
                                    Pair("", "")
                                }
                            } else {
                                Log.w(TAG, "Original parcel not found for split operation")
                                Pair("", "")
                            }
                        }
                        else -> {
                            Log.d(TAG, "Getting geometry for operation: ${survey.parcelOperation}")
                            val localParcel = activeParcelDao.getParcelById(subSurvey.parcelId)
                            if (localParcel != null) {
                                Log.d(TAG, "Parcel geometry: ID=${localParcel.id}, geomWKT length=${localParcel.geomWKT?.length ?: 0}")
                                Log.d(TAG, "Parcel centroid: ${localParcel.centroid}")
                                Pair(localParcel.geomWKT ?: "", localParcel.centroid ?: "")
                            } else {
                                Log.w(TAG, "Parcel not found for ID: ${subSurvey.parcelId}")
                                Pair("", "")
                            }
                        }
                    }

                    Log.d(TAG, "Final geometry data: geomWKT=${if (geomWKT.isNotEmpty()) "Present (${geomWKT.length} chars)" else "Empty"}")
                    Log.d(TAG, "Final centroid data: ${if (centroid.isNotEmpty()) centroid else "Empty"}")

                    val surveyPost = buildCleanSurveyPostNew(subSurvey, pictures, persons, geomWKT, centroid, khewatInfo, parcelAreaKMF, 100)

                    Log.d(TAG, "=== Survey Post Details ===")
                    Log.d(TAG, "Property Type: ${surveyPost.propertyType}")
                    Log.d(TAG, "Ownership Status: ${surveyPost.ownershipStatus}")
                    Log.d(TAG, "Variety: ${surveyPost.variety}")
                    Log.d(TAG, "Crop Type: ${surveyPost.cropType}")
                    Log.d(TAG, "Crop: ${surveyPost.crop}")
                    Log.d(TAG, "Year: ${surveyPost.year}")
                    Log.d(TAG, "Area: ${surveyPost.area}")
                    Log.d(TAG, "Geometry Correct: ${surveyPost.isGeometryCorrect}")
                    Log.d(TAG, "Remarks: ${surveyPost.remarks}")
                    Log.d(TAG, "Mauza ID: ${surveyPost.mauzaId}")
                    Log.d(TAG, "Area Name: ${surveyPost.areaName}")
                    Log.d(TAG, "Parcel ID: ${surveyPost.parcelId}")
                    Log.d(TAG, "Parcel No: ${surveyPost.parcelNo}")
                    Log.d(TAG, "Sub Parcel No: ${surveyPost.subParcelNo}")
                    Log.d(TAG, "KhewatInfo: ${surveyPost.khewatInfo}")
                    Log.d(TAG, "ParcelAreaKMF: ${surveyPost.parcelAreaKMF}")
                    Log.d(TAG, "Distance: ${surveyPost.distance}")
                    Log.d(TAG, "Parcel Operation: ${surveyPost.parcelOperation}")
                    Log.d(TAG, "Parcel Operation Value: ${surveyPost.parcelOperationValue}")
                    Log.d(TAG, "GeomWKT: ${if (surveyPost.geomWKT?.isNotEmpty() == true) "Present (${surveyPost.geomWKT.length} chars)" else "Empty/Null"}")
                    Log.d(TAG, "Centroid: ${surveyPost.centriod ?: "Empty/Null"}")
                    Log.d(TAG, "Pictures Count: ${surveyPost.pictures.size}")
                    Log.d(TAG, "Persons Count: ${surveyPost.persons.size}")

                    surveyPost.persons.forEachIndexed { personIndex, person ->
                        Log.d(TAG, "Person ${personIndex + 1}: ${person.firstName} ${person.lastName}, Grower Code: ${person.growerCode}")
                    }

                    surveyPost.pictures.forEachIndexed { picIndex, picture ->
                        Log.d(TAG, "Picture ${picIndex + 1}: Type=${picture.Type}, Data Length=${picture.PicData.length}")
                    }

                    Log.d(TAG, "=== End Survey Post Details ===")

                    surveyPost
                }
            }

            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            Log.d(TAG, "=== UPLOAD SUMMARY ===")
            Log.d(TAG, "Total surveys to upload: ${posts.size}")
            Log.d(TAG, "Token present: ${token.isNotEmpty()}")

            posts.forEachIndexed { index, post ->
                Log.d(TAG, "Upload ${index + 1}: ParcelNo=${post.parcelNo}, SubParcel=${post.subParcelNo}, HasGeometry=${post.geomWKT?.isNotEmpty() == true}, KhewatInfo=${post.khewatInfo}, ParcelAreaKMF=${post.parcelAreaKMF}, Distance=${post.distance}")
            }

            val response = api.postSurveyDataNew("Bearer $token", posts)

            Log.d(TAG, "=== UPLOAD RESPONSE ===")
            Log.d(TAG, "Response successful: ${response.isSuccessful}")
            Log.d(TAG, "Response code: ${response.code()}")
            Log.d(TAG, "Response body present: ${response.body() != null}")

            when {
                response.isSuccessful && response.body() != null -> {
                    Log.i(TAG, "Survey upload successful - marking surveys as uploaded")
                    withContext(Dispatchers.IO) {
                        subparcels.forEach { subSurvey ->
                            dao.markAsUploaded(subSurvey.pkId)
                            Log.d(TAG, "Marked survey pkId=${subSurvey.pkId} as uploaded")
                        }
                        if (survey.parcelOperation == "Split") {
                            deactivateOriginalParcel(survey.parcelId)
                            Log.d(TAG, "Deactivated original parcel locally after successful upload")
                        }
                    }
                    Log.i(TAG, "=== UPLOAD COMPLETED SUCCESSFULLY ===")
                    Resource.Success(Unit)
                }

                response.code() == 401 -> {
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
                        errorBody.contains("App version header", ignoreCase = true)) {

                        Log.e(TAG, "VERSION OUTDATED detected in error body")
                        sharedPreferences.edit().clear().apply()
                        return Resource.Error("APP_VERSION_OUTDATED: You are using an outdated version. Please contact admin to get the latest app.")
                    }

                    Resource.Error("Unauthorized: $errorBody")
                }

                else -> {
                    val errorBody = response.errorBody()?.string() ?: "Unknown"
                    val errorMessage = "Server error: $errorBody (code ${response.code()})"
                    Log.e(TAG, "Upload failed: $errorMessage")
                    Resource.Error(errorMessage)
                }
            }

        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "HTTP Exception during survey upload: ${e.message}", e)

            if (e.code() == 401) {
                val errorBody = e.response()?.errorBody()?.string() ?: ""
                Log.e(TAG, "HTTP 401 error body: $errorBody")

                if (errorBody.contains("isUpdateRequired", ignoreCase = true) ||
                    errorBody.contains("shouldLogout", ignoreCase = true) ||
                    errorBody.contains("outdated version", ignoreCase = true) ||
                    errorBody.contains("App version header", ignoreCase = true)) {

                    Log.e(TAG, "VERSION OUTDATED in HTTP exception")
                    sharedPreferences.edit().clear().apply()
                    return Resource.Error("APP_VERSION_OUTDATED: You are using an outdated version. Please contact admin to get the latest app.")
                }
            }

            Resource.Error(e.message ?: "HTTP error occurred")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during survey upload", e)
            Resource.Error(e.message ?: "Unexpected error")
        }
    }

    private suspend fun deactivateOriginalParcel(originalParcelId: Long) {
        withContext(Dispatchers.IO) {
            // Mark the original parcel as deactivated in local database
            activeParcelDao.deactivateParcel(originalParcelId)
            Log.d(TAG, "Deactivated original parcel with ID: $originalParcelId")
        }
    }

    // Build clean SurveyPostNew with geometry AND person data AND khewatInfo AND parcelAreaKMF AND distance from ActiveParcelEntity
    private fun buildCleanSurveyPostNew(
        survey: NewSurveyNewEntity,
        pictures: List<Pictures>,
        persons: List<SurveyPersonPost>,
        geomWKT: String,
        centroid: String,
        khewatInfo: String, // Pass khewatInfo as parameter from ActiveParcelEntity
        parcelAreaKMF: String, // Pass parcelAreaKMF as parameter from ActiveParcelEntity
        distance: Int = 100 // Pass distance as parameter with default value 100
    ): SurveyPostNew {

        // ✅ For split parcels (or any "New" operation), don't send parcel ID
        // Let the server generate new IDs
        val parcelIdToSend = if (survey.parcelOperation == "New") {
            0L // Don't send local ID - let server create new
        } else {
            survey.parcelId ?: 0L // Send existing ID for other operations
        }

        return SurveyPostNew(
            propertyType = survey.propertyType,
            ownershipStatus = survey.ownershipStatus,
            variety = survey.variety,
            cropType = survey.cropType,
            crop = survey.crop,
            year = survey.year,
            area = survey.area,
            isGeometryCorrect = survey.isGeometryCorrect,
            remarks = survey.remarks,
            mauzaId = survey.mauzaId,
            areaName = survey.areaName,
            parcelId = parcelIdToSend,
            parcelNo = survey.parcelNo,
            subParcelNo = survey.subParcelNo,
            parcelOperation = survey.parcelOperation, // ✅ Always "New" for clean creation
            parcelOperationValue = survey.parcelOperationValue, // ✅ Always empty - no references to old parcels
            geomWKT = if (geomWKT.isNotEmpty()) geomWKT else null,
            centriod = if (centroid.isNotEmpty()) centroid else null, // ✅ Changed to match DB field
            khewatInfo = khewatInfo, // ✅ USE KHEWAT INFO FROM ACTIVEPARCELENTITY
            parcelAreaKMF = parcelAreaKMF, // ✅ USE PARCEL AREA KMF FROM ACTIVEPARCELENTITY
            distance = distance, // ✅ USE DISTANCE PARAMETER
            pictures = pictures,
            persons = persons
        )
    }

    // ✅ Convert persons to SurveyPersonPost (your existing method is correct)
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
                extra1 = person.extra1 ?: "",
                extra2 = person.extra2 ?: "",
                mauzaId = person.mauzaId ?: 0L,
                mauzaName = person.mauzaName ?: ""
            )
        }
    }

    override suspend fun getOnePendingSurvey(): NewSurveyNewEntity? =
        dao.getOnePendingSurvey()

    private fun convertSurveyImagesToPictures(
        context: Context,
        images: List<SurveyImage>
    ): List<Pictures> = images.map { img ->
        // ✅ ADD DEBUG LOGGING
        Log.d(TAG, "=== Converting Image to Pictures ===")
        Log.d(TAG, "Image ID: ${img.id}")
        Log.d(TAG, "Type: ${img.type}")
        Log.d(TAG, "Latitude: ${img.latitude}")
        Log.d(TAG, "Longitude: ${img.longitude}")
        Log.d(TAG, "Timestamp: ${img.timestamp}")
        Log.d(TAG, "Bearing: ${img.bearing}")
        Log.d(TAG, "LocationAddress: ${img.locationAddress}")

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

        val picture = Pictures(
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

        // ✅ LOG THE CREATED PICTURE OBJECT
        Log.d(TAG, "Created Pictures object:")
        Log.d(TAG, "  Number: ${picture.Number}")
        Log.d(TAG, "  Latitude: ${picture.Latitude}")
        Log.d(TAG, "  Longitude: ${picture.Longitude}")
        Log.d(TAG, "  Timestamp: ${picture.Timestamp}")
        Log.d(TAG, "  Bearing: ${picture.Bearing}")

        picture
    }

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