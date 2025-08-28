package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttp
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
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonPost
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPostNew
import pk.gop.pulse.katchiAbadi.domain.repository.NewSurveyRepository
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

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
            dao.deleteSurvey(survey)

            // Check if there are any other surveys for this parcel
            val remainingSurveys = dao.getSurveysByParcelId(survey.parcelId)

            if (remainingSurveys.isEmpty()) {
                // No more surveys for this parcel, set status back to unsurveyed (1)
                activeParcelDao.updateSurveyStatus(survey.parcelId, 1)
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Delete failed")
        }
    }
//    override suspend fun uploadSurvey(survey: NewSurveyNewEntity): Resource<Unit> {
//override suspend fun uploadSurvey(context: Context, survey: NewSurveyNewEntity): Resource<Unit>
//{
//    return try {
//            val images = imageDao.getImagesBySurvey(survey.pkId)
//
////            val pictures = convertSurveyImagesToPictures(images)
//            val pictures = convertSurveyImagesToPictures(context, images)
//
//            val personsEntities = personDao.getPersonsForSurvey(survey.pkId)
//            val persons = convertPersonsToPost(personsEntities)
//
//            val post = buildSurveyPost(survey, pictures, persons)
//
//            val gson = Gson()
//            val json = gson.toJson(post)
//            println("SurveyPost JSON = $json")
//        val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
//            val response = api.postSurveyDataNew("Bearer $token", post)
//
////            return Resource.Success(Unit)
////        val response = api.postSurveyDataNew(token, post)
//
//        return if (response.isSuccessful && response.body() != null) {
//            dao.markAsUploaded(survey.pkId)
//            Resource.Success(Unit)
//        } else {
//            Resource.Error("Server error: ${response.errorBody()?.string() ?: "Unknown"} (code ${response.code()})")
//        }
//
//
//    } catch (e: Exception) {
//            Resource.Error(e.message ?: "Unexpected error")
//        }
//    }


    override suspend fun uploadSurvey(
        context: Context,
        survey: NewSurveyNewEntity
    ): Resource<Unit> {
        return try {
            // Run DB queries on IO dispatcher
            val subparcels = withContext(Dispatchers.IO) {
                dao.getCompleteRecord(survey.parcelId)
            }

            if (survey.parcelOperation == "Split") {
                val expectedCount = survey.parcelOperationValue.toIntOrNull() ?: 0
                if (subparcels.size != expectedCount) {
                    return Resource.Error("Expected $expectedCount subparcels but found ${subparcels.size}")
                }
            }

            // Build the posts list inside IO context too
            val posts = withContext(Dispatchers.IO) {
                subparcels.map { subSurvey ->
                    val images = imageDao.getImagesBySurvey(subSurvey.pkId)
                    val pictures = convertSurveyImagesToPictures(context, images)
                    val personsEntities = personDao.getPersonsForSurvey(subSurvey.pkId)
                    val persons = convertPersonsToPost(personsEntities)
                    buildSurveyPost(subSurvey, pictures, persons)
                }
            }
            if (posts.isEmpty()) {
                return Resource.Error("No parcel data to upload - posts list is empty")
            }


            val gson = Gson()
            val json = gson.toJson(posts)
            Log.d("UploadPayload", Gson().toJson(posts))
            // saveJsonToFile(context, json)
            Log.d("UploadDebug", "Uploading ${posts.size} posts")
            Log.d("UploadDebug", "JSON: ${json.take(500)}...")

            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            val response = api.postSurveyDataNew("Bearer $token", posts)

            return if (response.isSuccessful && response.body() != null) {
                // Mark all uploaded in IO thread
                withContext(Dispatchers.IO) {
                    subparcels.forEach { dao.markAsUploaded(it.pkId) }
                }
                Resource.Success(Unit)
            } else {
                Resource.Error(
                    "Server error: ${
                        response.errorBody()?.string() ?: "Unknown"
                    } (code ${response.code()})"
                )
            }

        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unexpected error")
        }
    }


    fun saveJsonToFile(context: Context, json: String, filename: String = "survey_post.json") {
        val file = File(context.filesDir, filename)
        file.writeText(json)
        Log.d("SurveyPostJSON", "JSON saved to file: ${file.absolutePath}")
    }


    override suspend fun getOnePendingSurvey(): NewSurveyNewEntity? =
        dao.getOnePendingSurvey()

//    private fun convertSurveyImagesToPictures(context: Context, images: List<SurveyImage>): List<Pictures> = images.map { img ->
//        val base64Encoded = try {
//            val uri = Uri.parse(img.uri)
//            context.contentResolver.openInputStream(uri)?.use { inputStream ->
//                BitmapFactory.decodeStream(inputStream)?.let { bmp ->
//                    ByteArrayOutputStream().use { os ->
//                        var quality = 100
//                        do {
//                            os.reset()
//                            bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)
//                            quality -= 5
//                        } while (os.size() / 1024 > 200 && quality > 75)
//                        Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
//                    }
//                } ?: "Image decoding failed"
//            } ?: "InputStream not found"
//        } catch (e: Exception) {
//            "Image not found: ${e.localizedMessage}"
//        }
//
//        Pictures(img.id.toInt(), img.type, base64Encoded, img.type)
//    }

    private fun convertSurveyImagesToPictures(
        context: Context,
        images: List<SurveyImage>
    ): List<Pictures> = images.map { img ->
        val base64Encoded = try {
            val uri = Uri.parse(img.uri)
            val inputStream = when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> File(uri.path ?: "").inputStream()
                null -> File(img.uri).inputStream() // 👈 handle plain file paths
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

        Pictures(img.id.toInt(), img.type, base64Encoded, img.type)

    }


    private fun convertPersonsToPost(persons: List<SurveyPersonEntity>): List<SurveyPersonPost> =
        persons.map { p ->
            SurveyPersonPost(
                personId = p.personId,
                firstName = p.firstName,
                lastName = p.lastName,
                gender = p.gender,
                relation = p.relation,
                religion = p.religion,
                mobile = p.mobile,
                nic = p.nic,
                growerCode = p.growerCode,
                personArea = p.personArea,
                ownershipType = p.ownershipType,
                extra1 = p.extra1,
                extra2 = p.extra2,
                mauzaId = p.mauzaId,
                mauzaName = p.mauzaName
            )
        }

    private fun buildSurveyPost(
        survey: NewSurveyNewEntity,
        pictures: List<Pictures>,
        persons: List<SurveyPersonPost>
    ): SurveyPostNew {
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
            parcelId = survey.parcelId,
            parcelNo = survey.parcelNo,
            subParcelNo = survey.subParcelNo,
            parcelOperation = survey.parcelOperation,
            parcelOperationValue = survey.parcelOperationValue,
            pictures = pictures,
            persons = persons
        )
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
