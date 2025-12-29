

package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "new_surveys")
data class NewSurveyNewEntity(

    @PrimaryKey(autoGenerate = true)
    val pkId: Long = 0,
//    val id: Long  ,
    val propertyType: String = "",
    val ownershipStatus: String = "",
    val variety: String = "",
    val cropType: String = "",
    val crop: String = "",
    val year: String = "",
    val area: String = "",
    val isGeometryCorrect: Boolean = false,
    val remarks: String = "",
    val mauzaId: Long = 0,
    val areaName: String = "",
    val parcelId: Long = 0,
    val parcelNo: String = "",
    val subParcelNo: String = "",
    val parcelOperation: String = "",
    val parcelOperationValue: String = "",

//    var userId: Long,
//    var surveyId: Long,

    val statusBit: Int = 0,




//
//    var gpsAccuracy: String,
//    var gpsAltitude: String,
//    var gpsProvider: String,
//    var gpsTimestamp: String,
//    var latitude: String,
//    var longitude: String,
//    var timeZoneId: String,
//    var timeZoneName: String,
//    var mobileTimestamp: String,
//    var appVersion: String,
//    var uniqueId: String,

    )


