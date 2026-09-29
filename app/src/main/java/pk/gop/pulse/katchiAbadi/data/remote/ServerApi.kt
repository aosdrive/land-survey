package pk.gop.pulse.katchiAbadi.data.remote

import ActiveParcelResponse
import AreaResponse
import OwnerResponse
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyRequest
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyResponse
import pk.gop.pulse.katchiAbadi.data.local.DropdownItem
import pk.gop.pulse.katchiAbadi.data.local.TaskSubmitDto
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateDto
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateResponse
import pk.gop.pulse.katchiAbadi.data.remote.post.RetakePicturesPost
import pk.gop.pulse.katchiAbadi.data.remote.post.SurveyPost
import pk.gop.pulse.katchiAbadi.data.remote.request.LoginRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.OnboardingResponse
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
import pk.gop.pulse.katchiAbadi.data.remote.request.OnboardingUploadDto
import pk.gop.pulse.katchiAbadi.data.remote.response.FreePolygonStartResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.JKGrowerDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LookupItemDto
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaBoundaryResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.BlockDto
import pk.gop.pulse.katchiAbadi.data.remote.response.DivisionDto
import pk.gop.pulse.katchiAbadi.data.remote.response.FarmDto
import pk.gop.pulse.katchiAbadi.data.remote.response.PlotDto
import pk.gop.pulse.katchiAbadi.data.remote.response.SectionDto
import pk.gop.pulse.katchiAbadi.data.remote.response.ZoneDto
import pk.gop.pulse.katchiAbadi.domain.model.CorporateParcelPost
import pk.gop.pulse.katchiAbadi.domain.model.LessorEntity
import pk.gop.pulse.katchiAbadi.data.remote.response.PostApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.ResponseDto
import pk.gop.pulse.katchiAbadi.data.remote.response.TaskListResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.VarietyValidationPost
import pk.gop.pulse.katchiAbadi.data.remote.response.VarietyValidationResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.VersionCheckResponse
import pk.gop.pulse.katchiAbadi.domain.model.AnalyticsParcelsResponse
import pk.gop.pulse.katchiAbadi.domain.model.BoundaryDto
import pk.gop.pulse.katchiAbadi.domain.model.GrowerFeatureDto
import pk.gop.pulse.katchiAbadi.domain.model.LookupItem
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclinePostDto
import pk.gop.pulse.katchiAbadi.domain.model.SowingPersonPostDto
import pk.gop.pulse.katchiAbadi.domain.model.SugarcaneResponseDto
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPostNew
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
    ): Response<ActiveParcelResponse>

    @GET("api/MobileData/Getownersfromdboffline2")
    @Headers("Content-Type: application/json")
    suspend fun getOwnersFromDbOffline(
        @Header("Authorization") token: String,
    ): Response<ResponseBody>

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

    @GET
    suspend fun checkAppVersion(
        @Url url: String,
        @Query("appVersion") appVersion: String
    ): Response<VersionCheckResponse>

    @POST("api/MobileData/UploadOnboarding")
    suspend fun uploadOnboarding(
        @Body data: OnboardingUploadDto
    ): Response<OnboardingResponse>

    @GET("api/IssueType/GetAllIssueTypes")
    suspend fun getIssueTypes(
    ): Response<List<LookupItemDto>>

    @GET("api/PestType/GetAllPestTypes")
    suspend fun getPestTypes(
    ): Response<List<LookupItemDto>>

    @GET("api/DiseaseType/GetAllDiseaseTypes")
    suspend fun getDiseaseTypes(
    ): Response<List<LookupItemDto>>

    @GET("api/MobileData/GetGrowersByMouzaId/{mauzaId}")
    suspend fun getJKGrowersByMouzaId(
        @Path("mauzaId") mauzaId: Long,
        @Header("Authorization") token: String
    ): Response<List<JKGrowerDto>>

