package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson

@Entity
data class NotAtHomeSurveyFormEntity(
    var surveyId: Long, // this is property id
    var surveyPkId: Long,// SQLite Local Id
    var surveyStatus: String,
    var propertyNumber: String,

    var parcelId: Long,
    var parcelPkId: Long, // SQLite Local Id
    var parcelStatus: String,

    var geom: String,
    var centroidGeom: String,

    var parcelOperation: String,
    var parcelOperationValue: String,
    var discrepancyPicturePath: String,
    var subParcelId: Int,

    var interviewStatus: String,

    var area: String,

    var name: String,
    var fatherName: String,
    var gender: String = "",
    var cnic: String = "",
    var cnicSource: String = "",
    var cnicOtherSource: String = "",
    var mobile: String = "",
    var mobileSource: String = "",
    var mobileOtherSource: String = "",

    var ownershipType: String = "",
    var ownershipOtherType: String = "",

    var floorsList: String = "",

    var picturesList: String = "",

    var remarks: String = "",

    var userId: Long,
    var kachiAbadiId: Long,

    var gpsAccuracy: String,
    var gpsAltitude: String,
    var gpsProvider: String,
    var gpsTimestamp: String,
    var latitude: String,
    var longitude: String,
    var timeZoneId: String,
    var timeZoneName: String,
    var mobileTimestamp: String,
    var appVersion: String,
    var uniqueId: String,
    var qrCode: String,

    val parcelNo: Long,
    val subParcelNo: String,
    val newStatusId: Int,
    val subParcelsStatusList: String,
    val isRevisit: Int,

    @PrimaryKey(autoGenerate = true)
    val pkId: Long = 0,
    val statusBit: Int = 0,
    val visitCount: Int = 0
){
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}