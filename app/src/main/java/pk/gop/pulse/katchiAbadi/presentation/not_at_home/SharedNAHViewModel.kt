package pk.gop.pulse.katchiAbadi.presentation.not_at_home

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.ExtraFloors
import pk.gop.pulse.katchiAbadi.common.ExtraPictures
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Results
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.SurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_list.SurveyListDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pk.gop.pulse.katchiAbadi.common.SubParcel
import pk.gop.pulse.katchiAbadi.common.toNotAtHomeSurveyFormEntityList
import pk.gop.pulse.katchiAbadi.common.toSurveyFormEntityList
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.use_case.not_at_home.NAHUseCase
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SharedNAHViewModel @Inject constructor(
    private val nahUseCase: NAHUseCase,
    private val sharedPreferences: SharedPreferences,
    private val surveyListDataUseCase: SurveyListDataUseCase,
    private val database: AppDatabase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    var isRevisit: Int
        get() = savedStateHandle.get<Int>("isRevisit") ?: 0
        set(value) = savedStateHandle.set("isRevisit", value)

    var currentLocation: String
        get() = savedStateHandle.get<String>("currentLocation") ?: ""
        set(value) = savedStateHandle.set("currentLocation", value)

    var surveyId: Long
        get() = savedStateHandle.get<Long>("surveyId") ?: 0L
        set(value) = savedStateHandle.set("surveyId", value)

    var surveyPkId: Long
        get() = savedStateHandle.get<Long>("surveyPkId") ?: 0L
        set(value) = savedStateHandle.set("surveyPkId", value)

    var surveyStatus: String
        get() = savedStateHandle.get<String>("surveyStatus") ?: ""
        set(value) = savedStateHandle.set("surveyStatus", value)

    var parcelId: Long
        get() = savedStateHandle.get<Long>("parcelId") ?: 0L
        set(value) = savedStateHandle.set("parcelId", value)

    var parcelPkId: Long
        get() = savedStateHandle.get<Long>("parcelPkId") ?: 0L
        set(value) = savedStateHandle.set("parcelPkId", value)

    var parcelNo: Long
        get() = savedStateHandle.get<Long>("parcelNo") ?: 0L
        set(value) = savedStateHandle.set("parcelNo", value)

    var subParcelNo: String
        get() = savedStateHandle.get<String>("subParcelNo") ?: "0"
        set(value) = savedStateHandle.set("subParcelNo", value)

    var newStatusId: Int
        get() = savedStateHandle.get<Int>("newStatusId") ?: 0
        set(value) = savedStateHandle.set("newStatusId", value)

    var subParcelsStatusList : String
        get() = savedStateHandle.get<String>("subParcelsStatusList") ?: ""
        set(value) = savedStateHandle.set("subParcelsStatusList", value)

    var parcelStatus: String
        get() = savedStateHandle.get<String>("parcelStatus") ?: ""
        set(value) = savedStateHandle.set("parcelStatus", value)

    var geom: String
        get() = savedStateHandle.get<String>("geom") ?: ""
        set(value) = savedStateHandle.set("geom", value)

    var centroid: String
        get() = savedStateHandle.get<String>("centroid") ?: ""
        set(value) = savedStateHandle.set("centroid", value)

    var discrepancyPicturePath: String
        get() = savedStateHandle.get<String>("discrepancyPicturePath") ?: ""
        set(value) = savedStateHandle.set("discrepancyPicturePath", value)

    var imageTaken: Int
        get() = savedStateHandle.get<Int>("imageTaken") ?: 0
        set(value) = savedStateHandle.set("imageTaken", value)

    var parcelOperation: String
        get() = savedStateHandle.get<String>("parcelOperation") ?: "Same"
        set(value) = savedStateHandle.set("parcelOperation", value)

    var parcelOperationValue: String
        get() = savedStateHandle.get<String>("parcelOperationValue") ?: ""
        set(value) = savedStateHandle.set("parcelOperationValue", value)

    var propertyNumber: String
        get() = savedStateHandle.get<String>("propertyNumber") ?: ""
        set(value) = savedStateHandle.set("propertyNumber", value)

    var area: String
        get() = savedStateHandle.get<String>("area") ?: ""
        set(value) = savedStateHandle.set("area", value)

    var interviewStatus: String
        get() = savedStateHandle.get<String>("interviewStatus") ?: "Respondent Present"
        set(value) = savedStateHandle.set("interviewStatus", value)

    var name: String
        get() = savedStateHandle.get<String>("name") ?: ""
        set(value) = savedStateHandle.set("name", value)

    var fname: String
        get() = savedStateHandle.get<String>("fname") ?: ""
        set(value) = savedStateHandle.set("fname", value)

    var gender: String
        get() = savedStateHandle.get<String>("gender") ?: "Male"
        set(value) = savedStateHandle.set("gender", value)

    var cnic: String
        get() = savedStateHandle.get<String>("cnic") ?: ""
        set(value) = savedStateHandle.set("cnic", value)

    var cnicSource: String
        get() = savedStateHandle.get<String>("cnicSource") ?: ""
        set(value) = savedStateHandle.set("cnicSource", value)

    var cnicOtherSource: String
        get() = savedStateHandle.get<String>("cnicOtherSource") ?: ""
        set(value) = savedStateHandle.set("cnicOtherSource", value)

    var mobile: String
        get() = savedStateHandle.get<String>("mobile") ?: ""
        set(value) = savedStateHandle.set("mobile", value)

    var mobileSource: String
        get() = savedStateHandle.get<String>("mobileSource") ?: ""
        set(value) = savedStateHandle.set("mobileSource", value)

    var mobileOtherSource: String
        get() = savedStateHandle.get<String>("mobileOtherSource") ?: ""
        set(value) = savedStateHandle.set("mobileOtherSource", value)

    var cnicSourcePosition: Int
        get() = savedStateHandle.get<Int>("cnicSourcePosition") ?: 0
        set(value) = savedStateHandle.set("cnicSourcePosition", value)

    var mobileSourcePosition: Int
        get() = savedStateHandle.get<Int>("mobileSourcePosition") ?: 0
        set(value) = savedStateHandle.set("mobileSourcePosition", value)

    var ownershipType: String
        get() = savedStateHandle.get<String>("ownershipType") ?: "First owner"
        set(value) = savedStateHandle.set("ownershipType", value)

    var ownershipOtherType: String
        get() = savedStateHandle.get<String>("ownershipOtherType") ?: ""
        set(value) = savedStateHandle.set("ownershipOtherType", value)

    var extraFloorsList: ArrayList<ExtraFloors>
        get() = savedStateHandle.get<ArrayList<ExtraFloors>>("extraFloorsList") ?: arrayListOf()
        set(value) = savedStateHandle.set("extraFloorsList", value)

    var extraPicturesList: ArrayList<ExtraPictures>
        get() = savedStateHandle.get<ArrayList<ExtraPictures>>("extraPicturesList") ?: arrayListOf()
        set(value) = savedStateHandle.set("extraPicturesList", value)

    var remarks: String
        get() = savedStateHandle.get<String>("remarks") ?: ""
        set(value) = savedStateHandle.set("remarks", value)

    var gpsAccuracy: String
        get() = savedStateHandle.get<String>("gpsAccuracy") ?: ""
        set(value) = savedStateHandle.set("gpsAccuracy", value)

    var gpsAltitude: String
        get() = savedStateHandle.get<String>("gpsAltitude") ?: ""
        set(value) = savedStateHandle.set("gpsAltitude", value)

    var gpsProvider: String
        get() = savedStateHandle.get<String>("gpsProvider") ?: ""
        set(value) = savedStateHandle.set("gpsProvider", value)

    var gpsTimestamp: String
        get() = savedStateHandle.get<String>("gpsTimestamp") ?: ""
        set(value) = savedStateHandle.set("gpsTimestamp", value)

    var latitude: String
        get() = savedStateHandle.get<String>("latitude") ?: ""
        set(value) = savedStateHandle.set("latitude", value)

    var longitude: String
        get() = savedStateHandle.get<String>("longitude") ?: ""
        set(value) = savedStateHandle.set("longitude", value)

    var uniqueId: String
        get() = savedStateHandle.get<String>("uniqueId") ?: UUID.randomUUID().toString()
        set(value) = savedStateHandle.set("uniqueId", value)

    var surveyFormCounter: Int
        get() = savedStateHandle.get<Int>("surveyFormCounter") ?: 0
        set(value) = savedStateHandle.set("surveyFormCounter", value)

    var visitCount: Int
        get() = savedStateHandle.get<Int>("visitCount") ?: 0
        set(value) = savedStateHandle.set("visitCount", value)

    var qrCode: String
        get() = savedStateHandle.get<String>("qrCode") ?: ""
        set(value) = savedStateHandle.set("qrCode", value)


    private val _saveForm = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val saveForm = _saveForm.asStateFlow()

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    fun resetValues() {
        viewModelScope.launch {
//            surveyId = 0L
//            surveyPkId = 0L
//            surveyStatus = ""

            propertyNumber = ""
            area = ""

            interviewStatus = "Respondent Present"

//            name = ""
//            fname = ""
            gender = "Male"

            cnic = ""
            cnicSource = ""
            cnicOtherSource = ""

            mobile = ""
            mobileSource = ""
            mobileOtherSource = ""

            cnicSourcePosition = 0
            mobileSourcePosition = 0

            ownershipType = "First owner"
            ownershipOtherType = ""

            extraFloorsList.clear()

            extraPicturesList.clear()

            remarks = ""

            qrCode = ""

            gpsAccuracy = ""
            gpsAltitude = ""
            gpsProvider = ""
            gpsTimestamp = ""
            latitude = ""
            longitude = ""

            _saveForm.emit(Resource.Unspecified())
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }


    fun saveNotAtHomeData() {
        viewModelScope.launch {
            _saveForm.emit(Resource.Loading())

            when (val validationResult =
                nahUseCase.nAHSaveUseCase(createSurveyFormInstance())) {
                is Resource.Success -> {
                    _saveForm.emit(Resource.Success(Unit))
                }

                is Resource.Error -> {
                    _saveForm.emit(Resource.Error(validationResult.message.toString()))
                }

                else -> {}
            }
        }
    }

    private inline fun <reified T : Any> createSurveyFormInstance(): T {

        if(parcelNo == 0L && parcelStatus == Constants.Parcel_SAME && centroid != ""){
            parcelNo = database.parcelDao().getParcelIdByCentroid(centroid)
        }


        val userId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val timeZone = TimeZone.getDefault()
        val timeZoneId = timeZone.id
        val timeZoneName = timeZone.displayName

        val mobileTimestamp = Constants.dateFormat.format(Date())

        val appVersion = Constants.VERSION_NAME

        val kachiAbadiId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val instance = when (T::class) {
            SurveyFormEntity::class -> {
                val surveyFormEntity = SurveyFormEntity(
                    surveyId = surveyId,
                    surveyPkId = surveyPkId, //SQLite Local Id
                    surveyStatus = surveyStatus,
                    propertyNumber = propertyNumber,
                    parcelId = parcelId,
                    parcelPkId = parcelPkId, //SQLite Local Id
                    parcelStatus = parcelStatus,
                    parcelOperation = parcelOperation,
                    parcelOperationValue = parcelOperationValue,
                    discrepancyPicturePath = discrepancyPicturePath,
                    subParcelId = surveyFormCounter,
                    name = name,
                    fatherName = fname,
                    gender = gender,
                    cnic = cnic,
                    cnicSource = cnicSource,
                    cnicOtherSource = cnicOtherSource,
                    mobile = mobile,
                    mobileSource = mobileSource,
                    mobileOtherSource = mobileOtherSource,
                    area = area,
                    ownershipType = ownershipType,
                    ownershipOtherType = ownershipOtherType,
                    floorsList = getFloorsList(),
                    picturesList = getPicturesObject(),
                    remarks = remarks,
                    userId = userId,
                    kachiAbadiId = kachiAbadiId,
                    gpsAccuracy = gpsAccuracy,
                    gpsAltitude = gpsAltitude,
                    gpsProvider = gpsProvider,
                    gpsTimestamp = gpsTimestamp,
                    latitude = latitude,
                    longitude = longitude,
                    timeZoneId = timeZoneId,
                    timeZoneName = timeZoneName,
                    mobileTimestamp = mobileTimestamp,
                    appVersion = appVersion,
                    uniqueId = uniqueId,
                    qrCode = qrCode,
                    interviewStatus = interviewStatus,
                    geom = geom,
                    centroidGeom = centroid,
                    parcelNo = parcelNo,
                    subParcelNo = subParcelNo,
                    newStatusId = newStatusId,
                    subParcelsStatusList = subParcelsStatusList,
                    isRevisit = isRevisit
                )
                surveyFormEntity as T
            }

            TempSurveyFormEntity::class -> {
                val tempSurveyFormEntity = TempSurveyFormEntity(
                    surveyId = surveyId,
                    surveyPkId = surveyPkId, //SQLite Local Id
                    surveyStatus = surveyStatus,
                    propertyNumber = propertyNumber,
                    parcelId = parcelId,
                    parcelPkId = parcelPkId, //SQLite Local Id
                    parcelStatus = parcelStatus,
                    parcelOperation = parcelOperation,
                    parcelOperationValue = parcelOperationValue,
                    discrepancyPicturePath = discrepancyPicturePath,
                    subParcelId = surveyFormCounter,
                    name = name,
                    fatherName = fname,
                    gender = gender,
                    cnic = cnic,
                    cnicSource = cnicSource,
                    cnicOtherSource = cnicOtherSource,
                    mobile = mobile,
                    mobileSource = mobileSource,
                    mobileOtherSource = mobileOtherSource,
                    area = area,
                    ownershipType = ownershipType,
                    ownershipOtherType = ownershipOtherType,
                    floorsList = getFloorsList(),
                    picturesList = getPicturesObject(),
                    remarks = remarks,
                    userId = userId,
                    kachiAbadiId = kachiAbadiId,
                    gpsAccuracy = gpsAccuracy,
                    gpsAltitude = gpsAltitude,
                    gpsProvider = gpsProvider,
                    gpsTimestamp = gpsTimestamp,
                    latitude = latitude,
                    longitude = longitude,
                    timeZoneId = timeZoneId,
                    timeZoneName = timeZoneName,
                    mobileTimestamp = mobileTimestamp,
                    appVersion = appVersion,
                    uniqueId = uniqueId,
                    qrCode = qrCode,
                    interviewStatus = interviewStatus,
                    geom = geom,
                    centroidGeom = centroid,
                    parcelNo = parcelNo,
                    subParcelNo = subParcelNo,
                    newStatusId = newStatusId,
                    subParcelsStatusList = subParcelsStatusList,
                    isRevisit = isRevisit
                )
                tempSurveyFormEntity as T
            }

            NotAtHomeSurveyFormEntity::class -> {
                val notAtHomeSurveyFormEntity = NotAtHomeSurveyFormEntity(
                    surveyId = surveyId,
                    surveyPkId = surveyPkId, //SQLite Local Id
                    surveyStatus = surveyStatus,
                    propertyNumber = propertyNumber,
                    parcelId = parcelId,
                    parcelPkId = parcelPkId, //SQLite Local Id
                    parcelStatus = parcelStatus,
                    parcelOperation = parcelOperation,
                    parcelOperationValue = parcelOperationValue,
                    discrepancyPicturePath = discrepancyPicturePath,
                    subParcelId = surveyFormCounter,
                    name = name,
                    fatherName = fname,
                    gender = gender,
                    cnic = cnic,
                    cnicSource = cnicSource,
                    cnicOtherSource = cnicOtherSource,
                    mobile = mobile,
                    mobileSource = mobileSource,
                    mobileOtherSource = mobileOtherSource,
                    area = area,
                    ownershipType = ownershipType,
                    ownershipOtherType = ownershipOtherType,
                    floorsList = getFloorsList(),
                    picturesList = getPicturesObject(),
                    remarks = remarks,
                    userId = userId,
                    kachiAbadiId = kachiAbadiId,
                    gpsAccuracy = gpsAccuracy,
                    gpsAltitude = gpsAltitude,
                    gpsProvider = gpsProvider,
                    gpsTimestamp = gpsTimestamp,
                    latitude = latitude,
                    longitude = longitude,
                    timeZoneId = timeZoneId,
                    timeZoneName = timeZoneName,
                    mobileTimestamp = mobileTimestamp,
                    appVersion = appVersion,
                    uniqueId = uniqueId,
                    qrCode = qrCode,
                    interviewStatus = interviewStatus,
                    geom = geom,
                    centroidGeom = centroid,
                    visitCount = visitCount + 1,
                    parcelNo = parcelNo,
                    subParcelNo = subParcelNo,
                    newStatusId = newStatusId,
                    subParcelsStatusList = subParcelsStatusList,
                    isRevisit = isRevisit
                )
                notAtHomeSurveyFormEntity as T
            }

            else -> throw IllegalArgumentException("Unsupported class type: ${T::class}")
        }

        return instance
    }


    private fun getFloorsList(): String {
        if (extraFloorsList.size > 0) {
            val floorsList = JSONObject().apply {
                put(
                    "floors",
                    JSONArray().apply {
                        for (floor in extraFloorsList) {
                            put(JSONObject().apply {
                                put("floor_number", floor.priority)

                                val partitions = JSONArray().apply {
                                    for (partition in floor.extraPartitionsList) {
                                        put(JSONObject().apply {

                                            put("partition_number", partition.priority)

                                            put(
                                                "landuse",
                                                removeUnderScores(partition.landuseType)
                                            )

                                            put(
                                                "commercial_activity",
                                                partition.commercialActivityType
                                            )

                                            put(
                                                "occupancy",
                                                removeUnderScores(partition.occupancyStatus)
                                            )

                                            put("tenant_name", "")
                                            put(
                                                "tenant_father_name",
                                                ""
                                            )
                                            put("tenant_cnic", "")
                                            put("tenant_mobile", "")

                                        })
                                    }

                                }

                                put("partitions", partitions)
                            })
                        }
                    }
                )
            }.toString()
            return floorsList
        } else {
            return ""
        }
    }

    private fun getPicturesObject(): String {
        if (extraPicturesList.size > 0) {
            val picturesList = JSONObject().apply {
                put(
                    "pictures",
                    JSONArray().apply {
                        for (picture in extraPicturesList) {
                            put(JSONObject().apply {
                                put("picture_number", picture.priority)
                                put("path", picture.picturePath)
                                put(
                                    "picture_type",
                                    picture.pictureType
                                )
                                put("picture_other_type", picture.otherDescription)
                            })
                        }
                    }
                )
            }.toString()
            return picturesList
        } else {
            return ""
        }
    }

    private fun removeUnderScores(value: String): String {
        return if (value.contains("_")) {
            value.split("_")[0]
        } else {
            value
        }
    }

    fun createFormInstance(item: NotAtHomeSurveyFormEntity) {

        item.let {
            surveyId = it.surveyId
            surveyPkId = it.surveyPkId //SQLite Local Id
            surveyStatus = it.surveyStatus

            parcelId = it.parcelId
            parcelNo = it.parcelNo
            subParcelNo = it.subParcelNo
            newStatusId = it.newStatusId
            subParcelsStatusList = it.subParcelsStatusList
            parcelPkId = it.parcelPkId //SQLite Local Id
            isRevisit = it.isRevisit

            parcelStatus = it.parcelStatus
            parcelOperation = it.parcelOperation
            parcelOperationValue = it.parcelOperationValue
            discrepancyPicturePath = it.discrepancyPicturePath

            propertyNumber = it.propertyNumber
            area = it.area

            name = it.name
            fname = it.fatherName
            visitCount = it.visitCount
            uniqueId = it.uniqueId
            geom = it.geom
            centroid = it.centroidGeom

        }

    }


    fun createSplitFormInstance(item: NotAtHomeSurveyFormEntity) {

        item.let {
            surveyId = it.surveyId
            surveyPkId = it.surveyPkId //SQLite Local Id
            surveyStatus = it.surveyStatus

            parcelId = it.parcelId
            parcelNo = it.parcelNo
            subParcelNo = it.subParcelNo
            newStatusId = it.newStatusId
            subParcelsStatusList = it.subParcelsStatusList
            parcelPkId = it.parcelPkId //SQLite Local Id
            isRevisit = it.isRevisit

            parcelStatus = it.parcelStatus
            parcelOperation = it.parcelOperation
            parcelOperationValue = it.parcelOperationValue
            discrepancyPicturePath = it.discrepancyPicturePath

            propertyNumber = it.propertyNumber
            area = it.area

            name = it.name
            fname = it.fatherName
            visitCount = it.visitCount
            uniqueId = it.uniqueId
            geom = it.geom
            centroid = it.centroidGeom

        }

    }


}