//    @GET("MobileData/GetJKGrowersByMouzaCircle/{mouzaName}")
//    suspend fun getJKGrowersByMouzaCircle(
//        @Path("mouzaName") mouzaName: String,
//        @Header("Authorization") token: String
//    ): Response<List<JKGrowerDto>>

    @GET("api/SugarcaneIntersection/GetYearListMapAnalysis")
    suspend fun getAnalysisYears(): List<String?>

    @GET("api/SugarcaneIntersection/GetGroups")
    suspend fun getAnalysisGroups(
        @Query("selectedYear") selectedYear: String?
    ): List<LookupItem>

    @GET("api/SugarcaneIntersection/GetUnits")
    suspend fun getAnalysisUnits(
        @Query("groupId") groupId: Int,
        @Query("selectedYear") selectedYear: String?
    ): List<LookupItem>

    @GET("api/SugarcaneIntersection/GetCircles")
    suspend fun getAnalysisCircles(
        @Query("unitId") unitId: Int,
        @Query("selectedYear") selectedYear: String?
    ): List<LookupItem>

    @GET("api/SugarcaneIntersection/GetMauzas")
    suspend fun getAnalysisMauzas(
        @Query("circleId") circleId: Int,
        @Query("selectedYear") selectedYear: String?
    ): List<LookupItem>

    @GET("api/SugarcaneIntersection/GetBoundaryLayer")
    suspend fun getBoundaryLayer(
        @Query("level") level: String,
        @Query("parentId") parentId: Int?,
        @Query("selectedYear") selectedYear: String?
    ): List<BoundaryDto>

    @GET("api/SugarcaneIntersection/GetSugarcaneByMauza")
    suspend fun getSugarcaneByMauza(
        @Query("mauzaId") mauzaId: Int,
        @Query("selectedYear") selectedYear: String?
    ): SugarcaneResponseDto

    @GET("api/SugarcaneIntersection/GetGrowersGeojsonMapAnalysis")
    suspend fun getGrowersForAnalysis(
        @Query("mauzaId") mauzaId: Int?,
        @Query("selectedYear") selectedYear: String?
    ): List<GrowerFeatureDto>


    @GET("api/SugarcaneIntersection/GetAnalyticsParcels")
    suspend fun getAnalyticsParcels(
        @Query("mauzaId") mauzaId: Int,
        @Query("selectedYear") selectedYear: String?
    ): AnalyticsParcelsResponse

    @POST("api/MobileData/AddParcelDecline")
    @Headers("Content-Type: application/json")
    suspend fun postParcelDeclines(
        @Header("Authorization") token: String,
        @Body body: List<ParcelDeclinePostDto>
    ): Response<PostApiDto>

    @POST("api/RykMobileData/RykValidateVariety")
    suspend fun postVarietyValidations(
        @Header("Authorization") token: String,
        @Body body: List<VarietyValidationPost>
    ): Response<VarietyValidationResponse>

    @GET("api/Mauza/GetMauzaBoundary")
    suspend fun getMauzaBoundary(
        @Header("Authorization") token: String,
        @Query("mauzaId") mauzaId: Long
    ): Response<MauzaBoundaryResponse>

    @POST("api/Mauza/StartFreePolygon")
    suspend fun startFreePolygon(
        @Header("Authorization") token: String,
        @Header("X-Device-Info") deviceInfo: String,
        @Header("X-App-Version") appVersion: String,
        @Query("mauzaId") mauzaId: Long,
        @Query("year") year: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("accuracy") accuracy: Float? = null
    ): Response<FreePolygonStartResponse>

    // =====================================================================
    //  Corporate Farms
    //  These are separate controllers server-side - MobileDataCorporate /
    //  CorporateShapefile / CorporateManagement - so they never clash with the
    //  Katchi Abadi endpoints.
    //
    //  Note: the corporate variants of crops / cropTypes / cropVarieties /
    //  addVariety carry a "Corporate" prefix. In the Corporate Farm app they
    //  REPLACED these methods, but here both flows run side by side so each
    //  needs its own method.
    // =====================================================================

    @POST("api/MobileDataCorporate/AddCorporateSurveyData")
    @Headers("Content-Type: application/json")
    suspend fun postCorporateSurveyData(
        @Header("Authorization") token: String,
        @Body json: List<CorporateParcelPost>
    ): Response<PostApiDto>

    // ---- Lookups ----
    @GET("api/MobileDataCorporate/GetCorporateCrops")
    suspend fun getCorporateCrops(): Response<List<DropdownItem>>

    @GET("api/MobileDataCorporate/GetCorporateCropTypes")
    suspend fun getCorporateCropTypes(): Response<List<DropdownItem>>

    @GET("api/MobileDataCorporate/GetCorporateCropVarieties")
    suspend fun getCorporateCropVarieties(): Response<List<DropdownItem>>

    @POST("api/MobileDataCorporate/AddCorporateCropVariety")
    suspend fun addCorporateCropVariety(
        @Body request: AddVarietyRequest
    ): Response<AddVarietyResponse>

    @GET("api/MobileDataCorporate/GetIrrigationSources")
    suspend fun getIrrigationSources(): Response<List<DropdownItem>>

    @GET("api/MobileDataCorporate/GetCorporatePropertyTypes")
    suspend fun getPropertyTypes(): Response<List<DropdownItem>>

    @GET("api/CorporateManagement/GetAllLessors")
    suspend fun getAllLessors(): Response<List<LessorEntity>>

    // ---- Farm hierarchy: zones -> divisions -> sections -> farms -> blocks -> plots ----
    @GET("api/MobileDataCorporate/zones")
    suspend fun getZones(
        @Header("Authorization") token: String
    ): Response<List<ZoneDto>>

    @GET("api/MobileDataCorporate/divisions/{zoneId}")
    suspend fun getDivisions(
        @Header("Authorization") token: String,
        @Path("zoneId") zoneId: Long
    ): Response<List<DivisionDto>>

    @GET("api/MobileDataCorporate/sections/{divisionId}")
    suspend fun getSections(
        @Header("Authorization") token: String,
        @Path("divisionId") divisionId: Long
    ): Response<List<SectionDto>>

    @GET("api/MobileDataCorporate/Farms/{sectionId}")
    suspend fun getFarms(
        @Header("Authorization") token: String,
        @Path("sectionId") sectionId: Long
    ): Response<List<FarmDto>>

    @GET("api/MobileDataCorporate/blocks/{farmId}")
    suspend fun getBlocks(
        @Header("Authorization") token: String,
        @Path("farmId") farmId: Long
    ): Response<List<BlockDto>>

    @GET("api/MobileDataCorporate/plots/{blockId}")
    suspend fun getPlots(
        @Header("Authorization") token: String,
        @Path("blockId") blockId: Long
    ): Response<List<PlotDto>>

    // AOI / tehsil (these replace mauza + area in the corporate flow)
    @GET("api/CorporateShapefile/GetTehsilNames")
    suspend fun getTehsilNames(
        @Header("Authorization") token: String,
        @Query("district") district: String = ""
    ): Response<List<String>>

    @GET("api/CorporateShapefile/GetAOINames")
    suspend fun getAOINames(
        @Header("Authorization") token: String,
        @Query("tehsil") tehsil: String
    ): Response<List<String>>

    // Returns pipe-delimited text, not JSON
    @GET("api/CorporateShapefile/GetParcelsByAOI")
    suspend fun getParcelsByAOI(
        @Header("Authorization") token: String,
        @Query("aoi") aoi: String
    ): Response<ResponseBody>

    @GET("api/CorporateShapefile/GetAOIByTehsil")
    suspend fun getAOIByTehsil(
        @Header("Authorization") token: String,
        @Query("tehsil") tehsil: String
    ): Response<ResponseBody>

    @GET("api/CorporateShapefile/GetSurveyedParcelsByTehsil")
    suspend fun getSurveyedParcelsByTehsil(
        @Header("Authorization") token: String,
        @Query("tehsil") tehsil: String
    ): Response<ResponseBody>
}
