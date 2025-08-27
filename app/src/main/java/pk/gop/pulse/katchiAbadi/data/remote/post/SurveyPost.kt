package pk.gop.pulse.katchiAbadi.data.remote.post

import com.google.gson.Gson
import java.io.Serializable

data class SurveyPost(
    val propertyId: Long,
    val propertyNumber: String,
    val parcelId: Long,
    var subParcelId: Int,
    var parcelOperationValue: String,
    val isDiscrepancy: Boolean,
    val discrepancyId: Int,
    val interviewStatus: String,
    val ownerName: String,
    val ownerFatherName: String,
    val ownerGender: String,
    val ownerCNIC: String,
    val cnicSource: String,
    val cnicOtherSource: String,
    val ownerMobileNo: String,
    val mobileSource: String,
    val mobileOtherSource: String,
    val area: String,
    val ownershipType: String,
    val ownershipOtherType: String? = null,
    val floorsList: List<Floors>,
    val picturesList: List<Pictures>,
    val remarks: String,
    val kachiAbadiId: Long,
    val userId: Long,
    var gpsAccuracy: String,
    var gpsAltitude: String,
    var gpsProvider: String,
    var gpsTimestamp: String,
    var latitude: String,
    var longitude: String,
    var timeZoneId: String,
    var mobileTimestamp: String,
    var appVersion: String,
    val discrepancyPicture: String? = null,
    val uniqueId: String,
    val qrCode: String,
    val geom: String,
    val centroidGeom: String,

    ) : Serializable {
    fun toJson(): String {
        return Gson().toJson(this)
    }
}

data class RetakePicturesPost(
    val picturesList: List<PictureFieldRecord>
) : Serializable {
    fun toJson(): String {
        return Gson().toJson(this)
    }
}

data class PictureFieldRecord(
    val fieldRecordId: Long,
    val parcelId: Long,
    val kachiAbadiId: Long,
    val pictures: List<RetakePicture>
)

data class RetakePicture(
    val id: Int,
    val number: Int,
    val otherType: String,
    val picData: String,
    val type: String
)