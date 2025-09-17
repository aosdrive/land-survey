package pk.gop.pulse.katchiAbadi.data.remote

import ActiveParcelResponse
import AreaResponse
import OwnerResponse
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
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaSyncResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.data.remote.response.PostApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.ResponseDto
import pk.gop.pulse.katchiAbadi.domain.model.ParcelCreationRequest
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPostNew
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

    @POST("api/MobileData/AddParcel")
    @Headers("Content-Type: application/json")
    suspend fun createParcel(
        @Header("Authorization") token: String,
        @Body parcels: List<ParcelCreationRequest>
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


}