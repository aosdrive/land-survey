package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import com.google.gson.Gson
import org.json.JSONObject
import pk.gop.pulse.katchiAbadi.BuildConfig
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcel
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.post.Floors
import pk.gop.pulse.katchiAbadi.data.remote.post.Partition
import pk.gop.pulse.katchiAbadi.data.remote.post.PictureFieldRecord
import pk.gop.pulse.katchiAbadi.data.remote.post.Pictures
import pk.gop.pulse.katchiAbadi.data.remote.post.RetakePicture
import pk.gop.pulse.katchiAbadi.data.remote.post.RetakePicturesPost
import pk.gop.pulse.katchiAbadi.data.remote.post.SurveyPost
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails
import pk.gop.pulse.katchiAbadi.domain.repository.SavedRepository
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class SavedRepositoryImpl @Inject constructor(
    private val context: Context,
    private val api: ServerApi,
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : SavedRepository {

    override fun getSurveyFormList(): LiveData<List<SurveyMergeDetails>> {
        return db.surveyFormDao().getSavedRecordsDetails()
    }

    override suspend fun deleteSavedRecord(survey: SurveyMergeDetails): SimpleResource {
        return try {
            when (survey.parcelOperation) {
                "Split" -> {
                    val recordsList = db.surveyFormDao().getRecord(survey.parcelNo, survey.uniqueId)
                    for (record in recordsList) {
                        db.parcelDao().updateParcelSurveyStatus(
                            record.newStatusId,
                            ParcelStatus.DEFAULT,
                            record.centroidGeom
                        )
                        db.surveyDao().updateSurveyStatus(false, record.surveyId)
                    }
                    db.surveyFormDao().deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                }

                "Merge" -> {
                    if (survey.parcelOperationValue.contains(",")) {
                        val parcelNos = survey.parcelOperationValue.split(",").toMutableList()
                        for (parcelNo in parcelNos) {
                            val newStatusId = db.parcelDao()
                                .getNewStatusId(parcelNo.toLong(), survey.kachiAbadiId)

                            db.parcelDao()
                                .updateParcelSurveyStatusWrtParcelId(
                                    newStatusId,
                                    ParcelStatus.DEFAULT,
                                    parcelNo.toLong()
                                )
                        }
                    } else {
                        val newStatusId = db.parcelDao().getNewStatusId(
                            survey.parcelOperationValue.toLong(),
                            survey.kachiAbadiId,
                        )
                        db.parcelDao()
                            .updateParcelSurveyStatusWrtParcelId(
                                newStatusId,
                                ParcelStatus.DEFAULT,
                                survey.parcelOperationValue.toLong()
                            )
                    }

                    val recordsList = db.surveyFormDao().getRecord(survey.parcelNo, survey.uniqueId)
                    for (record in recordsList) {
                        db.parcelDao().updateParcelSurveyStatus(
                            record.newStatusId,
                            ParcelStatus.DEFAULT,
                            record.centroidGeom
                        )
                        db.surveyDao().updateSurveyStatus(false, record.surveyId)
                    }
                    db.surveyFormDao().deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                }

                else -> {
                    val recordsList = db.surveyFormDao().getRecord(survey.parcelNo, survey.uniqueId)
                    for (record in recordsList) {
                        db.parcelDao().updateParcelSurveyStatus(
                            record.newStatusId,
                            ParcelStatus.DEFAULT,
                            record.centroidGeom
                        )
                        db.surveyDao().updateSurveyStatus(false, record.surveyId)
                    }
                    db.surveyFormDao().deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                }
            }

            Resource.Success(Unit)

        } catch (e: Exception) {
            Resource.Error(
                message = "An unexpected error occurred: ${e.message}"
            )
        }
    }

    override fun getSavedRecordByStatusAndLimit(statusBit: Int): LiveData<SurveyMergeDetails> {
        return db.surveyFormDao().getSavedRecordsByStatusAndLimit(statusBit)

    }

    override suspend fun postSavedData(parcelNo: Long, uniqueId: String): SimpleResource {

        try {

            val recordsList = db.surveyFormDao().getCompleteRecord(parcelNo, uniqueId)

            for (surveyFormEntity in recordsList) {

                if (Utility.checkInternetConnection(context)) {

                    val recordId = surveyFormEntity.pkId

                    Log.d("TAGGG", "${surveyFormEntity.isRevisit}")

                    if (surveyFormEntity.isRevisit == 1) {
                        if (surveyFormEntity.newStatusId in mutableListOf(3, 11)) {
                            val surveyPost = getSurveyRetakePicturesPost(surveyFormEntity)

                            val body = surveyPost.toJson()
                            if (BuildConfig.DEBUG)
                                Utility.writeJsonToFile(context, "${recordId}.json", body)

                            val response = api.postSurveyRetakePicturesData(
                                Constants.POST_SURVEY_DATA_RETAKE_PICTURE_URL,
                                surveyPost
                            )

                            response.message?.let { msg ->
                                if (msg == "Picture saved successfully") {
                                    db.surveyFormDao()
                                        .updateSurveyStatusWrtParcel(
                                            parcelNo,
                                            uniqueId,
                                            surveyFormEntity.pkId
                                        )
                                } else {
                                    return Resource.Error(message = msg)
                                }
                            } ?: return Resource.Error(Resource.unknownError())

                        } else {
                            val surveyPost = getSurveyFormPost(surveyFormEntity)

                            val rejectedSubParcel =
                                Gson().fromJson(
                                    surveyFormEntity.subParcelsStatusList,
                                    RejectedSubParcel::class.java
                                )
                            val fieldRecordId = rejectedSubParcel.fieldRecordId

                            val body = surveyPost.toJson()
                            if (BuildConfig.DEBUG)
                                Utility.writeJsonToFile(context, "${recordId}.json", body)

                            val response = api.postSurveyRevisitData(
                                "${Constants.POST_SURVEY_DATA_REVISIT_URL}/$fieldRecordId",
                                surveyPost
                            )

                            if (response.code != 200) {
                                response.message?.let { msg ->
                                    return Resource.Error(message = msg)
                                } ?: return Resource.Error(Resource.unknownError())
                            } else {
                                db.surveyFormDao()
                                    .updateSurveyStatusWrtParcel(
                                        parcelNo,
                                        uniqueId,
                                        surveyFormEntity.pkId
                                    )
                            }

                        }
                    } else {
                        val surveyPost = getSurveyFormPost(surveyFormEntity)

                        val body = surveyPost.toJson()
                        if (BuildConfig.DEBUG)
                            Utility.writeJsonToFile(context, "${recordId}.json", body)

                        val response =
                            api.postSurveyData(Constants.POST_SURVEY_DATA_URL, surveyPost)

                        if (response.code != 200) {
                            response.message?.let { msg ->
                                return Resource.Error(message = msg)
                            } ?: return Resource.Error(Resource.unknownError())
                        } else {
                            db.surveyFormDao()
                                .updateSurveyStatusWrtParcel(
                                    parcelNo,
                                    uniqueId,
                                    surveyFormEntity.pkId
                                )
                        }

                    }

                } else {
                    return Resource.Error("Internet disconnected, please check your internet connection")
                }
            }

            return Resource.Success(Unit)

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
                        message = "An unexpected error occurred: ${e.message}"
                    )
                }
            }
        }
    }

    private fun getSurveyRetakePicturesPost(surveyFormEntity: SurveyFormEntity): RetakePicturesPost {

        // Create Pictures
        val picturesList = ArrayList<RetakePicture>()

        val jsonObject = JSONObject(surveyFormEntity.picturesList)
        val jsonArray = jsonObject.getJSONArray("pictures")
        for (i in 0 until jsonArray.length()) {

            val item = jsonArray.getJSONObject(i)
            val path = item.getString("path")

            // Check if the file exists at the specified path
            val file = File(path)
            if (file.exists()) {

                val bitmap = BitmapFactory.decodeFile(path)
                val byteArrayOutputStream = ByteArrayOutputStream()

                var quality = 100 // Start with maximum quality
                var fileSizeInKB: Long

                do {
                    // Reset the stream before each compression
                    byteArrayOutputStream.reset()

                    // Compress the bitmap with the current quality
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        quality,
                        byteArrayOutputStream
                    )

                    // Calculate the size of the compressed image in KB
                    val compressedBytes = byteArrayOutputStream.toByteArray()
                    fileSizeInKB = (compressedBytes.size / 1024).toLong()

                    // Reduce the quality if the file size exceeds 200 KB
                    if (fileSizeInKB > 200) {
                        quality -= 5 // Decrease quality in small steps
                    }
                } while (fileSizeInKB > 200 && quality > 75) // Ensure quality doesn't drop below 75

                val byteArrayImage = byteArrayOutputStream.toByteArray()
                val encodedImage =
                    Base64.encodeToString(byteArrayImage, Base64.NO_WRAP)

                val picture = RetakePicture(
                    id = item.getInt("picture_number"),
                    type = item.getString("picture_type"),
                    otherType = item.getString("picture_other_type"),
                    number = item.getInt("picture_number"),
                    picData = encodedImage
                )

                picturesList.add(picture)
            } else {
                // Handle the case where the file does not exist, if necessary
                val picture = RetakePicture(
                    id = item.getInt("picture_number"),
                    type = item.getString("picture_type"),
                    otherType = item.getString("picture_other_type"),
                    number = item.getInt("picture_number"),
                    picData = "Image not found"
                )

                picturesList.add(picture)
            }
        }

        val rejectedSubParcel =
            Gson().fromJson(surveyFormEntity.subParcelsStatusList, RejectedSubParcel::class.java)

        val pictureFieldRecord = PictureFieldRecord(
            fieldRecordId = rejectedSubParcel.fieldRecordId.toLong(),
            parcelId = surveyFormEntity.parcelId,
            kachiAbadiId = surveyFormEntity.kachiAbadiId,
            pictures = picturesList

        )

        val retakePicturesPost = RetakePicturesPost(
            picturesList = listOf(pictureFieldRecord)
        )

        return retakePicturesPost
    }

    private fun getSurveyFormPost(surveyFormEntity: SurveyFormEntity): SurveyPost {
        // Create Floors
        val floorsList = ArrayList<Floors>()
        if (surveyFormEntity.floorsList != "") {
            val jsonObject1 = JSONObject(surveyFormEntity.floorsList)
            val jsonArray1 = jsonObject1.getJSONArray("floors")

            for (i in 0 until jsonArray1.length()) {

                val floors = Floors()

                val jsonObjectFloor = jsonArray1.getJSONObject(i)
                val floorNumber = jsonObjectFloor.getInt("floor_number")
                val partitions = jsonObjectFloor.getJSONArray("partitions")

                val partitionList = ArrayList<Partition>()

                for (j in 0 until partitions.length()) {
                    val partitionObject = partitions.getJSONObject(j)

                    val partition = Partition(
                        PartitionNumber = partitionObject.getInt("partition_number"),
                        Landuse = partitionObject.getString("landuse"),
                        CommercialActivity = partitionObject.getString("commercial_activity"),
                        Occupancy = partitionObject.getString("occupancy"),
                        TenantName = partitionObject.getString("tenant_name"),
                        TenantFatherName = partitionObject.getString("tenant_father_name"),
                        TenantCnic = partitionObject.getString("tenant_cnic"),
                        TenantMobile = partitionObject.getString("tenant_mobile"),
                    )

                    partitionList.add(partition)
                }
                floors.FloorNumber = floorNumber
                floors.Partitions = partitionList

                floorsList.add(floors)
            }
        }

        // Create Pictures
        val picturesList = ArrayList<Pictures>()

        val jsonObject = JSONObject(surveyFormEntity.picturesList)
        val jsonArray = jsonObject.getJSONArray("pictures")
        for (i in 0 until jsonArray.length()) {

            val item = jsonArray.getJSONObject(i)
            val path = item.getString("path")

            // Check if the file exists at the specified path
            val file = File(path)
            if (file.exists()) {

                val bitmap = BitmapFactory.decodeFile(path)
                val byteArrayOutputStream = ByteArrayOutputStream()

                var quality = 100 // Start with maximum quality
                var fileSizeInKB: Long

                do {
                    // Reset the stream before each compression
                    byteArrayOutputStream.reset()

                    // Compress the bitmap with the current quality
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        quality,
                        byteArrayOutputStream
                    )

                    // Calculate the size of the compressed image in KB
                    val compressedBytes = byteArrayOutputStream.toByteArray()
                    fileSizeInKB = (compressedBytes.size / 1024).toLong()

                    // Reduce the quality if the file size exceeds 200 KB
                    if (fileSizeInKB > 200) {
                        quality -= 5 // Decrease quality in small steps
                    }
                } while (fileSizeInKB > 200 && quality > 75) // Ensure quality doesn't drop below 75

                val byteArrayImage = byteArrayOutputStream.toByteArray()
                val encodedImage =
                    Base64.encodeToString(byteArrayImage, Base64.NO_WRAP)

                val picture = Pictures(
                    Type = item.getString("picture_type"),
                    OtherType = item.getString("picture_other_type"),
                    Number = item.getInt("picture_number"),
                    PicData = encodedImage
                )

                picturesList.add(picture)
            } else {
                // Handle the case where the file does not exist, if necessary
                val picture = Pictures(
                    Type = item.getString("picture_type"),
                    OtherType = item.getString("picture_other_type"),
                    Number = item.getInt("picture_number"),
                    PicData = "Image not found"
                )

                picturesList.add(picture)
            }
        }

        var discrepancyPicture = ""

        if (surveyFormEntity.parcelOperation != "Same") {
            // Check if the file exists at the specified path
            val file = File(surveyFormEntity.discrepancyPicturePath)
            if (file.exists()) {
                val bitmap =
                    BitmapFactory.decodeFile(surveyFormEntity.discrepancyPicturePath)
                val byteArrayOutputStream = ByteArrayOutputStream()

                var quality = 100 // Start with maximum quality
                var fileSizeInKB: Long

                do {
                    // Reset the stream before each compression
                    byteArrayOutputStream.reset()

                    // Compress the bitmap with the current quality
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        quality,
                        byteArrayOutputStream
                    )

                    // Calculate the size of the compressed image in KB
                    val compressedBytes = byteArrayOutputStream.toByteArray()
                    fileSizeInKB = (compressedBytes.size / 1024).toLong()

                    // Reduce the quality if the file size exceeds 200 KB
                    if (fileSizeInKB > 200) {
                        quality -= 5 // Decrease quality in small steps
                    }
                } while (fileSizeInKB > 200 && quality > 75) // Ensure quality doesn't drop below 75

                val byteArrayImage = byteArrayOutputStream.toByteArray()
                discrepancyPicture =
                    Base64.encodeToString(byteArrayImage, Base64.NO_WRAP)
            } else {
                // Handle the case where the file does not exist, if necessary
//                discrepancyPicture = "Image not found"
                discrepancyPicture = ""
            }
        }

        var isDiscrepancy = false
        var discrepancyId = 0

        if (surveyFormEntity.parcelStatus == Constants.Parcel_SAME) {

            if (surveyFormEntity.parcelOperation == "Split" && surveyFormEntity.surveyStatus == Constants.Survey_SAME_Unit) {
                isDiscrepancy = true
                discrepancyId = 1
            } else if (surveyFormEntity.parcelOperation == "Merge" && surveyFormEntity.surveyStatus == Constants.Survey_SAME_Unit) {
                isDiscrepancy = true
                discrepancyId = 2
            } else if (surveyFormEntity.parcelOperation == "Same" && surveyFormEntity.surveyStatus == Constants.Survey_New_Unit) {
                isDiscrepancy = true
                discrepancyId = 3
            } else if (surveyFormEntity.parcelOperation == "Split" && surveyFormEntity.surveyStatus == Constants.Survey_New_Unit) {
                isDiscrepancy = true
                discrepancyId = 4
            } else if (surveyFormEntity.parcelOperation == "Merge" && surveyFormEntity.surveyStatus == Constants.Survey_New_Unit) {
                isDiscrepancy = true
                discrepancyId = 5
            } else {
                isDiscrepancy = false
                discrepancyId = 0
            }

        } else if (surveyFormEntity.parcelStatus == Constants.Parcel_New) {

            if (surveyFormEntity.surveyStatus == Constants.Survey_SAME_Unit) {
                isDiscrepancy = true
                discrepancyId = 6
            } else {
                isDiscrepancy = true
                discrepancyId = 7

            }
        }

        val uploadingAppVersion = Constants.VERSION_NAME

        val deviceInfoJson = JSONObject().apply {
            put("Model", android.os.Build.MODEL)
            put("Brand", android.os.Build.BRAND)
            put("Device", android.os.Build.DEVICE)
            put("Manufacturer", android.os.Build.MANUFACTURER)
            put("SDK Version", android.os.Build.VERSION.SDK_INT)
            put("Android Version", android.os.Build.VERSION.RELEASE)
        }

        // Create SurveyPost instance
        val surveyPost = SurveyPost(
            propertyId = surveyFormEntity.surveyId,
            propertyNumber = surveyFormEntity.propertyNumber,
            parcelId = surveyFormEntity.parcelId,
            subParcelId = when (surveyFormEntity.parcelOperation) {
                "Split" -> surveyFormEntity.subParcelId
                else -> 0
            },
            parcelOperationValue = surveyFormEntity.parcelOperationValue,
            isDiscrepancy = isDiscrepancy,
            discrepancyId = discrepancyId,
            interviewStatus = surveyFormEntity.interviewStatus,
            ownerName = surveyFormEntity.name,
            ownerFatherName = surveyFormEntity.fatherName,
            ownerGender = if (surveyFormEntity.interviewStatus == "Respondent Present" /*&& surveyFormEntity.surveyId > 0*/) {
                surveyFormEntity.gender
            } else {
                ""
            },
            ownerCNIC = surveyFormEntity.cnic,
            cnicSource = surveyFormEntity.cnicSource,
            cnicOtherSource = surveyFormEntity.cnicOtherSource,
            ownerMobileNo = surveyFormEntity.mobile,
            mobileSource = surveyFormEntity.mobileSource,
            mobileOtherSource = surveyFormEntity.mobileOtherSource,
            area = surveyFormEntity.area,
            ownershipType = surveyFormEntity.ownershipType,
            ownershipOtherType = when (surveyFormEntity.ownershipType) {
                "Other" -> surveyFormEntity.ownershipOtherType
                else -> ""
            },
            floorsList = floorsList,
            picturesList = picturesList,
            remarks = surveyFormEntity.remarks,
            kachiAbadiId = surveyFormEntity.kachiAbadiId,
            userId = surveyFormEntity.userId,
            gpsAccuracy = surveyFormEntity.gpsAccuracy,
            gpsAltitude = deviceInfoJson.toString(),
            gpsProvider = surveyFormEntity.gpsProvider,
            gpsTimestamp = surveyFormEntity.gpsTimestamp,
            latitude = surveyFormEntity.latitude,
            longitude = surveyFormEntity.longitude,
            timeZoneId = surveyFormEntity.timeZoneId,
            mobileTimestamp = surveyFormEntity.mobileTimestamp,
            appVersion = "${surveyFormEntity.appVersion}::$uploadingAppVersion",
            discrepancyPicture = discrepancyPicture,
            uniqueId = surveyFormEntity.uniqueId,
            centroidGeom = surveyFormEntity.centroidGeom,
            geom = surveyFormEntity.geom,
            qrCode = surveyFormEntity.qrCode,
        )

        return surveyPost
    }

    override suspend fun viewSavedData(parcelNo: Long, uniqueId: String): List<SurveyFormEntity> {
        return db.surveyFormDao().getCompleteRecord(parcelNo, uniqueId)
    }
    override suspend fun viewSavedDataNew(parcelId: Long): List<NewSurveyNewEntity> {
//        return db.surveyFormDao().getCompleteRecord(parcelNo, uniqueId)
        return db.newSurveyNewDao().getCompleteRecord(parcelId)
    }

}