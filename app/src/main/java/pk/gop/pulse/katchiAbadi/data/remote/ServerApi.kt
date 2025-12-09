package pk.gop.pulse.katchiAbadi.data.remote

import ActiveParcelResponse
import AreaResponse
import OwnerResponse
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyRequest
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyResponse
import pk.gop.pulse.katchiAbadi.data.local.DropdownItem
import pk.gop.pulse.katchiAbadi.data.local.TaskSubmitDto
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateDto
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateResponse
import pk.gop.pulse.katchiAbadi.data.remote.post.RetakePicturesPost
import pk.gop.pulse.katchiAbadi.data.remote.post.SurveyPost
import pk.gop.pulse.katchiAbadi.data.remote.request.LoginRequest
import pk.gop.pulse.katchiAbadi.data.remote.response.BasicApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.BasicInfoDto
import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.KachiAbadiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.KatchiAbadiApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.LogoutResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaSyncResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.data.remote.response.PostApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.ResponseDto
import pk.gop.pulse.katchiAbadi.data.remote.response.TaskListResponse
import pk.gop.pulse.katchiAbadi.domain.model.ParcelCreationRequest
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPostNew
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import pk.gop.pulse.katchiAbadi.domain.model.TaskResponse
import pk.gop.pulse.katchiAbadi.domain.model.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ServerApi {

    @POST
    suspend fun login(
        @Url url: String,
        @Body json: LoginRequest
    ): BasicApiDto<LoginDto>

    @POST
    suspend fun loginSurveyor(
        @Url url: String,
        @Body json: LoginRequest
    ): LoginSurveyorResponse



    @POST
    suspend fun forgotPassword(
        @Url url: String,
        @Query("userName") userName: String,
    ): ResponseDto

    @GET
    suspend fun otpVerification(
        @Url url: String,
        @Query("userName") userName: String,
        @Query("userProvidedTotp") userProvidedTotp: String,
    ): ResponseDto

    @PUT
    suspend fun updatePassword(
        @Url url: String,
        @Query("userName") userName: String,
        @Query("updatedPassword") updatedPassword: String,
    ): ResponseDto

    @POST
    @Headers("Content-Type: application/json")
    suspend fun mouzaAssignedData(
        @Url url: String,
        @Body id: Long
    ): BasicInfoDto<MouzaAssignedDto, Info>

    @GET
    @Headers("Content-Type: application/json")
    suspend fun getMauzaSyncInfo(
        @Url url: String,
        @Header("Authorization") token: String
    ): MauzaSyncResponse

    @POST
    @Headers("Content-Type: application/json")
    suspend fun syncData(
        @Url url: String,
        @Body id: Long
    ): KatchiAbadiApiDto<KachiAbadiDto>

    @POST
    @Headers("Content-Type: application/json")
    suspend fun postSurveyData(

        @Url url: String,
        @Body json: SurveyPost
    ): PostApiDto

//   @POST("api/MobileData/AddSurveyData")
//    @Headers("Content-Type: application/json")
//    suspend fun postSurveyDataNew(
//       @Header("Authorization") token: String,
//        @Body json: SurveyPostNew
//    ): PostApiDto

    @POST("api/MobileData/AddSurveyData")
    @Headers("Content-Type: application/json")
    suspend fun postSurveyDataNew(
        @Header("Authorization") token: String,
        @Body json: List<SurveyPostNew>
    ): Response<PostApiDto>

    @POST
    @Headers("Content-Type: application/json")
    suspend fun postSurveyRevisitData(
        @Url url: String,
        @Body json: SurveyPost
    ): PostApiDto

    @POST
    @Headers("Content-Type: application/json")
    suspend fun postSurveyRetakePicturesData(
        @Url url: String,
        @Body json: RetakePicturesPost
    ): PostApiDto

    @GET("api/MobileData/GetAreaListByMauzaId/{mauzaId}")
    suspend fun getAreasByMauzaId(
        @Path("mauzaId") mauzaId: Long,
        @Header("Authorization") token: String
    ): AreaResponse

    @GET("api/MobileData/GetActiveParcelsByMauzaAndArea/{mauzaId}/{areaName}")
    suspend fun getActiveParcelsByMauzaAndArea(
        @Path("mauzaId") mauzaId: Long,
        @Path("areaName") areaName: String,
        @Header("Authorization") token: String
    ): ActiveParcelResponse

    @POST("api/MobileData/Getownersfromdboffline2")
    @Headers("Content-Type: application/json")
    suspend fun getOwnersFromDbOffline(
        @Header("Authorization") token: String,
        @Body khewatIds: List<Long>
    ): List<OwnerResponse>

    @GET("api/Account/GetAllUsersList")
    suspend fun getAllUsers(
        @Header("Authorization") token: String,
        @Query("roleId") roleId: Int? = null,
        @Query("vendorId") vendorId: Int? = null
    ): Response<List<UserResponse>>


    @POST("api/Tasks/create")
    suspend fun submitTask(
        @Header("Authorization") token: String,
        @Body task: TaskSubmitDto
    ): Response<TaskResponse>


    @GET("api/Tasks/user/{userId}")
    suspend fun getTasksForUser(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String
    ): Response<TaskListResponse>


    @PUT("api/tasks/update-status")
    suspend fun updateTaskStatus(
        @Header("Authorization") token: String,
        @Body updateDto: TaskUpdateDto
    ): Response<TaskUpdateResponse>

    @GET("api/MobileData/GetCrops")
    suspend fun getCrops(): Response<List<DropdownItem>>

    @GET("api/MobileData/GetCropTypes")
    suspend fun getCropTypes(): Response<List<DropdownItem>>

    @GET("api/MobileData/GetCropVarieties")
    suspend fun getCropVarieties(): Response<List<DropdownItem>>

    @POST("api/MobileData/AddCropVariety")
    suspend fun addCropVariety(@Body request: AddVarietyRequest): Response<AddVarietyResponse>

    @POST("api/Account/logoutUser")
    suspend fun logoutUser(
        @Query("userId") userId: Long,
        @Query("Mode") mode: String = "Android",
    ): Response<LogoutResponse>
}