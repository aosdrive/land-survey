package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.google.gson.Gson
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaDetail
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.data.remote.response.Settings
import pk.gop.pulse.katchiAbadi.data.remote.response.SubParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.domain.repository.MenuRepository
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject


class MenuRepositoryImpl @Inject constructor(
    private val context: Context,
    private val api: ServerApi,
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : MenuRepository {

    override suspend fun syncAndSaveData(
        mauzaId: Long,
        abadiId: Long,
        mauzaName: String,
        abadiName: String
    ): SimpleResource {

        try {
            val response = api.syncData(Constants.SYNC_DATA_URL, abadiId)
            if (response.code == 200) {

                response.kachiAbadi?.let { kachiAbadi ->
                    if (kachiAbadi.parcelsList.isNotEmpty()) {
                        if (kachiAbadi.surveyList.isNotEmpty()) {

                            try {
                                db.withTransaction {
                                    db.parcelDao().deleteAllParcelsWrtArea(abadiId)
                                    db.surveyDao().deleteAllSurveysWrtArea(abadiId)

                                    val gson = Gson()

                                    val listOfSurveyRecords =
                                        db.surveyFormDao().getAllUniqueCentroids().toHashSet()
                                    val listOfNotAtHomeRecords =
                                        db.notAtHomeSurveyFormDao().getAllUniqueCentroids()
                                            .toHashSet()

                                    // Create a list to hold the records to be inserted
                                    val parcelRecords = mutableListOf<ParcelEntity>()

                                    // Iterate through the dataList and add records to the list
                                    for (item in kachiAbadi.parcelsList) {

                                        /*
                                           1	Not Started Yet
                                           2	Surveyed
                                           3	Retake Pictures By Vendor
                                           4	Revisit Required By Vendor
                                           5	Accepted By Vendor
                                           6	Retake Pictures By Pulse
                                           7	Revisit Required By Pulse
                                           8	Accepted By Pulse

                                           1    Not Surveyed
                                           2    Surveyed
                                           3    Single - Retake Pictures
                                           4    Single - Revisit
                                           5    Single - False
                                           6    Multiple - Retake Pictures / Revisit
                                           7    Multiple - All Retake Pictures
                                           8    Multiple - All Revisit
                                           9    Multiple - All False
                                           10   Exception
                                           11   Multiple - Retake Picture
                                           12   Multiple - Revisit
                                       */

                                        var surveyStatus = item.newStatusId
                                        var status = ParcelStatus.DEFAULT

                                        if (listOfSurveyRecords.isNotEmpty() && listOfSurveyRecords.contains(
                                                item.centroid
                                            )
                                        ) {
                                            status = ParcelStatus.IN_PROCESS
                                        }

                                        if (listOfNotAtHomeRecords.isNotEmpty() && listOfNotAtHomeRecords.contains(
                                                item.centroid
                                            )
                                        ) {
                                            status = ParcelStatus.NOT_AT_HOME
                                        }

                                        if (surveyStatus > 2) {

                                            val rawMergedSurveyRecords = db.surveyFormDao().getAllUniqueMergedParcels() // Fetch the raw data from Room
                                            val listOfMergedSurveyRecords = rawMergedSurveyRecords
                                                .flatMap { it.split(",") }           // Split each string by commas
                                                .mapNotNull { it.toLongOrNull() }     // Convert each split value to an Int
                                                .distinct()
                                                .toHashSet()


                                            val rawMergedNotAtHomeRecords = db.notAtHomeSurveyFormDao().getAllUniqueMergedParcels() // Fetch the raw data from Room
                                            val listOfMergedNotAtHomeRecords = rawMergedNotAtHomeRecords
                                                .flatMap { it.split(",") }           // Split each string by commas
                                                .mapNotNull { it.toLongOrNull() }     // Convert each split value to an Int
                                                .distinct()
                                                .toHashSet()


                                            if (listOfMergedSurveyRecords.isNotEmpty() && listOfMergedSurveyRecords.contains(
                                                    item.parcelNo
                                                )
                                            ) {
                                                status = ParcelStatus.MERGE
                                            }

                                            if (listOfMergedNotAtHomeRecords.isNotEmpty() && listOfMergedNotAtHomeRecords.contains(
                                                    item.parcelNo
                                                )
                                            ) {
                                                status = ParcelStatus.MERGE
                                            }

                                            val subParcelsList = item.subParcelsList

                                            if(subParcelsList.isNotEmpty()){
                                                if(subParcelsList.size == 1){
                                                    surveyStatus = if (subParcelsList.all { it.fullRevisitRequired }) {
                                                        4
                                                    }else if (subParcelsList.all { it.pictureRevisitRequired }) {
                                                        3
                                                    } else if (subParcelsList.any { it.fullRevisitRequired || it.pictureRevisitRequired }) {
                                                        10
                                                    } else {
                                                        5
                                                    }
                                                }else{
                                                    surveyStatus = if (subParcelsList.all { it.fullRevisitRequired }) {
                                                        8
                                                    }else if (subParcelsList.all { it.pictureRevisitRequired }) {
                                                        7
                                                    } else if (subParcelsList.any { it.fullRevisitRequired || it.pictureRevisitRequired }) {
                                                        6
                                                    } else {
                                                        9
                                                    }
                                                }
                                            }
                                        }

                                        val record = ParcelEntity(
                                            id = item.id,
                                            geom = item.geom,
                                            centroidGeom = item.centroid,
                                            isSurveyed = false, // Not used anymore
                                            distance = item.distance,
                                            mauzaId = mauzaId,
                                            mauzaName = mauzaName,
                                            kachiAbadiId = abadiId,
                                            abadiName = abadiName,
                                            status = status,
                                            parcelNo = item.parcelNo,
                                            subParcelNo = item.subParcelNo,
                                            newStatusId = surveyStatus,
                                            subParcelsStatusList = gson.toJson(item.subParcelsList)
                                        )
                                        parcelRecords.add(record)

                                        // Insert in batches of 1000 records
                                        if (parcelRecords.size % 1000 == 0) {
                                            db.parcelDao().insertParcels(parcelRecords)
                                            parcelRecords.clear()
                                        }
                                    }

                                    // Insert any remaining records
                                    if (parcelRecords.isNotEmpty()) {
                                        db.parcelDao().insertParcels(parcelRecords)
                                    }

                                     val listOfSurveyPropertyRecords =
                                        db.surveyFormDao().getAllUniquePropertyIds().toHashSet()
                                    val listOfNotAtHomePropertyRecords =
                                        db.notAtHomeSurveyFormDao().getAllUniquePropertyIds()
                                            .toHashSet()


                                    val ownershipRecords = mutableListOf<SurveyEntity>()

                                    // Iterate through the dataList and add records to the list
                                    for (item in kachiAbadi.surveyList) {
                                        var attachmentStatus = item.isAttach

                                        if (listOfSurveyPropertyRecords.isNotEmpty()
                                            && listOfSurveyPropertyRecords.contains(
                                                item.propertyId
                                            )
                                        ) {
                                            attachmentStatus = true

                                        }

                                        if (listOfNotAtHomePropertyRecords.isNotEmpty()
                                            && listOfNotAtHomePropertyRecords.contains(
                                                item.propertyId
                                            )
                                        ) {
                                            attachmentStatus = true
                                        }

                                        val allowedAreaCharacters = "0123456789-"
                                        val cleanedArea =
                                            item.area?.filter { it in allowedAreaCharacters }
                                                ?: "0-0-0"

                                        val filterArea =
                                            cleanedArea.split("-").let { areaReceived ->
                                                if (areaReceived.size == 3) {
                                                    // Convert each part to integer or default to 0, with validation
                                                    val part1 = areaReceived[0].toIntOrNull() ?: 0
                                                    val part2 = areaReceived[1].toIntOrNull()
                                                        ?.takeIf { it in 0..19 } ?: 0
                                                    val part3 = areaReceived[2].toIntOrNull()
                                                        ?.takeIf { it in 0..272 } ?: 0

                                                    "$part1-$part2-$part3"
                                                } else {
                                                    "0-0-0"
                                                }
                                            }

                                        val allowedPropertyNoCharacters =
                                            "0123456789qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM()/ ._"

                                        val cleanedPropertyNo = item.propertyNo
                                            ?.filter { it in allowedPropertyNoCharacters }
                                            ?: "NA"

                                        val cleanedCNIC =
                                            if (item.cnic.isNullOrEmpty() || item.cnic.any { it.isLetter() }) {
                                                "NA"
                                            } else {
                                                val partsCNIC = item.cnic.split(":$$:")
                                                if (partsCNIC.size == 1) {
                                                    if (isValidCnic(partsCNIC[0])) {
                                                        partsCNIC[0].trim()
                                                    } else {
                                                        "NA"
                                                    }
                                                } else {
                                                    partsCNIC.joinToString(":$$:") { part ->
                                                        if (isValidCnic(part)) {
                                                            part.trim()
                                                        } else {
                                                            "NA"
                                                        }
                                                    }
                                                }
                                            }

                                        val allowedNameCharacters =
                                            "qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM ,.-_"

                                        val cleanedName = item.name
                                            ?.split(":$$:")
                                            ?.joinToString(":$$:") { part ->
                                                part.trim().filter { it in allowedNameCharacters }
                                            } ?: "NA"

                                        val cleanedFatherName = item.fName
                                            ?.split(":$$:")
                                            ?.joinToString(":$$:") { part ->
                                                part.trim().filter { it in allowedNameCharacters }
                                            } ?: "NA"

                                        val record = SurveyEntity(
                                            propertyId = item.propertyId,
                                            propertyNo = cleanedPropertyNo, // Use cleaned property number
                                            name = cleanedName,
                                            fname = cleanedFatherName,
                                            area = filterArea,
                                            relation = item.relation,
                                            cnic = cleanedCNIC,
                                            gender = item.gender,
                                            kachiAbadiId = abadiId,
                                            mauzaId = mauzaId,
                                            isAttached = attachmentStatus
                                        )
                                        ownershipRecords.add(record)

                                        // Insert in batches of 1000 records
                                        if (ownershipRecords.size % 1000 == 0) {
                                            db.surveyDao().insertSurveys(ownershipRecords)
                                            ownershipRecords.clear()
                                        }
                                    }

                                    // Insert any remaining records
                                    if (ownershipRecords.isNotEmpty()) {
                                        db.surveyDao().insertSurveys(ownershipRecords)
                                    }

                                    sharedPreferences.edit()
                                        .putLong(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                                            mauzaId
                                        )
                                        .apply()

                                    sharedPreferences.edit()
                                        .putString(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME,
                                            mauzaName
                                        )
                                        .apply()

                                    sharedPreferences.edit()
                                        .putLong(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
                                            abadiId
                                        )
                                        .apply()

                                    sharedPreferences.edit()
                                        .putString(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                                            abadiName
                                        )
                                        .apply()


                                }

                                return Resource.Success(Unit)


                            } catch (e: Exception) {
                                Log.d("testing", "Unexpected error: $e")
                                return Resource.Error(
                                    message = "Unexpected error: $e"
                                )
                            }

                        } else {
                            return Resource.Error(
                                message = "Contact administrator, property ownerships are not available to start survey."
                            )
                        }
                    } else {
                        return Resource.Error(
                            message = "Contact administrator, parcels are not available to start survey."
                        )
                    }
                } ?: run {
                    return Resource.Error(
                        message = "Contact administrator, '$abadiName' abadi data is not available."
                    )
                }


            } else {
                return response.message.let { msg ->
                    Resource.Error(msg)
                }
            }
        } catch (e: Exception) {
            return when (e) {
                is IOException -> {
                    Resource.Error(
                        message = "Try again! Couldn't reach the server.\n${e.message}."
                    )
                }

                is TimeoutException -> {
                    Resource.Error(
                        message = "Request timed out. Please try again later."
                    )
                }

                is HttpException -> {
                    Resource.Error(
                        message = "An HTTP error occurred. Status code: ${e.code()}"
                    )
                }

                else -> {
                    Resource.Error(
                        message = "Error: $e"
                    )
                }
            }
        }

    }
    override suspend fun fetchMauzaSyncData(): ResourceSealed<List<MauzaDetail>, Settings> {
        return try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            val response = api.getMauzaSyncInfo(Constants.Sync_Mauza_Info_URL,"Bearer $token")

            // You can apply null safety checks if needed
            ResourceSealed.Success(data = response.mauzaDetails, info = response.settings)

        } catch (e: Exception) {
            when (e) {
                is IOException -> ResourceSealed.Error("Network error: ${e.message}", null)
                is TimeoutException -> ResourceSealed.Error("Request timed out. Please try again.", null)
                is HttpException -> ResourceSealed.Error("HTTP error: ${e.code()}", null)
                else -> ResourceSealed.Error("Unexpected error: ${e.message}", null)
            }
        }
    }


    override suspend fun mouzaAssignedData(): ResourceSealed<MouzaAssignedDto, Info> {
        val userId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        return try {
            val response = api.mouzaAssignedData(Constants.Mauza_Assigned_URL, userId)

            if (response.code == 200) {
                val data = response.data // The data part of the response
                val info = response.info // The info part of the response

                if (data != null) {
                    // Pass both data and info to Resource.Success
                    ResourceSealed.Success(data = data, info = info)
                } else {
                    // Handle case where data is null
                    ResourceSealed.Error(message = "Response data is null", info = info)
                }
            } else {
                // Provide info as null in case of an error
                ResourceSealed.Error(message = response.message, info = null)
            }
        } catch (e: Exception) {
            when (e) {
                is IOException -> {
                    ResourceSealed.Error(
                        message = "Try again! Couldn't reach the server.\n${e.message}.",
                        info = null
                    )
                }

                is TimeoutException -> {
                    ResourceSealed.Error(
                        message = "Request timed out. Please try again later.",
                        info = null
                    )
                }

                is HttpException -> {
                    ResourceSealed.Error(
                        message = "An HTTP error occurred. Status code: ${e.code()}",
                        info = null
                    )
                }

                else -> {
                    ResourceSealed.Error(
                        message = "An unexpected error occurred: ${e.message}",
                        info = null
                    )
                }
            }
        }
    }


    fun isValidCnic(cnic: String): Boolean {
        // Trim any leading or trailing whitespace
        val trimmedCnic = cnic.trim()

        // Check if the CNIC is not all zeros
        if (trimmedCnic == "00000-0000000-0") {
            return false
        }

        // Check if the CNIC does not start with '00000'
        if (trimmedCnic.startsWith("00000")) {
            return false
        }

        // Check if the middle segment (digits 7 to 13) is not all zeros or repeated digits
        val middleSegment = trimmedCnic.substring(6, 13)
        if (middleSegment.all { it == '0' } || isRepeatedDigit(middleSegment)) {
            return false
        }

        // Check if the first segment (digits 1 to 5) is not all zeros or repeated digits
        val firstSegment = trimmedCnic.substring(0, 5)
        if (firstSegment.all { it == '0' } || isRepeatedDigit(firstSegment)) {
            return false
        }

        // Regular expression to match the CNIC format
//        val cnicPattern = Regex("^[0-9]{5}-[0-9]{7}-[0-9]$")
        val cnicPattern = Regex("^[0-9]{5}-[0-9]{6}$")

        // Check if the CNIC matches the pattern
        if (!cnicPattern.matches(trimmedCnic.substring(0, 12))) {
            return false
        }

        // If all checks pass, the CNIC is considered valid
        return true
    }

    // Helper function to check if all characters in the segment are the same digit
    fun isRepeatedDigit(segment: String): Boolean {
        val firstChar = segment[0]
        return segment.all { it == firstChar }
    }
}