package pk.gop.pulse.katchiAbadi.common

import java.text.SimpleDateFormat
import java.util.Locale

object Constants {

    var VERSION_NAME = ""

    const val SAVE_NAH = true

    val MAP_DOWNLOAD_TYPE = DownloadType.TILES

    var BASE_URL = ""
    var LOGIN_URL = ""
    var LOGIN_URL_SUR = ""
    var FORGOT_PASSWORD_URL = ""
    var OTP_VERIFICATION_URL = ""
    var UPDATE_PASSWORD_URL = ""
    var Mauza_Assigned_URL = ""
    var Sync_Mauza_Info_URL = ""
    var SYNC_DATA_URL = ""
    var POST_SURVEY_DATA_URL = ""
    var POST_SURVEY_DATA_REVISIT_URL = ""
    var POST_SURVEY_DATA_RETAKE_PICTURE_URL = ""

    const val DATABASE_NAME = "kachi_abadi_db"

    const val SPLASH_SCREEN_DURATION = 1000L

    const val SHARED_PREF_NAME = "shared_pref"

    const val SHARED_PREF_LOGIN_STATUS = "login"
    const val SHARED_PREF_USER_ID = "id"
    const val SHARED_PREF_USER_CNIC = "cnic"
    const val SHARED_PREF_USER_NAME = "name"
    const val SHARED_PREF_TOKEN = "token"

    const val SHARED_PREF_USER_ASSIGNED_MOUZA = "mouza_id"
    const val SHARED_PREF_USER_ASSIGNED_MOUZA_NAME = "mouza_name"
    const val SHARED_PREF_USER_ASSIGNED_MOUZA_FeetPerMarla = "feetPerMarla"

    const val SHARED_PREF_SYNC_STATUS = "sync_status"

    const val LOGIN_STATUS_ACTIVE = 1
    const val LOGIN_STATUS_INACTIVE = 0

    const val SYNC_STATUS_SUCCESS = 1

    const val SHARED_PREF_DEFAULT_INT = 0
    const val SHARED_PREF_DEFAULT_STRING = ""

    const val maxNumberOfPictures = 10
    const val maxNumberOfFloors = 5

    val newDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val newDateFormatPresentable = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
    val dateFormatPresentable = SimpleDateFormat("dd-MM-yyyy hh:mm:ss a", Locale.getDefault())

    const val UPLOAD_SINGLE_RECORD = "upload_single_record"
    const val UPLOAD_ALL = "upload_all"

    const val Survey_New_Unit = "New"
    const val Survey_SAME_Unit = "Same"

    const val Parcel_New = "New"
    const val Parcel_SAME = "Same"

    const val Package_Provider = "pk.gop.pulse.katchiAbadi.fileProvider"

    const val maxImageSize = 1000

    const val fixedText = "03"

    const val ReVisitThreshold = 3
    const val locationMediumAccuracy = 100000

    const val SHARED_PREF_DEFAULT_DISTANCE = 100
    const val SHARED_PREF_DEFAULT_ACCURACY = 100
    const val SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS = 3
    const val SHARED_PREF_DEFAULT_MIN_SCALE = 16
    const val SHARED_PREF_DEFAULT_MAX_SCALE = 20
    const val SHARED_PREF_DEFAULT_DOWNLOAD_SAVED_DATA = 0

    const val SHARED_PREF_METER_DISTANCE = "meter_distance"
    const val SHARED_PREF_METER_ACCURACY = "meter_accuracy"
    const val SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS = "allowed_downloadable_areas"
    const val SHARED_PREF_MAP_MIN_SCALE = "map_min_scale"
    const val SHARED_PREF_MAP_MAX_SCALE = "map_max_scale"
    const val SHARED_PREF_ALLOW_DOWNLOAD_SAVED_DATA = "allow_download_saved_data"

    const val SHARED_PREF_USER_DOWNLOADED_MAUZA_ID = "downloaded_mauza_id"
    const val SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME = "downloaded_mauza_name"
    const val SHARED_PREF_USER_DOWNLOADED_AREA_ID = "downloaded_area_id"
    const val SHARED_PREF_USER_DOWNLOADED_AREA_Name = "downloaded_area_name"

    const val SHARED_PREF_USER_SELECTED_MAUZA_ID = "selected_mauza_id"
    const val SHARED_PREF_USER_SELECTED_MAUZA_NAME = "selected_mauza_name"
    const val SHARED_PREF_USER_SELECTED_AREA_ID = "selected_area_id"
    const val SHARED_PREF_USER_SELECTED_AREA_NAME = "selected_area_name"

    const val SHARED_PREF_UPDATE_DATABASE = "update_database"
    const val SHARED_PREF_END_SESSION = "end_session"

}

enum class DownloadType {
    TPK,
    TILES,
//    SERVICE
}